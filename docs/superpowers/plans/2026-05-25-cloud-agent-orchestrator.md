# Cloud Agent Orchestrator — Implementation Plan

> **For agentic workers:** REQUIRED — use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the dual `ConversationService` + `PdlcV2Service` brains with a single Temporal-backed `CloudAgentSessionWorkflow`, ship a live voice plane (Deepgram + ElevenLabs), and deliver the pulsating voice sphere on tacticl-web. PDLC execution stays in Arbiter unchanged.

**Spec references:**
- PRD: `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-prd.md`
- SAD: `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md`

**Tech stack:** Java 25, Spring Boot 4.0.3, Spring Data MongoDB, Temporal Java SDK, JUnit 6, Mockito. tacticl-web: React + Three.js. No Testcontainers, no @WebMvcTest.

**Branching:** `feedback_no_feature_branches.md` — commit directly to `main`.

**Rollout model:** **single-deploy cutover, no feature flags for orchestrator/pipeline cutover.** The existing `ConversationService` state machine + `PdlcV2Service` direct path haven't worked end-to-end; we don't preserve them behind flags. They're deleted in the same JAR that ships the new orchestrator. Phase 7 is the single cutover.

The only flags retained are **provider kill switches** (`tacticl.deepgram.enabled`, `tacticl.elevenlabs.enabled`) — these gate calls to external providers for graceful degradation when they're down, not for rollout control.

**Sequencing:** Phases 0–6 build in order; Phase 7 is the single deploy.

---

## Critical Conventions (read before writing any code)

- **Services**: plain `@Service`, do NOT extend BaseService (unless cidadel `BaseService` is required)
- **Constructor injection only** — never field `@Autowired`
- **Entities**: NOT extending BaseMongoEntity — manage `@Id`, `createdAt`, `updatedAt` manually (follow `Spark.java` pattern)
- **Repositories**: extend `MongoRepository<T, String>`
- **Tests**: `@ExtendWith(MockitoExtension.class)` for non-workflow code, no Spring context. **For workflows + activities, use Temporal's test framework** (see below).
- **Jackson 3**: `tools.jackson.*` — never `com.fasterxml.jackson.databind.*`
- **AuthenticatedUser**: `io.cidadel.framework.authorization.context.AuthenticatedUser`, `@AuthUser` from `io.cidadel.framework.authorization.annotation.AuthUser`
- **Controllers**: extend BaseController from `io.cidadel.service.framework.base`
- **Optional<T>** for all findById queries
- **Packages**: `io.tacticl.{data|client|business|service}.{cloudagent|pipeline|voice}.*`
- **Temporal**: workflows are deterministic — no `new Date()`, no `Random`, no direct I/O. Use `Workflow.currentTimeMillis()` and call activities for anything non-deterministic.

### Temporal test strategy

Workflows and activities have specific test patterns. Don't mix them with vanilla Mockito-against-the-workflow-impl — that misses determinism bugs and timer issues.

**Workflow tests** use `io.temporal:temporal-testing` (`TestWorkflowEnvironment`):

```java
@BeforeEach
void setUp() {
  testEnv = TestWorkflowEnvironment.newInstance();
  worker = testEnv.newWorker(TASK_QUEUE);
  worker.registerWorkflowImplementationTypes(CloudAgentSessionWorkflowImpl.class);
  worker.registerActivitiesImplementations(mockedActivities);    // mocks
  testEnv.start();
  client = testEnv.getWorkflowClient();
}

@Test
void greeting_fires_on_session_start() {
  var stub = client.newWorkflowStub(CloudAgentSessionWorkflow.class, ...);
  WorkflowClient.start(stub::run, new SessionStartInput(...));
  testEnv.sleep(Duration.ofMillis(200));               // virtual time — instant
  verify(mockedActivities.emitGreeting).execute(any());
}
```

Key patterns:
- **Mock activities**, run the real workflow body. Use Mockito to mock each `@ActivityInterface`. Workflow logic gets exercised; activity I/O is stubbed.
- **Virtual time** via `testEnv.sleep(Duration)` — durable timers advance instantly. Don't use real-clock waits.
- **Signals + queries** — send signals via the stub, read state via `@QueryMethod`. Verify state transitions after each signal.
- **Idempotency tests** — send the same `onUserText(turnId=X)` twice; verify `invokePersonaActivity` is called once.
- **Child workflow tests** — register both parent and child workflow impls; verify parent kicks off child correctly.

**Activity tests** use plain Mockito + `TestActivityEnvironment` only when activity uses Temporal APIs (heartbeats, cancellation):

```java
var env = TestActivityEnvironment.newInstance();
env.registerActivitiesImplementations(new ElevenLabsSpeakActivityImpl(...));
var activity = env.newActivityStub(ElevenLabsSpeakActivity.class);
// can test heartbeat-cancellation via env.requestCancelActivity(...)
```

For purely-functional activities (`PersonaRouter` is pure but not a Temporal activity, so it just needs unit tests), use plain JUnit + Mockito.

**Don't test workflows by calling `new CloudAgentSessionWorkflowImpl().run()` directly** — workflows are stateful, deterministic, and use `Workflow.*` static helpers that don't work outside a worker. Always use `TestWorkflowEnvironment`.

Per-test runtime: ~200ms with virtual time, ~5s with real-clock sleeps if not using virtual time properly.

---

## Phase 0 — Cleanup (no behavior change)

### Task 0.1: Delete abandoned worktree code

- [ ] Delete `.claude/worktrees/agent-ad671ea0/` directory entirely (~3,500 LoC)
- [ ] Verify no live code references it (`grep -r "agent-ad671ea0"` across repo should return zero matches in non-doc files)
- [ ] Single commit: `chore(cleanup): remove abandoned PdlcPipelineOrchestrator worktree`

### Task 0.2: Mark superseded design docs

- [ ] Add SUPERSEDED banner to `docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-prd.md` linking to new PRD
- [ ] Add SUPERSEDED banner to `docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-sad.md` linking to new SAD (note: Arbiter sections remain canonical)
- [ ] Update memory pointers (`MEMORY.md`) — see Phase 7 Task 7.4

---

## Phase 1 — Temporal Foundation

### Task 1.1: Provision Temporal cluster on Hetzner

- [ ] Provision Postgres instance on Hetzner (dedicated for Temporal — separate from tacticl Mongo and Arbiter Postgres if any)
- [ ] Stand up Temporal services via Docker Compose: `temporal-frontend`, `temporal-history`, `temporal-matching`, `temporal-worker`, `temporal-web` (UI)
- [ ] Co-locate on the same Hetzner host as Arbiter (per SAD §3.1 / decision A1)
- [ ] Create namespaces: `tacticl-prod`, `tacticl-qa`
- [ ] Configure 90-day history retention
- [ ] Document cluster runbook: backup/restore for Postgres, cluster restart procedure
- [ ] Verify Temporal Web UI reachable at `temporal.hetzner.internal:8080` over private network

### Task 1.2: Gradle dependencies

- [ ] Add to `gradle/libs.versions.toml`:
  - `temporal-sdk = "1.27.0"` (latest stable as of 2026-05)
  - `temporal-spring-boot = "1.27.0"`
- [ ] New version-catalog aliases: `io.temporal:temporal-sdk`, `io.temporal:temporal-spring-boot-starter-alpha`
- [ ] Add temporal-sdk to `business/build.gradle.kts` shared deps
- [ ] Add temporal-spring-boot starter to `application/build.gradle.kts`

### Task 1.3: Bootstrap worker

- [ ] Create `application/src/main/java/io/tacticl/application/TemporalWorkerConfig.java`:
  - `@Bean WorkflowServiceStubs workflowServiceStubs()` — points at `tacticl.temporal.host:port`
  - `@Bean WorkflowClient workflowClient(stubs)`
  - `@Bean WorkerFactory workerFactory(client)`
- [ ] Register three workers (one per task queue from SAD §3.2): `cloud-agent-session-tq`, `pipeline-tq`, `voice-activity-tq`
- [ ] Workers register no workflows/activities yet — empty placeholders
- [ ] Add config to `application-prod.properties`, `application-qa.properties`:
  ```
  tacticl.temporal.host=temporal.hetzner.internal
  tacticl.temporal.port=7233
  tacticl.temporal.namespace=tacticl-prod
  ```

### Task 1.4: Smoke test

- [ ] Create `SmokeWorkflow` (no-op, returns "pong") + `SmokeWorkflowImpl`
- [ ] Register on `cloud-agent-session-tq`
- [ ] Integration test in `application/`: start workflow, await result, assert "pong"
- [ ] CI passes against QA Temporal cluster

---

## Phase 2 — Persona & Playbook Registries

### Task 2.1: Data layer

- [ ] Create `data/data-cloud-orchestrator/` module
- [ ] Entities (per SAD §4.1, §4.4b, §8.1):
  - `Persona` (`@Document("personas")`) with `skillIds: List<String>` field
  - `PersonaFamily` enum (CONVERSATIONAL, PDLC) — no UTILITY family
  - `VoicePreset` (embedded)
  - `Skill` (`@Document("skills")`) — id, name, description, inputSchema (JsonNode), activityName
  - `PlaybookV2` (`@Document("playbooks")`) — `V2` suffix to avoid collision with existing `Playbook` if present
  - `PhaseConfig` + `RoleSlot` (embedded)
- [ ] Repositories: `PersonaRepository`, `SkillRepository`, `PlaybookV2Repository`
- [ ] Indexes: `personas.id` unique, `personas.family`, `skills.id` unique, `playbooks.id` unique, `playbooks.sparkTypes`
- [ ] Unit tests for repositories (mock Mongo template)

### Task 2.2: Registry services

- [ ] Create `business/business-cloud-orchestrator/` module
- [ ] `PersonaRegistry`:
  - Caffeine cache (5min TTL, 100 entry cap)
  - `Optional<Persona> findById(String id)`
  - `List<Persona> findByFamily(PersonaFamily family)`
  - `List<Tool> toolsFor(String personaId)` — resolves persona's skillIds against SkillRegistry, returns Anthropic tool-use defs
  - Cache eviction on `save(Persona)`
- [ ] `SkillRegistry`:
  - Caffeine cache
  - `Optional<Skill> findById(String id)`
  - `List<Skill> findByIds(Collection<String> ids)` — used by `PersonaRegistry.toolsFor`
- [ ] `PlaybookRegistry`:
  - Caffeine cache
  - `Optional<PlaybookV2> findById(String id)`
  - `List<PlaybookV2> findBySparkType(SparkType type)`
- [ ] Unit tests for cache behavior, eviction, tool resolution

### Task 2.3: Migration runner — PDLC personas

- [ ] Create `business/business-cloud-orchestrator/.../migration/PersonaMigrationRunner.java`
- [ ] On startup (idempotent, guarded by `migration_log` entry):
  - Read each of the 12 markdown files in `business-pipeline/src/main/resources/role-identities/*.md`
  - Build `Persona` entries per SAD §4.3 mapping table — id mappings include `pm.md` → `product-owner`, kebab-case for compound names
  - `family=PDLC`, `defaultModel` from `AiSdlcStepDefaults`, `skillIds` per SAD §4.3 (initially `["read","write","bash","web_fetch"]` for builder roles; tighter for advisory)
  - Insert into `personas`
- [ ] **PM → PO Mongo migration**: update `pipeline_runs.role` and `pipeline_events.role` fields in-place where value is `"PM"` to `"PO"`. Run BEFORE persona insert.
- [ ] **Rename `PdlcRole.PM` → `PdlcRole.PO`** in Java enum. Update all references in `business-pipeline`, `service-pipeline`, tests.
- [ ] Migration log entries: `pdlc-role-pm-to-po-v1`, `personas-pdlc-v1`
- [ ] Integration test: empty Mongo + migration runs → 12 PDLC personas present (with `product-owner` id, not `pm`); existing `pipeline_runs` with `"PM"` role updated to `"PO"`.

### Task 2.4: Author conversational personas (just 2)

- [ ] Author **Product Manager** system prompt (per SAD §4.4.1) → `business-cloud-orchestrator/src/main/resources/conversational-personas/product-manager.md`
- [ ] Author **Market Researcher** system prompt (per SAD §4.4.2) → `business-cloud-orchestrator/src/main/resources/conversational-personas/market-researcher.md`
- [ ] Add to migration runner as `personas-conversational-v1` migration — both personas with `family=CONVERSATIONAL`, `defaultModel=claude-sonnet-4-6`, `voicePreset` = stock Adam voice in v1, `skillIds` per SAD §4.4.1/§4.4.2
- [ ] Integration test: registry returns Product Manager with `toolsFor()` returning 8 tool defs; Market Researcher with 7.

### Task 2.4b: Seed the skill catalogue

- [ ] Author ~15 `Skill` records per SAD §4.4b table:
  - Chat skills: `ask_clarification`, `propose_implementation`, `start_pipeline`, `start_cloud_skill`, `dispatch_to_device`, `summarize_pipeline_progress`, `mediate_pipeline_checkpoint`, `answer_in_conversation`
  - Research skills: `web_search`, `read_page`, `analyze_competitors`, `estimate_market_size`, `synthesize_findings`, `propose_validation_experiment`
  - PDLC skill: `complete_role` (used inside Claude Code container)
- [ ] Each skill: `inputSchema` is Anthropic tool-use JSON Schema; `activityName` points to the backing Temporal activity (some are `NoOpActivity` — LLM-internal skills)
- [ ] Add to migration runner as `skills-v1` migration
- [ ] Integration test: empty Mongo + migration → 15 skills present, each resolvable by id

### Task 2.5: Migration runner — playbooks

- [ ] Extract the 8 playbook definitions from current `PlaybookSpecResolver.java`
- [ ] Build `PlaybookV2` entries preserving phase/role structure
- [ ] Insert into `playbooks`
- [ ] Migration log entry `playbooks-v1` written on success
- [ ] Integration test: empty Mongo + migration runs → 8 playbooks present, structurally equivalent

### Task 2.6: Cutover

- [ ] Replace `RoleIdentityLoader.load(role)` call sites with `personaRegistry.findById(role.toPersonaId()).orElseThrow()`
- [ ] Replace `PlaybookSpecResolver.resolve(playbookId)` call sites with `playbookRegistry.findById(playbookId).orElseThrow()`
- [ ] Delete `RoleIdentityLoader.java`
- [ ] Delete `business-pipeline/src/main/resources/role-identities/*.md` (content now in Mongo)
- [ ] Delete the hardcoded map in `PlaybookSpecResolver` (file may remain as deprecated shim for one release, then deleted)
- [ ] All existing pipeline tests pass
- [ ] Commit: `refactor(cloud-agent): personas + playbooks now Mongo-backed via registries`

---

## Phase 3 — Pipeline Workflow (Temporalize existing pipeline)

### Task 3.1: Workflow interface + impl

- [ ] In `business/business-pipeline/`, create:
  - `PipelineWorkflow` interface (SAD §3.3.2)
  - `PipelineWorkflowImpl`
  - Signal payloads: `ArbiterCallbackSignal`, `CheckpointResolutionSignal`, `CancelSignal`
- [ ] `PipelineWorkflowImpl.run(input)`:
  - Activity: `submitToArbiter(input)` → `arbiterPipelineId`
  - Loop: wait for `ArbiterCallbackSignal`
    - Activity: `persistPipelineEvent`
    - Activity: `fanOutPipelineEvent`
    - If callback is checkpoint: parent signal (`Workflow.getParent()`) → `CloudAgentSessionWorkflow.onCheckpointRaised`; await `CheckpointResolutionSignal`
    - If callback is terminal: return `PipelineResult`
- [ ] Register workflow on `pipeline-tq`

### Task 3.2: Pipeline activities

- [ ] `SubmitToArbiterActivity` wraps existing `ArbiterGrpcClient.submitPipeline` (no logic change)
- [ ] `PersistPipelineEventActivity` wraps existing Mongo write path from `PdlcV2Service.handleCallbackEvent`
- [ ] `FanOutPipelineEventActivity` wraps existing `PipelineEventEmitter.emit`
- [ ] Each activity: `@Component`, constructor-injects existing services, has retry policy per SAD §3.4
- [ ] Unit tests with mocked Temporal `ActivityCompletionClient` where needed

### Task 3.2b: Per-role persona dispatch + version pinning

- [ ] `PipelineWorkflowImpl` iterates roles per the resolved playbook; for each role:
  - Resolve `personaId` from the role slot via `PdlcRole.toPersonaId()` (e.g., `PdlcRole.PO` → `"product-owner"`, `PdlcRole.IMPLEMENTER` → `"implementer"`)
  - Read `Persona` from `PersonaRegistry` (Mongo, cached)
  - Build `SubmitRoleRequest` with `systemPrompt = persona.systemPrompt`, `model = persona.defaultModel`, `toolAllowlist = personaRegistry.cliToolsFor(personaId)` (Claude Code CLI tool names derived from skillIds)
  - Invoke `InvokeArbiterRoleActivity` which calls `arbiterGrpcClient.submitRole(request)`
  - Arbiter spins ephemeral container, boots Claude Code CLI with the systemPrompt embedded in `boot.md` (Arbiter behaviour unchanged per SAD §4.5.3)
- [ ] Add `personaVersions: Map<String, Integer>` field to `PipelineRun` entity — populated at run start by snapshotting each resolved persona's version
- [ ] Reason: editing a persona post-run must not retroactively change what produced a past run's artifacts (reproducibility per SAD §4.5.2)
- [ ] Unit tests: persona update during a run does not affect the running pipeline; re-run uses snapshotted versions

### Task 3.3: Callback controller is a signal dispatcher (no flag)

- [ ] `PipelineCallbackController.handleCallback`:
  - Look up `workflowId` from `PipelineRun.workflowId` (new field added to entity)
  - Call `workflowClient.newWorkflowStub(PipelineWorkflow.class, workflowId).onArbiterCallback(signal)`
  - Existing legacy direct-invocation path is **deleted** from this controller in the same change.
- [ ] Add `workflowId` field to `PipelineRun` entity (required for new runs)
- [ ] Unit tests for the signal dispatch path. (No "flag off" path to test — there isn't one.)

### Task 3.4: PdlcV2Service slim-down

- [ ] `PdlcV2Service.submitPipeline(input)`:
  - Build workflow id `pipeline-{sparkId}`
  - Start `PipelineWorkflow` via `workflowClient.newWorkflowStub(...).run(input)` (async)
  - Persist `PipelineRun.workflowId = id`
- [ ] Delete the legacy direct-execution body (~80% of the file). Keep only the Temporal client wiring and the REST-shape facade methods used by `SparkController`/`PdlcRouter`.
- [ ] Keep `PdlcV2Service` REST contract intact (callers in `SparkController` and `PdlcRouter` unchanged).

### Task 3.5: Verify (no rollout — Phase 7 is the single cutover)

- [ ] On QA: exercise end-to-end pipeline through the workflow path. One full PDLC, one BUG_FIX, one cancellation, one checkpoint-and-resolve.
- [ ] Monitor Temporal Web UI + Mongo state for divergence vs expectations.
- [ ] No flag flips. No "1 week soak." The cutover happens once, in Phase 7, when all dependencies are ready.
- [ ] Commit: `feat(pipeline): pipeline execution runs as Temporal workflow (Phase 3 of orchestrator overhaul)`

---

## Phase 4 — Session Workflow (Text-Only First)

### Task 4.1: Workflow interface + impl (text path first)

- [ ] In `business/business-cloud-orchestrator/`:
  - `CloudAgentSessionWorkflow` interface (SAD §3.3.1) — signals: `onUserText`, `onCheckpointDecision`, `onModeChange`, `onCancel` (voice signals added in Phase 5); queries: `currentState`, `recentTurns`, `activePersonaId`
  - `CloudAgentSessionWorkflowImpl`
- [ ] Internal state per SAD §3.3.1
- [ ] On workflow start: emit **templated greeting** turn (per PRD §5.9 + SAD §6.4 step 4) — selects template by user state, persists as a `Turn` attributed to `product-manager`, emits via `EmitTurnEventActivity`. No LLM call. State transitions `IDLE → ENGAGED`.
- [ ] Turn loop (text-mode):
  - Wait for `onUserText` signal
  - `RoutingDecision decision = personaRouter.route(...)` — pure function, no activity (see Task 4.2)
  - If `ControlAction`: apply (mode change / cancel TTS / cancel session), continue loop
  - If `InvokePersona`: execute `InvokePersonaActivity(personaId, ...)`, handle tool-use loop (skill activities), persist turn, emit event
  - Loop

### Task 4.2: `PersonaRouter` (pure function) + session activities

- [ ] **`PersonaRouter` is a pure Spring bean, not a Temporal activity.** No Mongo, no LLM, no I/O. Implements `RoutingDecision route(state, lastUserTurn, pipelineState, recentTurns)` per SAD §7.1. Called directly from `CloudAgentSessionWorkflowImpl`.
- [ ] Unit tests for each routing branch: control intents, hard rules (PIPELINE_BLOCKED), sticky persona, market intent detection, default.
- [ ] `InvokePersonaActivity` — calls Anthropic with the resolved persona's system prompt + conversation context + `personaRegistry.toolsFor(personaId)` tool defs; returns `PersonaResponse(text, toolCalls, latencyMs, tokens)`.
- [ ] **Skill-backing activities** — implement each per SAD §4.4b table. Most are thin wrappers around existing services (`StartCloudSkillActivity` → `CloudOrchestratorService`, `BraveSearchActivity` → `BraveSearchClient`, etc.). `NoOpActivity` for LLM-internal skills.
- [ ] `StartPipelineWorkflowActivity` — `Workflow.executeChildWorkflow(PipelineWorkflow.class, ...)` (non-blocking; returns child workflow handle).
- [ ] `ResolveCheckpointActivity` — parses NL response, emits `CheckpointResolutionSignal` to the parent's child `PipelineWorkflow`.
- [ ] `EmitGreetingActivity` — selects templated greeting, persists Turn, emits `persona_text_chunk` event (or pre-cached audio ref for voice mode).
- [ ] `PersistTurnActivity`, `EmitTurnEventActivity` — Mongo writes, channel fan-out.
- [ ] All activities: retry policies per SAD §3.4.

### Task 4.3: Data model extensions

- [ ] Extend `ConversationSession` entity with new fields per SAD §9:
  - `workflowId` (string)
  - `mode` (`SessionMode` enum)
  - `activePersonaId` (string?)
  - `costAccumulator` (`CostBreakdown` embedded)
  - `costCeilingUsd` (double)
  - `turns` (List<Turn>)
- [ ] `Turn` embedded entity with all fields from SAD §9
- [ ] Migration: existing sessions get default values; `messages` field preserved as derived view
- [ ] Repository methods: `appendTurn`, `updateCost`

### Task 4.4: ConversationService → thin signal facade (legacy path deleted)

- [ ] `ConversationService.send(sessionId, text)`:
  - Look up `workflowId`
  - Send signal `CloudAgentSessionWorkflow.onUserText` via `workflowClient`
  - Return immediately; WS pushes the response asynchronously
- [ ] `ConversationService.create(userId, firstMessage?)`:
  - Generate `sessionId` + `workflowId`
  - Start `CloudAgentSessionWorkflow` async (templated greeting fires on start)
  - If `firstMessage` provided, send `onUserText` signal after start
- [ ] Existing REST endpoints (`POST /v1/conversations`, `POST /v1/conversations/{id}/messages`) preserved
- [ ] **Delete** the existing `ConversationService` in-process state machine (the gather/propose/active turn-loop body). What's left is a ~30-LoC signal dispatcher.
- [ ] No feature flag. The new path is the only path.
- [ ] Telegram (`TelegramConversationAdapter`) inherits change for free — it goes through `ConversationService`

### Task 4.5: Verify (no rollout — Phase 7 is the single cutover)

- [ ] On QA: exercise text-only flow on web + Telegram. Templated greeting plays. Product Manager handles intake. Market Researcher engages on market-intent keywords. Sticky persona works.
- [ ] Exercise checkpoint flow: pipeline raises checkpoint → Product Manager engages with `mediate_pipeline_checkpoint` skill → user NL response → pipeline resumes.
- [ ] No flag flips. No "1 week soak." Verification only; cutover is Phase 7.

---

## Phase 5 — Voice Plane

### Task 5.1: client-deepgram module

- [ ] Create `client/client-deepgram/`
- [ ] `DeepgramClient`: opens WS to `wss://api.deepgram.com/v1/listen?model=nova-2&encoding=linear16&sample_rate=16000&interim_results=true&endpointing=300&vad_events=true`
- [ ] `DeepgramSession` interface: `sendAudio(byte[] pcm)`, `onPartialTranscript(handler)`, `onFinalTranscript(handler)`, `onSpeechStarted(handler)`, `onSpeechFinal(handler)`, `close()`
- [ ] Vault config: `secret/strategiz/deepgram` key `api-key`
- [ ] Feature flag `tacticl.deepgram.enabled`
- [ ] Unit tests with mocked WS

### Task 5.2: client-elevenlabs module

- [ ] Create `client/client-elevenlabs/`
- [ ] `ElevenLabsClient`: opens WS to `wss://api.elevenlabs.io/v1/text-to-speech/{voice_id}/stream-input?model_id=eleven_turbo_v2`
- [ ] `ElevenLabsSession` interface: `sendTextChunk(String text)`, `flush()`, `onAudioChunk(handler)`, `onDone(handler)`, `close()` (cancellable)
- [ ] Vault config: `secret/strategiz/elevenlabs` key `api-key`
- [ ] Feature flag `tacticl.elevenlabs.enabled`
- [ ] Unit tests with mocked WS

### Task 5.3: business-voice module

- [ ] Create `business/business-voice/`
- [ ] `DeepgramStreamBridge` (per session):
  - Wraps `DeepgramSession`
  - On final transcript: sends `CloudAgentSessionWorkflow.onUserTranscript` signal
  - On speech_started during active TTS: sends `onBargeIn` signal
  - Auto-mute coordination via shared `SessionMuteState`
- [ ] `ElevenLabsStreamBridge` (per utterance):
  - Wraps `ElevenLabsSession`
  - Consumes streaming text chunks from `InvokePersonaActivity` output
  - Pushes audio chunks into `OutboundAudioQueue`
- [ ] `OutboundAudioQueue` — per-session bounded queue (500ms backlog cap)

### Task 5.4: VoiceWebSocketHandler

- [ ] Create `service/service-cloud-orchestrator/` module
- [ ] WebSocket handler at `/ws/cloud-agent/{sessionId}`:
  - Auth via existing cookie/Bearer at handshake
  - Binary frames → forward to `DeepgramStreamBridge` (when not muted)
  - Text frames → dispatch by `type`:
    - `text_turn` → workflow signal `onUserText`
    - `mode_change` → workflow signal `onModeChange` + reconfigure bridges
    - `barge_in` → workflow signal `onBargeIn`
    - `checkpoint_decision` → workflow signal `onCheckpointDecision`
    - `ping` → respond `pong`
  - Drain `OutboundAudioQueue` to client as binary frames
  - Emit `partial_transcript`, `persona_*`, `state_change`, `cost_update`, etc. as text frames per SAD §5.1
- [ ] On client connect: query workflow `currentState` + `recentTurns(20)` and push `state_change` + replay

### Task 5.5: Voice signals + barge-in in workflow

- [ ] Add `onUserTranscript` and `onBargeIn` signal methods to `CloudAgentSessionWorkflow`
- [ ] In voice mode turn loop:
  - On `onUserTranscript`: like `onUserText` but mark turn modality=`voice`, attach `audioRef`
  - On `onBargeIn`: cancel in-flight `ElevenLabsSpeakActivity` via heartbeat cancellation, mark current assistant turn `interrupted=true`
- [ ] `ElevenLabsSpeakActivity`:
  - Streams text → bridge → audio chunks → queue
  - Heartbeats every 1s (Temporal cancellation propagates through heartbeat)
  - On cancellation: close ElevenLabs WS, emit `tts_aborted` event

### Task 5.6: Latency instrumentation

- [ ] Record `LatencyBreakdown` per turn (SAD §9) — populate via `Workflow.currentTimeMillis()` snapshots
- [ ] Grafana dashboard: p50/p95 of `endpointMs`, `routeMs`, `llmFirstTokenMs`, `ttsFirstChunkMs`, `totalMs`
- [ ] Acceptance: p50 totalMs < 1200ms on a Hetzner→Cloud Run round trip with NYC test client

### Task 5.7: Echo telemetry

- [ ] Counter: `cloud_agent.echo_events_total` — incremented if Deepgram receives audio during active TTS
- [ ] Should always be zero; nonzero alert at >0.1/min

### Task 5.8: Cost tracking

- [ ] After each `InvokePersonaActivity`, `ElevenLabsSpeakActivity`, and Deepgram session: emit cost event to workflow via local activity → `costAccumulator` updated
- [ ] On `costAccumulator >= costCeilingUsd`: transition to `MUTED` mode, emit `cost_ceiling_reached` event
- [ ] `cost_update` WS event emitted every 5s to client

---

## Phase 6 — Sphere UI (tacticl-web)

### Task 6.1: Project setup

- [ ] Add Three.js dependency to `tacticl-web/package.json`: `three@^0.165`, `@react-three/fiber@^8.16`, `@react-three/drei@^9.105`
- [ ] Create `src/cloud-agent/` directory for all new code

### Task 6.2: VoiceSphere component

- [ ] `src/cloud-agent/VoiceSphere.tsx` — React Three Fiber canvas with a sphere mesh
- [ ] Custom shader material (`src/cloud-agent/sphereMaterial.ts`):
  - Uniforms: `uTime`, `uIntensity`, `uFrequency`, `uColor`, `uMode`
  - Vertex shader: noise-displaced surface scaled by `uIntensity`
  - Fragment shader: soft-glow with fresnel rim, color modulated by `uMode`
- [ ] Props: `state: 'idle' | 'listening' | 'thinking' | 'speaking' | 'error' | 'disabled'`
- [ ] State → uniform mapping per SAD §6.2
- [ ] Perf guard: detect `prefers-reduced-motion` → fall back to canvas2D pulsing circle
- [ ] Storybook story (or equivalent) showing all 6 states for QA

### Task 6.3: Mic capture worklet

- [ ] `src/cloud-agent/audio/MicCaptureWorklet.ts` + AudioWorkletProcessor at `public/worklets/mic-capture-processor.js`
- [ ] Worklet: downsamples to 16kHz s16le, posts 20ms PCM chunks (1280 bytes) to main thread
- [ ] Energy-based VAD: suppress silence frames (RMS < threshold)
- [ ] `useMicCapture()` hook — manages worklet lifecycle, exposes `start()`, `stop()`, amplitude observable

### Task 6.4: TTS player + pre-cached greeting audio

- [ ] `src/cloud-agent/audio/TtsPlayer.ts` — `MediaSource` + `SourceBuffer` for MP3 stream
- [ ] `appendChunk(buffer)`, `endOfStream()`, `abort()`, `onAmplitude(cb)`, `playStaticAudio(url, then?)`
- [ ] AnalyserNode for sphere `speaking` amplitude
- [ ] `useTtsPlayer()` hook
- [ ] **Pre-cached greeting audio**: pre-generate ElevenLabs MP3s for the 3-4 templated greetings per voice (PRD §5.9). Hosted at CDN edge (e.g., `/static/greetings/{voiceId}/{templateId}.mp3`). On page mount, TtsPlayer plays the cached MP3 within ~200ms while WS connects. Variable parts ({firstName}, {lastSpark.title}) are spoken via live TTS after the cached audio finishes — adds ~300ms for the variable parts but starts audio playback at ~200ms.
- [ ] Build-time tool (`tacticl-web/scripts/generate-greetings.ts`) that calls ElevenLabs once per voice × template, drops MP3s into `public/static/greetings/`. Run on CI, committed to repo (or to CDN bucket — TBD).

### Task 6.5: WebSocket client

- [ ] `src/cloud-agent/CloudAgentWebSocketClient.ts`:
  - Connect to `/ws/cloud-agent/{sessionId}`
  - Auto-reconnect with backoff (mirror existing `src/lib/websocket.ts` pattern)
  - Binary frames → route to TtsPlayer
  - Text frames → parse JSON, emit typed events
  - Outbound: `sendAudio(pcm)`, `sendText(text)`, `sendModeChange(mode)`, `sendBargeIn()`, `sendCheckpointDecision(...)`
- [ ] React context provider `CloudAgentWebSocketProvider`

### Task 6.6: VoiceChatPage assembly

- [ ] `src/pages/VoiceChatPage.tsx` replacing or extending `ChatPage.tsx`
- [ ] Layout per SAD §6.1: `<VoiceSphere />`, `<CaptionStrip />`, `<ChatHistory />`, `<ChatInput />`, `<ModeToggle />`, `<PipelineDock />`
- [ ] State machine: sphere state derived from WS events + mic/tts state
- [ ] Mode toggle wired to `sendModeChange`
- [ ] Captions stream in: partial transcripts (greyed), final transcripts (solid), persona text (assistant style)
- [ ] Existing checkpoint UI from `ChatPage` adapted into `PipelineDock`

### Task 6.7: Routing + auth

- [ ] Update router: `/chat` renders `VoiceChatPage` (replaces `ChatPage`). The legacy `ChatPage.tsx` is **deleted** in the same change — no fallback, no flag.
- [ ] WS auth: existing cookie flow; verify handshake works.

### Task 6.8: Manual QA matrix

- [ ] Page open: templated greeting plays in <500ms, sphere shows `speaking` state, then `idle`
- [ ] Voice-active: speak → see partial transcripts → see final → hear response → captions match
- [ ] Barge-in: speak during assistant response → assistant stops mid-sentence → user transcript captured
- [ ] Mode toggle voice → text → voice without losing session
- [ ] Mute: sphere idle, mic off, can still type
- [ ] Pipeline checkpoint in voice mode: Product Manager speaks the question (via `mediate_pipeline_checkpoint` skill), user answers naturally, pipeline resumes
- [ ] Market research turn: user says "validate this idea" → routing function picks Market Researcher → web_search + read_page skills fire → synthesis returned
- [ ] Graceful degradation: kill Deepgram session in network panel → UI surfaces "voice unavailable", switches to text

---

## Phase 7 — Hardening + Docs

### Task 7.1: Load test

- [ ] 10 concurrent voice sessions for 10 minutes
- [ ] Measure: Temporal worker CPU/mem, Mongo write throughput, Deepgram + ElevenLabs cost
- [ ] No turn loop p95 > 2200ms under load
- [ ] No Temporal worker queue backlog beyond 10 tasks

### Task 7.2: Failure injection

- [ ] Kill Deepgram mid-session → workflow stays alive, mode forces TEXT_ONLY, banner in UI
- [ ] Kill ElevenLabs mid-utterance → turn completes as text-only, captions render, no audio, no infinite retry
- [ ] Restart Temporal frontend → in-flight workflows survive, signals resume after reconnect
- [ ] Restart tacticl-core JVM mid-pipeline → child `PipelineWorkflow` continues (Arbiter callbacks queued in workflow signal channel)

### Task 7.3: Runbook updates

- [ ] `deployment/runbooks/temporal-cluster.md` — start/stop/backup/restore
- [ ] `deployment/runbooks/voice-plane.md` — Deepgram/ElevenLabs key rotation, cost alerting

### Task 7.4: Memory + cross-repo docs

- [ ] Update `MEMORY.md`:
  - Add `cloud_agent_orchestrator.md` memory pointer with PRD/SAD/plan refs and key decisions
  - Mark obsolete pointers: any reference to `RoleIdentityLoader`, `PlaybookSpecResolver` hardcoded
  - Update PDLC pipeline pointer to note Temporal-backed execution
- [ ] Update `CLAUDE.md` in tacticl-core: replace voice section to reflect Deepgram (live) + Whisper (file upload); update orchestrator section
- [ ] Sync `../tacticl-docs/`: add cross-repo summary doc under `architecture/` pointing at the new PRD/SAD

### Task 7.5: Single-deploy cutover

Per PRD §7.4 and SAD §10.8 — one deploy ships everything; the old code is physically absent from the JAR.

- [ ] Pre-deploy: full Mongo backup (used by Task 7.6 rollback if needed).
- [ ] Pre-deploy: dry-run migration runner on a copy of prod Mongo to confirm idempotency + PM→PO rename works.
- [ ] Deploy new JAR to QA. Verify:
  - Migration runner ran, `migration_log` shows all five entries (`pdlc-role-pm-to-po-v1`, `personas-pdlc-v1`, `personas-conversational-v1`, `skills-v1`, `playbooks-v1`)
  - `personas` has 14 entries, `skills` has ~15
  - QA sphere works end-to-end (Task 6.8 matrix passes)
- [ ] Deploy to prod. tacticl-web sphere UI deploys in the same release window.
- [ ] Post-deploy: 24-hour close monitoring of latency, echo telemetry, cost.
- [ ] Single completion commit: `feat(cloud-agent): unified orchestrator + voice plane live; legacy ConversationService + PdlcV2Service direct paths deleted`.

### Task 7.6: Rollback plan (emergency only)

- [ ] If post-deploy issues are severe and unfixable in <1 day: redeploy previous JAR.
- [ ] **Important**: the previous JAR does NOT have a working chat/pipeline either (that's why we're doing this). Rolling back returns the system to "chat/pipeline don't reliably complete." This is acceptable as a short-term emergency state.
- [ ] Mongo rollback: `migration_log` migrations are idempotent. The PM→PO Mongo update is the only data change — if rollback is needed and the old enum value is required, run a reverse migration script (provided in `deployment/scripts/rollback-pm-po.js`).

---

## Acceptance Criteria (from PRD §6.1)

- [ ] Single Temporal-backed `CloudAgentSessionWorkflow` handles conversation; `PipelineWorkflow` is the child
- [ ] `PersonaRegistry` + `SkillRegistry` (Mongo + cached) hold 14 personas + ~15 skills
- [ ] Product Manager + Market Researcher conversational personas authored and live
- [ ] `PdlcRole.PM` → `PdlcRole.PO` rename complete; Mongo data migrated
- [ ] `PlaybookRegistry` (Mongo) holds all playbooks; `PlaybookSpecResolver` hardcoded map deleted
- [ ] Voice sphere ships on tacticl-web at `/chat` (legacy `ChatPage` deleted)
- [ ] Templated greeting plays in <500ms on page mount (pre-cached audio)
- [ ] Always-on VAD voice mode end-to-end; p50 user-pause→first-TTS-chunk < 1200ms
- [ ] Modality toggle (voice/text/mute) preserves session state
- [ ] Pipeline checkpoints surface via Product Manager's `mediate_pipeline_checkpoint` skill, resolved via natural-language voice response
- [ ] Legacy `ConversationService` in-process state machine + `PdlcV2Service` direct-execution body deleted (no feature flags, no coexistence)
- [ ] Conversation transcripts persist with audio refs
- [ ] Worktree deleted
- [ ] Whisper out of live voice path, retained for file uploads
- [ ] Old PRD/SAD marked superseded

---

## Risks (from PRD §8.1)

| Risk | Mitigation in this plan |
|------|-------------------------|
| Voice latency budget tight | Phase 5 Task 5.6 — instrument early; Haiku for routing; ElevenLabs Turbo |
| Temporal cluster is new SPOF | Phase 1 Task 1.1 — backup runbook; Phase 7 Task 7.2 — failure injection |
| Echo when mute timing off | Phase 5 Task 5.7 — echo telemetry; strict mute-before-play sequencing |
| ElevenLabs cost surprise | Phase 5 Task 5.8 — per-session ceiling enforced at workflow level |

---

## Out-of-Scope (deferred)

- Mobile sphere parity (v2)
- Wake-word detection (v2)
- Persona-specific voices (v1.5)
- Parallel personas in same turn (v1.5)
- Console UI for editing personas/playbooks (v1.5 — direct Mongo edits for v1)
- Captions language detection (English-only v1)
