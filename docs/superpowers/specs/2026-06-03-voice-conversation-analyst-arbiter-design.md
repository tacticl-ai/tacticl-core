# Conversational Analyst → Arbiter: Voice Brain Migration Design

- **Date:** 2026-06-03
- **Status:** DRAFT — for review
- **Type:** Implementation design (cross-repo: `tacticl-core` Java + `cidadel-ai-arbiter` TS)
- **Extends / subordinate to:** `docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md` (§4.5 `ArbiterConversationService`, §7 integration contract), `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md` (§3.3, §4, §5, §7), `docs/handover/2026-05-30-orchestrator-migration/00-session-decisions.md`.
- **Supersedes for the voice conversation path:** the interim in‑JVM `VoiceConversationTurnHandler` shipped 2026‑06‑03 (kept only as the local fallback engine — see §6.3).

---

## 1. Goal & principle

Make Jarvis hold a real conversation in which a **conversational persona** (the "analyst" — see terminology in §3) talks with the user, **reviews/validates intent**, and **initiates the development pipeline** — with that brain running where the canon says it belongs: **the arbiter** (`cidadel-ai-arbiter`), as a persona‑with‑skills, not a stateless LLM call.

**Guiding principle — brain‑first, no dark period.** The voice path has three independently‑migratable layers:

| Layer | Today | Target | This design moves it |
|---|---|---|---|
| 1. Voice transport (STT/TTS/WS/audio) | tacticl-core `business-voice` | arbiter `packages/voice/`, browser↔arbiter WS direct (`00` §4) | **Phase 3 — last** (pure latency optimization) |
| 2. Conversation **brain** (persona + skills + memory + start_pipeline) | nowhere real (interim tool‑less handler) | arbiter `packages/conversation/` | **Phase 1 — now ★** |
| 3. PDLC engine (ephemeral role containers) | arbiter ✅ | arbiter ✅ | already done |

We migrate **layer 2 first**, reached from tacticl‑core's existing voice plane through a `ConversationEngine` seam. Voice keeps working throughout; nothing written is throwaway because the brain lands in its **final home** (the arbiter) and the seam is the same one the end‑state uses.

---

## 2. What the canon already locks (do not reinvent)

From the migrate plan + SAD + session decisions (cited):

- **Conversational personas execute as a direct LLM call (no container, ~1s budget), and post‑pivot they run in the arbiter via its provider routing — NOT a parallel direct‑Anthropic path** (SAD §4.5; migrate plan §1.1, §4.2 `packages/conversation/src/activities/invoke-persona-activity.ts`). The interim Java handler is exactly the "parallel LLM path" the pivot wants to retire — hence Phase 1.
- **There is already a sketched gRPC service: `ArbiterConversationService`** (migrate plan §4.5) with `StartSession`/`ResumeSession`/`SendUserText`/`VoiceStream`(bidi)/`GetUserActivePipelines`/`ResolveCheckpoint`/`CancelSession`/`CancelPipeline`. **We extend this**, we do not invent a new service.
- **`PersonaRouter` is a pure function folded into the workflow** — control‑intent regex, sticky persona, `MARKET_INTENT_RE`, `PIPELINE_BLOCKED → product-manager`, default `product-manager` (SAD §7.1; `00` §7). No LLM, no activity.
- **Voice WS end‑state = direct browser↔arbiter** (the ONLY browser→arbiter direct path), authed by a short‑lived token **signed by tacticl‑core, validated by arbiter via a shared Vault key** (`00` §4, migrate plan §6). Interim SAD terminates the WS in tacticl‑core.
- **Pipeline is SILENT** — all audio is in the conversation layer; PDLC personas have `voicePreset=null` and never speak; the PM persona narrates pipeline events via `summarize_pipeline_progress` (`00` §8; SAD §4.3).
- **productId is data‑scoping, not forked code** — threaded through workflow/router/registries/knowledge; no `if(product===…)` branches (`00` §10).
- **Learning layer (Mongo `agent_knowledge` + Qdrant) must be preserved** and re‑wired into pipeline role prompts (`learning-layer-and-codegen-prompts-preservation.md`). Whether *conversational* turns get knowledge augmentation is **undecided** (§9.3).

### Ground‑truth that shapes the build (from repo reads, 2026‑06‑03)

- **The arbiter's Anthropic provider is text‑only — it ignores `req.tools` and never returns `tool_use`** (`cidadel-ai-arbiter/packages/core/src/providers/anthropic.ts:144-172,214-240`). The only existing tool‑use loop is the Claude Code SDK path (`claude-code.ts`, `orchestrator-session.ts`). **So the persona tool‑use loop is net‑new and is the heart of Phase 1.**
- **`start_pipeline` can run in‑process in the arbiter** via `Shell.submitPipeline(...)` (`shell.ts:217`) — BUT tacticl‑core currently **owns** the working pipeline path: `PdlcV2Service.submitPipeline` persists the local `PipelineRun`, creates the Spark, and the callback fan‑out drives **voice narration** through `VoiceRunUpdateChannel` (`tacticl-core …/business-voice/VoiceRunUpdateChannel.java`). Moving start‑pipeline arbiter‑side now would orphan all of that. → **§7 ownership decision.**
- **tacticl‑core's gRPC client is ready to extend**: `client-ai-arbiter` already generates stubs via the protobuf plugin and holds a `ManagedChannel` bean; adding a `ConversationService` client is a new `.proto` + an async stub on the **same channel** + a triple‑bean config (mirroring `ArbiterClientConfig.java:22-58`).
- **The seam exists**: `VoiceConversationTurnHandler.generateReply()` is the single swap point; `ElevenLabsTtsBridge.speak()` is whole‑utterance today but the underlying `ElevenLabsSession.sendTextChunk()` supports incremental feeding for streamed replies.

---

## 3. Terminology: "the analyst"

The canon has **two** conversational personas (SAD §4.4): **Product Manager** (`product-manager`, default; owns `ask_clarification`, `propose_implementation`, **`start_pipeline`**, `summarize_pipeline_progress`, `mediate_pipeline_checkpoint`, …) and **Market Researcher** (`market-researcher` — literally "an evidence‑driven analyst"; owns `web_search`, `analyze_competitors`, `estimate_market_size`, … but **no pipeline‑control skills**).

The user's "analyst holds the conversation, reviews intent, and starts the pipeline" maps cleanly onto **the Product Manager** (it holds the conversation and owns `start_pipeline`), with the **Market Researcher** as the deep‑research specialist the PM (or routing) hands to for validation. We design for **"the default conversational persona that holds the conversation, reviews intent, and starts the pipeline"** and keep the *exact* persona id + skill assignment a **data decision** in the registry (§9.1) — because per canon, giving a single "analyst" both deep research *and* `start_pipeline` is a data‑only deviation, not a code change.

---

## 4. Architecture — Phase 1 (the slice we build now)

```
 ┌──────────────────────── tacticl-core (Java) — VOICE EDGE (unchanged transport) ───────────────────────┐
 │ Browser sphere ⇄ WS /v1/voice ⇄ VoiceWebSocketHandler                                                  │
 │   mic PCM → DeepgramSttBridge → final transcript                                                       │
 │   → VoiceSessionService.onFinalTranscript → IngressDispatchService (CONVERSATION_TURN)                 │
 │     → ConversationTurnHandler  → VoiceConversationTurnHandler                                          │
 │         → ConversationEngine.converse(sessionCtx, userText, history)   ◀── SEAM                        │
 │              ├── ArbiterConversationEngine (PRIMARY, gRPC) ───────────────┐                            │
 │              └── AnthropicDirectConversationEngine (FALLBACK, in-JVM)     │                            │
 │   reply tokens ⇐ stream ⇐ ───────────────────────────────────────────────┼── incremental TTS         │
 │   start_pipeline tool_use ⇐ ──────────────────────────────────────────────┘  → IngressDispatchService │
 │                                                                                 .handleExplicitTrigger │
 │                                                                                 (LOCAL run + narration)│
 └────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                   │ gRPC :50051  (cidadel-arbiter-prod)
                                   ▼
 ┌──────────────────── cidadel-ai-arbiter (TS) — CONVERSATION BRAIN (new) ───────────────────────────────┐
 │ ConversationService.ConverseTurn(request) → stream ConverseEvent     (packages/conversation)          │
 │   • load persona (prompt + skill tool-defs)   [minimal registry, §6.2]                                 │
 │   • tool-use loop over provider routing (Anthropic + fallback)  [Anthropic provider extended, §6.2]    │
 │       – cognitive skills (answer_in_conversation, propose_implementation, research*) run in-loop       │
 │       – side-effecting skills (start_pipeline) emitted as TERMINAL tool_use for the caller to execute  │
 │   • stream: started → token* (text deltas) → [tool_use]? → done | error                                │
 │   • product-scoped; optional knowledge augmentation (§9.3)                                             │
 └───────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                   │ in-process
                                   ▼  (end-state only — §7) Shell.submitPipeline → child PipelineWorkflow
```

`*research skills deferred — their Brave/Jina clients live in tacticl-core today (§6.2, §9).`

**Why a `ConverseTurn` server‑streaming RPC (not the full bidi `VoiceStream`) for Phase 1:** tacticl‑core keeps STT/TTS, so the arbiter never sees audio in Phase 1 — it gets a **final transcript (text) per turn** and streams **reply text deltas** back. That is a strict subset of the canonical `ArbiterConversationService` (it is `SendUserText` returning a server stream). The full bidi `VoiceStream` + browser‑direct WS is Phase 3, when the voice transport relocates.

---

## 5. The gRPC contract (Phase 1)

Added to the canonical `ArbiterConversationService` (migrate plan §4.5). New proto in **both** repos (kept byte‑identical): arbiter `packages/server/proto/conversation/v1/arbiter_conversation.proto` and tacticl‑core `client/client-ai-arbiter/src/main/proto/cidadel/ai/arbiter/conversation/v1/arbiter_conversation.proto` (codegen already wired — `client-ai-arbiter/build.gradle.kts` `generateProtoTasks { all() }`).

```proto
syntax = "proto3";
package cidadel.ai.arbiter.conversation.v1;
option java_package = "cidadel.ai.arbiter.conversation.v1";  // matches the existing pipeline proto convention
option java_multiple_files = true;
option java_outer_classname = "ArbiterConversationProto";

service ArbiterConversationService {
  // Phase 1: one conversational persona turn. Text transcript in → streamed reply out.
  // (Subset of the end-state service; full lifecycle/VoiceStream RPCs land in later phases.)
  rpc ConverseTurn(ConverseTurnRequest) returns (stream ConverseEvent);
}

message ConverseTurnRequest {
  string product_id   = 1;   // data-scoping key (00 §10); e.g. "tacticl"
  string user_id      = 2;   // resolved tacticl user id (already linked/authorized by the caller)
  string session_id   = 3;   // caller's voice session id (transcript linkage; NOT routing)
  string turn_id      = 4;   // client UUIDv4 for idempotency (SAD §3.3.1.x)
  string text         = 5;   // the final user transcript for this turn
  string persona_hint = 6;   // optional: persona id the caller's router picked; blank → arbiter routes
  repeated Turn history           = 7;  // prior turns this session (oldest first), caller-supplied
  repeated PipelineRef pipelines  = 8;  // user's in-flight pipelines (name + id + status) for grounding
  string locale       = 9;   // optional
}

message Turn {
  string role = 1;   // "user" | "assistant"
  string text = 2;
  string persona_id = 3; // optional, for sticky-persona routing
}

message PipelineRef {           // mirrors PipelineSummary (SAD §3.6.3) — names, never raw UUIDs to the user
  string pipeline_run_id = 1;
  string name = 2;
  string status = 3;            // RUNNING | BLOCKED | ...
  string current_role = 4;
  string blocked_checkpoint_id = 5;
}

message ConverseEvent {
  string type = 1;     // "started" | "token" | "tool_use" | "done" | "error"
  string text = 2;     // token: the reply text delta (the TTS chunk)
  string persona_id = 3;   // which persona produced this turn (for transcript attribution)
  ToolUse tool_use = 4;    // type == "tool_use": a skill the CALLER must execute (e.g. start_pipeline)
  Usage usage = 5;         // type == "done"
  double cost_usd = 6;     // type == "done"
  string message = 7;      // type == "error": user-safe message
  string turn_id = 8;
}

message ToolUse {
  string id = 1;
  string name = 2;          // skill id, e.g. "start_pipeline" | "propose_implementation"
  string input_json = 3;    // arguments per the skill's inputSchema
  bool   terminal = 4;      // Phase 1: side-effecting skills are terminal (no tool_result fed back)
}

message Usage { int32 prompt_tokens = 1; int32 completion_tokens = 2; int32 total_tokens = 3; }
```

**Event ordering:** `started` → `token`* (stream the reply; caller feeds each to TTS + a partial `transcript` frame) → optional `tool_use` (e.g. `start_pipeline` — caller executes; `propose_implementation` — caller may render a confirm) → `done` (usage/cost) | `error`.

**Why `tool_use` is "caller‑executed & terminal" in Phase 1:** the arbiter runs the LLM tool‑use loop for **cognitive** skills (it can answer, propose, and — later — research entirely inside the loop). But **side‑effecting** skills that tacticl‑core already owns (`start_pipeline`, and later `dispatch_to_device`, `start_cloud_skill`) are surfaced to the caller to execute against the existing, working local machinery (run persistence + narration). They are emitted **terminally** (the persona says "Starting that now." then emits the tool_use), so Phase 1 needs no tool_result round‑trip and the server‑streaming RPC suffices. Full in‑loop tool_results (bidi) arrive with `VoiceStream` in a later phase. This matches the canonical OUTBOUND direction (arbiter→product for `StartCloudSkill`/`DispatchToDevice`, migrate plan §7).

---

## 6. Component design

### 6.1 tacticl‑core: the `ConversationEngine` seam

```java
// business-voice — the single swap point. business→client edge is legal.
public interface ConversationEngine {
    /** Stream a persona reply for one turn. Implementations MUST NOT block the STT thread on the
     *  whole reply — they emit deltas via the sink as they arrive. Returns when the turn is done. */
    void converse(ConversationContext ctx, ConversationSink sink);
}
public record ConversationContext(String productId, String userId, String sessionId,
                                  String turnId, String userText, List<VoiceSession.Utterance> history,
                                  List<PipelineRef> pipelines) {}
public interface ConversationSink {
    void onToken(String delta);                 // → incremental TTS + partial transcript frame
    void onToolUse(String name, String inputJson); // → execute side-effecting skill locally
    void onDone();
    void onError(String userSafeMessage);
}
```

- **`ArbiterConversationEngine` (primary)** — wraps a new `ConversationServiceClient` (async stub on the existing `arbiterManagedChannel`); maps `ConverseEvent`s onto the `ConversationSink`. Lives in `business-voice`; adds `implementation(project(":client:client-ai-arbiter"))` to `business-voice/build.gradle.kts`.
- **`AnthropicDirectConversationEngine` (fallback)** — the current `VoiceConversationTurnHandler.generateReply()` lifted verbatim (`AnthropicDirectClient.generateContent`). Kept as `@ConditionalOnMissingBean(ConversationEngine.class)` so local/dev and arbiter‑down still speak.
- **`VoiceConversationTurnHandler`** stops calling Anthropic directly; it injects `ConversationEngine` and drives the sink: `onToken` → incremental TTS (one `ElevenLabsSession`, `sendTextChunk` per delta, `flush` on `onDone`) + partial assistant `transcript` frames; `onToolUse("start_pipeline", …)` → call `IngressDispatchService` to start the pipeline **locally** (§7); `onDone` → state IDLE; `onError` → friendly error frame (existing behavior).
- **Selection** (mirrors `ArbiterClientConfig.java:22-58`): `ArbiterConversationEngine` `@ConditionalOnProperty(name="tacticl.voice.arbiter-conversation.enabled", havingValue="true")`; fallback via `@ConditionalOnMissingBean`. New flag set `true` only in `application-prod.properties` (where voice is already on); local stays on the fallback.

**Threading:** gRPC stream callbacks run on the grpc executor, not the STT thread (confirmed). `VoiceSession` is already thread‑safe; the sink implementation serializes TTS feeding per session.

### 6.2 arbiter: the conversation brain (`packages/conversation`)

1. **Minimal persona + skill registry.** Phase 1 does **not** require the full Mongo `personas`/`skills` collections. Start with the conversational‑persona prompts (ported to `packages/personas/conversational/*.md`, per migrate plan §4.4) + a small in‑code skill catalogue (tool defs: `answer_in_conversation`, `propose_implementation`, `start_pipeline`; research skills deferred). Resolve `persona → tool defs` exactly like the canonical `PersonaRegistry.toolsFor(personaId)` will (SAD §4.2) — same shape, smaller backing store, so the Mongo registry drops in later without changing the loop.
2. **Persona routing.** Port the pure `PersonaRouter` function (SAD §7.1) into `packages/conversation` and call it inline (`persona_hint` from the caller is an override/short‑circuit; if blank, route from `text` + `history`). 19 router tests port verbatim (migrate plan Agent 4).
3. **Tool‑use loop (the net‑new core).** Extend the arbiter's Anthropic provider to support tool use — add `tools` to `buildMessageParams`, parse `tool_use` blocks in the response, run the multi‑turn loop (`tool_use` → execute → `tool_result` → continue) — OR add a dedicated conversation runner using the Anthropic SDK with tools. **Streaming:** emit `token` events from `text_delta` (mirror `GenerateStream`, `grpc-service.ts:189-240`). **Side‑effecting skills** (`start_pipeline`) are not executed in‑loop in Phase 1: the loop emits them as a terminal `tool_use` event and ends the turn.
4. **Provider routing + keys.** Reuse `packages/core` provider routing (Anthropic primary + fallback) and the existing Vault key loading (`server.ts`); this is the whole point of running in the arbiter (multi‑provider fallback, not a parallel path) — migrate plan §1.1.
5. **productId scoping.** `product_id` flows from the request into routing + (optional) knowledge; no product branches.
6. **gRPC registration.** Add the proto, `loadSync`→`loadPackageDefinition`→drill namespace→`grpcServer.addService(...)` before `bindAsync` (pattern at `server.ts:599-616`).

### 6.3 What the interim handler becomes

The 2026‑06‑03 in‑JVM `VoiceConversationTurnHandler` + per‑session history + the spoken persona prompt **stay**, but the brain call is swapped to the seam. The Anthropic‑direct path is demoted to `AnthropicDirectConversationEngine` (fallback). Nothing is deleted; the cognition relocates to the arbiter, the audio stays put.

---

## 7. The `start_pipeline` ownership decision (Phase 1 vs end‑state)

**Problem:** tacticl‑core owns the *working* pipeline lifecycle — `PdlcV2Service` persists the local `PipelineRun`, creates the Spark, and the arbiter callback fan‑out narrates progress through `VoiceRunUpdateChannel` into the bound voice session. The canonical end‑state (migrate plan §7, SAD §3.3.2) moves start_pipeline into a child `PipelineWorkflow` in the arbiter and has the arbiter write the Spark to Mongo directly.

**Phase 1 decision (recommended):** the arbiter persona **decides** to build and emits a terminal `tool_use: start_pipeline{sparkInput, playbook?}`; **tacticl‑core executes it locally** via `IngressDispatchService.handleExplicitTrigger` → `PdlcV2Service.submitPipeline`, binding the new run to the voice session so the **existing** event→`VoiceRunUpdateChannel` narration path lights up unchanged. This keeps run ownership, Spark creation, checkpoints, and narration on the proven local path — zero orphaning — while the *cognition* (deciding to build, after reviewing intent) is the arbiter analyst's.

**End‑state (later phase):** start_pipeline runs in‑loop in the arbiter (`Shell.submitPipeline`), the arbiter writes the Spark to Mongo (recommendation A, `00` §6), and the conversation narrates from the arbiter `PipelineWorkflow`. tacticl‑core's `PdlcV2Service` path retires with the rest of the voice‑plane relocation (Phase 3).

This means the user's exact ask — *the analyst reviews intent and instantiates the pipeline* — is satisfied in Phase 1: the analyst (arbiter) reviews and decides; the pipeline starts and narrates. Only the *bookkeeping location* of "who calls submit" differs between Phase 1 (tacticl‑core) and end‑state (arbiter), and that's invisible to the user.

---

## 8. Sequencing

- **Phase 1a — arbiter brain (text):** proto + `ConverseTurn`; minimal persona/skill registry; Anthropic tool‑use extension + streaming tool loop; `PersonaRouter` port; `answer_in_conversation` + `propose_implementation` + terminal `start_pipeline`. Deploy to `cidadel-arbiter-qa`/`prod`.
- **Phase 1b — tacticl‑core seam:** `ConversationEngine` interface + both impls; `ConversationServiceClient` (proto/codegen/async stub/beans); swap `VoiceConversationTurnHandler` to the seam; incremental TTS streaming; `start_pipeline` tool_use → local `IngressDispatchService`. Gate `tacticl.voice.arbiter-conversation.enabled` (prod only).
- **Phase 1c — verify e2e** against `cidadel-arbiter-qa` then prod; fallback engine proven by disabling the flag.
- **Phase 2 — research skills + in‑loop tools:** move/bridge Brave/Jina so the Market‑Researcher analyst can `web_search`/`analyze_competitors` in‑loop; upgrade `ConverseTurn`→bidi for tool_result round‑trips; Mongo persona/skill registries.
- **Phase 3 — voice transport relocation:** STT/TTS bridges → arbiter `packages/voice/`; browser↔arbiter WS direct with tacticl‑core‑signed session token (`00` §4); retire tacticl‑core `business-voice` transport + `PdlcV2Service`. Pure latency win.

**Interim infra (independent, user‑owned, blocks ALL voice regardless of this design):** Caddy `microphone=(self)` on `tacticl.ai`; Vault `secret/tacticl/{deepgram,elevenlabs}` `api-key`.

---

## 9. Open decisions requiring sign‑off

1. **Analyst persona identity & skills (§3).** Confirm the "analyst" = Product Manager (default, owns `start_pipeline`) with Market Researcher as the research specialist; OR mint a single "analyst" persona that has both research and `start_pipeline` (data‑only deviation from canon). Affects only registry data + a routing branch.
2. **`start_pipeline` ownership in Phase 1 (§7).** Confirm tacticl‑core executes the terminal tool_use locally (recommended) vs arbiter‑owned from day one (more rework, orphans local narration).
3. **Knowledge augmentation for conversational turns (§2).** Canon wires `KnowledgeLoader` into *pipeline role* prompts only; it's silent on conversational turns. Decision: do analyst turns read product knowledge per turn (better answers, +Mongo/Qdrant latency against the ~1s budget) or not (v1: no augmentation)? Recommended v1: **no per‑turn augmentation**; revisit with telemetry.
4. **Persona/skill registry productId scoping** — shared‑tagged vs per‑product namespaces — is an **open** canon question (`00` closing). Phase 1's minimal registry shouldn't bake either in.
5. **Routing reach to the analyst.** Market‑Researcher is reached only via `MARKET_INTENT_RE` or sticky persona (SAD §7.1). If the "analyst" should be the *default*, that's the Product Manager — confirm with (1).

---

## 10. Test plan

- **arbiter:** unit — persona routing (19 ported tests); tool‑loop (tool_use parsed, `tool_result` fed for cognitive skills, terminal emit for `start_pipeline`); streaming token order; product scoping. Integration — `ConverseTurn` happy path + error + terminal start_pipeline against a mock provider.
- **tacticl‑core:** unit — `ArbiterConversationEngine` maps events→sink (token/tool_use/done/error); `AnthropicDirectConversationEngine` parity with current `VoiceConversationTurnHandler` tests; selection (flag on → arbiter, off → fallback). The existing `VoiceConversationTurnHandlerTest` is refactored to drive the sink. Contract — proto stays byte‑identical across repos (a CI check or shared‑source).
- **e2e:** qa arbiter + local tacticl‑core voice: speak → analyst replies (streamed TTS); "build X" → analyst reviews/decides → terminal start_pipeline → local pipeline narrates.

## 11. Risks

- **Tool‑use loop is net‑new in the arbiter** (Anthropic provider is text‑only today). Mitigate: small, well‑tested loop; cognitive‑only skills first; `start_pipeline` terminal (no round‑trip).
- **Latency**: extra product→arbiter gRPC hop in Phase 1 (LAN, ms) vs the ~1.2s budget — acceptable; streaming starts TTS on the first sentence. Phase 3 removes the hop.
- **Two LLM paths during rollout** (arbiter primary + Anthropic‑direct fallback) — intended and flag‑gated; retire the fallback once arbiter is proven.
- **Proto drift across repos** — keep byte‑identical; consider a single source‑of‑truth proto later.
```
