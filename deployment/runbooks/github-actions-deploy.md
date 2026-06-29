# Deploy tacticl-api via GitHub Actions (self-hosted runner)

Replaces the local `scripts/deploy.sh` SSH flow, which is currently dead: `platform-apps`
(`10.0.1.1`) is reachable only via `ProxyJump platform-infra` (`178.156.141.55:443`), and that
jump host is firewalled / null-routed from arbitrary IPs. GitHub-hosted runners hit the same
wall, so we **do not** SSH inbound. Instead a **self-hosted runner on the host** connects
**outbound** to GitHub and runs the deploy locally.

## Topology

```
GitHub-hosted runner            self-hosted runner (ON platform-apps)
─────────────────────           ─────────────────────────────────────
gradle bootJar                  docker login ghcr.io (GITHUB_TOKEN)
docker build  ──push──▶ GHCR ──▶ docker pull ghcr.io/tacticl-ai/tacticl-core:<sha>
                                docker tag … tacticl-api:latest
                                docker compose up -d tacticl-api-prod|qa
```

Workflow: `.github/workflows/deploy-tacticl-api.yml`

## One-time setup

### 1. Repo secret — `GH_PACKAGES_TOKEN`
Gradle reads the **private** `cidadel-platform/cidadel-core` maven packages. The build needs a
classic PAT (`ghp_…`) with `read:packages` whose owner is a member of `cidadel-platform`
(same credential your local `~/.gradle/gradle.properties` `gpr.key` uses).

```bash
gh secret set GH_PACKAGES_TOKEN --repo tacticl-ai/tacticl-core --body '<ghp_…>'
```

GHCR push/pull themselves use the built-in `GITHUB_TOKEN` — no extra secret.

### 2. Self-hosted runner on platform-apps (label `tacticl-deploy`)
Mint a registration token (repo admin + `gh`), then run the setup script on the host:

```bash
# from your laptop (has gh + admin):
gh api -X POST /repos/tacticl-ai/tacticl-core/actions/runners/registration-token -q .token

# on platform-apps (root), with that token:
REG_TOKEN=<token> ./scripts/setup-github-runner.sh
```

The runner installs as a systemd service (`actions.runner.*`), runs as root (single-tenant host),
and must have docker access + `/opt/cidadel/docker-compose.yml` present (it already is).

Verify online at: `https://github.com/tacticl-ai/tacticl-core/settings/actions/runners`

## Day-to-day

- **Push to `main`** → builds + pushes `ghcr.io/tacticl-ai/tacticl-core:{latest,<sha>}`. **No deploy.**
- **Deploy** → Actions tab → `deploy-tacticl-api` → *Run workflow* → pick `prod` / `qa` / `both`.
  The build runs again (fresh image for that exact SHA), then the self-hosted runner pulls + restarts.

CLI equivalent:
```bash
gh workflow run deploy-tacticl-api.yml -f environment=prod
gh run watch
```

## Notes / gotchas

- The host compose pins `tacticl-api:latest`; the deploy step retags the pulled GHCR image to
  that tag, so **no compose edit** is needed.
- `tacticl-api-prod` env (VAULT_TOKEN, PDLC_CALLBACK_SECRET, …) comes from `/opt/cidadel/.env`,
  read automatically by `docker compose` — same as the old `deploy.sh`.
- This does **not** fix public reachability if `platform-infra` (Caddy gateway) is ever blocked
  again — that's a Hetzner-console action. This only fixes *shipping a new build* to the host.
- Bump `RUNNER_VERSION` in `scripts/setup-github-runner.sh` when GitHub deprecates older runners.
