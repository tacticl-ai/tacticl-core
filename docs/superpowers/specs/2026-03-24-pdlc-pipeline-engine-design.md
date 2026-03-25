# PDLC Pipeline Engine — Design Spec

**Date**: 2026-03-25
**Status**: Reviewed
**Scope**: tacticl-core — multi-agent Product Development Lifecycle pipeline orchestration with async execution and full traceability
**Prerequisites**:
- GitHub connectivity via `client-github` module (repo access for code artifacts) — requires building out `GitHubClient` with full API operations and GitHub agent skills in ToolRegistry
- Claude Code CLI installed on Cloud Run container image (for Implementer, Tester, DevOps roles)

---

## Problem

Today, every spark executes as a single-agent loop: classify → map to one step → execute via one engine → done. This works for simple tasks ("what time is it?", "post to Twitter") but fails for complex development work ("build a notification system with WebSocket support"). Complex sparks need a structured pipeline of specialized agent roles — each with domain expertise, distinct tools, and quality gates — to produce production-grade output with minimal rework.

Additionally, all spark execution is currently synchronous (HTTP request blocks until complete). Most non-trivial sparks should execute asynchronously with real-time progress streaming.

---

## Why PDLC, Not SDLC

The pipeline starts with product-level work — a PM writing a PRD, a researcher gathering context — before it ever touches code. SDLC implies the process starts at "we have requirements, now build it." PDLC (Product Development Lifecycle) captures the full picture from "user has an idea" through delivery and retrospective.

---

## Design Principles

1. **Every step is a discrete, measurable unit** — each role creates a child spark, giving us token/cost/duration tracking for free
2. **Rework is the enemy** — the pipeline catches issues early (reviewer, tester, security) and tracks where rework originates so the system gets better over time
3. **Human stewardship** — configurable checkpoints between any pipeline steps, with sensible defaults
4. **Async-first** — pipeline sparks always run async; simple sparks attempt sync with configurable timeout fallback
5. **Resumable** — pipeline state is Firestore-backed; survives server restarts
6. **Knowledge compounds** — retro analyst feeds learnings back into role knowledge bases
7. **Playbook-driven** — development workflows are data-driven configurations, not hardcoded logic

---

## Architecture Overview

```
Chat message → POST /api/agent/command { text, sessionId }
  → AgentController
    → SparkService.createSpark()
    → SparkClassifierService.classifySparkType()          [existing: code/social/research/etc.]
    → PdlcClassifierService.classifyDepth()               [NEW: SIMPLE/PLAYBOOK/FULL_PDLC]
    → User override? (explicit playbook selection via chat or settings)
    → Routing decision:
        SIMPLE   → VoiceAgentService (existing, sync with timeout fallback)
        PLAYBOOK → PdlcPipelineOrchestrator (async, playbook-defined roles)
        FULL_PDLC → PdlcPipelineOrchestrator (async, up to 12 roles)
    → Immediate HTTP response with sparkId + pipelineRunId
    → Async pipeline execution on background thread pool
    → Events streamed via WebSocket / FCM / polling API
```

---

## 1. Async Execution Model (Timeout-Based Hybrid)

### Execution Modes

| Spark Classification | Execution Mode | Behavior |
|---------------------|---------------|----------|
| Non-code (social, creative, research) | **Sync with timeout** | Attempt sync. If completes within `syncTimeoutMs`, return in HTTP response. Otherwise switch to async. |
| Code — SIMPLE | **Sync with timeout** | Same timeout behavior |
| Code — PLAYBOOK | **Always async** | Return immediately, stream events |
| Code — FULL_PDLC | **Always async** | Return immediately, stream events |

### Sync Timeout Configuration

User-configurable via `UserConfig`:

```java
// In UserConfig (existing entity, new fields)
private int syncTimeoutMs = 3000;           // Default 3s, range: 1000-30000
private boolean forceAsyncAll = false;       // If true, everything goes async
```

Configurable via existing `PUT /api/settings` endpoint and `manage_settings` agent skill.

### Async Response Contract

**Sync response** (completed within timeout):
```json
{
  "sparkId": "spark-abc123",
  "responseText": "It's 2:30 AM in Tokyo.",
  "status": "COMPLETED",
  "toolsInvoked": ["search_web"],
  "model": "claude-haiku-4-5"
}
```

**Async response** (timeout exceeded or pipeline):
```json
{
  "sparkId": "spark-abc123",
  "pipelineRunId": "pipeline-xyz789",
  "pipelineTier": "FULL_PDLC",
  "playbook": "FULL_PDLC",
  "activatedRoles": ["PM", "RESEARCHER", "ARCHITECT", "PLANNER",
                     "IMPLEMENTER", "REVIEWER", "TESTER", "SECURITY", "DEVOPS", "RETRO"],
  "responseText": "Starting full PDLC pipeline for your notification system. Tracking 10 stages.",
  "status": "PIPELINE_STARTED"
}
```

### Timeout Implementation

The sync-with-timeout approach must handle the case where the LLM call exceeds the timeout:

1. Controller submits execution to a `CompletableFuture`
2. `future.get(syncTimeoutMs, MILLISECONDS)` — if completes, return sync response
3. If `TimeoutException`: the underlying execution **continues running** (fire-and-forget)
   - The spark transitions to `executionMode = ASYNC` (new field on Spark entity)
   - HTTP response returns the async contract immediately
   - When the execution eventually completes, results are written to Firestore and pushed via WebSocket/FCM instead of HTTP response
4. **State machine rule**: Once `executionMode` flips to `ASYNC`, only the async completion path writes results. The spark's `executionMode` field is the single source of truth — prevents dual-write race conditions.
5. The spark gains a new field: `executionMode` (enum: `SYNC`, `ASYNC`, `PIPELINE`) set at creation or on timeout transition.

```java
// In AgentController (simplified)
CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(
    () -> voiceAgentService.execute(sparkId, ...),
    simpleSparkExecutor
);
try {
    AgentResult result = future.get(userConfig.getSyncTimeoutMs(), MILLISECONDS);
    return syncResponse(result);  // Completed within timeout
} catch (TimeoutException e) {
    sparkService.markAsync(sparkId);  // Atomically transition to async mode
    return asyncResponse(sparkId);    // Return immediately
    // future continues running — completion handler writes to Firestore + pushes WebSocket
}
```

### Thread Pool Configuration

```java
@Bean("pdlcPipelineExecutor")
public TaskExecutor pdlcPipelineExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);         // 4 concurrent pipelines
    executor.setMaxPoolSize(8);          // Burst to 8
    executor.setQueueCapacity(50);       // 50 can queue
    executor.setThreadNamePrefix("pdlc-pipeline-");
    return executor;
}
```

### Crash Recovery

`PipelineRecoveryJob` runs on application startup (`@PostConstruct`):
1. Query `pipeline_runs` WHERE `status = EXECUTING`
2. For each, attempt to claim via Firestore transaction:
   - Atomically set `claimedBy = instanceId` and `claimedAt = now` on the PipelineRun
   - Only the instance that wins the CAS (compare-and-swap) operation proceeds
   - Stale claim timeout: if `claimedAt` is older than 30 minutes, another instance can reclaim
3. For claimed pipelines, find last completed role
4. Resume from next role in sequence
5. Emit `PIPELINE_RESUMED` event

This handles Cloud Run multi-instance deployments where multiple instances start simultaneously during rolling updates.

---

## 2. PDLC Classifier

### Two-Stage Classification

**Stage 1** (existing): `SparkClassifierService` classifies spark type (code, social, research, devops, creative, data) via Haiku.

**Stage 2** (NEW): `PdlcClassifierService` evaluates depth for `code` and `devops` sparks. Uses a multi-dimensional rubric with chain-of-thought reasoning:

| Dimension | Weight | Signals |
|-----------|--------|---------|
| Scope | 25% | Number of components, files, systems affected |
| Risk | 20% | Greenfield vs modification, data model changes |
| Domain breadth | 15% | Backend-only, frontend-only, full-stack |
| Integration surface | 15% | External APIs, auth, databases |
| Testing complexity | 15% | Unit sufficient vs E2E/integration needed |
| Reversibility | 10% | Easy rollback vs hard (migrations, schema) |

### Classification Output

```java
public record PdlcClassification(
    PipelineTier tier,                    // SIMPLE, PLAYBOOK, FULL_PDLC
    String playbook,                       // Playbook name (e.g., "BUG_FIX", "FULL_PDLC")
    double confidence,                     // 0.0-1.0
    List<PdlcRole> activatedRoles,        // Roles to execute (from playbook)
    List<PdlcRole> skippedRoles,          // Roles not needed
    Map<String, Integer> dimensionScores, // Per-dimension scores for traceability
    String reasoning                       // Chain-of-thought explanation
) {}
```

### Confidence Gating

| Confidence | Behavior |
|-----------|----------|
| > 0.85 | Auto-route, inform user |
| 0.50 - 0.85 | Propose via checkpoint: "This looks like a major feature. Run full PDLC pipeline?" |
| < 0.50 | Ask user directly: "How should I handle this?" |

### User Override

Users can explicitly select a playbook regardless of classifier output:
- **Via chat**: "Run FULL_PDLC on this" or "Use the bug fix playbook"
- **Via API**: `POST /api/agent/command { text, playbook: "BUG_FIX" }`
- **Via settings**: Default playbook preference per spark type in `UserConfig`

When a user overrides, the classifier still runs (for analytics/comparison) but the user's choice takes precedence.

### Classifier Knowledge Base

- **Rubric definitions** — detailed criteria for each dimension score
- **Labeled examples** — curated spark → classification pairs (grows via retro feedback loop)
- **Codebase context** — repo structure, recent git history, test coverage (injected dynamically via `client-github`)

---

## 3. Playbook Architecture

### What Is a Playbook

A playbook is a **data-driven pipeline configuration** — an ordered list of roles with a dependency graph, describing a specific development workflow. The full PDLC is one playbook. Bug fix is another. Each has different roles, different ordering, and different checkpoint defaults.

### PlaybookConfig Data Structure

```java
public record PlaybookConfig(
    String name,                          // "FULL_PDLC", "BUG_FIX", etc.
    String displayName,                   // "Full Product Development Lifecycle"
    String description,                   // When to use this playbook
    PipelineTier tier,                    // PLAYBOOK or FULL_PDLC
    List<PlaybookStage> stages,           // Ordered role sequence
    Map<PdlcRole, List<PdlcRole>> parallelGroups, // Roles that can run concurrently
    Map<PdlcRole, CheckpointRule> defaultCheckpoints, // Default checkpoint config
    boolean isSystemPlaybook              // true = built-in, false = user-defined (future)
) {}

public record PlaybookStage(
    PdlcRole role,
    boolean required,                     // false = can be skipped by classifier
    List<PdlcRole> dependsOn,            // Must complete before this stage starts
    List<PdlcRole> canRejectTo,          // Which roles this stage can send rework to
    Duration timeout                      // Per-role timeout (e.g., 30min for Implementer, 10min for PM)
) {}
```

### Default Playbooks

| Playbook | Roles (in order) | Parallel Groups | When |
|----------|-----------------|-----------------|------|
| **FULL_PDLC** | PM → Researcher → Architect → Designer → Planner → Implementer → Reviewer → Tester ∥ Security → Tech Writer ∥ DevOps → Retro | Tester ∥ Security, Tech Writer ∥ DevOps | New systems, major features, multi-component work |
| **BUG_FIX** | Researcher → Implementer → Reviewer → Tester | — | Known bug, needs diagnosis and fix |
| **SMALL_FEATURE** | PM → Implementer → Reviewer → Tester | — | Clear, bounded feature that fits in a few files |
| **REFACTOR** | Researcher → Architect → Implementer → Reviewer → Tester | — | Restructuring existing code with design consideration |
| **INFRA_CHANGE** | Architect → DevOps → Security | — | CI/CD, deployment, infrastructure only |
| **DOCS_ONLY** | Researcher → Technical Writer | — | Documentation updates |
| **UI_CHANGE** | Designer → Implementer → Reviewer → Tester | — | Frontend-only work |
| **SECURITY_PATCH** | Security → Researcher → Implementer → Tester → DevOps | — | Vulnerability remediation (Security leads, urgency-first) |

Note: Each playbook has its own **role ordering and dependency graph** — not just a subset of the FULL_PDLC graph. SECURITY_PATCH puts Security first (to scope the vulnerability). BUG_FIX puts Researcher first (to diagnose).

### Playbook Storage

Default playbooks are defined as static configuration in code (a `PlaybookRegistry` with `@PostConstruct` initialization). The data structure is designed so that future user-defined playbooks can be stored in Firestore and loaded alongside defaults — no architecture change needed, just a CRUD layer on top of the same `PlaybookConfig` structure.

```java
@Service
public class PlaybookRegistry {
    private final Map<String, PlaybookConfig> playbooks = new LinkedHashMap<>();

    @PostConstruct
    void registerDefaults() {
        register(fullPdlcPlaybook());
        register(bugFixPlaybook());
        register(smallFeaturePlaybook());
        // ... etc
    }

    // Future: load user-defined playbooks from Firestore
    // void loadCustomPlaybooks(String userId) { ... }

    public Optional<PlaybookConfig> getPlaybook(String name) { ... }
    public List<PlaybookConfig> getAllPlaybooks() { ... }
}
```

---

## 4. PDLC Pipeline Roles (12 Roles)

### Role Definitions

Each role is a `PdlcRoleSkill` implementation with its own system prompt, knowledge base, few-shot examples, tool access, success criteria, and preferred engine/model.

| # | Role | PDLC Step (enum) | Engine | Model | Tools | Output Artifact |
|---|------|------------------|--------|-------|-------|-----------------|
| 1 | **Product Manager** | REQUIREMENTS_GATHERING | anthropic-agentic | sonnet | search_web, browse_web | Requirements doc / PRD, acceptance criteria, scope boundary |
| 2 | **Researcher** | WEB_RESEARCH + CODE_ANALYSIS | codex-cli / anthropic-agentic | sonnet | search_web, browse_web, github | Research findings, codebase context, API refs, risk flags |
| 3 | **Architect** | SYSTEM_DESIGN | anthropic-agentic | opus | search_web, browse_web, github | Architecture design, data models, API contracts, component diagram |
| 4 | **Designer** | UI_UX_DESIGN | anthropic-agentic | sonnet | browse_web, image_analysis | Wireframes, user flows, component specs. *Skipped for backend-only work.* |
| 5 | **Planner** | TASK_DECOMPOSITION | anthropic-agentic | sonnet | github | Task breakdown, dependency graph, execution order, effort estimates |
| 6 | **Implementer** | CODE_GENERATION | claude-code-cli | opus | full device/cloud toolset + github | Source code, migrations, configs → committed to GitHub branch |
| 7 | **Reviewer** | CODE_REVIEW | anthropic-agentic | sonnet | github | ACCEPT/REJECT + review comments via GitHub PR review |
| 8 | **Tester** | TEST_GENERATION + TEST_EXECUTION | claude-code-cli | sonnet | full device/cloud toolset + github | Test suites, test results, coverage reports, bug reports |
| 9 | **Security Analyst** | SECURITY_REVIEW | anthropic-agentic | opus | browse_web, search_web, github | Vulnerability report, OWASP checks, remediation items |
| 10 | **Technical Writer** | DOCUMENTATION | anthropic-agentic | sonnet | github | API docs, README, changelog, PR description |
| 11 | **DevOps Engineer** | DEPLOYMENT_SCRIPT | claude-code-cli | sonnet | full device/cloud toolset + github | CI/CD config, deploy scripts, infra changes, monitoring alerts |
| 12 | **Retro Analyst** | RETROSPECTIVE | anthropic-agentic | sonnet | none (analysis only) | Rework analysis, proficiency scores, improvement items, knowledge base updates |

**Note on `claude-code-cli` on Cloud Run**: Claude Code CLI is installed on the Cloud Run container image as a prerequisite. Roles that need filesystem access and autonomous coding (Implementer, Tester, DevOps) use it as a subprocess, the same way device agents do. The Cloud Build config must include Claude Code CLI installation in the Docker image. Fallback engines (`anthropic-agentic`, `codex-cli`) are configured per `AiSdlcStepDefaults` if CLI is unavailable.

### Role-to-Engine Integration

Each `PdlcRoleSkill.execute()` delegates to the existing `AiEngineRouterService`:

```
PdlcRoleSkill.execute(RoleContext ctx)
  → Build AiEngineRequest:
      - systemPrompt = role's static prompt + injected knowledge + upstream artifacts summary
      - prompt = original user request + rework feedback (if applicable)
      - tools = RoleToolFilter.getToolsForRole(role) from ToolRegistry
      - metadata = { sparkId, userId, pipelineRunId, pdlcRole }
  → AiEngineRouterService.executeStep(role.getAiSdlcStepName(), request)
      → Resolves engine + model from AiSdlcStepDefaults (or Firestore override)
      → Engine handles multi-turn tool loop (AgenticApiAiEngine or ClaudeCodeAiEngine)
  → Convert AiEngineResult → RoleResult (extract artifacts, metrics, outcome)
```

Each role skill is injected with `AiEngineRouterService` and `ToolRegistry`. The skill builds the request with role-specific context, then delegates entirely to the engine framework. This preserves the existing "every AI call goes through AiEngineRouterService" principle.

### Per-Role Timeout

Each playbook stage defines a timeout. If a role exceeds its timeout, the orchestrator:
1. Cancels the engine execution (best-effort)
2. Marks the role as FAILED with reason "timeout"
3. Escalates to user checkpoint: "The {role} role timed out after {duration}. Retry, skip, or abort?"

Default timeouts per role:
| Role | Default Timeout | Rationale |
|------|----------------|-----------|
| PM | 10 min | Scoping should be bounded |
| Researcher | 15 min | Web search + code analysis |
| Architect | 15 min | Design work |
| Designer | 10 min | UI/UX |
| Planner | 10 min | Task breakdown |
| Implementer | 45 min | Largest work unit — code writing |
| Reviewer | 15 min | Code review |
| Tester | 30 min | Test writing + execution |
| Security | 20 min | Security analysis |
| Technical Writer | 10 min | Documentation |
| DevOps | 20 min | Infrastructure work |
| Retro | 10 min | Analysis |

A `PipelineWatchdog` scheduled task runs every 60 seconds, checking for roles that have exceeded their timeout.

### New AiSdlcStep Enum Values

Add to existing `AiSdlcStep` enum (**keep the enum name as-is** — renaming to `AiPdlcStep` would break Firestore routing configs that reference step names as strings, plus all callers in VoiceAgentService, SparkClassifierService, AiSparkTypeStepMapper, and cidadel-core's AiEngineRouterService):
- `REQUIREMENTS_GATHERING` — "Gather and define requirements and acceptance criteria"
- `SYSTEM_DESIGN` — "Design system architecture and component breakdown"
- `UI_UX_DESIGN` — "Design user interface and user experience"
- `SECURITY_REVIEW` — "Review code for security vulnerabilities"
- `RETROSPECTIVE` — "Analyze pipeline execution and generate learnings"

### PdlcRoleSkill Interface

```java
public interface PdlcRoleSkill {
    PdlcRole getRole();
    String getSystemPrompt();
    List<KnowledgeEntry> getKnowledgeBase();
    List<FewShotExample> getFewShotExamples();
    List<String> getAvailableTools();
    String getPdlcStepName();
    SuccessCriteria getSuccessCriteria();
    RoleResult execute(RoleContext ctx);
}
```

### RoleContext (Input to Each Role)

```java
public record RoleContext(
    String pipelineRunId,
    String parentSparkId,
    String childSparkId,
    String userId,
    String originalRequest,              // User's original spark text
    PdlcClassification classification,   // Classifier output
    PlaybookConfig playbook,             // Active playbook config
    Map<PdlcRole, ArtifactRef> upstreamArtifacts,  // Artifacts from previous roles
    GitContext gitContext,                // Repo, branch, latest commit SHA
    String reworkFeedback,               // Null on first pass; rejection reason on rework
    int reworkIteration,                 // 1 = first pass, 2+ = rework
    UserConfig userConfig                // User preferences
) {}

public record GitContext(
    String repoFullName,                 // e.g., "cuztomizer/tacticl-core"
    String baseBranch,                   // e.g., "main"
    String workingBranch,                // e.g., "pdlc/spark-abc123-notification-system"
    String latestCommitSha               // Latest commit on working branch
) {}
```

### RoleResult (Output from Each Role)

```java
public record RoleResult(
    RoleOutcome outcome,                 // COMPLETED, REJECTED, FAILED, ESCALATED
    List<Artifact> artifacts,            // Produced artifacts
    String summary,                      // Human-readable summary of what was done
    String rejectionReason,              // If outcome is REJECTED
    PdlcRole reworkTarget,              // Which role to send back to (if REJECTED)
    RoleMetrics metrics                  // tokens, cost, duration, model, engine
) {}
```

---

## 5. Artifact Storage — GitHub as Source of Truth

### Principle

**All source code lives in GitHub.** The pipeline creates a feature branch per pipeline run. Code artifacts are commits on that branch, not Firestore documents. Non-code artifacts (requirements, designs, test reports) are stored in Firestore with references.

### Artifact Types and Storage

| Artifact Type | Storage | Reference in Firestore |
|--------------|---------|----------------------|
| **Source code** | GitHub (commits on feature branch) | `{ repo, branch, commitSha, filesChanged[] }` |
| **PR / Review** | GitHub (pull request) | `{ repo, prNumber, prUrl, reviewState }` |
| **Requirements / PRD** | Firestore `pipeline_artifacts/` | Full content in document |
| **Architecture design** | Firestore `pipeline_artifacts/` | Full content in document |
| **Test results** | Firestore `pipeline_artifacts/` | Summary + pass/fail counts |
| **Security report** | Firestore `pipeline_artifacts/` | Findings with severity levels |
| **Deployment config** | GitHub (committed to repo) | `{ repo, branch, commitSha, filesChanged[] }` |

### Git Flow per Pipeline

```
1. PM completes → no git activity yet (requirements in Firestore)
2. Architect completes → no git activity (design doc in Firestore)
3. Planner completes → create feature branch: pdlc/spark-{id}-{slug}
4. Implementer executes → commits code to feature branch
5. Reviewer executes → creates GitHub PR (feature branch → base branch), reviews via PR
6. Tester executes → commits tests to feature branch, runs CI
7. Security executes → reviews code on branch
8. Tech Writer executes → commits docs to feature branch
9. DevOps executes → commits deploy configs to feature branch
10. Pipeline complete → PR ready for final merge (human or auto based on checkpoint config)
```

### Prerequisite: GitHub Connectivity

The `client-github` module already exists in tacticl-core. The pipeline requires:
- User has granted repo access via `manage_repo` skill (existing)
- OAuth token for GitHub API access (stored in `social_integrations/`)
- Read/write access to target repository

---

## 6. Pipeline Orchestrator

### PdlcPipelineOrchestrator

Core orchestration service. Manages the full pipeline lifecycle. Reads playbook config to determine role ordering and dependencies.

```
orchestrate(parentSparkId, classification, playbook, userId):
  1. Create PipelineRun in Firestore
  2. Emit PIPELINE_STARTED event
  3. Check pipeline cost ceiling (both pipelineCostCeiling and monthly spendingLimit)
  4. For each stage in playbook.stages (respecting dependency order):
     a. Check for human checkpoint before this role (if configured)
     b. If stage has parallelizable peers, execute concurrently
     c. Create child Spark (parentSparkId = user's spark)
     d. Build RoleContext (upstream artifacts + knowledge + git context + rework feedback)
     e. Emit ROLE_STARTED event
     f. Execute role via PdlcRoleSkill.execute(ctx)
     g. Emit ROLE_COMPLETED/ROLE_REJECTED event with metrics
     h. Store artifacts via PipelineArtifactService
     i. Update PipelineRun.roleResults
     j. Check cost against ceiling — emit COST_THRESHOLD_WARNING if approaching limit
     k. If validation role (Reviewer/Tester/Security) rejects:
        - handleRework(run, rejectingRole, targetRole, feedback)
     l. Check for human checkpoint after this role (if configured)
  5. Run Retro Analyst — hardcoded as post-pipeline hook, NOT a configurable playbook stage.
     The retro always runs after the last playbook stage completes, regardless of playbook config.
     It is excluded from playbook.stages and cannot be skipped or reordered.
  6. Aggregate total tokens/cost from all child sparks
  7. Mark PipelineRun as COMPLETED
  8. Mark parent Spark as COMPLETED
  9. Emit PIPELINE_COMPLETED event
```

### Rework Loop Mechanics

When a downstream role rejects:

1. Increment `PipelineRun.reworkCount`
2. Emit `REWORK_TRIGGERED` event to `pipeline_events/` with metadata: `rejectingRole`, `targetRole`, `rejectionReason`, `reworkIteration`
3. Create NEW child spark for the target role with rejection feedback
4. Re-execute target role with `reworkFeedback` and `reworkIteration` in context
5. After rework, re-execute the rejecting role to validate

**Max rework per role**: 3 iterations (configurable). After max rejections → escalate to user via checkpoint.

**Rework flow example:**
```
Implementer (spark-101) → commits code to branch
Reviewer (spark-102) → REJECTS: "Missing error handling on WebSocket disconnect"
  ↳ Rework → Implementer (spark-103, iteration=2, feedback="Missing error handling...")
  ↳ Implementer commits fix
  ↳ Reviewer (spark-104) → ACCEPTS
  ↳ Continue to Tester
```

### Parallel Role Execution

Defined per playbook via `parallelGroups`. For FULL_PDLC:
- **Tester + Security Analyst** — run concurrently after Reviewer accepts
- **Technical Writer + DevOps** — run concurrently after Tester/Security complete

```
PM → Researcher → Architect → Designer → Planner → Implementer → Reviewer
                                                                     ↓
                                                              ┌──────┴──────┐
                                                              Tester    Security
                                                              └──────┬──────┘
                                                                     ↓
                                                              ┌──────┴──────┐
                                                           Tech Writer   DevOps
                                                              └──────┬──────┘
                                                                     ↓
                                                                   Retro
```

---

## 7. Pipeline Cost Ceiling

### Per-Pipeline Cost Management

Each pipeline run tracks cumulative cost. The orchestrator checks cost at every role transition against **two limits**:

1. **`pipelineCostCeiling`** — per-pipeline max cost (default: $50). Prevents a single pipeline from running away.
2. **`spendingLimit`** — monthly aggregate cap across ALL sparks (existing field, default: $0). Prevents overall overspend.

**Relationship**: Both limits are checked. The pipeline is blocked if either would be exceeded. The existing `spendingLimit = $0` default means **pipelines are blocked by default until the user sets a non-zero spending limit**. This is intentional — the user must explicitly opt into spending. The `pipelineCostCeiling` is a secondary guardrail within the monthly budget.

```java
// In UserConfig (existing field, enhanced)
private BigDecimal spendingLimit;              // Existing: monthly aggregate cap ($0 = pipelines blocked)
private BigDecimal pipelineCostCeiling;        // NEW: per-pipeline max cost (default: $50)
private double costWarningThreshold = 0.8;     // Warn at 80% of ceiling
```

### Cost Check Flow

```
Before pipeline starts:
  1. Check spendingLimit > 0 (if $0, block pipeline, prompt user to set a limit)
  2. Check monthly spend + estimated pipeline cost < spendingLimit

After each role completes:
  1. Calculate cumulative pipeline cost (sum of all child spark estimatedCost)
  2. Check against pipelineCostCeiling:
     a. If cumulative >= pipelineCostCeiling:
        - Emit COST_CEILING_REACHED event, PAUSE pipeline
        - Checkpoint: "Pipeline reached $X ceiling. Continue or abort?"
     b. If cumulative >= costWarningThreshold * pipelineCostCeiling:
        - Emit COST_THRESHOLD_WARNING, push notification (warning only)
  3. Check against monthly spendingLimit:
     a. Query total spend this month (all sparks) + current pipeline cumulative
     b. If exceeds spendingLimit, pause with checkpoint
```

### Cost Estimation per Role

Before each role starts, the orchestrator estimates the role's likely cost based on:
- Historical average for this role (from `pipeline_events/` metadata)
- Complexity of the current spark (from classifier dimensions)
- If estimated total would exceed either ceiling, warn the user proactively

---

## 8. Human Stewardship — Configurable Checkpoints

### Checkpoint Placement

Checkpoints can be placed **before** or **after** any role in the pipeline. Configuration is per-user with playbook-specific defaults.

### Default Checkpoint Configuration

| Position | Default | Rationale |
|----------|---------|-----------|
| After PM (requirements/PRD) | **ON** | Confirm scope before design work begins |
| After Architect (design) | **ON** | Confirm architecture before implementation |
| After Reviewer (code review) | OFF | Trust automated review unless user wants oversight |
| After Tester (test results) | OFF | Trust test results unless coverage concerns |
| After Security (security report) | **ON** (if medium+ findings) | Always surface security issues |
| Before DevOps (deployment) | **ON** | Confirm before deploying to any environment |
| Rework escalation (3 failures) | **ALWAYS ON** | Cannot be disabled — safety valve |

### User Configuration

```java
// In UserConfig (existing entity, new field)
private PipelineCheckpointConfig pipelineCheckpoints;

public record PipelineCheckpointConfig(
    Map<PdlcRole, CheckpointRule> roleCheckpoints,  // Per-role override
    boolean approveBeforeDeploy,                     // Default: true
    boolean approveRequirements,                     // Default: true
    boolean approveArchitecture,                     // Default: true
    boolean approveOnSecurityFindings,               // Default: true
    int securityFindingSeverityThreshold,            // Default: 2 (MEDIUM)
    boolean autoApproveAll                           // Default: false — danger mode
) {}

public record CheckpointRule(
    boolean beforeRole,    // Checkpoint before this role executes
    boolean afterRole,     // Checkpoint after this role completes
    boolean onRejection    // Checkpoint when this role rejects (vs auto-rework)
) {}
```

Configurable via:
- `PUT /api/settings` REST endpoint
- `manage_settings` agent skill
- Mobile app settings UI

### Checkpoint Flow

```
Orchestrator reaches checkpoint-enabled role:
  1. Emit CHECKPOINT_REQUESTED event
  2. Create Checkpoint entity (existing checkpoints/ collection)
  3. Send push notification (FCM)
  4. Pipeline PAUSES (role state = AWAITING_APPROVAL)
  5. User reviews via:
     a. Mobile app (checkpoint UI with artifact preview)
     b. Push notification → deep link
     c. Agent chat ("approve", "reject", "modify scope")
  6. User decision:
     APPROVED → continue pipeline
     REJECTED → cancel pipeline or rework specific role
     MODIFIED → user provides updated direction, restart from that role
  7. Emit CHECKPOINT_RESOLVED event
  8. Resume pipeline
```

### Checkpoint Timeout

If the user doesn't respond within a configurable window (default: 24 hours):
- Reminder notifications at 1h, 4h, 12h
- After timeout: pauses indefinitely (does NOT auto-approve)
- User can resume at any time — pipeline state is persisted

---

## 9. Real-Time Progress — Three Channels

### WebSocket (Existing Infrastructure)

Push pipeline events to connected clients. Reuses the device agent WebSocket infrastructure.

**Events pushed:**
`PIPELINE_STARTED`, `PIPELINE_COMPLETED`, `PIPELINE_FAILED`, `ROLE_STARTED`, `ROLE_COMPLETED`, `ROLE_REJECTED`, `REWORK_TRIGGERED`, `REWORK_COMPLETED`, `CHECKPOINT_REQUESTED`, `CHECKPOINT_RESOLVED`, `ARTIFACT_PRODUCED`, `COST_THRESHOLD_WARNING`

### FCM Push Notifications (Existing Infrastructure)

Key milestones when user isn't actively watching:
- `PIPELINE_STARTED` — "Starting PDLC pipeline: {spark title}"
- `CHECKPOINT_REQUESTED` — "Your approval is needed: {description}"
- `PIPELINE_COMPLETED` — "Pipeline complete: {summary}"
- `PIPELINE_FAILED` — "Pipeline failed at {role}: {error}"

### Polling API (New Endpoint)

```
GET /api/sparks/{sparkId}/pipeline
```

Returns full `PipelineRun` with current state, role statuses, events, artifacts, and metrics.

### PipelineEventEmitter

Single service that fans out all pipeline state changes:

```java
@Service
public class PipelineEventEmitter {
    void emitEvent(PipelineRun run, PipelineEventType type,
                   PdlcRole role, Map<String, Object> metadata) {
        // 1. Persist to pipeline_events/ (append-only)
        // 2. Update PipelineRun summary (denormalized current state)
        // 3. Push via WebSocket to user's session
        // 4. Push via FCM if milestone event + user not connected
    }
}
```

---

## 10. Full Traceability — Pipeline Events

### pipeline_events/ Collection (Append-Only)

Every state change is an immutable event. Single source of truth for timelines, dashboards, and audit trails.

| Field | Type | Description |
|-------|------|-------------|
| id | String | Auto-generated event ID |
| pipelineRunId | String | Parent pipeline run |
| sparkId | String | Parent user spark |
| childSparkId | String | Role's child spark (if applicable) |
| userId | String | Spark owner |
| eventType | PipelineEventType | ROLE_STARTED, ROLE_COMPLETED, REWORK_TRIGGERED, etc. |
| role | PdlcRole | Which role this event is about |
| roleIteration | int | 1 = first pass, 2+ = rework |
| metadata | Map<String, Object> | Tokens, cost, duration, artifactId, error, model, engine, commitSha, rejection reason |
| timestamp | Timestamp | When this event occurred |

### PipelineEventType Enum

```java
public enum PipelineEventType {
    PIPELINE_STARTED, PIPELINE_COMPLETED, PIPELINE_FAILED,
    PIPELINE_CANCELLED, PIPELINE_RESUMED,
    ROLE_STARTED, ROLE_COMPLETED, ROLE_REJECTED, ROLE_SKIPPED,
    REWORK_TRIGGERED, REWORK_COMPLETED, REWORK_ESCALATED,
    ARTIFACT_PRODUCED,
    CHECKPOINT_REQUESTED, CHECKPOINT_RESOLVED, CHECKPOINT_TIMEOUT_REMINDER,
    PARALLEL_ROLES_STARTED,
    COST_THRESHOLD_WARNING, COST_CEILING_REACHED
}
```

---

## 11. Knowledge Base Architecture

### Structure (Three Layers)

1. **System prompt** (static, versioned with code) — role identity, instructions, behavioral rules
2. **Few-shot examples** (static, versioned with code) — curated input→output pairs showing expected quality
3. **RAG knowledge** (dynamic, Firestore-backed, embedding-indexed) — grows via retro learnings and manual curation

### pdlc_role_knowledge/ Collection

| Field | Type | Description |
|-------|------|-------------|
| id | String | Knowledge entry ID |
| role | PdlcRole | Which role this knowledge is for |
| category | KnowledgeCategory | BEST_PRACTICE, ANTI_PATTERN, EXAMPLE, RETRO_LEARNING |
| content | String | The knowledge content |
| embedding | List\<Double\> | Vector embedding for semantic retrieval |
| source | String | "retro-{pipelineRunId}", "manual", "bootstrap" |
| relevanceScore | double | Updated by retro based on usefulness |
| createdAt | Timestamp | When added |

### Embedding Strategy

Embeddings computed at write time:
- **Model**: OpenAI `text-embedding-3-small` (1536 dimensions, $0.02/1M tokens) — cheapest high-quality option. Called via existing `client-openai` infrastructure.
- New knowledge entry → generate embedding → store in Firestore document
- At query time: embed the current spark context → vector similarity search → top-K retrieval
- **Search**: Use Firestore's native vector search (available since late 2024, `findNearest()` query) for efficient similarity search without a separate vector database. Knowledge base is per-role (small enough for Firestore vector search limits).

### Knowledge Injection Flow

```
Role execution:
  1. Load static system prompt + few-shot examples (always present)
  2. Embed current spark context (title + description + upstream artifacts summary)
  3. Vector search pdlc_role_knowledge/ for this role (top-K by cosine similarity)
  4. Inject into context: system prompt + knowledge entries + examples + upstream artifacts
```

### Retro Analyst Feedback Loop

After each pipeline completes, the Retro Analyst:
1. Analyzes which roles caused rework and why
2. Identifies patterns (e.g., "Implementer consistently misses error handling")
3. Creates new knowledge entries (category: RETRO_LEARNING) with embeddings
4. Updates relevance scores on existing knowledge entries
5. Computes proficiency scores per role (feeds into admin dashboard)

---

## 12. Data Model — Firestore Collections

### New Collections

| Collection | Type | Purpose |
|-----------|------|---------|
| `pipeline_runs/` | Mutable summary | Current pipeline state, role statuses, aggregated metrics, playbook used |
| `pipeline_events/` | Append-only log | Every state change, artifact, rework, checkpoint. Rework data captured via REWORK_TRIGGERED events with metadata (rejectingRole, targetRole, rejectionReason, reworkIteration) — no separate rework collection needed. |
| `pipeline_artifacts/` | Immutable artifacts | Role outputs (requirements, designs, test results, GitHub refs) |
| `pdlc_role_knowledge/` | Mutable knowledge base | Per-role knowledge with embeddings, grows via retro |

**Note**: `pipeline_rework_events/` was eliminated as redundant — all rework data is captured in `pipeline_events/` via `REWORK_TRIGGERED` / `REWORK_COMPLETED` / `REWORK_ESCALATED` event types with full metadata. The dashboard queries `pipeline_events/ WHERE eventType = REWORK_TRIGGERED` for rework analytics.

### PipelineRun Entity (Full Definition)

```java
public class PipelineRun extends BaseEntity {
    private String id;
    private String sparkId;                         // Parent spark ID
    private String userId;
    private String playbook;                        // Playbook name (e.g., "FULL_PDLC", "BUG_FIX")
    private PipelineTier pipelineTier;
    private PipelineStatus status;
    private List<PdlcRole> activatedRoles;          // Roles to execute
    private PdlcRole currentRole;                   // Currently executing role (null if complete)
    private Map<String, RoleResultSummary> roleResults;  // Role name → {sparkId, status, artifactId, metrics}
    private int reworkCount;                         // Total rework iterations across all roles
    private long totalTokens;
    private BigDecimal totalCost;
    private PdlcClassification classificationResult; // Embedded classifier output
    private GitContext gitContext;                    // Embedded: repo, branch, latest commit
    private String claimedBy;                        // Instance ID for distributed recovery
    private Timestamp claimedAt;                     // When claimed for recovery
    private Timestamp startedAt;
    private Timestamp completedAt;
    private Timestamp createdDate;
    private Timestamp updatedDate;
}

public record RoleResultSummary(
    String childSparkId,
    RoleStatus status,
    String artifactId,
    int iteration,           // 1 = first pass, 2+ = rework
    long tokens,
    BigDecimal cost,
    long durationMs,
    String model,
    String engine
) {}
```

**Note**: All new repository query methods filter by `userId` following the existing `findByField("userId", userId)` pattern. Admin dashboard queries (global aggregates) use admin-scoped endpoints without userId filter.

### Modified Entities

**AgentAuditLog** — add fields:
- `pipelineRunId` (String, nullable)
- `pdlcRole` (PdlcRole, nullable)

**Checkpoint** — add fields:
- `pipelineRunId` (String, nullable) — links checkpoint to pipeline run
- `pdlcRole` (PdlcRole, nullable) — which role triggered the checkpoint
- `checkpointType` (CheckpointType enum: `TACTIC`, `PIPELINE_STAGE`, `COST_CEILING`, `REWORK_ESCALATION`, `CONFIDENCE_GATE`)
Without `pipelineRunId`, the `PipelineRecoveryJob` cannot determine which checkpoint is blocking which pipeline.

**UserConfig** — add fields:
- `syncTimeoutMs` (int, default 3000)
- `forceAsyncAll` (boolean, default false)
- `pipelineCheckpoints` (PipelineCheckpointConfig)
- `pipelineCostCeiling` (BigDecimal, default $50)
- `costWarningThreshold` (double, default 0.8)

**Spark** — add fields:
- `pdlcRole` (PdlcRole, nullable — set on child sparks to identify which role they belong to)
- `executionMode` (ExecutionMode enum: `SYNC`, `ASYNC`, `PIPELINE` — tracks sync-to-async transition)

**AiSdlcStep** — add 5 new enum values (keep enum name as-is):
- `REQUIREMENTS_GATHERING`, `SYSTEM_DESIGN`, `UI_UX_DESIGN`, `SECURITY_REVIEW`, `RETROSPECTIVE`

### New Enums

All enums live in `data-social` module following existing convention (`SparkState`, `CheckpointPolicy`, `AiSdlcStep` are all in `data-social`).

```java
// data-social entity package — canonical location for all enums
public enum PdlcRole {
    PM, RESEARCHER, ARCHITECT, DESIGNER, PLANNER,
    IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST,
    TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST
}

public enum PipelineTier {
    SIMPLE,     // No pipeline — single agent loop
    PLAYBOOK,   // Named workflow with specific roles
    FULL_PDLC   // Complete 12-role pipeline
}

public enum RoleStatus {
    PENDING, EXECUTING, COMPLETED, REJECTED,
    REWORKING, FAILED, ESCALATED, SKIPPED, AWAITING_APPROVAL
}

public enum PipelineStatus {
    CREATED, CLASSIFYING, AWAITING_CONFIRMATION,
    EXECUTING, CHECKPOINT, COMPLETED, FAILED, CANCELLED
}

public enum CheckpointType {
    TACTIC,              // Existing: device tactic checkpoint
    PIPELINE_STAGE,      // NEW: before/after a pipeline role
    COST_CEILING,        // NEW: pipeline cost limit reached
    REWORK_ESCALATION,   // NEW: max rework iterations exceeded
    CONFIDENCE_GATE      // NEW: classifier confidence too low
}
```

### Data Relationships

```
Spark (parent) ──────────┬──→ PipelineRun (1:1)
  │                      │        │
  │                      │        ├──→ pipeline_events/ (1:many, append-only, includes rework events)
  │                      │        ├──→ pipeline_artifacts/ (1:many, one per role)
  │                      │        └──→ roleResults{} (embedded map, denormalized)
  │                      │
  ├──→ Spark (child: PM, pdlcRole=PM)
  ├──→ Spark (child: Architect, pdlcRole=ARCHITECT)
  ├──→ Spark (child: Implement, pdlcRole=IMPLEMENTER)
  ├──→ Spark (child: Review, pdlcRole=REVIEWER)
  ├──→ Spark (child: Implement, pdlcRole=IMPLEMENTER)  ← REWORK
  └──→ ...

Checkpoint (existing) ──→ pipelineRunId + pdlcRole (new fields)

PlaybookRegistry ──→ PlaybookConfig{} (static defaults + future Firestore custom)
pdlc_role_knowledge/ (shared across all users, grows via retro)
```

---

## 13. New Components Summary

### New Services

| Component | Module | Purpose |
|-----------|--------|---------|
| `PdlcClassifierService` | business-agent | Two-stage classifier: depth + playbook selection |
| `PdlcPipelineOrchestrator` | business-agent | Pipeline lifecycle management, playbook-driven |
| `PlaybookRegistry` | business-agent | Registry of playbook configurations |
| `PipelineStateManager` | business-agent | Firestore state persistence for pipeline runs |
| `PipelineEventEmitter` | business-agent | Fan-out events to Firestore + WebSocket + FCM |
| `PipelineArtifactService` | business-agent | Store/retrieve artifacts (Firestore + GitHub refs) |
| `ReworkTracker` | business-agent | Track rework via pipeline_events, enforce max iterations |
| `PipelineWatchdog` | business-agent | Scheduled task checking for role timeouts every 60s |
| `RoleToolFilter` | business-agent | Filter ToolRegistry tools per role config |
| `PipelineRecoveryJob` | business-agent | Resume interrupted pipelines on startup |
| `PdlcRoleRegistry` | business-agent | Registry of all PdlcRoleSkill implementations |
| `PipelineCostManager` | business-agent | Track cumulative cost, enforce ceiling, warn |

### New Role Skills (12)

| Skill Class | Module |
|-------------|--------|
| `PmRoleSkill` | business-agent |
| `ResearcherRoleSkill` | business-agent |
| `ArchitectRoleSkill` | business-agent |
| `DesignerRoleSkill` | business-agent |
| `PlannerRoleSkill` | business-agent |
| `ImplementerRoleSkill` | business-agent |
| `ReviewerRoleSkill` | business-agent |
| `TesterRoleSkill` | business-agent |
| `SecurityAnalystRoleSkill` | business-agent |
| `TechnicalWriterRoleSkill` | business-agent |
| `DevOpsRoleSkill` | business-agent |
| `RetroAnalystRoleSkill` | business-agent |

### New Entities / Repositories

| Entity | Repository | Collection |
|--------|-----------|------------|
| `PipelineRun` | `PipelineRunRepository` | `pipeline_runs/` |
| `PipelineEvent` | `PipelineEventRepository` | `pipeline_events/` |
| `PipelineArtifact` | `PipelineArtifactRepository` | `pipeline_artifacts/` |
| `PdlcRoleKnowledge` | `PdlcRoleKnowledgeRepository` | `pdlc_role_knowledge/` |

### Modified Components

| Component | Change |
|-----------|--------|
| `AgentController` | Add routing: SIMPLE → VoiceAgentService, PLAYBOOK/FULL → orchestrator. Support `playbook` override param. |
| `VoiceAgentService` | Add sync timeout wrapper with async fallback |
| `AgentAuditLog` | Add `pipelineRunId` and `pdlcRole` fields |
| `UserConfig` | Add `syncTimeoutMs`, `forceAsyncAll`, `pipelineCheckpoints`, `pipelineCostCeiling`, `costWarningThreshold` |
| `Spark` | Add `pdlcRole` field for child sparks |
| `Checkpoint` | Add `pipelineRunId`, `pdlcRole`, `checkpointType` fields |
| `AiSdlcStep` | Add 5 new enum values (keep name as-is) |
| `AiSdlcStepDefaults` | Add defaults for 5 new steps |
| `SparkService` | Add `createChildSpark(parentId, role)` method |

### New REST Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/sparks/{sparkId}/pipeline` | GET | Pipeline run status with events, roles, artifacts |
| `/api/sparks/{sparkId}/pipeline/events` | GET | Pipeline event timeline (paginated) |
| `/api/sparks/{sparkId}/pipeline/artifacts/{role}` | GET | Artifact for specific role |
| `/api/sparks/{sparkId}/pipeline/checkpoint/{checkpointId}` | POST | Resolve a checkpoint |
| `/api/playbooks` | GET | List available playbooks |

---

## 14. Module Structure

All new code in existing modules, following established layered architecture:

```
business-agent/
  src/main/java/io/strategiz/social/business/agent/
    pipeline/
      PdlcPipelineOrchestrator.java
      PdlcClassifierService.java
      PlaybookRegistry.java
      PlaybookConfig.java
      PipelineStateManager.java
      PipelineEventEmitter.java
      PipelineArtifactService.java
      PipelineCostManager.java
      PipelineRecoveryJob.java
      ReworkTracker.java
      RoleToolFilter.java
      PdlcRoleRegistry.java
    pipeline/role/
      PdlcRoleSkill.java           (interface)
      RoleContext.java
      RoleResult.java
      GitContext.java
      PmRoleSkill.java
      ResearcherRoleSkill.java
      ArchitectRoleSkill.java
      DesignerRoleSkill.java
      PlannerRoleSkill.java
      ImplementerRoleSkill.java
      ReviewerRoleSkill.java
      TesterRoleSkill.java
      SecurityAnalystRoleSkill.java
      TechnicalWriterRoleSkill.java
      DevOpsRoleSkill.java
      RetroAnalystRoleSkill.java

data-social/
  src/main/java/io/strategiz/social/data/
    entity/
      PipelineRun.java
      PipelineEvent.java
      PipelineArtifact.java
      PdlcRoleKnowledge.java
      PdlcRole.java                (enum — canonical location, referenced by business-agent)
      PipelineEventType.java       (enum)
      PipelineTier.java            (enum)
      PipelineStatus.java          (enum)
      CheckpointType.java          (enum)
      RoleStatus.java              (enum)
    repository/
      PipelineRunRepository.java
      PipelineEventRepository.java
      PipelineArtifactRepository.java
      PdlcRoleKnowledgeRepository.java

service-agent/
  src/main/java/io/strategiz/social/service/agent/controller/
    PipelineController.java
```

---

## 15. Dashboard Data Points (Feeds Admin Console — Future Spec)

The pipeline produces all data needed for the admin console:

| Metric | Source |
|--------|--------|
| Total pipeline runs | `pipeline_runs/` count |
| Success rate | `pipeline_runs/` COMPLETED / total |
| Active pipelines | `pipeline_runs/` where status=EXECUTING |
| Runs by role | `pipeline_events/` ROLE_COMPLETED group by role |
| Avg duration by role | `pipeline_events/` ROLE_COMPLETED metadata.duration |
| Tokens by role (in/out) | `pipeline_events/` ROLE_COMPLETED metadata.tokens |
| Cost by role | `pipeline_events/` ROLE_COMPLETED metadata.cost |
| Rework rate | `pipeline_events/` REWORK_TRIGGERED count / total role executions |
| Rework origin | `pipeline_events/` REWORK_TRIGGERED metadata.rejectingRole |
| First-pass acceptance | iteration=1 COMPLETED / total |
| Pipeline stage funnel | `pipeline_events/` count per role |
| Model distribution | `pipeline_events/` metadata.model |
| Playbook usage | `pipeline_runs/` group by playbook |
| Proficiency score per role | Composite: first-pass rate + avg rework + avg cost + avg duration |
| Checkpoint response time | `checkpoints/` decidedAt - createdAt |
| Cost per pipeline run | `pipeline_runs/` totalCost |
| Cost ceiling hit rate | COST_CEILING_REACHED events / total runs |

---

## 16. Prerequisite: GitHub Client & Agent Skills

The `client-github` module currently has OAuth config only — no API client or agent skills. The PDLC pipeline requires building these before pipeline implementation.

### GitHubClient API Methods (New)

Build `GitHubClient extends BaseHttpClient` in `client-github` with:

| Method | GitHub API | Used By Roles |
|--------|-----------|--------------|
| `createBranch(repo, branchName, baseBranch)` | POST `/repos/{owner}/{repo}/git/refs` | Planner |
| `commitFiles(repo, branch, files, message)` | PUT `/repos/{owner}/{repo}/contents/{path}` | Implementer, Tester, Tech Writer, DevOps |
| `createPullRequest(repo, title, body, head, base)` | POST `/repos/{owner}/{repo}/pulls` | Reviewer |
| `reviewPullRequest(repo, prNumber, action, comments)` | POST `/repos/{owner}/{repo}/pulls/{pr}/reviews` | Reviewer |
| `mergePullRequest(repo, prNumber, method)` | PUT `/repos/{owner}/{repo}/pulls/{pr}/merge` | DevOps |
| `listFiles(repo, path, branch)` | GET `/repos/{owner}/{repo}/contents/{path}` | Researcher, Architect, Reviewer, Security |
| `readFile(repo, path, branch)` | GET `/repos/{owner}/{repo}/contents/{path}` | Researcher, Architect, Reviewer, Security |
| `listBranches(repo)` | GET `/repos/{owner}/{repo}/branches` | Planner, DevOps |
| `searchCode(repo, query)` | GET `/search/code` | Researcher, Security |
| `getPullRequest(repo, prNumber)` | GET `/repos/{owner}/{repo}/pulls/{pr}` | Reviewer |

### GitHub Agent Skills (New — registered in ToolRegistry)

| Skill | Tier | Description |
|-------|------|-------------|
| `github_create_branch` | 1 | Create feature branch for pipeline |
| `github_commit` | 1 | Commit files to branch |
| `github_create_pr` | 1 | Create pull request |
| `github_review_pr` | 1 | Review PR (approve/request changes) |
| `github_merge_pr` | 1 | Merge PR (squash/rebase/merge) |
| `github_read_file` | 0 | Read file contents from repo |
| `github_list_files` | 0 | Browse repo file tree |
| `github_search_code` | 0 | Search code in repo |

---

## 17. Mobile App Response Contract

The existing `AgentCommandResponse` DTO needs new fields to support pipeline responses. The mobile app must handle both response shapes:

```java
// Extended AgentCommandResponse (service-agent)
public class AgentCommandResponse {
    // Existing fields
    private String sparkId;
    private String responseText;
    private List<String> toolsInvoked;
    private String model;
    private boolean success;

    // NEW: pipeline fields (null for non-pipeline sparks)
    private String pipelineRunId;
    private String pipelineTier;        // "SIMPLE", "PLAYBOOK", "FULL_PDLC"
    private String playbook;            // "FULL_PDLC", "BUG_FIX", etc.
    private List<String> activatedRoles;
    private String executionMode;       // "SYNC", "ASYNC", "PIPELINE"
}
```

The mobile app checks `executionMode`:
- `SYNC` → display response immediately (existing behavior)
- `ASYNC` → show "working on it" state, subscribe to WebSocket for result
- `PIPELINE` → show pipeline progress tracker UI, subscribe to WebSocket for role-by-role updates
