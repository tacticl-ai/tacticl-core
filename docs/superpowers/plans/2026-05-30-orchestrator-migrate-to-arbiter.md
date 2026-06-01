# Cloud Agent Orchestrator — Migration to Arbiter

> **For agentic workers:** REQUIRED — use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Steps use checkbox (`- [ ]`) syntax. Multi-repo plan: touches both `tacticl-core/` (Java, slimming) and `cidadel-ai-arbiter/` (TypeScript, growing).

**Date:** 2026-05-30
**Status:** Active — supersedes the orchestrator-in-tacticl-core build path from `2026-05-25-cloud-agent-orchestrator.md`
**Decision:** Option B (orchestrator engine moves to Arbiter; tacticl-core Java stays as product shell). Option C (kill tacticl-core Java entirely) deferred but explicitly preserved as a future path — see §10.

**Related specs (still canonical for the design, just the *home* changes):**
- PRD: `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-prd.md`
- SAD: `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md`
- Original (tacticl-Java) plan: `docs/superpowers/plans/2026-05-25-cloud-agent-orchestrator.md` — **now historical**

---

## 1. Why this pivot

### 1.1 The realization

We started building the Cloud Agent Orchestrator in tacticl-core (Java). After Wave 1+2 (~2 days of code), it became clear this was wrong-shaped:

1. **All LLM calls should go through Arbiter, not bypass it.** The Wave-2 plan had conversational personas (Product Manager, Market Researcher) calling Anthropic *direct* from Java via `cidadel-client-anthropic-direct`. That creates a parallel LLM path that doesn't inherit Arbiter's multi-provider fallback. We already hit Anthropic 429s on this path (commit `088259b` band-aided it with a Haiku swap).

2. **The Node ecosystem is where AI work lives.** Claude Code SDK is Node-native. Anthropic SDK, OpenAI SDK, Gemini SDK, Grok — all first-class in TypeScript, lagging in Java. The Arbiter already aggregates them cleanly (`packages/core/src/providers/`).

3. **The Java orchestrator would be a thin shell.** ~80% of `CloudAgentSessionWorkflow` would be "send gRPC to Arbiter → wait for callback." Cross-language boundary on every conversation turn = real friction.

4. **Cidadel is a product, Arbiter is a feature inside it.** The orchestrator is platform-level (will be used across Tacticl, Strategiz, and future products). It belongs inside Cidadel alongside Arbiter, not inside one product's backend.

### 1.2 Why Option B (not full Option C)

Option C would kill tacticl-core Java entirely and put everything in Node. Right answer eventually, wrong answer today:

- `tacticl-core` is ~4 months of Java. Auth (PASETO via cidadel framework), Telegram bot, social/research/video/browser skill executors, Mongo schemas, REST surface for tacticl-web/mobile/device — that's real, working code.
- Killing it day 1 = 3-6 month rewrite before shipping anything new.
- B is the pragmatic path: move the **orchestrator engine** to Arbiter (where AI work belongs), keep tacticl-core as the **product shell** (auth, REST/WS, Telegram, existing skill executors, product-specific business logic). When tacticl-core feels useless in 6 months, we kill it then.

Option C is **explicitly preserved** as a future path — see §10. Every choice in this migration is made to NOT foreclose C.

---

## 2. Before / After Architecture

### Before (Wave 1+2 — what we built but are now throwing out)

```
tacticl-core (Java)
├── business/business-cloud-orchestrator/   ← orchestrator brain (Java)
├── data/data-cloud-orchestrator/           ← Persona/Skill entities (Java)
├── business/business-voice/                ← STT/TTS bridges (Java)
├── client/client-deepgram/                 ← Java WS client
├── client/client-elevenlabs/               ← Java WS client
├── service/service-cloud-orchestrator/     ← WS handler /ws/cloud-agent (Java)
└── application-api/.../temporal/            ← Temporal Java SDK bootstrap

cidadel-ai-arbiter (Node)
└── packages/server/                         ← LLM routing + container exec only

LLM call paths:
  PDLC pipeline ─────────────────────→ Arbiter ──→ Anthropic/OpenAI/...
  Conversational persona ──────────────────────→ Anthropic (DIRECT, no fallback)  ← problem
```

### After (this plan)

```
tacticl-core (Java) — slimmed to product shell
├── service/service-conversation/           ← thin REST/WS adapter → Arbiter gRPC
├── service/service-sparks/                 ← spark CRUD + REST
├── service/service-telegram/               ← Telegram bot adapter
├── service/service-pipeline/               ← pipeline status REST (read projections)
├── business/business-sparks/, business/business-telegram/, business-conversation/ (thin)
├── data/data-sparks/, data/data-conversation/ (Mongo writes for product-side state)
├── client/client-ai-arbiter/               ← gRPC client to Arbiter (existing, extended)
├── client/client-cloud-orchestrator/        ← NEW: gRPC client for orchestrator RPCs
├── application-api/                        ← Spring Boot bootstrap (no more Temporal)
│
└── DELETED (moved to Arbiter):
    business-cloud-orchestrator/, data-cloud-orchestrator/, business-voice/,
    client-deepgram/, client-elevenlabs/, service-cloud-orchestrator/,
    application-api/.../temporal/

cidadel-ai-arbiter (Node/TS) — grows the orchestrator engine
├── packages/
│   ├── core/                               ← existing (LLM providers, routing)
│   ├── server/                             ← existing (gRPC server, pipeline RPCs)
│   ├── conversation/        NEW            ← persona registry, conversation workflow,
│   │                                          PersonaRouter, conversation gRPC service
│   ├── voice/               NEW            ← Deepgram + ElevenLabs WS bridges
│   └── personas/            NEW            ← persona prompt assets (markdown)
│                                              ports product-manager.md + market-researcher.md
│                                              ports 12 PDLC role-identities/*.md
│
├── proto/v1/
│   ├── arbiter-pipeline.proto              ← existing
│   └── arbiter-conversation.proto    NEW   ← session ops: start/resume, signal user turn,
│                                              query state, stream events
│
└── Temporal (Node SDK) — embedded in packages/conversation, started by server bootstrap

LLM call paths (after):
  PDLC pipeline       ──→ Arbiter pipeline service ──→ Claude Code containers ──→ Anthropic/...
  Conversational      ──→ Arbiter conversation service ──→ providers in core ──→ Anthropic/OpenAI fallback
  Voice (STT/TTS)     ──→ Arbiter voice bridges ──→ Deepgram + ElevenLabs
```

### Net effect

- Single LLM ingress: Arbiter. All hedging happens there.
- Single execution plane: Arbiter. Containers, conversation workflows, voice bridges all in one Node service.
- tacticl-core becomes a thin product shell: auth, REST/WS to clients, Telegram, Mongo for product-side state, gRPC calls to Arbiter.
- tacticl-web (and future tacticl-mobile, strategiz-web, etc.) talks to tacticl-core (or directly to Arbiter for some surfaces — see §6).

---

## 3. What's preserved vs. what's thrown away

### Preserved (carries to Arbiter unchanged or with light port)

| Asset | Where it lives now | Where it goes | Notes |
|---|---|---|---|
| PRD design (`2026-05-25-cloud-agent-orchestrator-prd.md`) | tacticl-core/docs/specs | unchanged location, language-neutral | Architecture decisions all carry |
| SAD design (`2026-05-25-cloud-agent-orchestrator-sad.md`) | tacticl-core/docs/specs | unchanged + small revision noting TS impl | Workflow shapes, persona registry, voice protocol all carry |
| Product Manager system prompt (~600 words, markdown) | `business-cloud-orchestrator/.../conversational-personas/product-manager.md` | `cidadel-ai-arbiter/packages/personas/conversational/product-manager.md` | Verbatim copy |
| Market Researcher system prompt (~700 words, markdown) | same | same target dir | Verbatim copy |
| 12 PDLC role markdown files (po.md, researcher.md, architect.md, ...) | `business-pipeline/src/main/resources/role-identities/` | `cidadel-ai-arbiter/packages/personas/pdlc/` | Verbatim copy |
| PersonaRouter logic (regex patterns, hard rules, sticky persona) | Java in `business-cloud-orchestrator/.../routing/` | TypeScript in `cidadel-ai-arbiter/packages/conversation/src/routing/` | Direct port — pure function, same 19 test cases |
| `RoutingDecision` sealed interface | Java | TS discriminated union | Type-level translation |
| Persona/Skill entity shapes | Java records | TS interfaces + Zod schemas | Same fields, same semantics |
| Migration runner concept (seed personas/skills/playbooks into Mongo) | Java migration runner (planned for Wave 3) | TS migration script | Same logic, run on Arbiter startup |
| Deepgram + ElevenLabs WS protocol knowledge | Java client implementations | TS rewrite using JDK/Node WebSocket patterns | The wire protocol notes carry; the code does not |
| Voice plane design (auto-mute during TTS, barge-in via cancellation, tool-use streaming protocol §5.10, long-running skill UX §5.11) | SAD | TS impl in `packages/voice` and `packages/conversation` | All design rules apply |
| Multi-pipeline + multi-session model (SAD §3.6) | SAD | TS impl in `packages/conversation` | Same model |
| Mongo projection model (SAD §3.7) | SAD | TS impl using existing arbiter mongodb client | Same model |
| Session ↔ Spark model (PRD §5.7.1) | SAD/PRD | TS impl | Same model |
| Templated greeting (PRD §5.9) | SAD/PRD | TS impl + pre-cached audio in CDN | Same model |
| Cost numbers (PRD §9.1) | PRD | unchanged | Same |

### Thrown away (Java code — ~2 days of Wave 1+2 work)

| Artifact | Reason |
|---|---|
| `data/data-cloud-orchestrator/` (Java entities, repositories) | Replaced by TS types + Mongo client in arbiter |
| `business/business-cloud-orchestrator/` (Java PersonaRouter, future workflow) | Replaced by TS impl in `packages/conversation` |
| `client/client-deepgram/` (Java WS client) | Replaced by TS Deepgram bridge in `packages/voice` |
| `client/client-elevenlabs/` (Java WS client) | Replaced by TS ElevenLabs bridge in `packages/voice` |
| `business/business-voice/` (Java bridges — was scaffolded only, no impl yet) | TS bridges in `packages/voice` |
| `service/service-cloud-orchestrator/` (Java WS handler — scaffold only) | TS handler in arbiter |
| `application-api/.../temporal/` (Temporal Java SDK bootstrap + SmokeWorkflow) | Replaced by Node Temporal SDK bootstrap in arbiter |
| Temporal Java SDK deps in `gradle/libs.versions.toml` | Removed |
| Caffeine cache dep (was for persona/skill registries) | Removed; Node uses native Map or `lru-cache` |
| All Wave-2 tests for the above (~80 tests) | Replaced by Vitest equivalents |

### What in tacticl-core stays (the "product shell")

| Module | Why it stays |
|---|---|
| `service/service-sparks/` | Spark REST API consumed by tacticl-web/mobile. Spark state lives in Mongo. |
| `service/service-conversation/` | Slimmed — REST endpoints (`POST /v1/conversations`, etc.) become gRPC adapters to Arbiter |
| `service/service-pipeline/` | Read-only REST projections of pipeline state. Writes happen in Arbiter. |
| `service/service-telegram/` | Telegram webhook + bot adapter. Sends conversation signals to Arbiter via gRPC. |
| `service/service-checkpoint/`, `service/service-repo/`, `service/service-token/` | Product-specific REST surfaces |
| `business/business-sparks/`, `business-telegram/`, slimmed `business-conversation/`, `business-pipeline/` | Thin adapters around Arbiter gRPC + Mongo writes |
| `data/data-sparks/`, `data/data-conversation/`, `data/data-pipeline/`, `data/data-profile/`, `data/data-connections/` | Mongo entity schemas for product-side state |
| `client/client-ai-arbiter/` | gRPC client to Arbiter (existing, will be extended for conversation RPCs) |
| `client/client-brave-search/`, `client-jina/`, `client-google/`, `client-twitter/`, `client-linkedin/`, `client-instagram/`, `client-github/`, `client-siliconflow/`, `client-gcs/`, `client-whisper/` | Product-side skill executors (social posting, browser, file upload Whisper). Still called by `CloudOrchestratorService` for `start_cloud_skill` invocations. |
| `business/business-agent/` (CloudOrchestratorService for non-PDLC sparks) | Existing skill executor for social/research/video — still owns the agent loop for those skills. Called from Arbiter via gRPC when a persona invokes `start_cloud_skill`. |
| `application-api/` (Spring Boot bootstrap) | Stays, minus Temporal additions |
| Cidadel framework consumption (auth, secrets, logging) | Stays — Java is the right tool for the auth/REST surface |

### Wave 1+2 fix-ups in tacticl-core that are PRESERVED

These were genuine prod fixes for the new design that are still right and shouldn't be reverted:

- `PdlcRole.PM` → `PdlcRole.PO` Java enum rename (was already correct independent of orchestrator location)
- `pipeline_runs.role` Mongo data migration (`"PM"` → `"PO"`) — already correct
- `PipelineRun` field additions: `name`, `creatingSessionId`, `userId` index, `personaVersions`, `blockedCheckpointId` — all are projection fields the Arbiter will write into via Mongo from TS, so the Java entity shape needs to match (or be readable from Java for the read-side REST projections)
- `PipelineCheckpoint.userId` denormalization + compound index
- `Spark.conversationSessionId` field — projection field
- Deprecated bridge methods on `ConversationSession` (`addMessage`, `markActive`, etc.) — needed because the legacy ConversationService stays compiling until §5 below deletes it
- `pm.md` → `po.md` filename rename in `business-pipeline/src/main/resources/role-identities/`
- `PdlcV2Service.resolveCheckpoint` `"PENDING"` → `CheckpointStatus.OPEN.name()` guard — real regression fix
- `ConversationEventChannel` migration to `appendTurn` + `changeStatus` API

All of these stay. The entity shapes still apply because Arbiter writes projections that Java reads.

---

## 4. Target structure inside `cidadel-ai-arbiter`

### 4.1 New packages

Two new workspaces in the npm monorepo (alongside existing `core` and `server`):

```
packages/
  core/                                  EXISTING — providers, routing logic (LLM-side)
  server/                                EXISTING — gRPC pipeline service, container mgmt
  conversation/        NEW               — conversation orchestrator: workflow, registry,
                                            persona invocation, skills as activities
  voice/               NEW               — Deepgram + ElevenLabs WS bridges,
                                            OutboundAudioQueue, voice WS server
  personas/            NEW               — persona prompt assets (markdown + frontmatter +
                                            seed data for the registry migration)
```

### 4.2 `packages/conversation/` — the orchestrator engine

```
packages/conversation/
├── package.json                          @cidadel/ai-arbiter-conversation
├── tsconfig.json
├── src/
│   ├── index.ts                          public exports
│   ├── types.ts                          shared TS types: Persona, Skill, Turn, SessionState
│   ├── persona/
│   │   ├── persona-registry.ts           Mongo-backed, lru-cache (5min TTL)
│   │   ├── skill-registry.ts             same pattern
│   │   ├── persona-router.ts             pure function (port of Java PersonaRouter §7)
│   │   └── routing-decision.ts           discriminated union
│   ├── workflow/
│   │   ├── cloud-agent-session-workflow.ts        Temporal workflow (Node SDK)
│   │   ├── cloud-agent-session-workflow.types.ts  signal/query types
│   │   ├── pipeline-workflow.ts                    child workflow
│   │   └── temporal-client.ts                      Temporal client wrapper
│   ├── activities/
│   │   ├── invoke-persona-activity.ts             calls @cidadel/ai-arbiter provider routing
│   │   ├── route-persona-activity.ts              (NOT an activity — folded into workflow as helper)
│   │   ├── persist-turn-activity.ts               Mongo write
│   │   ├── emit-turn-event-activity.ts            fan-out (FCM + WS push to all user's sessions)
│   │   ├── load-user-active-pipelines-activity.ts Mongo read by userId for §3.6
│   │   ├── start-pipeline-workflow-activity.ts    spawns PipelineWorkflow child
│   │   ├── start-cloud-skill-activity.ts          gRPC OUT to tacticl-core CloudOrchestratorService
│   │   ├── dispatch-to-device-activity.ts         gRPC OUT to tacticl-core device dispatch
│   │   ├── resolve-checkpoint-activity.ts         signals pipeline + Mongo update
│   │   ├── read-pipeline-state-activity.ts        Mongo read
│   │   ├── cancel-pipeline-activity.ts            signals pipeline.onCancel
│   │   ├── emit-greeting-activity.ts              templated, no LLM
│   │   └── skill-backing/                          per-skill activities (web_search, read_page,
│   │                                                 analyze_competitors, ...) — call core providers
│   ├── grpc/
│   │   ├── conversation-service-impl.ts           implements arbiter-conversation.proto
│   │   ├── conversation-streams.ts                bidi-streaming endpoints (event stream)
│   │   └── mongo-projections.ts                   read-side handlers
│   ├── migration/
│   │   ├── seed-personas.ts                       reads packages/personas/, writes to Mongo
│   │   ├── seed-skills.ts
│   │   ├── seed-playbooks.ts
│   │   └── pm-to-po-rename.ts                     idempotent, guarded (already done in Java
│   │                                                 once but TS runner is the canonical seed)
│   └── __tests__/
│       ├── persona-router.test.ts                 19 tests (port from Java)
│       ├── workflow.test.ts                       Temporal TestWorkflowEnvironment
│       ├── activities.test.ts                     Vitest + mocked Mongo/gRPC
│       └── ...
```

### 4.3 `packages/voice/` — STT/TTS bridges + voice WS

```
packages/voice/
├── package.json                          @cidadel/ai-arbiter-voice
├── src/
│   ├── index.ts
│   ├── deepgram/
│   │   ├── deepgram-client.ts            streaming WS client (Node ws lib or @deepgram/sdk)
│   │   ├── deepgram-stream-bridge.ts     per-session lifecycle, auto-mute, barge-in detection
│   │   └── deepgram-types.ts
│   ├── elevenlabs/
│   │   ├── elevenlabs-client.ts          streaming WS, init/text/flush/close protocol
│   │   ├── elevenlabs-stream-bridge.ts   per-utterance state machine (§5.10.5)
│   │   └── elevenlabs-types.ts
│   ├── audio/
│   │   ├── outbound-audio-queue.ts       bounded queue, 500ms backlog cap
│   │   └── pcm-utils.ts                  if any audio processing needed
│   ├── server/
│   │   └── voice-ws-handler.ts           /ws/cloud-agent/{sessionId} WebSocket endpoint
│   │                                       (Express + ws lib or uWebSockets)
│   └── __tests__/
│       └── ...
```

### 4.4 `packages/personas/` — assets only, no code

```
packages/personas/
├── package.json                          @cidadel/ai-arbiter-personas (private, asset-only)
├── conversational/
│   ├── product-manager.md                ported verbatim from tacticl-core
│   └── market-researcher.md              ported verbatim
├── pdlc/
│   ├── po.md                             ported verbatim from business-pipeline/.../role-identities/
│   ├── researcher.md
│   ├── architect.md
│   ├── designer.md
│   ├── planner.md
│   ├── implementer.md
│   ├── reviewer.md
│   ├── tester.md
│   ├── security_analyst.md
│   ├── technical_writer.md
│   ├── devops.md
│   └── retro_analyst.md
├── skills/
│   └── catalogue.json                    ~15 skill seed records (per SAD §4.4b)
└── playbooks/
    └── catalogue.json                    8 hardcoded playbooks (per current PlaybookSpecResolver)
```

The migration scripts in `packages/conversation/src/migration/` read these files and seed Mongo on startup.

### 4.5 Proto additions

```
packages/server/proto/v1/
├── arbiter-pipeline.proto                EXISTING
└── arbiter-conversation.proto      NEW
```

`arbiter-conversation.proto` defines:

```proto
service ArbiterConversationService {
  // Lifecycle
  rpc StartSession(StartSessionRequest) returns (StartSessionResponse);
  rpc ResumeSession(ResumeSessionRequest) returns (ResumeSessionResponse);

  // User input (text mode)
  rpc SendUserText(SendUserTextRequest) returns (SendUserTextResponse);

  // Voice — bidirectional streaming
  rpc VoiceStream(stream VoiceStreamMessage) returns (stream VoiceStreamMessage);
  //   client → server: audio_chunk, mode_change, barge_in, control intents
  //   server → client: partial_transcript, final_transcript, persona_text_chunk,
  //                    tts_chunk (audio), state_change, pipeline_event, checkpoint_raised, ...

  // Multi-pipeline queries (user-scoped, cross-session)
  rpc GetUserActivePipelines(GetUserActivePipelinesRequest)
      returns (GetUserActivePipelinesResponse);

  // Checkpoint resolution from ANY session (per SAD §3.6)
  rpc ResolveCheckpoint(ResolveCheckpointRequest) returns (ResolveCheckpointResponse);

  // Cancel a session OR a pipeline
  rpc CancelSession(CancelSessionRequest) returns (CancelSessionResponse);
  rpc CancelPipeline(CancelPipelineRequest) returns (CancelPipelineResponse);
}
```

Detailed message shapes derive from SAD §5.1 (WS protocol) and §3.6 (multi-pipeline ops).

### 4.6 Where existing arbiter pieces fit

- `packages/core/src/providers/anthropic.ts`, `openai-direct.ts`, etc. — UNCHANGED. The conversation workflow's `invoke-persona-activity.ts` calls into these via the existing routing layer.
- `packages/server/src/orchestrator/orchestrator-session.ts` (existing 345-line "OrchestratorSession" for exception handling) — UNCHANGED. Different purpose (arbiter-internal Claude Code session for template refinement). Will live alongside the new conversation orchestrator without overlap.
- `packages/server/src/` — gRPC server bootstrap extended to also serve `ArbiterConversationService` (new proto).
- Existing Mongo client + Qdrant client + Vault config — REUSED. The conversation package imports these.
- Existing OTel instrumentation — REUSED. Conversation workflow + activities get traces for free.

### 4.7 Temporal: Node SDK in Arbiter

- Add `@temporalio/client`, `@temporalio/worker`, `@temporalio/workflow`, `@temporalio/activity`, `@temporalio/testing` to `packages/conversation/package.json`.
- Temporal cluster: same Hetzner Postgres-backed cluster planned in the original SAD §3.1 — language-neutral.
- Worker process: starts inside arbiter's `packages/server` bootstrap (or a sibling `packages/conversation-worker` if we want process isolation later). v1: same process.
- Task queues: same names as the Java plan (`cloud-agent-session-tq`, `pipeline-tq`, `voice-activity-tq`).
- Workflow IDs: same scheme (`session-{sessionId}`, `pipeline-{sparkId}`).

The original SAD §3 stands. Only the SDK language changes.

---

## 5. tacticl-core slimming

What gets deleted from tacticl-core:

| Module | Action |
|---|---|
| `business/business-cloud-orchestrator/` | DELETE (was Wave-1+2 scaffold, Wave-2 partial impl) |
| `data/data-cloud-orchestrator/` | DELETE — TS types in arbiter are canonical now |
| `business/business-voice/` | DELETE (was scaffold only) |
| `client/client-deepgram/` | DELETE |
| `client/client-elevenlabs/` | DELETE |
| `service/service-cloud-orchestrator/` | DELETE (was scaffold only) |
| `application-api/src/main/java/io/tacticl/application/temporal/` | DELETE — Java Temporal bootstrap not needed |
| `application-api/build.gradle.kts` Temporal deps | REMOVE |
| `gradle/libs.versions.toml` — temporal-sdk + temporal-testing + caffeine | REMOVE |
| `settings.gradle.kts` includes for the 6 new modules | REMOVE |

What gets refactored in tacticl-core:

| Module | Change |
|---|---|
| `business/business-conversation/ConversationService.java` | The legacy in-process state machine STILL gets deleted (Phase 4 cutover) — its replacement is the Arbiter-hosted workflow. The slimmed Java `ConversationService` becomes a gRPC adapter that calls `ArbiterConversationService.SendUserText`. |
| `service/service-conversation/ConversationController.java` | REST endpoints (`POST /v1/conversations`, etc.) preserved for tacticl-web/mobile. Handler bodies become gRPC dispatch to Arbiter. ~30 LoC each. |
| `client/client-ai-arbiter/` | Extended with TS-generated Java stubs for `arbiter-conversation.proto`. Existing pipeline client unchanged. |
| `business/business-pipeline/PdlcV2Service.java` | Continues to exist as the legacy direct-to-arbiter pipeline submitter UNTIL Arbiter's new orchestrator becomes the pipeline owner. Initially: both work side by side (orchestrator-created pipelines vs legacy pipelines), then PdlcV2Service is deleted when Arbiter handles all pipeline creation. |
| `service/service-pipeline/PipelineController.java` | Stays as read-only REST projection of Mongo state (writes happen in Arbiter). |
| `business/business-agent/CloudOrchestratorService.java` | UNCHANGED. Continues to execute social/research/video/browser skills. Now called BY Arbiter (via gRPC) when a persona invokes `start_cloud_skill`. |
| `service/service-telegram/TelegramWebhookController.java` | Inbound Telegram → translates to `ArbiterConversationService.SendUserText` or `VoiceStream` gRPC calls. |
| `business/business-telegram/TelegramConversationAdapter.java` | Same — adapter shim. |

New tacticl-core module:

| Module | Purpose |
|---|---|
| `client/client-cloud-orchestrator/` (new, optional — could fold into `client-ai-arbiter`) | Generated TS-proto Java stubs for the conversation service. Keeps the pipeline client and the conversation client cleanly separated. |

### 5.1 What tacticl-core ends up looking like

A product shell:
- Auth (PASETO via cidadel framework)
- REST controllers for sparks, conversations (thin), pipeline (read-only), settings, console, repos, tokens
- WebSocket handler for **devices** (tacticl-device daemon) — voice WS goes to Arbiter directly
- Telegram bot adapter
- Mongo writes for product-side state (sparks, sessions, social posts, integrations)
- gRPC client to Arbiter (pipeline + conversation)
- Existing skill executors (`CloudOrchestratorService`) for social/research/video/browser sparks
- Cidadel framework consumption (auth, logging, secrets)

What it no longer owns: the conversation brain, the personas, the voice plane, the LLM hedging, the Temporal workflows.

This is the right shape for Option B. When we revisit Option C (§10), the remaining surfaces are small enough to port to Node.

---

## 6. Voice WS routing — direct to Arbiter, or via tacticl-core?

Open architectural choice for the voice plane: tacticl-web's voice sphere needs a WebSocket. Does it connect to:

**A.** `wss://api.tacticl.io/ws/cloud-agent/{sessionId}` (tacticl-core, Java) → relays binary frames to Arbiter via gRPC stream → Deepgram/ElevenLabs

**B.** `wss://arbiter.cidadel.io/ws/cloud-agent/{sessionId}` (Arbiter direct, Node) → Deepgram/ElevenLabs

**Recommendation: B (direct to Arbiter)** for voice traffic. Tacticl-core can still serve auth bootstrap (tacticl-web gets a short-lived signed token from tacticl-core, then connects to Arbiter with it). Reasoning:

- Voice latency budget is tight (<1200ms p50). Adding a Java relay hop adds ~30-60ms per frame round-trip.
- Java doesn't add value to audio frames — it'd just be byte-copying.
- The voice WS is fundamentally a streaming connection between the browser and the AI/voice services. Putting Java in the middle is a fashion choice, not a functional one.

Text mode (lower frequency, REST-friendly) can stay routed through tacticl-core if you want product-level rate limiting / audit there. Voice goes direct.

Token-based auth between tacticl-web and Arbiter:
- tacticl-web calls `POST /v1/conversations` on tacticl-core to start/resume session — gets back a short-lived (5min) signed session token
- Token includes: userId, sessionId, productId (multi-tenant), expires
- tacticl-web connects voice WS to Arbiter with token in handshake
- Arbiter validates token (shared signing key from Vault)
- Cidadel framework already provides PASETO infrastructure — Arbiter can verify PASETO tokens too (TS PASETO libs exist)

This authorizes Arbiter directly for voice without sacrificing tacticl-core's role as the product surface for everything else.

---

## 7. The integration contract: tacticl-core ↔ Arbiter

Defined in `arbiter-conversation.proto` (see §4.5). Direction summary:

```
INBOUND (tacticl-core → Arbiter, calls Arbiter:50051):
  StartSession / ResumeSession                — when web/mobile/Telegram opens a chat
  SendUserText                                  — text-mode message
  ResolveCheckpoint                             — explicit UI-button checkpoint resolution
  CancelSession / CancelPipeline                — cleanup
  GetUserActivePipelines                        — REST GET /v1/users/{id}/pipelines projection

OUTBOUND (Arbiter → tacticl-core, calls tacticl-core gRPC endpoint TBD):
  StartCloudSkill(skill, args, userId, sparkId) — when a persona invokes start_cloud_skill
                                                  (Arbiter doesn't own social/research/video
                                                  executors; tacticl-core does)
  DispatchToDevice(deviceId, taskSpec)          — when persona invokes dispatch_to_device
  CreateSpark(input, userId, type)              — when start_pipeline / start_cloud_skill /
                                                  dispatch_to_device need a Spark created on
                                                  the product side (or Arbiter creates the Spark
                                                  directly in Mongo — see below)

DIRECT (browser ↔ Arbiter):
  Voice WebSocket (bidi audio + control)
```

**Sparks: who creates them?** Two options:
- **A.** Arbiter creates them directly in Mongo. Mongo collection `sparks` is shared between tacticl-core and Arbiter. Both read; Arbiter writes from its activities; tacticl-core reads for REST projection.
- **B.** Arbiter calls back to tacticl-core via gRPC `CreateSpark` RPC. Tacticl-core writes to Mongo. Slower, but keeps the write side single-owned.

**Recommendation: A.** Sparks are simple entities, and the projection model in SAD §3.7 already says "Mongo collections are projections written by activities." Arbiter's activities write directly. Tacticl-core reads. Auth at the Mongo layer (Arbiter uses a write-scoped credential, tacticl-core uses a read-scoped credential).

---

## 8. The swarm — ~10 agents in 3 waves

### Wave 0 — Cleanup + alignment (1 agent, sequential, blocks everything)

#### Agent 1: Tear down tacticl-core Wave 1+2 + scaffold arbiter packages

- [ ] Delete the 6 tacticl-core modules listed in §5 (modules + tests + scaffolding files)
- [ ] Remove Temporal Java deps from `gradle/libs.versions.toml`
- [ ] Remove module entries from `settings.gradle.kts`
- [ ] Run `./gradlew compileJava test` — verify clean build with the new orchestrator dirs removed (Wave-2 fix-ups for PipelineRun/PipelineCheckpoint/Spark stay)
- [ ] In `cidadel-ai-arbiter`: scaffold 3 new packages (`conversation/`, `voice/`, `personas/`) with `package.json`, `tsconfig.json`, empty `src/index.ts`, empty `__tests__/`, registered in root workspaces
- [ ] Add Temporal Node SDK deps to `packages/conversation/package.json`: `@temporalio/client`, `@temporalio/worker`, `@temporalio/workflow`, `@temporalio/activity`, `@temporalio/testing`
- [ ] Add `lru-cache`, `zod`, `mongodb` (workspace-shared), `ws` to `packages/conversation/package.json`
- [ ] Add `ws` + Deepgram/ElevenLabs deps (if any official SDKs exist — otherwise plain `ws` is enough) to `packages/voice/package.json`
- [ ] Add `@cidadel/ai-arbiter` workspace dep to both new packages
- [ ] Create `packages/server/proto/v1/arbiter-conversation.proto` with the service surface from §4.5
- [ ] Run `npm install && npm run build` from arbiter root — verify everything compiles
- [ ] Single commit per repo

### Wave 1 — Foundation (4 parallel agents)

#### Agent 2: Port persona assets + migration scripts

- [ ] Copy `tacticl-core/business/business-cloud-orchestrator/src/main/resources/conversational-personas/product-manager.md` → `cidadel-ai-arbiter/packages/personas/conversational/product-manager.md` verbatim
- [ ] Copy `market-researcher.md` same way
- [ ] Copy 12 PDLC role markdowns from `tacticl-core/business/business-pipeline/src/main/resources/role-identities/*.md` → `cidadel-ai-arbiter/packages/personas/pdlc/*.md` verbatim (preserve `po.md` rename — do NOT revert to `pm.md`)
- [ ] Author `packages/personas/skills/catalogue.json` with ~15 skill seed records per SAD §4.4b (id, name, description, inputSchema, activityName)
- [ ] Author `packages/personas/playbooks/catalogue.json` with 8 hardcoded playbooks ported from current `PlaybookSpecResolver.java`
- [ ] Write `packages/conversation/src/migration/seed-personas.ts`, `seed-skills.ts`, `seed-playbooks.ts` — idempotent, guarded by `migration_log` Mongo doc per SAD §10.3
- [ ] Vitest tests for migration scripts using `mongodb-memory-server` or mocked Mongo
- [ ] Output report

#### Agent 3: TS types for Persona/Skill/Turn/Session entities + Zod schemas

- [ ] In `packages/conversation/src/types.ts`, define TS interfaces matching SAD §9 + §4.1:
  - `Persona`, `Skill`, `VoicePreset`, `PlaybookV2`, `PhaseConfig`, `RoleSlot`
  - `Turn`, `PartialTranscript`, `ToolCall`, `TokenUsage`, `LatencyBreakdown`, `CostBreakdown`
  - `SessionMode`, `SessionStatus` enums (string literal unions)
  - `ConversationSession` shape
  - `PipelineRunSummary` (read shape for projections)
  - `PipelineCheckpointSummary`
- [ ] Zod schemas for runtime validation at gRPC + DB boundaries
- [ ] Mongo collection wrappers (`personas`, `skills`, `playbooks`, `conversation_sessions`, etc.) typed with `Collection<Persona>` etc.
- [ ] Reuse existing arbiter Mongo client from `packages/server/src/` (lift it into a shared location if needed)
- [ ] Vitest tests for Zod parsing of edge cases

#### Agent 4: Port `PersonaRouter` to TS + 19 tests

- [ ] Create `packages/conversation/src/persona/persona-router.ts` — pure function
- [ ] Direct port of the Java `PersonaRouter.route()` algorithm from SAD §7.1 — same regex patterns, same hard rules, same sticky persona logic, same default
- [ ] Create `packages/conversation/src/persona/routing-decision.ts` — TS discriminated union:
  ```typescript
  type RoutingDecision =
    | { kind: 'invoke_persona'; personaId: string; reason: string }
    | { kind: 'control_action'; type: ControlType; reason: string };
  ```
- [ ] Port the 19 test cases from `tacticl-core/business/business-cloud-orchestrator/.../PersonaRouterTest.java` to Vitest
- [ ] Same test names, same coverage
- [ ] **Important: the eventual merge with the workflow (per the user's preference) — keep the router as a pure exported function. The workflow imports it directly.** No Spring bean, no class wrapper. A standalone TS function in a `.ts` module.
- [ ] Output report

#### Agent 5: PersonaRegistry + SkillRegistry + PlaybookRegistry (TS, Mongo + lru-cache)

- [ ] Create `packages/conversation/src/persona/persona-registry.ts`:
  ```typescript
  class PersonaRegistry {
    constructor(private mongo: MongoCollection<Persona>, ttlMs = 5 * 60 * 1000) {...}
    async findById(id: string): Promise<Persona | undefined>
    async findByFamily(family: PersonaFamily): Promise<Persona[]>
    async toolsFor(personaId: string): Promise<AnthropicToolDef[]>  // resolves persona's skillIds against SkillRegistry
    invalidate(id?: string): void
  }
  ```
- [ ] Same shape for `SkillRegistry` and `PlaybookRegistry`
- [ ] Use `lru-cache` for in-process caching (5min TTL, 100 entry cap)
- [ ] Vitest tests for cache behavior, eviction, tool resolution
- [ ] Output report

### Wave 2 — Workflow + activities + voice (4 parallel agents — depends on Wave 1)

#### Agent 6: `CloudAgentSessionWorkflow` (Temporal Node SDK)

- [ ] Create `packages/conversation/src/workflow/cloud-agent-session-workflow.ts`
- [ ] Implement per SAD §3.3.1 (with the multi-pipeline/multi-session updates from §3.6):
  - Internal state: sessionId, userId, mode, status, turns (compact), sessionStartedSparkIds, focusedPipelineId, costAccumulator, seenTurnIds (idempotency dedup per §3.3.1.x)
  - Signals: `onUserTranscript`, `onUserText`, `onCheckpointDecision`, `onModeChange`, `onBargeIn`, `onCancel`
  - Queries: `currentState`, `recentTurns(limit)`, `activePersonaId`
  - Turn loop per SAD §3.3.1 — calls `personaRouter.route(...)` inline (NO RoutePersonaActivity), then `invokePersonaActivity`, handles tool calls
  - Fold the persona-router import directly into the workflow (per user decision: "merge router into orchestrator") — workflow imports the pure function, not a separate bean/class
- [ ] Implement `pipeline-workflow.ts` per SAD §3.3.2 with `ParentClosePolicy.ABANDON`, 7d timeout, signal-by-workflowId routing
- [ ] Register workflows on Temporal worker bootstrap in arbiter server entry
- [ ] Tests using `@temporalio/testing` — TestWorkflowEnvironment, mocked activities

#### Agent 7: All activities (skill-backing + workflow-supporting)

- [ ] Per SAD §3.4 + §4.4b, implement these activities in `packages/conversation/src/activities/`:
  - `invoke-persona-activity.ts` — calls into `@cidadel/ai-arbiter/core` provider routing (so Anthropic + OpenAI + ... all hedge automatically)
  - `persist-turn-activity.ts` — Mongo write
  - `emit-turn-event-activity.ts` — fan-out to all the user's active sessions (FCM + WS push; FCM via existing infra, WS via the voice handler's session registry)
  - `load-user-active-pipelines-activity.ts` — Mongo read
  - `start-pipeline-workflow-activity.ts` — spawns `PipelineWorkflow` child workflow
  - `start-cloud-skill-activity.ts` — gRPC OUT to tacticl-core (`CloudOrchestratorService` adapter — see §7 note about new gRPC endpoint on tacticl-core)
  - `dispatch-to-device-activity.ts` — gRPC OUT to tacticl-core device dispatch
  - `resolve-checkpoint-activity.ts` — optimistic-locked Mongo update + signals target pipeline
  - `cancel-pipeline-activity.ts` — signals pipeline.onCancel
  - `read-pipeline-state-activity.ts` — Mongo read for `summarize_pipeline_progress`
  - `emit-greeting-activity.ts` — templated, returns pre-cached audio refs for voice mode
  - Skill activities: `brave-search-activity.ts`, `jina-read-activity.ts`, `competitor-analysis-activity.ts`, `market-size-activity.ts`, `complete-role-activity.ts`, `no-op-activity.ts`
- [ ] Each activity has its own Vitest test
- [ ] Skill activities that wrap existing arbiter providers reuse them directly (no new API client modules needed for LLM-internal skills)
- [ ] Output report

#### Agent 8: `packages/voice/` — Deepgram + ElevenLabs TS bridges + voice WS handler

- [ ] Create `packages/voice/src/deepgram/`:
  - `deepgram-client.ts` — streaming WS to Deepgram per SAD §5.2 (Node `ws` lib; auth via `Authorization: Token {key}` header)
  - `deepgram-stream-bridge.ts` — per-session lifecycle, auto-mute coordination via shared `SessionMuteState`, sends `onUserTranscript` signal to workflow on final transcript, sends `onBargeIn` signal on speech_started during active TTS
- [ ] Create `packages/voice/src/elevenlabs/`:
  - `elevenlabs-client.ts` — streaming WS per SAD §5.3 (init/text/flush/close protocol, auth via `xi-api-key` header)
  - `elevenlabs-stream-bridge.ts` — per-utterance state machine per SAD §5.10.5, non-blocking close for barge-in
- [ ] Create `packages/voice/src/audio/outbound-audio-queue.ts` — bounded queue 500ms backlog
- [ ] Create `packages/voice/src/server/voice-ws-handler.ts` — Express + ws WebSocket endpoint `/ws/cloud-agent/{sessionId}` per SAD §5.1 (full protocol: audio_chunk, partial_transcript, tts_chunk, mode_change, barge_in, ...)
- [ ] PASETO token verification at handshake (use ts-paseto or similar; signing key from Vault)
- [ ] Wire WS handler into arbiter server bootstrap on a new port (or shared port with gRPC if compatible)
- [ ] Tests for protocol round-trips, mocked Deepgram/ElevenLabs WS, barge-in cancellation timing

#### Agent 9: gRPC service implementation + tacticl-core integration

- [ ] Implement `ArbiterConversationService` in `packages/conversation/src/grpc/conversation-service-impl.ts`:
  - `StartSession` / `ResumeSession` → starts/queries `CloudAgentSessionWorkflow`
  - `SendUserText` → workflow signal `onUserText`
  - `VoiceStream` (bidi streaming) → bridges to voice WS handler patterns (could also be the SAME WS endpoint — TBD per Agent 8's design)
  - `GetUserActivePipelines` → Mongo read
  - `ResolveCheckpoint` → workflow signal to target pipeline via workflowId
  - `CancelSession`, `CancelPipeline`
- [ ] Register service in arbiter gRPC server bootstrap
- [ ] In `tacticl-core/client/client-ai-arbiter/`: generate Java stubs from `arbiter-conversation.proto` (use existing protobuf-gradle-plugin setup)
- [ ] In `tacticl-core/service/service-conversation/`: refactor `ConversationController` REST handlers to dispatch to Arbiter via the new gRPC client
- [ ] In `tacticl-core/business/business-conversation/ConversationService.java`: REPLACE the legacy in-process state machine body with gRPC dispatch (DELETE the old gather/propose/active logic — it lives in Arbiter now)
- [ ] Delete the deprecated bridge methods on `ConversationSession` (now safe — no callers remain)
- [ ] Update `TelegramConversationAdapter` to call Arbiter gRPC instead of in-process ConversationService logic
- [ ] In tacticl-core: add a NEW small gRPC server endpoint that Arbiter calls back into for `StartCloudSkill` and `DispatchToDevice` — this is the inbound direction from §7
- [ ] Full integration test: tacticl-core REST `POST /v1/conversations` → Arbiter gRPC → workflow starts → response

### Wave 3 — Hardening + cutover (3 agents in series)

#### Agent 10: tacticl-web sphere UI + voice WS client

- [ ] In `tacticl-web/src/`: build `VoiceSphere` (Three.js, React Three Fiber) per SAD §6.2
- [ ] `MicCaptureWorklet` (AudioWorklet, 16kHz s16le PCM, 20ms chunks)
- [ ] `TtsPlayer` (MediaSource + SourceBuffer for MP3; pre-cached greeting audio integration)
- [ ] `VoiceWebSocketClient` — connects to Arbiter `/ws/cloud-agent/{sessionId}` (NOT tacticl-core)
- [ ] Auth flow: REST `POST /v1/conversations` to tacticl-core returns session token → WS handshake with token → Arbiter validates
- [ ] `VoiceChatPage` assembly per SAD §6.1; routing `/chat` → this new page; DELETE legacy `ChatPage.tsx`
- [ ] Pre-build script to generate ElevenLabs greeting MP3s for caching (per Wave-1 Task 6.4 in the original plan)
- [ ] Manual QA matrix from original plan §Task 6.8 — verify all flows

#### Agent 11: Deployment + observability + load test

- [ ] Update `cidadel-ai-arbiter/scripts/deploy.sh` to build + deploy the new packages
- [ ] Arbiter Dockerfile updated to install Temporal SDK deps (Node-side, already in package.json)
- [ ] Temporal cluster on Hetzner — same Postgres-backed plan from original SAD §3.1 (provision if not already done; per Wave-1 Task 1.1 of original plan)
- [ ] OTel instrumentation — workflow + activities get traces via existing arbiter OTel setup
- [ ] Grafana dashboards: latency (per turn breakdown), cost (LLM + STT + TTS per user), echo events, duplicate-signal-dropped counter
- [ ] Load test: 10 concurrent voice sessions for 10 minutes, no p95 > 2200ms, no Temporal worker backlog
- [ ] Failure injection: Deepgram kill, ElevenLabs kill, Temporal restart, arbiter restart
- [ ] Runbooks: `cidadel-ai-arbiter/docs/runbooks/conversation-orchestrator.md`, `voice-plane.md`, `temporal-cluster.md`

#### Agent 12 (the reviewer): cross-repo code review

- [ ] Review all PRs/commits across `tacticl-core` (deletes + slim refactors) and `cidadel-ai-arbiter` (new packages)
- [ ] Verify no Java orchestrator code remnants (zero references to `CloudAgentSessionWorkflow` in Java)
- [ ] Verify all conversational LLM calls now go through Arbiter (no `cidadel-client-anthropic-direct` calls outside the legacy CloudOrchestratorService skill path)
- [ ] Verify PersonaRouter TS port matches Java semantics (all 19 tests have equivalents)
- [ ] Verify SAD/PRD references in code comments updated to point at the TS impl locations
- [ ] Sanity check: tacticl-core no longer compiles Temporal — `gradle/libs.versions.toml` clean
- [ ] File-by-file diff against this plan — flag any drift
- [ ] Output report — final ship/no-ship recommendation

---

## 9. Sequencing

```
Day 1: Wave 0 — Agent 1 scaffolds (1 agent, ~3 hours)
Day 2-3: Wave 1 — Agents 2-5 in parallel (4 agents, ~1 day each)
Day 4-5: Wave 2 — Agents 6-9 in parallel (4 agents, ~1-2 days each)
                  Agent 9 has a soft dep on Agents 6-8 being ~80% done
Day 6: Wave 3 — Agents 10-11 in parallel; Agent 12 reviewer at end
Day 7: Cutover deploy + 24h monitoring
```

Total swarm capacity used: 12 agents (10 builders + 1 reviewer + 1 scaffolder). Realistic calendar: ~1 week of focused work with the swarm running.

---

## 10. The eventual Option C — kill tacticl-core Java entirely

When this migration is complete (Option B done), tacticl-core's surface area shrinks dramatically. At that point, the remaining Java code is:

1. Auth (PASETO via cidadel framework — Java)
2. REST controllers (sparks, conversations thin, pipeline thin, settings, console, repos, tokens)
3. Telegram bot
4. Mongo writes for product-side state
5. `CloudOrchestratorService` skill executors (social/research/video/browser/Playwright)
6. WebSocket handler for devices

What it would take to kill this:

| Item | Effort | Notes |
|---|---|---|
| Auth | 1-2 weeks | Port PASETO verification to TS (libs exist). Migrate cidadel framework consumption — but most of this can be inlined since Arbiter already does auth verification on its own surface. |
| REST controllers | 2 weeks | Port to Fastify/Express in Node. Smaller surface area after the slimming. |
| Telegram bot | 1 week | `node-telegram-bot-api` or `grammy` are mature. |
| Mongo writes | 0 weeks | Already shared with Arbiter — use the same client. |
| `CloudOrchestratorService` skill executors | **6-10 weeks** | This is the hard part. Social APIs (Twitter, LinkedIn, Instagram), Google Photos, video gen (SiliconFlow), browser automation (Playwright Java → playwright-node), GitHub client — all need TS rewrites. Or: keep this one Java service alive as a "skill executor sidecar" that Arbiter calls. |
| WebSocket for devices | 1-2 weeks | Existing protocol; reimplement in `ws` lib. |

**Total estimated effort: 11-17 weeks** (3-4 months) of focused work to kill tacticl-core entirely.

**When to revisit:** when the Option B path has shipped and stabilized (probably 3-4 months after this migration), and if the Java codebase feels like maintenance burden rather than value-add. At that point, the rewrite is scoped and the cost/benefit is clearer.

**To NOT foreclose Option C, this migration:**
- Keeps tacticl-core's surface clearly demarcated (product shell vs platform engine)
- Defines the gRPC contract between them — that's the same contract any future Node tacticl-core would implement
- Mongo schemas are language-neutral
- Doesn't add new Java code in the orchestrator space (everything new is TS in Arbiter)

When you're ready for C, you have a clean swap: rewrite the product shell in Node, point it at the same Arbiter, same Mongo, same Temporal — and turn off the Java JVM.

---

## 11. Risks + open questions

| Risk | Mitigation |
|---|---|
| Wave 1+2 Java code thrown away (~2 days lost) | Cheap sunk cost. The DESIGN is preserved (PRD/SAD/persona prompts all carry). |
| TS Temporal SDK less mature than Java SDK | Real but workable. Temporal Node SDK is production-grade as of 2025. We lose some convenience (Spring auto-config) but gain ecosystem fit. |
| Cross-language gRPC contract drift | Mitigate with shared proto + generated stubs on both sides + integration tests. |
| Arbiter becomes a single point of failure | Already true — Arbiter owns pipeline execution. Adding conversation orchestration to it concentrates more risk in one service. Mitigate with same backup/runbook story for Temporal Postgres. |
| Mongo cross-process writes (Arbiter writes, tacticl-core reads) | Standard projection pattern; use Mongo's optimistic locking for hot fields (checkpoint resolution). Spec'd in SAD §3.7. |
| Voice direct-to-Arbiter means tacticl-core can't see voice traffic for audit | True. Acceptable for v1. If audit is required later, add a tap inside Arbiter that mirrors events to a log/queue tacticl-core consumes. |
| TS PASETO interop with Java | Verify before Wave 2 — Agent 8's task includes choosing a TS PASETO lib that decodes Java-issued PASETO tokens correctly. If incompatibility, fall back to a shorter-lived JWT or session token signed with HMAC + shared Vault secret. |
| Hermes (Nous models) revisit | Punted per the earlier analysis. Still right answer. |

---

## 12. Companion doc updates required

After this migration plan ships, update these companion docs to reflect the language/repo change without re-stating the design:

- [ ] `2026-05-25-cloud-agent-orchestrator-prd.md` — small front-matter note: "Implementation lives in `cidadel-ai-arbiter`; see `2026-05-30-orchestrator-migrate-to-arbiter.md` for the migration plan."
- [ ] `2026-05-25-cloud-agent-orchestrator-sad.md` — front-matter note same as above. Module-layout section (§11) updated to point at `cidadel-ai-arbiter/packages/{conversation,voice,personas}/`. Java class names in §3, §4, §7 retained as conceptual references but annotated as "TS impl in [path]".
- [ ] `2026-05-25-cloud-agent-orchestrator.md` (original tacticl-core plan) — banner: "SUPERSEDED by 2026-05-30-orchestrator-migrate-to-arbiter.md. Do not implement from this doc."
- [ ] `tacticl-core/CLAUDE.md` — update the "active architectural overhaul" banner to point at this plan + reflect that tacticl-core is becoming a product shell, not the orchestrator home.
- [ ] `cidadel-ai-arbiter/CLAUDE.md` — add a new section describing the conversation orchestrator + voice plane packages.
- [ ] `tacticl-docs/product/pdlc/2026-04-13-tacticl-pdlc-architecture.md` — banner already says superseded; update the pointer to also mention this plan.
- [ ] Memory file `project_cloud_agent_orchestrator.md` — add the language/repo pivot. Critical for future sessions to not assume Java + tacticl-core.

---

## 13. Decision log (so future you knows why)

| Decision | When | Who | Rationale |
|---|---|---|---|
| Build orchestrator in tacticl-core Java | 2026-05-25 | Self (without asking) | Defaulted to existing codebase; didn't consider Arbiter as home. **Wrong call.** |
| Pivot to Arbiter (Option B) | 2026-05-30 | User direction | All LLM calls should go through Arbiter; Node/TS is right tool for AI work; Arbiter already has providers + Mongo + Vault + OTel; cross-language friction at every conversation turn was wrong-shaped. |
| Keep tacticl-core as product shell (don't kill Java yet) | 2026-05-30 | This plan | 3-6 month rewrite to kill all Java now would block MVP. Pragmatic path: slim it to product shell, kill later (§10). |
| Voice WS direct-to-Arbiter | 2026-05-30 | This plan | Latency budget tight; Java relay adds 30-60ms; no value Java adds to audio frames. |
| Sparks: Arbiter writes directly to shared Mongo | 2026-05-30 | This plan | Projection pattern; simpler than callback-RPC; Mongo handles consistency. |
| PersonaRouter folded into workflow (vs separate class) | 2026-05-30 | User direction | Less indirection; static helper imported by workflow. |
| Defer Option C (kill all Java) | 2026-05-30 | This plan + user lean | Option B unblocks shipping; C revisit after MVP stabilizes. |

---

## 14. Quick-reference checklist (for "where do I put this code?")

| If you're adding... | Put it in... |
|---|---|
| A new conversational persona | `cidadel-ai-arbiter/packages/personas/conversational/{id}.md` + add to seed catalogue + Mongo migration on next deploy |
| A new PDLC role persona | `cidadel-ai-arbiter/packages/personas/pdlc/{id}.md` + add to `PdlcRole` TS enum + seed catalogue |
| A new skill (chat-side) | New activity in `packages/conversation/src/activities/skill-backing/` + entry in `packages/personas/skills/catalogue.json` + add to relevant personas' `skillIds` |
| A new LLM provider | `packages/core/src/providers/{name}.ts` (existing pattern) |
| A new voice provider (e.g., another TTS) | `packages/voice/src/{provider}/` with bridge + client |
| A new gRPC RPC on the conversation surface | Update `arbiter-conversation.proto`, regenerate stubs on both sides, implement in `packages/conversation/src/grpc/conversation-service-impl.ts`, consume in tacticl-core's `client/client-ai-arbiter/` Java stubs |
| A new REST endpoint on tacticl-core (product shell) | `service/service-{module}/.../Controller.java` (Java); call Arbiter via gRPC for AI work |
| A new Mongo collection used by both Arbiter and tacticl-core | Define schema in BOTH languages (TS Zod + Java entity). Document who writes (usually Arbiter writes, tacticl-core reads). |
| A new Temporal activity (orchestrator-side) | `packages/conversation/src/activities/{name}-activity.ts` + register on worker |
| A new persona system-prompt edit | Edit the markdown file in `packages/personas/`; bump persona `version` in Mongo on next deploy via migration runner; old versions retained per SAD §4.1 |

---

## 15. Stop conditions for the swarm

If any agent hits one of these, STOP and report — don't push through:

1. Temporal Node SDK incompatibility with our Postgres backend (unlikely but check on first smoke workflow)
2. PASETO TS lib can't decode Java-issued tokens (Wave 2 Agent 8 verification gate)
3. Existing `OrchestratorSession` in arbiter (the Claude Code SDK wrapper, 345 lines) conflicts with new conversation orchestrator class names — rename one of them
4. Mongo schema conflict between Java entity writes and TS projection writes (test early in Wave 2)
5. Wave 1 Agent 1's tacticl-core deletions break something we didn't anticipate — `./gradlew test` must stay green throughout
6. Pre-Wave-2 fix-ups for `ConversationSession` legacy bridges become load-bearing in unexpected places

---

**END OF MIGRATION PLAN.**

Want changes? Edit this doc, then dispatch agents against the updated version.
