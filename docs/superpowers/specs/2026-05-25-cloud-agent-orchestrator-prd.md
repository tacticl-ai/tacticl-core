---
name: Cloud Agent Orchestrator PRD
description: Unified persona-driven agent orchestrator with live voice (voice sphere) and Temporal-backed durable workflows. Supersedes Tacticl PDLC v2.
type: product-requirements
status: draft
date: 2026-05-25
author: Gabriel Jimenez
related-docs:
  - 2026-05-25-cloud-agent-orchestrator-sad.md
  - 2026-04-17-conversational-chat-design.md
  - 2026-05-05-agent-intake-consolidation-design.md
  - 2026-05-15-telegram-conversational-spark-to-pdlc-plan
supersedes:
  - 2026-04-11-tacticl-pdlc-v2-prd.md
---

# Cloud Agent Orchestrator — Product Requirements Document

**Date:** 2026-05-25
**Status:** Draft
**Author:** Gabriel Jimenez
**Supersedes:** [Tacticl PDLC v2 PRD (2026-04-11)](2026-04-11-tacticl-pdlc-v2-prd.md)

---

## 1. Executive Summary

Tacticl today has two disconnected brains. `ConversationService` drives the multi-turn chat (gather → propose → confirm) before any work starts. `PdlcV2Service` then takes over and drives the 12-role PDLC pipeline through Arbiter. The conversation is text-only on web and Telegram-only with voice (transcribed Whisper push-to-talk on mobile). Personas live as static markdown files attached one-to-one to PDLC roles. There is no live voice interface, no streaming TTS, and no concept of "the orchestrator picks the right voice for this turn."

The **Cloud Agent Orchestrator** collapses both brains into one. A single durable workflow runs per user session and decides, every turn, *which persona should respond and what they should do* — whether that's the **Product Manager** asking a clarifying question, the **Market Researcher** doing competitor analysis, or kicking off the full 12-role PDLC playbook through Arbiter. On the user's screen, this is rendered as a **pulsating voice sphere** that listens via Deepgram streaming STT, thinks visibly, and speaks via ElevenLabs streaming TTS — with a one-click toggle to fall back to text chat at any moment.

The orchestrator itself runs on **Temporal**. Every session is a long-lived workflow with durable state, signals for user input (voice transcript, text, checkpoint decisions, mode toggle), and child workflows for pipeline execution. PDLC execution stays delegated to Arbiter — only the *conversation + persona routing* layer changes.

This is the next evolution of the PDLC vision laid out in the 2026-04-11 v2 docs: the pipeline mechanics are sound, but the front of the funnel was too narrow and the orchestrator was too thin. This is the orchestrator the PDLC always deserved.

## 1.5 Scope — What the Cloud Agent Orchestrator Owns (and Doesn't)

The Cloud Agent Orchestrator is a **conversation brain**. Its job is to take a user turn (voice or text), pick the right persona, run that persona's LLM call, speak the response (or render it as text), and decide what comes next — including handing off to other systems for actual execution work. It does **not** execute pipelines, run cloud agent skills, drive devices, or do any of the heavy lifting itself.

| Concern | Owner | Notes |
|---|---|---|
| Conversation turn loop (intake, clarification, proposal, narration) | **Cloud Agent Orchestrator** (`CloudAgentSessionWorkflow`) | The only owner |
| Persona selection per turn | **Cloud Agent Orchestrator** (`ROUTER` persona + hard rules) | Single source of truth |
| Voice plane (STT, TTS, sphere events) | **Cloud Agent Orchestrator** | But STT/TTS providers (Deepgram/ElevenLabs) are dumb downstream |
| Modality (voice / text / mute) state | **Cloud Agent Orchestrator** | Lives in workflow state + WS handler |
| Conversation transcript persistence | **Cloud Agent Orchestrator** | Mongo `conversation_sessions.turns` |
| **PDLC pipeline execution** (12-role code/devops pipelines) | **Arbiter** (Hetzner, unchanged) | Invoked via `PipelineWorkflow` child workflow; orchestrator handles only handoff + checkpoint mediation |
| **Cloud agent skill execution** (social posting, research, video gen, browser automation) | **Existing `CloudOrchestratorService`** (in `business-agent`) | Wrapped as a Temporal activity called by the workflow when a non-PDLC spark is dispatched |
| **Device execution** (run a task on a paired desktop/mobile) | **Device daemons** via existing WebSocket spark dispatch | Orchestrator selects "device" as the target and signals device dispatch; daemon does the work |
| **LLM API calls, model routing, cost tracking per provider** | **Arbiter** (for in-pipeline) and **shared LLM clients** (for in-orchestrator) | Orchestrator just calls activities; routing is downstream |
| Spark lifecycle (create, classify, complete) | **`SparkService`** (existing) | Workflow invokes this as an activity when a turn produces a spark |

### 1.5.1 What the orchestrator hands off to downstream systems

When a persona decides work needs to happen, it emits one of these tool calls. The workflow translates them into downstream actions and continues driving the conversation while the work runs:

- `start_pipeline(playbookId, sparkInput)` → spawns `PipelineWorkflow` (child) → Arbiter → ephemeral containers
- `start_cloud_skill(skill, args)` → calls `CloudOrchestratorService` activity → existing skill loop
- `dispatch_to_device(deviceId, taskSpec)` → signals existing device dispatch path → device daemon
- `answer_in_conversation(text)` → no downstream; the persona's response IS the action

The orchestrator stays in the conversation throughout. While a pipeline runs, the user can ask the Product Manager for status (it uses `summarize_pipeline_progress` to read artifacts), pipeline events can be proactively narrated (same persona, same skill), and pipeline-raised checkpoints are mediated by the Product Manager's `mediate_pipeline_checkpoint` skill — all without leaving the same workflow.

### 1.5.2 Forward-look — Device Agent Orchestrator (future sibling)

The "Cloud" prefix is deliberate: a **Device Agent Orchestrator** is anticipated as a sibling system that runs *on* the user's device (laptop / phone) rather than in Cloud Run. It mirrors the same conversation model (personas, turn loop, voice plane) but:

- Lives in the device daemon (or mobile app), not Cloud Run
- Uses local STT/TTS where available (Apple on-device speech, Whisper.cpp) with cloud fallback
- Can run conversation entirely offline for simple intents
- Handoffs to *cloud* execution still travel through the existing device→cloud WebSocket
- For privacy-sensitive sparks ("don't send my screen to the cloud"), the device orchestrator can drive a local-only pipeline using Claude Code CLI on the device (already designed — see `2026-03-09-claude-code-device-engine-design.md`)

This PRD scopes only the **Cloud** Agent Orchestrator. The Device Agent Orchestrator is a future doc; it should be designed to share the **persona registry** (sync down from cloud) and the **conversation transcript schema** (sync up to cloud), but otherwise be independently deployable.

**Implication for this build:** keep the workflow signal/query interface clean and well-named so a future device implementation can adopt a compatible contract. Don't bake Cloud-Run-specific assumptions into `CloudAgentSessionWorkflow` (e.g., assuming Anthropic is always reachable, assuming MongoDB is always the persistence layer).

## 2. Problem Statement

### 2.1 What's Broken Today

**Two brains, no shared model.** `ConversationService` and `PdlcV2Service` don't share a persona registry, don't share state, and hand off one-way. Once the PDLC starts, the conversation channel can't easily re-engage the user mid-pipeline — it can only emit events to a passive log. The user can't say "hold on, change the design" without manually crafting a checkpoint payload.

**Personas are static and role-bound.** The 12 markdown files in `business-pipeline/src/main/resources/role-identities/` are loaded eagerly by `RoleIdentityLoader` and bound 1:1 to `PdlcRole`. There's no `PersonaRegistry`, no per-step override surface, no concept of a conversational persona like Product Manager or Market Researcher, and no way to add a persona without editing Java enums.

**Playbooks are hardcoded.** `PlaybookSpecResolver` contains 8 playbooks as Java code. Adding `RESEARCH_ONLY` or `DESIGN_REVIEW_ONLY` requires a code change, build, and deploy. The PDLC v2 PRD called for data-driven playbooks as a goal (`G3`) but the implementation kept them in Java.

**Voice is half-built and wrong-shaped for live conversation.** Whisper is great for "transcribe this 30-second push-to-talk recording" but terrible for "stream a continuous conversation with sub-second response latency." There is no TTS at all — the assistant has no voice. The web has no microphone capture, no audio playback, and no sphere/avatar visualization. The conversational voice experience the product is meant to deliver simply does not exist yet.

**Orchestration is fragile.** `PdlcV2Service` orchestrates pipeline submission and callback handling using Spring `@Async` on a thread pool. If the JVM restarts mid-pipeline, recovery is reconstruction-by-replay of MongoDB events. There is no first-class workflow state, no built-in retry policy per activity, no signal/query separation, and no durable timer for checkpoint timeouts beyond a Spring `@Scheduled` watchdog. The 2026-05-20 architecture critiques recommended deferring Temporal until ~10 concurrent sparks; we are choosing to do it now to avoid building the turn loop twice.

**Dead code in the worktree.** `.claude/worktrees/agent-ad671ea0/` contains 22 files / ~3,500 LoC of an abandoned Spring-`@Async`-based `PdlcPipelineOrchestrator` that was never merged and won't fit the Temporal model. It's actively misleading and needs to go.

### 2.2 Why a Single Orchestrator (Not Two Coordinated Services)

The split between "conversation service" and "pipeline service" felt natural when one was a chat box and the other was an execution engine. With a voice sphere as the primary interface, the split becomes a liability:

- **The user doesn't think in those layers.** They talk to the assistant. The assistant decides whether this turn is a clarifying question, a design proposal, or "I'm going to spend the next 40 minutes coding this — confirm?" That decision belongs in one brain.
- **One coherent voice the user gets to know.** The Product Manager handles intake, scope clarification, proposal, narration, and checkpoint mediation through skills it owns — not by switching to a "clarifier" persona then a "proposer" persona then a "narrator." Skills as tools, personas as job roles. Mirrors how a real team works.
- **Voice latency demands a single state machine.** The user speaks; we have ~600ms to start TTS before it feels laggy. We cannot afford a cross-service hop, a Mongo round-trip, and a Spring bean dispatch between "transcript final" and "first TTS chunk." One workflow holding both transcript intake and response dispatch is much faster to reason about and to optimize.

## 3. Vision & Goals

### 3.1 Vision

A user opens Tacticl. The sphere is idle — a slow ambient pulse. They start talking. The sphere flares to listening (faster, brighter pulse following voice amplitude). Mid-sentence, partial transcripts stream onto the screen. When they pause, the sphere shifts to thinking (a different motion — slower, denser), the orchestrator picks the right persona, and a moment later starts speaking — sphere now in speaking mode, audio playing, captions following. The user can interrupt by talking; the sphere returns to listening, the assistant stops mid-sentence. They can hit a button: sphere collapses to a text chat, same session, same state — they keep going by typing.

Behind the scenes, the same orchestrator that drove the conversation also kicked off the PDLC pipeline when the user confirmed. Pipeline events surface back through the orchestrator — the sphere can speak progress ("Architect just finished, want to hear the design before Implementer starts?"), summon clarifying personas when the pipeline needs user input, and resume the pipeline after the user responds. The user is never out of the loop, and never has to context-switch to a different UI.

### 3.2 Primary Goals

| # | Goal | How we measure it |
|---|------|-------------------|
| G1 | **One unified conversation orchestrator** owning persona selection for pre-pipeline chat AND in-pipeline checkpoint mediation; pipeline execution itself remains in Arbiter | `ConversationService` collapses into the workflow; `PdlcV2Service` survives only as the body of a `submitToArbiter` activity |
| G2 | **Live voice interface** with sub-second perceived latency | p50 time from user-pause to first-TTS-chunk < 1200ms; p95 < 2200ms |
| G3 | **Pulsating voice sphere UI** on web with toggle to text chat | Sphere ships on tacticl-web; mode toggle (voice / text) usable without page reload |
| G4 | **Always-on VAD listening (user-disableable)** with auto-mute during TTS | No feedback echo; mic auto-mutes while assistant speaks; user can switch to push-to-talk or off |
| G5 | **Persona registry** decoupled from PDLC roles | Personas defined in data (Mongo/YAML), not Java enums; new personas addable without deploy |
| G6 | **Data-driven playbooks** | The 8 hardcoded playbooks move to Mongo; new playbooks addable from Console without deploy |
| G7 | **Temporal-backed durable workflows** | Every session is a Temporal workflow; pipeline execution is a child workflow; signals replace polling |
| G8 | **Graceful degradation** | If Deepgram or ElevenLabs is down, the sphere falls back to text-only without dropping the session |

### 3.3 Secondary Goals

| # | Goal |
|---|------|
| G9 | Mobile sphere parity (post-v1) — same WebSocket spine, native audio capture/playback |
| G10 | Telegram benefits from the unified orchestrator without UI changes (text-only path still works) |
| G11 | Cost ceiling per session (LLM + STT + TTS combined), enforced at the workflow level |
| G12 | Full conversation transcript persisted (text + audio refs) — replayable |

### 3.4 Non-Goals

- **Wake-word ("Hey Tacticl") detection** — out of scope for v1; user opens the app and clicks the sphere or starts talking with mic active.
- **Multi-party voice** — single-user sessions only. No conference / multi-user sphere.
- **Speaker diarization** — assumes one user voice per session.
- **Voice cloning of the user** — ElevenLabs uses a stock professional voice. User-trained voices are out of scope.
- **Replacing Arbiter** — pipeline execution stays in Arbiter. Only the orchestration/conversation layer changes.
- **Replacing existing cloud agent skill execution** — `CloudOrchestratorService` (social/research/video/browser skills) stays; the workflow wraps it as an activity.
- **Removing Whisper entirely** — Whisper stays for file-upload transcription (Telegram voice messages, future audio attachments). It just leaves the *live conversation* path.
- **Temporal SaaS** — self-hosted Postgres-backed cluster on Hetzner, alongside Arbiter.
- **Device Agent Orchestrator** — this PRD scopes only the *Cloud* Agent Orchestrator. The device sibling is anticipated (§1.5.2) but designed in a separate doc.

## 4. Personas

### 4.1 Target User

Same as PDLC v2: a solo founder or small-team operator using Tacticl as their AI factory. Voice is the preferred input when they're walking, driving, cooking, or otherwise hands-busy. Text is the preferred input at a desk doing precise work.

### 4.2 Personas and Skills

**Personas are job roles. Skills are the tools any persona can use.** The orchestrator picks one persona per turn; that persona picks one or more skills within the turn (standard Anthropic tool-use pattern).

Two registries, two families.

#### 4.2.1 Conversational personas — `CONVERSATIONAL` family

| Persona | When invoked | Skills |
|---|---|---|
| **Product Manager** | Default chat partner. Strategy, scoping, "what should we build and why." Handles intake, proposals, pipeline narration, and checkpoint mediation. | `ask_clarification`, `propose_implementation`, `start_pipeline`, `start_cloud_skill`, `dispatch_to_device`, `summarize_pipeline_progress`, `mediate_pipeline_checkpoint`, `answer_in_conversation` |
| **Market Researcher** | Summoned for competitor / demand / market validation ("is anyone else doing this?", "validate this idea before we build", "what's the TAM?"). | `web_search`, `read_page`, `analyze_competitors`, `estimate_market_size`, `synthesize_findings`, `propose_validation_experiment`, `answer_in_conversation` |

#### 4.2.2 PDLC personas — `PDLC` family

Twelve personas execute inside Arbiter containers during pipeline runs (Claude Code CLI driven, per SAD §4.5):

`PO` (Product Owner), `RESEARCHER`, `ARCHITECT`, `DESIGNER`, `PLANNER`, `IMPLEMENTER`, `REVIEWER`, `TESTER`, `SECURITY_ANALYST`, `TECHNICAL_WRITER`, `DEVOPS`, `RETRO_ANALYST`.

PDLC personas' "skills" are CLI/MCP tools available to Claude Code inside the container (file edits, bash, web fetch, custom MCP servers) — not orchestrator-level skills. Their `skillIds` in the registry reference the allowed CLI tools; mapping to `--allowedTools` happens at boot.md assembly time inside Arbiter.

#### 4.2.3 Naming distinctions (deliberate)

| Chat (CONVERSATIONAL) | Pipeline (PDLC) | Why the distinction |
|---|---|---|
| **Product Manager** | **Product Owner (PO)** | Industry convention: PM = strategic, customer-facing. PO = tactical, scope/backlog owner inside a delivery team. The chat brain is strategic; the pipeline first role is tactical. |
| **Market Researcher** | **Researcher** | Market = customer/competitor/demand. Researcher (PDLC) = technical feasibility, libraries, prior art in the codebase. Different jobs, different prompts. |

The existing `PdlcRole.PM` enum value is renamed to `PdlcRole.PO` (Product Owner) in the same migration that introduces the registry. Existing `pipeline_runs` and `pipeline_events` records get an in-place Mongo update from `"PM"` → `"PO"` (single-deploy cutover — see §7).

#### 4.2.4 Registry schema

```
Persona {
  id            String              "product-manager" | "market-researcher" | "product-owner" | ...
  family        PersonaFamily       CONVERSATIONAL | PDLC
  displayName   String
  description   String
  systemPrompt  String              markdown body
  defaultModel  String              Anthropic model id; for PDLC, passed to claude --model
  skillIds      List<String>        refs into skills collection
  voicePreset   VoicePreset?        ElevenLabs config; null for PDLC personas (they don't speak directly)
  active        boolean
  version       int                 bumped on edit; old versions retained for replay
}

Skill {
  id            String              "propose_implementation" | "web_search" | ...
  name          String
  description   String
  inputSchema   JsonNode            Anthropic tool-use input schema
  activityName  String              Temporal activity that handles the tool call
  active        boolean
}
```

#### 4.2.5 Headline numbers

- v1 ships: **2 conversational personas + 12 PDLC personas = 14 personas**
- v1 ships: **~15 skills** shared across personas
- Adding a new persona = insert a Mongo document + author a system prompt. No code change, no deploy.
- Adding a new skill = insert a Mongo document + implement the backing activity. One code change scoped to the activity, no workflow change.

## 5. Functional Requirements

### 5.1 Session Lifecycle

```
IDLE → ENGAGED (templated greeting plays on open; user starts speaking or types)
ENGAGED → GATHERING (Product Manager drives, uses ask_clarification skill)
GATHERING → PROPOSING (Product Manager uses propose_implementation skill)
PROPOSING → CONFIRMED (user approves; Product Manager uses start_pipeline skill)
CONFIRMED → PIPELINE_ACTIVE (PipelineWorkflow child workflow runs — see note below on multi-pipeline)
PIPELINE_ACTIVE → PIPELINE_BLOCKED (any of the user's pipelines raised a checkpoint)
PIPELINE_BLOCKED → PIPELINE_ACTIVE (user resolves a checkpoint conversationally)
PIPELINE_ACTIVE → ENGAGED (user has no more in-flight pipelines)
Any → ABANDONED (session idle 24h — pipelines continue independently)
Any → CANCELLED (user explicit cancel of session)
```

**Multi-pipeline + multi-session notes** (see SAD §3.6 for full model):
- A session can be in `PIPELINE_ACTIVE` with N pipelines in flight at once.
- `PIPELINE_BLOCKED` reflects "at least one of the user's pipelines is blocked on a checkpoint." A user with 3 in-flight pipelines can have 2 running + 1 blocked simultaneously; the session is in `PIPELINE_BLOCKED` until the blocked one resolves OR another pipeline blocks too.
- **Sessions don't own pipelines.** Pipelines are user-scoped. A pipeline started in Session 1 (web) can be checkpoint-resolved from Session 2 (mobile/Telegram) — the session that completes its `mediate_pipeline_checkpoint` skill wins, others receive a "resolved by other channel" event and clear their banner.
- **Sessions end, pipelines don't.** When a session abandons after 24h idle, the user's in-flight pipelines keep running. On the user's next session start, bootstrap shows "you have 2 in-flight, 1 blocked" and the templated greeting acknowledges it (see §5.9 below).
- **Leapfrog is normal.** Pipeline A started Monday, blocked Tuesday on a design decision. Pipeline B started Wednesday, runs through and completes Thursday. Pipeline A's checkpoint resolved Friday, finishes Saturday. Order of start ≠ order of finish, by design.

### 5.2 Modality Switching

| Mode | Mic | TTS | Sphere visible | Text chat visible |
|------|-----|-----|----------------|-------------------|
| `VOICE_ACTIVE` (default) | always-on VAD | streaming | yes | captions only |
| `VOICE_PTT` | push-to-talk | streaming | yes | captions only |
| `TEXT_ONLY` | off | off | minimized indicator | full chat |
| `MUTED` | off | off | yes, idle pulse | yes |

Switching modes mid-session never destroys session state. The Temporal workflow doesn't care which mode is active; modality lives in the WS handler and UI.

### 5.3 Voice Plane Requirements

- **STT**: Deepgram streaming WebSocket. Interim transcripts emit every ~100-300ms. Final transcripts trigger orchestrator turns.
- **Endpointing**: Deepgram's `endpointing` parameter (default 10ms after speech end) decides turn end. User can interrupt assistant by speaking — barge-in supported.
- **TTS**: ElevenLabs streaming WebSocket or HTTP-chunked. First audio chunk must start within 600ms of orchestrator-response-first-token. Voice = stock professional voice for v1 (configurable per persona later).
- **Auto-mute during TTS**: while assistant audio is playing, the mic capture stream stops sending to Deepgram (or sends but is suppressed) to prevent the assistant from hearing itself. Re-engage immediately when TTS playback ends.
- **Barge-in**: if user starts speaking during TTS, abort TTS playback, abort the in-flight ElevenLabs request, mark the assistant turn as `INTERRUPTED`, accept the new user transcript.
- **Captions**: every TTS chunk has corresponding text. UI shows live captions for accessibility and for users who toggle to text mid-stream.
- **Graceful degradation**: if Deepgram fails, surface "voice unavailable" in UI, switch to TEXT_ONLY. If ElevenLabs fails, deliver text only — sphere shows speaking pulse but no audio (with subtle UI hint).

### 5.4 Sphere UI Requirements

- **States**: `idle` (slow ambient pulse), `listening` (pulse modulated by mic amplitude), `thinking` (denser/dimmer motion), `speaking` (pulse modulated by TTS audio amplitude), `error` (red tint), `disabled` (greyed).
- **Interactions**: click to mute/unmute mic, long-press to switch modality, hover for captions toggle.
- **Performance**: must hold 60fps on a 2020-era MacBook Air. WebGL/Three.js or pure Canvas — implementation-detail call by the engineer.
- **Toggle**: explicit "Text chat" button in the chat area; sphere shrinks to a corner indicator. Text input becomes primary.
- **Captions**: live caption strip under the sphere when in voice mode. Scrollable history beneath.

### 5.5 Persona Selection (Routing as a Function)

Routing is a **function**, not an LLM call. Saves ~300ms per turn and ~$0.0001 per turn vs. an LLM router. Hard rules and intent keywords cover the common case; if we ever see telemetry showing too many misroutings, we add a Haiku fallback for the edge case — but ship without it.

```
chooseHandler(state, userTurn, pipelineState, recentTurns) -> personaId | controlAction:

  // 1. Control intents (regex, no persona invoked)
  if userTurn matches /switch to text|be quiet|mute|go quiet|cancel/:
    return ControlAction.MODE_CHANGE | CANCEL

  // 2. Hard rules
  if state == PIPELINE_BLOCKED:
    return "product-manager"      // uses mediate_pipeline_checkpoint skill

  // 3. Sticky persona — last persona keeps the turn unless topic shifts
  if recentTurns.last().personaId == "market-researcher"
     and userTurn doesn't match topic-shift keywords:
    return "market-researcher"

  // 4. Market research intent detection
  if userTurn matches /market|competitor|validate|demand|pricing|traction|TAM|SAM|customer interview/:
    return "market-researcher"

  // 5. Default
  return "product-manager"
```

The selected persona then drives the turn — its LLM call returns text and optional tool calls (skills). The workflow executes each skill via the bound Temporal activity, feeds results back, and loops until the persona stops requesting tools.

The routing decision is logged with the turn for telemetry and retro analysis.

**Parallel personas** (e.g., advisory turns where Product Manager and Market Researcher both speak briefly) are out of scope for v1 — single persona per turn.

### 5.6 Checkpoints (Pipeline → Conversation)

Today, pipeline checkpoints surface as a UI banner with Accept/Reject buttons. With the Cloud Agent Orchestrator:

- Pipeline raises a checkpoint → workflow transitions to `PIPELINE_BLOCKED` → routing returns `product-manager` (hard rule) → Product Manager handles the turn using its `mediate_pipeline_checkpoint` skill.
- The skill receives the structured checkpoint payload (role, message, options, artifact refs) and renders it conversationally.
- Voice mode: sphere speaks *"The architect wants to confirm the database choice — Postgres or Mongo? Either works, but Postgres gives you the relational queries you mentioned earlier."*
- User responds in natural language → the skill's backing activity parses the response → emits `CheckpointResolutionSignal` to the `PipelineWorkflow` → pipeline resumes.
- Text mode: same skill, user can click button OR type a natural-language response.

### 5.7 Persistence

- Every turn (user + assistant) persists to Mongo with: timestamp, modality, persona used, transcript text, audio refs (S3-compatible storage URLs for STT input and TTS output), tokens, cost.
- Session state queryable via Temporal `@QueryMethod` for UI bootstrap.
- Audio retention: configurable, default 30 days.

### 5.7.1 Session ↔ Spark conceptual model

A **`ConversationSession`** is the long-lived top-level entity for a user's chat — one workflow per session, hours to days long. A **`Spark`** is a unit of executable work — a pipeline run, a cloud skill invocation, a device dispatch.

**The relationship is 1-to-many (and N can be 0).** Today's flat 1-to-1 `ConversationSession.sparkId` is replaced with `sparkIds: List<String>`. Within one session:

| Conversation turn | Creates a Spark? |
|---|---|
| Page open → templated greeting | No |
| User clarifies requirements | No |
| User asks Market Researcher about competitors | No (research happens inside the conversation via skills; no spark) |
| User confirms scope → `start_pipeline` skill fires | **Yes** — one Spark of type CODE/DEVOPS, child `PipelineWorkflow` runs |
| User asks "how's it going" → `summarize_pipeline_progress` | No (reads the existing spark's state) |
| Pipeline completes; user says "great, now add tests too" → another `start_pipeline` | **Yes** — second Spark |
| User says "post this to Twitter when done" → `start_cloud_skill` | **Yes** — Spark of type SOCIAL |
| User says "switch to text" | No (modality change) |

A spark is created exactly when execution starts. Conversation-only turns produce zero sparks. This makes Sparks honest units of work — every spark represents a real unit of execution to bill, track, and report on.

**Implications:**
- `ConversationSession.sparkIds` is a List, ordered by creation time, append-only.
- `SparkController.createSpark(...)` is still called by the workflow as an activity (`StartPipelineWorkflowActivity` / `StartCloudSkillActivity` / `DispatchToDeviceActivity` all create a Spark before kicking off the underlying execution).
- `Spark.conversationSessionId` (new field) — the spark records which conversation produced it, for traceability.
- The Telegram path: a Telegram message creates a session if none exists for that user/group, then proceeds normally. Sparks accumulate as work happens. The "/spark" Telegram command can be interpreted as "create the next spark right now without further conversation" — a fast-path through `start_pipeline`.
- The existing `Spark` entity gains `conversationSessionId` field. Existing sparks (pre-deploy) get null for that field; not an issue since pre-deploy sparks don't reliably complete anyway.

**Multi-session per user** (see SAD §3.6 for full model): a user has **many** sessions concurrently — web tab A, web tab B, mobile, Telegram all open at once is normal. **Sparks (and the pipelines they drive) are scoped to the user, not to the session that created them.** The `Spark.conversationSessionId` is recorded for transcript linkage, but operations on a spark (status query, checkpoint resolution, cancel) can come from ANY of the user's sessions. The session that resolves a pipeline checkpoint may be different from the session that created the pipeline.

### 5.9 Templated Session Greeting

The opening greeting on a fresh page/session load is **templated, not LLM-generated.** Three reasons:
1. **Zero latency on page mount** — no LLM round-trip means audio can play within ~200ms of arrival
2. **Consistent first impression** — no model drift run-to-run
3. **Cost** — the cheapest possible first turn

The greeting is **attributed to Product Manager in the transcript** (one coherent voice the user gets to know) but emitted by a workflow activity, not an Anthropic call. Product Manager's LLM only fires starting on the user's first response.

Template selection by session bootstrap state (queries user's cross-session in-flight work — see SAD §6.4):

| Condition | Template | Priority |
|---|---|---|
| First-ever session for this user | `"Hey {firstName}, welcome to Tacticl. What do you want to build?"` | (only applies if true) |
| Same-day resume, no new sparks since last activity, nothing in flight | (none — user re-engages directly) | (only applies if true) |
| **User has any pending checkpoint** | `"Welcome back. {Pipeline X} is waiting on you to decide {Y}, and {Pipeline Z} is still running. What do you want to tackle?"` (or the single-pipeline form if only one) | **highest** |
| User has in-flight pipelines but no pending checkpoints | `"Welcome back. {Pipeline X} is at step {N} ({roleName}), {Pipeline Z} is running. Want a status update or something new?"` | high |
| Recent completions while away, nothing in flight | `"Welcome back. {Pipeline X} finished while you were away. What's next?"` | medium |
| Resume after idle, most recent spark known, nothing in flight | `"Welcome back. Last we were on {lastSpark.title} — pick up there, or something new?"` | medium |
| Generic resume | `"Hey {firstName}, what are we working on?"` | lowest |

Variable substitution (`{Pipeline X}`, `{roleName}`, etc.) uses **human-readable pipeline names** (`PipelineRun.name`, ~30 chars, auto-set at pipeline creation from the `propose_implementation` summary — see SAD §9). Never expose pipeline UUIDs to the user.

**Voice mode optimization**: ElevenLabs audio for the 3-4 standard templates is pre-generated per voice and cached at CDN edge. Page mount → audio plays. The sphere can speak before the workflow has even fully woken up. {firstName} and {lastSpark.title} are templated client-side by concatenating cached audio with on-the-fly TTS of just the variable part (or, for v1 simplicity, the variable parts are spoken via live TTS while the rest is cached — adds 100-300ms, still under the latency budget).

### 5.8 Cost & Safety

- **Per-session cost ceiling**: LLM + Deepgram + ElevenLabs combined. Default **$5 per session** — above the ~$1.80 median for a 30-turn voice session (§9.1) but caps the heavy tail. Configurable per user.
- **Per-user monthly cost ceiling**: existing `spendingLimit` extended to include voice plane costs.
- **Hard kill**: hitting the per-session ceiling moves the session to `MUTED` mode (text-only) and surfaces a notice. Pipeline execution unaffected — pipelines have their own `pipelineCostCeiling` ($50/run default).

## 6. Acceptance Criteria

### 6.1 V1 Ship Criteria

- [ ] Single Temporal-backed `CloudAgentSessionWorkflow` handles conversation; `PipelineWorkflow` is the child for execution.
- [ ] `PersonaRegistry` + `SkillRegistry` (Mongo + cached) hold the 2 conversational + 12 PDLC personas and ~15 skills.
- [ ] `PlaybookRegistry` (Mongo) holds all playbooks; `PlaybookSpecResolver` hardcoded map deleted.
- [ ] Product Manager and Market Researcher personas authored and live; routing function (no router LLM) picks between them.
- [ ] `PdlcRole.PM` → `PdlcRole.PO` rename complete; existing `pipeline_runs` / `pipeline_events` Mongo records migrated in-place.
- [ ] Voice sphere ships on tacticl-web at `/chat` (replaces current `ChatPage`).
- [ ] Templated greeting plays on session open in <500ms (pre-cached ElevenLabs audio for the standard templates).
- [ ] Always-on VAD voice mode works end-to-end: user speaks → captions appear → assistant speaks back. p50 user-pause-to-first-TTS-chunk < 1200ms.
- [ ] Modality toggle (voice / text / mute) preserves session state.
- [ ] Pipeline checkpoints surface via Product Manager's `mediate_pipeline_checkpoint` skill, resolved via natural-language response in voice mode.
- [ ] Conversation transcripts persist with audio refs.
- [ ] **Existing in-process `ConversationService` state machine and `PdlcV2Service` direct-execution path are physically deleted** (not feature-flag-gated, not coexisting).
- [ ] `.claude/worktrees/agent-ad671ea0/` deleted, `RoleIdentityLoader` deleted, `role-identities/*.md` deleted.
- [ ] Whisper removed from live voice path; `client-whisper` retained for Telegram voice + future file uploads.
- [ ] Old PDLC v2 PRD/SAD marked superseded.

### 6.2 Out-of-Scope for V1

- Mobile sphere (mobile keeps current push-to-talk → Whisper → REST path; migrates in V2).
- Wake-word detection.
- Persona-specific voices (single voice in v1).
- Multi-party / shared sessions.

## 7. Tear-Out & Cutover

**No phased rollout, no feature flags, no dual-system coexistence.** The existing `ConversationService` in-process state machine and `PdlcV2Service` direct-execution path haven't worked end-to-end in production. Preserving them while building the new orchestrator is preserving broken code that we'd just delete later. They get deleted in the same deploy that ships the new orchestrator.

### 7.1 Deleted outright

| Artifact | Replaced by |
|----------|-------------|
| `.claude/worktrees/agent-ad671ea0/` (~3,500 LoC abandoned `@Async` orchestrator) | — (dead code) |
| `business-pipeline/.../RoleIdentityLoader.java` | `PersonaRegistry` |
| `business-pipeline/src/main/resources/role-identities/*.md` | `personas` Mongo collection |
| `business-pipeline/.../PlaybookSpecResolver.java` hardcoded map | `playbooks` Mongo collection |
| `ConversationService` in-process state machine (the gather/propose/active turn loop) | `CloudAgentSessionWorkflow` (Temporal) |
| `PdlcV2Service` orchestration body (submit + callback handling logic) | `PipelineWorkflow` activities |
| Whisper invocation in any live conversation path | Deepgram streaming bridge |
| Any feature flag intended to gate the cutover (`pdlc.temporal.enabled`, `tacticl.jarvis.enabled`, etc.) | **Not introduced** — single implementation, no flag |

### 7.2 Refactored (shape preserved, body replaced)

| Artifact | Change |
|----------|--------|
| `ConversationService` REST controllers | REST endpoints (`POST /v1/conversations`, `POST /v1/conversations/{id}/messages`) preserved for `tacticl-web` and `tacticl-mobile`. Handler bodies become "send signal to workflow." Service class itself is reduced to ~30 LoC of signal dispatching. |
| `PdlcV2Service` | Reduced to a constructor-injected `ArbiterGrpcClient` used by `SubmitToArbiterActivity`. ~80% of current LoC removed. |
| `PipelineEventEmitter` + channels | Kept; `PipelineWorkflow.FanOutPipelineEventActivity` calls into it. |
| `TelegramConversationAdapter` | Translates Telegram inbound to workflow signals; rest unchanged. |
| `SparkController` / `AgentController` / `AgentCommandService` | Slimmed to "send signal to workflow"; existing REST endpoints preserved. |

### 7.3 Untouched

- Arbiter and `client-ai-arbiter` (execution plane unchanged)
- `PipelineRun`, `PipelineEvent`, `PipelineCheckpoint` entities + repositories
- Existing MongoDB pipeline schema (other than the in-place `"PM"` → `"PO"` role rename — see §4.2.3)
- The 12 PDLC role markdown content (migrated verbatim into the registry)
- `CloudOrchestratorService` (cloud agent skill executor for social/research/video sparks) — wrapped as the body of `StartCloudSkillActivity`, not modified

### 7.4 Cutover mechanics

Single-deploy, single-cut. Order of operations within the deploy:

1. Temporal cluster up and healthy (provisioned earlier per Phase 1 of the plan).
2. New JAR with persona/playbook registries + workflows + voice plane deploys to Cloud Run.
3. Migration runner on startup:
   - Ingests `role-identities/*.md` → `personas` collection (12 PDLC personas).
   - Inserts authored Product Manager + Market Researcher personas (2 conversational).
   - Inserts ~15 skill records into `skills` collection.
   - Ingests `PlaybookSpecResolver` map → `playbooks` collection.
   - Migrates `pipeline_runs.role` and `pipeline_events.role` fields: `"PM"` → `"PO"` in-place.
   - All migrations idempotent + guarded by a `migration_log` document.
4. Old `ConversationService` / `PdlcV2Service` direct-execution code is *not present* in the new JAR — there is no in-flight code that could still hit the old paths.
5. tacticl-web ships with new sphere UI in the same release window.

**In-flight conversations and pipelines at cutover:** none worth preserving. Current pipelines aren't reaching `COMPLETED` reliably anyway. Pre-cutover sessions are abandoned; users restart.

**Rollback strategy:** the old system doesn't work — there's nothing to roll back to. If post-deploy issues are severe and unfixable in <1 day, the emergency rollback target is "previous JAR with chat/pipeline functionality entirely disabled" (operationally acceptable as a short-term emergency, much less acceptable than "the old broken behavior"). Risk acknowledged.

### 7.5 Old PRD/SAD Status

`2026-04-11-tacticl-pdlc-v2-prd.md` and `2026-04-11-tacticl-pdlc-v2-sad.md` are marked **SUPERSEDED** by this PRD and its companion SAD. The pipeline-execution content from the old SAD (Arbiter, workspace assembly, knowledge layers, 4-layer injection) remains canonical for the Arbiter side and is referenced from the new SAD rather than restated.

## 8. Risks & Open Questions

### 8.1 Risks

| Risk | Mitigation |
|------|------------|
| Voice latency budget (<1200ms p50) is tight | Function-based routing (zero LLM in common path); Haiku for fast personas; ElevenLabs Turbo; pre-cached greeting audio; co-locate Temporal worker with Hetzner egress |
| Adopting Temporal contradicts 2026-05-20 deferral | Accepted by user (2026-05-25). More infra now, no rewrite later |
| Always-on mic auto-mute echo | Strict mute-before-play sequencing; echo telemetry; tune over first week |
| ElevenLabs cost at scale | Per-session + per-user ceilings enforced at workflow level; templated greeting cached to skip LLM/TTS round-trip on opens |
| Single-tenant Temporal cluster as new SPOF | Postgres backup + restore runbook before going live; cluster restart runbook |
| **Hard cutover means no rollback path to the old system** | The old system doesn't reliably work end-to-end — there's nothing to roll back to. If post-deploy issues are severe and unfixable in <1 day, emergency rollback target is "previous JAR with chat/pipeline disabled entirely" (operationally acceptable short-term). Risk acknowledged. |
| Migration runner data corruption (PM→PO Mongo update) | Idempotent + guarded by migration_log doc; full Mongo backup taken pre-deploy; QA dry-run on a copy of prod data |

### 8.2 Open Questions

1. **Per-persona voices** — single voice across Product Manager + Market Researcher in v1; revisit in v1.5 if dogfood says distinct voices help.
2. **Captions language** — English-only v1.
3. **Temporal cluster host** — same Hetzner host as Arbiter v1; revisit at 50+ concurrent sessions.
4. **Mobile sphere** — explicitly not v1.
5. **Skill granularity** — `web_search` + `read_page` could collapse into a single `research_web` skill, or stay separate for finer cost control. Defer to first authoring pass.
6. **Pre-existing Spark-creation REST surface (`POST /v1/agent/command`, `POST /v1/sparks`)** — kept as thin "send signal to workflow" adapters indefinitely (Telegram + mobile REST consumers depend on them).

## 9. Success Metrics

| Metric | Target |
|--------|--------|
| p50 user-pause → first-TTS-chunk | < 1200ms |
| p95 user-pause → first-TTS-chunk | < 2200ms |
| % of sessions that complete in voice mode without manual fallback to text | > 70% |
| % of pipeline checkpoints resolved via natural-language voice response | > 50% |
| Sphere UI 60fps hold-rate on baseline hardware | > 95% |
| Voice plane uptime | > 99.5% (graceful text fallback covers the rest) |
| Cost per active session (median, 30-turn voice session) | < $2.50 incl. STT + TTS + LLM |
| Cost per active session (median, text-only session) | < $0.30 |

### 9.1 Cost model (sanity check)

A typical **30-turn voice session** (15 user + 15 assistant) breaks down roughly as:

| Component | Per-session usage | Per-session cost |
|---|---|---|
| Deepgram nova-2 streaming STT | ~10 min audio @ $0.0043/min | $0.04 |
| ElevenLabs Turbo TTS | ~2,000 characters spoken @ $0.18/1k chars | $0.36 |
| Anthropic Sonnet 4.6 (Product Manager) | ~25 turns, avg 4k input + 800 output tokens | ~$1.10 |
| Anthropic Sonnet 4.6 (skill activities like analyze_competitors) | 2-3 tool calls per session, ~2k tokens each | ~$0.30 |
| Mongo / Temporal writes | negligible | ~$0.00 |
| **Total (median)** | | **~$1.80** |

A heavy session that triggers `analyze_competitors` + several `web_search` calls can reach $4-5. Per-session ceiling default = **$5** (PRD §5.8) — above the median, below the heavy tail.

**Text-only sessions** drop STT + TTS, so ~$0.20-0.40.

**Pipeline cost is separate** and has its own ceiling (`pipelineCostCeiling`, default $50/run). The session-level ceiling only covers conversational tokens + STT + TTS, not what Arbiter spends inside containers.

These numbers assume ElevenLabs Turbo tier + Sonnet 4.6. If we drop to Haiku for some turns (likely for `summarize_pipeline_progress` and short answers), session cost drops 30-40%. We'll revise after first week of dogfood.

## 10. References

- [Cloud Agent Orchestrator SAD (2026-05-25)](2026-05-25-cloud-agent-orchestrator-sad.md) — companion architecture doc
- [Conversational Chat Design (2026-04-17)](2026-04-17-conversational-chat-design.md) — current text-only conversation model being subsumed
- [Agent Intake Consolidation (2026-05-05)](2026-05-05-agent-intake-consolidation-design.md) — recent intake unification work, complementary
- [Telegram Conversational Spark to PDLC Plan (2026-05-15)](../plans/2026-05-15-telegram-conversational-spark-to-pdlc.md) — Telegram path that will run on the new orchestrator
- [Tacticl PDLC v2 PRD (2026-04-11)](2026-04-11-tacticl-pdlc-v2-prd.md) — **superseded by this doc**
- [Workflow Temporal Critique (2026-05-20)](../../architecture/critiques/2026-05-20-workflow-temporal-critique.md) — prior deferral recommendation, explicitly overridden
- [Architecture Synthesis (2026-05-20)](../../architecture/critiques/2026-05-20-synthesis.md) — prior consensus, explicitly overridden for Temporal timing
