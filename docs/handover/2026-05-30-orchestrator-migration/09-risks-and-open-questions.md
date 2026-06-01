# 09 — Risks, Open Questions, and First Actions for the Next Session

> Companion to `00-session-decisions.md` in this same handover dir
> (`/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/handover/2026-05-30-orchestrator-migration/`),
> which captures the 12 locked architectural decisions. This section does **not** restate those — it
> surfaces what is still open, what is risky, what corrections are owed, the build/review discipline,
> and the parallelism constraints. **Read `00-session-decisions.md` first; this section assumes it.**

## Source-of-truth files (all verified by direct read this session)

| What | Absolute path | Verified |
|---|---|---|
| Locked decisions (companion) | `…/docs/handover/2026-05-30-orchestrator-migration/00-session-decisions.md` | 8964 bytes, read in full |
| **The migration plan** (authoritative execution artifact) | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md` | 55860 bytes, read in full |
| Learning-layer preservation review | `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/architecture/learning-layer-and-codegen-prompts-preservation.md` | 8998 bytes, read in full |
| Memory file to update | `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/project_cloud_agent_orchestrator.md` | exists, 14626 bytes |
| Arbiter repo (TS/Node) | `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter` | exists |

> **Naming caution:** the migration plan is `2026-05-30-orchestrator-migrate-to-arbiter.md`. It is
> **distinct from** `2026-05-25-cloud-agent-orchestrator.md` (the **historical** plan that built the
> orchestrator in tacticl-core/Java). The `2026-05-25` plan's own header now says "SUPERSEDED"-class
> status in the `2026-05-30` plan (§12). When the two disagree, the `2026-05-30` plan + the decisions
> doc win. The orchestrator/PDLC engine moves into `cidadel-ai-arbiter` (TypeScript); tacticl-core
> stays a full product backend.

---

## 1. Open question — persona / skill registry scoping (NOT decided)

Verbatim from `00-session-decisions.md` §"Open question (not yet decided)":

> **Persona/skill registry scoping:** shared registry with product-tagged personas (filter by
> `productId`) vs. per-product registry namespaces. The diagrams currently assume **shared,
> product-tagged**. Confirm before it becomes canonical in the SAD.

- **Current lean (NOT locked):** the architecture diagrams already assume **shared registry,
  product-tagged (filter by `productId`)**. This is consistent with decision §10 ("the arbiter IS
  product-aware — via DATA, not forked code … `productId` is a first-class scoping key … registry
  per-product agent definitions") and §6 (arbiter writes directly to shared Mongo). The preservation
  doc corroborates: `agent_knowledge` is already product-scoped via an `agent_knowledge.product`
  discriminator, and Qdrant via a `product` field — so a shared-but-tagged registry would match the
  existing learning-store pattern.
- **The alternative still on the table:** **per-product registry namespaces** (physically separate
  collections/namespaces per product) — trades cross-product reuse for hard isolation / smaller blast
  radius. Not ruled out.
- **Hard constraint:** must be **confirmed with the user before it is written as canonical in the
  SAD** (`docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md`). Do not let a wave task
  silently bake "shared, product-tagged" into the SAD, the Zod/Mongo schemas, or the seed migration
  without that confirmation. This is **upstream of all persona/skill registry work** (see §6).

---

## 2. Risk — learning-layer re-wiring (the ONE real risk per the preservation doc)

The preservation review (`learning-layer-and-codegen-prompts-preservation.md`) is unambiguous: the
learning layer is **NOT at risk of being lost** (it is committed in arbiter since April 2026, lives on
the *destination* side of the migration, and its accumulated value is **data** in Mongo + Qdrant, not
source). But it has **exactly one** real risk, captured in that doc's §4:

> **Risk:** the rebuilt orchestrator / `PipelineWorkflow` gets written "fresh" and **forgets to call**
> `KnowledgeLoader` (prompt augmentation) and `LearningProposer`/`RetroAgent` (learning capture).
> Nothing is deleted — the capability just **silently goes dark**: pipelines stop benefiting from
> accumulated knowledge and stop capturing new learnings. Invisible until someone notices quality
> regressed.

**Verified components that must be re-wired (all in `cidadel-ai-arbiter`, committed since April):**

- `packages/server/src/shell/knowledge-loader.ts` — `loadForAgent(product, agentType)` → `{ authored, learned }`; `learned` reads Mongo `agent_knowledge` filtered by `{ product, status:'approved', agent_types }`.
- `packages/server/src/retro/learning-proposer.ts` — `propose(product, learnings[])` writes Mongo `agent_knowledge` with `status:'proposed'`.
- `packages/server/src/retro/retro-agent.ts` (+ `retro-boot.md`) — post-run / weekly scan feeding LearningProposer.
- Supporting (do-not-rewrite-blindly): `shell/knowledge-indexer.ts`, `shell/qdrant-client.ts`, `shell/embedding-client.ts`, `shell/registry-cache.ts`, `shell/registry-client.ts`, `shell/pipeline-tracker.ts`, `scripts/index-knowledge.ts`, `scripts/migrate-knowledge-firestore-to-mongo.ts`, and tests `packages/server/tests/shell/knowledge-loader.test.ts` + `knowledge-indexer.test.ts`.

**Required wiring (preservation doc §4):**
- New `PipelineWorkflow`, **per role invocation**, must call `KnowledgeLoader.loadForAgent(productId, agentType)` and fold `authored` + `learned` into that role's system prompt (alongside the persona/role markdown the migration copies into `packages/personas/`).
- The **RETRO_ANALYST** step must run `RetroAgent` → `LearningProposer.propose(productId, …)` on run completion.
- **DO-NOT-DROP:** Mongo `agent_knowledge` collection + the Qdrant knowledge collection. (The plan's risk table currently has **no** entry for this — see §3 correction.)
- Keep `knowledge-loader.test.ts` / `knowledge-indexer.test.ts` green throughout as the regression guard.
- If embeddings change during the overhaul, re-run `scripts/index-knowledge.ts` to rebuild Qdrant from `agent_knowledge` (rebuild, not data loss).

**Why high-severity:** it is a **silent-failure** class — both repos can build/test green while the
read-augment OR the write-capture half is simply not invoked. It must be verified live (both ends),
not just compiled.

---

## 3. Corrections owed (apply early in execution)

There are **two overlapping correction lists** — the decisions doc's "Corrections owed" and the
migration plan's own §12. They mostly agree; both are reproduced so nothing is dropped.

### 3a. From `00-session-decisions.md` §"Corrections owed to existing artifacts"

1. **Migration plan §3 — "shell" language.** In `2026-05-30-orchestrator-migrate-to-arbiter.md`,
   replace "product shell" language with "full product backend." **ADD a "Preserve & integrate
   (arbiter learning layer)" subsection + a DO-NOT-DROP list** for Mongo `agent_knowledge` and the
   Qdrant collection (per preservation doc **§4**).
   - **Verified context:** the plan *currently uses* "product shell" pervasively (header `**Decision:**`
     line; §1.2; §2 "After" block "slimmed to product shell"; §5 / §5.1 "What tacticl-core ends up
     looking like: A product shell"). This is the language decision §2 says to drop ("There is no
     'shell.' Drop that word everywhere"). The plan's §3 ("What's preserved vs. what's thrown away")
     has **no** learning-layer subsection today, and the plan's risk table (§11) has **no**
     DO-NOT-DROP entry for `agent_knowledge` / Qdrant — both additions are owed.
2. **Handoff / execution prompt — one line.** Change *"slimmed but substantial shell"* →
   *"tacticl-core stays a full product backend; only the LLM/orchestration engine moves to arbiter."*
   (The handoff prompt is the operator/execution prompt that drives the swarm; locate its exact file
   before editing — it is not one of the five verified files above.)
3. **Memory file** `project_cloud_agent_orchestrator.md` — update to reflect the **arbiter pivot**
   (engine in arbiter / TypeScript, NOT tacticl-core / Java) so future sessions don't drift back to
   the old mental model. (The currently-injected `MEMORY.md` still frames the orchestrator as
   Temporal-in-tacticl-core; that is the stale model this fixes.)
4. **Diagrams** — *already updated* (verification-only). They show "one Orchestrator + child
   PipelineWorkflow" and "full product, any size" at `docs/architecture/*.drawio` + `.png`. **Treat
   the `.drawio` as source of truth if regenerating.**

### 3b. From migration plan §12 ("Companion doc updates required") — additional owed edits

These are checkboxes still unchecked in the plan; the next session owes them alongside 3a:

- `2026-05-25-cloud-agent-orchestrator-prd.md` — front-matter note: implementation lives in `cidadel-ai-arbiter`; see the `2026-05-30` migration plan.
- `2026-05-25-cloud-agent-orchestrator-sad.md` — same front-matter note; §11 module-layout repointed to `cidadel-ai-arbiter/packages/{conversation,voice,personas}/`; Java class names in §3/§4/§7 annotated "TS impl in [path]".
- `2026-05-25-cloud-agent-orchestrator.md` (original tacticl-Java plan) — SUPERSEDED banner: "Do not implement from this doc."
- `tacticl-core/CLAUDE.md` — update the "active architectural overhaul" banner to point at the `2026-05-30` plan and reflect tacticl-core as a full product backend, not the orchestrator home.
- `cidadel-ai-arbiter/CLAUDE.md` — add a section describing the conversation-orchestrator + voice-plane packages.
- `tacticl-docs/product/pdlc/2026-04-13-tacticl-pdlc-architecture.md` — update the superseded pointer to also mention the `2026-05-30` plan.

**Apply order:** decisions-doc list first — do 3a.1 (plan §3 + learning subsection) before 3a.2/3a.3
so the handoff prompt and memory reconcile to corrected plan wording. The §12 companion-doc updates
(3b) can follow.

---

## 4. Build-green discipline — BOTH gates, continuously

From `00-session-decisions.md` §"Execution mode": *"Keep BOTH build gates GREEN continuously."* The two
gates are explicit and confirmed across the decisions doc and migration plan:

- **Gate A — tacticl-core (Java/Gradle):** `./gradlew test`
  (run from `/Users/cuztomizer/Documents/GitHub/tacticl-core`). The migration plan reinforces this as
  a stop condition — §8 Wave-0 task: *"Run `./gradlew compileJava test` — verify clean build"*; §15
  stop-condition 5: *"`./gradlew test` must stay green throughout."* Repo needs Java 25 + GitHub
  Packages auth (`GITHUB_ACTOR`/`GITHUB_TOKEN`) — a "compile" failure is often a missing-auth /
  dependency-resolution failure.
- **Gate B — arbiter (TypeScript/Node):** **Vitest**
  (run in `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter`). The decisions doc names it
  "arbiter Vitest"; the migration plan uses Vitest throughout (§3 "Replaced by Vitest equivalents",
  per-agent "Vitest tests"). `UNVERIFIED:` the exact npm/pnpm script name — confirm in the arbiter
  `package.json` (likely `npm test`/`pnpm test` → vitest). Plan §8 Wave-0 also requires
  `npm install && npm run build` to compile.

**Discipline:** "continuously," not just at the end. Both-gates-green is a per-wave **synchronization
barrier** — the next wave does not start until the previous wave is green on both. Use evidence (run
the command, read the output) before any "done"/"passing" claim.

---

## 5. Ultrareview cadence — at wave boundaries, USER-TRIGGERED ONLY

From `00-session-decisions.md` §"Execution mode":

> **ultrareview at wave boundaries**: at each wave end, ask the USER to run `/code-review ultra`
> (user-triggered + billed; **an agent CANNOT launch it**). Triage, fix confirmed findings, proceed.

- **When:** at each wave boundary — the plan's waves are Wave 0 → Wave 1 → Wave 2 → Wave 3 (§8).
- **Who triggers:** **the user only.** `/code-review ultra` is user-triggered and billed; an agent
  cannot launch it. The fresh session must reach the boundary, get both gates green (§4), then
  **PAUSE and ask the user** to run it — never auto-run, never assume it's automatic.
- **After:** triage findings, fix the confirmed ones, then proceed. (Distinct from the in-plan
  per-unit **adversarial-verify** stage — see §6 — which the swarm runs itself.)

---

## 6. Dependency / ordering constraints that limit parallelism

The user directive is "ULTRA" parallelism — the plan's "~12 agents / 3 waves / ~1 week" (§8/§9) is a
**FLOOR, not a ceiling**; fan out 100–300+ agents where work has wide independent units. But width is
**bounded by dependencies and correctness, not a number** (`00-session-decisions.md`). The concrete
serialization edges, verified against migration plan §8 (the swarm) and §9 (sequencing):

- **Wave 0 is a hard, single-agent gate that blocks everything.** Plan §8: "Wave 0 — Cleanup +
  alignment (1 agent, sequential, blocks everything)." Agent 1 tears down the 6 tacticl-core modules,
  removes Temporal Java deps, scaffolds the 3 new arbiter packages (`conversation/`, `voice/`,
  `personas/`), and creates `arbiter-conversation.proto`. **Nothing in Wave 1 starts until Wave 0 is
  green on both gates.**
- **Proto → generated clients → importing code.** Decision-doc ordering rule. The new
  `arbiter-conversation.proto` (created in Wave 0) must exist before the gRPC service impl (Agent 9)
  and the tacticl-core Java stubs (Agent 9) can be built.
- **Wave 1 (Agents 2–5) is the foundation; Wave 2 (Agents 6–9) depends on it.** Plan §9: Wave 2 is
  "depends on Wave 1." Within Wave 2, **Agent 9 (gRPC + tacticl-core integration) has a soft dep on
  Agents 6–8 being ~80% done** (plan §9).
- **Registry scoping (§1) is upstream of persona/skill registry work.** Agents 2, 3, 5 (persona
  assets/migration, TS entity types, the registries) bake in whichever scoping model is chosen. **Hold
  / fence these against the §1 decision** so the SAD and seed migration aren't baked prematurely.
- **Learning-layer re-wiring (§2) couples the rebuilt `PipelineWorkflow` (Agent 6) with the arbiter
  knowledge stores.** Sequence/fence the re-wiring rather than treating it as independent — and add it
  to the plan first (correction 3a.1) so the workflow agent has the requirement.
- **Wave 3 is serial-tail:** Agents 10–11 in parallel, then Agent 12 (reviewer) at the end (plan §8).
- **Adversarial-verify per unit** (decisions doc): orchestrate with the Workflow tool (ultracode)
  `pipeline()` over task-lists with a refute-the-work second agent per unit; `isolation: 'worktree'`
  for parallel file mutation.
- **Stop conditions (plan §15) bound width too** — STOP and report (don't push through) on: Temporal
  Node SDK ↔ Postgres incompatibility; TS PASETO can't decode Java-issued tokens (Wave-2 Agent-8
  gate); name collision with arbiter's existing `OrchestratorSession` (345-line Claude Code wrapper);
  Mongo schema conflict between Java entity writes and TS projection writes; Wave-0 deletions breaking
  something unanticipated; legacy `ConversationSession` bridge methods becoming load-bearing.

---

## ✅ DO THIS FIRST — ordered checklist for the fresh session

1. **Confirm tooling is healthy.** Run `echo ok` (Bash) and `Read` `CLAUDE.md`. (Note: the prior
   session hit transient API/tool errors — the empty-output condition that triggered this handover.
   If your tools return empty, retry; if it persists, re-establish the session before trusting any
   read.)
2. **Read the two authoritative docs in full:**
   - `…/docs/handover/2026-05-30-orchestrator-migration/00-session-decisions.md` (the 12 locked
     decisions — internalize before touching anything).
   - `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md`
     (the migration plan — note §3, §8 swarm, §9 sequencing, §11 risk table, §12 companion-doc
     updates, §15 stop conditions).
   - Skim `…/docs/architecture/learning-layer-and-codegen-prompts-preservation.md` §4 (the exact
     re-wiring requirement).
3. **Update memory** `…/memory/project_cloud_agent_orchestrator.md`: record the **arbiter pivot**
   (engine in arbiter/TypeScript, NOT tacticl-core/Java), the open registry-scoping question (§1), the
   both-gates build discipline (§4), and the user-triggered ultrareview cadence (§5). (Correction
   3a.3.)
4. **Fix the plan (corrections owed, §3), in order:**
   a. Migration plan §3 — kill "product shell" language → "full product backend"; ADD the
      "Preserve & integrate (arbiter learning layer)" subsection + DO-NOT-DROP list (Mongo
      `agent_knowledge`, Qdrant collection) per preservation doc §4; add the DO-NOT-DROP row to the
      §11 risk table.
   b. Handoff/execution prompt — fix the "slimmed but substantial shell" line (locate the file).
   c. (Memory already done in step 3; companion-doc updates from plan §12 / §3b can follow.)
5. **Resolve the open question (§1) WITH THE USER** before dispatching any persona/skill registry task
   (Agents 2/3/5) — it is upstream and must not be baked into the SAD/schemas without confirmation.
6. **Confirm both build gates run:** tacticl-core `./gradlew test`; arbiter Vitest (verify the exact
   script in the arbiter `package.json`).
7. **Then start Wave 0** (single sequential agent: tacticl-core deletes + arbiter package scaffolds +
   `arbiter-conversation.proto`), keeping both gates green. Honor `proto → generated clients →
   importing code` and Wave 0 → 1 → 2 → 3 ordering; hold registry tasks until §1 is resolved; fence
   the learning-layer re-wiring (§2). At the Wave 0 → Wave 1 boundary: get both gates green, then
   **PAUSE and ask the user to run `/code-review ultra`** (do not auto-run).
