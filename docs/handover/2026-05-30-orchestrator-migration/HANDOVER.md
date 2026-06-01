# HANDOVER — Orchestrator Migration (Option B)

**Folder:** `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/handover/2026-05-30-orchestrator-migration`
**Date:** 2026-05-30 · **Decision owner:** lead (session) · **Status:** planning locked, execution not yet started

## Executive summary

This handover covers the **Option B** migration: the **orchestrator + PDLC pipeline engine** move out of
**tacticl-core** (Java) and into **cidadel-ai-arbiter** (TypeScript). The arbiter becomes the durable
brain — Temporal-backed `CloudAgentSessionWorkflow`, the pure-function `PersonaRouter`, the Mongo-backed
persona/skill registries, the PDLC `PipelineWorkflow`, and the voice-plane bridges (Deepgram STT,
ElevenLabs TTS). **tacticl-core stays a full product backend** — it keeps the `/v1/*` REST API, auth
(PASETO), social publishing, spark CRUD, and device dispatch. It is NOT being thinned to a client; it
simply stops running orchestration in-JVM and instead **signals** the arbiter. Option A (Temporal inside
tacticl-core) and Option C (full rewrite into arbiter) were both rejected.

This handover exists because a prior planning session hit **Anthropic API thinking-block errors** mid-flight
and could not finish cleanly, so the locked decisions and current state were written out to disk as section
files to survive the context loss. **Use this folder as the map:** start with `HANDOVER.md` (this file),
then read the numbered sections in order. `00-session-decisions.md` is the authoritative, locked record —
if any other file contradicts it, 00 wins. Detail lives in the section files; this file is navigational only.

## READ IN THIS ORDER

| # | File | What it is |
|---|------|------------|
| 0 | [./00-session-decisions.md](./00-session-decisions.md) | **CANONICAL / LOCKED** — the authoritative decisions. Read first; do not re-litigate. |
| 1 | [./01-migration-plan-summary.md](./01-migration-plan-summary.md) | High-level phased migration plan + the §3 corrections owed to the full plan. |
| 2 | [./02-arbiter-current-state.md](./02-arbiter-current-state.md) | cidadel-ai-arbiter as the migration target — what exists, what's missing. |
| 3 | [./03-tacticl-core-current-state.md](./03-tacticl-core-current-state.md) | tacticl-core as the source — what stays, what moves, in-flight git changes. |
| 4 | [./04-prd-sad-canonical-decisions.md](./04-prd-sad-canonical-decisions.md) | Distilled PRD/SAD decisions reconciled with Option B (personas, roles, playbooks). |
| 5 | [./05-learning-layer-preservation.md](./05-learning-layer-preservation.md) | **CRITICAL** — Mongo `agent_knowledge` + Qdrant must be preserved, not dropped. |
| 6 | [./06-ecosystem-context.md](./06-ecosystem-context.md) | Cross-repo map, shared platform, deploy targets, cross-cutting rules. |
| 7 | [./07-architecture-prose-and-ascii.md](./07-architecture-prose-and-ascii.md) | Target architecture — prose + ASCII diagram of the two planes. |
| 8 | [./08-memory-and-docs-index.md](./08-memory-and-docs-index.md) | Pointers to user memory + canonical PRD/SAD/plan docs to load. |
| 9 | [./09-risks-and-open-questions.md](./09-risks-and-open-questions.md) | DO-THIS-FIRST checklist, risks, open questions, follow-ups. |

## TL;DR — the locked decisions

(distilled from [./00-session-decisions.md](./00-session-decisions.md) — that file is canonical)

- **Option B** chosen: orchestrator + pipeline engine move to **cidadel-ai-arbiter** (TS); tacticl-core stays a full product backend.
- Option A (Temporal in tacticl-core) and Option C (full rewrite) are **rejected** — do not revisit.
- **Moves to arbiter:** Cloud Agent Orchestrator, PDLC `PipelineWorkflow` + role activities, persona/skill registries, voice bridges, Temporal worker(s).
- **Stays in tacticl-core:** `/v1/*` REST, social + publishing, spark CRUD + device WS dispatch, PASETO auth, product data.
- **Learning layer (Mongo `agent_knowledge` + Qdrant) MUST be preserved** — migrate as-is, no schema reset, no data loss. Hard constraint, flagged twice.
- **Orchestration is Temporal-backed** — full Postgres-backed cluster adopted now (deferral overridden).
- One `CloudAgentSessionWorkflow` per conversation; `PipelineWorkflow` is its child workflow.
- **`PersonaRouter` is a pure function** (hard rules + intent regex, no LLM); 14 personas (2 conversational + 12 PDLC), ~15 shared skills.
- **PM→PO rename** in progress (`pm.md`→`po.md`); 12 PDLC roles; 8 playbooks move to Mongo.
- **Neither build may go red** — tacticl-core (Gradle) and arbiter (tsc) stay green; land behind flags, keep old path until new path is proven.
- **No feature branches** — single-person team, commit directly to `main` on every repo.
- **Subagents run on opus**; deep review is `/code-review ultra` (cloud) — the **lead** launches it, a subagent must not.

## DO THIS FIRST (fresh session)

(from [./09-risks-and-open-questions.md](./09-risks-and-open-questions.md))

1. Read [./00-session-decisions.md](./00-session-decisions.md) (locked decisions).
2. Read the rest of this folder in order (01 → 09).
3. Load user memory: `MEMORY.md` + the entries listed in [./08-memory-and-docs-index.md](./08-memory-and-docs-index.md).
4. Read the canonical PRD/SAD/plan (paths in [./04-prd-sad-canonical-decisions.md](./04-prd-sad-canonical-decisions.md) / [./08-memory-and-docs-index.md](./08-memory-and-docs-index.md)).
5. Read `tacticl-docs/CLAUDE.md` for ecosystem context.
6. Confirm **both builds are green** before changing anything (Gradle + arbiter tsc).
7. Apply the **CORRECTIONS OWED** (see next section).
8. Only then resume phasing from where the plan left off.

## CORRECTIONS OWED

These are known-wrong items from the prior session that must be fixed:

- **Plan §3 "shell" terminology** — the plan calls tacticl-core a "product shell." Per the locked decision
  (00 §2), there is no "shell": rewrite as **"full product backend (of any size)."** Only LLM/AI work routes
  to arbiter; the product keeps everything else. (See [./00-session-decisions.md](./00-session-decisions.md) §2.)
- **Plan §3 missing learning-layer preservation** — add a "Preserve & integrate (arbiter learning layer)"
  subsection + a **DO-NOT-DROP list** (Mongo `agent_knowledge` + Qdrant) and the re-wiring deliverable
  (KnowledgeLoader + RetroAgent/LearningProposer into the new PipelineWorkflow).
  (See [./05-learning-layer-preservation.md](./05-learning-layer-preservation.md) §4.)
- **Handoff/execution prompt line is wrong** — it says tacticl-core becomes a "slimmed but substantial shell."
  Correct to **"tacticl-core stays a full product backend; only the LLM/orchestration engine moves to arbiter."**
  The learning layer is **preserved + re-wired**, never deleted.
- **Memory update owed** — add a memory entry recording the Option B decision, the learning-layer
  constraint, and this handover folder path so a fresh session finds it.
  (See [./08-memory-and-docs-index.md](./08-memory-and-docs-index.md).)

## DO NOT

- **Do NOT** drop, truncate, re-init, or re-embed the learning layer — Mongo `agent_knowledge` and the
  Qdrant collection are durable user data. Migrate 1:1; reconnect, don't rebuild.
- **Do NOT** create feature branches — commit directly to `main`.
- **Do NOT** re-litigate the locked decisions in [./00-session-decisions.md](./00-session-decisions.md)
  (Option B is chosen; A and C are rejected).
- **Do NOT** let either build go red — keep Gradle (tacticl-core) and tsc (arbiter) green; gate new
  paths behind flags and keep the old path until the new one is proven.
- **Do NOT** try to launch `/code-review ultra` yourself (subagent) — only the lead launches the cloud
  multi-agent review.
