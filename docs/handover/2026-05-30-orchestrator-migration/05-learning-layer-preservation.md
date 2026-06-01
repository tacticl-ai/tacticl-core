# 05 — Learning Layer & Codegen Prompt Preservation

> Part of the orchestrator-migration handover (2026-05-30). For the architectural
> decisions driving this migration, see `00-session-decisions.md` in this same
> directory — this section does **not** restate them.
>
> Companion deep-dive (same conclusions, more file-level detail):
> `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/architecture/learning-layer-and-codegen-prompts-preservation.md`
> This handover section is **fully re-verified against source** (`cidadel-ai-arbiter`
> on `main`, 2026-05-30) and **corrects two facts** the companion doc left vague —
> see "Corrections" below.

---

## Verdict (one line)

**The learning layer and codegen/role prompts are NOT at risk from this
migration.** The accumulated value is **data** (Mongo collection `agent_knowledge`
+ Qdrant collection `tacticl_knowledge`), it lives in `cidadel-ai-arbiter` — the
*destination* this migration builds *into*, not the tacticl-core code being torn
down — and it has been committed since **2026-04-03**. The producer and consumer
of that data are **entirely internal to the arbiter**, reached through the gRPC
pipeline entrypoint the workflow already calls — so the migration's exposure is
smaller than first feared.

### Re-verification status (all CONFIRMED FROM SOURCE this session)

Every claim below was confirmed by reading the actual arbiter files on
2026-05-30. Paths are absolute; arbiter root is
`/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter`.

| Claim | Evidence |
|---|---|
| Mongo collection name is `agent_knowledge` | `…/packages/server/src/shell/knowledge-loader.ts:48` (`.collection('agent_knowledge')`); `…/retro/learning-proposer.ts:71` (`.collection('agent_knowledge').insertMany(docs)`) |
| Qdrant collection name is `tacticl_knowledge` | `…/packages/server/src/shell/qdrant-client.ts:3` (`const COLLECTION = 'tacticl_knowledge'`) |
| KnowledgeLoader returns `{ authored, learned }` | `…/shell/knowledge-loader.ts:28-34` |
| `authored` = registry-loaded markdown | `…/shell/knowledge-loader.ts:36-42` → `registry.getKnowledgeFiles(product, agentDef.knowledge_files)`; registry impl `…/shell/registry-cache.ts:75-78` + `registry-client.ts` |
| `learned` = Mongo `agent_knowledge`, `status:'approved'`, matching `agent_types` | `…/shell/knowledge-loader.ts:44-60` |
| KnowledgeIndexer embeds + upserts to Qdrant; id = SHA-256 of `product:path`; roles from frontmatter | `…/shell/knowledge-indexer.ts` (whole file, esp. lines 23, 30-43, 47-53) |
| LearningProposer writes `status:'proposed'`, `proposed_by:'retro-agent'` | `…/retro/learning-proposer.ts:43-75` |
| RetroAgent owns the proposer and calls `propose()` | `…/retro/retro-agent.ts:54,60,96` |
| Committed since 2026-04-03; not dirty | `git log --follow` (oldest commits `c6fbc57` loader, `c8374f0` retro, both 2026-04-03; indexer `b42ef41` 2026-04-18); `git status --porcelain` on shell/ + retro/ returns empty |

---

## Corrections vs. the companion doc

1. **Qdrant collection name.** The companion doc calls it "the Qdrant collection"
   generically. The literal name is **`tacticl_knowledge`**
   (`qdrant-client.ts:3`). Vectors: size default **1536**, distance **Cosine**
   (`qdrant-client.ts:35,44-46`). Use the exact name in any DO-NOT-DROP / infra
   note.

2. **The producer/consumer boundary is ENTIRELY INSIDE THE ARBITER — neither is
   driven by the tacticl-core orchestrator.** This is the single most
   migration-relevant fact and the companion doc does not state it explicitly:
   - **Consumer:** `KnowledgeLoader` is instantiated once in
     `…/packages/server/src/server.ts:282`
     (`new KnowledgeLoader(registryCache, mongoDb)`) and consumed by
     **`WorkspaceAssembler`** (`…/shell/workspace-assembler.ts:43,51,82,303` —
     `this.knowledge.loadForAgent(req.product, req.agentType)`). It is **not**
     called from tacticl-core. When the arbiter assembles a per-role workspace it
     loads + injects knowledge automatically.
   - **Producer:** `RetroAgent` constructs its own `LearningProposer`
     (`retro-agent.ts:60`) and calls `propose()` (`retro-agent.ts:96`). It is a
     **weekly / filesystem-archive scan** of completed workspace archives
     (`retro-agent.ts:38-52` docstring), **not** a synchronous end-of-pipeline
     step.
   - **Implication:** the new tacticl-core `PipelineWorkflow` does **not** (and
     should not) call `KnowledgeLoader` or `LearningProposer` directly. It must
     simply **keep invoking the arbiter's gRPC pipeline entrypoint** the way the
     current pipeline does, so the arbiter's `WorkspaceAssembler` continues to
     inject knowledge. The re-wiring risk is therefore "don't bypass the arbiter
     /assemble-workspace path," not "re-create loader/proposer call sites in
     Java." See action items 2–3.

---

## 1. WHERE the accumulated value lives (NOT in source)

The `.ts` files are **machinery**. The accumulated *value* is in two data stores:

1. **MongoDB collection `agent_knowledge`** — the durable, product-scoped corpus
   of learned knowledge entries. Both `proposed` (written by `RetroAgent` →
   `LearningProposer`) and `approved` (the ones `KnowledgeLoader` injects).
   Document fields seen in source: `product`, `category`, `agent_types: string[]`,
   `content`, `evidence`, `risk`, `status`, `proposed_at`/`approved_at`,
   `proposed_by`. (`learning-proposer.ts:19-29,59-69`; `knowledge-loader.ts:11-18,52-59`.)

2. **Qdrant collection `tacticl_knowledge`** — embeddings of authored + learned
   knowledge for semantic retrieval. Per-point payload: `content`, `product`,
   `visibility` (`global`|`user`), `userId`, `role`, `source`
   (`qdrant-client.ts:49-64`). Populated by `KnowledgeIndexer`; embeddings from
   `…/shell/embedding-client.ts`.

A third asset: **authored prompt/knowledge markdown**, resolved per product via
the **registry** (`getKnowledgeFiles`), not from any tacticl-core file — §3.

**Why this framing matters:** a *code* migration of the orchestration layer
cannot, by itself, destroy data in Mongo/Qdrant. The only ways to lose the value
are operational: drop the `agent_knowledge` collection, delete/recreate the
`tacticl_knowledge` Qdrant collection without re-indexing, or lose the registry's
authored files.

---

## 2. WHY it's safe

1. **It's on the GROWING side.** This migration **deletes Java pipeline/
   orchestration code in `tacticl-core`** and **builds into `cidadel-ai-arbiter`**.
   The learning layer already lives in the arbiter — the destination, not the
   demolition site. None of the tacticl-core modules being removed are the
   knowledge/retro code.
2. **It's committed since 2026-04-03** (git, confirmed) and the working tree is
   clean for `shell/` and `retro/` (`git status --porcelain` empty). Not scratch
   work — protected by history.
3. **The value is Mongo + Qdrant data, independent of any code rewrite** (§1).
4. **It's self-contained inside the arbiter** (loader → `WorkspaceAssembler`;
   `RetroAgent` → proposer). The tacticl-core orchestration rebuild cannot reach
   in and remove these wirings; it can only stop *triggering the arbiter* — which
   it won't, because the pipeline still runs through the arbiter (see
   `00-session-decisions.md`).

---

## 3. The components (verified file inventory)

All paths relative to `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/`.

| Component | Path | Role |
|---|---|---|
| **KnowledgeLoader** (consumer) | `packages/server/src/shell/knowledge-loader.ts` | `loadForAgent(product, agentType)` → `{ authored, learned }`. `authored` = registry markdown; `learned` = Mongo `agent_knowledge` (approved, matching `agent_types`). Wired in `server.ts:282`; consumed by `WorkspaceAssembler`. |
| **WorkspaceAssembler** (consumer call site) | `packages/server/src/shell/workspace-assembler.ts` | Calls `knowledge.loadForAgent(...)` (lines 82, 303) when building a per-role workspace; folds knowledge into the role's context. **This is the injection point.** |
| **KnowledgeIndexer** (producer) | `packages/server/src/shell/knowledge-indexer.ts` | `indexFiles(...)`: embeds files, upserts to Qdrant `tacticl_knowledge`. Idempotent point id = SHA-256(`product:path`). Roles parsed from `--- roles:[...] ---` frontmatter. |
| **LearningProposer** (producer) | `packages/server/src/retro/learning-proposer.ts` | `propose(product, learnings[])` → inserts into Mongo `agent_knowledge` (`status:'proposed'`, `proposed_by:'retro-agent'`). 6 categories (move_to_shell, knowledge_gap, template_improvement, agent_behavior, pipeline_optimization, failure_pattern). |
| **RetroAgent** (producer driver) | `packages/server/src/retro/retro-agent.ts` (+ `retro-boot.md`) | **Weekly** scan of completed workspace archives on the filesystem; produces learnings → `LearningProposer.propose()` (line 96). Not a per-run hook. |
| **Qdrant client** | `packages/server/src/shell/qdrant-client.ts` | `KnowledgeStore`: `ensureCollection`/`upsert`/`query` against collection `tacticl_knowledge` (size 1536, Cosine). |
| **Embedding client** | `packages/server/src/shell/embedding-client.ts` | Multi-provider embeddings (per arbiter CLAUDE.md: fastembed/openai/voyage/ollama). |
| **Registry (authored-prompt source)** | `packages/server/src/shell/registry-cache.ts`, `registry-client.ts` | Resolves per-product agent definitions + `getKnowledgeFiles(product, files)`. **Where authored role/codegen prompt assets resolve from.** |
| **Pipeline tracker** | `packages/server/src/shell/pipeline-tracker.ts` | Tracks PDLC role progress; emits events to product shells. |
| **Index script** | `scripts/index-knowledge.ts` (+ `run-initial-index.sh`) | Bulk (re-)index knowledge into Qdrant. |
| **Knowledge migration** | `scripts/migrate-knowledge-firestore-to-mongo.ts` | One-time Firestore→Mongo migration (already run; learning store moved to Hetzner Mongo in commit `87cb070`, 2026-04-11). |
| **Tests (CONFIRMED present)** | `packages/server/tests/shell/knowledge-loader.test.ts`, `packages/server/tests/shell/knowledge-indexer.test.ts`, `packages/server/tests/shell/qdrant-client.test.ts`, `packages/server/tests/shell/embedding-client.test.ts`, `packages/server/tests/retro/retro-agent.test.ts`, `packages/server/tests/shell/workspace-assembler.test.ts` | Regression coverage for the whole loop. |

### The loop (verified)

```
[periodically — RetroAgent runs WEEKLY over archived workspaces]
   RetroAgent (retro/retro-agent.ts) scans completed workspace archives
   → LearningProposer.propose(product, learnings)   → Mongo agent_knowledge (status:'proposed')
   → human review flips status → 'approved'
   → KnowledgeIndexer.indexFiles(...)                → Qdrant tacticl_knowledge
                                                        ↑ producer (arbiter-internal)
                                                        ↓ consumer (arbiter-internal)
[every per-role workspace assembly, inside the arbiter]
   WorkspaceAssembler → KnowledgeLoader.loadForAgent(product, agentType)
       authored ← registry.getKnowledgeFiles(...)            (codegen/role prompts)
       learned  ← Mongo agent_knowledge (approved, agentType) (accumulated lessons)
   → both folded into the role's workspace/system context
```

### Codegen prompt preservation specifically

Authored codegen/role prompts load through the **arbiter registry**
(`getKnowledgeFiles` over `agentDef.knowledge_files`) — a **separate** source from
the tacticl-core `role-identities/*.md` files this migration is deleting (git
status shows `D role-identities/pm.md`, `A role-identities/po.md`; tacticl-core
CLAUDE.md says that markdown is removed and persona/role content "lives in
Mongo"). These are **two different prompt sources in two different repos**: the
tacticl-core persona-prompt-in-Mongo change does **not** replace the arbiter's
authored-prompt registry. Deleting tacticl-core role-identity markdown must not be
assumed to touch the arbiter registry (cross-repo — heed MEMORY
`feedback_cross_repo_context.md`).

---

## 4. Action items (for the fresh session / migration plan)

1. **DO-NOT-DROP — Mongo `agent_knowledge` + Qdrant `tacticl_knowledge`.** These
   two stores are the irreplaceable asset. No migration step, infra teardown, DB
   rename, or environment re-provision may drop them. Add an explicit DO-NOT-DROP
   entry (with both literal names) to the migration plan's risk table — it
   currently has none for this.

2. **Keep the pipeline running THROUGH the arbiter so knowledge injection
   survives.** Because retrieval is arbiter-internal (`WorkspaceAssembler` →
   `KnowledgeLoader`), the new tacticl-core `PipelineWorkflow` must **continue to
   invoke the arbiter's gRPC pipeline entrypoint** (the path that builds per-role
   workspaces). Do **not** introduce a code path that runs a role/codegen agent
   while bypassing `WorkspaceAssembler` — that would silently drop both authored
   and learned knowledge from the prompt. There is **no** Java-side
   `loadForAgent` call to re-create; the requirement is "don't bypass the arbiter
   assemble path."

3. **Don't break the weekly retro loop.** `RetroAgent` is a scheduled,
   archive-scanning job inside the arbiter — verify the migration keeps (a)
   workspace archives being written (so there's something to scan) and (b) the
   retro schedule wired. It is **not** an end-of-pipeline step the workflow needs
   to call; the risk is the migration disabling the archiver or the schedule,
   which would make `agent_knowledge` stop growing (a silent, read-still-works
   regression).

4. **Keep the knowledge tests green.** Ensure these pass throughout the
   migration: `tests/shell/knowledge-loader.test.ts`,
   `tests/shell/knowledge-indexer.test.ts`, `tests/shell/qdrant-client.test.ts`,
   `tests/shell/embedding-client.test.ts`, `tests/retro/retro-agent.test.ts`,
   `tests/shell/workspace-assembler.test.ts`. They are the regression guard that
   the loop in §3 still works.

5. **Re-index Qdrant ONLY IF embeddings change.** Per arbiter CLAUDE.md, changing
   the embedding provider/model/dimension forces recreating the
   `tacticl_knowledge` collection (query vectors must match index vectors;
   `qdrant-client.ts` hardcodes size at construction). If the migration touches
   embeddings, run `scripts/index-knowledge.ts` to rebuild Qdrant from the Mongo
   `agent_knowledge` source of truth (a rebuild, not data loss). If embeddings are
   **unchanged, do NOT re-index** — needless and risks corrupting a healthy index.

6. **Treat these files as DO-NOT-REWRITE-BLINDLY** when building the new
   workflow: `shell/knowledge-loader.ts`, `shell/knowledge-indexer.ts`,
   `shell/qdrant-client.ts`, `shell/embedding-client.ts`, `shell/registry-cache.ts`,
   `shell/registry-client.ts`, `shell/workspace-assembler.ts`,
   `shell/pipeline-tracker.ts`, `retro/learning-proposer.ts`,
   `retro/retro-agent.ts`, `retro/retro-boot.md`, `scripts/index-knowledge.ts`,
   `scripts/migrate-knowledge-firestore-to-mongo.ts`, and the tests in item 4.
   Reuse/re-wire; do not regenerate.

---

## Bottom line

- **Value is in data** — Mongo `agent_knowledge` + Qdrant `tacticl_knowledge`
  (both names source-confirmed) — **not** in the source the migration touches.
  That's why it survives a code migration. Committed since 2026-04-03.
- **The loop is arbiter-internal**: `WorkspaceAssembler` → `KnowledgeLoader`
  (read/inject) and `RetroAgent` → `LearningProposer` → `agent_knowledge` →
  `KnowledgeIndexer` (weekly write). The tacticl-core workflow neither owns nor
  needs to call these directly.
- **The real risk is bypass/disable, not deletion:** keep running the pipeline
  through the arbiter's workspace-assembly path (preserves reads) and keep the
  archiver + weekly retro schedule alive (preserves writes). Both failure modes
  are silent.
- **DO-NOT-DROP:** Mongo `agent_knowledge` and Qdrant `tacticl_knowledge`.
