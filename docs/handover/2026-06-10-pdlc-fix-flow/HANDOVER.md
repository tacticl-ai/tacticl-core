# HANDOVER — Discord/voice alert → autonomous PDLC fix → PR → human-approve → merge

**Date:** 2026-06-10 · **Repos:** `cidadel-ai-arbiter` (the brain/engine, TS), `tacticl-core` (product backend, Java)
**Read this first if resuming.** Companion: `cidadel-ai-arbiter/docs/plans/2026-06-10-pdlc-shared-workspace-replumb.md` (the one remaining code piece).

---

## 1. THE GOAL (what we're building)

Make alert items (Discord "Send to PDLC" admin action, or a spoken "fix …") flow into the PDLC
pipeline as **autonomous fixes that open a PR**, with a human in the loop at two checkpoints:

```
Discord alert ──► tacticl-core ingress ──► arbiter gRPC ──► Temporal PDLC run
                                                               │
   investigator → fix-planner → [DESIGN gate 🧑] → implementer → test → [PR gate 🧑] → merge
                                  approve/reject/                      approve/reject/
                                  rework the design                   rework the code
```

- **Two human gates.** The DESIGN gate reviews `design.md` (no PR yet). The PR gate reviews a real
  GitHub PR and merges on approval. Trivial fixes auto-skip the design gate ("drop → PR" fast path).
- **Approval is via Discord buttons** (a `SubmitPipelineDecision` signal), NOT GitHub webhooks —
  because non-PR HITL steps exist (the design gate). One evolving PR per run.
- **One run = one shared branch** `pdlc/fix/{intakeId}`; agents accumulate commits; the host pushes.

---

## 2. ARCHITECTURE (the moving parts)

| Piece | Where | Role |
|---|---|---|
| Discord ingress + EntryPoint registry | tacticl-core `business-pipeline/ingress/` | alert → `IngressRequest` → arbiter gRPC. Built, committed. |
| Arbiter gRPC `SubmitPipeline` + `SubmitPipelineDecision` | cidadel-ai-arbiter `shell/grpc-pipeline-service.ts` | start a Temporal run / signal a gate decision |
| `pdlcRunWorkflow` (Temporal) | `temporal/pdlc-run-workflow.ts` | the durable run: DAG, the TWO gates, rework, the PR edge |
| Pure DAG layer | `temporal/dag.ts` | `computeReadySet` / gate-halt — replay-safe pure functions |
| `runAgentRole` activity | `temporal/activities/run-agent-role.ts` | owns one ephemeral agent container's lifecycle |
| Workspace assembler | `shell/workspace-assembler.ts` | clones the repo + writes the agent's `.agent/` tree |
| host-git edge | `temporal/activities/host-git-wiring.ts` + `shell/host-git-activities.ts` | push branch / open PR / merge (gitleaks-scanned) |
| GitHub App auth | `shell/github-app-auth.ts` | JIT ≤1h single-repo installation tokens (PAT fallback) |
| Registry seed | `cidadel-ai-arbiter/registry-seed/tacticl/` | the `pdlc-fix` pipeline def + agent defs + the two-gate `playbookConfig` |

**Pipeline name** is config-driven: tacticl submits `EntryPoint.defaultPlaybook` as the wire
`pipeline_name`; the arbiter firewall accepts only `pdlc-fix` | `pdlc-feature`. All layers agree.
Deploy env must set `PDLC_TEMPORAL_ENABLED=true` and `PDLC_TEMPORAL_PIPELINES=pdlc-fix`.

**Determinism rule (Temporal):** the workflow body must replay identically. New behavior is gated
behind `patched('<marker>')` so old parked runs replay unchanged. Two markers in play:
`pdlc-plan-gate-v1` (the design gate), `pdlc-repo-gate-v1` (the host-git push/PR/merge).

---

## 3. WHAT'S DONE (committed)

All in `cidadel-ai-arbiter` `main` unless noted. tacticl-core ingress side was done earlier.

| Commit | What |
|---|---|
| `9adfe86` | host-git wiring factory (App-token mint, idempotent push/PR/merge), 9 tests |
| `f7e028f` | host-git activity type contract + `AgentRoleResult.headSha/repoWorkDir` SEAM |
| `916dcc8` | `PDLC_TQ_REPO` worker + server assembles host-git activities (App token, PAT fallback) |
| `8b10b35` | registry seed: two-gate `pdlc-fix` DAG + lean `fix-planner` role |
| `7280683` | **Stage 3a** — the merge gate drives a real PR (gitleaks→push→find-or-create-PR→merge), patched-guarded |
| `c5c2f87` | **Stage 3b pt1** — plan-gate bundle plumbing + symmetric DAG hold |
| `45c15ed` | **Stage 3b pt2** — the design-gate park in the workflow body (patched `pdlc-plan-gate-v1`) |
| `177ad1b` | **adversarial-review hardening** — 6 fixes (see §5), 127/127 tests |
| `9cc65bf` | the re-plumb plan doc (the one piece left) |

**Resolved (no code needed):** pipeline name (config-driven, consistent); **GitHub owner = `tacticl-ai`
org** (cuztomizer is Owner); `createOrg`-as-skill dropped (no org API + CAPTCHA + Playwright not a
server dep + the org already exists).

**Test posture:** 127/127 pdlc + host-git tests green, including determinism replay gates for BOTH a
v1 single-gate history and the new two-gate path. Run: `cd packages/server && npm run build &&
npx vitest run pdlc-run-workflow pdlc-dag resolve-bundle pdlc-workflow-replay host-git`.

---

## 4. WHAT'S LEFT (to fire end-to-end)

### 4a. The shared-workspace re-plumb — THE LAST CODE PIECE  ⬅ start here
Plain version: today each agent re-clones fresh + the clone is deleted, so commits don't accumulate
and the PR gate has nothing complete to push. Make all agents in a run share ONE working folder
(`pdlc-run-{intakeId}/project`) so commits stack; record the commit SHA + path (the seam) so the
push step works. **Full plan:** `cidadel-ai-arbiter/docs/plans/2026-06-10-pdlc-shared-workspace-replumb.md`.
- Flag-gated `PDLC_SHARED_WORKSPACE` (default OFF) + pdlc-profile only → codegen byte-identical.
- Touches `workspace-assembler.ts`, `container-manager.ts`, `run-agent-role.ts`, `reaper.ts`.
- **Key finding:** today's clone ALREADY embeds the token in `project/.git/config` (the "token-less
  file://" is aspirational text), so a shared clone is token-neutral; strip the remote for token-positive.
- **OPEN DECISIONS (need a go):** OK to modify `container-manager.ts` (actively-edited, shared with
  codegen)? origin-strip for a token-less working copy? flag name? Verify the agent queue is
  single-slot (then no lock needed). Real-Docker bind behavior must be validated on the canary.

### 4b. Operator setup (human)
- **GitHub App `tacticl-pdlc-bot`** under `tacticl-ai` (form pre-filled 2026-06-11 via Chrome; passkey
  is a sudo gate the human taps). FINAL scopes decided (8 repo + 1 mandatory): **Administration R/W**
  (repo create/delete/settings — chosen so the App fully replaces the PAT incl. the voice/build
  create_repo path), **Contents R/W**, **Pull requests R/W**, **Workflows R/W**, **Issues R/W**,
  **Checks R**, **Commit statuses R**, **Actions R**, **Metadata R** (mandatory). Webhook off. Install
  scope "Only on this account". Steps: click Create → Generate a private key (.pem) → note App ID →
  Vault `secret/tacticl/github-app` (`app-id` + `private-key`) → **Install on "All repositories"**
  (REQUIRED for repo creation — a selected-repos install can't create a repo that doesn't exist yet).
  `makeGithubAppAuthFromEnv` reads that Vault path (commit `9adfe86`). The `github.app-token` PAT stays
  as the fail-soft fallback but is no longer required once the App is installed.
- **Vault Discord secrets** + `tacticl.discord.enabled=true` + EntryPoint rows
  (`tacticl.ingress.entry-points[N]`: DISCORD row `playbook=pdlc-fix`, real repo-url, real admin-user-ids).
- **Deploy env:** `PDLC_TEMPORAL_ENABLED=true`, `PDLC_TEMPORAL_PIPELINES=pdlc-fix`,
  `PDLC_SHARED_WORKSPACE=true` (once 4a lands + validated), optionally `PDLC_ALLOW_MISSING_GITLEAKS`
  (NOT needed — the new Dockerfile installs gitleaks).
- **Pick the canary repo** under `tacticl-ai`.
- ⚠️ **Vault data-host outage** may still be in play (see `reference_vault_data_host_outage`).

### 4c. Open product decisions
- Which Discord guild + alert channels; which admins (the human who clicks "Send to PDLC").
- Voice end-to-end depends on the arbiter `ConverseTurn` service (see `project_voice_e2e_and_brain_migration`).
- Later/non-blocking: thread-per-incident, MinIO AttachmentMaterializer, fix-vs-feature classifier.

---

## 5. GOTCHAS / HARD-WON LESSONS

- **`patched()` is the determinism lever.** Place it as the FIRST operand of the gate's `if`; never
  remove a marker. v1 histories must replay byte-identical (planGateRole undefined ⇒ all new checks
  no-op; `askSeq` untouched ⇒ first merge ask stays `ask:1`). There's a replay test per path — keep them.
- **Run `npm run build` before the replay vitest** — it replays compiled `dist`, not source.
- **arbiter `src/__tests__` is excluded from `tsc`** — type errors in test files won't fail `npm run lint`;
  only vitest catches them.
- **Adversarial-review catches (all fixed in `177ad1b`, do not regress):**
  - host-git push must use an EXPLICIT `--force-with-lease=<branch>:<remoteSha>` (from `ls-remote`),
    NOT a bare `--force-with-lease` — pushing by URL has no tracking ref → "stale info" wedge on rework.
  - gitleaks is FAIL-CLOSED (`if (!scan.clean)`); a missing scanner refuses the push unless
    `PDLC_ALLOW_MISSING_GITLEAKS=true` (host-side). The worker image now installs gitleaks AND gh.
  - rework budget is PER-GATE (planReworkCount/mergeReworkCount), bounded by the run-wide hard ceiling.
- **The seam (`headSha`/`repoWorkDir`) is undefined until 4a lands** → the host-git edge is gracefully
  skipped and the gate just parks. So 4a is the gate to the whole flow firing.
- **`container-manager.ts` is the user's actively-edited, codegen-shared file** — coordinate before touching.

---

## 6. HOW TO RESUME

1. Read this file + the re-plumb plan (`…/2026-06-10-pdlc-shared-workspace-replumb.md`).
2. Confirm the §4a open decisions, then implement the re-plumb (flag OFF default) with unit tests.
3. Operator does §4b; enable the flags on the canary; validate one real Discord-alert→fix→PR→merge.
4. Memory index entry: `project_discord_alert_to_fix` (the canonical running log).
