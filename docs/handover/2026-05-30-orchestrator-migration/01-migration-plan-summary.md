# 01 — Migration Plan Digest

**Source plan (verified, 794 lines):** `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md`
**Companion (do not duplicate):** `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/handover/2026-05-30-orchestrator-migration/00-session-decisions.md`
**Date of plan:** 2026-05-30

This is a faithful digest of the plan. Section numbers (§) reference the source plan so a reader can jump to the original. Where this digest's facts diverge from the plan, that is called out under **VERIFICATION FINDINGS** at the bottom — read that section before executing anything.

---

## The decision (§1, §2)

The **Cloud Agent Orchestrator** (persona brain + voice plane + Temporal workflows) moves **out of `tacticl-core` (Java) into `cidadel-ai-arbiter` (TypeScript/Node)**. `tacticl-core` becomes a product shell (auth, REST/WS, Telegram, Mongo product-side state, existing skill executors). This is **Option B** (§1.2).

Four reasons for the pivot (§1.1):
1. All LLM calls should route through Arbiter (the Java conversational path called Anthropic *direct* via `cidadel-client-anthropic-direct`, bypassing multi-provider fallback — already hit 429s, band-aided by commit `088259b` swapping to Haiku).
2. The Node ecosystem is where AI SDKs are first-class (Claude Code SDK is Node-native; Anthropic/OpenAI/Gemini/Grok all lead in TS). Arbiter already aggregates providers in `packages/core/src/providers/`.
3. A Java orchestrator would be ~80% "gRPC to Arbiter → wait for callback" — cross-language friction every conversation turn.
4. The orchestrator is platform-level (Tacticl, Strategiz, future products), so it belongs in Cidadel alongside Arbiter, not inside one product backend.

**Option C** (kill `tacticl-core` Java entirely) is **deferred but explicitly preserved** — see §10. Every choice is made to not foreclose C.

LLM call paths after migration (§2): PDLC pipeline → Arbiter pipeline service → Claude Code containers; Conversational → Arbiter conversation service → core providers (with fallback); Voice STT/TTS → Arbiter voice bridges → Deepgram + ElevenLabs. Single LLM ingress, single execution plane.

---

## What's preserved vs. thrown away (§3)

### Preserved — design carries; ports verbatim or with light translation
- PRD `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-prd.md` and SAD `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md` — language-neutral, stay canonical for the design.
- **Persona prompts ported verbatim:** Product Manager (~600 words), Market Researcher (~700 words), and 12 PDLC role markdowns → `cidadel-ai-arbiter/packages/personas/{conversational,pdlc}/`.
- **PersonaRouter logic** (regex patterns, hard rules, sticky persona) — direct Java→TS port, pure function, same 19 test cases. `RoutingDecision` sealed interface → TS discriminated union.
- Persona/Skill entity shapes (Java records → TS interfaces + Zod). Migration-runner concept (seed personas/skills/playbooks into Mongo). Deepgram/ElevenLabs WS *protocol knowledge* (the wire notes carry; the code does not). Voice-plane design rules (auto-mute, barge-in via cancellation, tool-use streaming SAD §5.10, long-running-skill UX SAD §5.11). Multi-pipeline/multi-session model (SAD §3.6). Mongo projection model (SAD §3.7). Session↔Spark model (PRD §5.7.1). Templated greeting (PRD §5.9). Cost numbers (PRD §9.1).

### Thrown away — ~2 days of Java Wave-1+2 work (the plan's claim)
The plan lists these for deletion: `data/data-cloud-orchestrator/`, `business/business-cloud-orchestrator/` (incl. Java PersonaRouter), `client/client-deepgram/`, `client/client-elevenlabs/`, `business/business-voice/` (scaffold only), `service/service-cloud-orchestrator/` (scaffold only), `application-api/.../temporal/` (Temporal Java SDK bootstrap + SmokeWorkflow), Temporal Java SDK deps in `gradle/libs.versions.toml`, Caffeine cache dep, and ~80 Wave-2 tests.
**⚠️ See VERIFICATION FINDINGS — none of these six modules nor the temporal/caffeine deps exist in the working tree or git history. The "thrown away" list appears to be aspirational, not a description of code that was committed here.**

### Wave-1+2 fix-ups in tacticl-core that are PRESERVED (§3, do NOT revert)
These are framed as genuine prod fixes that stay because the entity shapes are still needed for read-side projections (Arbiter writes projections that Java reads):
- `PdlcRole.PM` → `PdlcRole.PO` enum rename. **VERIFIED present:** `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/model/PdlcRole.java` contains `PO("Product Owner", "po", ...)`.
- `pipeline_runs.role` Mongo data migration (`"PM"` → `"PO"`).
- `PipelineRun` field additions: `name`, `creatingSessionId`, `userId` index, `personaVersions`, `blockedCheckpointId`.
- `PipelineCheckpoint.userId` denormalization + compound index.
- `Spark.conversationSessionId` field.
- Deprecated bridge methods on `ConversationSession` (`addMessage`, `markActive`, etc.) — kept so legacy `ConversationService` keeps compiling until §5 deletes it.
- `pm.md` → `po.md` filename rename in `business-pipeline/src/main/resources/role-identities/`. **VERIFIED:** `po.md` exists on disk, `pm.md` does not; the initial git-status snapshot shows `D pm.md` / `A po.md` (rename staged).
- `PdlcV2Service.resolveCheckpoint` `"PENDING"` → `CheckpointStatus.OPEN.name()` guard (regression fix).
- `ConversationEventChannel` migration to `appendTurn` + `changeStatus` API.

---

## Target structure inside `cidadel-ai-arbiter` (§4)

Three NEW npm workspaces alongside existing `core` + `server`:

- **`packages/conversation/`** (`@cidadel/ai-arbiter-conversation`) — the orchestrator engine (§4.2):
  - `src/types.ts` (shared TS types), `src/persona/` (`persona-registry.ts`, `skill-registry.ts`, `persona-router.ts` pure fn, `routing-decision.ts`).
  - `src/workflow/` (`cloud-agent-session-workflow.ts` + `.types.ts`, `pipeline-workflow.ts` child, `temporal-client.ts`).
  - `src/activities/` (invoke-persona, persist-turn, emit-turn-event, load-user-active-pipelines, start-pipeline-workflow, start-cloud-skill, dispatch-to-device, resolve-checkpoint, read-pipeline-state, cancel-pipeline, emit-greeting; plus `skill-backing/` per-skill activities). Note: `route-persona-activity.ts` is explicitly NOT an activity — folded into the workflow as a helper.
  - `src/grpc/` (`conversation-service-impl.ts`, `conversation-streams.ts`, `mongo-projections.ts`).
  - `src/migration/` (`seed-personas.ts`, `seed-skills.ts`, `seed-playbooks.ts`, `pm-to-po-rename.ts` idempotent/guarded).
  - `src/__tests__/` (persona-router 19 tests, workflow via `@temporalio/testing` TestWorkflowEnvironment, activities Vitest).
- **`packages/voice/`** (`@cidadel/ai-arbiter-voice`) — STT/TTS bridges + voice WS (§4.3): `src/deepgram/` (client + stream-bridge + types), `src/elevenlabs/` (client + per-utterance state-machine bridge per SAD §5.10.5 + types), `src/audio/` (`outbound-audio-queue.ts` bounded 500ms backlog, `pcm-utils.ts`), `src/server/voice-ws-handler.ts` (`/ws/cloud-agent/{sessionId}`).
- **`packages/personas/`** (`@cidadel/ai-arbiter-personas`, asset-only) — `conversational/{product-manager,market-researcher}.md`, `pdlc/{po,researcher,architect,designer,planner,implementer,reviewer,tester,security_analyst,technical_writer,devops,retro_analyst}.md` (12), `skills/catalogue.json` (~15 records per SAD §4.4b), `playbooks/catalogue.json` (8 playbooks per `PlaybookSpecResolver`). Migration scripts read these and seed Mongo on startup.

**Proto (§4.5):** new `arbiter-conversation.proto` defining `ArbiterConversationService` with RPCs: `StartSession`, `ResumeSession`, `SendUserText`, `VoiceStream` (bidi streaming), `GetUserActivePipelines`, `ResolveCheckpoint`, `CancelSession`, `CancelPipeline`. Message shapes derive from SAD §5.1 (WS protocol) + §3.6 (multi-pipeline ops).
**⚠️ Proto path is contradictory in the plan: §4.5 says `packages/server/proto/v1/`, but the §2 "After" diagram says `proto/v1/` at repo root. 00-session-decisions §"Conflicts" #5 flags this as a must-verify (where does Arbiter keep protos?). NOT verified here — `cidadel-ai-arbiter` was not inspected by this section.**

**Existing arbiter pieces (§4.6):** `packages/core/src/providers/*` UNCHANGED (invoke-persona-activity calls them via existing routing). The existing 345-line `packages/server/src/orchestrator/orchestrator-session.ts` ("OrchestratorSession", an arbiter-internal Claude Code session for template refinement) UNCHANGED — different purpose, coexists; **name-collision risk flagged in stop conditions (§15 #3)**. Mongo client, Qdrant client, Vault config, OTel — all REUSED.

**Temporal (§4.7):** Node SDK (`@temporalio/{client,worker,workflow,activity,testing}`). Same Hetzner Postgres-backed cluster (SAD §3.1, language-neutral). v1 worker starts inside `packages/server` bootstrap (sibling `packages/conversation-worker` possible later for isolation). Task queues unchanged: `cloud-agent-session-tq`, `pipeline-tq`, `voice-activity-tq`. Workflow IDs unchanged: `session-{sessionId}`, `pipeline-{sparkId}`. "The original SAD §3 stands. Only the SDK language changes."

---

## What stays in tacticl-core (§5, §5.1)

**Deleted from tacticl-core (§5 table):** the 6 modules (`business-cloud-orchestrator`, `data-cloud-orchestrator`, `business-voice`, `client-deepgram`, `client-elevenlabs`, `service-cloud-orchestrator`), `application-api/src/main/java/io/tacticl/application/temporal/`, the `application-api/build.gradle.kts` Temporal deps, the `gradle/libs.versions.toml` temporal-sdk/temporal-testing/caffeine entries, and the `settings.gradle.kts` includes for those 6 modules. **⚠️ See VERIFICATION FINDINGS — these targets are not present.**

**Refactored in tacticl-core (§5):**
- `business/business-conversation/ConversationService.java` — legacy in-process state machine gets deleted (Phase-4 cutover); slimmed version becomes a gRPC adapter calling `ArbiterConversationService.SendUserText`.
- `service/service-conversation/ConversationController.java` — REST endpoints (`POST /v1/conversations`, etc.) preserved for web/mobile; handler bodies become gRPC dispatch (~30 LoC each).
- `client/client-ai-arbiter/` — extended with generated Java stubs for `arbiter-conversation.proto`; pipeline client unchanged.
- `business/business-pipeline/PdlcV2Service.java` — stays as legacy direct-to-arbiter pipeline submitter until Arbiter's orchestrator owns pipeline creation; runs side-by-side, then deleted.
- `service/service-pipeline/PipelineController.java` — stays as read-only REST projection of Mongo state.
- `business/business-agent/CloudOrchestratorService.java` — **UNCHANGED**; continues executing social/research/video/browser skills; now called BY Arbiter via gRPC when a persona invokes `start_cloud_skill`.
- `service/service-telegram/TelegramWebhookController.java` + `business/business-telegram/TelegramConversationAdapter.java` — become adapter shims translating inbound Telegram → `ArbiterConversationService` gRPC. **⚠️ VERIFIED: there is no `service-telegram` module; Telegram lives in `business/business-telegram` only. See findings.**

**New tacticl-core module (§5):** `client/client-cloud-orchestrator/` (optional — could fold into `client-ai-arbiter`) — generated Java stubs for the conversation service.

**End state (§5.1) — the product shell:** auth (PASETO via cidadel framework); REST controllers (sparks, conversations thin, pipeline read-only, settings, console, repos, tokens); WebSocket handler for **devices** only (voice WS goes direct to Arbiter); Telegram bot adapter; Mongo writes for product-side state; gRPC client to Arbiter; `CloudOrchestratorService` skill executors; cidadel framework consumption. No longer owns: conversation brain, personas, voice plane, LLM hedging, Temporal workflows.

---

## Voice WS routing decision (§6)

Browser voice sphere connects **directly to Arbiter** (Option B: `wss://arbiter.cidadel.io/ws/cloud-agent/{sessionId}`), NOT relayed through tacticl-core. Rationale: tight latency budget (<1200ms p50); a Java relay adds ~30-60ms/frame and adds no value to audio bytes. Text mode (lower frequency) may stay routed through tacticl-core for product-level rate-limit/audit. Auth: tacticl-web calls `POST /v1/conversations` on tacticl-core → gets a short-lived (5min) signed session token (userId, sessionId, productId, expires) → connects voice WS to Arbiter with token in handshake → Arbiter validates against shared Vault signing key (PASETO; TS PASETO libs exist).

---

## Integration contract: tacticl-core ↔ Arbiter (§7)

- **INBOUND (tacticl-core → Arbiter:50051):** `StartSession`/`ResumeSession`, `SendUserText`, `ResolveCheckpoint`, `CancelSession`/`CancelPipeline`, `GetUserActivePipelines`.
- **OUTBOUND (Arbiter → tacticl-core gRPC, endpoint TBD):** `StartCloudSkill(skill,args,userId,sparkId)` (tacticl-core owns social/research/video executors), `DispatchToDevice(deviceId,taskSpec)`, `CreateSpark(input,userId,type)` (or Arbiter writes Spark directly — see below).
- **DIRECT (browser ↔ Arbiter):** Voice WebSocket (bidi audio + control).
- **Sparks ownership decision: Option A** — Arbiter writes Sparks directly to the shared Mongo `sparks` collection (projection pattern, SAD §3.7); tacticl-core reads for REST projection. Auth at Mongo layer: Arbiter write-scoped credential, tacticl-core read-scoped credential.

---

## Per-wave checklists (§8) — summarized

12 agents total (10 builders + 1 reviewer + 1 scaffolder) across 4 waves. Sequencing (§9): Day 1 Wave 0; Days 2-3 Wave 1 (parallel); Days 4-5 Wave 2 (parallel; Agent 9 soft-depends on Agents 6-8 ~80% done); Day 6 Wave 3 + reviewer; Day 7 cutover + 24h monitoring. ~1 week of focused swarm work.

### Wave 0 — Cleanup + scaffold (Agent 1, sequential, blocks everything)
Delete the 6 tacticl-core modules; remove Temporal Java deps from `libs.versions.toml`; remove module entries from `settings.gradle.kts`; run `./gradlew compileJava test` green (fix-ups stay). In arbiter: scaffold `conversation/`, `voice/`, `personas/` (package.json, tsconfig, empty index.ts/__tests__, register in root workspaces); add Temporal Node SDK + `lru-cache`/`zod`/`mongodb`/`ws` to `conversation`; add `ws`+Deepgram/ElevenLabs deps to `voice`; add `@cidadel/ai-arbiter` workspace dep to both; create `arbiter-conversation.proto` (§4.5 surface); `npm install && npm run build` clean; single commit per repo.

### Wave 1 — Foundation (Agents 2-5, parallel)
- **Agent 2 — Persona assets + migration scripts:** copy PM + Market Researcher markdowns; copy 12 PDLC role markdowns (preserve `po.md`, do NOT revert to `pm.md`); author `skills/catalogue.json` (~15 records, SAD §4.4b) + `playbooks/catalogue.json` (8 playbooks from `PlaybookSpecResolver.java`); write `seed-personas/skills/playbooks.ts` (idempotent, guarded by `migration_log` Mongo doc per SAD §10.3); Vitest with `mongodb-memory-server` or mocks.
- **Agent 3 — TS types + Zod:** `types.ts` per SAD §9 + §4.1 (`Persona`, `Skill`, `VoicePreset`, `PlaybookV2`, `PhaseConfig`, `RoleSlot`, `Turn`, `PartialTranscript`, `ToolCall`, `TokenUsage`, `LatencyBreakdown`, `CostBreakdown`, `SessionMode`/`SessionStatus` unions, `ConversationSession`, `PipelineRunSummary`, `PipelineCheckpointSummary`); Zod for gRPC+DB boundary validation; typed Mongo collection wrappers; reuse arbiter Mongo client; Vitest edge-case parsing.
- **Agent 4 — PersonaRouter TS port + 19 tests:** pure function `persona-router.ts` (direct port of Java `PersonaRouter.route()` from SAD §7.1 — same regex, hard rules, sticky persona, default); `routing-decision.ts` discriminated union (`invoke_persona` | `control_action`); port 19 Vitest cases from `PersonaRouterTest.java` (same names). **Keep router a pure exported function — no Spring bean, no class wrapper; workflow imports it directly** (user decision).
- **Agent 5 — Registries:** `persona-registry.ts` (Mongo + `lru-cache`, 5min TTL, methods `findById`/`findByFamily`/`toolsFor(personaId)→AnthropicToolDef[]`/`invalidate`); same for `SkillRegistry` + `PlaybookRegistry`; cap 100 entries; Vitest cache/eviction/tool-resolution.

### Wave 2 — Workflow + activities + voice (Agents 6-9, parallel; depends on Wave 1)
- **Agent 6 — CloudAgentSessionWorkflow (Temporal Node SDK):** per SAD §3.3.1 + §3.6. State: sessionId, userId, mode, status, compact turns, sessionStartedSparkIds, focusedPipelineId, costAccumulator, seenTurnIds (idempotency dedup). Signals: `onUserTranscript`, `onUserText`, `onCheckpointDecision`, `onModeChange`, `onBargeIn`, `onCancel`. Queries: `currentState`, `recentTurns(limit)`, `activePersonaId`. Turn loop calls `personaRouter.route()` INLINE (no RoutePersonaActivity) then `invokePersonaActivity`, handles tool calls. `pipeline-workflow.ts` per SAD §3.3.2 (`ParentClosePolicy.ABANDON`, 7d timeout, signal-by-workflowId). Register on worker bootstrap. Tests via `@temporalio/testing`.
- **Agent 7 — All activities:** per SAD §3.4 + §4.4b. `invoke-persona-activity` (calls `@cidadel/ai-arbiter/core` routing for auto-hedge), `persist-turn`, `emit-turn-event` (FCM + WS fan-out to all user's active sessions), `load-user-active-pipelines`, `start-pipeline-workflow` (spawns child), `start-cloud-skill` (gRPC OUT to tacticl-core), `dispatch-to-device` (gRPC OUT), `resolve-checkpoint` (optimistic-locked Mongo + signal pipeline), `cancel-pipeline`, `read-pipeline-state`, `emit-greeting` (templated, pre-cached audio refs). Skill activities: `brave-search`, `jina-read`, `competitor-analysis`, `market-size`, `complete-role`, `no-op`. Each with Vitest; LLM-internal skills reuse arbiter providers directly.
- **Agent 8 — `packages/voice/`:** Deepgram client (WS, `Authorization: Token {key}`) + stream-bridge (per-session lifecycle, auto-mute via shared `SessionMuteState`, sends `onUserTranscript`/`onBargeIn` signals); ElevenLabs client (WS init/text/flush/close, `xi-api-key`) + per-utterance state machine (SAD §5.10.5, non-blocking close for barge-in); `outbound-audio-queue.ts` (500ms backlog); `voice-ws-handler.ts` (`/ws/cloud-agent/{sessionId}` per SAD §5.1, Express + ws); **PASETO token verification at handshake** (ts-paseto or similar, Vault key); wire into server bootstrap; tests incl. barge-in cancellation timing. **This agent owns the PASETO-interop verification gate (§11, §15 #2).**
- **Agent 9 — gRPC service + tacticl-core integration:** implement `ArbiterConversationService` (`conversation-service-impl.ts`) mapping all RPCs to workflow signals/queries/Mongo reads; register in arbiter gRPC bootstrap. In tacticl-core: generate Java stubs from proto (existing protobuf-gradle-plugin); refactor `ConversationController` → gRPC dispatch; **REPLACE** the legacy state-machine body in `ConversationService.java` with gRPC dispatch (delete gather/propose/active logic); delete now-orphaned deprecated `ConversationSession` bridge methods; update `TelegramConversationAdapter` to call Arbiter gRPC; add a NEW small inbound gRPC server endpoint on tacticl-core for `StartCloudSkill` + `DispatchToDevice`; full integration test (`POST /v1/conversations` → Arbiter gRPC → workflow start).

### Wave 3 — Hardening + cutover (Agents 10-11 parallel, then Agent 12 reviewer)
- **Agent 10 — tacticl-web sphere UI + voice WS client:** `VoiceSphere` (Three.js/R3F, SAD §6.2); `MicCaptureWorklet` (16kHz s16le PCM, 20ms chunks); `TtsPlayer` (MediaSource/SourceBuffer MP3 + pre-cached greeting); `VoiceWebSocketClient` → Arbiter (NOT tacticl-core); auth flow via `POST /v1/conversations` token; `VoiceChatPage` (SAD §6.1), route `/chat`, DELETE legacy `ChatPage.tsx`; pre-build ElevenLabs greeting MP3s; manual QA matrix.
- **Agent 11 — Deploy + observability + load test:** update `cidadel-ai-arbiter/scripts/deploy.sh`; Arbiter Dockerfile installs Temporal SDK deps; provision Temporal cluster on Hetzner (Postgres, SAD §3.1); OTel via existing setup; Grafana dashboards (latency per-turn, cost LLM+STT+TTS per user, echo events, duplicate-signal-dropped counter); load test (10 concurrent voice sessions × 10 min, p95 < 2200ms, no worker backlog); failure injection (kill Deepgram/ElevenLabs, restart Temporal/arbiter); runbooks (`conversation-orchestrator.md`, `voice-plane.md`, `temporal-cluster.md`).
- **Agent 12 — Reviewer (cross-repo):** review all PRs/commits both repos; verify zero Java orchestrator remnants (no `CloudAgentSessionWorkflow` refs in Java); verify all conversational LLM calls go through Arbiter (no `cidadel-client-anthropic-direct` outside legacy `CloudOrchestratorService` skill path); verify PersonaRouter TS port matches Java (19 tests); verify code-comment doc references updated; sanity-check `libs.versions.toml` Temporal-clean; file-by-file diff vs plan; ship/no-ship report.

---

## Risk table (§11)

| Risk | Mitigation |
|---|---|
| ~2 days Java thrown away | Cheap sunk cost; DESIGN preserved (PRD/SAD/prompts). |
| TS Temporal SDK less mature than Java | Production-grade as of 2025; lose Spring auto-config, gain ecosystem fit. |
| Cross-language gRPC contract drift | Shared proto + generated stubs both sides + integration tests. |
| Arbiter = single point of failure | Already true (owns pipeline exec); same backup/runbook story for Temporal Postgres. |
| Mongo cross-process writes (Arbiter writes, tacticl-core reads) | Standard projection pattern; optimistic locking on hot fields (checkpoint resolution); SAD §3.7. |
| Voice direct-to-Arbiter → tacticl-core can't audit voice | Acceptable v1; later add an Arbiter-side tap mirroring events to a queue tacticl-core consumes. |
| TS PASETO interop with Java | Verify before Wave 2 (Agent 8 gate); fallback to shorter-lived JWT or HMAC session token w/ shared Vault secret. |
| Hermes (Nous models) revisit | Punted; still right answer. |

---

## Rollback / Option-C preservation (§10)

Option C = kill `tacticl-core` Java entirely (port everything to Node). After Option B, the remaining Java is: (1) Auth (PASETO/cidadel framework), (2) REST controllers, (3) Telegram bot, (4) Mongo writes, (5) `CloudOrchestratorService` skill executors, (6) device WebSocket handler.

Effort to kill (§10 table): Auth 1-2wk; REST controllers 2wk; Telegram bot 1wk (`grammy`/`node-telegram-bot-api`); Mongo writes 0wk (already shared); **`CloudOrchestratorService` skill executors 6-10wk (the hard part — social APIs, Google Photos, SiliconFlow video, Playwright Java→node, GitHub; alternative: keep this one Java service as a "skill executor sidecar")**; device WebSocket 1-2wk. **Total 11-17 weeks (3-4 months).** Revisit when Option B has shipped + stabilized (~3-4 months out) and if Java feels like maintenance burden.

To NOT foreclose C, this migration: keeps tacticl-core's surface clearly demarcated; defines the gRPC contract (the same contract a future Node tacticl-core would implement); keeps Mongo schemas language-neutral; adds no new Java in the orchestrator space. When ready for C: rewrite the shell in Node, point at same Arbiter/Mongo/Temporal, turn off the JVM.

**Decision log (§13)** records each choice + rationale + who. **Quick-reference "where do I put this code?" matrix is §14. Stop conditions (§15):** halt-and-report if (1) Temporal Node SDK incompatible with Postgres backend, (2) PASETO TS lib can't decode Java tokens (Agent 8 gate), (3) existing `OrchestratorSession` name-collides with new orchestrator classes, (4) Mongo schema conflict between Java entity writes and TS projection writes, (5) Agent-1 deletions break something (`./gradlew test` must stay green), (6) `ConversationSession` legacy bridges turn out load-bearing.

**Companion doc updates required after ship (§12):** front-matter notes on the PRD/SAD; "SUPERSEDED" banner on the 2026-05-25 plan; update `tacticl-core/CLAUDE.md` overhaul banner; add a section to `cidadel-ai-arbiter/CLAUDE.md`; update `tacticl-docs/.../2026-04-13-tacticl-pdlc-architecture.md` pointer; update memory file `project_cloud_agent_orchestrator.md` with the language/repo pivot (flagged critical so future sessions don't assume Java + tacticl-core).

---

## VERIFICATION FINDINGS — read before executing (facts + conflicts the plan/00-doc do not surface)

Verified against the actual working tree at `/Users/cuztomizer/Documents/GitHub/tacticl-core` and git on 2026-05-30.

1. **The Wave-1+2 Java orchestrator code DOES exist on disk — but is UNTRACKED (never committed).** All six deletion-target modules are present as untracked (`??`) directories — `git check-ignore` confirms they are NOT gitignored, just uncommitted:
   - `business/business-cloud-orchestrator/` — VERIFIED, with real source: `src/main/java/io/tacticl/business/cloudorchestrator/routing/{PersonaRouter.java, RoutingDecision.java, Turn.java, PipelineState.java}` and `src/test/java/.../routing/PersonaRouterTest.java`, plus `src/main/resources/conversational-personas/{product-manager.md, market-researcher.md}`. (This is the Java PersonaRouter + the persona prompts the plan ports.)
   - `data/data-cloud-orchestrator/`, `business/business-voice/`, `client/client-deepgram/`, `client/client-elevenlabs/`, `service/service-cloud-orchestrator/` — all present, all untracked.
   - `application-api/src/main/java/io/tacticl/application/temporal/` — VERIFIED, real source: `TemporalWorkerConfig.java`, `TemporalProperties.java`, `smoke/SmokeWorkflow.java`, `smoke/SmokeWorkflowImpl.java`, and test `smoke/SmokeWorkflowTest.java`. (Matches the plan's "Temporal Java SDK bootstrap + SmokeWorkflow".)
   - `settings.gradle.kts` — VERIFIED 6 includes to remove: `:data:data-cloud-orchestrator`, `:client:client-deepgram`, `:client:client-elevenlabs`, `:business:business-cloud-orchestrator`, `:business:business-voice`, `:service:service-cloud-orchestrator` (total file has 27 `include(` lines).
   - `gradle/libs.versions.toml` — VERIFIED temporal + caffeine present: `temporal-sdk = "1.27.0"` plus libs `temporal-sdk`, `temporal-spring-boot-starter` (`io.temporal:temporal-spring-boot-starter-alpha`), `temporal-testing`, and `caffeine = "3.1.8"`. **Note:** the plan's §5/Wave-0 only names `temporal-sdk + temporal-testing + caffeine` — it omits `temporal-spring-boot-starter`, which also must be removed.
   **Implication:** Wave 0's deletion block is REAL work against this tree (not a no-op). Because everything is untracked, deleting it via `rm` (not `git rm`) is correct, and there is NO committed git history to recover it from — the Java PersonaRouter (`business/business-cloud-orchestrator/src/main/java/io/tacticl/business/cloudorchestrator/routing/PersonaRouter.java`) + its `PersonaRouterTest.java` are the only copy and must be ported to TS (Wave-1 Agent 4) BEFORE deletion, or copied out first. The persona markdowns (Agent 2) likewise live only here: `business/business-cloud-orchestrator/src/main/resources/conversational-personas/{product-manager.md, market-researcher.md}`. NOTE: even the migration plan doc itself (`docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md`) is UNTRACKED (`??`) — this whole work-stream is uncommitted, which is consistent with the session having crashed pre-commit.

2. **Actual current tacticl-core module set is LARGER than the plan's "After" picture — the plan under-describes it.** Real directories on disk (some untracked, e.g. `*-connections`, `*-profile`, `*-sparks`, `*-telegram` appear to be in flight):
   - `business/`: business-agent, business-browser, business-cloud-orchestrator, business-connections, business-conversation, business-pipeline, business-profile, business-social, business-sparks, business-telegram, business-voice
   - `data/`: data-browser, data-cloud-orchestrator, data-connections, data-conversation, data-pipeline, data-profile, data-social, data-sparks, data-telegram
   - `client/`: client-ai-arbiter, client-brave-search, client-deepgram, client-elevenlabs, client-gcs, client-github, client-google, client-instagram, client-jina, client-linkedin, client-siliconflow, client-telegram, client-twitter, client-whisper
   - `service/`: service-agent, service-checkpoint, service-cloud-orchestrator, service-connections, service-conversation, service-pipeline, service-profile, service-repo, service-social, service-spark, service-sparks, service-token, service-telegram
   - app module: `application-api` (note: there are BOTH `service-spark` and `service-sparks`, which is suspicious — verify which is canonical before touching spark code).

3. **Module-name notes (mostly the plan is CORRECT — earlier draft was wrong to say these don't exist):**
   - `service/service-sparks`, `business/business-sparks`, `data/data-sparks` — **DO exist** (plus a separate `service/service-spark` singular). The plan's `*-sparks` references are valid; resolve the `service-spark` vs `service-sparks` duplication.
   - `service/service-telegram`, `business/business-telegram`, `client/telegram` — `service-telegram`, `business-telegram`, and `client-telegram` all **DO exist**. The plan's references to a Telegram service module are valid (the controller filename `TelegramWebhookController.java` was not confirmed by name, but the module exists).
   - `data/data-profile`, `data/data-connections`, `service/service-checkpoint`, `service/service-repo`, `service/service-token` — **all DO exist.** The plan's §3/§5 "stays" tables are accurate here.
   - `client/client-cloud-orchestrator` — new, does not exist yet (plan acknowledges).
   - Several of these modules are untracked/in-flight (the initial git-status snapshot in the task header was truncated mid-list), so a fresh session should run a full `git status` to see exactly which are committed vs staged vs untracked before deleting or refactoring anything.

4. **PRESERVED fix-ups confirmed real (don't second-guess these):**
   - `PdlcRole` enum lives at `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PdlcRole.java` (package `entity`, NOT `model`; NOT under `business/`). VERIFIED: it is a bare enum (no constructor/fields) with exactly 12 constants in order `PO, RESEARCHER, ARCHITECT, DESIGNER, PLANNER, IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST, TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST`; no `PM` constant remains. The class Javadoc documents the `PM`→`PO` rename and notes the migration runner bulk-updates `pipeline_runs.role` + `pipeline_events.role` Mongo records `"PM"`→`"PO"` in-place (no `@JsonAlias`, single-cut deploy per SAD §4.3).
   - `po.md` rename: `po.md` exists on disk in `business/business-pipeline/src/main/resources/role-identities/` and `pm.md` is gone from disk. The rename is **STAGED but not committed** — `git status` shows `D pm.md` / `A po.md`, while `git ls-files` (which reflects the index) lists BOTH `pm.md` and `po.md`. Exactly 12 PDLC role markdowns on disk: architect, designer, devops, implementer, planner, po, researcher, retro_analyst, reviewer, security_analyst, technical_writer, tester.

5. **Conflicts from 00-session-decisions that this plan does NOT reflect (the plan predates / contradicts the locked decisions — reconcile before executing):**
   - **"Shell" language (00-decisions #2, "Corrections owed" #1):** the plan says "product shell" throughout (title of §5, §5.1, §10, §13). The locked decision is **"there is no shell — tacticl-core is a full product backend of whatever size the product needs."** The plan's §3/§5 wording must be corrected. (00-decisions #9 nuances this: tacticl-core will *feel light* by design because Tacticl commercializes the engine — but the word "shell" is banned.)
   - **Learning layer / codegen-prompt preservation (00-decisions #10, #12, "Corrections owed" #1):** this is a HARD locked constraint that the migration plan **completely omits.** The plan's §3 "preserved" table has NO entry for the arbiter learning layer. 00-decisions #12 requires: (a) NEVER drop Mongo `agent_knowledge` + the Qdrant collection, (b) the rebuilt `PipelineWorkflow` must re-wire `KnowledgeLoader` (prompt augmentation) and `RetroAgent`/`LearningProposer` (learning capture). See `docs/architecture/learning-layer-and-codegen-prompts-preservation.md`. **The plan must gain a "Preserve & integrate (arbiter learning layer)" subsection + DO-NOT-DROP list.** This is the single biggest gap between the plan and the locked decisions.
   - **"Two planes" vs "one Orchestrator" (00-decisions #3):** the plan's §2 LLM-path diagram still draws conversation and pipeline as separate ingress lines. The locked model is ONE durable `SessionWorkflow` that SPAWNS a child `PipelineWorkflow`. The plan's §4.2 structure (cloud-agent-session-workflow + pipeline-workflow child) is consistent with this, but the framing/diagrams should say "one brain, child sub-job," not "two planes."
   - **Voice is conversation-layer only; the pipeline is SILENT (00-decisions #8):** the plan does not state that there is NO audio inside the pipeline. Make explicit: Deepgram/ElevenLabs wrap the conversation; the pipeline emits TEXT events the persona narrates.
   - **productId scoping (00-decisions #10):** the plan does not thread `productId` through SessionWorkflow / PersonaRouter / knowledge store. The locked decision makes the arbiter product-aware via DATA (no `if (product==='tacticl')` branches). Persona/skill registry scoping (shared-tagged vs per-product) is an OPEN question (00-decisions "Open question").
   - **Execution mode (00-decisions "ULTRA"):** the plan's "~12 agents / 3 waves / ~1 week / pace yourself" (§8, §9) is explicitly a **FLOOR not a ceiling** per the locked directive — fan out 100-300+ agents on wide independent units, bounded only by real dependencies + correctness, with adversarial-verify per unit and ultrareview (user-triggered `/code-review ultra`) at wave boundaries.
   - **Proto location ambiguity:** plan §4.5 says `packages/server/proto/v1/arbiter-conversation.proto` while the §2 "After" diagram shows `proto/v1/` at repo root. 00-decisions does not resolve this. Must verify against `cidadel-ai-arbiter` layout.

6. **`cidadel-ai-arbiter` was NOT inspected by this section.** Every claim about arbiter internals (existing `packages/core`/`packages/server`, the 345-line `orchestrator-session.ts`, proto location, Mongo/Qdrant/Vault/OTel reuse, `scripts/deploy.sh`, the `agent_knowledge` Mongo collection + Qdrant collection from the learning layer) is taken from the plan / 00-decisions as-written and is UNVERIFIED here. A fresh session must validate the arbiter side (especially the learning-layer stores per finding #5) before Wave 0 scaffolding.
