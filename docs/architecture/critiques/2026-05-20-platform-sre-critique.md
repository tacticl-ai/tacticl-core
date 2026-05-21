# Platform / SRE Architect Critique

**Date:** 2026-05-20
**Persona:** Senior platform / SRE architect (multi-tenant agentic platforms, AI inference on-call)
**Topic:** Operational architecture of Tacticl's Temporal-augmented two-host topology

## 1. Stacking Temporal onto platform-infra

platform-infra is already running Mongo + Vault + MinIO + Neo4j + ClickHouse + Qdrant + the full LGTM stack + otel-collector + Caddy. Realistically that's ~12-14 GB of resident memory before you add anything. ClickHouse alone wants 4GB for its mark cache; Neo4j JVM heap is 2GB minimum to not thrash; Mongo's WiredTiger cache defaults to 50% of RAM-1GB and will silently expand to fill it. Adding temporal-postgres means yet another memory-greedy process with its own page cache that *needs* hot pages to stay resident or every workflow signal becomes a disk seek.

If this is a CX32 (8GB), it's already over-committed and surviving on Linux's page reclaim being merciful. On a CX42 (16GB) you have maybe 2GB of headroom — one ClickHouse query against a cold partition swallows that. The realistic answer: temporal-postgres needs its own dedicated RAM budget (4GB minimum for non-trivial throughput), and you cannot get that on the current box without evicting working sets elsewhere. IOPS is worse — Hetzner Cloud NVMe is shared. Mongo's journal fsync, Postgres WAL fsync, ClickHouse merges, and MinIO writes all hit the same volume. I've watched this exact shape (Mongo + Postgres + Loki on one host) hit `iowait` cliffs at ~3K sustained write IOPS on Hetzner CX. You'll see it as Temporal task timeouts that look like worker bugs but are actually fsync stalls.

platform-apps adds temporal-server + temporal-ui + Java workers in tacticl-api + arbiter spawning ephemeral per-role containers via dockerode. A 12-role pipeline running concurrently with even 3 active Sparks = 36 transient containers. Docker daemon serializes container create/destroy through a single goroutine pool; I've seen `dockerd` event loop saturate at ~50 concurrent create calls/min on Hetzner shared CPU. You'll get arbitrary multi-second hangs on `docker run` that look like Temporal activity timeouts.

Single Hetzner DC: Falkenstein had a 7-hour outage in October 2022 and a partial outage in 2023. RTO/RPO without a second region is "however long Hetzner takes." There is no story here.

## 2. Postgres as Temporal's persistence

`temporalio/auto-setup` is a development convenience — it runs `temporal-sql-tool setup-schema` on boot, which is idempotent but doesn't tune anything. Default Postgres on a shared NVMe with `shared_buffers=128MB` and default autovacuum will fall over around 5K workflows. Temporal's `history_node` table is the canary: it grows monotonically, and the default Postgres autovacuum threshold (20% dead tuples) means you'll see vacuum storms at exactly the wrong times. You need: `shared_buffers=2GB`, `effective_cache_size=4GB`, `autovacuum_vacuum_scale_factor=0.05` for history tables, and aggressive `retention` policies per namespace (default is 30 days, but the Java SDK lets it implicitly default to longer).

`pg_dump` once an hour is wrong for Temporal. Workflow state isn't like an OLTP row — replaying from older history means activities re-execute. If your activity is "charge Anthropic $0.40 for a Sonnet call," you just double-charged. You need WAL archiving to MinIO via `pgbackrest` or `wal-g` for sub-minute RPO. This is a hard requirement, not a nice-to-have, the moment money-spending activities are non-idempotent (which they are — LLM calls are not idempotent).

## 3. Cross-host networking

Hetzner private network is ~0.3-0.5ms p50 between nodes in the same location, but p99 spikes to 5-10ms during noisy-neighbor events. Postgres connection from temporal-server (platform-apps) to temporal-postgres (platform-infra) over this network: every transaction commit pays that RTT. Temporal's persistence layer issues many small reads/writes per workflow event. Pool size needs to be ~`active_workflows × 2`, with `tcp_keepalive` tuned aggressively, and you need PgBouncer in transaction-pooling mode in front of Postgres or you'll exhaust max_connections at ~100 active workflows.

Network capacity: Hetzner private network is "up to 1 Gbps" but shared. MinIO replication + Loki ingestion + Mongo oplog + Postgres traffic all over the same interface. I've seen this saturate at ~400Mbps sustained because of cloud-network hypervisor throttling. Single Caddy = single TLS terminator. Caddy cold-start is fine (<1s), but ACME renewals can stall if Cloudflare rate-limits or DNS-01 challenges flap. No fallback.

## 4. Backups — the actual non-negotiable

Four durable stores, one disk. Mongo → MinIO on same host is **not a backup**, it's a copy. Mirror to platform-apps is same DC = same blast radius as a Hetzner Falkenstein power event. The founder's "no GCP" preference is fine but does not extend to "no off-site." Backblaze B2 ($6/TB/month) or Cloudflare R2 (10GB free, $15/TB after) are the answer. `restic` to B2 with a daily verify job. This is $5-10/month and removes the single largest existential risk.

Vault backups specifically: if `vault operator raft snapshot save` isn't running on cron with the output going off-host, the entire product is one disk failure from being unrecoverable. Tokens, OAuth refresh tokens, Anthropic keys — gone. The founder almost certainly does not have a tested Vault restore. This is *the* thing to fix before Temporal.

## 5. Observability — real credit, real gaps

The LGTM stack is genuine investment most solo founders skip. But "running" ≠ "alerting." Three questions: (1) Is Alertmanager wired to a real channel (PagerDuty/ntfy/SMS)? (2) Are there SLOs defined for Spark completion rate, p95 turn latency, arbiter activity success rate? (3) Are RED/USE dashboards built for the existing services, or just the default node-exporter dashboard?

For Temporal: minimum day-one dashboards are `temporal_workflow_endtoend_latency_seconds` (per workflow type), `temporal_workflow_failed_total`, `temporal_activity_execution_failed_total`, `temporal_sticky_cache_size`, and Postgres-side `pg_stat_activity` for long-running queries. Alerts: workflow stuck >2× p95, activity retry storms (>10 retries in 5min), Postgres connection pool >80%.

Ephemeral agent container logs via dockerode: if these aren't being shipped to Loki *before container exit*, they're gone. Solution is `--log-driver=loki` on container spawn or a sidecar tail pattern. Do this on day one or every postmortem becomes "we don't know what the IMPLEMENTER role saw."

## 6. Multi-tenancy

Three products, two hosts, shared Caddy/Vault/Mongo cluster. Docker resource limits are almost certainly not set today — `docker-compose.yml` files rarely include `mem_limit` and `cpus`. One runaway pipeline (an infinite loop in the IMPLEMENTER role) will burn all platform-apps CPU and degrade strategiz-api response times. Fix: `cpus: "2.0"`, `mem_limit: 2g`, `pids_limit: 200` on every container, especially arbiter and the spawned role containers. This is 30 minutes of work and prevents a class of cross-product incidents.

## 7. Deployment & rollback

Temporal workflow versioning is a real footgun. A bad deploy that changes a workflow signature *without* using `Workflow.getVersion()` will cause non-determinism errors on replay for every in-flight workflow — they become un-finishable until you redeploy the old code. `deploy.sh prod` with no rollback means a corrupted in-flight Spark population. Mandatory: `Workflow.getVersion` checkpoints on every workflow change, plus a tag-based rollback (`./deploy.sh prod v1.2.3`) that retags the running image.

Real-staging on the cheap: a CX22 (~$5/mo) as `platform-staging` with the same docker-compose. That's $60/year and gives you a place to test Mongo schema migrations and Temporal workflow versioning before they hit prod. Co-located `*-qa` containers are not staging; they share kernel, Docker daemon, and disk with prod.

## 8. Bus-factor

One person, one Vault unseal key set. If founder is offline 72h and Vault restarts (kernel update, OOM, anything), the entire stack is dead. Unseal keys must be Shamir-split with at least one share held by a trusted person off-site. The founder explicitly rejected auto-unseal, which is defensible, but the corollary is *operationally split keys*, not "they're on my laptop."

`RUNBOOK.md` per service is the minimum. `deploy.sh` should be idempotent (re-runnable, converges to desired state) — most hand-rolled bash deploy scripts aren't, they assume clean state.

## 9. Cost ledger

Current realistic Hetzner: 2× CX32 ≈ €12/mo, or CX42 ≈ €24/mo. Adding Temporal pushes platform-apps to CX42 minimum (€16-18/mo incremental). Off-site backups to B2: ~$5-10/mo. Real staging CX22: ~$5/mo. Total monthly: €40-60. This topology breaks technically at ~100 concurrent active Sparks (Postgres connection pool, Docker daemon throughput). It breaks financially never — Hetzner is unreasonably cheap. The constraint is operational complexity, not bill.

## 10. The "do nothing" option

The founder's instinct to self-host is mostly correct; the cargo-cult risk is Temporal itself. For "one workflow per Spark with multi-hour pipeline + checkpoints + retries," the lighter alternatives are: (a) **Spring `@Async` + Mongo state machine** — ~400 LoC, you already have Mongo, the failure mode is "process dies, you restart from the last persisted step." (b) **`jcabi-aspects @RetryOnFailure` + a Mongo-backed saga table** — ~600 LoC. (c) **Temporalite** (single-binary Temporal) — same API as Temporal but SQLite-backed, deferring the Postgres problem.

Temporal is correct *if* you genuinely need durable timers (sleep for 6 hours and resume), child workflows, and signal-driven human-in-the-loop checkpoints. You do. But you do not need Temporal Cluster on Postgres on day one — Temporalite gets you 80% there with one container instead of three, no cross-host network, no Postgres tuning. Migrate to clustered Temporal at ~50 concurrent Sparks.

---

**The platform-level non-negotiable before we commit to this plan is off-site, tested, automated backups of Vault + Mongo + Postgres to a different provider (Backblaze B2 or Cloudflare R2) with a quarterly restore drill — because every other failure on this list is recoverable, but losing Vault is product death and the current backup story is a copy on the same disk in the same datacenter.**
