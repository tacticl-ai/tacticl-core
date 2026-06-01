# 00 — Architectural Decisions Made This Session (2026-05-30)

> **Source:** these decisions came out of a long working conversation, NOT from files. They are the authoritative
> "locked in" set. Where they refine an existing doc, the doc must be updated to match (see §"Corrections owed" below).
> Everything here is a CONSTRAINT for the execution session, not a discussion point — raise a specific technical
> objection if one is wrong, but do not re-litigate the foundation.

## The decisions (locked)

1. **Option B, not Option C.** The orchestrator + PDLC pipeline ENGINE moves into `cidadel-ai-arbiter` (TypeScript/Node).
   `tacticl-core` (Java) stays as a **full product backend**. Option C (kill tacticl-core Java entirely) is **deferred**,
   explicitly preserved as a future path — do not expand scope toward it.

2. **There is no "shell."** Drop that word everywhere. `tacticl-core` and `strategiz-core` are **full product backends
   of whatever size the product needs.** Strategiz is enormous and keeps all of its domain. The single unifying rule:
   > **Any LLM / AI work routes through the shared arbiter. Everything else stays in the product.**
   The product is not hollowed out — only the AI engine relocates.

3. **One Orchestrator, not "two planes."** The diagram's "conversation plane / pipeline plane" was misleading. It is
   **one brain**: a durable **`SessionWorkflow`** (Temporal). When a build is needed it **SPAWNS a child
   `PipelineWorkflow`** — a durable sub-job, not a second orchestrator.
   - Rejected **A** (one mega-workflow doing chat + build) — anti-pattern (cardinality 0..N builds per conversation,
     lifecycle mismatch, history bloat, no failure isolation).
   - Chosen **B**: the conversation itself is ALSO a durable Temporal workflow. Rationale the user explicitly valued:
     **session survives arbiter restart / WS reconnect with full context, idle-timeout timers, signal-driven voice.**
   - Rejected **C** (stateless conversation, only pipeline durable) — B's durability was wanted.

4. **Voice WS is the ONLY direct browser → arbiter path.** Latency exception (<1200ms p50 target — a Java relay hop
   would blow it). Auth: a **short-lived session token signed by the product backend (tacticl-core) at session start;
   arbiter validates it via a shared signing key in Vault.** Everything else (REST, non-voice WS) goes
   browser → product backend → arbiter.

5. **Cidadel / arbiter gRPC is INTERNAL-ONLY.** Browsers NEVER call arbiter directly except the voice WS. Product
   backends (tacticl-core, strategiz-core, future) are the only gRPC callers. Same integration pattern for every
   product → arbiter can evolve its API freely without breaking browser clients.

6. **Spark writes: arbiter writes directly to shared Mongo** (projection pattern, SAD §3.7). The product backend READS
   those projections for its REST surface.

7. **PersonaRouter folds into the workflow as a pure imported function** — not a separate class/bean. (Pure fn, same
   test cases as the Java version.)

8. **Voice providers live in the Orchestrator/conversation layer ONLY. The pipeline is SILENT.** Deepgram (STT) and
   ElevenLabs (TTS) are **I/O adapters wrapping the conversation** — there is NO audio anywhere inside the pipeline.
   The end-to-end shape:
   ```
   mic → Deepgram STT → text → SessionWorkflow → PersonaRouter → persona (Anthropic stream)
       → persona decides to build → start_pipeline → [SILENT child PipelineWorkflow runs containers]
       → pipeline emits TEXT events → persona narrates them → ElevenLabs TTS → voice sphere speaks
   ```

9. **The pipeline is 100% inside arbiter/cidadel.** And conceptually: **Tacticl-the-product IS primarily the
   commercialization of the arbiter pipeline engine — that engine is what Tacticl sells.** So `tacticl-core` will
   *feel light* on the product side, BY DESIGN — it's the go-to-market / delivery wrapper (auth, channels, OAuth,
   device pairing, billing/quotas, REST projections, UX) around the engine, not the engine itself. If tacticl-core
   felt heavy after this migration, the boundary would be wrong. Contrast: **Strategiz sells its own domain**
   (market data, analytics) and uses the engine as **one capability among many.** Same shared engine, two very
   different product "weights" — that asymmetry is the proof the boundary is correct.

10. **The arbiter IS product-aware — via DATA, not forked code.** `productId` is a first-class scoping key threaded
    through the SessionWorkflow, PersonaRouter (picks that product's personas), and crucially the **knowledge store +
    learning layer are partitioned by product** (`agent_knowledge.product`, Qdrant `product` field, registry
    per-product agent definitions). The *code* stays generic and multi-tenant; the *behavior* differs by per-product
    data. **No `if (product === 'tacticl')` branches in the engine.** (User corrected an earlier overstatement of
    "agnostic" — it is product-aware, just expressed as a scoping dimension over shared machinery. The per-product
    learning store is the moat: each product's partition compounds independently.)

11. **Channels live in each product as thin signalers.**
    - **Tacticl**: Telegram bot + web chat + voice sphere (tacticl-web / tacticl-mobile).
    - **Strategiz**: Discord (writes alerts + accepts commands) + strategiz-web dashboard.
    - Channels translate inbound events into arbiter signals. `start_pipeline` is decided by **PersonaRouter inside
      arbiter**, not by the channels. Both products feed the SAME engine.

12. **Learning layer / codegen prompts are NOT at risk — but must be re-wired.** The knowledge + retro layer is
    committed in arbiter **since April 2026** (not "today's" work; today's strategiz work is separate product-side LLM
    console config). Accumulated value lives in **Mongo `agent_knowledge` + Qdrant**, not in source. The migration
    must (a) NEVER drop those stores, and (b) ensure the rebuilt `PipelineWorkflow` re-wires `KnowledgeLoader`
    (prompt augmentation) and `RetroAgent`/`LearningProposer` (learning capture). Full detail:
    `docs/architecture/learning-layer-and-codegen-prompts-preservation.md`.

## Execution mode (user directive: "ULTRA")

- Go HARD with parallelism. The migration plan's "~12 agents / 3 waves / ~1 week / pace yourself" is a **FLOOR, not a
  ceiling.** Fan out 100–300+ agents where the work has wide independent units (file transforms, scaffolding N skills).
- Width is bounded by **dependencies and correctness, not a number.** Honor real ordering (proto → generated clients →
  importing code; Wave 0 deletes → Wave 1 scaffolding). Don't serialize genuinely independent work.
- Use the **Workflow tool (ultracode)** to orchestrate: `pipeline()` over task-lists with an **adversarial-verify**
  stage per unit (a second agent that tries to REFUTE the work). Use `isolation: 'worktree'` for parallel file mutation.
- Keep BOTH build gates GREEN continuously: tacticl-core `./gradlew test`, arbiter Vitest.
- **ultrareview at wave boundaries**: at each wave end, ask the USER to run `/code-review ultra` (user-triggered +
  billed; an agent CANNOT launch it). Triage, fix confirmed findings, proceed.

## Conventions (from memory + user)

- Single-person team. **Commit directly to `main`. NO feature branches.**
- Always `model: "opus"` for subagents.
- Don't commit unless explicitly requested. Don't run destructive git ops without explicit confirmation.
- Run cross-repo commands with explicit cwd (`git -C <path>`, `(cd <path> && ./gradlew …)`) — never `cd` as global
  state change.

## Corrections owed to existing artifacts (do these early in execution)

1. **Migration plan §3** (`2026-05-30-orchestrator-migrate-to-arbiter.md`): replace "product shell" language with
   "full product backend"; ADD a "Preserve & integrate (arbiter learning layer)" subsection + a DO-NOT-DROP list for
   Mongo `agent_knowledge` and the Qdrant collection (per the preservation doc §4).
2. **The handoff/execution prompt**: the "slimmed but substantial shell" line → "tacticl-core stays a full product
   backend; only the LLM/orchestration engine moves to arbiter."
3. **Memory file** `project_cloud_agent_orchestrator.md`: update to reflect the arbiter pivot (engine in arbiter/TS,
   not tacticl-core/Java) so future sessions don't drift back to the old mental model.
4. **Diagrams** already updated to "one Orchestrator + child PipelineWorkflow" and "full product, any size"
   (`docs/architecture/*.drawio` + `.png`). Treat the `.drawio` as source of truth if regenerating.

## Open question (not yet decided)

- **Persona/skill registry scoping:** shared registry with product-tagged personas (filter by `productId`) vs.
  per-product registry namespaces. The diagrams currently assume **shared, product-tagged**. Confirm before it
  becomes canonical in the SAD.
