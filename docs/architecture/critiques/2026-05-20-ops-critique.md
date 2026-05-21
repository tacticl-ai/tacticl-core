# Distributed Systems / Production Ops Critique

**Date:** 2026-05-20
**Persona:** Senior distributed systems / production-ops architect (3 AM on-call lens)
**Topic:** What breaks in production for Tacticl at single-founder operational scale

## Nightmare #1: The Docker socket is mounted into a process that talks to an LLM

This is the one that would make me physically refuse to run this in prod as-is.

`ContainerManager` uses `dockerode`, which means `/var/run/docker.sock` is bind-mounted into the arbiter container. The Docker socket is root on the host. There is no privilege boundary between "arbiter can spawn agent containers" and "arbiter can spawn `--privileged --pid=host -v /:/host` and own the box."

Your threat model is not "malicious attacker." It is **prompt injection via Telegram**. A user pastes a "research this URL" into chat. The page contains adversarial text. The Researcher persona summarizes it. That summary becomes context for the orchestrator. The orchestrator, doing its job, decides the next step is to spawn a container — and the spec for that container is partially derived from attacker-controlled content. You don't have to be careless for this to bite you; you just have to be wrong about a single sanitization boundary once.

Mitigations, cheap to expensive:
- **Cheap**: A hardcoded allowlist of image names + flag sets in `ContainerManager`. The orchestrator's "spawn container" tool takes a *role enum*, not a free-form spec. The actual `docker run` arguments are built server-side from a static table. Orchestrator cannot influence privileges, mounts, network, or env.
- **Medium**: Run arbiter as non-root, talk to a `docker-socket-proxy` (Tecnativa) that whitelists only `POST /containers/create` with a constrained schema.
- **Expensive**: Move agent containers off this host entirely (rootless Podman in a separate VM, or a Cloud Run job runner).

Do the cheap one this week. The orchestrator should not be able to express "spawn a privileged container" even in principle.

## Nightmare #2: There is one host and one Mongo and one Vault, and you cannot reason about RPO

Single Hetzner host means: disk fails → Tacticl + Strategiz + Pointstax + arbiter + Mongo + Vault are all simultaneously dark. Your RTO is "however long it takes me to restore Mongo dumps to a new VM, re-unseal Vault by hand at 3 AM, and re-bootstrap Caddy." If you don't have Vault unseal keys printed on paper in a fireproof box and Mongo backups going off-host on a cron you have actually verified, your RPO is "everything since the last time I remembered."

Three things compound this:

1. **No Mongo replication.** Conversation sessions are the live working memory of every user's interaction. A corrupted Mongo means resumed conversations come back wrong, which is a worse failure than down.
2. **`ConversationSession.messages` is an unbounded array in a single document.** At ~200 turns you're rewriting a 1MB doc on every reply. At ~3,000 turns you hit the 16MB limit and the conversation can no longer be written to. The fix is trivial — separate `conversation_messages` collection keyed by sessionId — but you have to do it *before* the long-tail user does it to you. I've watched this exact pattern kill a startup's chat product on day 90.
3. **Vault unseal at 3 AM.** If unseal is manual and you're asleep, every container that needs a secret on cold-start hangs. Auto-unseal via a cloud KMS (even GCP KMS, since you already have a `tacticl` GCP project) is a one-afternoon project and is the difference between "downtime" and "page."

## Nightmare #3: Cost runaway is theoretical, not enforced, and the enforcement point is wrong

You bumped the pipeline ceiling to $10,000 "uncapped during pre-production rollout." That phrase is exactly how the postmortems start. Three problems:

- **`pipelineCostCeiling` is enforced where?** If it's enforced by tacticl checking accumulated cost *between* role steps, then a single role doing a long Sonnet run inside Claude Code with `--max-turns=N` can burn $200 before the next check fires. The CLI's `--max-cost` is per-invocation, not per-pipeline, and the CLI accounting does not know about your Anthropic Max quota.
- **Conversation has no ceiling at all that I can see.** A user on Telegram who keeps mashing reply on Option A (persistent OrchestratorSession) is in an unbounded loop with no per-turn or per-day cap. Sunday morning, confused user, 400 turns, $80. Not catastrophic alone — catastrophic across 50 users.
- **Subscription-quota cliff.** When you hit 80% of Anthropic Max utilization across all parallel sessions, the API doesn't degrade gracefully; you start getting rate-limit errors mid-tool-call. Long-lived OrchestratorSessions don't have a clean "pause and resume from here" — they have a half-finished tool_use block. Multiple sessions hitting this at the same instant create a thundering retry herd that worsens the rate limit. You need a circuit breaker at the *arbiter* level (one place that sees all sessions) that cuts new turns once you cross 70%, not the CLI level.

## The other things, briefly

- **Option A (one persistent subprocess per conversation) is the wrong default.** A Claude Code SDK subprocess is ~150–300MB resident, more once it loads context. 50 idle conversations = 10–15GB of arbiter-host RAM doing nothing. Hetzner host has finite RAM shared with Mongo, Vault, and three product backends. You will OOM-kill Mongo before you OOM-kill the subprocesses, because Mongo's working set grows faster. **Pick Option C** (persistent orchestrator, ephemeral researcher containers on demand) but with aggressive idle-eviction: kill the OrchestratorSession after 10 minutes of silence, rehydrate from Mongo on next message. Conversations are not stateful in the subprocess; they're stateful in Mongo. The subprocess is a cache.
- **`platform-net` bridge default is a /16, so IP exhaustion is not your real limit** — the docker daemon's per-event syscall storm during container churn is. At ~5 container starts/sec sustained, dockerd's event loop becomes the bottleneck and `docker ps` starts timing out, which makes your own health checks lie. Rate-limit container spawns in `ContainerManager` (semaphore, max 8 concurrent creates).
- **Idle conversation cleanup needs a TTL index on `ConversationSession.lastActivityAt`**, plus an explicit "abandoned" state after, say, 24h of silence. Telegram has no end-of-session signal. You invent one.
- **Token refresh under concurrency**: if your "re-read from Vault on `invalid_grant`" path doesn't have a per-process mutex (and a Vault-side lease/lock for multi-instance), two in-flight requests hitting expiry simultaneously will both try to refresh, one will win, the other writes a stale token over the fresh one. Single-instance arbiter today hides this. The day you scale to two, it surfaces as random 401s. Add the mutex *now* while you remember; it's 20 lines.
- **Observability**: if OTel spans aren't going anywhere, they're not in the JAR — they're noise. Stand up a single `otel-collector` container on the same host writing to a managed Grafana Cloud free tier. Two hours of work. Without this, "production debugging" means SSH and `docker logs | grep` and you will burn a Saturday on a thing that should have taken 10 minutes.
- **Telegram outbound queue silently dropping is the worst kind of bug** because the user sees nothing wrong; they just stop getting replies. At minimum, emit a counter metric on every drop and alert when the rate is non-zero for 5m. Better: switch the drop to a bounded block with a 2s timeout, then drop with a structured log.

## Where this is over-engineered for one person

- 12 PDLC roles with rework loops and watchdogs is a team's architecture. As a solo founder you cannot meaningfully be on-call for a system that can autonomously spend hours and dollars without you watching. Until you have a second human, every pipeline run should require a human checkpoint at IMPLEMENTER entry, not just at the end.
- Per-role LLM override + step-level override + product defaults is three layers of indirection. You will debug this at 2 AM and not remember which one is winning. Collapse to *one* override table until you have two real users complaining about the default.

## Where this is under-engineered

- No staging. "QA and prod are two containers on the same host" means a bad migration takes both down. A second Hetzner box ($20/mo) running only `qa` is the cheapest insurance you'll ever buy.
- No backup verification. Backups you have not restored are not backups. Restore your Mongo dump to a scratch VM once a month. Put it on the calendar.

## The three changes I would make first, ranked by impact-per-effort

1. **Lock down the Docker socket.** Replace `ContainerManager`'s free-form `docker run` with a hardcoded role→spec table, run arbiter through a `docker-socket-proxy` whitelisting only `POST /containers/create` with schema validation. One afternoon. Eliminates the entire prompt-injection-to-root attack class.
2. **Enforce a per-conversation daily cost cap in arbiter (the one place that sees all sessions), and split `ConversationSession.messages` into its own collection with a TTL index.** One day. Caps your Sunday-morning blast radius at a known dollar amount and prevents the 16MB document cliff that will otherwise silently brick a power user's chat in month three.
3. **Auto-unseal Vault via GCP KMS and verify Mongo off-host backup restore.** One day combined. Turns "host dies on a weekend" from a multi-hour manual recovery into a documented, rehearsed procedure you can execute groggy. This is the single change that lets you actually sleep.
