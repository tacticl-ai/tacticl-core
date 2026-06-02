# Jarvis — Communicator → readyToBuild Gate → Spawn Build (Definitive Design Spec)

**Status:** Canonical design, revised against ground truth at HEAD (`cidadel-ai-arbiter @ eb734ce`, `tacticl-core @ 09612be`, `tacticl-web @ committed protocol.ts`). Folds in every CRITICAL/HIGH finding from three adversarial reviews. Supersedes the draft's falsified "reuse" claims.
**Locked context:** Option B — all orchestration + LLM live in the arbiter (`cidadel-ai-arbiter`, TS/Temporal); `tacticl-core`/`strategiz-core` are product backends + thin channels. 2026-05-30 handover, `tacticl-core/docs/handover/2026-05-30-orchestrator-migration/`.
**Governing principle:** The communicator is **data + a thin loop, not a heavy subsystem**. Adding a product's Jarvis = three registry documents. Promoting a lens to a real sub-agent = one `PersonaRouter` route + one `agent_type`. Zero change to the conversational surface (the `ConverseTurn`/`SessionEvent` wire contract) or the UI.

> **Three corrections that the draft got factually wrong (verified by `grep`/`sed` against both repos) and that this revision bakes in everywhere:**
> 1. **There is no `PersonaRouter`, no `Interviewer`, no `analyst`, no `product-manager.md` in the arbiter.** The communicator loop is **net-new arbiter code**, *seeded from* (not "migrated from") the legacy `tacticl-core ConversationService.java` prompt + marker-gate semantics and `pm.json`. Effort is budgeted as net-new.
> 2. **`SubmitPipeline` bypasses Temporal entirely** — it routes to `shell.submitPipeline(...)` (the legacy in-memory `PipelineTracker`), not `decideExecutor`/`pdlcRunWorkflow`. Therefore **"the build is a child of the conversation" is a Phase-D property only.** In Phases 0–2 the spawned build is a *detached legacy-tracker run*; `ParentClosePolicy.ABANDON` is inert until Temporal is the live executor.
> 3. **The committed `VoiceSessionService.classify()` is the *inverse* of the draft's flow.** `BUILD_INTENT_PREFIXES = ["build ", "create ", "implement ", "fix ", "add ", "refactor ", "ship ", "generate ", "write ", "deploy ", "make ", "send to pdlc"]` is a greedy prefix catch-all: "add a dark-mode toggle" / "fix the login bug" / "make it faster" all classify as `EXPLICIT_TRIGGER` and fire the pipeline *today*. **Gutting this list is the FIRST committed change**, with a regression test — nothing else works until it lands.

---

## 0. Thesis

The conversation front is **one persona — the Analyst** (a net-new registry doc `{product}/agents/analyst`, authored by porting the legacy `ConversationService.java` `GATHERING_SYSTEM_PROMPT_TEMPLATE` + marker-gate semantics and `pm.json`). It carries **three lenses as skills** (Business / Product / Architect), invoked mid-turn, surfaced on the HUD ("◈ consulting: feasibility") for the multi-agent *feel* with one coherent voice. The conversation **is** a session — proven first in an **in-memory v1 behind a real unary `ConversationService.SubmitTurn` RPC** (turn-in → reply-out, no streaming), then promoted to a durable Temporal `ConversationSessionWorkflow` (+ a server-streamed `StreamSession` for real-time voice narration) via a `decideConversationExecutor('inmemory'|'durable')` swap that moves zero protocol/UI surface. Each turn runs the Analyst (LLM activity) → a conversational reply + lens-delta + HUD label; the **readyToBuild gate is a SERVER-decided structured verdict** (not an LLM-callable tool) that folds the existing single-shot `classify`/`route` activities, and the build can **only** fire after (a) all three lenses are satisfied with **tool-call provenance** + classification is actionable, (b) a **conversation-budget ceiling** has not been exceeded, and (c) the human APPROVES a checkpoint whose **id is the idempotency key**. On approval, the session spawns the build — in v1 a detached `Shell.submitPipeline` run, in v2 `startChild(pdlcRunWorkflow, { brief, briefAlreadyConfirmed:true }, ParentClosePolicy.ABANDON)` — narrated back into one voice. The voice WebSocket terminates in **tacticl-core** (STT/TTS server-side); the arbiter is text-in/text-out behind it. Everything is additive, flag-gated; neither build goes red.

We are **inverting the arbiter's current shape**: today `SubmitPipeline` starts the build, which (in the Temporal path, not yet live) would run its own intake. We add a *sibling* conversational session that interviews *first*, gates on a server verdict, then spawns the build. We reuse — never rewrite — the **single-shot `classify`/`route` intake activities** (as the gate's projection), the `pdlcRunWorkflow` (as the eventual child), the legacy `ConversationService.java` prompt (as the *port source*, deleted at parity — not Phase 0), and the committed `protocol.ts` (with two small, explicit additions: `resumeSessionId` + `audio_format`).

---

## 1. End-to-end flow (target state, Phase D)

```
                                          ┌──────────────── tacticl-web ────────────────┐
                                          │ voice sphere · push-to-talk · text composer  │
                                          │ multi-turn transcript · HUD                   │
                                          │ src/voice/protocol.ts  (+ resumeSessionId,    │
                                          │   + audio_format negotiation — §3.6/§5.5)     │
                                          └──────▲────────────────────────────┬──────────┘
                                                 │ WS DOWN: state/level/        │ WS UP: start/stop/text(NEW)/
                                                 │  transcript/hud/checkpoint/  │  barge_in/decision + binary PCM
                                                 │  err + binary TTS audio      │
┌──────────────────────────── tacticl-core (thin voice/transport edge) ────────┼───────────┐
│                                                                               ▼           │
│  service-voice  VoiceWebSocketHandler /v1/voice   (+ NEW  case "text")                    │
│     │ binary→pushAudio        │ control: start|stop|barge_in|decision|TEXT(new)           │
│     ▼                         ▼                                                           │
│  business-voice  VoiceSessionService                                                      │
│   ├ DeepgramSttBridge (server-side STT) final→onFinalTranscript ─┐                        │
│   ├ ElevenLabsTtsBridge (server-side TTS) ◀── narration text ──┐ │                        │
│   └ classify() — GUTTED to genuine markers only (§3, F-classify):                         │
│        EXPLICIT_TRIGGER  ("/pdlc…", "send to pdlc", "just build") ─┐  (text only; voice    │
│                                                                    │   needs wake-phrase) │
│        CONVERSATION_TURN  (TRUE DEFAULT — everything else) ────────┼─┐                     │
│  business-pipeline IngressDispatchService.dispatch(IngressRequest) │ │                     │
│     EXPLICIT_TRIGGER → PdlcV2Service.submitPipeline ───────────────┼─┼──────┐              │
│  CONVERSATION_TURN → ConversationTurnHandler (was ZERO impls) ─────┼─┼──▶ ArbiterConversationRelay
│                                                                    │ │     (NEW; holds per-session │
│  client-ai-arbiter:                                                │ │      StreamSession sub; owns │
│   ├ ArbiterPipelineService (BlockingStub, EXISTING) SubmitPipeline ┼─┼──┐   the orb until done)      │
│   └ ConversationSessionClient (NEW):                               │ │  │                            │
│       v1: SubmitTurn (UNARY, reply-in-response)   ─────────────────┼─┼──┼──┐                         │
│       v2: + StreamSession (server-stream, BlockingStub Iterator) ──┼─┼──┼──┼─ SessionEvent stream ─┐ │
└────────────────────────────────────────────────────────────────────┼─┼──┼──┼──────────────────────┼─┘
                                                          (gRPC unary) │ │  │  │ (gRPC server-stream) │
┌──────────────────────────── cidadel-ai-arbiter (the ONE brain) ─────┼─┼──┼──┼──────────────────────┼─┐
│                                                                      │ │  ▼  ▼                      ▼ │
│  ConversationService (NEW gRPC handler)                              │ │  decideConversationExecutor()│
│    OpenSession · SubmitTurn(unary reply) · StreamSession(v2) · SubmitDecision · CloseSession         │
│    ┌──────── v1 InMemorySessionManager ────────┐  ┌──── v2 ConversationSessionWorkflow (Temporal) ──┐│
│    │ Map<sessionId,SessionState>; idle sweeper;  │  │ turns[]+lenses+buildEvents[] in history;        ││
│    │ runPersonaTurn/evaluateReadyGate/buildBrief │  │ idle TIMER; resume; same 3 fns AS ACTIVITIES;   ││
│    │ as plain async fns (activity-shaped)        │  │ signals/queries/updates; PersonaRouter (pure)   ││
│    └───────┬──────────────────────────────────────┘  └───────┬──────────────────────────────────────┘│
│            ▼  (same activity-shaped fns; same proto; flag swap)▼                                       │
│  runPersonaTurn → Router.generate(system=analyst persona, history[], tools=ANALYST_TOOLS)             │
│       (Phase 2: → Router GenerateStream for token-streamed narration — EXISTS at arbiter.proto:6)     │
│  evaluateReadyGate → SERVER verdict; folds classifyIntake + routeIntake (EXISTING single-shot acts)   │
│  loadAnalystPersona(product) → registry-client.getBootTemplate + knowledge-loader.loadForAgent        │
│                                                                                                       │
│  GATE PASS (server) + budget-ok + HITL APPROVE ─▶ start_pipeline(brief, intakeId = approvedCheckpoint)│
│      v1:  Shell.submitPipeline(brief)                 (LEGACY in-mem tracker — DETACHED run, no child) │
│      v2:  startChild(pdlcRunWorkflow,{brief,briefAlreadyConfirmed:true}, ABANDON, wfId=pdlc-${intakeId})│
│      idempotent: re-check phase after await + treat WorkflowExecutionAlreadyStarted as success         │
│                                                                          │                            │
│  EXPLICIT_TRIGGER  ───────▶  SubmitPipeline (unary, EXISTING → shell.submitPipeline LEGACY tracker)   │
│                                                                          ▼                            │
│  pdlcRunWorkflow (build pipeline; +additive briefAlreadyConfirmed skip behind NEW patched('…v1'))     │
│    Phase A extract→classify→route→merge-gate   (interview SKIPPED when briefAlreadyConfirmed;          │
│                                                  classify/route STILL RUN — they own repo resolution)  │
│    Phase B dev DAG: Product Owner → BUILD Architect (details rough approach) → … → PR                  │
│    progress + checkpoints ──▶ narrated back INTO the session (buildEvents[]) ──▶ one voice             │
└───────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. The arbiter CONVERSATION session + Analyst persona + readyToBuild gate

### 2.1 New module layout (`cidadel-ai-arbiter/packages/server/src/`)

Name everything `conversation/*` — **never** `orchestrator/*` (verified collision: `orchestrator/orchestrator-session.ts` is the Claude-Code recovery brain).

```
conversation/
  conversation-service.ts            ← gRPC handler; decideConversationExecutor('inmemory'|'durable')
  inmemory-session-manager.ts        ← v1 substrate (Map<sessionId, SessionState>; idle sweeper)
  persona-router.ts                  ← PersonaRouter (pure fn; NET-NEW — only `analyst` route live; others dormant)
  session-types.ts                   ← SessionInput, SessionState, ConverseTurn, SessionEvent, ReadyGateVerdict, BuildBrief
  session-constants.ts               ← idle TTLs, max turns, budget caps (workflow-safe consts)
  activities/
    run-persona-turn.ts              ← runPersonaTurn(persona, history, lenses) → {reply, lensDeltas, activeLensLabel, toolProvenance}
    evaluate-ready-gate.ts           ← evaluateReadyGate(history, lenses, product) → ReadyGateVerdict  (calls classify/route)
    build-brief.ts                   ← buildBrief(state, verdict, checkpointId) → BuildBrief  (intakeId = checkpointId)
    persona-loader.ts                ← loadAnalystPersona(product) via registry-client + knowledge-loader
  temporal/                          ← v2 ONLY
    conversation-session-workflow.ts ← sessionWorkflow(input)  (sibling to pdlc-run-workflow.ts; LLM only via activities)
    conversation-session-client.ts   ← makeConversationClient: signalWithStart/signal/executeUpdate/query
```

`runPersonaTurn`/`evaluateReadyGate`/`buildBrief` are **activity-shaped from day one** (plain async fns in v1), so v2 registers them as Temporal activities verbatim. The v2 workflow imports only `@temporalio/workflow` + `session-constants` + type-only modules — the determinism discipline verified in `pdlc-run-workflow.ts` (proxyActivities for every LLM call; no LLM in the workflow body).

> **Honest reuse surface:** the **`classify`/`route` activity implementations** (`temporal/activities/intake/classify-intake.ts`, `route-intake.ts`) are single-shot and genuinely reusable inside the gate. The arbiter has **no pre-build conversational interviewer** — the only "interview" today is the *post-build merge-gate* park inside `pdlc-run-workflow.ts` (`answerInterviewSignal`/`openAsk`/`APPROVE_MERGE`). The `runPersonaTurn` loop is therefore net-new.

### 2.2 Session state (durable via event history in v2; Map entry in v1 — identical shape)

```ts
interface SessionState {
  sessionId: string;
  product: string;                 // productId scopes persona + rubric + knowledge + pipeline
  userId: string;
  channel: 'voice' | 'text' | 'discord';
  turns: Turn[];                   // the conversation IS this array (replays for free in v2)
  buildEvents: BuildEvent[];       // F11: build progress/checkpoints persisted here so snapshot rehydrates the LIVE checkpoint
  lenses: {
    business:  LensState;          // why / value / acceptance criteria
    product:   LensState;          // scope / dedup / product-fit / smaller-version
    architect: LensState;          // feasibility / rough approach / target repo
  };
  classification?: PdlcClassification;   // reused intake type — populated by evaluateReadyGate
  routing?: PdlcRouting;                 // reused intake projection — DISPLAYED, not authoritative (F8)
  phase: 'gathering' | 'proposing' | 'submitting' | 'building' | 'detached_monitor' | 'done' | 'idle_expired';
  budget: { turns: number; usdSpent: number; proposalRounds: number };  // F4/F10 ceilings
  stickyPersona?: string;          // future sub-agent routing
  pendingProposal?: { brief: BuildBrief; checkpointId: string };  // checkpointId = the idempotency key (F1)
  childPipelineId?: string;        // v1: legacy tracker run id (for narration correlation) / v2: child workflowId
  lastActivityAt: number;          // v1 sweeper bookkeeping (v2 uses a durable timer)
}

interface LensState {
  satisfied: boolean;              // SERVER-set, never an LLM float
  summary: string;
  openQuestions: string[];
  provenance?: { peekRepo?: string; searchKnowledge?: boolean };  // F6: required for satisfied:true on product/architect
}
```

> **F6 (gate grades its own homework):** `LensState.satisfied` is set by the **server** in `evaluateReadyGate`, NOT lifted from the LLM's self-report. `architect.satisfied` requires `provenance.peekRepo` (a real repo/file ref, not prose). `product.satisfied` requires `provenance.searchKnowledge === true` (a dedup call ran). The `confidence: number` field from the draft is **dropped** — boolean + evidence-with-provenance, no threshold rathole (Open Q3 closed).

### 2.3 Workflow signals / queries / updates (v2)

```ts
// SIGNALS (fire-and-forget control)
defineSignal<[DecisionPayload]>('submitDecision')   // HITL APPROVE/CHANGES/REJECT on the proposal checkpoint
defineSignal<[void]>('bargeIn')                      // edge-driven; arbiter-level abort is a follow-up (F6)
defineSignal<[CancelPayload]>('cancelSession')
defineSignal<[PipelineEvent]>('pipelineEvent')       // child pipeline event fanned back in → buildEvents[]

// UPDATES (request/response — turn-ingest ACK; v2 only)
defineUpdate<TurnAck, [ConverseTurn]>('turnUpdate')  // returns {turnId, accepted}; validator rejects empty/closed/over-budget
defineSignal<[ConverseTurn]>('submitTurn')           // fallback for non-Update-capable callers

// QUERIES (replay-safe state read — reconnect hydration)
defineQuery<SessionSnapshot>('snapshot')             // phase, lenses, last N turns, childPipelineId, + LIVE build checkpoint (F11)
defineQuery<ReadyGateVerdict | null>('lastGate')
```

> **v1 note:** v1 uses **no streaming and no Update** — `SubmitTurn` is a plain unary RPC returning the reply (§3). The Update/Signal/Query machinery is v2-only. This is the single largest de-scope from the draft (F5/F9) and keeps the first demo off the never-run Temporal worker and off the codebase's first streaming gRPC client.

### 2.4 The session loop (shared logic; runs as plain async in v1, as the workflow body in v2)

```ts
// v1: InMemorySessionManager.handleTurn(state, turn) → returns the reply synchronously (unary).
// v2: the sessionWorkflow body below. SAME runPersonaTurn/evaluateReadyGate/buildBrief calls.

async function processTurn(state, turn, narrate): Promise<ConverseReply> {
  // ── BUDGET CEILING (F4) — checked BEFORE any paid LLM call ──
  if (state.budget.turns >= CONVERSATION_MAX_TURNS || state.budget.usdSpent >= CONVERSATION_BUDGET_USD) {
    state.phase = 'done';
    return reply("I can't scope this from here — let's hand it to a human or start fresh.", { escalate: true });
  }
  state.budget.turns++;
  state.turns.push({ role:'user', text: turn.text, turnId: turn.turnId });

  const routing = PersonaRouter.route(turn, state.lenses, state.phase, state.stickyPersona);  // pure; only 'analyst' live
  if (routing.kind === 'control') return handleControl(routing, state, narrate);

  const result = await runPersonaTurn({ persona, product: state.product, history: state.turns, lenses: state.lenses });
  state.budget.usdSpent += result.costUsd;                    // accrue interview spend (F4)
  state.turns.push({ role:'assistant', text: result.reply });
  state.lenses = mergeLensDeltasWithProvenance(state.lenses, result.lensDeltas, result.toolProvenance);  // server merge (F6)
  narrate('hud', { note: result.activeLensLabel });           // "◈ consulting: feasibility"
  narrate('transcript', { role:'assistant', text: result.reply, partial:false });

  // ── THE GATE — SERVER-decided (F5): the model never "fires" the build ──
  // We evaluate when all three lenses LOOK satisfiable; the SERVER decides whether to emit a checkpoint.
  if (allLensesLookSatisfied(state.lenses)) {
    const verdict = await evaluateReadyGate({ history: state.turns, lenses: state.lenses, product: state.product });
    state.lastGate = verdict;
    const ready =
      verdict.lenses.business.satisfied &&
      verdict.lenses.product.satisfied  && verdict.lenses.product.provenance?.searchKnowledge === true &&  // F6
      verdict.lenses.architect.satisfied && !!verdict.lenses.architect.provenance?.peekRepo &&             // F6
      ['bug','feature'].includes(verdict.classification.type) && !verdict.classification.trivial;          // actionable
    if (ready) {
      state.classification = verdict.classification;
      state.routing        = verdict.routing;                 // DISPLAYED only; build re-computes (F8)
      const checkpointId   = `propose-${state.sessionId}-${state.budget.proposalRounds}`;  // F1: STABLE key
      const brief          = await buildBrief({ state, verdict, intakeId: checkpointId });  // intakeId === checkpointId
      state.pendingProposal = { brief, checkpointId };
      state.phase = 'proposing';
      narrate('checkpoint', { checkpointId, title: brief.proposalSummary, options: ['APPROVE','CHANGES','REJECT'] });
    } else {
      narrate('transcript', { role:'assistant', text: verdict.blockingQuestion, partial:false });  // ask exactly the blocker
    }
  }
  return reply(result.reply, { phase: state.phase, checkpoint: state.pendingProposal?.checkpointId });
}

// ── HITL DECISION (proposal outstanding) — workflow-idempotent (F2) ──
async function onDecision(state, d, narrate) {
  if (state.phase !== 'proposing') return;                    // F2: re-check guard (handles at-least-once double-delivery)
  if (d.checkpointId !== state.pendingProposal!.checkpointId) return;  // stale checkpoint → ignore
  if (d.decision === 'APPROVE') {
    state.phase = 'submitting';                               // F2: flip SYNCHRONOUSLY before the await
    const { brief, checkpointId } = state.pendingProposal!;
    try {
      const child = await spawnBuild(brief, checkpointId);    // wfId = pdlc-${brief.intakeId} = pdlc-${checkpointId}
      state.childPipelineId = child.id; state.phase = 'building';
      narrate('hud', { phase:'submitted', runId: child.id, note: "Build started — I'll narrate progress." });
      void pumpChildProgress(child, state, narrate);          // → state.buildEvents[] + narrate (F11)
    } catch (e) {
      if (isAlreadyStarted(e)) { state.childPipelineId = idFor(brief); state.phase = 'building'; }  // F2: dedup = success
      else { state.phase = 'proposing'; narrate('error', { message:'Could not start the build — try again?' }); }
    }
  } else if (d.decision === 'CHANGES') {
    state.budget.proposalRounds++;
    if (state.budget.proposalRounds >= CONVERSATION_MAX_PROPOSAL_ROUNDS) {   // F10
      state.phase = 'done';
      narrate('transcript', { role:'assistant', text:"We've gone back and forth a few times — want me to hand this to a human or drop it?" });
      return;
    }
    state.pendingProposal = undefined; state.phase = 'gathering';
    /* fold feedback as a synthetic user turn on the next processTurn */
  } else { /* REJECT */ state.pendingProposal = undefined; state.phase = 'gathering';
    narrate('transcript', { role:'assistant', text:'Scrapped. What should we do instead?' }); }
}
```

`spawnBuild` is the only mode-divergent call:
- **v1 (legacy):** `Shell.submitPipeline({ ...brief })` → detached in-memory tracker run. **No parent/child, no ABANDON** (F4). Records `childPipelineId` for narration correlation only.
- **v2 (durable):** `startChild(pdlcRunWorkflow, { args:[{ ...brief, briefAlreadyConfirmed:true }], workflowId: \`pdlc-${brief.intakeId}\`, parentClosePolicy: ParentClosePolicy.ABANDON })`.

### 2.5 Idempotency — the two CRITICAL closures (F1 + F2)

1. **Stable idempotency key (F1).** `brief.intakeId = checkpointId`, and `checkpointId = propose-${sessionId}-${proposalRound}` is minted **when the checkpoint is emitted**, not when APPROVE arrives. `buildBrief` is passed the `checkpointId` and must echo it verbatim — never `randomUUID()`. The build `workflowId = pdlc-${intakeId}` + the verified existing `WorkflowIdReusePolicy = REJECT_DUPLICATE` (`pdlc-client.ts`) then collapses any double-APPROVE / decision-replay / brief-retry to a single build.
2. **Workflow-idempotent transition (F2).** Temporal signals are at-least-once. `onDecision` (a) early-returns unless `phase === 'proposing'`, (b) flips `phase = 'submitting'` **synchronously before** the `await spawnBuild`, and (c) wraps `spawnBuild` in try/catch that treats `WorkflowExecutionAlreadyStarted` as success (re-attach). **Required test:** two `submitDecision(APPROVE)` for the same `checkpointId` ⇒ exactly one `startChild`.

### 2.6 Single-active-session-per-user (F9) — Phase-0 correctness requirement

A user on voice + a second tab (or a reconnect minting a fresh `sessionId`) must **not** get two concurrent interviews that both reach a build. **`userId → activeSessionId` claim lives in tacticl-core** (where identity lives), in `ArbiterConversationRelay`: a reconnect attaches to the live session via `OpenSession{resume_session_id}` instead of minting a new one. This lands in **Phase 0**, not deferred. (Couples with F1: even if two sessions slipped through, distinct `sessionId` ⇒ distinct `checkpointId` ⇒ distinct `workflowId` ⇒ two builds — so the relay-side single-active claim is the real guard.)

### 2.7 Child-spawn lifecycle (the correctness graft — Phase D only)

- **`ParentClosePolicy.ABANDON`** — an idle-expired / user-closed conversation must not kill a running build. **Phase-D property only** (no child exists in v1/v2-legacy).
- **Orphaned-build closure (F7).** On `idle_expired` while `phase === 'building'`, the session does **not** simply abandon-and-vanish. It transitions to a lightweight **`detached_monitor`** state: no LLM, no interview, just relays the child's checkpoints to a fallback channel (Discord/email via the existing notification path) so a mid-build merge-gate can still be answered. Additionally, the build pipeline must carry its own checkpoint-timeout → auto-reject so an orphaned build self-cancels rather than parking forever. **State both**; ship at least the build-side timeout in Phase D.
- `CONVERSATION_CANCELS_BUILD=false` (default ABANDON); `true` ⇒ `child.cancel()` on session close.

### 2.8 The Analyst persona + three lenses as skills

One persona, one voice. Three lenses are **skills it invokes**, surfaced on the HUD. The skill interface is fixed so each lens is **promotable to a real sub-agent with zero conversational-surface change**:

```ts
interface LensConsultant { consult(lens: 'business'|'product'|'architect', ctx): Promise<LensDelta>; }
// v1/v2 now:  InlineLensConsultant — a lens-rubric section in the single runPersonaTurn prompt (one voice, one call)
// later:      SubAgentLensConsultant — invokes a real BA/PA/Architect sub-agent (arbiter SpawnSubAgent — currently stubbed code:12)
//             The Analyst becomes a FACILITATOR; HUD still shows "◈ consulting: feasibility"; the wire/UI never change.
```

`ANALYST_TOOLS` passed in every `runPersonaTurn` Generate call (ported from the legacy `GATHERING_SYSTEM_PROMPT_TEMPLATE` skills + `pm.json`):

| Tool | Purpose | Lens |
|---|---|---|
| `consult_lens(lens, focus)` | Run a lens sub-judgement; returns verdict + what's still missing | the sub-agent promotion seam |
| `peek_repo(query)` | Codebase/repo recon — **its return populates `architect.provenance.peekRepo`** | Product / Architect |
| `search_knowledge(query)` | Product-scoped dedup — **its call populates `product.provenance.searchKnowledge`** | all |
| `ask_clarification(question)` | One question at a time, voice-first | gathering |
| `draft_brief(...)` / `sketch_approach(...)` | Build the mini-PRD + rough approach | BA / Architect |

> **F5 (callability lock was decorative — removed).** The draft's `ready_to_build`/`propose_implementation` LLM tools are **deleted**. The proposal is **not a tool the model calls** — it is a **server decision** in `processTurn` derived from the structured `evaluateReadyGate` verdict (§2.4). The model surfaces clarifications and lens findings; the server decides whether to emit the checkpoint. This is the real, non-LLM-overridable lock.

> **F13 (tool-availability mechanism).** Since there is no LLM tool that fires the build, the "callability-gating" concern is moot. `peek_repo`/`search_knowledge` are always available; the gate's *provenance requirement* (§2.2/§2.4) is enforced server-side regardless of whether the arbiter `Generate` layer supports mid-conversation tool toggling.

### 2.9 The readyToBuild verdict (structured; folds existing single-shot classify/route)

```jsonc
ReadyGateVerdict {
  "lenses": {
    "business":  { "satisfied": true,  "openQuestions": [],
                   "summary": "value: cut manual reconciliation; done = report matches ledger" },
    "product":   { "satisfied": true,  "openQuestions": [],
                   "summary": "scoped to Portfolios; not a dup; smaller version = single account",
                   "provenance": { "searchKnowledge": true } },                                  // F6 required
    "architect": { "satisfied": false, "openQuestions": ["which store holds ledger snapshots — ClickHouse vs Mongo"],
                   "summary": "approach pending data-store answer",
                   "provenance": { "peekRepo": "data/data-portfolio/PortfolioSnapshotRepository.java" } }
  },
  "blockingQuestion": "Which account(s) are in scope, and where do the ledger snapshots live?",
  "classification": { "type": "feature", "trivial": false },                  // reused classifyIntake
  "routing": { "targetRepo": "...", "pipelineName": "pdlc-feature", "knowledgeNamespace": "strategiz" }  // reused routeIntake — DISPLAYED, not authoritative (F8)
}
```

**The build emits a checkpoint iff ALL of (server-evaluated):** business satisfied; product satisfied **with `searchKnowledge` provenance**; architect satisfied **with `peekRepo` provenance**; `classification.type ∈ {bug,feature}`; `!trivial`. Any miss ⇒ `blockingQuestion` only; loop continues.

> **F8 (routing authority).** The gate's `routing` is a **preview**, never the build's source of truth. The build's Phase A `classify`/`route` activities **still run** (only the *interview* is skipped under `briefAlreadyConfirmed`). **Phase-D conformance test:** a brief whose gate-projection chose repo X, fed to `pdlcRunWorkflow` with the skip, lands on the *same* repo/playbook Phase A would compute — else the gate display is stale and the run uses Phase A's answer.

---

## 3. The tacticl ⇄ arbiter SESSION protocol

### 3.1 Decision: a dedicated `ConversationService`, **unary-first**, server-stream added only for voice

- **Distinct service**, not muxed onto `ArbiterPipelineService` (turns and pipeline-progress are different lifecycles).
- **v1 = `SubmitTurn` UNARY (reply-in-response).** No `StreamSession`, no async stub, no `StreamObserver`, no narration fan-in. The text composer renders the unary reply. This removes the codebase's *first streaming gRPC client* from the thesis-proving critical path (F5).
- **v2/voice adds `StreamSession` (server-stream).** Consumed via the **`BlockingStub` `Iterator<SessionEvent>`** on a dedicated drain thread — grpc-java consumes server-streams natively; **no async stub required** (F2-streaming correction). Why a new service over reusing the existing `GenerateStream` (verified at `arbiter.proto:6`): lifecycle separation (a dropped narration stream drops the *subscription*, not the *session*; reconnect = re-subscribe by `session_id`). Token-level narration *generation* still rides `GenerateStream` internally inside `runPersonaTurn` (§5).

### 3.2 New proto — `proto/conversation/v1/conversation.proto` (pkg `cidadel.ai.conversation.v1`)

```proto
service ConversationService {
  rpc OpenSession    (OpenSessionRequest)    returns (OpenSessionResponse);     // start/attach session (resume-aware)
  rpc SubmitTurn     (SubmitTurnRequest)     returns (SubmitTurnReply);         // v1 UNARY: reply-in-response
  rpc StreamSession  (StreamSessionRequest)  returns (stream SessionEvent);     // v2/voice: narration DOWN
  rpc SubmitDecision (SubmitDecisionRequest) returns (SubmitDecisionAck);       // HITL APPROVE/CHANGES/REJECT
  rpc CloseSession   (CloseSessionRequest)   returns (CloseSessionAck);
}

message OpenSessionRequest  { string product = 1; string user_id = 2; string channel = 3; string resume_session_id = 4; }
message OpenSessionResponse { string session_id = 1; bool resumed = 2; }

message SubmitTurnRequest   { string session_id = 1; string turn_id = 2; string text = 3; string correlation_id = 4; }
message SubmitTurnReply {                              // v1: the full reply inline (no stream)
  string turn_id = 1;
  bool   accepted = 2;
  string assistant_text = 3;                           // → tacticl-web TranscriptFrame(role:assistant)
  string hud_note = 4;                                 // → HudFrame "◈ consulting: …"
  string phase = 5;                                    // gathering|proposing|building|done
  Checkpoint checkpoint = 6;                            // present when a proposal was emitted this turn
  bool   escalate = 7;                                 // budget/round ceiling hit (F4/F10)
}
message Checkpoint { string checkpoint_id = 1; string title = 2; repeated string options = 3; }

message StreamSessionRequest{ string product = 1; string session_id = 2; }

message SubmitDecisionRequest { string session_id = 1; string checkpoint_id = 2;
                                string decision = 3; /* APPROVE|CHANGES|REJECT */ string feedback = 4; }

message SessionEvent {                          // v2/voice DOWN — maps 1:1 to protocol.ts DownControlMessage
  oneof event {
    StateEvent      state      = 1;             // idle|listening|thinking|speaking  → StateFrame
    TranscriptEvent transcript = 2;             // role, id, text, partial           → TranscriptFrame
    HudEvent        hud        = 3;             // role?, phase?, runId?, note        → HudFrame
    CheckpointEvent checkpoint = 4;             // checkpointId, title, options[]     → CheckpointFrame
    ErrorEvent      error      = 5;             //                                    → ErrorFrame
    NarrationDone   done       = 6;             // turn/narration boundary → edge emits state:idle
  }
}
```

> **F9 (build-red guard).** The proto is added to `client-ai-arbiter/src/main/proto/`. **In Phase 0 the `stream SessionEvent` RPC is present but the Java client only generates/uses the UNARY stub** — identical codegen risk profile to the existing `SubmitPipeline`. Phase-0 exit gate: `./gradlew :client:client-ai-arbiter:compileJava` green. (If the streaming RPC's codegen is at all risky on the toolchain, ship a unary-only proto in Phase 0 and add `StreamSession` in the voice phase.)

### 3.3 Mapping to tacticl-web `protocol.ts`

| DOWN (v1 unary fields / v2 `SessionEvent`) | `protocol.ts` `DownControlMessage` | Notes |
|---|---|---|
| `assistant_text` / `transcript` | `TranscriptFrame{role,id,text,partial}` | v1 from the unary reply; v2 streamed |
| `hud_note` / `hud` | `HudFrame{note}` | "◈ consulting: feasibility" — already rendered |
| `checkpoint` / `checkpoint` | `CheckpointFrame{checkpointId,title,options:(APPROVE\|CHANGES\|REJECT)[]}` | proposal + brokered build checkpoints |
| `phase`/`state` | `StateFrame{idle\|listening\|thinking\|speaking}` | arbiter drives thinking/speaking; edge drives listening/level |
| `error` | `ErrorFrame{message}` | |
| `done` | (drives edge StateFrame idle) | v2 turn boundary |

| `protocol.ts` UP frame | maps to | RPC |
|---|---|---|
| `TextFrame{type:'text',text}` | typed final turn | `SubmitTurn` (after the NEW `case "text"`) |
| STT-final (from binary PCM) | spoken final turn | `SubmitTurn` |
| `DecisionFrame{checkpointId,decision,feedback?}` | HITL decision | `SubmitDecision` |
| `BargeInFrame` | interrupt | edge TTS abort (v1/v2); `bargeIn` signal (arbiter abort = follow-up, F6) |
| `LevelFrame`, `AudioFormatFrame` | mic level / codec | **stay tacticl-core-local** (see §5.5 for `audio_format` DOWN) |

### 3.4 Java client additions (`client-ai-arbiter`)

- New `client/.../arbiter/conversation/ConversationSessionClient.java`: `openSession`, `submitTurn` (**unary**, returns `SubmitTurnReply`), `submitDecision`, `closeSession`; **`streamSession(sessionId, Consumer<SessionEvent>)` added in the voice phase** as a `BlockingStub` `Iterator` drained on a managed executor (reconnect re-subscribes by `session_id`).
- The existing `ArbiterGrpcClientImpl` blocking-unary stub (verified: `ArbiterPipelineServiceBlockingStub` only) is untouched.

### 3.5 `SignalPipelineDecision` — net-new, Phase-D-gated (F3)

Verified: `SignalPipelineDecision` is **declared in proto but has NO handler** (`grpc-pipeline-service.ts` implements only `SubmitPipeline`/`StreamPipelineProgress`/`CancelPipeline`/`GetPipelineResult`/`SpawnSubAgent`), and **`PdlcClient.signalWorkflow` does not exist**. So the draft's "two-birds, both from one addition" is **net-new work**, deferred to Phase D with explicit deps: (1) write `PdlcClient.signalWorkflow(workflowId, signalName, payload)`; (2) implement the `SignalPipelineDecision` handler; (3) a *live* Temporal workflow to signal (D-0 first). Until Temporal runs, this seam cannot be exercised end-to-end. The session brokers a *build's* mid-run checkpoint to its child via this path; the user always talks to one voice.

### 3.6 `protocol.ts` deltas — "zero UI change" is FALSE; state the two additions (F7/F8)

The draft's "zero UI change" invariant is dropped. `protocol.ts` gains:
1. **`resumeSessionId`** (optional) on the voice-token response / `StartFrame`, persisted client-side (sessionStorage), replayed on reconnect (§5.6).
2. **`audio_format`** DOWN negotiation — the edge MUST emit `audio_format:mp3` before the first audio chunk if ElevenLabs returns mp3, else the browser decodes mp3 as PCM and plays static (§5.5).

**Phase-0 gate (F6/R6):** a round-trip test — `SessionEvent`/`SubmitTurnReply` → `protocol.ts` decode — must pass, **including the mp3/PCM codec path**, before Phase 0 is "done."

---

## 4. Routing / gate / explicit-trigger bypass + HITL

- **"Enough to build" = the §2.9 SERVER verdict** — all three lenses satisfied *with provenance* + actionable classification. The model never fires; the server emits the checkpoint.
- **HITL before spend (non-negotiable):** gate-ready ⇒ `Checkpoint{options:[APPROVE,CHANGES,REJECT]}`. The cost-bearing build starts **only** on `APPROVE`. `CHANGES` ⇒ re-interview (bounded by `CONVERSATION_MAX_PROPOSAL_ROUNDS`, F10). `REJECT` ⇒ back to gathering.
- **Interview itself is budgeted (F4/F10):** `CONVERSATION_MAX_TURNS` + `CONVERSATION_BUDGET_USD` enforced in `processTurn` *before* each paid LLM call; a lens-stall (same `openQuestions` N turns running) and a proposal-round ceiling both escalate to "hand to a human / drop." The interview is **not** free; spend can occur entirely before the build.

### 4.1 Explicit-trigger bypass — the classifier flip is the FIRST commit (F-classify, F3, F7)

**Verified current behavior is the inverse of safe.** `BUILD_INTENT_PREFIXES` is a greedy prefix catch-all (`add `/`fix `/`make `/`create `/…). The FIRST committed change:

1. **Gut `BUILD_INTENT_PREFIXES`** to genuine explicit markers only: `["/pdlc", "send to pdlc"]` for text; **plus a required wake-phrase for voice** (`"strategiz, build:"`) — a bare spoken verb must NOT reach the pipeline (STT has no `/`; a mis-transcription is one filler word from spend). Regression test: `classify("add a dark-mode toggle") == CONVERSATION_TURN`.
2. **`CONVERSATION_TURN` becomes the true default** (already the fallback; now the overwhelming majority).
3. **Voice explicit-triggers still get a one-tap confirm checkpoint** even on the explicit path — text `/pdlc` is deliberate; a spoken trigger is not. The admin gate is authZ, not a cost control — do not conflate.

> **Correction to the draft's §4 (F7):** the draft claimed explicit triggers "skip classification." False — `handleExplicitTrigger` runs `SparkClassifierService.classify` and creates a `Spark` regardless. Explicit triggers run *more* classification, not less. The Phase-0 tacticl-core conversation-routing reduces to: (a) gut the prefix list (with wake-phrase + voice-confirm), (b) implement `ConversationTurnHandler`, (c) add `case "text"`. The "demote the keyword classifier" framing as new infra is wrong — the conversation-default *fallback* already exists; it's the greedy prefix list that must shrink.

```
"add a dark-mode toggle"            → CONVERSATION_TURN → Analyst interviews → gate → APPROVE → build (brief pre-confirmed)
"/pdlc add a dark-mode toggle"      → EXPLICIT_TRIGGER  → SubmitPipeline (one-shot; build runs own Phase-A intake)
"strategiz, build: dark-mode"       → EXPLICIT_TRIGGER (voice) → one-tap confirm → SubmitPipeline
"make it faster" (voice, no wake)   → CONVERSATION_TURN (NOT a build trigger anymore)
```

One `SubmitPipeline` serves both callers (Analyst `start_pipeline` with interview skipped; explicit bypass with full Phase A).

---

## 5. Voice wiring + Deepgram/ElevenLabs reconciliation

### 5.1 Binding decision (ratifies committed reality, supersedes handover §00/§02)

> **DECISION:** The voice WebSocket terminates in **tacticl-core** (`service-voice` `/v1/voice`). Deepgram STT + ElevenLabs TTS run **server-side in tacticl-core `business-voice`**, never in the browser, never in the arbiter. The arbiter has **no voice plane** (verified absent) and is reached over the §3 `ConversationService` channel behind tacticl-core.

Rationale: (a) third-party keys never reach the browser; (b) the arbiter has no auth plane (insecure gRPC) while tacticl-core has PASETO + the voice token service; (c) Vault keys live in the strategiz context, consumed by tacticl-core's `client-deepgram`/`client-elevenlabs`; (d) the arbiter stays I/O-agnostic — it speaks turns, not audio (serves Discord/Telegram/typed identically).

### 5.2 tacticl-core wiring (all additive)

1. **Implement `ConversationTurnHandler`** (verified zero implementors → every CONVERSATION_TURN is silently dropped today via `getIfAvailable()==null → log.warn`). New `ArbiterConversationRelay` in `business-conversation` (or new `business-voice-relay`). **Ownership model (F4):** the relay **opens the `StreamSession` subscription at `OpenSession` time** (one long-lived subscription per session, keyed `correlationId == sessionId`), so `handleTurn` is just `submitTurn`. For v1 (unary), `handleTurn` calls the unary `SubmitTurn` and renders the reply inline.
2. **Fix the orb lifecycle (F4).** Verified: `onFinalTranscript` transitions the orb to **IDLE** for `CONVERSATION_TURN` today (no reply awaited). Change so a conversation turn transitions to **THINKING** and the relay owns the orb (`THINKING`→`SPEAKING`→`idle`) until the reply (v1) / `NarrationDone` (v2). As written the committed idle-on-conversation line eats the reply.
3. **Close the typed-turn gap (verified).** `VoiceWebSocketHandler` switch has `start`/`stop`/`barge_in`/`decision` but **no `case "text"`** → typed turns hit `default → ignored`. Add `case "text" -> sessionService.submitTextTurn(voiceSession, asString(frame.get("text")))` → same path, skipping STT. **This makes the text composer work end-to-end with zero audio infra — the Phase-0 substrate.**
4. **Single-active-session claim (F9):** the relay maintains `userId → activeSessionId` and passes `resume_session_id` on `OpenSession` for reconnects.

### 5.3 Barge-in — edge-only for v1/v2; arbiter abort is a Router-gated follow-up (F6)

The UI sends `barge_in` on mic level > 0.22 during `speaking`.
- **Edge (ship now):** `VoiceSessionService.bargeIn` aborts the in-flight `ElevenLabsTtsBridge` playback, emits `StateFrame{listening}`.
- **Arbiter generation-abort (follow-up, NOT a v2 deliverable):** stopping the in-flight `runPersonaTurn`/`Generate` from *generating* requires threading an `AbortSignal` through `Router.generate` → engine → provider — verified absent. Scope as a Router-refactor stretch; do not claim two-level abort as v2.

### 5.4 STT/TTS lifecycle (unchanged components, new consumer)

`DeepgramSttBridge`/`ElevenLabsTtsBridge` already exist and are wired. Only their *consumer* changes: STT-final → relay; assistant text → ElevenLabs. State machine: `listening` (Deepgram partials → `transcript(partial)`) → STT-final → `thinking` → `speaking` (ElevenLabs) → `idle`.

### 5.5 Audio codec negotiation (F8) — required for voice

ElevenLabs WS commonly emits **mp3**. The edge MUST send `audio_format:mp3` (a `protocol.ts` DOWN frame, §3.6) before the first audio chunk, or the browser decodes mp3 as PCM and plays static. Include the mp3/PCM path in the Phase-0 round-trip decode test.

### 5.6 Voice session resume (F7/F10) — Phase-2 requirement, not an open question

Verified: `sessionId = UUID.randomUUID()` is minted fresh on **every** WS connect; the voice token is single-use (invalidated on close). So a reconnect today gets a brand-new session + token — resume is **unreachable** without a wire change. Promote to a **Phase-2 requirement**:
1. `VoiceTokenResponse`/`StartFrame` carries optional **`resumeSessionId`** (`protocol.ts` delta, §3.6); client persists in sessionStorage, replays on reconnect.
2. `VoiceSessionService.openSession` accepts + re-attaches it; the **session outlives the token** (decouple session identity from token lifecycle); the token service allows re-issue for a live session.
3. The relay's `OpenSession{resume_session_id}` consults the `userId → activeSessionId` claim (§2.6/§5.2.4).

Build this **before** the voice phase exposes it.

### 5.7 Latency — token-streamed narration is a Phase-2 EXIT GATE, not "open question" (F5)

Whole-turn narration (`runPersonaTurn` fully completes → ElevenLabs synth) is a 2–4s dead-air gap on voice. The arbiter already has `GenerateStream` (verified `arbiter.proto:6`) — token streaming is *available*. **Phase 2:** wire `runPersonaTurn` → `GenerateStream` → `SessionEvent.transcript(partial:true)` chunks → ElevenLabs incremental synth. **Exit gate: < 800ms to first audio.** Whole-turn is acceptable only for the Phase-0 text demo.

---

## 6. Durable vs v1 + smallest demoable slice

### 6.1 Substrate decision

Verified dormant: `PDLC_TEMPORAL_ENABLED` default off, `decideExecutor` defaults `'legacy'`, **`activities = {}` at `server.ts:530`** (worker has never started; the warn-and-skip guard confirms it), and **no `patched()` call exists yet** in `pdlc-run-workflow.ts` (the v1 body is unconditional). Standing up a brand-new durable session on a never-run worker is two unproven bets at once.

**Resolution:** v1 = `InMemorySessionManager` behind the real **unary** `ConversationService`, running the exact `runPersonaTurn`/`evaluateReadyGate`/`buildBrief` functions that become Temporal activities in v2. Migration = `decideConversationExecutor('inmemory'|'durable')` swap (mirrors the existing `decideExecutor`), zero protocol/UI movement.

**v1 honest limitation (stated loudly, R3):** v1 `resumeSession` works only within a single process lifetime; a **process restart loses in-flight sessions**. The protocol *looks* resumable; v1 is not durable across deploys. **Resume-on-reconnect is a v2 (durable) guarantee, not a v1 promise.** Build the relay/UI knowing this boundary.

### 6.2 De-risk the never-run worker as its own milestone — Phase D-0 (HARD GATE, F4/F10)

Before any durable conversation work, **Phase D-0** is a hard blocker: wire the `activities = {}` stub, bring the worker up behind `PDLC_TEMPORAL_ENABLED`, run **one trivial throwaway workflow** to prove the worker executes at all — decoupled from the conversation. Validate the build-side `briefAlreadyConfirmed` skip's **first-ever `patched('pdlc-brief-preconfirmed-v1')`** guard actually executes and replays correctly. **Critical (F10):** ensure conversation activities do NOT get registered on the codegen task queue and perturb the byte-identical codegen boot the `server.ts` comment protects. The conversation workflow (cheap activities only — LLM + registry reads, NO dockerode containers) is then the *safest possible* first real Temporal workload.

### 6.3 Smallest first demoable slice (Phase 0 — text only, no voice, no streaming, no real spend)

1. **tacticl-core (first commit):** gut `BUILD_INTENT_PREFIXES` (+ regression test) — **nothing reaches the relay until this lands** (F-classify).
2. **Arbiter:** `conversation/*` (in-mem); `ConversationService` proto + handler with **UNARY `SubmitTurn`**; `runPersonaTurn`/`evaluateReadyGate`/`buildBrief`; `InlineLensConsultant`; author `{product}/agents/analyst` registry doc (port from legacy `ConversationService.java` + `pm.json`).
3. **tacticl-core:** `ArbiterConversationRelay implements ConversationTurnHandler` (unary path, owns orb THINKING→reply); `case "text"` fix; `userId → activeSessionId` claim; `ConversationSessionClient` (**unary only**).
4. **tacticl-web:** nothing — type in the existing composer.
5. **Gate fires (server) → `Checkpoint` → user APPROVEs → narrate the brief back** ("I'd build: …"), or wire `Shell.submitPipeline` behind `CONVERSATION_BUILD_ENABLED`.

This proves the inversion — converse → three-lens interview with the "◈ consulting" HUD → server gate → "ready to build?" → approve — end-to-end through the **real UI** with **one new unary RPC, no streaming, no voice key, no Temporal worker**, before a dollar of pipeline spend.

---

## 7. User provisioning (exact Vault key names — verified)

Vault (strategiz context — `http://10.0.1.10:8200` internal / `http://178.156.141.55:8200` public; KV v2; key literally `api-key`):

```bash
vault kv put secret/strategiz/deepgram   api-key="<DEEPGRAM_KEY>"     # client-deepgram   DeepgramVaultConfig  (VAULT_PATH verified)
vault kv put secret/strategiz/elevenlabs api-key="<ELEVENLABS_KEY>"   # client-elevenlabs ElevenLabsVaultConfig (VAULT_PATH verified; header xi-api-key)
```

tacticl-core flags / properties:
```
tacticl.voice.enabled=true
tacticl.deepgram.enabled=true
tacticl.elevenlabs.enabled=true
tacticl.voice.conversation.enabled=true          # route turns through ArbiterConversationRelay
VoiceProperties.voiceId=<ELEVENLABS_VOICE_ID>     # the Jarvis voice
```

Arbiter flags (all default-off until wired):
```
CONVERSATION_ENABLED=true                          # ConversationService + InMemorySessionManager (Phase 0)
CONVERSATION_BUILD_ENABLED=true                    # allow gate-APPROVE → real Shell.submitPipeline (Phase 1)
CONVERSATION_DURABLE=true                          # decideConversationExecutor → Temporal (Phase D)
CONVERSATION_CANCELS_BUILD=false                   # default: ABANDON child on session close (Phase D)
PDLC_TEMPORAL_ENABLED=true                         # bring up the dormant worker; WIRE server.ts:530 activities={}
PDLC_TEMPORAL_PIPELINES=pdlc-feature,pdlc-fix      # enable Temporal build executor
CONVERSATION_IDLE_TTL=900s
CONVERSATION_MAX_TURNS=40                           # F4 — ENFORCED in processTurn
CONVERSATION_BUDGET_USD=2.50                        # F4 — per-session interview spend ceiling, ENFORCED
CONVERSATION_MAX_PROPOSAL_ROUNDS=3                  # F10 — CHANGES/REJECT loop ceiling
```

Registry seeding (per product — the only product-specific artifacts):
```
{product}/agents/analyst         # persona doc (NET-NEW; ported from legacy ConversationService.java + pm.json)
{product}/rubrics/ready-gate     # three-lens rubric + product surfaces + dedup hints
{product}/analyst/knowledge      # knowledge namespace (knowledge-loader.loadForAgent)
```
(Filesystem fallback under `{product}/agents/` for dev; Mongo `RegistryDoc` upserts for prod.)

**Phase 0 needs NONE of the voice keys** — text-only demo runs on `CONVERSATION_ENABLED` + `tacticl.voice.conversation.enabled`. No new Anthropic provisioning (the Router reads `secret/strategiz/anthropic` fresh per call, billed engine).

---

## 8. Phased build plan (additive, flag-gated, neither build red)

| Phase | Arbiter (cidadel-ai-arbiter) | tacticl-core | tacticl-web | Flags | Demo / exit gate |
|---|---|---|---|---|---|
| **0a — Classifier flip (FIRST)** | — | **Gut `BUILD_INTENT_PREFIXES`** → markers only + voice wake-phrase; regression test `classify("add …")==CONVERSATION_TURN` | none | — | "add a dark-mode toggle" no longer fires a build. Nothing else works until this lands. |
| **0 — Text converse→route** | `conversation/*` (in-mem); `ConversationService` proto + **UNARY `SubmitTurn`** + handler; `ANALYST_TOOLS`; `InlineLensConsultant`; author `{product}/agents/analyst` (port from legacy `ConversationService.java` + `pm.json`); **server-decided gate**; APPROVE→narrate-brief (or `Shell.submitPipeline` behind flag) | `ArbiterConversationRelay implements ConversationTurnHandler` (unary, owns orb); `case "text"` fix; `userId→activeSessionId` claim; `ConversationSessionClient` (**unary only**) | none | `CONVERSATION_ENABLED`, `tacticl.voice.conversation.enabled` | Type in composer → 3-lens interview + "◈ consulting" HUD → server gate → APPROVE checkpoint. Budget ceiling enforced. Round-trip decode test (incl. codec path) green; `compileJava` green. |
| **1 — Real spend on APPROVE** | gate APPROVE → `Shell.submitPipeline(brief)` (**detached legacy tracker — no child, ABANDON inert**); `pumpChildProgress` tails `StreamPipelineProgress` → `buildEvents[]` + narration | — | none | `CONVERSATION_BUILD_ENABLED` | APPROVE builds; progress narrates back. Spawned build is a *detached* run (stated, not hidden). |
| **2 — Voice** | add `StreamSession`; `runPersonaTurn`→`GenerateStream` token narration | enable Deepgram/ElevenLabs; STT-final→relay; narration→ElevenLabs→WS audio; `audio_format:mp3`; edge barge-in; voice session resume (`resumeSessionId` + token-outlives-session); `listening/thinking/speaking` | **`resumeSessionId` + `audio_format`** protocol deltas | `tacticl.{voice,deepgram,elevenlabs}.enabled` + keys (§7) | Speak → Analyst interviews aloud, one voice, lens HUD → "ready to build?" → "yes" → narrated build. **< 800ms to first audio.** Reconnect resumes the live session. |
| **D-0 — Prove Temporal worker (HARD GATE)** | wire `activities={}` (`server.ts:530`); worker up; one throwaway workflow; validate first-ever `patched('pdlc-brief-preconfirmed-v1')` executes/replays; ensure no codegen-queue perturbation | — | — | `PDLC_TEMPORAL_ENABLED` | One Temporal workflow has run end-to-end. Blocks all of Phase D. |
| **D — Durable session + child build** | `conversation/temporal/*`: register `sessionWorkflow` + signals/queries/updates; `makeConversationClient`; **net-new `PdlcClient.signalWorkflow`** + **net-new `SignalPipelineDecision` handler**; `start_pipeline`→`startChild(pdlcRunWorkflow,{briefAlreadyConfirmed}, ABANDON, wfId=pdlc-${intakeId})`; idempotent transition (F2); `briefAlreadyConfirmed` interview-skip (classify/route STILL run); `detached_monitor` on idle-with-live-child + build-side checkpoint-timeout; conformance test (gate routing == Phase A routing); `decideConversationExecutor('durable')` | none (wire unchanged) | none | `CONVERSATION_DURABLE`, `PDLC_TEMPORAL_PIPELINES`, `CONVERSATION_CANCELS_BUILD` | Kill arbiter mid-interview → reconnect → session resumes (full history + live build checkpoint). Build is a child with `ABANDON`. Double-APPROVE ⇒ one build. |
| **MP — Multi-product** | — (data only) | — | none | per-product | Seed `strategiz/agents/analyst` + rubric + knowledge-ns → strategiz Jarvis with **zero code change**. |
| **SA — Lens → sub-agent** (optional) | swap `InlineLensConsultant`→`SubAgentLensConsultant` (needs `SpawnSubAgent` un-stubbed — currently code:12); add `PersonaRouter` route | none | none | `ANALYST_SUBAGENTS` | A lens becomes a real BA/PA/Architect sub-agent behind one voice; HUD already shows "◈ consulting"; **zero conversational-surface change**. |

**Build-red guardrails:** every arbiter change is a new proto/package behind `CONVERSATION_*`; dormant Temporal scaffolding stays default-off until D-0. Every tacticl-core change is a new relay class + switch case behind `tacticl.voice.conversation.enabled` (`ConversationTurnHandler` null→present is additive). **Proto codegen is NOT flag-gated** — `compileJava` green is a Phase-0 exit gate (F9); ship unary-only proto in Phase 0 if the streaming RPC's codegen is risky.

**Invariant across phases:** the `ConverseTurn`/`SessionEvent`/`SubmitTurnReply` wire contract never changes. The UI changes **exactly twice** — `resumeSessionId` + `audio_format` in Phase 2 (not "zero"). Substrate moves underneath: in-memory→Temporal (D), unary→+stream (2), inline-lens→sub-agent (SA) — all behind interfaces/flags.

---

## 9. Risks

| # | Risk | Sev | Mitigation |
|---|---|---|---|
| R1 | **Temporal has never run** (`activities={}`, all flags off); durable session AND the never-introduced `patched()` guard both inherit this. | High | Phase D-0 HARD GATE: throwaway workflow first; validate `patched()` executes/replays; conversation workflow (cheap activities, no containers) is the safest first real workload. |
| R2 | **start_pipeline double-spawn** from at-least-once signal delivery / double-APPROVE / brief-retry. | **Crit** | F1: `intakeId = checkpointId` minted at checkpoint-emit (not APPROVE). F2: synchronous `phase='submitting'` before await + `WorkflowExecutionAlreadyStarted`-as-success. Required dedup test. |
| R3 | **Child spawn close semantics** — idle/closed conversation orphaning or killing a build. | High | F7: `ABANDON` (Phase D); `detached_monitor` on idle-with-live-child + build-side checkpoint-timeout → auto-reject. NOT applicable in Phases 0–2 (detached legacy run; stated). |
| R4 | **v1 loses sessions on restart** while protocol looks resumable. | Med | R3-style boundary stated: resume is a v2 guarantee; v1 reconnect re-hydrates only within a process lifetime. |
| R5 | **Narration egress** from a workflow that can't push to gRPC directly. | Med | Reject node-pinned task queue. v1 = unary (no egress problem); v2 = `BlockingStub` `Iterator` drain + reconstructable from `turns[]`+`buildEvents[]` via `snapshot` on reconnect. Redis pub/sub later behind the same interface if multi-replica (R12). |
| R6 | **Legacy-tracker vs child-workflow narration mismatch.** | Med | `pumpChildProgress` is mode-aware: tails `StreamPipelineProgress` (legacy/v1) / child signals→`pipelineEvent`→`buildEvents[]` (v2). |
| R7 | **"UI unchanged" is false** — a 1:1 map off by one enum / a missed codec frame silently drops frames. | Med | F8: drop "zero UI change"; `resumeSessionId` + `audio_format` are explicit deltas. Round-trip decode test (incl. mp3/PCM) is a Phase-0 gate. |
| R8 | **Premature/runaway gate fire** burning pipeline spend on a misread. | High | F5: gate is a SERVER verdict, not an LLM tool. F6: lens `satisfied` server-set + provenance-required. HITL APPROVE mandatory. Three real locks. |
| R9 | **Interview itself burns unbounded LLM spend** before any build. | High | F4/F10: `CONVERSATION_MAX_TURNS` + `CONVERSATION_BUDGET_USD` + `MAX_PROPOSAL_ROUNDS` enforced in `processTurn` before each paid call; lens-stall + round ceilings escalate. |
| R10 | **Explicit-trigger bypass on voice** — a spoken lead verb / STT mis-transcription one word from unconfirmed spend. | High | F-classify/F3: gut `BUILD_INTENT_PREFIXES` (FIRST commit); voice requires a wake-phrase + a one-tap confirm even on the explicit path. Admin gate is authZ, not cost control. |
| R11 | **`briefAlreadyConfirmed` skip wrong-repos the build** (skips routing the gate's preview didn't fully populate). | Med | F8: skip the *interview* only; classify/route STILL run and own repo resolution; gate `routing` is a preview. Phase-D conformance test: gate routing == Phase A routing. |
| R12 | **Multi-replica arbiter** breaks the in-process narration channel. | Med | Track the scale event (Open Q); swap R5's in-process channel for Redis pub/sub *before* it, behind the same interface. |
| R13 | **Two persona sources drift** (legacy `ConversationService.java` markers vs the new registry `analyst`). | Low | One canonical source: author registry `analyst` by porting the legacy prompt + marker-gate semantics; delete the legacy Java class **at parity (Phase D-or-later), not Phase 0** — until then it's the read-only port source (note its `TelegramConversationAdapter` + `data.cloudorchestrator` deps as deletion blockers). |
| R14 | **Cloud-orchestrator migration collision** — building a third session model beside an in-flight `data-cloudorchestrator` / tacticl `ConversationSession`. | Med | F6-collision: Phase-0 precondition — confirm whether the cloud-orchestrator `SessionWorkflow`/`PersonaRouter` migration landed in the arbiter. If landed, EXTEND it; if abandoned, delete the half-migrated entities same release (remove-don't-deprecate). Do NOT add a third model. |
| R15 | **Arbiter gRPC is insecure / no auth**; the session channel carries user turns. | Med | tacticl-core (PASETO + voice token) is the auth boundary; the session channel is reached only from tacticl-core over Wireguard, never browser-direct. mTLS on the Wireguard hop is a follow-up. |

## 10. Open questions

1. **Narration partial-token vs whole-turn:** whole-turn for Phase-0 text; token-streamed is a Phase-2 *exit gate* (< 800ms first audio), not optional (F5).
2. **Multi-replica timing (R12):** when does the arbiter go multi-replica? That's the trigger to swap the in-process narration channel for Redis pub/sub — track it so the swap lands before the scale event.
3. **Per-product gate rubric tunability:** start prompt-driven (`{product}/rubrics/ready-gate`); the `confidence` float is dropped (F6) — add weighted thresholds only if false-fires appear.
4. **Build checkpoints brokered-through-Analyst vs raw `CheckpointFrame`:** spec says brokered (one voice); confirm latency acceptable, else allow raw surfacing for build-only checkpoints.
5. **`PersonaRouter` non-analyst routes:** register only `analyst` initially; leave any future `market-researcher`/etc. routes dormant (and note the router itself is net-new, not migrated — Q5 corrected).
6. **Cloud-orchestrator reconciliation (R14):** resolve before any code — extend or delete the half-migrated session model; do not add a third.

---

### Canonical calls (do not relitigate)

1. **Communicator = NET-NEW registry doc `{product}/agents/analyst`, ported from the legacy `tacticl-core ConversationService.java` prompt + `pm.json`** — NOT a "migration" of a nonexistent `product-manager.md`/`PersonaRouter`. Reusable arbiter assets are the single-shot `classify`/`route` activities + `pdlcRunWorkflow` (as child) only. Three lenses are skills; promotable to sub-agents via `LensConsultant` + a `PersonaRouter` route with zero conversational-surface change.
2. **WS terminates in tacticl-core; STT/TTS server-side; arbiter text-in/text-out** — ratifies committed `protocol.ts`, supersedes the handover's "browser→arbiter-direct."
3. **Distinct `ConversationService`; v1 = UNARY `SubmitTurn` (reply-in-response); `StreamSession` server-stream added only for voice (Phase 2), consumed via `BlockingStub` Iterator** — no async stub, no streaming on the thesis-proving critical path.
4. **Gate = SERVER-decided structured verdict (3 lenses, provenance-required, + folded classify/route), NOT an LLM tool.** Locks: server verdict + provenance + mandatory HITL APPROVE. Interview is budgeted (turns/USD/rounds). `/pdlc` (text) and a voice wake-phrase (with one-tap confirm) bypass to the existing one-shot `SubmitPipeline`.
5. **v1 in-memory behind the real proto; durable Temporal via `decideConversationExecutor` swap after Phase D-0 proves the never-run worker.** Durability designed-in, deferred only in implementation. v1 is NOT resume-across-restart — stated, not hidden.
6. **`start_pipeline` build is a DETACHED legacy-tracker run in Phases 0–2; the parent/child + `ParentClosePolicy.ABANDON` invariant is a Phase-D property only** (because `SubmitPipeline` bypasses Temporal today). Idempotency: `intakeId = checkpointId` + synchronous-`submitting` + `AlreadyStarted`-as-success.
7. **Multi-product = three registry docs per product; lens-promotion = one route.** Zero code, zero surface change.

**The single seam that turns the GAP into the GOAL:** gut `BUILD_INTENT_PREFIXES` (FIRST commit), then implement `ArbiterConversationRelay` as the (currently absent) `ConversationTurnHandler`, point all non-explicit turns at it, and have it drive the arbiter `ConversationService` session that runs the net-new registry-backed `analyst` persona and gates the build via a SERVER verdict → (v1) detached `Shell.submitPipeline` / (v2) `startChild(pdlcRunWorkflow)`.

**Smallest thing that proves the whole thesis:** Phase 0 — type a fuzzy ask into the composer that already exists, watch the Analyst interview you across the three lenses with the "◈ consulting" HUD, hit the SERVER gate, get a "here's what I'd build, ready?" checkpoint, approve — the entire conversation living behind one new **unary** `ConversationService.SubmitTurn`, with the interview budget-capped, ready to flip to a durable Temporal workflow (after D-0) that survives an arbiter restart mid-interview without moving the wire contract or (beyond the two named deltas) the UI.
