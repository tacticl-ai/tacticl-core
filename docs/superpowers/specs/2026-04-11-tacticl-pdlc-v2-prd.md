# Tacticl PDLC v2 — Product Requirements Document

**Date:** 2026-04-11
**Status:** Draft
**Author:** Gabriel Jimenez
**Related docs:** `2026-04-11-tacticl-pdlc-v2-sad.md` (architecture), `2026-03-24-pdlc-pipeline-engine-design.md` (v1 design, superseded)

---

## 1. Executive Summary

Tacticl v1 ships a Product Development Lifecycle (PDLC) pipeline that runs 12 specialized AI agents inside a single Spring Boot JVM on Cloud Run. It works, but it has architectural ceilings that prevent it from reaching the quality bar required for production software delivery: shared thread pools, hand-coded orchestration, no workspace isolation, no cross-run memory, limited rework iterations, and tight coupling between pipeline logic and LLM provider code.

Tacticl PDLC v2 rebuilds the pipeline on top of **cidadel-ai-arbiter**, Tacticl's new agent execution factory: each role runs in its own Docker container on Hetzner with a pre-assembled workspace, Claude Code CLI as the execution engine, aggressive multi-candidate generation and rework loops, scoped vector search over past successful runs, a 4-layer knowledge system, and between-agent user checkpoints.

The goal is not speed of delivery or cost minimization. **The goal is the highest possible quality of generated code — production-ready, tested, documented, secure, and consistent with the target codebase's conventions.** Every design choice in v2 prioritizes quality, even at significant cost and latency overhead.

## 2. Problem Statement

### 2.1 What's Broken in v1

**Architecture**
- All 12 roles execute in one JVM thread pool (`pdlcPipelineExecutor`, 4 core / 8 max). Concurrent pipelines compete for CPU, memory, and Anthropic rate limits at the JVM level.
- Zero workspace isolation — every pipeline shares the same filesystem and process. Two concurrent IMPLEMENTER runs on different repos theoretically could clobber each other. The safety net is "don't run concurrent PDLC for now."
- Pipeline orchestration is hand-coded Java. Adding a new role, a new playbook, or a new rework policy requires code changes, compilation, and deploy.
- LLM routing happens through `AiEngineRouterService` with provider adapters wired via Spring beans. Each provider is a separate Gradle module. The router code is intertwined with cost tracking, rework detection, and event emission.

**Knowledge & Memory**
- No cross-run memory. Every pipeline starts from scratch. An IMPLEMENTER that solved a similar problem last week has no way to recall that solution.
- `KnowledgeBaseService` exists but uses Firestore vector search (being decommissioned with the Hetzner migration) and is populated only by the Retro Analyst — most roles have no authored knowledge curated for them.
- Agents waste turns re-discovering codebase conventions every run. The same file imports, naming patterns, and architectural decisions get re-derived over and over.

**Execution Engine**
- Claude Code CLI is invoked as a subprocess from Java via `client-claude-code`. Subprocess spawn adds 5-11 seconds of overhead per invocation. The persistent session mode exists but the architecture fundamentally wants the CLI to live inside a Node.js runtime, not Java.
- CLI execution happens on the same Cloud Run instance that serves HTTP requests. A long-running CODE_GENERATION session competes with spark classification, dashboard queries, and device WebSocket handling.

**Quality Ceiling**
- Rework loops cap at 3 iterations. Complex tasks that need 5-8 iterations are marked failed.
- IMPLEMENTER generates one candidate. If that candidate has subtle bugs the REVIEWER doesn't catch, they ship.
- Tests are generated after implementation (not TDD). Tests become rubber-stamps rather than specifications.
- No mandatory multi-critic consensus. A single REVIEWER can approve flawed code.
- No REFACTOR pass after tests pass — functional code is considered "done."

**Operational**
- Cloud Run Java image is ~400MB and includes Claude Code CLI, Node.js runtime, Playwright, and every LLM client. Cold starts are slow, deploys are slow.
- Firestore PDLC state means queries are expensive and limited. No aggregations, no efficient cross-pipeline analytics.
- `PipelineRecoveryJob` runs on startup but pipeline state lives in Firestore subcollections that are hard to reason about.

### 2.2 Why a Refactor, Not a Fix

The v1 problems aren't bugs — they're consequences of the architecture. You can't bolt workspace isolation onto an in-JVM pipeline. You can't make Java a good host for Claude Code CLI. You can't make single-candidate generation produce best-of-3 quality by adding a retry.

The arbiter factory pattern — already proven in strategiz v2-generative — solves all of these at the architecture level. Tacticl v2 adopts it wholesale for PDLC work.

**PDLC v2 replaces v1 entirely.** There is no hybrid state. When v2 is deployed, the Java `PdlcPipelineOrchestrator`, `PipelineStateManager`, `PipelineEventEmitter`, `PipelineArtifactService`, `ReworkTracker`, `PipelineCostManager`, `PipelineRecoveryJob`, and `PipelineWatchdog` are deleted. Firestore `pipeline_runs/`, `pipeline_events/`, and `pipeline_artifacts/` collections are migrated to MongoDB and then removed from Firestore. A feature flag `pdlc.v2.enabled` gates the migration. When enabled, all new pipeline submissions go to v2. Existing running v1 pipelines are allowed to complete before the flag flips.

## 3. Vision & Goals

### 3.1 Vision

Tacticl is a factory for building software. A user describes what they want in natural language. The factory produces production-ready code — designed, implemented, tested, reviewed, documented, and deployed — using 12 specialized AI agents coordinated through a configurable pipeline. The factory improves over time through a weekly learning loop. The user is in control at every checkpoint.

### 3.2 Primary Goals

| # | Goal | How we measure it |
|---|------|-------------------|
| G1 | **Production-ready code output** | % of FULL_PDLC pipelines that produce PRs passing all CI checks on first run |
| G2 | **Isolation & safety** | Zero cross-pipeline context contamination; per-run filesystem isolation |
| G3 | **Configurable without code changes** | New pipelines, roles, and knowledge can be added by editing JSON + markdown, no Java deploys |
| G4 | **Cross-run memory** | Agents can recall and reuse patterns from past successful pipelines |
| G5 | **Quality through multiplicity** | Multi-candidate generation, multi-critic consensus, aggressive rework, TDD enforcement |
| G6 | **User control at decision points** | Mandatory checkpoints at key phases; pipeline pauses until user approves |
| G7 | **Self-improving** | Weekly retro loop identifies and proposes learnings; human approves; next runs benefit |

### 3.3 Secondary Goals

| # | Goal |
|---|------|
| G8 | Unified infrastructure with strategiz on Hetzner (shared arbiter, shared MongoDB, shared Qdrant) |
| G9 | Clean separation between tacticl-core (control plane) and arbiter (execution plane) |
| G10 | Every LLM call observable via OpenTelemetry traces, cost tracked per role per pipeline |
| G11 | Smooth migration path from v1 — both can run concurrently during rollout |

## 4. Non-Goals

Explicitly **not** included in v2 scope:

- **Non-PDLC workloads** — social posting, chat/spark execution, video generation, research tasks stay in Java tacticl-core for now. They migrate in later phases.
- **Device-side execution** — desktop daemon tactics decomposition is a separate execution plane. PDLC v2 is server-side only.
- **Multi-tenant SaaS** — v2 targets single-user and small-team use. Multi-tenant isolation beyond per-user workspace namespacing is v3.
- **General-purpose agent framework** — v2 is specifically for PDLC. It's not "run any agent on any task."
- **IDE integration** — no VSCode extension, no JetBrains plugin. Chat, voice, and GitHub webhook are the only triggers.
- **Real-time collaborative editing** — agents produce PRs; humans review async.
- **Cost minimization** — quality is the priority. Cost controls exist (per-pipeline ceiling, monthly spend limit) but not at the expense of output quality.

## 5. Users & Personas

### 5.1 Primary Persona: Solo Developer / Founder

- Builds products alone or on a small team
- Uses tacticl to offload implementation work while staying in architectural control
- Reviews every PR before merging
- Cares about code quality, maintainability, test coverage
- Willing to wait minutes (or hours) for a high-quality result
- Has 1-10 repos connected to tacticl

### 5.2 Secondary Persona: Product Manager

- Files feature requests as GitHub issues
- Tacticl picks up the issue via webhook and runs PDLC
- PM reviews the resulting PR without needing to write code themselves
- Uses checkpoints to steer the pipeline when the AI's direction needs adjustment

### 5.3 Tertiary Persona: Engineering Lead

- Sets org-wide quality bars via configurable checkpoints, rework limits, and required critics
- Reviews weekly retro outputs, approves learnings
- Monitors pipeline cost/quality metrics across the team

## 6. Use Cases

### 6.1 UC1: Solo Developer Implements a Feature (happy path)

**Trigger:** Developer sends chat message: "Add a password reset flow to the auth service. Send a reset link via SendGrid, verify the token, let the user set a new password."

**Flow:**
1. `SparkClassifierService` classifies as `code` spark
2. `PdlcClassifierService` selects `FULL_PDLC` playbook
3. Tacticl-core submits pipeline to arbiter via gRPC
4. Arbiter runs PRODUCT phase:
   - PM agent writes a PRD based on the request
   - RESEARCHER agent investigates existing auth code, SendGrid integration
5. **Checkpoint:** User reviews the PRD, approves
6. Arbiter runs DESIGN phase:
   - ARCHITECT designs the password reset flow, API endpoints, data model
   - PLANNER breaks it into implementation tasks
7. **Checkpoint:** User reviews the design, approves
8. Arbiter runs DEVELOPMENT phase:
   - IMPLEMENTER generates 3 candidate implementations in parallel
   - Internal CRITIC sub-agent ranks them, selects best
   - Result: code changes pushed to a branch
9. Arbiter runs TEST phase (parallel):
   - TESTER writes tests (TDD enforced — tests written before implementation confirmed)
   - REVIEWER reviews the implementation
   - SECURITY_ANALYST reviews for vulnerabilities
   - All three must approve; any rejection triggers rework (up to 5 iterations)
10. Arbiter runs REFACTOR pass (implicit in DEVELOPMENT phase):
    - After TEST phase approves, IMPLEMENTER runs a refactor iteration for code quality
    - REVIEWER validates refactor didn't break anything
11. Arbiter runs DEPLOY phase:
    - TECHNICAL_WRITER writes documentation
    - DEVOPS creates deployment scripts if needed
12. RETRO_ANALYST runs post-pipeline:
    - Analyzes what went well, what failed, proposes learnings
13. Arbiter creates a PR on the user's GitHub repo
14. Tacticl-core sends push notification: "Your password reset flow PR is ready"
15. User reviews, merges, done

### 6.2 UC2: Bug Fix from GitHub Issue

**Trigger:** A GitHub issue is filed: "Login endpoint returns 500 when username contains emoji"

**Flow:**
1. GitHub webhook fires → tacticl-core receives
2. Tacticl-core creates a spark from the issue
3. `PdlcClassifierService` selects `BUG_FIX` playbook
4. Arbiter runs BUG_FIX pipeline (subset of full PDLC):
   - RESEARCHER reproduces the bug, identifies root cause
   - IMPLEMENTER applies fix (multi-candidate generation)
   - TESTER writes regression test that reproduces the bug and verifies the fix
   - REVIEWER approves
5. Arbiter creates PR linked to the original issue
6. Tacticl-core comments on the issue with a link to the PR

### 6.3 UC3: User Rejects an Architecture

**Trigger:** Same as UC1, but at step 7 user rejects the design

**Flow:**
1. User provides feedback: "Don't use SendGrid, we use Mailgun. Also tokens should expire in 15 minutes, not 1 hour."
2. Tacticl-core forwards feedback to arbiter as checkpoint response with `status=rework`
3. Arbiter re-dispatches ARCHITECT with original prompt + user feedback in `context/checkpoint-feedback.md`
4. ARCHITECT produces new design
5. Pipeline pauses at checkpoint again
6. User reviews, approves, pipeline continues

### 6.4 UC4: Rework Loop Triggered by Failing Tests

**Trigger:** UC1 reaches TEST phase, TESTER writes tests, IMPLEMENTER's code fails 2 of 8 tests

**Flow:**
1. TESTER marks rework needed, writes feedback to `results/test-failures.md`
2. Shell detects `shouldRework=true`, increments rework counter
3. Shell re-dispatches IMPLEMENTER with:
   - Original prompt
   - Prior implementation in `context/prior-implementation.md`
   - Test failures in `context/test-failures.md`
4. IMPLEMENTER regenerates (new multi-candidate pass)
5. TESTER re-runs tests
6. Loop continues until tests pass OR rework counter hits 5
7. If rework exhausted → `REWORK_ESCALATED` event → user checkpoint

### 6.5 UC5: Retro Learning Loop

**Trigger:** Weekly cron on Sunday

**Flow:**
1. RETRO agent (long-running container or orchestrator session) scans past week's archived workspaces
2. Identifies patterns: "47 pipelines had IMPLEMENTER wasting tokens re-discovering that we use Jackson 3, not 2"
3. Proposes learning: "Add to IMPLEMENTER authored knowledge: 'This codebase uses Jackson 3 (`tools.jackson.*`). Do not use Jackson 2 imports.'"
4. Writes proposal to MongoDB `agent_knowledge` with `status=proposed`
5. Eng lead reviews proposals in the retro dashboard
6. Approves → `status=approved`
7. Next workspace assembly for IMPLEMENTER includes the learning in `workspace/knowledge/learned/patterns.md`

## 7. Functional Requirements

### 7.1 Pipeline Execution

- **FR1.1:** System MUST run the 12 PDLC roles as isolated Docker containers on Hetzner, one container per role dispatch.
- **FR1.2:** Each container MUST receive a fresh, pre-assembled workspace via bind mount before starting.
- **FR1.3:** Containers MUST run Claude Code CLI (`claude -p "$BOOT_CONTENT" --dangerously-skip-permissions --max-turns ...`) as the execution engine.
- **FR1.4:** Container images MUST be generic (single `cidadel-agent` image). Agent identity comes from `boot.md` + `CLAUDE.md`, not the container type.
- **FR1.5:** Workspaces MUST be isolated — two concurrent pipelines share zero filesystem state.
- **FR1.6:** Failed or completed containers MUST be destroyed after result extraction; workspaces MUST be archived to `/opt/cidadel/agent-workspaces/archive/...` for 30 days.
- **FR1.7:** Pipeline execution MUST be asynchronous — the gRPC `SubmitPipeline` call returns in under 2 seconds with a pipeline ID. Results arrive later via callback.

### 7.2 Role Orchestration

- **FR2.1:** Pipelines MUST be defined as JSON files in a GCS/filesystem registry, not hardcoded Java.
- **FR2.2:** Each role definition MUST include: agent type, model, max_turns, resource class (light/medium/heavy), boot template path, CLAUDE.md path, knowledge files, pre-population spec.
- **FR2.3:** The shell MUST dispatch roles according to the pipeline's dependency graph, supporting sequential and parallel groups.
- **FR2.4:** The shell MUST pass results from completed roles to subsequent roles via `workspace/context/prior-agent-output.md`.
- **FR2.5:** The shell MUST support both container-handled agents (spawn Claude Code container) and shell-handled agents (deterministic compute, no LLM).

### 7.3 Knowledge System (4 Layers)

- **FR3.1:** Layer 1 (Live Repo) — shell MUST clone the user's repo into `workspace/context/repo/` before container starts, fresh on every run.
- **FR3.2:** Layer 2 (Authored Knowledge) — shell MUST populate `workspace/knowledge/authored/` with curated markdown files from the registry, scoped per agent type.
- **FR3.3:** Layer 3 (Learned Patterns) — shell MUST query MongoDB `agent_knowledge` for approved learnings matching `product=tacticl` AND `agent_types=[currentRoleType]`, render into `workspace/knowledge/learned/patterns.md`.
- **FR3.4:** Layer 4 (Past Runs Vector Search) — a Qdrant MCP server MUST be available inside agent containers. Agents can call `find_similar_runs(query, top_k)` to retrieve semantically similar past successful pipeline runs.
- **FR3.5:** Past runs indexing — Retro agent MUST index successful pipelines into Qdrant after completion using Voyage-code-3 embeddings. Index includes role prompt, role output, outcome metadata.
- **FR3.6:** Vector search MUST NOT be used for exploring the user's live repo — agents use native Grep/Glob/Read/Bash for that.

### 7.4 Quality Multipliers

- **FR4.1: Multi-candidate generation** — IMPLEMENTER role MUST generate 3 parallel candidate implementations by default. A CRITIC sub-agent selects the best. Per-role configuration allows setting candidate count (1-5).
- **FR4.2: Rework loops** — Each role supports up to 5 rework iterations (configurable per playbook). Rework is triggered when:
  - REVIEWER rejects an implementation
  - TESTER reports failing tests
  - SECURITY_ANALYST reports vulnerabilities
  - Agent itself signals `shouldRework=true` in results
- **FR4.3: Multi-critic consensus** — TEST phase MUST run REVIEWER, TESTER, and SECURITY_ANALYST. All three must approve before DEPLOY phase starts. Any rejection triggers rework on IMPLEMENTER.
- **FR4.4: Test-first enforcement** — In FULL_PDLC playbook, TESTER MUST write failing tests BEFORE IMPLEMENTER writes code. IMPLEMENTER then writes code to make tests pass. This is TDD enforcement at the pipeline level.
- **FR4.5: REFACTOR pass** — After TEST phase approves, IMPLEMENTER runs a dedicated REFACTOR iteration focused on code quality (readability, duplication, naming, structure) without changing behavior. REVIEWER validates no regression.
- **FR4.6: Sub-agent self-correction** — Container agents MAY spawn sub-agents for validation (e.g., ARCHITECT spawns a sub-agent critic to check spec before marking complete). Sub-agents share parent's context.
- **FR4.7: High max_turns** — Default max_turns per role is 100 (not 10 or 30 as in v1). No hard cap on thinking.
- **FR4.8: Best models** — Opus for creative/judgment roles (ARCHITECT, SECURITY_ANALYST, CRITIC, RETRO). Sonnet for execution roles (IMPLEMENTER, TESTER, REVIEWER). Haiku never used in PDLC v2.

### 7.5 Checkpoints & User Control

- **FR5.1:** Pipeline definitions MAY mark any role as requiring a checkpoint (`checkpoint: true`).
- **FR5.2:** When a role with `checkpoint: true` completes, the shell pauses the pipeline, writes a `Checkpoint` record to MongoDB, and emits `CHECKPOINT_REQUESTED` event.
- **FR5.3:** Tacticl-core receives the event via gRPC stream, sends push notification to user's mobile/web client.
- **FR5.4:** User responds with approve / reject+feedback / cancel via REST API.
- **FR5.5:** Tacticl-core relays user response to arbiter via gRPC `ResolveCheckpoint(pipelineId, checkpointId, decision, feedback)`.
- **FR5.6:** Arbiter resumes pipeline (dispatches next role) or re-dispatches the rejected role with feedback in context.
- **FR5.7:** Mandatory checkpoints in FULL_PDLC: after PM (requirements review), after ARCHITECT (design review), before DEPLOY (final review). These cannot be disabled.

### 7.6 Triggers

- **FR6.1: Chat trigger** — Existing `POST /v1/agent/command` endpoint in tacticl-core submits a spark that can be classified as PDLC work.
- **FR6.2: Voice trigger** — Existing push-to-talk flow produces a text that enters the same chat pipeline.
- **FR6.3: GitHub webhook trigger** — New endpoint `POST /v1/webhooks/github` receives issue/PR events. When an issue is filed with label `tacticl-implement`, a spark is created from the issue body.
- **FR6.4: Scheduled trigger (cron)** — Users can schedule recurring sparks (e.g., "Every Monday, audit dependencies and file a PR with updates"). Implemented via existing `agent_reminders` collection.

### 7.7 Output Artifacts

- **FR7.1:** Default output MUST be a PR on the user's GitHub/GitLab repo.
- **FR7.2:** Per-user and per-repo configuration allows: `diff-only`, `branch-push`, `pull-request` (default), `auto-merge`.
- **FR7.3:** `auto-merge` is opt-in per repo, requires all CI checks to pass, and requires `required_checks` to be configured at the repo level.
- **FR7.4:** PR description MUST include: original user request, playbook used, roles that ran, final artifacts summary, link to execution logs, cost breakdown, retro observations.

- **FR7.5: Dual-format Tier 1 reports** — Every Tier 1 phase report MUST be produced in two formats:
  1. `.md` (Markdown + YAML frontmatter) — the source of truth. Written by agents, version-controlled in GitHub. Full content, every detail. If `.md` and `.html` ever conflict, `.md` wins.
  2. `.html` (self-contained HTML, Tacticl purple theme) — the HITL approval surface. Auto-generated by the `HTML_ASSEMBLER` step that runs after all phase roles complete. Contains approve / request changes / cancel buttons that POST to the tacticl-core checkpoint API via a signed short-TTL URL. Shareable via push notification deep link — user opens it in any browser, no app required.

- **FR7.6: HTML_ASSEMBLER step** — Each phase definition MUST include an `HTML_ASSEMBLER` step after the last role completes. This step is shell-handled (no LLM, no container). It reads the Tier 1 `.md` + Tier 2 artifact links, renders the HITL HTML using the phase-specific template (see `tacticl-docs/architecture/pdlc/templates/`), and commits both files to the pipeline run's GitHub branch.

- **FR7.7: Phase 1 PRD HTML structure** — The Phase 1 HITL HTML MUST include: executive summary, 5 key requirements, research findings summary, mockup thumbnails (linked to `.html` mockup files), approve / request changes / cancel buttons, cost-so-far metadata bar.

- **FR7.8: Phase 2 SAD HTML structure** — The Phase 2 HITL HTML MUST include:
  - Executive summary
  - Architecture decisions (key choices, max 5 bullets)
  - Deployment topology diagram — draw.io SVG exported and embedded inline for the HTML surface; Mermaid code block in the `.md` source
  - Flow diagrams rendered via Mermaid.js (auth flow, API request flow, role orchestration sequence, data model ERD)
  - Story summary ({N} stories, {M} tasks)
  - Links to all Tier 2 supporting artifacts
  - Approve / request changes / cancel buttons
  - Cost-so-far metadata bar

### 7.8 Pipeline State & Recovery

- **FR8.1:** All pipeline state MUST be persisted to MongoDB collection `pipeline_runs` on every state transition.
- **FR8.2:** In-memory `PipelineTracker` in the arbiter shell caches hot state for performance.
- **FR8.3:** On arbiter shell restart, hydrate in-memory state from MongoDB — resume any `RUNNING` pipelines that were interrupted.
- **FR8.4:** Pipeline execution MUST be idempotent — resuming a pipeline picks up from the last completed role, not re-runs completed work.

### 7.9 Retro Learning Loop

- **FR9.1:** A retro agent container MUST run weekly (Sunday 2 AM UTC) via Docker cron.
- **FR9.2:** Retro scans the past 7 days of archived workspaces in `/opt/cidadel/agent-workspaces/archive/`.
- **FR9.3:** Retro identifies patterns across runs: repeated failures, token waste, knowledge gaps, template improvements.
- **FR9.4:** Retro writes proposed learnings to MongoDB `agent_knowledge` with `status=proposed`.
- **FR9.5:** Eng lead reviews proposals via a console UI (tacticl-core admin).
- **FR9.6:** Approved learnings flow into the next workspace assembly for matching agents.
- **FR9.7:** Retro also populates the Qdrant past-runs index using Voyage-code-3 embeddings.

### 7.10 Observability

- **FR10.1:** Every role execution MUST emit OpenTelemetry spans: `role.start`, `role.complete`, `role.rework`, `role.checkpoint`.
- **FR10.2:** Token usage MUST be tracked per role per pipeline, recorded in MongoDB.
- **FR10.3:** Cost MUST be tracked per role per pipeline, with per-pipeline ceiling ($100 default, configurable) and monthly spending limit ($0 default, must be explicitly enabled per user).
- **FR10.4:** Structured logs (JSONL) MUST be written to `workspace/logs/` for every role execution.

## 8. Non-Functional Requirements

### 8.1 Quality (Primary Priority)

- **NFR-Q1: Output quality bar** — Target ≥85% of FULL_PDLC pipelines produce PRs that pass all CI checks on first run without human intervention. Stretch: ≥95%.
- **NFR-Q2: Bug regression rate** — Target ≤2% of merged tacticl PRs introduce bugs caught in production within 30 days.
- **NFR-Q3: Style consistency** — Target ≥90% of generated code matches the target repo's conventions (linter clean, formatter clean, no obvious style breaks).
- **NFR-Q4: Test coverage** — Target ≥80% line coverage on generated code. TESTER enforces this via coverage reports.

### 8.2 Performance

- **NFR-P1: Pipeline submission latency** — `SubmitPipeline` gRPC call returns in ≤2 seconds 95% of the time.
- **NFR-P2: PDLC total latency** — No hard cap. FULL_PDLC can take 30 minutes to several hours. Quality > speed.
- **NFR-P3: Container spawn latency** — ≤3 seconds from dispatch to first `boot.md` read.
- **NFR-P4: Checkpoint response latency** — From user approval to next role dispatch: ≤5 seconds.

### 8.3 Scale

- **NFR-S1: Concurrent pipelines** — Support at least 20 concurrent PDLC pipelines on current Hetzner infrastructure (CPX31 + added box). Scale by adding boxes, not re-architecting.
- **NFR-S2: Containers per pipeline** — Up to 15 concurrent containers per pipeline at peak (parallel TEST phase + sub-agents).
- **NFR-S3: Past-runs index** — Qdrant scales to ~100K past run chunks with sub-100ms query latency.

### 8.4 Cost

- **NFR-C1: Per-pipeline ceiling** — Default $100. Pipeline pauses at threshold, user must explicitly raise ceiling to continue.
- **NFR-C2: Monthly user budget** — Default $0 (blocked). User must explicitly enable and set a monthly limit.
- **NFR-C3: Cost transparency** — Every role's token + API cost tracked and displayed in pipeline detail view.
- **NFR-C4: Cost is NOT a quality constraint** — high max_turns, multi-candidate, and rework are never disabled for cost reasons. If the user's budget is exceeded, the pipeline pauses for explicit approval.

### 8.5 Reliability

- **NFR-R1: Pipeline recovery** — Any pipeline in state `RUNNING` at the time of shell restart MUST be resumable (via MongoDB-backed state).
- **NFR-R2: Container failure recovery** — If a container crashes, the role is marked failed. Shell can auto-retry once before escalating to rework loop.
- **NFR-R3: Callback delivery** — If the HTTP callback to tacticl-core fails, arbiter retries with exponential backoff (3 attempts, 1s/5s/30s).

### 8.6 Security

- **NFR-Sec1: Workspace isolation** — Per-pipeline bind mounts. No shared filesystem state between concurrent pipelines.
- **NFR-Sec2: Credential scoping** — GitHub tokens passed per-pipeline via env var, never logged, rotated per-workspace.
- **NFR-Sec3: Container privileges** — Agent containers run as non-root user. No privileged mode. No Docker-in-Docker (unless user-repo needs it, in which case gated by user approval).
- **NFR-Sec4: Vault integration** — All secrets (LLM API keys, GitHub tokens, user OAuth) loaded from Vault, never hardcoded or logged.
- **NFR-Sec5: SECURITY_ANALYST veto** — SECURITY_ANALYST rejection is a hard stop in the TEST phase. Cannot be overridden without explicit user checkpoint.

### 8.7 Observability

- **NFR-O1: Distributed tracing** — OTel traces span tacticl-core → arbiter → containers → callback, correlated by pipeline ID.
- **NFR-O2: Metrics** — Prometheus metrics for pipeline count, success rate, duration, token usage, rework rate, checkpoint pause time.
- **NFR-O3: Structured logs** — JSONL logs for every role, centralized via Loki on Hetzner.
- **NFR-O4: Dashboard** — Grafana dashboards for pipeline ops, token economics, quality metrics, retro outputs.

### 8.8 Extensibility

- **NFR-E1: New pipeline without deploy** — Adding a new pipeline requires: add JSON to registry, add boot templates, add knowledge files. No Java/Node.js code changes.
- **NFR-E2: New role without deploy** — Same as above. Register the new role type in the pipeline definition, provide boot template + CLAUDE.md + knowledge files.
- **NFR-E3: Pipeline registry versioning** — Registry files in git, deployed to `/agent-registry/` on Hetzner via SSH sync (same as strategiz pattern).

## 9. Success Metrics

### 9.1 Launch Criteria (v2 GA)

- [ ] FULL_PDLC playbook runs end-to-end with all 12 roles without manual intervention
- [ ] At least 3 sample PRs produced that pass CI on first run without human changes
- [ ] Checkpoint flow works end-to-end (pause → notify → user approves → resume)
- [ ] Multi-candidate generation produces measurably better code than single-candidate (A/B test)
- [ ] Rework loop correctly triggers on test failures and reviews
- [ ] Retro agent populates `agent_knowledge` with at least 5 proposed learnings from sample runs
- [ ] Past runs index in Qdrant supports semantic search with ≤100ms p95 latency
- [ ] Tacticl-core forwards pipeline progress events to mobile client via WebSocket
- [ ] All 5 Hetzner infrastructure pieces operational (arbiter, MongoDB, Qdrant, tacticl-core, cidadel-core)

### 9.2 Ongoing Quality Metrics (track weekly)

| Metric | Target | Measurement |
|--------|--------|-------------|
| First-run CI pass rate | ≥85% | % of pipelines whose PR passes CI without human edits |
| PR merge rate | ≥70% | % of pipelines whose PR gets merged (user approval signal) |
| Rework rate | ≤30% | % of roles that require at least 1 rework iteration |
| Checkpoint rejection rate | ≤15% | % of checkpoints rejected vs approved |
| Retro learnings proposed | ≥10/week | Signal that the retro loop is finding patterns |
| Retro learnings approved | ≥50% | Signal that retro-generated learnings are useful |
| Test coverage on generated code | ≥80% | Measured by TESTER coverage reports |
| Pipeline cost p50 | <$5 | Median cost per pipeline |
| Pipeline cost p95 | <$30 | 95th percentile cost per pipeline |

### 9.3 Anti-Goals (things to NOT optimize for)

- ❌ **Speed-to-PR** — Don't celebrate sub-10-minute pipelines if they produce lower-quality code. A 2-hour pipeline that produces merge-ready code is better.
- ❌ **Cost per pipeline** — Don't reduce multi-candidate, rework, or max_turns to cut costs. Quality trumps cost.
- ❌ **Number of pipelines run** — Don't count vanity metrics. Count merged PRs and first-run CI pass rate.

## 10. Dependencies

### 10.1 External Dependencies

| Dependency | Purpose | Status |
|------------|---------|--------|
| **Anthropic API** (Claude Opus 4.6, Sonnet 4.6) | Primary LLM | Live |
| **Voyage-code-3** | Embeddings for past-runs index | Needs account + API key |
| **GitHub API** | Repo clone, PR creation, webhooks | Live (existing integration) |
| **Hetzner CPX31+** | Hosting arbiter, MongoDB, Qdrant, tacticl-core | Live (second box needed for agent fleet) |
| **Docker** | Container runtime on Hetzner | Live |
| **MongoDB 7** | Persistent state, agent_knowledge, pipeline_runs | Live on Hetzner |
| **Qdrant** | Past-runs vector index | New — needs deploy to Hetzner |

### 10.2 Internal Dependencies

| Repo | Component | Status |
|------|-----------|--------|
| **cidadel-ai-arbiter** | Shell orchestrator, workspace assembler, container manager, gRPC services | Live (needs tacticl registry + checkpoint support) |
| **cidadel-core** | Auth (PASETO), user/session management, shared LLM infra | Live (on Hetzner) |
| **tacticl-core** | Control plane, WebSocket bridge, spark CRUD, webhook receiver, checkpoint relay | Needs refactor (remove PDLC execution, add control plane code) |
| **tacticl-web + tacticl-mobile** | Pipeline dashboard, checkpoint UI | Needs updates for v2 pipeline visualization |

### 10.3 Prerequisite Work

- **P1: Firestore → MongoDB migration for tacticl-core** — Must complete before v2 launches. Separate project.
- **P2: Tacticl-core Hetzner deployment** — Must be on Hetzner with strategiz and cidadel-core. Separate project.
- **P3: Qdrant on Hetzner** — Deploy Qdrant as Docker container in platform-net.
- **P4: GCS registry → filesystem registry** — Tacticl pipeline definitions in `/agent-registry/tacticl/` on Hetzner.

## 11. Data Storage & Persistence

### 11.1 Storage Systems

Tacticl PDLC v2 uses four storage systems. No new systems introduced beyond what the platform already runs.

| System | Purpose | Status |
|--------|---------|--------|
| **MongoDB 7** | Primary OLTP — pipeline state, agent knowledge, tacticl operational data | Live on Hetzner |
| **Qdrant** | Vector search over past successful runs (cross-run memory) | **New** — deploy as Docker container in platform-net |
| **Filesystem** | Workspaces, registry, archived runs | Live on Hetzner (`/opt/cidadel/`) |
| **Vault** | Secrets (LLM keys, GitHub tokens, MongoDB creds) | Live |

**Explicitly NOT used:** Redis, Postgres, Neo4j. Per Constraint C6.

### 11.2 MongoDB Collections

**New collections in `tacticlDb` (for v2):**

| Collection | Purpose | Writer | Reader |
|------------|---------|--------|--------|
| `pipeline_runs` | Pipeline state, role statuses, aggregated metrics, current playbook | Arbiter shell | Tacticl-core (dashboard), arbiter |
| `pipeline_events` | Append-only event log: role start/complete, rework, checkpoint, cost warnings | Arbiter shell | Tacticl-core (dashboard) |
| `pipeline_artifacts` | Role output artifacts — PRDs, designs, test results, GitHub refs | Arbiter shell | Tacticl-core (dashboard) |
| `pipeline_checkpoints` | Pending user approvals, checkpoint responses | Arbiter + tacticl-core | Both |
| `pipeline_jobs` | Maps `sparkId → arbiterPipelineId` for callback routing | Tacticl-core | Tacticl-core |
| `pipeline_configs` | Per-user, per-repo output preferences (diff/branch/PR/auto-merge) | Tacticl-core | Tacticl-core |
| `webhook_events` | GitHub webhook history, dedup, audit | Tacticl-core | Tacticl-core |

**Shared collection in `cidadelShared` database:**

| Collection | Purpose | Writer | Reader |
|------------|---------|--------|--------|
| `agent_knowledge` | Retro-proposed + human-approved learnings, scoped by `product` field | Retro agent (arbiter), console UI (tacticl-core) | Arbiter workspace assembler |

**Existing tacticl collections (carried forward from Firestore migration P1):**

`users`, `sparks`, `tactics`, `execution_logs`, `devices`, `social_integrations`, `repo_grants`, `agent_tokens`, `reminders`, `content_templates`, `scheduled_batches`, `generated_videos`, `action_confirmations`, `agent_audit_log`, `device_commands`

These remain owned by tacticl-core (Java) and are untouched by arbiter in PDLC v2.

### 11.3 Qdrant Collection: `tacticl_past_runs`

Self-hosted Qdrant Docker container on Hetzner, registered in `platform-net` for internal access from arbiter shell and agent containers.

**Point structure:**
- **Vector:** Voyage-code-3 embedding (1024 dimensions)
- **Payload schema:**
  ```json
  {
    "product": "tacticl",
    "pipeline_id": "uuid",
    "agent_type": "IMPLEMENTER",
    "playbook": "FULL_PDLC",
    "chunk_type": "role_prompt | role_output | outcome_summary",
    "success_score": 0.92,
    "merged_to_main": true,
    "tags": ["auth", "oauth", "spring-boot"],
    "timestamp": "2026-04-01T12:34:56Z",
    "content": "... chunk text ..."
  }
  ```

**Indexing strategy:**
- Filter on `product` + `agent_type` for scoped retrieval
- Filter on `success_score >= 0.8` (only successful runs indexed)
- Filter on `merged_to_main = true` (only user-accepted work)

**Query pattern:** Agent containers call MCP tool `find_similar_runs(query_text, agent_type, top_k=5)` → returns top-5 semantically similar chunks from past successful PDLC runs matching the current agent type.

**Writer:** Retro agent weekly job. Reads archived workspaces, chunks content, embeds via Voyage-code-3, upserts to Qdrant with payload metadata.

**Reader:** Agent containers via local MCP server. The MCP server exposes `find_similar_runs` as a tool Claude Code can invoke during execution.

### 11.4 Filesystem Layout

**Active workspaces** (bind-mounted to containers):
```
/opt/cidadel/agent-workspaces/
  workspace-{pipeline_id}-{role}-{ts}/
    boot.md
    CLAUDE.md
    knowledge/
      authored/
      learned/patterns.md
    context/
      repo/           # cloned user repo
      user-prompt.md
      prior-agent-output.md
      checkpoint-feedback.md
    results/
    logs/
```

**Archived workspaces** (30-day retention, cron cleanup):
```
/opt/cidadel/agent-workspaces/archive/
  tacticl/
    2026/04/11/
      workspace-.../
```

**Registry** (git-versioned, SSH-deployed):
```
/opt/cidadel/agent-registry/tacticl/
  pipelines/
    full-pdlc.json
    bug-fix.json
    small-feature.json
    refactor.json
    infra-change.json
    docs-only.json
    ui-change.json
    security-patch.json
  agents/
    pm.json
    researcher.json
    architect.json
    designer.json
    planner.json
    implementer.json
    reviewer.json
    tester.json
    security-analyst.json
    technical-writer.json
    devops.json
    retro-analyst.json
  templates/
    *.md.hbs                  # Handlebars boot templates per agent
  claude-configs/
    *.md                      # CLAUDE.md per agent
  knowledge/
    authored/
      java-conventions.md
      spring-boot-patterns.md
      testing-guidelines.md
```

### 11.5 Vault Secret Paths

| Path | Content |
|------|---------|
| `secret/tacticl/anthropic` | Anthropic API key (or OAuth token for Claude Code CLI) |
| `secret/tacticl/voyage` | Voyage-code-3 API key (for retro agent embeddings) |
| `secret/tacticl/github/{userId}` | Per-user GitHub OAuth tokens |
| `secret/cidadel/mongodb` | MongoDB connection credentials |
| `secret/cidadel/qdrant` | Qdrant API key (if auth enabled) |

### 11.6 Cross-Product Database Strategy

Since the arbiter is shared between strategiz and tacticl:

**Per-product databases** (each product owns its pipeline state):
- `strategizDb` — strategy generation runs, strategiz-specific collections
- `tacticlDb` — PDLC runs, tacticl-specific collections
- Rationale: Pipeline execution schemas diverge between products. Don't force a single schema.

**Shared `cidadelShared` database** (cross-cutting data):
- `agent_knowledge` — retro learnings, scoped by `product` field at query time
- Rationale: Cross-product visibility into learning patterns; retro scanner can identify cross-product insights (e.g., "both strategiz and tacticl waste tokens on the same thing").

### 11.7 Schema Ownership & Cross-Language Sync

| Collection category | Source of truth | Other side |
|--------------------|----------------|-----------|
| Arbiter-written (`pipeline_runs`, `pipeline_events`, `pipeline_artifacts`) | TypeScript schemas in arbiter (Zod or manual), documented in SAD | Java reads as `Document`, typed via documented contract |
| Tacticl-core operational (`sparks`, `tactics`, `devices`, etc.) | Java `MongoBaseEntity` classes in cidadel-core framework | N/A — arbiter doesn't touch |
| Shared (`agent_knowledge`) | Defined once in cidadel-core framework, TypeScript types mirror in arbiter | Both sides must sync on schema changes |

**Schema evolution rule:** Breaking changes require coordinated deploys. Additive changes (new optional fields) are safe without sync.

### 11.8 Retention & Backup Policy

| Store | Retention | Backup |
|-------|-----------|--------|
| MongoDB operational collections | Indefinite | Daily `mongodump` → external storage |
| MongoDB `pipeline_events` | 90 days | Daily (same as above) |
| Qdrant `tacticl_past_runs` | Indefinite (grows with learning) | Weekly snapshot → external storage |
| Active workspaces | Pipeline duration only | None (ephemeral by design) |
| Archived workspaces | 30 days on local disk | Weekly rsync → external storage |
| Registry | Git version control | Git (GitHub) |
| Vault secrets | N/A (Vault manages its own persistence) | Vault's built-in backup |

### 11.9 Indexing Strategy (MongoDB)

Critical indexes to create on launch:

| Collection | Indexes |
|------------|---------|
| `pipeline_runs` | `{userId: 1, createdAt: -1}`, `{sparkId: 1}`, `{status: 1, claimedBy: 1}` (for recovery job) |
| `pipeline_events` | `{pipelineId: 1, timestamp: 1}`, `{eventType: 1, timestamp: -1}` |
| `pipeline_artifacts` | `{pipelineId: 1, roleType: 1}` |
| `pipeline_checkpoints` | `{pipelineId: 1, status: 1}`, `{userId: 1, status: 1}` |
| `pipeline_jobs` | `{arbiterPipelineId: 1}` (unique), `{userId: 1, createdAt: -1}` |
| `agent_knowledge` | `{product: 1, status: 1}`, `{product: 1, agent_types: 1}` (multikey) |
| `webhook_events` | `{deliveryId: 1}` (unique, for dedup), `{source: 1, timestamp: -1}` |

### 11.10 Databases That User Code Needs (Not Tacticl's Infrastructure)

**Important distinction:** The collections in sections 11.2–11.9 are tacticl's own factory infrastructure. They store pipeline state, agent knowledge, and operational data. **They are NEVER accessed by user code.** Tacticl does not host production data for user applications.

When a user spark needs a database (e.g., "build a URL shortener with Postgres"), the approach depends on whether the work targets an existing repo or a greenfield project.

#### Case A: User's spark targets an existing repo (majority of PDLC work)

The agent uses whatever database configuration already exists in the repo.

- Shell clones user's repo into `workspace/context/repo/`
- IMPLEMENTER reads `docker-compose.yml`, `application.properties`, existing schema
- Agent writes code that matches the repo's existing database setup
- TESTER runs the repo's existing test infrastructure (Spring Boot with H2, Node.js with Testcontainers, etc.)
- Tacticl provides nothing database-related — the repo's setup handles it

#### Case B: Greenfield spark — building from scratch

The agent picks an appropriate database, generates code + infrastructure, and hands off to the user.

- ARCHITECT chooses database based on requirements
- IMPLEMENTER generates application code + `docker-compose.yml` + migrations + README
- TESTER uses embedded databases (H2, Fongo, Fake Redis) or Testcontainers for integration tests
- DEVOPS generates deployment configs (Docker Compose, Kubernetes manifests, etc.)
- Pipeline produces a runnable PR — **user deploys it to their own infrastructure**
- Tacticl does NOT host the running application. Ever.

#### Testing Strategy for Database-Dependent Code

TESTER has two paths for running tests that need real databases:

| Strategy | When | How |
|----------|------|-----|
| **Embedded databases** (default) | Unit tests, simple integration tests | H2/HSQLDB for SQL, Fongo for MongoDB, Fake Redis — runs in-memory inside the agent container, zero infrastructure |
| **Testcontainers** (escape hatch) | Real integration tests where embedded isn't sufficient | Ephemeral Postgres/MySQL/Mongo/Redis containers spun up inside the agent container's Docker context, destroyed after tests |

#### Container Image Requirements for Testcontainers Support

For agent containers to run Testcontainers, one of:

- **Docker socket mount** (v2 default) — `/var/run/docker.sock:/var/run/docker.sock`. Agent container spawns sibling containers on the host Docker daemon. Simple, privileged.
- **Docker-in-Docker** (v2.1 if needed) — nested Docker daemon inside the agent container. Heavier but more isolated.

**Security safeguard:** Arbiter shell validates `docker run` commands against a curated allowlist of images (Postgres, Mongo, Redis, MySQL). Agent containers cannot spawn arbitrary images on the host.

#### What Is NEVER Done

- User code never accesses tacticl's MongoDB, Qdrant, or any infrastructure database
- Tacticl never provisions production databases for user applications
- Tacticl never hosts user data in its own storage systems
- In-memory databases (H2, SQLite in-memory, embedded Mongo) are strictly testing tools — never a substitute for real production databases

## 12. Constraints

- **C1:** Budget — Hetzner infrastructure only. No GCP, no AWS. Hardware-bound concurrency.
- **C2:** Java stack stays — tacticl-core remains Java/Spring Boot. Execution moves to Node.js arbiter, but the Java backend is the control plane.
- **C3:** cidadel-core is the auth source of truth — don't rebuild auth in Node.js.
- **C4:** Claude Code CLI is the execution engine — not Claude Code SDK, not direct Anthropic API, not alternatives. CLI for isolation + native toolchain.
- **C5:** Single Vault endpoint — all secrets from the existing Vault service. Don't fragment secret management.
- **C6:** No new databases — MongoDB + Qdrant only. No adding Postgres, no Neo4j, no Redis (except for rate limiting if needed).

## 13. Out of Scope / Future Work

### 13.1 Deferred to v2.1

- Multi-repo PDLC (one pipeline spanning multiple repos)
- IDE integration (VSCode extension, JetBrains plugin)
- Real-time agent token streaming to user's UI (arbiter currently reports at role boundaries)
- Live debugging — attach to a running container, see what the agent is thinking in real-time
- Cost prediction before pipeline submission

### 13.2 Deferred to v3

- Non-PDLC workloads through arbiter (social, chat, research, video) — separate migrations
- Multi-tenant isolation beyond per-user namespacing
- Team/org features — shared knowledge, shared pipelines, role-based access
- Knowledge graph (Neo4j) for code relationships
- Self-hosted LLM models (for air-gapped deployments)
- Agent ability to use user's local desktop daemon for device-side work

## 14. Glossary

| Term | Definition |
|------|-----------|
| **Pipeline** | An ordered sequence of agent roles that together produce a deliverable (e.g., FULL_PDLC, BUG_FIX) |
| **Playbook** | A named pipeline configuration (FULL_PDLC, BUG_FIX, SMALL_FEATURE, etc.) |
| **Role** | A specialized AI agent with a specific responsibility (PM, ARCHITECT, IMPLEMENTER, etc.) |
| **Phase** | A logical grouping of roles (PRODUCT, DESIGN, DEVELOPMENT, TEST, DEPLOY) — used for UX |
| **Workspace** | Per-run isolated filesystem containing boot.md, CLAUDE.md, knowledge, context, results, logs |
| **Agent container** | A Docker container running Claude Code CLI, executing a single role |
| **Shell** | The arbiter orchestrator — dispatches agents, manages workspaces, handles callbacks |
| **Registry** | Filesystem store of pipeline definitions, agent definitions, boot templates, CLAUDE.md configs, knowledge files |
| **Checkpoint** | A pause point in the pipeline requiring user approval before continuing |
| **Rework loop** | Re-dispatching a role with feedback after it was rejected by a critic or its own tests |
| **Multi-candidate** | Generating N parallel implementations and selecting the best via a critic sub-agent |
| **Critic** | A sub-agent that evaluates outputs from another agent (validation, selection, review) |
| **Retro agent** | Weekly job that scans past runs and proposes learnings |
| **Past runs index** | Qdrant vector DB of successful past pipeline runs, queried by agents for examples |
| **Control plane** | tacticl-core's new role: auth, CRUD, WebSocket bridge, webhooks |
| **Execution plane** | The arbiter: where pipelines actually run |
