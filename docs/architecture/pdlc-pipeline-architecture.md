# PDLC Pipeline Engine — Architecture

**Date**: 2026-03-25
**Status**: Implemented (Wave 1–3 complete; Wave 4 role execution in progress)
**Module**: `business/business-agent` (pipeline services), `data/data-social` (entities), `service/service-agent` (REST)

---

## Overview

The PDLC Pipeline Engine extends single-agent spark execution into a structured multi-role pipeline for complex development work. Where the existing `VoiceAgentService` executes every spark as a single agent loop, the pipeline routes complex code and devops sparks through up to 12 specialized roles — Product Manager, Researcher, Architect, Designer, Planner, Implementer, Reviewer, Tester, Security Analyst, Technical Writer, DevOps Engineer, and Retro Analyst.

PDLC (Product Development Lifecycle) starts at product definition — PM writes a PRD, Researcher gathers context — before touching code. This contrasts with SDLC which assumes requirements already exist.

**Why it exists**: Complex tasks like "build a notification system with WebSocket support" cannot be handled by a single agent loop. They require a structured pipeline of specialists, each with domain expertise, distinct tools, quality gates, and rework feedback loops to produce production-grade output.

---

## System Diagram

```
Chat message → POST /api/agent/command { text, sessionId }
    │
    ├─ SparkService.createSpark()
    ├─ SparkClassifierService.classifySparkType()    [code / social / research / devops / ...]
    └─ PdlcClassifierService.classifyDepth()         [SIMPLE / PLAYBOOK / FULL_PDLC]
         │
         ├─ SIMPLE ──→ VoiceAgentService             [existing sync-with-timeout path]
         │
         └─ PLAYBOOK / FULL_PDLC ──→ PdlcPipelineOrchestrator (async)
              │
              ├─ PipelineStateManager.createRun()    [Firestore: pipeline_runs/]
              ├─ PipelineEventEmitter.emitStarted()  [WebSocket + FCM + Firestore]
              │
              └─ PlaybookRegistry.getPlaybook()
                   │
                   └─ Iterate stages (respecting dependency order):
                        │
                        ├─ [checkpoint?] ──→ pause + notify user
                        │
                        ├─ Single role:
                        │    ├─ SparkService.createChildSpark()
                        │    ├─ PdlcRoleRegistry → PdlcRoleSkill.execute(RoleContext)
                        │    │    └─ AiEngineRouterService.executeStep()
                        │    ├─ PipelineArtifactService.storeArtifact()
                        │    └─ PipelineEventEmitter.emitRoleCompleted()
                        │
                        ├─ Parallel group (e.g., Tester ∥ Security):
                        │    └─ CompletableFuture.allOf(...)
                        │
                        └─ [rejection?] ──→ ReworkTracker → re-run target role
                             └─ max 3 iterations → escalate to user checkpoint
              │
              ├─ RetroAnalystRoleSkill (always runs post-pipeline)
              ├─ PipelineStateManager.markCompleted()
              └─ SparkService.markCloudCompleted()
```

### FULL_PDLC Execution Graph

```
PM → Researcher → Architect → Designer → Planner → Implementer → Reviewer
                                                                      ↓
                                                               ┌──────┴──────┐
                                                            Tester      Security
                                                               └──────┬──────┘
                                                                      ↓
                                                               ┌──────┴──────┐
                                                           Tech Writer    DevOps
                                                               └──────┬──────┘
                                                                      ↓
                                                                    Retro
```

---

## Components

### Classification

**`PdlcClassifierService`** — Stage 2 depth classifier. Only runs for `code` and `devops` spark types. Uses a six-dimension rubric (scope 25%, risk 20%, domain breadth 15%, integration surface 15%, testing complexity 15%, reversibility 10%) with chain-of-thought reasoning to select a pipeline tier and named playbook. Falls back to SIMPLE on any engine failure.

### Orchestration

**`PdlcPipelineOrchestrator`** — Core pipeline lifecycle engine. Reads the playbook config, iterates through stages in dependency order, handles parallel groups via `CompletableFuture.allOf`, creates child sparks per role, delegates execution to `PdlcRoleExecutor`, stores artifacts, and aggregates final cost metrics. Runs `@Async("pdlcPipelineExecutor")` on a dedicated thread pool (4 core / 8 max / 50 queue).

**`PlaybookRegistry`** — In-memory registry of `PlaybookConfig` instances initialized at startup via `@PostConstruct`. Stores all 8 system playbooks. Designed to load user-defined playbooks from Firestore in the future without architecture changes.

### State and Events

**`PipelineStateManager`** — Single point of entry for all `PipelineRun` mutations. Manages status transitions (CREATED → EXECUTING → CHECKPOINT → COMPLETED / FAILED), role status updates, rework count increments, and claim management for distributed recovery.

**`PipelineEventEmitter`** — Fan-out service for all pipeline state changes. Every event is (1) persisted as an immutable document to `pipeline_events/`, (2) denormalized into the `PipelineRun` summary, (3) pushed via WebSocket to the user's active session, and (4) pushed via FCM for key milestones when the user is not connected.

### Artifacts and Knowledge

**`PipelineArtifactService`** — Stores and retrieves role output artifacts. Non-code artifacts (requirements, designs, test results, security reports) stored as Firestore documents in `pipeline_artifacts/`. Code artifacts stored in GitHub and referenced by `{repo, branch, commitSha, filesChanged[]}`.

**`KnowledgeBaseService`** — Manages the `pdlc_role_knowledge/` collection. Provides semantic retrieval via Firestore vector search (`findNearest()`). Writes new entries from Retro Analyst feedback with embeddings computed via OpenAI `text-embedding-3-small`.

### Infrastructure

**`ReworkTracker`** — Tracks rework loops per role via `pipeline_events/` (no separate collection needed). Enforces the 3-iteration max; escalates to a user checkpoint on exhaustion. All rework data is captured as `REWORK_TRIGGERED` / `REWORK_COMPLETED` / `REWORK_ESCALATED` events with full metadata.

**`PipelineCostManager`** — Tracks cumulative cost per pipeline run against two limits: `pipelineCostCeiling` (default $50, per-pipeline) and `spendingLimit` (monthly aggregate, default $0). Emits `COST_THRESHOLD_WARNING` at 80% of ceiling and `COST_CEILING_REACHED` at 100%, pausing the pipeline.

**`PipelineRecoveryJob`** — Runs `@PostConstruct` on application startup. Queries `pipeline_runs` where `status = EXECUTING`, claims unclaimed or stale runs (claim timeout: 30 minutes) via a compare-and-swap on `claimedBy`, and emits `PIPELINE_RESUMED`. Handles Cloud Run multi-instance rolling updates.

**`PipelineWatchdog`** — `@Scheduled(fixedDelay = 60_000)` task. Checks all EXECUTING pipeline runs for roles that have exceeded their playbook-configured timeout. Emits `REWORK_ESCALATED` and logs a warning; full timeout action (marking FAILED, escalating to checkpoint) is wired in a later wave.

**`RoleToolFilter`** — Filters the global `ToolRegistry` to the tool subset allowed for a given PDLC role. Each role skill declares `getAvailableTools()` and this filter enforces the boundary.

**`PdlcRoleRegistry`** — Spring-managed registry of all `PdlcRoleSkill` implementations. Resolves the correct skill instance by `PdlcRole` enum value.

---

## 12 PDLC Roles

| # | Role (Enum) | PDLC Step | Engine | Model | Key Tools | Can Reject To |
|---|-------------|-----------|--------|-------|-----------|---------------|
| 1 | PM | REQUIREMENTS_GATHERING | anthropic-agentic | sonnet | search_web, browse_web | — |
| 2 | RESEARCHER | WEB_RESEARCH + CODE_ANALYSIS | codex-cli / anthropic-agentic | sonnet | search_web, browse_web, github | — |
| 3 | ARCHITECT | SYSTEM_DESIGN | anthropic-agentic | opus | search_web, browse_web, github | — |
| 4 | DESIGNER | UI_UX_DESIGN | anthropic-agentic | sonnet | browse_web, image_analysis | — |
| 5 | PLANNER | TASK_DECOMPOSITION | anthropic-agentic | sonnet | github | — |
| 6 | IMPLEMENTER | CODE_GENERATION | claude-code-cli | opus | full toolset + github | — |
| 7 | REVIEWER | CODE_REVIEW | anthropic-agentic | sonnet | github | IMPLEMENTER |
| 8 | TESTER | TEST_GENERATION + TEST_EXECUTION | claude-code-cli | sonnet | full toolset + github | IMPLEMENTER |
| 9 | SECURITY_ANALYST | SECURITY_REVIEW | anthropic-agentic | opus | browse_web, search_web, github | IMPLEMENTER |
| 10 | TECHNICAL_WRITER | DOCUMENTATION | anthropic-agentic | sonnet | github | — |
| 11 | DEVOPS | DEPLOYMENT_SCRIPT | claude-code-cli | sonnet | full toolset + github | — |
| 12 | RETRO_ANALYST | RETROSPECTIVE | anthropic-agentic | sonnet | none (analysis only) | — |

**Notes**:
- DESIGNER is skipped for backend-only work (classifier sets it as not activated).
- RETRO_ANALYST always runs as a post-pipeline hook regardless of playbook config — it is not a configurable stage.
- `claude-code-cli` roles (IMPLEMENTER, TESTER, DEVOPS) require the Claude Code CLI installed in the Cloud Run container image.

---

## 8 Playbooks

| Playbook | Roles (in order) | Parallel Groups | When to Use |
|----------|-----------------|-----------------|-------------|
| **FULL_PDLC** | PM → Researcher → Architect → Designer → Planner → Implementer → Reviewer → Tester ∥ Security → Tech Writer ∥ DevOps → Retro | Tester ∥ Security, Tech Writer ∥ DevOps | New systems, major features, multi-component work |
| **BUG_FIX** | Researcher → Implementer → Reviewer → Tester | — | Known bug needing diagnosis and fix |
| **SMALL_FEATURE** | PM → Implementer → Reviewer → Tester | — | Clear, bounded feature fitting a few files |
| **REFACTOR** | Researcher → Architect → Implementer → Reviewer → Tester | — | Restructuring existing code with design consideration |
| **INFRA_CHANGE** | Architect → DevOps → Security | — | CI/CD, deployment, infrastructure only |
| **DOCS_ONLY** | Researcher → Technical Writer | — | Documentation updates |
| **UI_CHANGE** | Designer → Implementer → Reviewer → Tester | — | Frontend-only work |
| **SECURITY_PATCH** | Security → Researcher → Implementer → Tester → DevOps | — | Vulnerability remediation (Security leads for urgency) |

Playbooks are data-driven `PlaybookConfig` records, not hardcoded logic. Each has its own role ordering and dependency graph — not merely a subset of FULL_PDLC.

---

## Async Execution

### Timeout-Based Hybrid Model

| Spark Type | Execution Mode | Behavior |
|-----------|---------------|----------|
| Non-code (social, creative, research) | Sync with timeout | Attempt sync; if exceeds `syncTimeoutMs`, switch to async |
| Code — SIMPLE | Sync with timeout | Same timeout behavior |
| Code — PLAYBOOK or FULL_PDLC | Always async | Return immediately, stream events |

Default `syncTimeoutMs` = 3000ms (range: 1000–30000ms, configurable in `UserConfig`). When the sync timeout fires, the underlying execution continues running; the spark's `executionMode` field atomically flips to `ASYNC`, and results are delivered via WebSocket/FCM rather than the HTTP response.

### Thread Pool

```
Bean: pdlcPipelineExecutor
Core pool size:  4   (4 concurrent pipelines)
Max pool size:   8   (burst to 8)
Queue capacity: 50   (50 can queue)
Thread prefix:  pdlc-pipeline-
```

A separate `simpleSparkExecutor` pool serves the sync-with-timeout wrapper for SIMPLE sparks.

### Real-Time Progress Channels

1. **WebSocket** — All `PipelineEventType` events pushed to user's active session in real time.
2. **FCM** — Key milestones when user is not connected: `PIPELINE_STARTED`, `CHECKPOINT_REQUESTED`, `PIPELINE_COMPLETED`, `PIPELINE_FAILED`.
3. **Polling API** — `GET /api/sparks/{sparkId}/pipeline` returns full `PipelineRun` with role statuses, events, artifacts, and metrics.

---

## Data Model

### Firestore Collections (New)

| Collection | Type | Purpose |
|-----------|------|---------|
| `pipeline_runs/` | Mutable summary | Current pipeline state, role statuses, aggregated metrics, playbook, git context |
| `pipeline_events/` | Append-only log | Every state change: role start/complete, rework, checkpoint, cost warnings |
| `pipeline_artifacts/` | Immutable artifacts | Role output documents (requirements, designs, test results, GitHub refs) |
| `pdlc_role_knowledge/` | Mutable knowledge base | Per-role knowledge entries with vector embeddings, grows via Retro feedback |

### Artifact Storage Strategy

Code artifacts live in GitHub, not Firestore. The pipeline creates one feature branch per run (`pdlc/spark-{id}-{slug}`):

| Artifact Type | Storage | Firestore Reference |
|--------------|---------|---------------------|
| Source code, deploy configs | GitHub (commits on feature branch) | `{ repo, branch, commitSha, filesChanged[] }` |
| Pull request / code review | GitHub (PR) | `{ repo, prNumber, prUrl, reviewState }` |
| Requirements, architecture, test results, security report | Firestore `pipeline_artifacts/` | Full content in document |

### Key Entity Additions

**`PipelineRun`** — Tracks the full pipeline lifecycle: `status`, `activatedRoles`, `currentRole`, `roleResults` (embedded map of `RoleResultSummary`), `reworkCount`, `totalTokens`, `totalCost`, `gitContext`, `claimedBy`/`claimedAt` (distributed recovery).

**`Spark` (modified)** — Two new fields: `pdlcRole` (which role this child spark belongs to) and `executionMode` (`SYNC`, `ASYNC`, `PIPELINE`).

**`UserConfig` (modified)** — New fields: `syncTimeoutMs`, `forceAsyncAll`, `pipelineCheckpoints` (`PipelineCheckpointConfig`), `pipelineCostCeiling` (default $50), `costWarningThreshold` (default 0.8).

**`Checkpoint` (modified)** — New fields: `pipelineRunId`, `pdlcRole`, `checkpointType` (`TACTIC`, `PIPELINE_STAGE`, `COST_CEILING`, `REWORK_ESCALATION`, `CONFIDENCE_GATE`).

### Data Relationships

```
Spark (parent) ────────────────────┬──→ PipelineRun (1:1)
  │                                 │        ├──→ pipeline_events/       (1:many, append-only)
  ├──→ Spark (child, pdlcRole=PM)  │        ├──→ pipeline_artifacts/    (1:many, one per role)
  ├──→ Spark (child, pdlcRole=ARCHITECT)     └──→ roleResults{}          (embedded map)
  ├──→ Spark (child, pdlcRole=IMPLEMENTER)
  ├──→ Spark (child, pdlcRole=REVIEWER)
  └──→ Spark (child, pdlcRole=IMPLEMENTER)   ← REWORK

Checkpoint ──→ pipelineRunId + pdlcRole    (existing collection, new fields)
pdlc_role_knowledge/ ──→ per role, shared across users, grows via Retro
```

---

## Rework Mechanics

When a validation role (REVIEWER, TESTER, SECURITY_ANALYST) rejects:

1. `PipelineRun.reworkCount` increments.
2. A `REWORK_TRIGGERED` event is emitted with `{ rejectingRole, targetRole, rejectionReason, reworkIteration }`.
3. A new child spark is created for the target role with `reworkFeedback` and `reworkIteration` in `RoleContext`.
4. The target role re-executes with the feedback.
5. The rejecting role re-validates.

**Maximum rework per role**: 3 iterations (configurable). After 3 failures, `REWORK_ESCALATED` is emitted and the pipeline pauses at a mandatory user checkpoint. This checkpoint cannot be disabled.

**Rework example:**
```
Implementer (spark-101) → commits code
Reviewer (spark-102)    → REJECTS: "Missing error handling on WebSocket disconnect"
Implementer (spark-103, iteration=2, feedback="Missing error handling...") → commits fix
Reviewer (spark-104)    → ACCEPTS
Tester ...
```

---

## Cost Management

Two limits are checked in order at every role transition:

1. **`spendingLimit`** (monthly aggregate, default $0) — pipelines are blocked until the user sets a non-zero value. This is intentional: the user must explicitly opt into AI spend.
2. **`pipelineCostCeiling`** (per-pipeline, default $50) — secondary guardrail within the monthly budget.

The pipeline is blocked if either limit would be exceeded. `PipelineCostManager` emits:
- `COST_THRESHOLD_WARNING` at `costWarningThreshold * pipelineCostCeiling` (default 80%)
- `COST_CEILING_REACHED` at 100% → pipeline pauses, user checkpoint: "Pipeline reached $X ceiling. Continue or abort?"

---

## Human Stewardship

Checkpoints pause the pipeline and require user action (APPROVED / REJECTED / MODIFIED). User can respond via mobile app, push notification deep link, or agent chat.

### Default Checkpoint Configuration

| Position | Default | Rationale |
|----------|---------|-----------|
| After PM (requirements) | ON | Confirm scope before design work begins |
| After Architect (design) | ON | Confirm architecture before implementation |
| After Reviewer (code review) | OFF | Trust automated review |
| After Tester (test results) | OFF | Trust test results |
| After Security (findings) | ON (medium+ severity) | Always surface security issues |
| Before DevOps (deployment) | ON | Confirm before deploying |
| Rework escalation (3 failures) | ALWAYS ON | Cannot be disabled — safety valve |

**Checkpoint timeout**: 24 hours. Reminders sent at 1h, 4h, 12h. After timeout: pauses indefinitely (no auto-approve). Pipeline state is Firestore-backed and survives server restarts.

---

## Two-Stage Classification

**Stage 1** (existing): `SparkClassifierService` classifies spark type via Haiku (code, social, research, devops, creative, data).

**Stage 2** (new): `PdlcClassifierService` evaluates depth for `code` and `devops` sparks only. All other types return SIMPLE immediately.

### Confidence Gating

| Confidence | Behavior |
|-----------|----------|
| > 0.85 | Auto-route, inform user |
| 0.50–0.85 | Propose via checkpoint: "This looks like a major feature. Run full PDLC pipeline?" |
| < 0.50 | Ask user directly: "How should I handle this?" |

### User Override

Users can override the classifier result:
- **Via chat**: "Run FULL_PDLC on this" or "Use the bug fix playbook"
- **Via API**: `POST /api/agent/command { text, playbook: "BUG_FIX" }`
- **Via settings**: Default playbook preference per spark type in `UserConfig`

The classifier still runs for analytics and comparison even when overridden.

---

## API Endpoints

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/api/sparks/{sparkId}/pipeline` | GET | RequireAuth | Pipeline run status, role results, aggregated metrics |
| `/api/sparks/{sparkId}/pipeline/events` | GET | RequireAuth | Pipeline event timeline (paginated via `limit`/`offset`) |
| `/api/sparks/{sparkId}/pipeline/artifacts/{role}` | GET | RequireAuth | Artifact produced by a specific role |
| `/api/sparks/{sparkId}/pipeline/checkpoint/{checkpointId}` | POST | RequireAuth | Resolve a checkpoint (APPROVED / REJECTED / MODIFIED) |
| `/api/playbooks` | GET | RequireAuth | List all available playbooks with stage configs |

All endpoints enforce ownership: returns 403 if `pipelineRun.userId != authenticatedUser.userId`.

---

## Deployment

### Cloud Run Requirements

Claude Code CLI must be installed in the Cloud Run container image. Roles that need autonomous coding (IMPLEMENTER, TESTER, DEVOPS) spawn it as a subprocess via `ClaudeCodeAiEngine`, identical to how device agents use it. The Cloud Build config (`deployment/cloudbuild/cloudbuild-prod.yaml`) must include CLI installation.

If the Claude Code CLI is unavailable, fallback engines are configured per `AiSdlcStepDefaults` in Firestore — typically `anthropic-agentic` for IMPLEMENTER.

### GitHub Connectivity Prerequisite

The pipeline requires the user to have granted repo access via the `manage_repo` agent skill. GitHub OAuth tokens are stored in `social_integrations/` (existing collection) and accessed via `client-github`. Read/write access to the target repository is required for code commits, branch creation, and PR operations.

---

## Related Docs

- **Design spec**: `docs/superpowers/specs/2026-03-24-pdlc-pipeline-engine-design.md`
- **Cloud orchestrator**: `docs/architecture/cloud-orchestrator-architecture.md`
- **Device agent**: `docs/architecture/device-agent-architecture.md`
- **AI engine framework**: see memory `ai-engine-framework.md`
