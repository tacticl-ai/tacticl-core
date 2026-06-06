# Arbiter `pdlc-fix` Activation — Plan (Discord text bug → fix → PR)

**Status:** scoped 2026-06-06 (read-only investigation of `cidadel-ai-arbiter`). REVIEW + decisions (§6) required before writing arbiter code.
**Goal (lean):** drop text (later: screenshot) in a Discord alert channel → "Send to PDLC" → autonomous Claude Code fix → ONE PR (the only human gate) → merge on approve. No full-PDLC rigor (no docs roles, no mid-run HITL, no interview for fixes).
**Front half (committed in tacticl-core):** Discord trigger + account-link + attachment extraction + config registry submit `pipeline_name=pdlc-fix` + relay callbacks. See [[project_discord_alert_to_fix]].

## 1. State of readiness
The routing/submission spine is LIVE, not stubbed: with `PDLC_TEMPORAL_ENABLED=true` the arbiter boots a real Temporal worker registering `runAgentRole` + `resolvePipelineBundle` + `emitPipelineCallback` (`server.ts:601-614`), `pdlcRunWorkflow` is bundled (`workflows.ts:24`), `decideExecutor` routes `pdlc-fix` to Temporal when allow-listed, and tacticl already submits with `idempotency_key=pipelineRunId`. **But the run cannot complete:** a fresh `pdlc-fix` starts then HANGS on three intake activities the worker never registers (`pdlc-run-workflow.ts:388-389,707` call `extractFromIntake/classifyIntake/routeIntake`; `server.ts:591-595` omits them — and the `patched('pdlc-intake-phaseA-v1')` comment is WRONG: `patched()` is true for new runs). Beyond that: no `tacticl/pipelines/pdlc-fix.json` (fail-closed at first activity), no PR ever opened/merged (host-git dormant; `pdlc-run-workflow.ts:464` "merge wired in Phase 4" = log-only), no `SignalPipelineDecision` handler (parked runs unreachable), and no Temporal server proven deployed (defaults `localhost:7233`).

## 2. Ordered activation steps (all blockers are ARBITER-side)
- **A7 [BLOCKER, infra] (M)** — Stand up/point at a reachable Temporal frontend; set `TEMPORAL_ADDRESS`; ensure `default` namespace. (`pdlc-config.ts:48-49`)
- **A1 [BLOCKER] (S)** — Disarm Phase A for new runs: gate `runIntakePhaseA()` on an explicit `input.skipIntake` (tacticl supplies classification) instead of `patched()`. Files: `pdlc-run-workflow.ts:388-389`, `pdlc-types.ts` (PdlcRunInput +`skipIntake`), `shell.ts:410-420`. **#1 review flag.**
- **A2 [BLOCKER] (M)** — Author `tacticl/pipelines/pdlc-fix.json` + `tacticl/agents/<type>.json` (model/max_turns/resource_class live in agent defs: `resolve-bundle.ts:106`, `run-agent-role.ts:355`) + a committed `scripts/seed-registry.ts`. Registry store = `AGENT_REGISTRY_PATH` files or Mongo `cidadel_registry` (`registry-client.ts:86-88,196-198`).
- **A8 [HIGH, config] (S)** — `PDLC_TEMPORAL_ENABLED=true` AND `PDLC_TEMPORAL_PIPELINES=pdlc-fix`; confirm not in `PDLC_FORCE_LEGACY_PIPELINES` (`decide-executor.ts:64-74`).
- → **Milestone 1: bug → fix → COMMIT proven** (no PR yet). Needs only A7+A1+A2+A8 (+T1/T2). No repo-owner decision required.
- **A3 [BLOCKER] (L)** — Wire host-git PR/merge: `makeHostGitActivities` factory (A3a) → add 5 fns to `PdlcActivities` (`pdlc-types.ts:319`, A3b) → register `PDLC_TQ_REPO` worker (`worker.ts:191`, A3c) → assemble in `server.ts:601-614` (A3d) → in workflow, after fixer/test run `gitleaksScan→pushBranch→openPullRequest`, surface PR URL+headSha into the `blocked` callback; on `APPROVE_MERGE` call `mergePullRequest(--match-head-commit approvedSha)` (replace log-only `pdlc-run-workflow.ts:459-468`, A3e) → deterministic branch `pdlc/{intakeId}` (A3f).
- **A5 [BLOCKER] (M)** — Implement the `SignalPipelineDecision` gRPC handler (declared `proto/arbiter-pipeline.proto:11`, no impl): authenticated decision → `getHandle(workflowId).signal(approveMerge, {...})`. Without it parked runs EXPIRE.
- **A6 [HIGH, security] (M)** — JIT GitHub App installation-token minting (single-repo, ≤1h, contents+PR) inside each repo activity so it never enters Temporal history. **Interim:** pass the per-run fine-grained PAT as `installationToken` (accept token-in-history on the canary).
- **A4 [HIGH, security] (M)** — For `profile==='pdlc'`, mount `prepareRepo`'s token-less `file://` workdir as `project/` instead of a credentialed clone (`workspace-assembler.ts:208-215`). KEEP the COMMIT-ONLY instruction. Deferrable to fast-follow if canary PAT risk is accepted.
- → **Milestone 2: bug → fix → PR → human approve → merge proven** (A3+A5+T3, on the canary).

### tacticl-core side (mostly done; all ADDITIVE)
- **T1 (S)** — Set `skipIntake`/already-classified on the submit (pairs with A1).
- **T2 (S)** — Guarantee `idempotency_key` (= pipelineRunId) is charset-safe `^[A-Za-z0-9_-]{1,200}$` (Temporal path hard-throws otherwise; `shell.ts:380-387`).
- **T3 (M)** — Discord "PR ready → Approve" button → call `SignalPipelineDecision`; parse PR url/headSha/askId/gateNonce from the legacy `blocked` callback message in `PdlcV2Service.translateArbiterEvent` (`PdlcV2Service.java:220`).
- **T4 (S, conditional)** — Enrich `request_context_json` for pdlc-fix only (add `intakeId`, `product`, `raw.userText`, `trivial`); additive, legacy codegen untouched.
- **T5 (S/M)** — Proto sync: add `SignalPipelineDecision` to `client-ai-arbiter/.../arbiter_pipeline.proto`.

## 3. Contract verdict
**All tacticl-core changes are additive, non-breaking.** Crucially the Temporal path emits the **legacy** `ArbiterCallbackPayload` with `X-Arbiter-Secret` (`emit-callback.ts:41`), which tacticl's existing `PipelineCallbackController` (`/v1/internal/pipeline/callback`) already consumes — so NO new callback endpoint / HMAC needed for this slice. `PdlcCallbackPayload` (Contract 2) is defined but never emitted (dead).

## 4. `pdlc-fix.json` shape (lean, recommended)
```json
{ "pipeline": "pdlc-fix", "product": "tacticl",
  "agents": [
    { "type": "investigator", "parallel": false, "resource_class": "light" },
    { "type": "fixer",        "parallel": false, "resource_class": "medium" },
    { "type": "test",         "parallel": false, "resource_class": "light" } ] }
```
Omit PRD/Architect/Plan/DevOps entirely (no docs rigor; no `skip_when` needed). Model defaults go in `tacticl/agents/<type>.json`. Merge gate + `reworkMax:1` come from `playbookConfigJson` (decide its origin — §6.7); set the gate role = last role for one human PR gate.

## 5. Top risks + de-risk
- Autonomous commit to a real repo → **canary repo only**, `investigator→fixer→test` (tests gate), low `max_turns`, `reworkMax:1`.
- Auto-merge to protected branch → open **DRAFT PR**, require human `APPROVE_MERGE` (A5), branch protection + required checks on canary, `--match-head-commit approvedSha` (already in `host-git-activities.ts:284`).
- Token scope / token-in-history → A6 App token; interim = fine-grained single-canary-repo PAT; never log.
- gitleaks → run before push; block on `clean===false`.
- Silent hang (A1 wrong) → verify in Temporal UI that `resolvePipelineBundle` (not `extractFromIntake`) is the first scheduled activity.

## 6. Open decisions (human)
1. **Repo owner / App-vs-PAT:** `cuztomizer` vs `tacticl-ai` org (standing open decision). Gates A6.
2. **Canary repo:** a dedicated throwaway repo (recommended) for the first autonomous merge.
3. **App token now vs PAT interim** (gates whether A4+A6 are in this slice or fast-follow).
4. **Images/MinIO/vision — defer** (recommended; lean text path skips Phase A via A1).
5. **`reworkMax` 0 vs 1.**
6. **Draft PR + always-human-approve** (recommended for canary).
7. **Where `playbookConfigJson` originates** for pdlc-fix (registry-static vs tacticl-generated) — else degenerate bundle = no gate.

**Critical path:** A7 → A1 → A2 → A8 → *(bug→fix→commit)* → A3 + A5 + T3 → *(bug→fix→PR→approve→merge)* → A6/A4 hardening before leaving the canary.
