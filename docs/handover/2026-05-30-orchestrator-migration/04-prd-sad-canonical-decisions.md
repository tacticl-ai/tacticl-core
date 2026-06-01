# 04 — PRD/SAD Canonical Design Decisions

> **Purpose:** the non-negotiable architecture from the Cloud Agent Orchestrator PRD + SAD + plan that a fresh
> session MUST honor when continuing the migration. Every fact below was read from the actual files and cites a
> section number. Where the 2026-05-30 working session *overrode or refined* these docs, that is flagged
> explicitly — see `00-session-decisions.md` in this same dir for the full set of session-level decisions
> (Option B, engine-into-arbiter, productId scoping, etc.). **This file does NOT duplicate `00`; it captures
> what the canonical specs say, and where `00` supersedes them.**

## Source files (verified, with line counts)

| Doc | Absolute path | Lines |
|---|---|---|
| PRD | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-prd.md` | 526 |
| SAD | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md` | 1386 |
| Plan | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/plans/2026-05-25-cloud-agent-orchestrator.md` | 603 |

These three docs supersede the PDLC v2 PRD/SAD (`2026-04-11-tacticl-pdlc-v2-{prd,sad}.md`) **for orchestration only**.
The PDLC v2 SAD remains canonical for the Arbiter execution plane (SAD §15, §1, §4.5.3).

---

## ⚠️ Two overrides a fresh session must internalize before trusting these docs verbatim

These docs are architecturally canonical, but two foundational things changed AFTER they were written:

### Override A — "defer Temporal" was reversed, then "defer Temporal" inside the docs is itself moot
The PRD and SAD **already overrode** an earlier 2026-05-20 architecture critique that recommended *deferring* Temporal
until ~10 concurrent sparks:
- PRD §2.1 (last bullet of "Orchestration is fragile"): *"The 2026-05-20 architecture critiques recommended deferring
  Temporal until ~10 concurrent sparks; we are choosing to do it now to avoid building the turn loop twice."*
- PRD §8.1 risk row: *"Adopting Temporal contradicts 2026-05-20 deferral → Accepted by user (2026-05-25). More infra
  now, no rewrite later."*
- SAD §14 row A1/A2 and §3.1 commit to a self-hosted Postgres-backed Temporal cluster.
- PRD §3.4 Non-Goals: *"Temporal SaaS — self-hosted Postgres-backed cluster on Hetzner, alongside Arbiter."*

**So: full Temporal is adopted NOW.** There is no "defer Temporal" stance left to honor inside these docs — they are
already the "adopt now" decision. (The MEMORY pointer phrasing "Temporal deferral OVERRIDDEN — adopting full Postgres-backed
cluster now" matches this.)

### Override B — implementation language pivoted Java → TypeScript; the engine moves to arbiter
`00-session-decisions.md` (2026-05-30) decided the orchestrator + PDLC pipeline **engine** relocates into
`cidadel-ai-arbiter` (TypeScript/Node), with `tacticl-core` (Java) staying a full product backend. **The ARCHITECTURE
described in the PRD/SAD remains canonical** (persona model, workflow shapes, voice plane rules, projection model,
session↔spark model, greeting templates, cost numbers). What changed is *where the code lives and what language it is
written in*, plus a handful of refinements (`00` items 4, 6, 7, 10). When PRD/SAD say "Java", "Spring bean",
"`io.tacticl.*` package", "in-JVM worker", treat those as **superseded by `00`** — the same logic is TS in arbiter.
Per `00` item 7: `PersonaRouter` folds into the workflow as a pure imported function, not a separate Spring bean.

---

## 1. Persona model — 14 personas (2 conversational + 12 PDLC)

**Headline (PRD §4.2.5):** v1 ships **2 conversational personas + 12 PDLC personas = 14 personas**, plus **~15 skills**
shared across personas.

### 1.1 Two families (PRD §4.2, SAD §4.5)
`PersonaFamily` enum has exactly **CONVERSATIONAL | PDLC** — the plan (Task 2.1) explicitly notes "no UTILITY family".

| Family | Execution shape (SAD §4.5) | Speaks? |
|---|---|---|
| `CONVERSATIONAL` | In-JVM/in-engine Anthropic API call via `InvokePersonaActivity`. No container. ~1s response. | Yes (has `voicePreset`) |
| `PDLC` | Ephemeral Docker container on Hetzner via Arbiter, Claude Code CLI driven. Container per role per run. | No — `voicePreset = null`; narrated by Product Manager's `summarize_pipeline_progress` skill |

### 1.2 The 2 conversational personas (PRD §4.2.1, SAD §4.4)
- **Product Manager** — id `product-manager`, `defaultModel = claude-sonnet-4-6`, voicePreset `{adam, calm, 0.5, 0.75}`.
  Default chat partner: intake, scoping, proposals, pipeline narration, checkpoint mediation. **8 skills** (SAD §4.4.1):
  `ask_clarification`, `propose_implementation`, `start_pipeline`, `start_cloud_skill`, `dispatch_to_device`,
  `summarize_pipeline_progress`, `mediate_pipeline_checkpoint`, `answer_in_conversation`.
- **Market Researcher** — id `market-researcher`, `defaultModel = claude-sonnet-4-6`, same Adam voice in v1. Competitor /
  demand / market validation. **7 skills** (SAD §4.4.2): `web_search`, `read_page`, `analyze_competitors`,
  `estimate_market_size`, `synthesize_findings`, `propose_validation_experiment`, `answer_in_conversation`.

Prompts committed to `business-cloud-orchestrator/src/main/resources/conversational-personas/{product-manager,market-researcher}.md`
(plan Task 2.4) and ingested by the migration runner. (Note: PRD §4.4 prose says
`business-jarvis/src/main/resources/conversational-personas/*.md` — this is an **internal inconsistency** in the docs;
the plan and SAD §11 module layout use `business-cloud-orchestrator`. Treat `business-cloud-orchestrator` as correct.
UNVERIFIED which path the eventual TS impl uses; under Override B the engine is in arbiter, so this resource path is
likely irrelevant — but the *content* of the two prompts is canonical.)

### 1.3 The 12 PDLC personas + the PM→PO rename (PRD §4.2.2, §4.2.3; SAD §4.3)
The 12 PDLC personas, in order:
`PO` (Product Owner), `RESEARCHER`, `ARCHITECT`, `DESIGNER`, `PLANNER`, `IMPLEMENTER`, `REVIEWER`, `TESTER`,
`SECURITY_ANALYST`, `TECHNICAL_WRITER`, `DEVOPS`, `RETRO_ANALYST`.

**Critical rename:** the existing enum value `PdlcRole.PM` is renamed to `PdlcRole.PO` (Product Owner). The source file
`pm.md` migrates to persona id `product-owner`. SAD §4.3 migration mapping table (verbatim source-file → persona-id):

| Source file | persona id | displayName |
|---|---|---|
| `pm.md` | `product-owner` | Product Owner |
| `researcher.md` | `researcher` | Researcher |
| `architect.md` | `architect` | Architect |
| `designer.md` | `designer` | Designer |
| `planner.md` | `planner` | Planner |
| `implementer.md` | `implementer` | Implementer |
| `reviewer.md` | `reviewer` | Reviewer |
| `tester.md` | `tester` | Tester |
| `security_analyst.md` | `security-analyst` | Security Analyst |
| `technical_writer.md` | `technical-writer` | Technical Writer |
| `devops.md` | `devops` | DevOps |
| `retro_analyst.md` | `retro-analyst` | Retro Analyst |

The naming distinction is deliberate (PRD §4.2.3): **chat "Product Manager" ≠ pipeline "Product Owner"**; chat
"Market Researcher" ≠ pipeline "Researcher". Different jobs, different prompts.

**Mongo data migration:** `pipeline_runs.role` and `pipeline_events.role` get an **in-place** `"PM"` → `"PO"` bulk update
(PRD §4.2.3, §7.4 step 3; SAD §4.3). Single-cut, irreversible — no `@JsonAlias`. Migration-log key:
`pdlc-role-pm-to-po-v1`. The reverse script for emergency rollback is `deployment/scripts/rollback-pm-po.js` (plan Task 7.6).

### 1.4 Persona / Skill registry schema (PRD §4.2.4, SAD §4.1)
**`personas` collection** fields: `id` (kebab-case), `family` (CONVERSATIONAL|PDLC), `displayName`, `description`,
`systemPrompt` (markdown body), `defaultModel` (Anthropic model id; for PDLC passed to `claude --model`),
`skillIds: List<String>`, `voicePreset: VoicePreset?` (null for PDLC), `active`, `version` (bumped on edit, old versions
retained for replay), `createdAt`, `updatedAt`.
`VoicePreset`: `providerVoiceId`, `style`, `stability` (0–1), `similarityBoost` (0–1).

**`skills` collection** fields: `id`, `name`, `description` (shown to LLM as tool description), `inputSchema` (JsonNode —
Anthropic tool-use JSON Schema), `activityName` (Temporal activity that handles the tool call), `active`, `createdAt`,
`updatedAt`.

**Loading/caching (SAD §4.2):** `PersonaRegistry` + `SkillRegistry` with Caffeine caches, 5min TTL, evicted on write.
All loaded on startup (warm cache). `PersonaRegistry.toolsFor(personaId)` resolves a persona's `skillIds` → Anthropic
tool defs, passed to `InvokePersonaActivity` per turn. Versioning is append-only.

**Adding a persona** = insert a Mongo doc + author a system prompt; **no code change, no deploy** (PRD §4.2.5).
**Adding a skill** = insert a Mongo doc + implement the backing activity; one code change scoped to the activity, no
workflow change.

**Reproducibility (SAD §4.5.2):** each `PipelineRun` pins `personaVersions: Map<role, version>` snapshotted at run
start, so re-running a past pipeline uses the prompts that produced the original artifacts.

> **`00` refinement (open question):** persona/skill **registry scoping by `productId`** — shared registry with
> product-tagged personas vs. per-product namespaces — is an **open question in `00`** (bottom section). Diagrams assume
> "shared, product-tagged". Not yet canonical in the SAD. A fresh session must NOT bake either choice in without
> confirming.

---

## 2. Skill registry — ~15 skills (PRD §4.2.5, SAD §4.4b)

Each skill has a backing Temporal activity (some are `NoOpActivity` for LLM-internal skills). Full catalogue from SAD §4.4b:

| Skill | Backing activity | Notes |
|---|---|---|
| `ask_clarification` | `EmitClarificationActivity` | No-op beyond emitting; persona text IS the question |
| `propose_implementation` | `RecordProposalActivity` | Persists proposal; session → `PROPOSING` |
| `start_pipeline(sparkInput, playbook?)` | `StartPipelineWorkflowActivity` | `executeChildWorkflow(PipelineWorkflow)` with `ParentClosePolicy.ABANDON`; sets `PipelineRun.name` from proposal |
| `start_cloud_skill(skill, args)` | `StartCloudSkillActivity` | Wraps existing `CloudOrchestratorService.execute(...)` |
| `dispatch_to_device(deviceId, taskSpec)` | `DispatchToDeviceActivity` | Wraps existing device WebSocket dispatch |
| `summarize_pipeline_progress(pipelineRunId)` | `ReadPipelineStateActivity` | Reads PipelineRun + recent events; cross-session safe |
| `mediate_pipeline_checkpoint(pipelineRunId, checkpointId, userResponse)` | `ResolveCheckpointActivity` | Parses NL → CheckpointDecision; signals target `PipelineWorkflow.onCheckpointResolved` by workflowId |
| `list_user_pipelines()` | `LoadUserActivePipelinesActivity` | Same activity backing per-turn context, exposed as skill |
| `cancel_pipeline(pipelineRunId, reason?)` | `CancelPipelineActivity` | Tier-1 (confirmation required); tells Arbiter to tear down containers |
| `answer_in_conversation` | `NoOpActivity` | Persona text IS the answer |
| `web_search` | `BraveSearchActivity` | Wraps `client-brave-search` |
| `read_page` | `JinaReadActivity` | Wraps `client-jina` |
| `analyze_competitors` | `CompetitorAnalysisActivity` | Composed: web_search + read_page + LLM synthesis |
| `estimate_market_size` | `MarketSizeActivity` | v1 stub — triangulate from public sources |
| `synthesize_findings` | `NoOpActivity` | LLM-internal |
| `propose_validation_experiment` | `NoOpActivity` | LLM-internal |
| `complete_role` | `CompleteRoleActivity` | PDLC-only — invoked inside the Claude Code container when a role finishes |

That is **17 skill rows** in the SAD table (the "~15" headline is approximate; the migration target in plan Task 7.5 is
"`skills` has ~15"). PDLC personas' `skillIds` reference **allowed Claude Code CLI/MCP tools** (e.g.
`["read","write","bash","web_fetch"]` for builder roles), mapped to `--allowedTools` at boot.md assembly inside Arbiter
(PRD §4.2.2, SAD §4.3) — NOT orchestrator-level skills.

---

## 3. Workflow shapes — `CloudAgentSessionWorkflow` (parent) + `PipelineWorkflow` (child)

### 3.1 `CloudAgentSessionWorkflow` (SAD §3.3.1)
One instance **per `ConversationSession`**. A user can have multiple concurrently (web tab A/B, mobile, Telegram). Sessions
are conversational channels, NOT owners of pipelines. Self-completes on 24h idle abandonment; **pipelines outlive sessions**.

Signals: `onUserTranscript` (final Deepgram transcript), `onUserText` (typed), `onCheckpointDecision`, `onModeChange`,
`onBargeIn`, `onCancel`.
Queries: `currentState()`, `recentTurns(int)`, `activePersonaId()`.

Internal state: `sessionId`, `userId`, `mode`, `state`, `turns`, `sessionStartedPipelineIds: Set<String>` (transcript
linkage only — NOT the source of truth for "what's running"; that's a Mongo query), `focusedPipelineId: Optional<String>`
(per-session focus), `pendingCheckpoint: Optional<CheckpointRef>`, `costAccumulator`, `seenTurnIds: LinkedHashSet<String>`.

**Turn loop (SAD §3.3.1):** wait for signal → `personaRouter.route(...)` (pure function, no activity, no LLM) →
`LoadUserPipelinesActivity` to build context → `invokePersona` activity → if voice, fan to `elevenlabsSpeak` → `persistTurn`
→ `emitTurnEvent` → handle tool calls (start_pipeline async child / propose_implementation / mediate_pipeline_checkpoint
signals target pipeline by workflowId / summarize / cancel) → loop.

**Signal idempotency (SAD §3.3.1.x):** every user signal carries a client-generated UUIDv4 `turnId` persisted in
`sessionStorage`; workflow keeps a bounded `LinkedHashSet` of the last 50 `seenTurnIds` and silently drops duplicates.
Metric `cloud_agent.duplicate_signals_dropped_total`. Mode-change signals are NOT deduped (idempotent by nature).

### 3.2 `PipelineWorkflow` (SAD §3.3.2)
One per spark execution. **Lifetime independent of any session.** Started as a child of `CloudAgentSessionWorkflow` but
runs autonomously. Replaces today's `PdlcV2Service` orchestration logic.

Signals (routed by `workflowId`, callable from any session/device): `onArbiterCallback`, `onCheckpointResolved`, `onCancel`.
Query: `currentState()`.

**Critical workflow options (SAD §3.3.2):**
- `workflowId = "pipeline-" + sparkId` (signal routing key — NOT parent traversal)
- `setWorkflowExecutionTimeout / setWorkflowRunTimeout = Duration.ofDays(7)` (covers HITL waits)
- `setWorkflowTaskTimeout = 30s`
- `setMaximumAttempts(1)` (pipelines retry within themselves, not at workflow level)
- **`setParentClosePolicy(ParentClosePolicy.ABANDON)`** — the key that lets pipelines outlive the originating session.
  Parent session closing does NOT terminate the pipeline.

Pipeline body: `submitToArbiter` activity → wait for `onArbiterCallback` signals (today's HTTP callback becomes a signal
sender via `PipelineCallbackController`) → per callback `persistPipelineEvent` + `fanOutPipelineEvent` → on checkpoint
callback insert `PipelineCheckpoint` (OPEN), fan out to ALL the user's active sessions, `Workflow.await` for
`onCheckpointResolved` → on terminal callback update `PipelineRun.status`, fan out, return, close.

### 3.3 Two persona execution paths (SAD §4.5)
`CONVERSATIONAL` → in-engine Anthropic call (no container, ~1s). `PDLC` → Arbiter ephemeral Docker container,
Claude Code CLI, workspace isolation. The Arbiter execution plane is **fully retained** from PDLC v2 SAD §3–§7
(workspace assembly `/opt/cidadel/agent-workspaces/{runId}/{role}/`, 4-layer knowledge injection, `cidadel-agent` image,
gRPC protocol, HTTP callbacks, per-role rework, Qdrant `past_pipeline_runs` MCP) — SAD §4.5.3.

> **`00` refinement:** `00` item 3 confirms this exact shape (one durable `SessionWorkflow` that SPAWNS a child
> `PipelineWorkflow`) and explicitly **rejected** the "one mega-workflow doing chat + build" alternative (cardinality
> 0..N builds, lifecycle mismatch, history bloat, no failure isolation). It also rejected "stateless conversation /
> only pipeline durable" — durability of the conversation was wanted. So the parent/child shape here is doubly canonical.

---

## 4. Multi-session + multi-pipeline model (SAD §3.6)

Eight design principles, verbatim intent from SAD §3.6:
1. **Pipelines are user-scoped, not session-scoped.** `workflowId = pipeline-{sparkId}`; `PipelineRun.userId` is the
   primary query axis. `PipelineRun.creatingSessionId` is recorded for transcript linkage but never used for routing or auth.
2. **Sessions are conversational channels** — multiple per user concurrently is normal; ends on 24h idle; pipelines don't end with it.
3. **Bootstrap queries user-scoped state** — every session start calls `loadUserActivePipelines(userId)` returning
   `List<PipelineSummary>` (id, name, status, currentRole, blockedCheckpointId?).
4. **Human-readable pipeline names** — `PipelineRun.name` (~30 chars, from `propose_implementation` summary). Never expose UUIDs.
5. **Checkpoint fan-out hits all live sessions** — FCM (mobile) + WS (web); whoever resolves first wins.
6. **Race-resolution** — `ResolveCheckpointActivity` uses optimistic locking (`status: OPEN → RESOLVED`); second resolver
   gets "already resolved by other session".
7. **Focus tracking is per-session** (`focusedPipelineId`), not global.
8. **No "session A owns pipeline X" enforcement** — authorization purely `PipelineRun.userId == requestingSession.userId`.

**Leapfrog is first-class** (PRD §5.1, SAD §3.6): order of start ≠ order of finish, by design. Cancellation does NOT
cascade across sibling pipelines.

Session state machine (PRD §5.1): `IDLE → ENGAGED → GATHERING → PROPOSING → CONFIRMED → PIPELINE_ACTIVE ↔ PIPELINE_BLOCKED
→ ENGAGED`; `Any → ABANDONED` (24h idle, pipelines continue) / `Any → CANCELLED`. `PIPELINE_BLOCKED` = "at least one of the
user's pipelines is blocked on a checkpoint".

---

## 5. Mongo projection model — Temporal truth vs queryable views (SAD §3.7)

**Temporal workflow history is the source of truth** (Postgres-backed, event-sourced, replayable). **Mongo collections are
projections** — written by activities, queried by everything outside the workflow (UI, REST, push, reporting). Recovery is
one-directional: a stale Mongo doc can be rebuilt by replaying workflow history; the inverse is not true.

Projection-write map (SAD §3.7 table):
- `conversation_sessions` ← `CreateSessionActivity`, `PersistTurnActivity` — read by resume bootstrap/history/transcripts.
- `pipeline_runs` ← `CreatePipelineRunActivity`, `UpdatePipelineRunStatusActivity` — read by dashboard/bootstrap/REST.
- `pipeline_events` ← `PersistPipelineEventActivity` — read by timeline/EXPLAINER/retro.
- `pipeline_checkpoints` ← `RaiseCheckpointActivity` (insert OPEN), `ResolveCheckpointActivity` (update RESOLVED).
- `pipeline_artifacts` ← `PersistArtifactActivity`.
- `sparks` ← `SparkService.createSpark` (via `StartPipelineWorkflowActivity` / `StartCloudSkillActivity` / `DispatchToDeviceActivity`).

**Consistency:** projections are eventually consistent (10–100ms lag). Checkpoint resolution completes synchronously so the
user sees confirmation immediately. Correctness-critical ops (cancel-then-create-replacement) use the signal directly + await
terminal state via query — don't depend on projection freshness for state machines. Recovery: `RebuildProjectionJob`
(runbook `deployment/runbooks/projection-recovery.md`, to be authored Phase 7).

> **`00` refinement (item 6):** under the engine-into-arbiter pivot, **arbiter writes directly to shared Mongo** (the
> projection pattern of §3.7), and the product backend (tacticl-core) **READS** those projections for its REST surface.
> The §3.7 projection model is therefore the cross-repo data contract, not just an internal one.

---

## 6. Session ↔ Spark conceptual model (PRD §5.7.1)

A `ConversationSession` is the long-lived top-level entity (one workflow per session). A `Spark` is a unit of executable
work (pipeline run / cloud skill / device dispatch). **Relationship is 1-to-many, and N can be 0.** Flat 1-to-1
`ConversationSession.sparkId` is replaced by `sparkIds: List<String>` (append-only, ordered by creation; SAD §9.1 names the
field `sessionStartedSparkIds`).

**A spark is created exactly when execution starts. Conversation-only turns produce zero sparks.** Examples (PRD §5.7.1):
page-open greeting → no spark; clarification → no spark; market-research-via-skills → no spark; confirm scope → `start_pipeline`
fires → **yes, a CODE/DEVOPS spark + child PipelineWorkflow**; "how's it going" → no spark; "now add tests" → second spark;
"post to Twitter" → `start_cloud_skill` → SOCIAL spark; "switch to text" → no spark.

New field: `Spark.conversationSessionId` (SAD §9.4) — for traceability/transcript linkage, NOT routing. Pre-deploy sparks
get null (acceptable — they don't reliably complete anyway). **Sparks/pipelines are user-scoped, not session-scoped** — any
of the user's sessions can query/checkpoint/cancel any of their sparks.

---

## 7. Voice plane rules

### 7.1 Tool-use streaming protocol (SAD §5.10)
The Anthropic response is a sequence of typed blocks; the bridge must speak text and **suppress tool-call JSON**. Block-type
routing (SAD §5.10.1):
- `text_delta` → forward to ElevenLabs `stream-input` immediately + emit `persona_text_chunk` caption.
- `tool_use` (start) → **flush ElevenLabs buffer** (finish current sentence audibly), pause forwarding, emit
  `tool_use_started`; sphere → `thinking`.
- `tool_result` → **no-op for TTS** (never spoken).
- continuation `text_delta` after a tool round → **open a NEW ElevenLabs stream-input WS** (previous was closed by flush),
  resume; sphere → `speaking`.
- `message_stop` → close WS, emit `tts_done`; sphere → `idle`.

Multiple `tool_use` blocks (parallel tool use): workflow runs them in parallel; bridge stays in `thinking` until ALL
results return (SAD §5.10.3). `ElevenLabsStreamBridge` is per-utterance with states `IDLE / SPEAKING / PAUSED_FOR_TOOL`;
multiple WS connections per assistant turn is fine (Turbo handshake <100ms) (SAD §5.10.5).

### 7.2 Long-running skill UX (SAD §5.11)
Persona system prompts instruct the LLM to emit a **brief text block BEFORE calling any tool likely to take >1s** ("Good
question. Let me check the landscape."). Flagged long-running skills (SAD §5.11.2): `web_search` (1–2s), `read_page` (1–3s/page),
`analyze_competitors` (15–30s), `estimate_market_size` (10–20s), `summarize_pipeline_progress` (1–2s). NOT flagged (fast/LLM-internal):
`start_pipeline`, `mediate_pipeline_checkpoint`, `propose_implementation`, `answer_in_conversation`, `ask_clarification`. v1
ships WITHOUT enforcement — if the LLM skips filler, the sphere just sits in `thinking` (acceptable; add a "thinking sound"
after 2s of silence only if telemetry warrants). Text mode: filler renders as caption, no special handling.

### 7.3 Auto-mute + barge-in (PRD §5.3, SAD §5.2)
- **Auto-mute during TTS:** while a TTS stream is active, client audio frames are dropped (not forwarded to Deepgram);
  mute released on `tts_done`/`tts_aborted`. Belt-and-suspenders: client also stops sending mic frames during TTS playback.
  Config `tacticl.cloud-orchestrator.voice.auto-mute-during-tts=true` (SAD §12.1).
- **Barge-in:** user speaking during TTS aborts TTS playback, aborts the in-flight ElevenLabs request, marks the assistant
  turn `INTERRUPTED`, accepts the new transcript. Implemented via `onBargeIn` signal cancelling the in-flight
  `elevenlabsSpeak` activity through Temporal heartbeat cancellation (SAD §3.3.1, §5.3).
- **Echo telemetry:** counter `cloud_agent.echo_events_total` must always be zero; alert at >0.1/min (plan Task 5.7).

### 7.4 Providers, modes, latency
- STT = **Deepgram** streaming WS, `nova-2`, `linear16`/16kHz/mono, `endpointing=300ms`, interim + VAD events (SAD §5.2, §12.1).
- TTS = **ElevenLabs** streaming, `eleven_turbo_v2`, stock **Adam** voice (single voice v1; SAD §5.3, §12.1, §14 A5).
- Modes (PRD §5.2): `VOICE_ACTIVE` (default, always-on VAD), `VOICE_PTT`, `TEXT_ONLY`, `MUTED`. Switching never destroys
  session state — modality lives in the WS handler + UI, not the workflow.
- **Latency target (PRD §6.1, SAD §5.4): p50 user-pause → first-TTS-chunk < 1200ms; p95 < 2200ms.** Achieved via pure-function
  routing (no LLM), templated greeting (no LLM), regex control intents (no LLM).
- **Graceful degradation (PRD §5.3, G8):** Deepgram down → "voice unavailable", force `TEXT_ONLY`; ElevenLabs down → text/captions
  only, no audio, sphere still shows speaking pulse. Provider kill switches `tacticl.deepgram.enabled` / `tacticl.elevenlabs.enabled`
  are the ONLY retained flags (plan intro) — they gate provider calls for degradation, NOT rollout.

> **`00` refinements (items 4, 8):** The **voice WS is the ONLY direct browser → arbiter path** (latency exception; a Java
> relay hop would blow the <1200ms budget). Auth = short-lived session token signed by tacticl-core at session start,
> validated by arbiter via a shared Vault signing key. **Voice providers live in the conversation layer ONLY — the pipeline
> is SILENT** (no audio anywhere inside the pipeline; pipeline emits TEXT events that the persona narrates → ElevenLabs).

---

## 8. Templated session greeting (PRD §5.9, SAD §6.4)

The opening greeting on a fresh page/session load is **templated, NOT LLM-generated.** Three reasons (PRD §5.9): zero
latency on mount, consistent first impression, cheapest possible first turn. It is **attributed to Product Manager** in the
transcript (one coherent voice) but emitted by a workflow activity — Product Manager's LLM only fires on the user's first
response. Recorded as a `Turn` with `personaId="product-manager"`; state transitions `IDLE → ENGAGED`; no LLM call.

Template selection by session-bootstrap state (PRD §5.9 / SAD §6.4 step 5), priority order:
1. **Highest — pending checkpoint:** "Welcome back. {Pipeline X} is waiting on you to decide {Y}, and {Pipeline Z} is still
   running. What do you want to tackle?"
2. **High — in-flight, no checkpoints:** "Welcome back. {Pipeline X} is at step {N} ({roleName}), {Pipeline Z} is running.
   Want a status update or something new?"
3. **Medium — recent completions, nothing in flight:** "Welcome back. {Pipeline X} finished while you were away. What's next?"
4. **Medium — idle resume:** "Welcome back. Last we were on {lastSpark.title} — pick up there, or something new?"
5. **First-ever session:** "Hey {firstName}, welcome to Tacticl. What do you want to build?"
6. **Same-day resume (user never left):** no greeting.
7. **Lowest — generic resume:** "Hey {firstName}, what are we working on?"

Variable substitution uses **human-readable `PipelineRun.name`** (~30 chars), never UUIDs. Bootstrap queries
cross-session user-scoped state (SAD §6.4 step 3): `loadUserActivePipelines(userId)`, `findOpenCheckpointsByUserId(userId)`,
`findRecentCompletions(userId, since=last-greeting-time)`.

**Voice optimization (PRD §5.9, SAD §6.4):** ElevenLabs audio for the 3–4 standard template bodies is pre-generated per voice
and cached at CDN edge (`/static/greetings/{voiceId}/{templateId}.mp3`, plan Task 6.4); variable parts ({firstName},
{Pipeline X}, {lastSpark.title}) spoken via live TTS. **Acceptance: greeting plays in <500ms on page mount** (PRD §6.1,
plan Task 6.8).

---

## 9. Cost numbers (PRD §9.1)

A typical **30-turn voice session** (15 user + 15 assistant) — PRD §9.1 breakdown:

| Component | Per-session usage | Per-session cost |
|---|---|---|
| Deepgram nova-2 streaming STT | ~10 min @ $0.0043/min | $0.04 |
| ElevenLabs Turbo TTS | ~2,000 chars @ $0.18/1k | $0.36 |
| Anthropic Sonnet 4.6 (Product Manager) | ~25 turns, avg 4k in + 800 out | ~$1.10 |
| Anthropic Sonnet 4.6 (skill activities) | 2–3 tool calls, ~2k tokens each | ~$0.30 |
| Mongo / Temporal writes | negligible | ~$0.00 |
| **Total (median)** | | **~$1.80** |

- Heavy session (analyze_competitors + several web_search) reaches **$4–5**.
- **Per-session cost ceiling default = $5** (PRD §5.8, §9.1; SAD §12.1 `session-cost-ceiling-usd=5.0`) — above the ~$1.80
  median, caps the heavy tail. Configurable per user. Hard kill = move session to `MUTED` (text-only), surface notice;
  pipeline execution unaffected.
- **Text-only session ≈ $0.20–0.40** (drops STT+TTS).
- **Pipeline cost is SEPARATE**, own ceiling `pipelineCostCeiling` **default $50/run** — the session ceiling never covers
  what Arbiter spends inside containers.
- Success-metric targets (PRD §9): cost/active voice session (median) **< $2.50**; cost/text-only session (median) **< $0.30**.
- Dropping to Haiku for some turns (e.g. `summarize_pipeline_progress`, short answers) cuts session cost 30–40%.

---

## 10. Tear-out / cutover (PRD §7, SAD §10) — non-negotiables

**Single-deploy, single-cut. No feature flags for the orchestrator/pipeline cutover. No dual-system coexistence.** The old
`ConversationService` in-process state machine + `PdlcV2Service` direct path haven't worked end-to-end and are **physically
deleted in the same JAR** that ships the new orchestrator (PRD §6.1, §7; SAD §10; plan §intro, Phase 7).

**Deleted outright (PRD §7.1):** `.claude/worktrees/agent-ad671ea0/` (~3,500 LoC abandoned `@Async` orchestrator),
`RoleIdentityLoader.java`, `role-identities/*.md`, `PlaybookSpecResolver` hardcoded map, `ConversationService` state machine,
`PdlcV2Service` orchestration body, Whisper in any live-conversation path, any rollout feature flag.

**Refactored — shape preserved, body replaced (PRD §7.2):** `ConversationService` REST controllers (endpoints
`POST /v1/conversations`, `POST /v1/conversations/{id}/messages` preserved → reduced to ~30-LoC signal dispatcher);
`PdlcV2Service` (→ thin `ArbiterGrpcClient` body for `SubmitToArbiterActivity`, ~80% removed); `PipelineEventEmitter` + channels
(kept); `TelegramConversationAdapter` (→ thin signaler); `SparkController`/`AgentController`/`AgentCommandService` (→ signal
dispatchers, endpoints preserved).

**Untouched (PRD §7.3):** Arbiter + `client-ai-arbiter`; `PipelineRun`/`PipelineEvent`/`PipelineCheckpoint` entities + repos;
existing pipeline Mongo schema (other than the PM→PO rename); the 12 PDLC role markdown content (migrated verbatim);
`CloudOrchestratorService` (wrapped as `StartCloudSkillActivity`, not modified).

**Cutover migration-log keys (5)** that must exist after deploy (PRD §7.4, plan Task 7.5):
`pdlc-role-pm-to-po-v1`, `personas-pdlc-v1`, `personas-conversational-v1`, `skills-v1`, `playbooks-v1`. Post-deploy verify:
`personas` has **14 entries**, `skills` has **~15**. All migrations idempotent + guarded by `migration_log`.

**Rollback (PRD §7.4, §8.1; plan Task 7.6):** there is no roll-back-to-old-system — the old system doesn't work. Emergency
target = "previous JAR with chat/pipeline disabled entirely" (acceptable short-term). PM→PO is the only data change; reverse
via `deployment/scripts/rollback-pm-po.js`.

**Whisper:** removed from the live voice path; `client-whisper` retained for Telegram voice messages + future file uploads
(PRD §3.4, §6.1; SAD §11).

> **Override B reminder:** plan/SAD describe this cutover as a single Java JAR deploy. Under `00`, the engine moves to
> arbiter (TS) and tacticl-core stays a full product backend; the *cutover discipline* (single-cut, no flags, delete the old
> paths, migrate PM→PO, idempotent migrations) is still canonical — but the *deliverable shape* (one Java JAR) is superseded
> by the cross-repo migration described in `docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md`.

---

## 11. Other canonical points worth carrying forward

- **Persona routing is a pure function, NOT an LLM call** (PRD §5.5, SAD §7) — `PersonaRouter.route(state, lastUserTurn,
  pipelineState, recentTurns) → RoutingDecision` (`InvokePersona` | `ControlAction`). Order: (1) control-intent regex →
  ControlAction, (2) hard rule `PIPELINE_BLOCKED → product-manager`, (3) sticky persona (market-researcher keeps turn unless
  topic shift), (4) market-intent regex → market-researcher, (5) default product-manager. Regexes are static finals in
  `PersonaRouter` (SAD §7.1). Saves ~300ms + ~$0.0001/turn. LLM fallback only if >5% misroutes telemetry (SAD §7.3) — not v1.
  Per `00` item 7, this folds into the workflow as a pure imported function.
- **Data-driven playbooks (PRD §3.2 G6, SAD §8):** the 8 hardcoded `PlaybookSpecResolver` playbooks → `playbooks` Mongo
  collection (`Playbook` { id, displayName, description, sparkTypes, phases, active, version }, `PhaseConfig`, `RoleSlot`).
  Console CRUD deferred to v1.5; v1 edits via direct Mongo.
- **Temporal topology (SAD §3.1–§3.4):** self-hosted Postgres-backed cluster on Hetzner, co-located with Arbiter (decision A1);
  namespaces `tacticl-prod` / `tacticl-qa`; 90-day history retention; task queues `cloud-agent-session-tq`, `pipeline-tq`,
  `voice-activity-tq`. Workers in-JVM for v1 (decision A2). Workflows are deterministic — no `new Date()`/`Random`/direct I/O;
  use `Workflow.currentTimeMillis()` + activities (plan Critical Conventions).
- **Vault paths (SAD §12.2):** `secret/strategiz/deepgram` → `api-key`; `secret/strategiz/elevenlabs` → `api-key`;
  `secret/tacticl/temporal` → mTLS certs (v1 plain trust on private network).
- **Non-goals (PRD §3.4, §6.2):** wake-word, multi-party/shared voice, speaker diarization, user voice cloning, replacing
  Arbiter, replacing `CloudOrchestratorService`, removing Whisper entirely, mobile sphere (v2), persona-specific voices (v1.5),
  parallel personas in one turn (v1.5), Temporal SaaS.
- **Doc internal inconsistency to be aware of:** PRD §1.5 table row labels persona selection as `"ROUTER" persona` while
  PRD §5.5 / SAD §7 are explicit that routing is a function, not a persona. Trust §5.5 / §7 — there is NO `ROUTER` persona;
  the 14-persona count is 2 conversational + 12 PDLC with no router.
