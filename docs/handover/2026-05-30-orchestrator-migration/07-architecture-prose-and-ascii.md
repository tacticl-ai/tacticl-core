# 07 — Architecture in Prose + ASCII

> **Why this file exists.** The architecture lives in two `.drawio` diagrams (see "Source of truth" below). `.drawio`
> renders are unreliable inside a session, and the `.drawio.png` exports could not be read by the image pipeline this
> session ("could not process image"). This file is a faithful **text + ASCII** rendering of those diagrams so a fresh
> session can resume with the full mental model without opening draw.io. Every box and label below was parsed directly
> from the `.drawio` XML — node values and edge labels are verbatim where quoted.
>
> For the *decisions* behind this shape (why one orchestrator, why voice-WS direct, why Option B, productId scoping,
> etc.) read the companion `00-session-decisions.md` in this same directory. This file is the **picture**; that file is
> the **reasoning**. They are intentionally non-overlapping.

## Source of truth (editable originals)

| Artifact | Absolute path | Status (verified via `ls` 2026-05-30) |
|---|---|---|
| Ecosystem view (both products → arbiter), editable | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/architecture/ecosystem-arbiter-architecture.drawio` | EXISTS (19,843 bytes) — read in full, parsed below |
| Ecosystem view, PNG export | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/architecture/ecosystem-arbiter-architecture.drawio.png` | EXISTS (417,674 bytes). Could not be opened by the image reader this session ("could not process image") — render outside the session if you need the picture. |
| Tacticl deep view (concentric rings), editable | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/architecture/tacticl-cidadel-arbiter-architecture.drawio` | EXISTS (15,598 bytes) — read in full, parsed below |
| Tacticl deep view, PNG export | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/architecture/tacticl-cidadel-arbiter-architecture.drawio.png` | EXISTS (350,750 bytes). Same image-reader caveat as above. |

**Naming note:** the PNG exports are `*.drawio.png` (the draw.io default export naming), NOT `*.png` stripped of the
`.drawio` segment. `00-session-decisions.md` §"Corrections owed" item 4 says the diagrams were "already updated …
(`docs/architecture/*.drawio` + `.png`)" — that is accurate, accounting for the `.drawio.png` suffix. **The `.drawio`
files are the canonical, editable source of truth; the `.drawio.png` files are derived exports.** If you edit the
diagram, re-export the PNG (e.g. via the `drawio` skill / draw.io export).

> Full `ls` of the architecture dir, confirmed this session:
> `cloud-orchestrator-architecture.md`, `critiques/`, `device-agent-architecture.md`,
> `ecosystem-arbiter-architecture.drawio`, `ecosystem-arbiter-architecture.drawio.png`,
> `learning-layer-and-codegen-prompts-preservation.md`, `pdlc-pipeline-architecture.md`,
> `tacticl-cidadel-arbiter-architecture.drawio`, `tacticl-cidadel-arbiter-architecture.drawio.png`.

---

## The one-paragraph summary

There is **ONE shared AI engine** — `cidadel-ai-arbiter` (TypeScript/Node) — that serves **every product**. Inside it
lives **ONE orchestrator brain**: a durable Temporal `SessionWorkflow` (one per conversation). When, and only when, a
build is actually needed, that orchestrator **spawns a child `PipelineWorkflow`** — a separate durable sub-job, not a
second brain. The conversation layer is where voice lives (Deepgram STT in, ElevenLabs TTS out, as I/O adapters); the
**pipeline is SILENT** — it emits text events that the persona narrates back through TTS, but there is no audio
anywhere inside the pipeline. Products (`tacticl-core` Java, `strategiz-core` Java, future) remain **full product
backends of any size** and reach the arbiter over **internal-only gRPC**. The **single exception** to "browsers never
touch the arbiter" is the **voice WebSocket**, which connects browser → arbiter directly for latency, authorized by a
short-lived session token the product backend signs and the arbiter verifies via a shared Vault key.

---

## (1) Ecosystem view — both products → one arbiter

Parsed from `ecosystem-arbiter-architecture.drawio`. Two product backends (Tacticl, Strategiz) sit on top of the same
shared arbiter. The arbiter is internally stratified into three horizontal bands: **Orchestrator (the single brain)**,
**PipelineWorkflow (child, silent)**, and **Shared infrastructure**.

```
 TACTICL — channels & UX                          STRATEGIZ — channels & UX
 ┌──────────────┬──────────────┬─────────────┐    ┌──────────────┬─────────────────┐
 │ tacticl-web  │ tacticl-     │ Telegram    │    │ strategiz-   │ Discord bot     │
 │ chat + 🎙    │ mobile       │ bot         │    │ web          │ alerts +        │
 │ voice sphere │ push-to-talk │             │    │ dashboard    │ commands        │
 └──────┬───────┴──────┬───────┴──────┬──────┘    └──────┬───────┴────────┬────────┘
        │              │              │                  │                │
        │ REST+WS      │ (REST)       │ (inbound)        │ (REST)         │ alerts +
        │ (non-voice)  │              │                  │                │ commands
        ▼              ▼              ▼                  ▼                ▼
 ┌─────────────────────────────────────────┐    ┌─────────────────────────────────────┐
 │ tacticl-core — product backend           │    │ strategiz-core — product backend     │
 │ (Java / Spring) · full product, any size │    │ (Java / Maven) · ENORMOUS, full prod │
 │                                          │    │                                      │
 │ Telegram adapter (thin signaler)         │    │ Discord adapter (signaler) + alert   │
 │ Web chat + Voice WS handler·token signer │    │   publisher                          │
 │ Auth·OAuth·Device pairing·UserConfig·Push│    │ Auth · user config · large domain    │
 │ REST projections (read)                  │    │ Market data / analytics ingest       │
 │ Skill executors — CloudOrchestratorSv"c  │    │ REST · jobs · strategiz skill        │
 │   (social/browser/video)·domain·jobs     │    │   executors                          │
 └────────────────┬─────────────────────────┘    └───────────────┬──────────────────────┘
                  │                                               │
                  │ gRPC (LLM / AI work)         gRPC (LLM/AI work)│      [Market-data APIs
                  │   INTERNAL-ONLY               INTERNAL-ONLY    │       (Strategiz)] ◄─ market data
                  ▼                                               ▼
 ╔══════════════════════════════════════════════════════════════════════════════════════╗
 ║ cidadel-ai-arbiter — shared AI engine (TypeScript / Node) · the LLM gateway for ALL    ║
 ║                                                                              products   ║
 ║ ┌──────────────────────────────────────────────────────────────────────────────────┐ ║
 ║ │ ORCHESTRATOR — the single brain · durable SessionWorkflow (Temporal):             │ ║
 ║ │ survives restart/reconnect, idle timers, signal-driven voice ·                     │ ║
 ║ │ sessions & personas are product-scoped                                             │ ║
 ║ │                                                                                    │ ║
 ║ │  ┌─────────┐  ┌──────────────┐  ┌────────────────────┐  ┌──────────────────────┐  │ ║
 ║ │  │ Voice WS│→ │ SessionWork- │→ │ PersonaRouter       │→ │ Anthropic streaming  │→─╫─► Anthropic API
 ║ │  │ endpoint│  │ flow         │  │ (pure fn)           │  │ (persona responses)  │  │ ║
 ║ │  │(tacticl │tr│ (Temporal,   │rt│ Persona/Skill regis │  ├──────────────────────┤  │ ║
 ║ │  │ voice,  │an│ durable) one │ou│ tries (Tacticl +    │  │ Deepgram bridge(STT) │◄─╫─► Deepgram (cloud STT)
 ║ │  │ DIRECT) │sc│ per convo    │te│ Strategiz personas) │  │  — I/O adapter       │  │ ║
 ║ │  └────▲────┘ri└──────┬───────┘  └─────────┬───────────┘  ├──────────────────────┤  │ ║
 ║ │       │      pt      │                    │              │ ElevenLabs bridge    │◄─╫─► ElevenLabs (cloud TTS)
 ║ │       │              │ start_pipeline     │ gRPC callback│  (TTS) — I/O adapter │  │ ║
 ║ │       │              │ → SPAWNS child wf  │ start_cloud_ │  └──────────────────────┘  │ ║
 ║ │       │              │ (only when a build │ skill (back  │                            │ ║
 ║ │       │              │  is needed)        │ to products) │                            │ ║
 ║ │       │              ▼                    └──► tacticl t_skill / strategiz s_rest    │ ║
 ║ └───────┼──────────────┼──────────────────────────────────────────────────────────────┘ ║
 ║         │              ▼                                                                  ║
 ║ ┌───────┼────────────────────────────────────────────────────────────────────────────┐ ║
 ║ │ PIPELINEWORKFLOW — child workflow (spawned per build by the orchestrator) ·          │ ║
 ║ │ durable, async, SILENT (no audio) · shared by all products                           │ ║
 ║ │  ┌──────────────┐  ┌────────────────┐  ┌─────────────────────┐  ┌─────────────────┐  │ ║
 ║ │  │ PipelineWork-│  │ Activities      │  │ Ephemeral containers│  │ Knowledge store │  │ ║
 ║ │  │ flow         │  │ SubmitToArbiter │  │ Claude Code CLI     │  │ + Learning layer│  │ ║
 ║ │  │ (Temporal    │  │ InvokeArbiter-  │  │ (ultracode mode)    │  │ (RAG over past  │  │ ║
 ║ │  │  child)      │  │ Role            │  │ → 12 PDLC roles     │  │ runs, captured  │  │ ║
 ║ │  │              │  │ PersistEvent·   │  │        │            │  │ learnings)      │  │ ║
 ║ │  │              │  │ FanOut          │  │        │ clone/push │  └─────────────────┘  │ ║
 ║ │  └──────┬───────┘  └───────┬─────────┘  └────────┼────────────► [GitHub (repos)]      │ ║
 ║ │         │ durable          │ write-through       │ Claude Code CLI ─► Anthropic API   │ ║
 ║ │         │ history          │ projections                                              │ ║
 ║ └─────────┼──────────────────┼──────────────────────────────────────────────────────────┘ ║
 ║           ▼                  ▼                                                            ║
 ║ ┌──────────────────────────────────────────────────────────────────────────────────────┐ ║
 ║ │ Shared infrastructure (multi-product)                                                  │ ║
 ║ │  ┌──────────────────────┐ ┌───────────────────────────────────┐ ┌───────────────────┐ │ ║
 ║ │  │ Temporal cluster     │ │ MongoDB (shared): conversation_   │ │ Vault (secrets ·  │ │ ║
 ║ │  │ (Postgres)           │ │ sessions·personas·skills·playbooks│ │ shared signing    │ │ ║
 ║ │  │                      │ │ ·pipeline_runs/events·checkpoints │ │ keys · voice      │ │ ║
 ║ │  │                      │ │ ·sparks (+ strategiz collections) │ │ token key)        │ │ ║
 ║ │  └──────────────────────┘ └───────────────────────────────────┘ └─────────┬─────────┘ │ ║
 ║ └─────────────────────────────────────────────────────────────────────────── │ ─────────┘ ║
 ╚════════════════════════════════════════════════════════════════════════════ │ ═══════════╝
                                                       secrets/keys (Vault ─────►arbiter)
```

### Ecosystem edges (verbatim labels from the XML)

- `t_web → c_tshell` — **"REST + WS (non-voice)"**
- `t_web → a_voicews` — **"Voice WS — DIRECT (token-signed)"** — bold red, strokeWidth 3. **This is the only browser →
  arbiter edge in the whole diagram.**
- `t_mobile → c_tshell`, `t_telegram → c_tshell` — unlabeled (mobile/telegram go through the product backend).
- `c_tshell → c_conv` — **"gRPC (LLM / AI work)"**
- `s_web → c_sshell` — unlabeled; `s_discord → c_sshell` — **"alerts + commands"**
- `c_sshell → c_conv` — **"gRPC (LLM / AI work)"** (same edge type as Tacticl — identical integration pattern)
- `a_voicews → a_sessionwf` — **"transcript"**; `a_sessionwf → a_persona` — **"route turn"**;
  `a_persona → a_anth` — **"invoke persona"**
- `a_deep → x_deep` — **"audio ↔ transcript"** (red); `a_eleven → x_eleven` — **"text ↔ audio"** (red);
  `a_anth → x_anth` — **"stream"**
- `a_sessionwf → a_pipewf` — **"start_pipeline → SPAWNS child workflow (only when a build is needed)"** — bold purple,
  strokeWidth 3. **This is the parent→child spawn edge.**
- `a_containers → x_github` — **"clone / push"**; `a_containers → x_anth` — **"Claude Code CLI"** (dashed)
- `a_pipewf → i_temporal` — **"durable history"** (dashed); `a_sessionwf → i_temporal` — unlabeled (dashed)
- `a_activities → i_mongo` — **"write-through projections"** (dashed)
- `a_persona → t_skill` — **"gRPC callback start_cloud_skill"** (dashed) — arbiter calls BACK into tacticl-core's skill
  executors
- `a_persona → s_rest` — **"gRPC callback (strategiz skills)"** (dashed) — same callback pattern for Strategiz
- `s_market → x_market` — **"market data"** (dashed); `i_vault → c_arbiter` — **"secrets / keys"** (dashed)

### The four invariants this view encodes

1. **Internal-only gRPC.** The only inbound edges to the arbiter from the product side are the two `gRPC (LLM / AI
   work)` edges from `tacticl-core` and `strategiz-core`. Browsers do not appear as gRPC callers.
2. **Voice WS is the single direct-browser exception.** `e2: Voice WS — DIRECT (token-signed)` is the lone edge from a
   browser box (`t_web`) straight into the arbiter (`a_voicews`). Everything else from a browser lands on a product
   backend first.
3. **One brain, child pipeline.** `SessionWorkflow` is the brain; the bold purple `start_pipeline → SPAWNS child
   workflow` edge is the only thing that creates a `PipelineWorkflow`, and only "when a build is needed."
4. **Voice attaches to conversation only.** The Deepgram and ElevenLabs bridge boxes live **inside the Orchestrator
   band** (`c_conv`), never inside the `PipelineWorkflow` band (`c_pipe`). The pipeline band's title literally reads
   "SILENT (no audio)."

---

## (2) Tacticl deep view — the three concentric rings

Parsed from `tacticl-cidadel-arbiter-architecture.drawio`. The session brief frames this as "three concentric rings:
voice I/O → orchestrator brain → silent pipeline." In the `.drawio` they are drawn as **stacked horizontal bands**
(not literal nested circles), but the *containment / data-flow* relationship is genuinely concentric: the outermost
ring is voice/channel I/O, it wraps the orchestrator brain, which in turn wraps (and spawns) the silent pipeline core.
Here is that concentric relationship made explicit:

```
 ┌──────────────────────────────────────────────────────────────────────────────────┐
 │ RING 1 — VOICE / CHANNEL I/O (outermost)                                           │
 │   tacticl-web 🎙 voice sphere+mic │ tacticl-mobile push-to-talk │ Telegram          │
 │   Deepgram (cloud STT) ◄─audio   │   ElevenLabs (cloud TTS) ◄─audio                 │
 │   tacticl-core (Java/Spring): auth, OAuth, device pairing, push,                    │
 │     REST projections (READ), skill executors, Telegram adapter,                    │
 │     Voice session-token SIGNER                                                      │
 │                                                                                    │
 │   ┌──────────────────────────────────────────────────────────────────────────┐   │
 │   │ RING 2 — ORCHESTRATOR BRAIN (middle) — inside cidadel-ai-arbiter            │   │
 │   │   Voice WS endpoint → SessionWorkflow (durable) → PersonaRouter (pure fn)   │   │
 │   │   → Anthropic streaming.   Deepgram bridge (STT) + ElevenLabs bridge (TTS)  │   │
 │   │   are I/O ADAPTERS that live HERE and nowhere else.                         │   │
 │   │                                                                            │   │
 │   │   ┌────────────────────────────────────────────────────────────────────┐  │   │
 │   │   │ RING 3 — SILENT PIPELINE CORE (innermost)                            │  │   │
 │   │   │   PipelineWorkflow (child) → Activities (SubmitToArbiter,            │  │   │
 │   │   │   InvokeArbiterRole, PersistEvent, FanOut) → Ephemeral containers    │  │   │
 │   │   │   (Claude Code CLI, ultracode, 12 PDLC roles) + Knowledge/Learning   │  │   │
 │   │   │   layer.   *** NO AUDIO IN THIS RING — it emits TEXT events only ***  │  │   │
 │   │   └────────────────────────────────────────────────────────────────────┘  │   │
 │   │            ▲ spawned by SessionWorkflow's start_pipeline                    │   │
 │   └──────────────────────────────────────────────────────────────────────────┘   │
 │   Shared infra underneath all rings: Temporal (Postgres) · MongoDB · Vault         │
 └──────────────────────────────────────────────────────────────────────────────────┘
```

The same diagram, in its actual stacked-band layout (verbatim box labels):

```
 ┌───────────────────────────┬─────────────────────────┬─────────────────┐
 │ tacticl-web (browser)     │ tacticl-mobile          │ Telegram        │
 │ 🎙 voice sphere + mic     │ (push-to-talk)          │                 │
 └──────┬──────────────┬─────┴───────────┬─────────────┴────────┬────────┘
        │              │                 │                      │
        │ Voice WS —   │ REST + WS       │ REST +               │ (inbound)
        │ DIRECT       │ (all non-voice) │ push-to-talk         │
        │ latency      │                 │                      │
        │ exception    ▼                 ▼                      ▼
        │ (token-    ┌─────────────────────────────────────────────────────┐
        │  signed)   │ tacticl-core — product backend (Java/Spring)         │
        │            │ · full product, any size                             │
        │            │  ┌──────────────┐ ┌──────────────────┐ ┌───────────┐ │
        │            │  │ Auth·OAuth   │ │ REST projections │ │ Skill     │ │
        │            │  │ callbacks    │ │ (READ): pipeline_│ │ executors │ │
        │            │  │ Device pair  │ │ runs·sparks·     │ │ CloudOrch │ │
        │            │  │ UserConfig·  │ │ conversations    │ │ (social/  │ │
        │            │  │ Push         │ │                  │ │ browser/  │ │
        │            │  └──────────────┘ └──────────────────┘ │ video)    │ │
        │            │  ┌──────────────────────────────────┐  └───────────┘ │
        │            │  │ Telegram adapter · Voice session │                │
        │            │  │ token SIGNER                     │                │
        │            │  └──────────────────────────────────┘                │
        │            └──────────────────────┬───────────────────────────────┘
        │                                   │ gRPC (all AI / LLM work)
        │                                   ▼
        │  ╔══════════════════════════════════════════════════════════════╗
        │  ║ cidadel-ai-arbiter — shared AI engine (TypeScript / Node)      ║
        │  ║ ┌────────────────────────────────────────────────────────────┐ ║
        │  ║ │ ORCHESTRATOR — the single brain · durable SessionWorkflow   │ ║
        │  ║ │ (Temporal): survives restart/WS reconnect, idle timers,     │ ║
        │  ║ │ signal-driven voice                                         │ ║
        │  ║ │ ┌─────────┐ ┌─────────────┐ ┌──────────────┐ ┌────────────┐ │ ║
        └──╫─►│Voice WS │→│SessionWork- │→│PersonaRouter │→│ Anthropic  │─╫──► Anthropic API
           ║ │ │endpoint │ │flow(Temporal│ │(pure fn)     │ │ streaming  │ │ ║
   transcript ││ (direct │tr│ durable) one│rt│ Persona/Skill│ ├────────────┤ │ ║
   signal     ││ from    │an│ per convo   │ou│ registries   │ │Deepgram    │◄╫──► Deepgram STT
           ║ │ │ browser)│sc└──────┬──────┘te└──────┬───────┘ │bridge(STT) │ │ ║
           ║ │ └─────────┘ri       │ start_         │ gRPC     ├────────────┤ │ ║
           ║ │             pt      │ pipeline →     │ callback │ElevenLabs  │◄╫──► ElevenLabs TTS
           ║ │                     │ SPAWNS child   │ start_   │bridge(TTS) │ │ ║
           ║ │                     │ (only when a   │ cloud_   └────────────┘ │ ║
           ║ │                     │  build needed) │ skill ──► n_skillexec   │ ║
           ║ │ └───────────────────┼────────────────┴─────────────────────────┘ ║
           ║ │                     ▼                                            ║
           ║ │ ┌────────────────────────────────────────────────────────────┐ ║
           ║ │ │ PIPELINEWORKFLOW — child workflow (spawned per build) ·      │ ║
           ║ │ │ durable, async, SILENT (no audio)                           │ ║
           ║ │ │ ┌───────────┐ ┌───────────────┐ ┌──────────────┐ ┌────────┐ │ ║
           ║ │ │ │Pipeline-  │ │ Activities    │ │ Ephemeral    │ │Knowledge││ ║
           ║ │ │ │Workflow   │ │ SubmitToArbi- │ │ containers   │ │store +  ││ ║
           ║ │ │ │(Temporal  │ │ ter·InvokeAr- │ │ Claude Code  │ │Learning ││ ║
           ║ │ │ │ child wf) │ │ biterRole·    │ │ CLI(ultracode│ │layer    ││ ║
           ║ │ │ │           │ │ PersistEvent· │ │ )→12 PDLC    │ │(RAG over││ ║
           ║ │ │ │           │ │ FanOut        │ │ roles ──clone│ │past runs││ ║
           ║ │ │ └─────┬─────┘ └──────┬────────┘ │ /push─► GitHub│└────────┘│ ║
           ║ │ │ durable│history write-│through                  │          │ ║
           ║ │ └────────┼──────────────┼──────────────────────────────────────┘ ║
           ║ │          ▼              ▼                                        ║
           ║ │ ┌────────────────────────────────────────────────────────────┐ ║
           ║ │ │ Shared infrastructure                                        │ ║
           ║ │ │  Temporal cluster (Postgres) · MongoDB (conversation_sess-   │ ║
           ║ │ │  ions·personas·skills·playbooks·pipeline_runs/events·        │ ║
           ║ │ │  checkpoints·sparks) · Vault (secrets + voice token key)     │ ║
           ║ │ └──────────────────────────────────────────────────────┬─────┘ ║
           ║ └──────────────────────────────────────────────────────── │ ─────┘ ║
           ╚════════════════════════════════════════════════════════ │ ═══════╝
                                       secrets / verify voice token ──┘
```

### Tacticl-deep edges (verbatim labels from the XML)

- `n_web → c_shell` — **"REST + WS (all non-voice)"**
- `n_web → n_voicews` — **"Voice WS — DIRECT  latency exception (token-signed)"** — bold red, strokeWidth 3
- `n_mobile → c_shell` — **"REST + push-to-talk"**; `n_telegram → c_shell` — unlabeled
- `c_shell → c_conv` — **"gRPC (all AI / LLM work)"**
- `n_voicews → n_sessionwf` — **"transcript signal"**; `n_sessionwf → n_persona` — **"route turn"**;
  `n_persona → n_anth` — **"invoke persona"**
- `n_deep → n_ext_deep` — **"audio ↔ transcript"** (red); `n_eleven → n_ext_eleven` — **"text ↔ audio"** (red);
  `n_anth → n_ext_anth` — **"stream"**
- `n_sessionwf → n_pipewf` — **"start_pipeline → SPAWNS child workflow (only when a build is needed)"** — bold purple,
  strokeWidth 3
- `n_containers → n_ext_github` — **"clone / push"**; `n_containers → n_ext_anth` — **"Claude Code CLI"** (dashed)
- `n_pipewf → n_temporal` — **"durable history"** (dashed); `n_sessionwf → n_temporal` — unlabeled (dashed)
- `n_activities → n_mongo` — **"write-through projections"** (dashed)
- `n_persona → n_skillexec` — **"gRPC callback start_cloud_skill"** (dashed) — arbiter calls BACK into tacticl-core
- `n_vault → c_arbiter` — **"secrets / verify voice token"** (dashed)

---

## (3) One orchestrator spawns a child PipelineWorkflow

This is the spine of the whole design, and it is one specific edge in both diagrams (`a_sessionwf → a_pipewf` /
`n_sessionwf → n_pipewf`, labeled **"start_pipeline → SPAWNS child workflow (only when a build is needed)"**).

```
 SessionWorkflow (the ONE brain — durable, one per conversation)
   │
   │  ... handles every turn: transcript → route → persona → narrate ...
   │  ... a conversation can run a long time with ZERO builds ...
   │
   ├── (turn that needs no build) ──► persona answers, NO child spawned
   │
   ├── start_pipeline ──► spawns ──► PipelineWorkflow #1  (child, durable, SILENT)
   │                                   ├─ Activities (SubmitToArbiter / InvokeArbiterRole / PersistEvent / FanOut)
   │                                   ├─ Ephemeral containers (Claude Code CLI, ultracode, 12 PDLC roles)
   │                                   └─ emits TEXT events ──► back up to SessionWorkflow ──► persona narrates ──► TTS
   │
   └── start_pipeline (later turn) ──► spawns ──► PipelineWorkflow #2  (another child)
                                                   (0..N children per conversation — cardinality is why
                                                    a single mega-workflow was REJECTED; see 00-session-decisions §3)
```

Key properties (consistent with `00-session-decisions.md` §3, do not re-derive there):

- **One brain, not two planes.** The old "conversation plane / pipeline plane" framing is gone. There is one
  `SessionWorkflow`; `PipelineWorkflow` is its **child**, not a peer orchestrator.
- **Spawned on demand, 0..N per conversation.** A conversation may produce zero builds (pure chat/clarification) or
  many. Each build is its own child workflow → independent failure isolation, independent durable history, no
  conversation-history bloat. This cardinality is exactly why "one mega-workflow doing chat + build" (Option A) was
  rejected.
- **Both are durable Temporal workflows** persisted to the Temporal cluster (Postgres). The dashed `durable history`
  edges from both `SessionWorkflow` and `PipelineWorkflow` to the Temporal cluster encode this. The session surviving
  arbiter restart / WS reconnect with full context is the explicitly-valued reason the conversation itself is durable.
- **Projections, not source of truth.** `Activities → MongoDB` is labeled **"write-through projections"** — the
  workflow history is authoritative; Mongo (`pipeline_runs`/`pipeline_events`/`checkpoints`/`sparks`/etc.) is a
  read-side projection the product backends serve over REST.

---

## (4) Where Deepgram / ElevenLabs attach (conversation only)

Both bridges are drawn **inside the Orchestrator band** and are labeled, verbatim, **"Deepgram bridge (STT) — I/O
adapter"** and **"ElevenLabs bridge (TTS) — I/O adapter"** (red fill, the only red boxes inside the arbiter). They do
**not** appear anywhere in the `PipelineWorkflow` band, whose title explicitly says **"SILENT (no audio)."**

```
  USER SPEAKS                                                         SPHERE SPEAKS
      │ mic audio                                                          ▲ audio
      ▼   (Voice WS, DIRECT, token-signed)                                 │
 ┌─────────────┐   transcript   ┌──────────────┐  route   ┌─────────────┐ │ text deltas
 │ Voice WS    │───signal──────►│ Session      │──turn───►│ PersonaRouter│ │
 │ endpoint    │                │ Workflow     │          │ (pure fn)    │ │
 └─────────────┘                └──────────────┘          └──────┬───────┘ │
      ▲                                                          │ invoke   │
      │ audio ↔ transcript                                       ▼ persona  │
 ┌─────────────────────┐  (red)        ┌──────────────────┐  stream  ┌──────┴──────┐
 │ Deepgram bridge(STT)│◄──────────────│ Anthropic stream │◄─────────│  (continua- │
 │  — I/O adapter      │  ┌────────────│ (persona resp.)  │          │   tion)     │
 └─────────────────────┘  │            └──────────────────┘          └─────────────┘
      ▲                   │ text ↔ audio (red)
      │ audio↔transcript  ▼
 [Deepgram STT]   ┌─────────────────────────┐
 (cloud)          │ ElevenLabs bridge (TTS) │◄── persona text_delta ──► [ElevenLabs TTS] (cloud)
                  │  — I/O adapter          │
                  └─────────────────────────┘

  ── The pipeline (RING 3) is BELOW this whole picture and is reached only via start_pipeline. ──
  ── It emits TEXT events; the persona narrates them; ElevenLabs voices the narration.          ──
  ── There is NO Deepgram/ElevenLabs box, and no audio, inside the pipeline.                     ──
```

End-to-end voice shape (matches `00-session-decisions.md` §8 verbatim flow):

```
mic → Deepgram STT → text → SessionWorkflow → PersonaRouter → persona (Anthropic stream)
    → persona decides to build → start_pipeline → [SILENT child PipelineWorkflow runs containers]
    → pipeline emits TEXT events → persona narrates them → ElevenLabs TTS → voice sphere speaks
```

**Why it matters for the migration:** when scaffolding the arbiter side, the Deepgram and ElevenLabs bridges belong to
the conversation/orchestrator module, wired to `Voice WS endpoint` ↔ `SessionWorkflow` ↔ `Anthropic streaming`. Do
**not** add any audio dependency to the `PipelineWorkflow`, its activities, or the containers. If a pipeline ever needs
to "speak," it does so by emitting a text event that bubbles up to the persona, which speaks via the conversation
layer's TTS — never by the pipeline calling ElevenLabs itself.

---

## Cross-references

- **Decisions / rationale:** `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/handover/2026-05-30-orchestrator-migration/00-session-decisions.md`
  (Option B, voice-WS exception, productId scoping, ULTRA execution mode, corrections owed).
- **Learning-layer preservation:** `00-session-decisions.md` §12 references
  `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/architecture/learning-layer-and-codegen-prompts-preservation.md`
  — **verified to EXIST** in the architecture dir this session (see the `ls` listing above).
- **Editable diagram source:** the two `.drawio` files listed in "Source of truth" above; their `.drawio.png` exports
  exist alongside them. Re-export the PNG after any `.drawio` edit.
