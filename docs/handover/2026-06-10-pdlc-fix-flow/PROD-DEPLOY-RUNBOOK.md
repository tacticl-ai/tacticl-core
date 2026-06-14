# PROD GO-LIVE RUNBOOK — Discord alert → PDLC fix → PR → approve → merge

**Validated locally 2026-06-13 (fully green):** a Discord-equivalent `SubmitPipeline` drove a real
Temporal `pdlcRunWorkflow` through the design gate → implementer (real fix committed to the shared
tree) → test → merge gate → **PR opened + squash-merged to `main`**. Every code piece (Stage 3a
host-git, Stage 3b two-gate, the shared-workspace re-plumb) is on `origin/main`
(cidadel-ai-arbiter `a912522`). What remains is **deployment + config only.** Execute on the prod
host (platform-apps, 178.156.253.208) unless noted.

The exact local sequence that proved it (mirror this on prod): seed shared OAuth → run arbiter with
the env in step 4 → `SubmitPipeline(product=tacticl, pipeline=pdlc-fix, playbook_config_json with the
two gate roles, github_token)` → approve `approveMerge` at each gate.

---

## 0. Prereqs (verify first)
- [ ] Code deployed is from `origin/main` ≥ `a912522` (incl. re-plumb `ae18660` + hardening `177ad1b`).
      `deploy.sh` streams the LOCAL tree (no host git-pull) → **deploy from a clean `git pull` of main.**
- [ ] `cidadel-agent:latest` present on the prod Docker host; `/var/run/docker.sock` reachable by the
      arbiter container (group_add = host docker gid); `platform-net` network exists.
- [ ] Vault reachable + unsealed (the arbiter exits without VAULT_ADDR/TOKEN; the brain is dead if the
      OAuth secrets can't be read — see `reference_vault_data_host_outage`).

## 1. Stand up a Temporal cluster  ← the single biggest gap (none deployed today)
- [ ] Bring up `temporal-postgres` (set `TEMPORAL_POSTGRES_PASSWORD`, confirm `pg_isready`), then
      `temporal` + `temporal-ui` from `cidadel-ai-arbiter/docker-compose.temporal.yml`
      (currently shipped-but-dormant; never merged into `/opt/cidadel/docker-compose.yml`).
- [ ] Verify the frontend is reachable at the address you'll use for `TEMPORAL_ADDRESS` (design: `10.0.1.1:7233`).
      `temporal operator cluster health` → SERVING.

## 2. Vault secrets — shared, OAuth-only (two accounts)
- [ ] `ANTHROPIC_VAULT_CONTEXT=shared` (repoints ONLY the Anthropic paths to the shared context).
- [ ] `secret/shared/anthropic` → `oauth-access-token` + `oauth-refresh-token` (account #1 — brain).
- [ ] `secret/shared/anthropic-codegen` → account #2's OAuth (agents — keeps codegen load off the brain).
      (Fallback `secret/shared/anthropic-2` optional.) **OAuth-only — never an API key** (cost-incident).
- [ ] `secret/{vaultContext}/github-app` → `app-id` + `private-key` (PEM) for host-git push/PR/merge;
      install the GitHub App on the target repo/org (contents:write + pull_requests:write). Per-run PAT
      is the fallback if the App is absent.
- [ ] `PDLC_CALLBACK_SECRET` in `/opt/cidadel/.env` (feeds arbiter `CALLBACK_SECRET` + tacticl
      `pdlc.v2.callback.secret` — must match).

## 3. Seed the registry into PROD Mongo (correct DB)
- [ ] From `cidadel-ai-arbiter/packages/server`:
      `REGISTRY_MONGO_URI=<prod strategiz uri> REGISTRY_MONGO_DATABASE=strategiz SEED_CONFIRM=1 \
       npx tsx ../../scripts/seed-tacticl-pdlc-fix.ts`
      **DB MUST be `strategiz`** (MongoRegistryClient defaults to strategiz, NOT tacticl). Restart the
      arbiter (or wait for the 5-min registry refresh) so the cache reloads.

## 4. Arbiter env (add to the prod arbiter service / `/opt/cidadel/.env`)
- [ ] `PDLC_TEMPORAL_ENABLED=true`
- [ ] `PDLC_TEMPORAL_PIPELINES=pdlc-fix`   (comma-list allow-list; add `pdlc-feature` later)
- [ ] `TEMPORAL_ADDRESS=<the step-1 frontend>`
- [ ] `PDLC_SHARED_WORKSPACE=true`   (arms commit-accumulation + the gate push)
- [ ] `ARBITER_OAUTH_REFRESH_OWNER=true` on exactly ONE instance
- [ ] Do **NOT** set `PDLC_ALLOW_MISSING_GITLEAKS` (the worker image installs gitleaks; leave fail-closed)

## 5. Redeploy the arbiter + verify boot
- [ ] `scripts/deploy.sh prod` from a clean `origin/main` checkout.
- [ ] Boot log MUST show:
      `Provider registered: anthropic (OAuth … fallback secret/shared/anthropic-2)` ·
      `[agent-oauth] agent containers use the DEDICATED codegen OAuth account (secret/shared/anthropic-codegen)` ·
      `[pdlc] Temporal worker started (3 orch/agent + 4 repo activities)` ·
      `gRPC server listening on port 50051`.

## 6. tacticl-core — turn the Discord ingress on (config only; code is 100% wired)
- [ ] Create the Discord app + bot; Interactions Endpoint URL = `https://api.tacticl.ai/v1/discord/interactions`;
      invite to the admin guild with `applications.commands`.
- [ ] Vault (tacticl ctx): `discord-public-key`, `discord-bot-token`, `discord-application-id`
      (missing public-key → every interaction 500s).
- [ ] `tacticl.discord.enabled=true` + `tacticl.discord.command-guild-id=<guildId>`.
- [ ] ONE `tacticl.ingress.entry-points[0]`: `channel=DISCORD`, `external-key=<guildId>:<channelId>`,
      `product-id=tacticl`, `repo-url=<git>`, **`playbook=pdlc-fix`** (NOT the FULL_PDLC default),
      `admin-user-ids[0]=<linked tacticlUserId>`, `cost-ceiling-usd=…`.
- [ ] `pdlc.v2.arbiter.host` reachable (intra-compose `cidadel-arbiter-prod:50051`, else the no-op stub).
- [ ] Admin runs `/link` in Discord + redeems in the web app; that userId must be in `admin-user-ids`.

## 7. Smoke test
- [ ] "Send to PDLC" on a text alert in the configured channel → watch the Temporal UI: investigator →
      fix-planner → DESIGN gate (Discord card) → approve → implementer → test → PR gate (PR link in the
      card) → approve → merged. First test: TEXT-ONLY (image attachments wedge on unregistered intake
      activities until MinIO materialization lands).

## Known follow-ups (non-blocking)
- Run-end cleanup of `pdlc-run-*` dirs must be TERMINALITY-AWARE (a parked run sits for days; naive
  mtime age-out would delete a live tree). Token-less, so leaking is disk-only/safe meanwhile.
- A merge-gate push failure currently fails the run terminally (clean, not a wedge) — consider re-park.
- `IngressRegistryProperties` playbook default is `FULL_PDLC` (footgun) — set the row explicitly.
