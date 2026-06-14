# Discord→PDLC-fix — Production Readiness Remediation Plan

Source: 314-agent adversarial review (2026-06-14), 139 findings, **123 confirmed (26 blockers, 56 high)**.
Full output: workflow `wxjc9qhls`. This doc triages them into go-live tiers for the Discord→`pdlc-fix` flow.

**Verdict:** the flow is wired and 80% deployed (Temporal live, arbiter wired, `pdlc-fix` registry seeded), but
**not safe to flip on** until Tier 1 + Tier 2 land. Tier 3 hardens before untrusted/team alerts.

Legend: `[A]`=cidadel-ai-arbiter, `[T]`=tacticl-core. ✅=fixed, ⬜=todo.

---

## TIER 1 — FLOW-BLOCKING or UNSAFE (must fix before ANY go-live)

⬜ **1. pushBranch can't authenticate** `[A]` — git ignores `GH_TOKEN` for https; token must be embedded in the URL
   (`x-access-token:<tok>@github.com`). *This is why the 2026-06-13 smoke failed at `git push`.* Without it the PR
   never lands → no e2e. `host-git-wiring.ts:116-128` (+ openPR/merge). Mirror `GitHubClient.authedUrl`.

⬜ **2. GRANT_REWORK auto-completes on the terminal gate** `[A]` — `pdlc-fix` DAG ends at `test` (the merge gate), so
   `isGateHaltingDownstream` returns false after rework → run marks COMPLETED, **reworked code skips human re-approval,
   PR left open.** `pdlc-run-workflow.ts:826` → re-park guard must mirror line 596 (`if(!completed.has(gateRole))…`).
   Guard with `patched()`. Add a regression test with the gate as the LAST step.

⬜ **3. Playbook MUST be literally `pdlc-fix`** `[T]` — `BUG_FIX`/`FULL_PDLC` skip the merge gate (no `mergeGateRole`).
   Pin entry-point `playbook=pdlc-fix` + add allow-list validation in `EntryPointSeeder.isValid()`; change the
   `EntryPointDef.playbook` default off `FULL_PDLC`. `IngressRegistryProperties.java:67`, `PlaybookSpecResolver.java:40`.
   *(Mostly handled by correct go-live config; add the validation.)*

⬜ **4. Agent can exfiltrate the shared Claude Max OAuth token** `[A]` — refresh+access token bind-mounted at
   `/home/agent/.claude` into a container processing **untrusted Discord alerts**; prompt-injection → account takeover
   (refresh token = long-lived). `container-manager.ts:226`. Fix: inject a **short-lived access token only, no refresh**
   for the pdlc profile (stolen token dies ≤55min). *Acceptable risk for a controlled own-repo first smoke; MUST fix
   before real/team alerts.*

⬜ **5. Callback endpoint fails OPEN on blank secret** `[T]` — `PipelineCallbackController.java:41`. Secret IS set today
   (not exploitable now) but must fail-closed + fail-fast at boot. Quick hardening.

---

## TIER 2 — RELIABILITY / OBSERVABILITY (fix before go-live; mostly cheap)

⬜ **6. Workflow terminal callback has no try/catch** `[A]` `pdlc-run-workflow.ts:249-272` — a thrown failure (dead
   container, TTL, intake 401) never notifies Discord; the run vanishes. Wrap → emit `FAILED` callback, then rethrow.
⬜ **7. Shared `pdlc-run-{id}` tree never cleaned up** `[A]` `workspace-assembler.ts:236` — unbounded disk leak on the
   shared host (which has had a disk-full incident). Wire the **dead** `PdlcReaper` (`server.ts:674`) +/or a terminal
   cleanup activity (terminality-aware — a parked run sits for days).
⬜ **8. No `scheduleToStartTimeout` on any activity proxy** `[A]` `pdlc-run-workflow.ts:128` — a dead worker wedges the
   run forever with no terminal callback. Add bounded timeouts to all proxies + a `workflowExecutionTimeout` (~14d).
⬜ **9. Callback can FAIL a COMPLETED run** `[A]` `emit-callback.ts:37` — no fetch timeout; a hung tacticl exhausts
   Temporal retries uncaught. Add `AbortSignal.timeout(15s)` + swallow at the `emitEvent` seam.
⬜ **10. Merge after APPROVE throws uncaught → approval lost, run dies** `[A]` `pdlc-run-workflow.ts:721` — wrap
   `mergePullRequest` in try/catch; on failure re-park on a fresh ask instead of terminating.
⬜ **11. No ROLE_FAILED event** `[A/T]` — mid-pipeline agent failures are silent. Emit `agent_failed` →
   `ROLE_FAILED` → Discord card.
⬜ **12. Per-role TTL governor dead** `[A]` — every role gets the fixed 2h ceiling; with a single serialized slot that's
   a 2h head-of-line stall. Read `ttlSeconds` from the registry agent JSONs in `resolve-bundle.ts`.
⬜ **13. gRPC submit failure orphans a PENDING run** `[T]` `PdlcV2Service.java:63-94` — wrap, `markFailed`, surface.

### My recent backoff code (introduced by the rate-limit work — fix properly):
⬜ **14. Backoff WRAPS the failover loop** `[A]` `anthropic.ts:93` — up to 15 live calls/generate against an
   already-limited account. Invert: backoff INSIDE each per-account attempt.
⬜ **15. `RateLimitWindowError` never caught** `[A]` `backoff.ts:166` — dead-end. Either delete it or wire a real parker
   (Temporal `ApplicationFailure` w/ `nextRetryDelay`) in the intake activities.
⬜ **16. 401 classified FATAL, conversation has no refresh** `[A]` `backoff.ts:81` + `conversation-llm.ts` — on 401,
   refresh once + retry while `produced===false`. *(Conversation path; not used by pdlc-fix.)*
⬜ **17. OAuth refresh interval 3h ≫ ~55min token life** `[A]` `oauth-token-manager.ts` — capture `expires_in`, refresh
   at `expiresAt-5min`. Root cause of the 403/429 churn on the brain path.

---

## TIER 3 — HARDENING (before untrusted / team-wide alerts; fast-follow)

⬜ **18. Secret/PII redaction** `[T]` — Discord free text → durable Temporal history + agent workspace files, unredacted
   (`ArbiterGrpcClientImpl.java:149`, `DiscordInboundAdapter.java:192`, `workspace-assembler.ts:634`). Add a
   `SecretRedactor` at the ingress source. **Critical once real alerts (which may paste secrets) flow.**
⬜ **19. Broad org PAT in durable history** `[A/T]` `pdlc-types.ts:69`, `IngressDispatchService.java:130` — provision the
   GitHub **App** (`secret/{ctx}/github-app`) for JIT single-repo tokens; stop threading the classic PAT.
⬜ **20. Merge approval not SHA-bound** `[T]` `PdlcV2Service.java:148` — `approvedSha` hardcoded empty → TOCTOU defense
   dead. Persist the blocked-card `headSha` onto the checkpoint + echo on approve.
⬜ **21. Discord Ed25519 no timestamp-freshness** `[T]` `DiscordEd25519Verifier.java` — replayable after the 600s dedup
   TTL. Reject `|now-ts|>300s`.
⬜ **22. gateNonce empty-string bypass** `[A]` `pdlc-run-workflow.ts:396` — make the nonce mandatory at the gate.
⬜ **23. Repo-target allowlist** `[A/T]` — arbiter doesn't validate `repo_url`; caller `repoUrl` can override the
   entry-point lock; v1 REST accepts arbitrary repo+token. `submit-temporal-run.ts:125`, `IngressDispatchService.java:125`,
   `PipelineController.java:70`. Allowlist owners; ignore caller repoUrl for Discord.
⬜ **24. Cost/quota guard** `[A/T]` — `cost-ceiling-usd` is dead config; OAuth makes $-meter moot. Enforce turn/time +
   per-run + daily run-admission budgets.
⬜ **25. Concurrency caps** `[A]` — sub-agent spawn bypasses the admission semaphore; no global cap on concurrent runs
   sharing the OAuth pool with the live brain. `sub-agent-tracker.ts:80`, `submit-temporal-run.ts:120`.
⬜ **26. gitleaks config unpinned + soft-skip** `[A]` `host-git-activities.ts:152,166` — a repo-supplied `.gitleaks.toml`
   neuters the scan; `PDLC_ALLOW_MISSING_GITLEAKS` can push unscanned. Pin `--config` baked in the image; remove soft-skip.
⬜ **27. Idempotency key is random, not derived** `[T]` `ArbiterGrpcClientImpl.java:60` — `REJECT_DUPLICATE` can't dedup
   a double "Send to PDLC". Derive from the Discord interaction/message id.
⬜ **28. Credentialed-clone origin strip is best-effort** `[A]` `workspace-assembler.ts:277` — on failure the PAT stays
   in the agent's `project/.git/config`. Make fail-closed.

---

## Already done (this session)
- ✅ `pdlc-fix` registry seeded into prod `strategiz.cidadel_registry` (10 docs, DAG verified, backup saved).
- ✅ Confirmed 403-scope / 429 errors are on the conversation/brain path, NOT the pdlc-fix agent CLI path.

## Plan
1. Land **Tier 1 + Tier 2** code fixes in `cidadel-ai-arbiter` (+ a few `tacticl-core`), grouped per file; `patched()`
   any workflow-branching change; build + test.
2. **One** arbiter rebuild+redeploy carrying these + the backoff (`bbf8653`).
3. Discord config (user provides app + decisions) → seed `secret/tacticl/discord`, entry-point `playbook=pdlc-fix`,
   `enabled=true`, redeploy tacticl-api.
4. Controlled smoke on the user's own repo (operator-authored alert → keeps Tier-3 injection/redaction risk low).
5. Fast-follow **Tier 3** before opening to real/team alerts.
