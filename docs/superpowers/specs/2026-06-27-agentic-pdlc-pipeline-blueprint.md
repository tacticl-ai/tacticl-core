# Agentic PDLC Pipeline — Canonical Blueprint + Build Sequence (2026-06-27)

> Canonical target: the analyst persona agent IS the orchestrator; ephemeral persona agents; HITL gates; dynamic phase scoping; one feature branch; host-rendered Pipeline Delivery Summary. Supersedes the rigid 12-role pdlcRunWorkflow DAG. Source vision + locked decisions: memory `project_agentic_pdlc_pipeline_vision`.

## Locked decisions (2026-06-27)
- Launch = in-workflow `startChild(featurePipelineWorkflow, ABANDON)`; admin re-proven in-arbiter; intent-stable `featureId` dedup.
- Cost cap = NONE for v1 (MUST-BEFORE-LAUNCH to add).
- Linear = DEFERRED from v1 (seam only; central-Tacticl-Linear in v1.1).
- 11 persona agents + research-as-a-skill; dynamic phase scoping; Technical Writer authors the (host-rendered-facts) delivery summary; DevOps optional post-merge + deploy-review gate; Retro silent -> learning layer.
- GitHub = App via onboarding install; merge-on-approval; no raw PAT.
- Two-workflow model: 1 analyst + a family of pipeline variants (one parameterized engine driven by the analyst's phase-plan). Review Intent = transient preview screen (no git artifact).

---

# BLUEPRINT

All ground truth confirmed against the arbiter source. Key verifications:

- `start_pipeline` (conversation-mcp.ts:179-232) is **TERMINAL and side-effect-free** — pre-asserts only, does NOT submit. **No `startChild`/`ParentClosePolicy`/`getExternalWorkflowHandle` anywhere** → the launch bridge is greenfield (critique D0 confirmed).
- `alignmentState.aligned && alignmentState.alignedByAdmin` enforced server-side, non-forgeable (conversation-mcp.ts:610-685).
- Gate machinery (`approveMergeSignal`, `decisionByAsk`, `openAsk`, `gateNonce`, first-decision-wins, `condition(MERGE_GATE_TTL)`, `decideRework`, `MAX_REWORK_ROUNDS_HARD_CEIL=16`, `computeDownstreamClosure`) exists — but as **two fixed gates** (`planGateRole`/`mergeGateRole`); ordered-list generalization is greenfield (Inconsistency B confirmed).
- Host-git `pushBranch`/`openPullRequest`/`mergePullRequest`(`approvedSha`/`--match-head-commit`)/`gitleaksScan`(fail-closed) exist; `ensureFeatureBranch`/`initRepo`/`writeHandoff` do NOT (D4 confirmed).
- `runAgentRole` capture seam: `taskPlanJson` via `readResultFile(...,'tasks.json')` exists; `manifestJson` does not (one more capture needed).
- `github-app-auth.ts` exists: JIT installation-token minting, App installed across orgs, `administration` perm for repo-create, Vault `secret/{context}/github-app`.
- Retro learning layer exists as a **weekly batch** (`retro/retro-agent.ts` → `learning-proposer.ts` → Mongo `agent_knowledge`); needs per-feature re-wire.

Here is the final canonical blueprint.

---

# Analyst-Orchestrated Agentic PDLC Pipeline — Canonical Blueprint (v2, 2026-06-27)

Supersedes the v1 spec. Keeps v1's topology (two Temporal workflows, phases-as-control-flow, `runAgentRole` per persona, host-git reuse, gate machinery) and applies the 2026-06-27 locked decisions, refinements, and critique-fixes verbatim. Every code claim below was verified against `cidadel-ai-arbiter/packages/server/src`. **Decisions still owned by a human are tagged `[HUMAN DECISION]`. Launch-blockers are tagged `[MUST-BEFORE-LAUNCH]`.**

---

## 0. Ground truth + canonical naming

**Verified state of the arbiter (not assumed):**

| Claim | Status | Evidence (file) |
|---|---|---|
| Analyst conversation workflow exists, `continueAsNew`s, carries `ConversationCarryOver{dispatchedRuns[]}` | ✅ built | `temporal/conversation-session-workflow.ts` |
| `start_pipeline` launches a build | ❌ **FALSE** — it is TERMINAL + side-effect-free (pre-assert only) | `conversation/conversation-mcp.ts:179-232` |
| Launch bridge (`startChild`) exists | ❌ **greenfield — none in repo** | grep: zero `startChild`/`ParentClosePolicy` |
| Non-forgeable admin gate `aligned && alignedByAdmin` | ✅ built, server-side | `conversation-mcp.ts:610-685`, `conversation-session-store.ts` |
| `runAgentRole` ephemeral-container activity, shared-workspace, `taskPlanJson` capture | ✅ built | `temporal/activities/run-agent-role.ts` |
| Durable 3-outcome gate (`approveMergeSignal`/`decisionByAsk`/`openAsk`/`gateNonce`/first-decision-wins/`condition(MERGE_GATE_TTL)`) | ✅ built but **two fixed gates only** | `temporal/pdlc-run-workflow.ts:196-488` |
| `decideRework`, `MAX_REWORK_ROUNDS_HARD_CEIL=16`, `computeDownstreamClosure` | ✅ built | `pdlc-run-workflow.ts`, `temporal/dag.ts` |
| Host-git `pushBranch`/`openPullRequest`/`mergePullRequest`(SHA-pinned)/`gitleaksScan`(fail-closed) | ✅ built | `temporal/activities/host-git-wiring.ts` |
| `ensureFeatureBranch`/`initRepo`/`writeHandoff` | ❌ **greenfield** | not present |
| `createRepo` (raw GitHub) | ✅ built | `shell/github-client.ts:83` |
| GitHub App JIT installation-token mint (least-priv, ≤1h, per-repo, `administration` for create) | ✅ built | `shell/github-app-auth.ts` |
| Retro learning layer → Mongo `agent_knowledge` + Qdrant RAG (`knowledgeNamespace`) | ✅ built **as weekly batch** | `retro/retro-agent.ts`, `retro/learning-proposer.ts`, `run-agent-role.ts:157,399` |
| `manifestJson` capture in `runAgentRole` | ❌ greenfield (one `readResultFile` call) | `run-agent-role.ts:539-544` (the `taskPlanJson` template) |

**Canonical names:**

- **`featureId`** — the one correlation key. **CHANGED FROM v1:** NOT `pdlc-{turnId}`. It is an **intent-stable hash** so "start it" said twice never launches two pipelines (see §2.3). Used in workflowId `feat-{featureId}`, container names, artifact paths `docs/pdlc/{featureId}/…`, and the (deferred) Linear refs.
- **Two durable workflows:** `conversationSessionWorkflow` (`conv-{conversationId}`, exists) and **`featurePipelineWorkflow`** (`feat-{featureId}`, NEW).
- **One ephemeral-agent activity:** `runAgentRole` — reused verbatim for every persona.
- **Queues:** `PDLC_TQ_ORCHESTRATION` (workflow + cheap activities), `PDLC_TQ_AGENT` (serialized containers, `PDLC_AGENT_MAX_LIVE=1`), `PDLC_TQ_REPO` (host-git + deploy host-ops), `PDLC_TQ_CONVERSATION` (analyst). `PDLC_TQ_PROJECTION` (Linear) is **designed but not registered** in v1 (§10).
- The legacy `pdlcRunWorkflow` (rigid 12-role DAG) is **not** the engine. We reuse its *parts* inside the new explicit, **dynamically-scoped** phase machine. `submit-temporal-run.ts → pdlcRunWorkflow` stays only for existing `pdlc-fix` callers.
- New files: `temporal/feature-pipeline-workflow.ts`, `temporal/feature-types.ts`, `temporal/feature-phase-plan.ts` (pure validator).

---

## 1. End-to-end flow (with dynamic scoping, DevOps deploy tail, Retro)

```
ANALYST (conversationSessionWorkflow, conv-{conversationId}) — EXISTS
  └─ converses; deciphers intent using SHARED SKILLS (product · business · research[web+codebase])
  └─ SIZES the task → emits a PHASE PLAN (dynamic scope; typo-fix ≠ full PDLC)
  └─ repo decision IN-CONVERSATION: CREATE (user's own org) | SELECT existing
  └─ SHOWCASES {DecipheredIntent + PhasePlan + repo} → user PREVIEWS → "Start Pipeline"
         │  alignment gate flips aligned=true, alignedByAdmin=true   (server-tracked, not forgeable)
         │  start_pipeline tool (still terminal pre-assert) returns dispatch{intent,phasePlan,repo}
         ▼  processTurn — re-proves aligned && alignedByAdmin IN THE ARBITER, then:
            featureId = stableHash(alignedIntent)            // intent-level dedup
            startChild(featurePipelineWorkflow, ABANDON, workflowId=feat-{featureId})
            activePipelines.push({featureId, status:RUNNING})
         ▼
FEATURE PIPELINE (featurePipelineWorkflow, feat-{featureId}) — NEW
  step 0  ensureFeatureBranch  (create → initRepo + first-commit-on-main, then cut feature/{slug}-{featureId})
  step 0  notify analyst: pipeline_started
  step 0  [Linear projection DEFERRED — seam only]

  ── runs ONLY the phases in phasePlan.phases (conditional execution) ──
  P1 PRODUCT_OWNER → PRD + user stories        ── HITL GATE   (skipped if not in plan)
  P2 ARCHITECT     → SAD + ADRs                ── HITL GATE   (skipped for small/docs work)
  P3 DESIGNER      → UX report                 ── HITL GATE   (skipped unless uxNeeded)
  P4 PLANNER       → impl plan + tasks.json    ── HITL GATE   (skipped for trivial work)

  ── THE COMBO (present iff any code phase scoped; sequential, NO HITL between) ──
  P5 IMPLEMENTER ⇄ P7 REVIEWER   (bounded internal loop, manifest-verdict driven, no human)
  P6 SECURITY     (after reviewer, before tester)
  P8 TESTER       → tests + AC→test→pass/fail report
     └─ combo narrates progress UP to analyst + heartbeat "taking too long" (R3)

  P9 DELIVERY (TECHNICAL WRITER) → Pipeline Delivery Summary
        facts HOST-RENDERED deterministically (PRD/SAD/diffs/test results/security findings)
        TW writes ONLY the narrative prose sections
     ── FINAL HITL GATE (approve / reject / feedback→rework) ──
        on APPROVE → gitleaks + SAST fail-closed → GitHub App merges feature → main

  ── OPTIONAL POST-MERGE TAIL (iff phasePlan.devopsNeeded) ──
  P10 DEVOPS → deploy to PREVIEW/staging
     ── DEPLOY-REVIEW GATE (DISTINCT type: surfaces LIVE URLs / preview / deploy status, not a doc/diff) ──
        feedback → DevOps rework;  approve → promote to PROD

  ── ALWAYS, SILENT, POST-COMPLETION ──
  P11 RETRO_ANALYST → extract learnings → Mongo agent_knowledge + Qdrant   (no gate, no user surface)
```

The **analyst is the only conversational surface**. The pipeline never talks to the user directly: it narrates *up* (`onPipelineEvent`) and receives gate decisions *down* (`resolveGate`). The tacticl-core dashboard checkpoint UI is the second, equivalent decision surface resolving the same signal (§4.5).

### Dynamic scoping mechanism (refinement)

- The analyst's deciphered intent carries a **`PhasePlan`** it computes by sizing the task (LLM, constrained). `DecipheredIntent` gains `phasePlan: PhasePlan`.
- **`PhasePlan`** = `{ phases: PhaseId[], uxNeeded: boolean, devopsNeeded: boolean, deployTarget?: 'preview'|'staging', sizing: 'trivial'|'small'|'standard'|'full' }`.
- **`PhaseId`** enum: `PRODUCT_OWNER | ARCHITECT | DESIGNER | PLANNER | IMPLEMENTER | REVIEWER | SECURITY | TESTER | DELIVERY | DEVOPS_DEPLOY`. `RETRO` is **not** in the plan — it is appended unconditionally and silently.
- **`feature-phase-plan.ts` — pure, deterministic validator/normalizer** runs in-workflow before execution (no LLM, sandbox-safe). It enforces non-negotiable invariants so a hallucinated plan can't produce unsafe scope:
  - any of `{IMPLEMENTER}` ⇒ force-add `PLANNER, REVIEWER, SECURITY, TESTER, DELIVERY` (code never ships ungated/untested/unreviewed);
  - `DELIVERY` is always last before any devops;
  - `DESIGNER` ⇒ requires `PRODUCT_OWNER`;
  - `DEVOPS_DEPLOY` ⇒ requires a successful merge (post-final-gate only);
  - topological order is canonical regardless of the order the LLM emitted.
- Example scopes: a **typo/docs fix** → `[IMPLEMENTER, TESTER, DELIVERY]` (no PRD/SAD/UX/Planner; the validator allows skipping PLANNER only for `sizing:'trivial'` with ≤N files — otherwise it force-adds it). **[HUMAN DECISION] the trivial fast-path floor** — confirm whether a trivial fix may skip PLANNER+REVIEWER+SECURITY, or whether the floor is always `IMPLEMENTER+REVIEWER+TESTER+DELIVERY`. (Recommendation: floor = `IMPLEMENTER+REVIEWER+TESTER+DELIVERY`; never skip review/test on code.)

---

## 2. Temporal topology — startChild(ABANDON) launch + admin re-assert + intent dedup

### 2.1 Two workflows, one bidirectional signal bridge

```
conversationSessionWorkflow              featurePipelineWorkflow
  conv-{conversationId}                    feat-{featureId}
  (chatty, continueAsNew often)            (durable, parks for days on gates)
        │                                          ▲
        │ startChild(ParentClosePolicy.ABANDON) ───┤  launch (workflow-native, deterministic)
        │ getExternalWorkflowHandle(feat-…)         │
        │   .signal(resolveGate | cancelFeature) ──►│  commands DOWN
        │ getExternalWorkflowHandle(conv-…)          │
        │   .signal(onPipelineEvent) ◄──────────────┤  narration UP
```

Why a **sibling top-level** workflow with **`ParentClosePolicy.ABANDON`** (not an inner block, not a default child): the analyst is single-flight FIFO and `continueAsNew`s constantly for transcript compaction — a multi-day gated build cannot live inside it, and the analyst's `continueAsNew` would orphan/terminate a default child. `ABANDON` decouples lifecycle ("builds outlive the conversation") while keeping the launch workflow-native (no activity round-trip, deterministic, no `randomUUID` in the sandbox). After `continueAsNew`, the conversation re-derives the handle deterministically via `getExternalWorkflowHandle(feat-{featureId})` from a carried `activePipelines[]`. Both bridge directions are pure workflow code (signals are commands).

### 2.2 The "Start Pipeline" launch bridge (greenfield — this is the missing wire)

`start_pipeline` **stays a terminal pre-assert** (do not change the tool). The launch happens in `processTurn` *after* the brain returns the terminal tool_use, exactly where v1 left a gap:

```ts
// conversation-session-workflow.ts — processTurn, NEW launch branch
const dispatch = invokeResult.dispatch;            // extended InvokePersonaResult.dispatch
if (dispatch) {
  // ── D0: RE-PROVE ADMIN AUTHORITY IN THE ARBITER (critique D0) ──
  // tacticl-core's broker re-assert is BYPASSED now; the arbiter is the authority.
  const align = await readAlignmentState(conversationId);     // FRESH persisted server truth
  if (!align.aligned)        return refuse('not aligned');
  if (!align.alignedByAdmin) return refuse('aligned, but not by an admin');     // non-forgeable bit
  if (dispatch.repo.mode === 'select' && !sameRepo(align.repoUrl, dispatch.repo.repoUrl))
                              return refuse('repo differs from the aligned proposal');

  const featureId = stableFeatureId(align);        // §2.3 — intent-level dedup, NOT pdlc-{turnId}
  try {
    await startChild(featurePipelineWorkflow, {
      workflowId: `feat-${featureId}`,
      parentClosePolicy: ParentClosePolicy.ABANDON,
      workflowIdReusePolicy: WorkflowIdReusePolicy.REJECT_DUPLICATE,   // idempotency
      args: [{ featureId, intent: dispatch.intent, phasePlan: dispatch.phasePlan,
               repo: dispatch.repo, conversationWorkflowId: workflowInfo().workflowId }],
    });
    activePipelines.push({ featureId, workflowId:`feat-${featureId}`, status:'RUNNING' });
  } catch (e) {
    if (isAlreadyStarted(e)) return inform('that build is already running — here is its status …');
    throw e;
  }
}
```

`InvokePersonaResult.dispatch` (extend the existing type):
```ts
dispatch?: {
  intent: DecipheredIntent;     // title, problem, goals, acceptanceCriteria[], sparkType, playbook
  phasePlan: PhasePlan;         // §1 — the dynamic scope
  repo: { mode:'create'|'select'; repoUrl?:string; owner?:string; name?:string; visibility?:string };
  inputJson: string;            // audit, unchanged
}
```

> **[MUST-BEFORE-LAUNCH] D0 — the admin re-assert is load-bearing.** The launch is now in the arbiter and tacticl-core's old broker re-assert no longer guards it. The `startChild` MUST be gated on the freshly-read, non-forgeable `align.aligned && align.alignedByAdmin`. This is the only thing standing between "the model emitted a tool_use" and "containers spend money on main." Never read these from a model-supplied arg.

### 2.3 Intent-level dedup (the featureId change)

```ts
// pure, deterministic, sandbox-safe (no Date.now / randomUUID)
function stableFeatureId(align: AlignmentState): string {
  const norm = canonicalize({                       // sorted keys, trimmed, lowercased title/problem
    title: align.proposal.title,
    problem: align.proposal.problem,
    repoUrl: align.repoUrl ?? `${align.proposal.owner}/${align.proposal.name}`,
    acceptanceCriteria: [...align.proposal.acceptanceCriteria].sort(),
  });
  return 'f' + sha256Hex(norm).slice(0, 16);        // feat-f<16hex>
}
```
Two utterances of "start it" on the *same aligned proposal* hash to the same `featureId`; `feat-{featureId}` + `WorkflowIdReusePolicy.REJECT_DUPLICATE` makes the second `startChild` throw `WorkflowExecutionAlreadyStarted`, which the analyst catches and answers "already running." A genuinely *new* aligned proposal (different title/AC/repo) hashes differently → new pipeline. This replaces v1's `pdlc-{turnId}`, which would have launched twice.

> **[HUMAN DECISION] dedup window.** A user who finishes a build, then re-aligns the *identical* proposal weeks later, would collide on the same `featureId`. Recommendation: salt the hash with a coarse epoch or the `conversationId` so re-runs are allowed across conversations but deduped within one. Confirm the desired semantics.

### 2.4 Workflow-vs-activity table

| Concern | Mechanism | Queue |
|---|---|---|
| Analyst conversation | `conversationSessionWorkflow` (exists) | `PDLC_TQ_CONVERSATION` |
| Phased build orchestration | `featurePipelineWorkflow` (NEW), explicit conditional phase machine | `PDLC_TQ_ORCHESTRATION` |
| **A phase** | workflow control flow = {validate scope} + one `runAgentRole` + {commit/push} + {gate park} + {`writeHandoff`} — NOT a child workflow | — |
| **A persona agent** | one `runAgentRole` = one ephemeral container (create→work→destroy) | `PDLC_TQ_AGENT` (`PDLC_AGENT_MAX_LIVE=1`) |
| The combo (P5–P8) | inline sequential block + bounded internal reviewer↔implementer loop | `PDLC_TQ_AGENT` |
| Host-git ops | activities | `PDLC_TQ_REPO` |
| Deploy host-ops (devops tail) | activities (`deployPreview`, `promoteProd`, `readDeployStatus`) | `PDLC_TQ_REPO` (or new `PDLC_TQ_DEPLOY`) |
| Delivery summary FACTS | host activity `renderDeliverySummaryFacts` (deterministic) | `PDLC_TQ_ORCHESTRATION` |
| Delivery summary NARRATIVE | `runAgentRole` (TECHNICAL_WRITER persona) | `PDLC_TQ_AGENT` |
| Retro | `runAgentRole` (RETRO persona) + `learning-proposer` write | `PDLC_TQ_AGENT` / `PDLC_TQ_ORCHESTRATION` |
| Linear projection (deferred) | designed seam only | `PDLC_TQ_PROJECTION` |

> **[HUMAN DECISION] combo extraction.** Keep the combo inline (recommended, simplest state-sharing) vs extract to a child `comboWorkflow` only if combo history proves large.

---

## 3. The 11-persona roster + research-as-skill + persona knowledge

### 3.1 Roster = **11 persona agents**, RESEARCH = a shared skill (not a persona/phase)

The 12-role `PdlcRole` enum minus `RESEARCHER` = 11 build personas. Research is web+codebase capability shared by Analyst/PO/Architect, not its own container.

| # | Phase | role (`agents/{role}.json`) | Job | Reads | Writes (committed + `results/`) | Gate | Run params |
|---|---|---|---|---|---|---|---|
| 1 | Product Owner | `product-owner` | Thorough PRD + user stories from analyst intent | `handoff.json`, `user-prompt.md` | `docs/pdlc/{featureId}/prd.md` + `manifest.json` (acceptanceCriteria[]) | ✅ | Sonnet, medium, max_turns 30, **research skill on** |
| 2 | Architect | `architect` | SAD + 1 ADR/significant decision; consistent w/ existing code | PRD, repo | `sad.md`, `adr-NNN-*.md` | ✅ | Sonnet (Opus for hard systems), 40, **research on** |
| 3 | Designer (optional) | `designer` | UX report: flows, wireframe descriptions, states, a11y | PRD, SAD | `ux.md` (+ optional `ux-wireframe.html`) | ✅ | Sonnet, 25 |
| 4 | Planner | `planner` | Impl plan chunked + machine task plan | all prior | `plan.md` + `results/tasks.json` | ✅ | Sonnet, 25 |
| 5 | Implementer | `implementer` | Code per plan, chunk-by-chunk, focused tests, commit per chunk | `plan.md`, `tasks.json`, SAD, repo, (rework) `review.md`/`security.md` | code commits + `manifest.json` | ❌ combo | Sonnet, heavy, 60 |
| 6 | Reviewer | `reviewer` | Review diff; drive bounded internal loop | `git diff featureBase..HEAD`, SAD, plan | `review.md` + `manifest.review={verdict,findings[]}` | ❌ combo | Sonnet, 30 |
| 7 | Security | `security` | Reason over diff for vuln classes vs OWASP checklist | diff, SAD, `review.md` | `security.md` + manifest findings | ❌ combo | Sonnet, 25 |
| 8 | Tester | `test` | Write/run tests; AC→test→pass/fail report | accumulated `handoff.json` (AC), code | test commits + `test-report.md` | ❌ combo | Sonnet, heavy, 40 |
| 9 | **Technical Writer** | `technical-writer` | **NARRATIVE prose** of the delivery summary (facts host-rendered) | full `handoff.json`, host-rendered facts block, `git log` | narrative sections injected into `delivery-summary.{md,html}` | ✅ FINAL | Sonnet, light, 20 |
| 10 | DevOps (optional) | `devops` | Deploy to preview/staging; on approve promote to prod | merged `main`, deploy config | `deploy-report.md` + live URLs into manifest | ⚙️ deploy-review | Sonnet, medium, 30 |
| 11 | Retro Analyst | `retro-analyst` | Silent post-completion learnings extraction | all workspace logs + artifacts | `agent_knowledge` docs (Mongo + Qdrant) | none | Haiku/Sonnet, light, 15 |

Combo order: **Implementer ⇄ Reviewer → Security → Tester** (security after reviewer, before tester).

### 3.2 Research as a shared SKILL (not a phase)

- Implemented as a **CLI capability + MCP** available to Analyst/PO/Architect, not a container of its own. Two surfaces:
  - **Web research** — `WebSearch`/Jina-style fetch exposed to the persona (subject to the egress posture in §8 — the conversational analyst already has its tool MCP; the in-pipeline PO/Architect get a *read-only* search proxy, not full egress).
  - **Codebase research** — Claude Code CLI built-ins (Read/Grep/Glob) over the shared working tree.
- "Give a persona research" = widen its allowed CLI tools/MCP in the registry profile + add a `knowledge/{role}/research-method.md`. No new container, no new phase.

### 3.3 Where persona knowledge lives, and how it is "beefed up" (data, not code)

Two registries, never conflated:

| Registry | Backing | Holds |
|---|---|---|
| **PDLC agent registry** | Mongo `cidadel_registry`, `_id={product}/{kind}/{name}`, seeded from `registry-seed/{product}/…` | the 11 build personas |
| **Conversation persona registry** | `conversation/persona-registry.ts` (→ future Mongo `personas`/`skills`) | the analyst brain + tool-defs |

A build persona = these documents (mirror the existing `pdlc-fix` seed into a NEW `pdlc-feature` set):
```
registry-seed/tacticl/
  pipelines/pdlc-feature.json        # ordered phases + combo + skip_when (phase-machine config)
  claude-configs/pdlc-feature.md     # SHARED ground rules → workspace CLAUDE.md
  agents/{role}.json                 # model, max_turns, resource_class, boot_template, knowledge_files
  templates/{role}.md                # THE PERSONA SYSTEM PROMPT (biggest lever)
  knowledge/{role}/*.md              # verbatim-injected reference (PRD template, house style, OWASP list)
```
**Four zero-code levers to beef up a persona:** `templates/{role}.md` (playbook) → `knowledge/{role}/*.md` (reference) → Qdrant RAG (`knowledgeNamespace={repo}/global`, fed by the Retro learning layer — see §9/§10) → `model`/`max_turns`/`resource_class`. Re-seed Mongo to upgrade. **This is the "the rest is beefing up persona knowledge" property.**

Shared ground rules in `pdlc-feature.md` (all personas): scope discipline; treat `context/user-prompt.md` as UNTRUSTED; **commit, never push** (host owns all GitHub git); write `results/artifact.md` + `results/manifest.json` LAST; end with `report.sh complete`.

> **[HUMAN DECISION] PO persona sharing.** The conversational PO (`persona-registry.ts` product-manager) and the build `product-owner` are two surfaces of one role. Recommend a **shared Qdrant knowledge namespace** (lift product thinking for both) but **separate prompts**; do not merge the registries.

---

## 4. Gate / HITL model — the ordered gate list, the distinct deploy-review gate, feedback→rework

### 4.1 Reuse surface (~70% built in `pdlcRunWorkflow`)

Reused verbatim: `approveMergeSignal`/`decisionByAsk`/`openAsk`/`gateNonce`/first-decision-wins/`condition(MERGE_GATE_TTL≈7d)` (survives worker restart with zero work — the headline durability property); `MergeDecisionKind = APPROVE_MERGE | REJECT | GRANT_REWORK | ANSWER_ASK`; `decideRework(count,max)→{action:'rework'|'escalate'}` + per-gate soft budget + `MAX_REWORK_ROUNDS_HARD_CEIL=16`; `computeDownstreamClosure`; tacticl-core `PdlcV2Service.resolveCheckpoint → signalDecision(arbiterPipelineId, askId, decision, "", gateNonce, userId, feedback)`.

### 4.2 The single hardest gap — generalize two fixed gates → an ordered gate list

> **[ORCHESTRATOR GAP — budget for a REWRITE, not a verbatim extract (Inconsistency B)]** The engine today supports exactly TWO fixed human gates (`planGateRole` + `mergeGateRole`), expressed as inline branches in `pdlc-run-workflow.ts`. The target needs **up to 4 pre-combo gates + 1 final + 1 deploy-review**, attached *conditionally* per the dynamic phase plan. Lifting the inline gate logic into a reusable `parkOnGate(...)` over an **ordered list of gate roles**, driven by a single `resolveGate` signal, is the hardest single piece of this build. The personas stay deliberately gate-agnostic (they only produce artifact + manifest + `report.sh complete`), so the generalization is purely orchestrator-side — but it is a rewrite of the workflow's control spine, not a copy-paste.

```ts
// feature-pipeline-workflow.ts — the generalized park
export const resolveGate   = defineSignal<[GateDecision]>('resolveGate');
export const cancelFeature = defineSignal<[CancelInput]>('cancelFeature');
export const featureStatus = defineQuery<FeatureStatus>('featureStatus');

async function parkOnGate(gate: GateSpec): Promise<GateDecision> {
  askSeq += 1;
  const askId    = `${info.runId}:ask:${askSeq}`;
  const gateNonce = `${info.runId}:nonce:${askSeq}`;
  openAsk = { askId, gateNonce };
  notifyConversation({ kind:'gate_open', featureId, phase: gate.phase,
                       gateType: gate.type, askId, gateNonce, surfaced: gate.surfaced });
  const ok = await condition(() => decisionByAsk.has(askId), MERGE_GATE_TTL);   // 7d, restart-safe
  openAsk = undefined;
  if (!ok) { terminal = 'EXPIRED'; throw ApplicationFailure.nonRetryable('gate expired'); }
  return decisionByAsk.get(askId)!;
}
```
`GateDecision` = `MergeDecision` widened: `{ decision:'APPROVE'|'REJECT'|'FEEDBACK', feedback?:string, targetPhase?:PhaseId, askId, gateNonce, approverUserId, byAdmin }`.
`GateSpec.type ∈ { 'phase' | 'final-merge' | 'deploy-review' }` — drives the gate UX (see §4.4).

### 4.3 Three outcomes + feedback→rework

Uniform across every gate:
- **APPROVE** — record phase complete (`headSha`, artifact path), `writeHandoff`, advance.
- **REJECT** — terminal `REJECTED`.
- **FEEDBACK → rework the agent that did the work.** For phase gates P1–P4, rework is trivially "re-run this same persona": phases are strictly linear and gated, so nothing downstream has run — there is **no `computeDownstreamClosure` to reset**. The human feedback is threaded into the next iteration's `requestContext.handoff.feedback`; the agent re-spawns **fresh** (ephemerality makes doc-phase rework genuinely free — no agent state to reset), reads the richer manifest, redoes the artifact. Bounded by a per-gate soft budget; on exhaustion → `ESCALATED`. **Caveat for CODE phases:** rework is NOT free (see §9 — partial commits on the shared branch must be reconciled).

### 4.4 The DISTINCT deploy-review gate (DevOps tail)

`gateType:'deploy-review'` is a **first-class, different gate**, not a doc/diff gate. Its `surfaced` payload and UX are different:
```ts
surfaced: {                            // deploy-review only
  kind: 'deploy-review',
  deployStatus: 'live'|'failed'|'partial',
  previewUrls: string[],               // clickable, the human reviews LIVE OUTPUT
  deployTarget: 'preview'|'staging',
  logsPointer: string,                 // tail of deploy logs
  healthChecks: { name:string, ok:boolean }[],
}
```
The analyst/dashboard renders **deployed links and live status**, not a markdown diff. Outcomes:
- **APPROVE** → `promoteProd` activity (promote the validated preview to production).
- **FEEDBACK** → re-enter the **DevOps** agent only (redeploy with the fix), bounded by the deploy gate budget.
- **REJECT** → stop at preview, terminal `DEPLOY_REJECTED` (merge already happened; prod simply not promoted).

### 4.5 Two equivalent decision surfaces, one signal

Both surfaces resolve the same `resolveGate` (askId + gateNonce; first-decision-wins dedupes):
- **Conversational (analyst):** user turn ("approve the PRD", "change the auth flow", "ship it") → `invokePersona` returns `gateDecision?:{featureId, askId, gateNonce, decision, feedback?, targetPhase?}` → `processTurn` relays via `getExternalWorkflowHandle(feat-{featureId}).signal(resolveGate, …)`, binding approver + admin.
- **Dashboard (tacticl-core):** `PdlcV2Service.resolveCheckpoint → SignalPipelineDecision gRPC → resolveGate`.

**Narration up:** the pipeline carries `conversationWorkflowId` and calls `notifyConversation(ev) → getExternalWorkflowHandle(conversationWorkflowId).signal(onPipelineEvent, ev)` at each milestone. **NEW on `conversationSessionWorkflow`:** an `onPipelineEvent` handler buffering `{featureId, kind, phase?, gateType?, askId?, gateNonce?, artifactPointer?, summary?}` into durable `pipelineEvents[]` (add to `ConversationCarryOver` next to `dispatchedRuns[]`); the turn loop drains it so the analyst can answer "how's it going?" and announce gates.

> **[HUMAN DECISION] proactive narration.** On `gate_open`, should the analyst proactively speak (enqueue a synthetic system turn — can interrupt a voice session) or only answer when the user next asks? Affects voice UX.

---

## 5. Git / branch / PR / merge model — parameterized `diff|pr` (default `diff`) + repo-create

**One feature branch off main, shared working tree, commits accumulate across all phases.** Run in `PDLC_SHARED_WORKSPACE` mode keyed by `featureId`; every phase/combo role commits onto `feature/{slug}-{featureId}` and `headSha` accumulates. The Architect literally opens `docs/pdlc/{featureId}/prd.md` off disk.

Host-git activities on `PDLC_TQ_REPO`:
- **`ensureFeatureBranch({repo, featureId, branchPattern})`** (NEW — greenfield). If `repo.mode==='create'`: **`initRepo` + first commit on `main`** (NEW; repo creation reuses the analyst's `create_repo`/`RepoProvisioner`, but main-init-before-branch is unbuilt), then cut `feature/{slug}-{featureId}` off main. If `select`: fetch + branch off main. **Repos are always created in the USER'S OWN connected org, never the tacticl org** (per project decision `project_repo_owner_is_user_org`).
- `pushBranch`, `openPullRequest`, `mergePullRequest` (`approvedSha` / `--match-head-commit` TOCTOU SHA-pin, squash), `gitleaksScan` (fail-closed) — **exist** (`host-git-wiring.ts`).
- **`writeHandoff`** (NEW) — write the thin JSON manifest + the phase's markdown handoff, commit to the feature branch.

**Parameterized gate review:** `bundle.gateReview:'pr'|'diff'`, default **`'diff'`**:
- `'diff'` (default) — each phase commits to the feature branch; host pushes after each phase; the gate renders `git diff prevPhaseSha..thisPhaseSha`. ONE real GitHub PR opened at the final delivery gate (feature→main); merge only after APPROVE + fail-closed gitleaks/SAST.
- `'pr'` — a real PR per phase into the feature branch (richer GitHub UI per phase). Fully supported by the same gate code.

Merge is the only place code reaches `main`, gated by explicit human APPROVE then a fail-closed scan, executed by the **GitHub App** (§6).

> **[HUMAN DECISION]** Confirm default `'diff'` (recommended — incremental push + one PR is a tiny delta vs N per-phase PRs and their host-git/PR noise).

---

## 6. GitHub-App onboarding access + merge-on-approval

**One onboarding act — "Connect GitHub" — grants everything.** No raw PATs anywhere in the pipeline.

- **Onboarding flow (OAuth-style App install):** the user installs the **Tacticl GitHub App** on their own org via the standard GitHub App installation/consent screen (surfaced in the tacticl-web onboarding as "Connect GitHub"). This single consent grants the App `contents:write`, `pull_requests:write`, `metadata:read`, and (for repo-create) `administration:write` on the selected repos/org.
- **Auth chain (built — `shell/github-app-auth.ts`):** App holds ONE private key (Vault `secret/{context}/github-app` → `app-id`, `private-key`); per run it mints a **short-lived (≤1h), single-repo, least-privilege installation access token** (App JWT → installation id → installation token). The token is minted **inside the host activity, used, and discarded — it never enters Temporal history** and never enters a container.
- **Agent git-ops:** the in-container agents have **no GitHub token at all** — they only `commit` to the shared tree. All `push`/`PR`/`merge` run host-side under the freshly-minted installation token.
- **Merge-on-approval:** at the final gate APPROVE, the host mints a token and the **App itself merges** feature→main (`mergePullRequest`, SHA-pinned). One onboarding act → the App performs git-ops AND the merge. The merge commit is authored by the App.
- **Repo-create:** `create_repo` / `ensureFeatureBranch(initRepo)` uses the same App with `administration:write`, `POST /orgs/{userOrg}/repos`, in the **user's org**.

> **[HUMAN DECISION — optional, later]** Add a *user* GitHub OAuth (separate from the App) purely for **human merge-commit attribution** (so `main`'s merge commit is attributed to the approving user rather than the App). Not required for v1.

> **[MUST-BEFORE-LAUNCH]** Confirm the Tacticl GitHub App is created and the onboarding install screen is wired into tacticl-web; the App must be installed on the user's org before any `create`/`select` build. (Project memory notes the App exists for `strategiz-io`; the user-facing onboarding install is the gap.)

---

## 7. Handoff manifest schema + artifact set + host-rendered delivery summary (+ TW narrative)

### 7.1 Artifact set (first-class repo docs + viewer mirror)

Per phase the persona writes both `results/artifact.md` (human-facing, rendered in the viewer; `+.html` for UX/delivery) and the **real committed artifact** on the feature branch:

| Role | Committed artifact |
|---|---|
| Product Owner | `docs/pdlc/{featureId}/prd.md` |
| Architect | `docs/pdlc/{featureId}/sad.md`, `adr-NNN-*.md` |
| Designer | `docs/pdlc/{featureId}/ux.md` (+ optional `ux-wireframe.html`) |
| Planner | `docs/pdlc/{featureId}/plan.md` (+ `results/tasks.json`) |
| Reviewer | `docs/pdlc/{featureId}/review.md` |
| Security | `docs/pdlc/{featureId}/security.md` |
| Tester | `docs/pdlc/{featureId}/test-report.md` |
| Technical Writer | `docs/pdlc/{featureId}/delivery-summary.md` + `.html` |
| DevOps | `docs/pdlc/{featureId}/deploy-report.md` |

`materializePdlcArtifact` (exists; writes `.tacticl/pdlc/{featureId}/<stem>.md` + commits) is kept as the **viewer mirror**; extend its stem map per role.

### 7.2 The thin per-phase manifest (agent-to-agent)

Add **one field** `manifestJson` to `AgentRoleResult` and **one** `readResultFile('manifest.json')` capture in `run-agent-role.ts` — mirroring the existing `taskPlanJson`/`tasks.json` seam (`run-agent-role.ts:539-544`) exactly. That single call is the *only* change to the agent activity.

```jsonc
// results/manifest.json  (per-phase, thin — pointers not whole docs)
{
  "schemaVersion": 2,
  "feature":  { "id":"<featureId>", "title":"…", "branch":"feature/<slug>-<featureId>" },
  "phase":    "product-owner",
  "status":   "complete",                                // complete | blocked
  "artifacts":[ { "kind":"prd", "path":"docs/pdlc/<featureId>/prd.md", "title":"PRD: …" } ],
  "acceptanceCriteria": [ "AC-1: …", "AC-2: …" ],        // load-bearing; tester verifies these
  "facts": {                                             // STRUCTURED, host-trusted (delivery facts)
    "filesChanged": ["src/x.ts"], "testResults": { "passed":12, "failed":0, "skipped":1 },
    "securityFindings": [ { "severity":"medium", "class":"authz", "remediated":true } ],
    "reviewVerdict": "approve"
  },
  "review":   null,                                      // reviewer-only: { verdict, findings[] }
  "deploy":   null,                                      // devops-only: { previewUrls[], status, healthChecks[] }
  "handoffNotes": "free-form markdown for the next persona",
  "linear":   null                                       // DEFERRED — emitted as data only when v1.1 lands
}
```

Host accumulates each phase manifest into a run-level canonical **`handoff.json`** committed on the feature branch (`docs/pdlc/{featureId}/handoff.json`):
```jsonc
{
  "feature": { "id","title","branch","repoUrl" },
  "phasePlan": { "phases":[…], "uxNeeded":false, "devopsNeeded":true },
  "currentPhase": "architect",
  "acceptanceCriteria": ["GET /health returns 200 with {status}"],
  "phases": { "product-owner": { "status":"approved","agentType":"product-owner",
              "headSha":"abc123","artifacts":[…],"facts":{…} } }
}
```
The next phase reads host-injected `context/handoff.json` + prior `docs/pdlc/{featureId}/*.md` directly off the shared tree. The workflow also keeps a durable in-memory mirror (`manifest`, `phaseRecords`) so correctness never depends on a git or Linear read.

### 7.3 Delivery summary — host-rendered FACTS + TW NARRATIVE (critique fix)

The summary's **facts are deterministic, host-rendered, NOT LLM-authored** — so "all tests pass" can never be hallucinated:
1. **`renderDeliverySummaryFacts({featureId, handoff})`** (NEW host activity, `PDLC_TQ_ORCHESTRATION`) reads the accumulated `handoff.json` + per-phase `facts` + `git log`/`git diff --stat` and emits a **structured, verified facts block**: PRD/SAD/ADR/plan/UX links, real `filesChanged`, real `testResults`, real `securityFindings`, real `reviewVerdict`, commit SHAs, diff links. This is real data, not prose.
2. **TECHNICAL_WRITER persona** (`runAgentRole`) receives that facts block as `context/facts.json` (read-only, authoritative) and writes ONLY the **narrative prose** sections (executive summary, "what changed and why", reviewer-comment synthesis, risk narrative). Its template forbids restating numeric facts except by reference to `facts.json`.
3. The host assembles `delivery-summary.html` (gate UI) and `delivery-summary.md` (committed) by **slotting the verified facts block + the TW narrative** together — facts win on any conflict. The final HITL gate parks on this. On APPROVE → gitleaks/SAST fail-closed → merge.

This resolves v1's open "P9 persona vs host activity" decision: **it is both** — host owns facts, TW owns prose.

---

## 8. Security model

- **In-pipeline agent (P7 Security)** — reasons over the diff for vuln classes (authz, injection, secrets, SSRF, unsafe deserialization, dependency risk) against `knowledge/security/checklist.md` (OWASP-style); severity-ranked findings + minimal remediations; a blocking finding sets `manifest.review`-style blocking status that **re-enters the combo's implementer loop** (manifest-verdict edge, §9). Complements — does not replace — the automated scan.
- **Automated fail-closed scan at merge** — `gitleaksScan` already blocks on `!scan.clean` and on a missing scanner (fail-closed). Add a **SAST host activity** at the final gate, also fail-closed. Merge is SHA-pinned (`--match-head-commit`).
- **Container hardening** — every agent runs `CapDrop ALL`, `no-new-privileges`, **no GitHub token** (host owns all push/PR/merge), OAuth access-token only, credential-shredded + token-revoked on teardown, reaper-on-entry for crashed attempts. Subscription `CLAUDE_CODE_OAUTH_TOKEN` with the **`withClaudeCodeIdentity` invariant upstream** — dropping it → Anthropic 429s that kill ALL OAuth LLM (incl. Strategiz mobile AI Signal). Never ship a build that drops it.
- **Untrusted input** — `context/user-prompt.md` is treated as untrusted by every persona (in `pdlc-feature.md` ground rules).
- **Gate forgery/replay defense** — askId + gateNonce echo + first-decision-wins on every gate; admission semaphore (`PDLC_AGENT_MAX_LIVE=1`) + deterministic container names prevent duplicate live containers.

### Build-agent egress (critique D1 — functional blocker, resolved)

> **[MUST-BEFORE-LAUNCH] D1.** The hardened `pdlc` profile is arbiter-network-only; a sandboxed Implementer/Tester **cannot `npm install`/`mvn` and therefore cannot build or run tests** — this is a hard functional blocker, not a nicety. Resolution (decided, not "full egress"):
> 1. **Pre-baked toolchain image** — the agent container image ships the languages/build tools/common deps the target stacks need (node+pnpm, JDK+gradle/maven, python, etc.), so the common path needs no network at all.
> 2. **Vetted package-registry proxy** — for the long tail, route package managers through a **read-only allowlisted registry proxy** (npm/maven/pypi mirrors) reachable from the agent network, NOT open internet egress. The proxy is the only egress; it caches and can be audited.
> This keeps the gitleaks/SAST fail-closed posture intact (no exfil path, no arbitrary outbound) while unblocking real builds. **[HUMAN DECISION]** which proxy implementation (e.g., Verdaccio + a maven mirror, or an existing artifact proxy) and the allowlist policy.

---

## 9. Idempotent re-entry (R1/R2) + combo visibility (R3)

### 9.1 Idempotent phase re-entry / tree reconciliation (the partial-commit problem)

> **[MUST-BEFORE-LAUNCH] R1/R2.** `runAgentRole` is `maximumAttempts:1` (non-idempotent — it spawns containers and **commits to the shared branch**). A worker/arbiter crash *mid-build* fails the phase **and leaves partial commits** on `feature/{slug}-{featureId}`. v1's claim that "rework is free" is **FALSE for code phases** — a naive re-run stacks half-finished work on top of half-finished work. Design (must land before the combo ships):
>
> - **Per-phase commit checkpoint.** Before each code phase, the workflow records `phaseBaseSha = currentHeadSha` into durable state (`phaseRecords[phase].baseSha`). The agent's commits during the phase are children of `phaseBaseSha`.
> - **Reaper-on-entry already destroys the crashed container + workspace** (`run-agent-role.ts`, deterministic name `pdlc-{featureId}-{role}-a{attempt}`). Extend it to **tree reconciliation**: on re-entry of a code phase, a host activity **`resetPhaseTree({featureId, role, phaseBaseSha})`** runs `git reset --hard {phaseBaseSha}` + clean on the shared tree, discarding the failed attempt's partial commits, so the re-run starts from a known-good base. Doc phases (P1–P4) overwrite their single artifact and need no reset, but get the same `baseSha` discipline for uniformity.
> - **Re-run as a fresh attempt** (`attempt+1`, new deterministic name) reading the same `handoff.json` (+ human feedback if a gate FEEDBACK), so rework for code is *idempotent* (tree reset → clean re-apply), not free-but-corrupting.
> - **Everything between agents** (gates, handoffs, git push, the deferred Linear, deploy host-ops) is already retry-safe (`maximumAttempts:3`, never-fatal posture).
>
> **[HUMAN DECISION]** whether `resetPhaseTree` hard-resets the whole tree to `phaseBaseSha` (simplest, recommended) or attempts a finer-grained revert of only the failed role's commits (riskier). Recommend hard-reset.

### 9.2 Combo visibility + heartbeat (R3)

The combo can run for hours with no human gate. Even with no hard cost cap:
- **Narrate combo progress UP to the analyst.** The workflow emits `onPipelineEvent` `{kind:'combo_progress', phase, attempt, reviewerLoop, summary}` after each combo sub-step (implementer chunk done, reviewer verdict, security pass, tester result), so "how's it going?" gets a real answer.
- **Heartbeat / "taking too long → surface to human."** A durable timer (`condition(..., COMBO_SOFT_DEADLINE)`) running alongside the combo; on expiry the workflow emits `{kind:'combo_slow', elapsed, lastPhase}` to the analyst (and dashboard) as a **soft surfacing** — not a kill, just visibility — and keeps going. Pairs with the kill-switch in §10.
- **Manifest-driven combo rework edge (the one non-human engine extension):** the reviewer writes `manifest.review = {verdict:'approve'|'changes_requested', findings[]}`; the **workflow** reads it (`readResultFile`) and on `changes_requested` re-dispatches Implementer→Reviewer via the existing rework machinery **triggered by the verdict, not a human signal**, bounded by `reviewerLoopMax`. Security can likewise set a blocking status re-entering the implementer loop. On exhaustion → escalate to the final human gate. No sub-DAG inside an agent.

---

## 10. Linear-deferred seam + the cost-cap-before-launch flag

### 10.1 Linear — DEFERRED entirely from v1 (seam only)

Canonical state = **git + JSON manifest (durable workflow local) + Temporal history.** Linear is **not built in v1.** Design the seam so v1.1 is additive and never on the correctness path:

- The agent **never** calls Linear (no network, honoring "correctness must not depend on Linear uptime"). It emits `manifest.linear.stories` as **data only** (the schema field is reserved in §7.2, left `null`/unprojected in v1).
- v1.1 adds best-effort host activities on `PDLC_TQ_PROJECTION` (`maximumAttempts:3`, never fatally throw): `publishFeatureToLinear`, `createLinearStories` (idempotent on `featureId+localKey`), `updateLinearGate`. These read the existing `manifest.linear.stories` / `handoff.json` — no agent or workflow-spine change required.
- **Central-Tacticl Linear projection** (one Tacticl workspace mirroring every tenant's features) is the v1.1 add-on the seam targets. Design it, don't build it.

> **[HUMAN DECISION — v1.1]** dedicated `PDLC_TQ_PROJECTION` queue (recommended — isolates a slow Linear API from the single agent slot) vs fold into `PDLC_TQ_ORCHESTRATION`.

### 10.2 Cost cap — NONE in v1, but a kill-switch is a launch-blocker

Per the locked decision, **no cost cap for v1** (dev / 0 users). BUT:

> **[MUST-BEFORE-LAUNCH] hard per-feature $/wall-clock kill-switch (escalate-to-gate).** Given the 2026-06 Anthropic cost-incident history, before this ever runs unattended there MUST be a per-feature ceiling on **both** cumulative spend AND wall-clock that, on breach, does NOT silently kill but **escalates to a synthetic human gate** ("this build has spent $X / run Yh — continue, refund-scope, or abort?"). Mechanics: the workflow tracks `spendEstimate` (sum of per-`runAgentRole` token cost) + `startedAt`; a durable `condition(..., KILL_DEADLINE)` alongside the combo (§9.2 heartbeat shares the timer); on breach → `parkOnGate({type:'phase', phase:'cost-escalation', surfaced:{spend, elapsed}})`. This is a *gate*, not a `terminate`, so no work is lost and the human decides. It is explicitly NOT a v1 *cap* — it is a v1 *circuit-breaker*. **[HUMAN DECISION]** the default $ and wall-clock thresholds.

---

## Consolidated human decisions

1. **Trivial fast-path floor** (§1) — may a trivial fix skip Planner/Reviewer/Security, or is the floor always `IMPLEMENTER+REVIEWER+TESTER+DELIVERY`? (Rec: hard floor; never skip review/test on code.)
2. **Dedup window / featureId salt** (§2.3) — allow identical-proposal re-runs across conversations? (Rec: salt with `conversationId`.)
3. **Combo extraction** (§2.4) — inline (rec) vs child `comboWorkflow`.
4. **PR-vs-diff gate review** (§5) — confirm default `'diff'`.
5. **PO persona sharing** (§3.3) — shared Qdrant namespace, separate prompts (rec; confirm).
6. **Proactive gate narration** (§4.5) — analyst proactively speaks on `gate_open` vs answers on next turn.
7. **Build-agent egress proxy** (§8) — which proxy + allowlist policy. **[MUST-BEFORE-LAUNCH]**
8. **resetPhaseTree granularity** (§9.1) — hard-reset to `phaseBaseSha` (rec) vs fine-grained revert. **[MUST-BEFORE-LAUNCH]**
9. **Optional human merge attribution** (§6) — add user GitHub OAuth later (not v1).
10. **Linear queue** (§10.1) — dedicated `PDLC_TQ_PROJECTION` (rec) vs fold-in (v1.1).
11. **Kill-switch thresholds** (§10.2) — default $ / wall-clock. **[MUST-BEFORE-LAUNCH]**
12. **Repo-create owner** — confirmed by project policy = the user's own connected org, never the tacticl org (§5/§6).

## MUST-BEFORE-LAUNCH summary (the launch-blockers, distinct from nice-to-haves)

- **D0** admin re-assert (`aligned && alignedByAdmin`) gating the in-arbiter `startChild` (§2.2).
- **D1** build-agent egress: pre-baked toolchain image + vetted registry proxy (§8) — without it Implementer/Tester cannot build/test at all.
- **R1/R2** idempotent phase re-entry + `resetPhaseTree` tree reconciliation (§9.1) — without it a mid-build crash corrupts the shared branch.
- **GitHub App onboarding install** wired into tacticl-web + installed on the user's org (§6).
- **Per-feature $/wall-clock kill-switch → escalate-to-gate** (§10.2).
- **Gate-list generalization** (`parkOnGate` over ordered `GateSpec[]` + single `resolveGate`) — the hardest single piece; the 8–11-phase dynamically-scoped flow cannot be expressed until it lands (§4.2).

## Build delta (reuse vs new)

**Reuse verbatim:** `runAgentRole` + ephemeral container lifecycle + reaper; gate primitives (`decisionByAsk`/`openAsk`/`gateNonce`/first-decision-wins/`condition(MERGE_GATE_TTL)`); `decideRework` + `MAX_REWORK_ROUNDS_HARD_CEIL=16` + per-gate budgets; `computeDownstreamClosure`; host-git `pushBranch`/`openPullRequest`/`mergePullRequest`/`gitleaksScan`; `materializePdlcArtifact` + `PDLC_SHARED_WORKSPACE` + `readResultFile`; `continueAsNew` snapshot pattern; `WorkspaceAssembler`; `MongoRegistryClient`; `github-app-auth.ts`; retro `learning-proposer` → `agent_knowledge`/Qdrant; tacticl-core `PdlcV2Service.resolveCheckpoint → signalDecision`.

**Build new:** `temporal/feature-pipeline-workflow.ts` + `temporal/feature-types.ts` + `temporal/feature-phase-plan.ts` (pure validator); generalize 2 fixed gates → ordered `GateSpec[]` + `parkOnGate` + `resolveGate`/`cancelFeature`/`featureStatus`; the deploy-review gate type + `deployPreview`/`promoteProd`/`readDeployStatus` activities; manifest-driven combo rework edge; `onPipelineEvent` + `pipelineEvents[]`/`activePipelines[]` on `conversationSessionWorkflow`; extend `InvokePersonaResult.dispatch`/`gateDecision`; the `startChild(ABANDON)` launch + `stableFeatureId`; host-git `ensureFeatureBranch`/`initRepo`/`writeHandoff` + `resetPhaseTree`; `renderDeliverySummaryFacts` host activity (facts) feeding the TECHNICAL_WRITER persona (narrative); the build-agent egress proxy + toolchain image; the kill-switch escalate-gate; the cost/heartbeat timers; `AgentRoleResult.manifestJson` + one `readResultFile('manifest.json')`; the `pdlc-feature` registry seed (`pipelines/`, `claude-configs/`, `agents/{11 roles}.json`, `templates/{11 roles}.md`, `knowledge/{role}/*.md`); the per-feature Retro re-wire (from weekly batch to silent post-completion phase); worker wiring (re-export `featurePipelineWorkflow`, register new activities). Linear projection activities = **deferred (seam reserved, not built).**

Relevant verified source paths: `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/conversation/conversation-mcp.ts`, `.../temporal/conversation-session-workflow.ts`, `.../temporal/pdlc-run-workflow.ts`, `.../temporal/activities/run-agent-role.ts`, `.../temporal/activities/host-git-wiring.ts`, `.../shell/github-app-auth.ts`, `.../shell/github-client.ts`, `.../temporal/dag.ts`, `.../temporal/activities/artifact-materializer.ts`, `.../retro/retro-agent.ts`, `.../retro/learning-proposer.ts`.

---

# BUILD SEQUENCE

Verified the blueprint's file claims against the arbiter source. One correction to fold in: the raw git functions live in `shell/host-git-activities.ts` (header literally says "NOT wired to any caller"), while the Temporal activity wrapper is `temporal/activities/host-git-wiring.ts` (`makeHostGitActivities`) — the blueprint conflates them. And `container-config.ts` confirms the `pdlc` profile is `CapDrop ALL` + no-new-privileges + API-key-only but **NOT network-fenced yet** (the dedicated internal-only PDLC network is documented-but-off) — this is the egress blocker, code-confirmed.

Here is the dependency-ordered build sequence.

---

# BUILD SEQUENCE — Analyst-Orchestrated Agentic PDLC (v2 blueprint)

**Reading order = build order: A → B → C → D.** But the true critical path interleaves A and B: the ORCHESTRATOR spine (A) can be authored against stubs immediately, yet it cannot go *green end-to-end* until the host-git/egress pieces of B land. So B's git-activity slice is a co-requisite of A, not strictly after it. Sequence each item against its real dependency, not its bucket letter. Effort = focused engineer-days; `[swarm:N]` = parallel-agent fan-out.

---

## WAVE A — ORCHESTRATOR (the foundation; everything hangs off this)

### A0 · Type seams + manifest capture — ~1–1.5d · `[swarm:2]` · SERIAL (unblocks all)
- **BUILD-NEW:** `temporal/feature-types.ts` (`FeaturePipelineInput`, `FeaturePipelineCarryOver`, `GateDecision` = `MergeDecision` widened with `APPROVE|REJECT|FEEDBACK`+`feedback?`+`targetPhase?`, `FeatureRef`, `PipelineEvent`, `DecipheredIntent`, `PhasePlan`, thin `manifest.json` schema §7.2); add `AgentRoleResult.manifestJson` to `pdlc-types.ts`; **one** `readResultFile('manifest.json')` capture in `run-agent-role.ts` mirroring the existing `taskPlanJson` seam (~L539).
- **REUSE:** `temporal/activities/run-agent-role.ts`, `temporal/pdlc-types.ts`.
- **PARALLEL:** no — pure-additive but every later wave compiles against it. Land first.
- **HUMAN:** none.

### A1 · `featurePipelineWorkflow` spine + gate-machinery rewrite (THE KEYSTONE) — ~4–5d · `[swarm:2, low parallelism]` · SERIAL
- **BUILD-NEW:** `temporal/feature-pipeline-workflow.ts` (explicit **linear** phase machine, durable locals `phaseIndex/phaseRecords/manifest/featureBranch/reworkByGate/decisionByAsk/openAsk`); `temporal/feature-phase-plan.ts` (pure deterministic validator — force-adds PLANNER/REVIEWER/SECURITY/TESTER/DELIVERY on any IMPLEMENTER, DELIVERY-last, topo-canonical); **generalize the two fixed inline gates into an ordered gate list + `parkOnGate(GateSpec)` helper + single `resolveGate` signal** (+ `cancelFeature` signal, `featureStatus` query). Wire P1–P4 conditional phases.
- **REUSE (lift, mostly verbatim):** gate body from `temporal/pdlc-run-workflow.ts` (`approveMergeSignal`/`decisionByAsk`/`openAsk`/`gateNonce`/first-decision-wins/`condition(MERGE_GATE_TTL≈7d)`, ~L658-820); `temporal/dag.ts` (`decideRework`, `computeDownstreamClosure`, `MAX_REWORK_ROUNDS_HARD_CEIL=16`, `shouldSkip`/`computeSkipSet` for Designer optionality) **verbatim**; `continueAsNew` discipline from `conversation-session-workflow.ts` (phase-boundary only, never while parked).
- **PARALLEL:** lowest — this is the single dependency bottleneck. Protect it; mandatory replay test mirroring `__tests__/pdlc-workflow-replay.test.ts`.
- **HUMAN DECISION:** gate-review default `'diff'` vs `'pr'` (recommend `'diff'`); trivial fast-path floor (recommend never skip REVIEWER+TESTER on code).

### A2 · Combo block P5–P8 + manifest-verdict rework edge — ~3–4d · `[swarm:2]` · SERIAL after A1
- **BUILD-NEW:** inline sequential **Implementer ⇄ Reviewer** (bounded loop on `manifest.review.verdict`, `reviewerLoopMax`) **→ Security → Tester**; the one engine extension = a **non-human** rework edge (reuse `decideRework`, triggered by `changes_requested`/security-blocking in the manifest, not a human signal).
- **REUSE:** `run-agent-role.ts` (one call per persona), `dag.ts` rework math, `admission-semaphore.ts` (`PDLC_AGENT_MAX_LIVE=1`), `reaper.ts`.
- **PARALLEL:** partial — combo prompts (Wave C) author alongside.
- **HUMAN DECISION:** keep combo inline (recommend) vs extract `comboWorkflow`; combo egress posture (→ Wave B).

### A3 · Launch bridge + admin re-assert + intent dedup + idempotent re-entry — ~3d · `[swarm:2]` · SERIAL after A1 (child must exist)
- **BUILD-NEW (greenfield — confirmed zero `startChild` in repo):** in `conversation-session-workflow.ts`, replace the audit-only dispatch (~L587) with `startChild(featurePipelineWorkflow, {parentClosePolicy: ABANDON, workflowId: feat-{featureId}, workflowIdReusePolicy: REJECT_DUPLICATE})`; `stableFeatureId(align)` pure hash (§2.3, **replaces v1's `pdlc-{turnId}`**); `WorkflowExecutionAlreadyStarted` catch → "already running"; re-derive handle post-`continueAsNew` from carried `activePipelines[]`. **D0 admin re-assert**: read fresh non-forgeable `align.aligned && align.alignedByAdmin` *in the arbiter* before `startChild`, never from a model arg; refuse on repo-mismatch.
- **REUSE:** existing alignment store (`conversation-session-store.ts`, `conversation-mcp.ts:610-685`), `start_pipeline` tool stays a terminal pre-assert (do **not** change it), `ConversationCarryOver` carry pattern.
- **Idempotent re-entry:** the durability boundary is `runAgentRole` = `maximumAttempts:1` (non-idempotent — spawns containers/commits). Crash-mid-agent → workflow narrates "agent crashed, retry?" and treats retry as a feedback/rework iteration. True idempotent agent re-entry is **out of v1 scope** — needs lead sign-off as the durability boundary.
- **HUMAN DECISION:** dedup window (salt hash with `conversationId`/coarse epoch so identical re-aligns weeks later don't collide).
- **`[MUST-BEFORE-LAUNCH]` D0:** this admin re-assert is the only thing between "model emitted a tool_use" and "containers spend money on `main`." Load-bearing.

### A4 · Final delivery gate + merge — ~2d · `[swarm:2]` · SERIAL after A2
- **BUILD-NEW:** `renderDeliverySummaryFacts` host activity (deterministic facts block from `handoff.json`+`git log`+`git diff --stat`); final `parkOnGate(type:'final-merge')`; new SAST host activity (fail-closed). Resolves v1's "P9 persona vs host" → **both**: host owns facts, TW persona owns prose.
- **REUSE:** `shell/host-git-activities.ts` `gitleaksScan` (fail-closed, exists), `openPullRequest`/`mergePullRequest` (SHA-pinned `--match-head-commit`, exist) via `temporal/activities/host-git-wiring.ts`.

### A5 · Conversation narration bridge (UP direction) — ~2d · `[swarm:2]` · after A1/A3
- **BUILD-NEW:** `onPipelineEvent` signal handler on `conversation-session-workflow.ts` buffering `pipelineEvents[]` into `ConversationCarryOver` (next to `dispatchedRuns[]`); turn loop drains it; `gateDecision?` relay via `getExternalWorkflowHandle(feat-…).signal(resolveGate,…)`; pipeline calls `notifyConversation → signal(onPipelineEvent)` at milestones. Extend `InvokePersonaResult.dispatch`.
- **REUSE:** `PdlcV2Service.resolveCheckpoint → signalDecision` (tacticl-core) as the **second, equivalent** dashboard decision surface onto the same `resolveGate` — **KEEP**, do not delete.
- **HUMAN DECISION:** proactive gate narration (can interrupt voice) vs answer-on-next-turn.

---

## WAVE B — CLIENTS (host-git, GitHub App, egress) — co-requisite of A for end-to-end green

### B1 · Host-git feature ops — ~2–3d · `[swarm:3]` · PARALLEL with A1 (independent, unit-testable)
- **BUILD-NEW (greenfield, D4-confirmed):** `ensureFeatureBranch({repo,featureId,branchPattern})` (+ `initRepo` + first-commit-on-`main` for `create` mode, then cut `feature/{slug}-{featureId}`; `select` mode = fetch+branch); `writeHandoff({featureId,phase,manifest})` (write+commit thin JSON + phase markdown).
- **REUSE:** `shell/host-git-activities.ts` `prepareRepo` is the seed; everything else (`pushBranch`/`openPullRequest`/`mergePullRequest`/`gitleaksScan`) exists; wire via `temporal/activities/host-git-wiring.ts` on `PDLC_TQ_REPO`.
- **`[MUST-BEFORE-LAUNCH]`** repo-create targets the **user's connected org, never the tacticl org** (per `project_repo_owner_is_user_org`).

### B2 · GitHub App onboarding install + merge-on-approval + repo-create — ~2–3d · `[swarm:2]` · PARALLEL with A
- **REUSE (mostly built):** `shell/github-app-auth.ts` (JIT installation-token mint, ≤1h least-priv per-repo, `administration` perm, Vault `secret/{context}/github-app`); `shell/github-client.ts` `createRepo`. Token minted **inside the host activity, used, discarded — never enters Temporal history or any container.**
- **BUILD-NEW:** the **onboarding install UX** (tacticl-web "Connect GitHub" → GitHub App install/consent screen on the user's org) — this is the real gap; the App auth chain exists. Merge-on-approval = App merges feature→main at final gate (already wired in A4 via host-git).
- **`[MUST-BEFORE-LAUNCH]` HUMAN PREREQ:** **Create the Tacticl GitHub App** (project memory notes one exists for `strategiz-io`; the user-facing onboarding install across an arbitrary user org is the gap). App must be installed on the user's org before any `create`/`select` build.
- **HUMAN DECISION (later, optional):** separate user GitHub OAuth purely for human merge-commit attribution; not required for v1.

### B3 · Pre-baked toolchain image + registry proxy egress fence — ~3–4d · `[swarm:2]` · PARALLEL, but gates real builds
- **BUILD-NEW (D1 — confirmed functional blocker):** bake node+pnpm / JDK+gradle+maven / python into the agent image (`DEFAULT_AGENT_IMAGE` in `container-manager.ts`); stand up a **read-only allowlisted registry proxy** (npm/maven/pypi mirrors) as the *only* egress; **switch on the dedicated internal-only PDLC network** that `container-config.ts` already documents-but-leaves-off (lines 44-70: "Phase 1 does NOT switch the PDLC network on").
- **REUSE:** `shell/container-config.ts` (`pdlc` profile: CapDrop ALL + no-new-privileges + API-key-only already there), `container-manager.ts` (`agentImage`/network seam).
- **`[MUST-BEFORE-LAUNCH]` D1:** a sandboxed Implementer/Tester on the hardened profile **cannot `npm install`/`mvn` today** → cannot build or run tests. Hard blocker. The proxy keeps gitleaks/SAST fail-closed posture intact (no exfil path). Until the network is fenced, untrusted-intake dispatch must stay gated (the config file says so explicitly).

---

## WAVE C — KNOWLEDGE (the "beefing up" — data, not code) — ~3–4d · `[swarm:4]` · PARALLEL from B onward

- **BUILD-NEW (mirror `pdlc-fix` seed → new `pdlc-feature` set):** `registry-seed/tacticl/pipelines/pdlc-feature.json` (ordered phases + combo + `skip_when`); `claude-configs/pdlc-feature.md` (shared ground rules: scope discipline, **untrusted `user-prompt.md`**, **commit-never-push**, write `manifest.json` last, `report.sh complete`); `agents/{11 roles}.json`; `templates/{11 roles}.md` (the system prompts — biggest lever); `knowledge/{role}/*.md` (incl. `security/checklist.md` OWASP). **Research-as-skill** = widen allowed CLI tools/MCP in PO/Architect/Analyst registry profiles + `knowledge/{role}/research-method.md` — **no new container, no new phase**.
- **REUSE:** `MongoRegistryClient`/`registry-client.ts`/`registry-cache.ts`, `registry-seed/tacticl/pipelines/pdlc-fix.json` (shape template), `conversation/persona-registry.ts` (analyst brain — separate registry, do **not** merge), `template-renderer.ts`, `knowledge-loader.ts`/`knowledge-indexer.ts`.
- **PARALLEL:** highly — seed authoring needs no engine code; iterate prompts by re-seeding Mongo. This is the "rest is beefing up persona knowledge" payoff.
- **HUMAN DECISION:** shared Qdrant namespace between conversational PO and build `product-owner`, separate prompts (recommend); never merge the two registries.

---

## WAVE D — LEARNING (Retro per-feature re-wire) — ~1–2d · `[swarm:2]` · LAST (depends on a completed feature run)

- **REUSE (built as weekly batch):** `retro/retro-agent.ts` → `retro/learning-proposer.ts` → Mongo `agent_knowledge`; Qdrant RAG via `qdrant-client.ts` + `knowledgeNamespace` (`run-agent-role.ts`); `shell.ts` already persists `learning` push events from RETRO_ANALYST.
- **BUILD-NEW:** the **per-feature re-wire** — append `P11 RETRO_ANALYST` unconditionally + silently at pipeline completion (one `runAgentRole` + `learning-proposer` write on completion), instead of the weekly cron. Feeds Qdrant `knowledgeNamespace={repo}/global` → closes the loop into Wave C's persona knowledge.
- **HUMAN DECISION:** none blocking; confirm namespace scoping matches Wave C.

---

## CRITICAL PATH & PARALLELISM

```
A0 ──► A1 ─┬─► A2 ─► A4 ─┐
           ├─► A3 ───────┤
           └─► A5 ───────┤
B1 (∥A1) ──────────────► (needed for A green)
B2,B3 (∥A) ────────────► (needed before real builds)
C  (∥ from B) ─────────► (needed before first real feature run)
                          └─► D (after first completed run)
```
- **Critical path:** A0 → A1 → A2 → A4, with B1 as A's green-gate co-req. ~**18–24 engineer-days single-threaded; ~2 weeks** with the swarm model.
- **A1 (gate-machinery rewrite) is the single bottleneck** — the blueprint's "hardest single piece." Start it first, protect it, replay-test it.
- **B1/B2/B3/C all parallelize off A1**; D is strictly last.

## MUST-BEFORE-REAL-USERS (launch blockers)
1. **Cost cap / spend ceiling** — NOT in the blueprint and the #1 missing guardrail. The launch path is "model emits tool_use → containers run on `main`." Add a per-feature + per-conversation USD ceiling enforced at `startChild` (A3) and as a combo-loop budget (A2), surfaced as an `ESCALATED` terminal. **Build before any non-author user.**
2. **D0 admin re-assert** (A3) — non-forgeable `aligned && alignedByAdmin` read in the arbiter before `startChild`.
3. **D1 egress fence** (B3) — toolchain image + registry proxy + the PDLC network actually switched on; without it Implementer/Tester can't build, and the hardened profile isn't network-isolated yet (untrusted dispatch must stay gated until this lands).
4. **Tacticl GitHub App created + onboarding install wired into tacticl-web** (B2) — App must be installed on the user's org before any build; repos created **only** in the user's org.
5. **Fail-closed scans at merge** (A4) — `gitleaksScan` (exists) + new SAST, SHA-pinned merge.
6. **`withClaudeCodeIdentity` invariant preserved** in every OAuth agent boot (cross-cutting) — dropping it 429s all OAuth LLM including Strategiz mobile AI Signal.

## Deferred behind a green run (NOT blockers)
- tacticl-core destructive deletions (`RoleIdentityLoader` + `role-identities/*.md`, `PlaybookSpecResolver` hardcode, legacy `PdlcV2Service` direct path / `ConversationService` state machine — **keep `resolveCheckpoint`**) — land only after a `pdlc-feature` run completes end-to-end. New path green → switch callers → delete.
- Linear projection (`PDLC_TQ_PROJECTION`) — seam only in v1.
- `comboWorkflow` extraction — only if combo history proves large.

**Relevant files (absolute):**
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/temporal/pdlc-run-workflow.ts` (gate body to lift, ~L658-820)
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/temporal/conversation-session-workflow.ts` (audit-only dispatch ~L587 → `startChild`)
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/temporal/activities/run-agent-role.ts` (`manifestJson` capture seam ~L539)
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/temporal/dag.ts` (`decideRework`/`computeDownstreamClosure`/`shouldSkip` — reuse verbatim)
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/shell/host-git-activities.ts` (`prepareRepo`→`ensureFeatureBranch`; `pushBranch`/`openPullRequest`/`mergePullRequest`/`gitleaksScan` reuse) + `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/temporal/activities/host-git-wiring.ts` (activity wrapper)
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/shell/github-app-auth.ts` (JIT token) + `shell/github-client.ts` (`createRepo`)
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/shell/container-config.ts` (PDLC network fence — documented, OFF) + `shell/container-manager.ts` (`agentImage`)
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/retro/{retro-agent.ts,learning-proposer.ts}` (weekly→per-feature)
- `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/registry-seed/tacticl/pipelines/pdlc-fix.json` (template for `pdlc-feature` seed)
- New: `temporal/feature-pipeline-workflow.ts`, `temporal/feature-types.ts`, `temporal/feature-phase-plan.ts`

---

# FINAL CRITIQUE — GO-WITH-FIXES

Verified the foundational claims against `cidadel-ai-arbiter/packages/server/src` directly. The ground-truth table holds: no `startChild`/`ParentClosePolicy` anywhere (launch bridge is greenfield), two fixed gates only (`planGateRole`/`mergeGateRole` in `resolve-bundle.ts`+`pdlc-types.ts`), `taskPlanJson` capture exists / `manifestJson` does not (`run-agent-role.ts:539`), host-git push/PR/merge/gitleaks exist / `ensureFeatureBranch`/`writeHandoff`/`initRepo` do not, `github-app-auth.ts` exists, egress proxy is explicitly "a later phase" (`container-config.ts:48-69`, D1 confirmed unbuilt). But reading the actual D0 code surfaced things the blueprint gets subtly wrong and several silent decisions.

---

VERDICT: GO-WITH-FIXES. Topology is sound, foundations check out, the gate rewrite is correctly scoped as real work, the security spine is right. Four must-fix-in-spec items and two genuine internal inconsistencies before code.

FIRST THING TO BUILD: the launch bridge in `processTurn` — `startChild(featurePipelineWorkflow, ABANDON, workflowId=feat-{featureId})` gated on the **in-workflow** `alignmentState.aligned && alignmentState.alignedByAdmin`, plus a stub `featurePipelineWorkflow` that records "started" and fires `getExternalWorkflowHandle(conversationWorkflowId).signal(onPipelineEvent, …)`. Smallest end-to-end slice that proves the two-workflow bridge AND lands the load-bearing D0 invariant. Build it with the corrected re-assert (see F1).

---

PER-CHECKLIST:

1. startChild-ABANDON + admin re-proven in arbiter — PARTIAL/WRONG MECHANISM. The launch-as-`startChild` and the requirement to re-prove admin are correct in spirit, but §2.2's pseudo-code `const align = await readAlignmentState(conversationId)` (an activity round-trip to the persisted store) is wrong. The verified workflow already carries `alignmentState` as durable, deterministic **workflow state**, set non-forgeably from `turn.isAdmin` in the pre-turn affirmation reducer (`conversation-session-workflow.ts:400, 528-533`; comment at :525 "it is NEVER a model-supplied arg"). An activity read adds non-determinism + a TOCTOU window for zero gain. Fix: re-assert against the in-workflow variable. (Note: defense-in-depth already exists — the MCP alignment-gate at `conversation-mcp.ts:600-685` denies `start_pipeline` if `!alignedByAdmin` inside the activity — so the workflow re-assert is a correct second layer, not the only one.)

2. intent-level dedup — INCOMPLETE + COLLIDES WITH EXISTING. `stableFeatureId` is sound, but the workflow ALREADY dedupes dispatch by `runId` via `dispatchedRuns[]` (`conversation-session-workflow.ts:587`, carried across continueAsNew at :421). The blueprint adds `activePipelines[]`/featureId dedup without saying whether `dispatchedRuns` is retired or kept as audit-only — silent overlap. Worse: `WorkflowIdReusePolicy.REJECT_DUPLICATE` rejects a same-featureId relaunch even after the prior pipeline FAILED, so a legitimate retry-after-failure is blocked. Almost certainly want `ALLOW_DUPLICATE_FAILED_ONLY`. The [HUMAN DECISION] "dedup window" tag touches this but doesn't name the reuse-policy semantics.

3. idempotent re-entry + dirty-tree reconciliation (R1/R2) — CANNOT CONFIRM; ABSENT FROM THE DOCUMENT. The text cuts off at §8/D1 ("…unblocking real buil"). R1/R2/R3, §9 (combo internal loop + code-rework reconciliation), and §10 (queues/Linear) are *referenced* (§1, §4.3 "see §9") but not present in the provided blueprint, so their correctness is unverifiable here. Beyond the missing text: the described mechanisms don't cover R2 — §5's host-git additions are only `ensureFeatureBranch`+`writeHandoff`; dirty-tree reconciliation of partial commits on the shared feature branch (crash mid-`runAgentRole`, or code-phase rework) implies an un-enumerated greenfield reset/revert-to-SHA host activity. R1 (no double-launch on conv replay/continueAsNew) IS covered by `activePipelines[]` carryover + deterministic `getExternalWorkflowHandle` + REJECT-duplicate. R2 is neither in the text nor in the activity list.

4. gate-machinery rewrite as real work (not verbatim) — CORRECT. §4.2 explicitly tags it `[ORCHESTRATOR GAP — budget for a REWRITE]`; source confirms exactly two fixed gates, so the ordered-gate-list generalization is genuinely a control-spine rewrite. Only soft spot: the existing `mergeGateRole` does `computeDownstreamClosure`; the linear doc-gates correctly need none, but the FINAL/deploy gates' rework-into-combo closure is deferred to the absent §9.

5. host-rendered delivery summary — MOSTLY CORRECT, ONE OVERSTATEMENT. Facts-host-rendered / TW-prose-only / facts-win is the right split; `materializePdlcArtifact` exists as viewer mirror, `renderDeliverySummaryFacts` correctly labeled greenfield. But "all tests pass can never be hallucinated" is overstated: `git diff --stat`/SHAs/filesChanged are host-trusted, yet `testResults`/`securityFindings` come from the agent-written `manifest.facts`. To be non-hallucinable, the tester must emit a host-captured machine artifact (e.g. JUnit XML parsed host-side), not a manifest integer. As written, some "facts" are agent-asserted.

6. deploy-review gate as distinct live-output type — CORRECT AS A TYPE, UNDERBUILT AS PLUMBING. `gateType:'deploy-review'` with previewUrls/healthChecks/logsPointer is the right first-class distinction. But it's the least-built path: today `devops` is just-another-agent-role released post-merge (`pdlc-run-workflow.test.ts:152-167`), not a host deploy with live URLs. It needs new gate type + new host activities (`deployPreview`/`promoteProd`/`readDeployStatus`, all greenfield) + a deploy-target credential/egress posture that §8's D1 (package-registry proxy only) does not provide. How the devops agent authenticates to and reaches the deploy target is unaddressed.

7. dynamic scoping — SOUND, BUT INTERNALLY INCONSISTENT. The pure `feature-phase-plan.ts` validator with force-add invariants + topological canonicalization is the right design. Inconsistency: §1 gives THREE conflicting floors in one section — the worked example `[IMPLEMENTER, TESTER, DELIVERY]` (no reviewer/security), the invariant "{IMPLEMENTER} ⇒ force-add PLANNER, REVIEWER, SECURITY, TESTER, DELIVERY", and the recommendation "floor = IMPLEMENTER+REVIEWER+TESTER+DELIVERY" (no planner/security). These cannot all be true. This is a real contradiction, not merely the open [HUMAN DECISION].

8. research-as-skill — CORRECT INTENT, MINOR FRICTION. Research-as-shared-capability (not a container) is right and matches that `RESEARCHER` is only an artifact-materializer stem + the `market-researcher` conversation persona. Two frictions: dropping the in-DAG `investigator/researcher` container is a real change from the built `artifact-materializer.ts:52,60`; and §3.2's "read-only search proxy" for in-pipeline PO/Architect needs web egress that §8/D1 (registry proxy only) doesn't grant — the conversational analyst's WebSearch routes to sandbox (`conversation-mcp.test.ts:242`), but in-pipeline web research is unprovisioned.

---

DECISIONS STILL SILENTLY MADE (not surfaced as [HUMAN DECISION]):

- S1 (biggest): the real authority shift. Today the actual dispatch enforcement is the **Java client's `EXPLICIT_TRIGGER → requireAdmin → submit`** (stated verbatim in `conversation-mcp.ts` start_pipeline + alignment-gate comments). Moving dispatch into the arbiter `startChild` deletes that enforcement point and makes the inbound signal's `turn.isAdmin` the sole identity assertion. The blueprint never states who guarantees `turn.isAdmin` is authenticated/non-forgeable at the tacticl-core→arbiter signal ingress once Java requireAdmin is gone. requireAdmin must MOVE to the signal ingress, not vanish.
- S2: `REJECT_DUPLICATE` silently blocks retry-after-failure (want `ALLOW_DUPLICATE_FAILED_ONLY`).
- S3: fate of the existing `dispatchedRuns[]` runId-dedup vs the new featureId-dedup — unreconciled.
- S4: devops deploy-target auth + egress — unmade.
- S5: delivery-summary `testResults`/`securityFindings` provenance (agent vs host-verified) — assumed trusted.
- S6: alignment re-read via activity vs workflow state — silently picks the non-deterministic option (F1).
- S7: combo internal-loop bound + its partial-commit reconciliation — deferred to absent §9.

MUST-FIX BEFORE BUILDING:
- F1: re-assert admin from in-workflow `alignmentState`, not an activity read; and explicitly relocate `requireAdmin` to the arbiter signal ingress (S1).
- F2: set `ALLOW_DUPLICATE_FAILED_ONLY` and state the `dispatchedRuns` vs `activePipelines` relationship.
- F3: resolve the §1 trivial-floor contradiction to a single stated floor (recommend `IMPLEMENTER+REVIEWER+TESTER+DELIVERY`, with SECURITY/PLANNER force-added above `sizing:'trivial'`).
- F4: supply §9/§10 + R1/R2/R3 (the provided document is truncated at §8) and add the un-enumerated dirty-tree reconcile host activity.

Load-bearing evidence files: `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/temporal/conversation-session-workflow.ts` (alignmentState as workflow state :400/:528-533; dispatchedRuns dedup :587), `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/conversation/conversation-mcp.ts` (terminal start_pipeline :179; admin alignment-gate + "Java client runs requireAdmin" :600-685), `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/temporal/run-agent-role.ts:539` (taskPlanJson, no manifestJson), `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/packages/server/src/shell/container-config.ts:48-69` (egress proxy unbuilt, D1).