# Learning Layer & Codegen Prompts — Preservation Review

**Date:** 2026-05-30
**Context:** Pipeline overhaul (orchestrator → arbiter migration, plan `2026-05-30-orchestrator-migrate-to-arbiter.md`).
**Concern raised:** *"we created a learning layer and all the codegen prompts — that cannot be lost while we overhaul the pipeline."*

**Verdict: NOT at risk.** It is old, committed, lives on the *destination* side of the migration, and the accumulated value is data in Mongo + Qdrant — not source code. There is exactly **one** real risk (re-wiring), captured in §4.

> ⚠️ All names/paths below were verified by reading the files on `main` in `cidadel-ai-arbiter` on 2026-05-30. An earlier draft of this doc guessed collection/file names — those were wrong and have been corrected here.

---

## 1. What exists today (verified, committed on `main`)

These are **committed** (tracked, not in `git status`) — i.e. safe in git history, not dangling work. And they are **not new**: git dates them to early–mid April 2026.

| Asset | Path (arbiter) | What it does | First committed |
|---|---|---|---|
| **KnowledgeLoader** | `packages/server/src/shell/knowledge-loader.ts` | `loadForAgent(product, agentType)` returns `{ authored, learned }`. **authored** = prompt/knowledge markdown loaded via the **registry** (`registry.getKnowledgeFiles(product, agentDef.knowledge_files)`). **learned** = docs from Mongo `agent_knowledge` filtered by `{ product, status:'approved', agent_types: agentType }`. | 2026-04-03 |
| **KnowledgeIndexer** | `packages/server/src/shell/knowledge-indexer.ts` | Embeds files and upserts into the **Qdrant** knowledge store (`qdrant-client.ts`), scoped by `product`, `visibility` (global/user), `role`. Idempotent id = SHA-256 of `product:path`. Roles parsed from `--- roles: [...] ---` frontmatter. | 2026-04-18 |
| **LearningProposer** | `packages/server/src/retro/learning-proposer.ts` | `propose(product, learnings[])` writes to Mongo **`agent_knowledge`** with `status:'proposed'`, `proposed_by:'retro-agent'`, product discriminator. Categories: `move_to_shell`, `knowledge_gap`, `template_improvement`, `agent_behavior`, `pipeline_optimization`, `failure_pattern`. | 2026-04-03 |
| **RetroAgent** | `packages/server/src/retro/retro-agent.ts` (+ `retro-boot.md`) | Post-run / weekly workspace scan that produces the proposed learnings fed to LearningProposer. | 2026-04-03 |
| **Qdrant client** | `packages/server/src/shell/qdrant-client.ts` | Vector store interface (`KnowledgeStore`) — `ensureCollection`, `upsert`, search. | — |
| **Embedding client** | `packages/server/src/shell/embedding-client.ts` | Multi-provider embeddings (fastembed/openai/voyage/ollama) — see arbiter CLAUDE.md. | 2026-04-19 |
| **Registry (prompt source)** | `packages/server/src/shell/registry-cache.ts`, `registry-client.ts` | Resolves per-product agent definitions + their `knowledge_files` (the authored prompts/knowledge). **This is where role/codegen prompt assets resolve from — there is no `pipeline/role-prompts.ts`.** | — |
| **Pipeline tracker** | `packages/server/src/shell/pipeline-tracker.ts` | Tracks PDLC role progress; emits events to product shells (already handles `skip_roles`). | — |
| **Index script** | `scripts/index-knowledge.ts` (+ `run-initial-index.sh`) | Bulk-index knowledge into Qdrant. | — |
| **Knowledge migration** | `scripts/migrate-knowledge-firestore-to-mongo.ts` | One-time Firestore → Mongo migration of learned knowledge. | 2026-04-11 |
| **Tests** | `packages/server/tests/shell/knowledge-loader.test.ts`, `knowledge-indexer.test.ts` | Regression coverage for loader/indexer. | — |

Confirmed by arbiter `CLAUDE.md`: configurable embedding providers, Qdrant knowledge store, Vault provider secrets. Related design history: tacticl-core plans `2026-04-13-tacticl-knowledge-vault-init.md`, `2026-04-17-knowledge-infrastructure.md`.

## 2. Where the actual accumulated value lives (survives any code rewrite)

The code above is **machinery**. The accumulated *value* lives in **data stores**, not source files:

- **Mongo `agent_knowledge`** — the durable, product-scoped learned-knowledge corpus (both `proposed` and `approved`).
- **Qdrant collection** — the embedded/vector index of authored + learned knowledge for retrieval.
- **Authored prompt/knowledge markdown** — resolved through the registry per product (`knowledge_files`).

A *code* overhaul cannot lose this. The only ways to lose it: (a) drop the Mongo `agent_knowledge` collection, (b) delete/recreate the Qdrant collection, or (c) lose the registry's authored files. The migration must do none of these.

> Note: per arbiter CLAUDE.md, **changing the embedding provider/model forces a Qdrant collection recreate** (vector dims must match). If the overhaul touches embeddings, re-run `scripts/index-knowledge.ts` afterward to rebuild the vector index — the Mongo `agent_knowledge` source of truth is unaffected, so this is a rebuild, not a data loss.

## 3. Why the migration does not threaten this

1. **It's on the GROWING side.** The migration **deletes Java pipeline code in `tacticl-core`** and **builds into `cidadel-ai-arbiter`**. The learning layer is already in arbiter — the *destination*, not the demolition site. (The plan's "thrown away" list, §3/§5, is exclusively tacticl-core Java modules: `business-cloud-orchestrator`, `business-voice`, `client-deepgram`, `client-elevenlabs`, `service-cloud-orchestrator`, the Java Temporal bootstrap. None of it is the knowledge/retro code.)
2. **It's committed to `main` since April.** Git history protects it; it is not uncommitted scratch work.
3. **The value is Mongo + Qdrant data**, independent of code (§2).

## 4. The ONE real risk — and the actions that neutralize it

**Risk:** the rebuilt orchestrator / `PipelineWorkflow` gets written "fresh" and **forgets to call** `KnowledgeLoader` (prompt augmentation) and `LearningProposer`/`RetroAgent` (learning capture). Nothing is deleted — the capability just silently goes dark: pipelines stop benefiting from accumulated knowledge and stop capturing new learnings. This is invisible until someone notices quality regressed.

**Actions (add these to the migration plan explicitly):**

- [ ] Add a **"Preserve & integrate (arbiter learning layer)"** subsection to the plan's §3. Name these files as DO-NOT-REWRITE-BLINDLY: `shell/knowledge-loader.ts`, `shell/knowledge-indexer.ts`, `shell/qdrant-client.ts`, `shell/embedding-client.ts`, `shell/registry-cache.ts`, `shell/registry-client.ts`, `shell/pipeline-tracker.ts`, `retro/learning-proposer.ts`, `retro/retro-agent.ts`, `retro/retro-boot.md`, `scripts/index-knowledge.ts`, `scripts/migrate-knowledge-firestore-to-mongo.ts`, and the two knowledge tests.
- [ ] New `PipelineWorkflow`, per role invocation, must call `KnowledgeLoader.loadForAgent(productId, agentType)` and fold `authored` + `learned` into that role's system prompt (alongside the persona/role markdown the plan copies into `packages/personas/`).
- [ ] RETRO_ANALYST step in the new pipeline must run `RetroAgent` → `LearningProposer.propose(productId, ...)` on run completion.
- [ ] **DO-NOT-DROP list** for the migration: Mongo `agent_knowledge` collection + the Qdrant knowledge collection. Add to the plan's risk table (it currently has no entry for this).
- [ ] Keep `knowledge-loader.test.ts` / `knowledge-indexer.test.ts` green throughout (regression guard that wiring still works).
- [ ] If embeddings change during the overhaul: re-run `scripts/index-knowledge.ts` to rebuild Qdrant from `agent_knowledge` (§2 note).

## 5. The "strategiz today" work is separate (and also safe)

Strategiz-core's last ~30h of commits are **product-side LLM config + observability**, e.g. `feat(llm): route AI Chat + Labs model selection through console store`, `feat(preferences): configurable LLM function/model resolution`, plus alert/observability fixes. All **committed** (only `service-console/.../JobManagementService.java` is uncommitted). These are **strategiz product config**, not the shared arbiter learning layer, and are untouched by the tacticl pipeline overhaul.

**If** any strategiz prompt/learning work was meant to enrich the *shared* engine, confirm it landed in `cidadel-ai-arbiter` (product-scoped via `agent_knowledge.product` / Qdrant `product`) rather than only in strategiz-core. From what's visible, today's strategiz work is product-local config — so nothing shared is at stake.

---

**Bottom line:** the learning layer + codegen/role prompts are **committed in arbiter since April**, the accumulated value is **product-scoped data in Mongo `agent_knowledge` + Qdrant**, and the migration builds *into* arbiter rather than deleting it. The only thing to enforce: the rebuilt pipeline must **re-wire `KnowledgeLoader` + `RetroAgent`/`LearningProposer`**, and the migration must **never drop `agent_knowledge` or the Qdrant collection.** Both are now action items for the plan.
