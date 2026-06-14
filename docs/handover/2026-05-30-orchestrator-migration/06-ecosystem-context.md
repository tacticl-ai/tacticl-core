# 06 — Cross-Repo Ecosystem Context

> Handover section for the Cloud Agent Orchestrator → arbiter migration. This is a **navigation
> aid**: it maps the repos, how each product reaches the LLM arbiter, the per-product channels,
> the shared infra, deploy targets, and Vault secret paths a fresh session needs to resume.
>
> The locked architectural decisions for this migration live in the companion file
> [`00-session-decisions.md`](./00-session-decisions.md) — this section does **not** restate them, it
> references them by number (e.g. "Decision 5"). **Read 00 first.**
>
> **Verification status:** every file below was read in full and is VERIFIED. Items I could not
> nail down to a concrete source are marked **UNVERIFIED:** with the reason.
> - `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter/CLAUDE.md`
> - `/Users/cuztomizer/Documents/GitHub/tacticl-core/CLAUDE.md`
> - `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/MEMORY.md` (index)
> - `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/ecosystem_map.md`
> - `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/project_arbiter_integration.md`
> - `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/reference_platform_infra_host.md`
> - `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/project_cloud_agent_orchestrator.md`
> - `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/reference_cloud_agent_orchestrator_docs.md`
> - `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/project_voice_provider_recommendations.md`
> - `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/handover/2026-05-30-orchestrator-migration/00-session-decisions.md`

---

## 0. Migration framing (the one thing to internalize)

Per `00-session-decisions.md` (Decisions 1, 2, 9): the **orchestrator + PDLC pipeline ENGINE moves
into `cidadel-ai-arbiter` (TypeScript/Node)**. `tacticl-core` (Java) **stays a full product
backend** — NOT a "shell." The single unifying rule:

> **Any LLM / AI work routes through the shared arbiter. Everything else stays in the product.**

This reframes everything below: the Cloud Agent Orchestrator described in the older tacticl-core
docs and memory (`CloudAgentSessionWorkflow` / `PipelineWorkflow` as **Java/Temporal in tacticl-core**)
is being **relocated to the arbiter**. Where the older docs say "in tacticl-core," the migration target
is "in cidadel-ai-arbiter." The orchestrator is **one brain** — a durable `SessionWorkflow` (Temporal)
that **spawns a child `PipelineWorkflow`** when a build is needed (Decision 3). This section maps the
ecosystem that this relocation lands in.

---

## 1. The Repos

Three products (**Tacticl**, **Cidadel** shared platform, **Strategiz**) plus the centralized LLM
gateway (**cidadel-ai-arbiter**). This migration primarily touches **cidadel-ai-arbiter** (gains the
engine) and **tacticl-core** (sheds the engine, keeps the product).

### Tacticl (Personal AI Agent)

| Repo | Local path | Stack | Role | Consumes tacticl-core via |
|------|-----------|-------|------|---------------------------|
| **tacticl-core** | `/Users/cuztomizer/Documents/GitHub/tacticl-core` | Java 25, Spring Boot 4.0.3, Gradle 9.4.0 (Kotlin DSL) | Cloud backend, REST API (`/v1/`), product domain (auth, channels, OAuth, device pairing, billing/quotas, REST projections, UX). **Engine leaving this repo.** | — |
| **tacticl-web** | (sibling repo) | React 19, Vite, MUI 7, TypeScript | Web dashboard (Spark Control, Chat, Devices, Social, Repos); voice sphere UI at `/chat` | REST (`src/api/*.ts`) + WebSocket |
| **tacticl-mobile** | (sibling repo) | React Native, Expo, TypeScript | Mobile app (Chat, Push-to-talk, Device pairing) | REST (`src/api/*.ts`) + WebSocket |
| **tacticl-device** | (sibling repo) | Electron 33, TypeScript, Anthropic SDK | Desktop device agent (tray app, executes Sparks locally) | **WebSocket only** (no REST), plus Claude API directly |

Per `ecosystem_map.md`: tacticl-web consumes 9 API modules / 44 endpoints; tacticl-mobile 7 API
modules / 20 endpoints; tacticl-device is `src/main/ws-client.ts`, WebSocket only.

### Cidadel (Shared Platform)

| Repo | Local path | Stack | Role |
|------|-----------|-------|------|
| **cidadel-core** | (sibling repo) | Java 25, Spring Boot 4, Gradle | Shared infrastructure published to **GitHub Packages** under group `io.cidadel`: auth (`framework-authorization`), PASETO tokens (`framework-token-issuance`), exceptions, logging, secrets/Vault, resilience, AI engine framework, data base classes, `client-base`. |
| **cidadel-ai-arbiter** | `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter` | Node.js >= 22, TypeScript ^5.7, Vitest ^3.0 | **Centralized LLM gateway** + (post-migration) **home of the orchestrator + PDLC pipeline engine + per-product learning layer.** Single gRPC ingress for ALL AI model calls. **Primary repo gaining code in this migration.** |
| **cidadel-console** | (sibling) | Vite, TypeScript | Admin console for Cidadel platform |
| **cidadel-web** | (sibling) | Vite, TypeScript | Cidadel public web |

> **cidadel-core publishing note** (`MEMORY.md`): group `io.cidadel`, version `0.4.11` as of
> 2026-03-15. Published to `https://maven.pkg.github.com/cidadel-platform/cidadel-core`. The earlier
> arbiter-integration project (April 2026, see §2) targeted publishing **cidadel-core 0.6.3** with the
> updated proto + gRPC client. Consumers need **both** the cidadel and strategiz GitHub Packages
> repos (LLM clients historically came from strategiz). Requires `GITHUB_ACTOR` + `GITHUB_TOKEN`.

### Strategiz (Investment Platform)

| Repo | Stack | Role |
|------|-------|------|
| **strategiz-core** | Java 25, Spring Boot 4, Gradle (note: `MEMORY.md` references Maven history too) | Strategiz backend; **full product backend, enormous, keeps all its domain** (Decision 2). Uses the shared engine as **one capability among many** (Decision 9). Historically the source of the LLM client modules (`client-anthropic-direct`, etc.) that tacticl reused. |
| **strategiz-ui** | Vite, TypeScript | Strategiz web frontend |
| **strategiz-docs** | Static | Documentation site |

> Tacticl shares **auth/token keys with Strategiz** for cross-product SSO (PASETO v4.local, shared
> keys). LLM Vault secrets also live under the `strategiz` Vault context (see §6).

### A fourth brand exists: PointsTax

`reference_platform_infra_host.md` (VERIFIED) names **pointstax** among the brands served by the
shared Hetzner box (`pointstax-landing-frontend`). Not in `ecosystem_map.md`; not relevant to the
engine migration, but noted so a fresh session isn't surprised by it on `platform-infra`.

### cidadel-ai-arbiter internal layout (VERIFIED from its CLAUDE.md)

```
packages/
  core/    — @cidadel/ai-arbiter        : provider abstractions, routing logic, shared types (leaf pkg, no internal deps)
  server/  — @cidadel/ai-arbiter-server : gRPC server, OTel instrumentation, entrypoint (depends on core via workspace *)
```

- Transport: **gRPC**, proto definitions in `packages/server/proto/`.
- Providers: Anthropic SDK, Claude Code SDK, OpenAI, Codex SDK, Gemini, Grok. All AI SDKs are
  **optional peerDependencies** of `core` — providers gracefully degrade when their SDK is absent.
- Observability: **OpenTelemetry** traces exported via **OTLP gRPC** (`OTEL_EXPORTER_OTLP_ENDPOINT`).
- Build/test: `npm run build`, `npm run test` (Vitest), `npm run lint` (type-check only), dev server
  `cd packages/server && npm run dev` (tsx hot-reload).
- Module rule: `server -> core`; `core` is a leaf (no internal deps).

---

## 2. How Each Product Reaches the Arbiter

The arbiter is the **single ingress point for all AI model calls** in the Cidadel platform (VERIFIED,
arbiter CLAUDE.md line 5). Two distinct access paths, per `00-session-decisions.md`:

### 2a. Internal gRPC — the only path for product backends (Decision 5)

- Transport **gRPC**, default port **`50051`** (`GRPC_PORT`, VERIFIED).
- **INTERNAL-ONLY.** Browsers NEVER call arbiter over gRPC. Only product backends (**tacticl-core,
  strategiz-core, future products**) are gRPC callers. Same integration pattern for every product, so
  the arbiter can evolve its API freely without breaking browser clients.
- On the shared Hetzner box, Caddy addresses the arbiter as `h2c://cidadel-arbiter-prod:50051` —
  **never enable `encode` on arbiter site blocks (breaks gRPC)** (`reference_platform_infra_host.md`,
  VERIFIED).

### 2b. Voice WebSocket — the ONE direct browser → arbiter path (Decision 4)

- A **latency exception**: voice has a **<1200ms p50** target; a Java relay hop through the product
  backend would blow it, so the browser's voice WS connects **directly to the arbiter**.
- Auth: a **short-lived session token signed by the product backend (tacticl-core) at session
  start**; the **arbiter validates it via a shared signing key in Vault.**
- Everything else (REST, non-voice WS) goes browser → product backend → arbiter.

### Tacticl ↔ arbiter integration history (VERIFIED, `project_arbiter_integration.md`)

This migration builds on an April 2026 integration that already replaced tacticl's direct LLM clients
with the arbiter via gRPC:

- **Why:** centralize LLM routing, credential management, cost tracking. Claude Code CLI via the
  arbiter gives per-request workspace isolation for concurrent PDLC pipelines.
- **Three tacticl-side engine types:** `arbiter-api` (single-turn), `arbiter-agentic` (local tool
  loop), `arbiter-cli` (full CLI via arbiter). **No direct REST or CLI LLM clients remain in tacticl.**
- **Scope (3 repos):** cidadel-ai-arbiter (new `claude-code-cli` provider), cidadel-core (proto +
  client update → publish 0.6.3), tacticl-core (consume, rewire engines).
- **Key decisions:** full replacement (option A, no fallback); CLI subprocess (not SDK) for agentic
  execution; workspace isolation via per-request temp dir + shallow git clone; CLI flag is
  **`--max-cost`** (NOT `--max-budget-usd`).
- **Spec:** `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/specs/2026-04-01-tacticl-arbiter-grpc-integration-design.md`
- **Plan:** `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/plans/2026-04-01-tacticl-arbiter-grpc-integration.md`
  (paths quoted from memory; confirm on disk.)

### Spark / projection write pattern (Decision 6)

The arbiter **writes orchestrator state directly to shared Mongo** (projection pattern, referenced as
SAD §3.7). The product backend **READS** those projections to serve its REST surface. Mongo
`pipeline_runs` / `pipeline_events` / `pipeline_checkpoints` are projections; **Temporal workflow
history is the source of truth** (`project_cloud_agent_orchestrator.md`, VERIFIED).

### Product-awareness via data, not forked code (Decision 10)

`productId` is a first-class scoping key threaded through `SessionWorkflow`, `PersonaRouter` (picks
that product's personas), and the **per-product-partitioned knowledge/learning store**
(`agent_knowledge.product`, Qdrant `product` field). The engine code stays generic/multi-tenant —
**no `if (product === 'tacticl')` branches.** The per-product learning partition is the moat.

---

## 3. Per-Product Channels (Decision 11 — VERIFIED in `00-session-decisions.md`)

Channels live in each product as **thin signalers**: they translate inbound events into arbiter
signals. **`start_pipeline` is decided by `PersonaRouter` inside the arbiter, not by the channels.**
Both products feed the SAME engine.

### Tacticl channels

- **Telegram** bot — inbound bot mentions, `/spark`, `/repo <github-url>`, voice transcripts. Legacy
  path: `TelegramConversationAdapter` → `ConversationService` (gather → propose → align), handoff on a
  `<<<START>>>` marker. **Under the migration** the adapter becomes a thin signaler translating inbound
  Telegram events into workflow signals; the marker protocol
  (`<<<CREATE_REPO>>>`, `<<<PROPOSE>>>`, `<<<START>>>`) is replaced by persona tool calls
  (`propose_implementation`, `start_pipeline`). Operator runbook: `docs/runbooks/telegram-bot.md`.
- **Web chat + voice sphere** (tacticl-web `/chat`) — voice WS connects directly to the arbiter (§2b);
  non-voice traffic relays through tacticl-core.
- **Mobile push-to-talk** (tacticl-mobile) — legacy: expo-av → Whisper API (~500ms) → text →
  `POST /v1/agent/command`. Migrates to the orchestrator.

### Strategiz channels

- **Discord** — writes alerts AND accepts commands. Plus **strategiz-web dashboard.**
  (VERIFIED in `00-session-decisions.md` Decision 11 — this resolves the brief's
  "Strategiz = Discord alerts + commands.")

> Contrast: **Tacticl** is conversational/agentic (Telegram + web voice sphere + mobile PTT);
> **Strategiz** is alert/command oriented (Discord + dashboard). Same engine, different channel shapes.

---

## 4. Voice Plane (Tacticl) — VERIFIED

Voice providers live in the **Orchestrator/conversation layer ONLY; the pipeline is SILENT**
(Decision 8). Deepgram (STT) and ElevenLabs (TTS) are I/O adapters wrapping the conversation — there
is **no audio anywhere inside the pipeline.** End-to-end shape (Decision 8):

```
mic → Deepgram STT → text → SessionWorkflow → PersonaRouter → persona (Anthropic stream)
    → persona decides to build → start_pipeline → [SILENT child PipelineWorkflow runs containers]
    → pipeline emits TEXT events → persona narrates them → ElevenLabs TTS → voice sphere speaks
```

Concrete provider config (`project_cloud_agent_orchestrator.md`, VERIFIED):

- **STT: Deepgram** `nova-2`, streaming WS, `endpointing=300`, VAD on, 16kHz s16le from browser.
- **TTS: ElevenLabs** `eleven_turbo_v2`, streaming WS, single voice for v1 (per-persona voices → v1.5).
- Mic: always-on VAD with auto-mute during TTS; user can switch to push-to-talk or text-only; no
  wake-word v1. Barge-in: `onBargeIn` signal → activity heartbeat-cancellation → TTS aborted.
- **Provider kill switches** (the only flags retained — gate external calls, NOT rollout):
  `tacticl.deepgram.enabled` (STT) and `tacticl.elevenlabs.enabled` (TTS). Degradation: Deepgram down →
  forced TEXT_ONLY; ElevenLabs down → captions only.
- Provider choice rationale: `project_voice_provider_recommendations.md` (user pick 2026-05-19).
  Whisper (`client-whisper`) is OUT of the live voice path, retained ONLY for file-upload paths
  (Telegram voice messages, future uploads).

---

## 5. Shared Infrastructure & Deploy Targets

There are **two Hetzner hosts** — do not conflate them.

### platform-infra (Hetzner) — `reference_platform_infra_host.md` (VERIFIED)

Single box hosting **every prod frontend + API ingress for all four brands** (tacticl, cidadel,
strategiz, pointstax).

- SSH alias `platform-infra` → `178.156.141.55`, user `root`, **port `443`**. Hostname `clickhouse-prod`.
- **Caddy** runs in Docker (`caddy:2-alpine`, container `caddy`); config bind-mounted
  `/opt/cidadel/Caddyfile` → `/etc/caddy/Caddyfile`. Reload:
  `docker exec caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile`.
  **Gotcha: never `sed -i` the Caddyfile** (replaces inode, breaks bind mount).
- **Static frontends** bind-mounted at `/opt/cidadel/{tacticl,auth,console,strategiz-console,web,pointstax-landing,web-frontend-qa}-frontend`
  (tacticl-web deploys here via `scripts/deploy.sh` rsync).
- **Backends** are Docker containers reverse-proxied by name: `tacticl-api-prod:8080`,
  `cidadel-api-prod:8080`, `strategiz-api-prod:8082`, etc.
- **Arbiter** addressed here as `h2c://cidadel-arbiter-prod:50051` (gRPC; never `encode`).
- Also runs heavy workloads (ClickHouse, Strategiz Python polars detectors) that can CPU-starve Caddy.

### platform-apps (Hetzner) — arbiter deploy target (arbiter CLAUDE.md, VERIFIED)

The arbiter runs **only on platform-apps, NOT platform-infra.**

- Host `root@178.156.253.208`, private `10.0.1.1`. SSH alias `platform-apps`, **ProxyJump through
  platform-infra** (`178.156.141.55`).
- Deploy: `./scripts/deploy.sh [prod|qa|both]` (default `both`), run from the arbiter repo.
- Image lineage: `packages/server/Dockerfile` (base) → remote
  `/opt/cidadel/arbiter-build/Dockerfile.oauth` (hot-patch layers) → tagged `:patched-latest` +
  `:latest`; containers force-recreated. Keep a `rollback-<date>` tag.
- **Vault for arbiter:** `http://10.0.1.10:8200` (`VAULT_TOKEN` in `/opt/cidadel/.env` on
  platform-apps; Bitwarden "platform-apps env").

> Reconciliation note: `reference_platform_infra_host.md` shows an `cidadel-arbiter-prod` container on
> the **platform-infra** Caddy network, while the arbiter CLAUDE.md says the arbiter **runs on
> platform-apps**. Most consistent reading: the arbiter process lives on platform-apps and is reached
> from platform-infra's Caddy over the private network by that name. **Confirm the actual host of the
> running arbiter container before deploying** (it determines which `deploy.sh` / Vault you touch).

### Temporal + Postgres

- The orchestrator is **Temporal-backed**, durable: `SessionWorkflow` (one per conversation) spawns a
  child `PipelineWorkflow` (Decision 3; older Java names `CloudAgentSessionWorkflow` /
  `PipelineWorkflow`). The 2026-05-25 pivot **OVERRODE** the prior Temporal deferral — **full
  Postgres-backed Temporal cluster adopted now** (`project_cloud_agent_orchestrator.md`, VERIFIED).
  Temporal therefore needs a **Postgres** backing store. The memory states the **Temporal cluster
  lives on Hetzner (same host as Arbiter v1).** Key workflow facts (VERIFIED, memory): pipelines are
  **user-scoped not session-scoped**, `workflowId = "pipeline-{sparkId}"`,
  `ParentClosePolicy.ABANDON`, `WorkflowExecutionTimeout = 7 days`.

### MongoDB

- System-of-record for orchestrator/registry entities AND the projection target the arbiter writes to
  (Decision 6). Collections (tacticl-core CLAUDE.md + memory): `conversation_sessions`, `personas`,
  `skills`, `playbooks`, `pipeline_runs`, `pipeline_events`, `pipeline_checkpoints`, `sparks`. The
  **learning layer** lives in Mongo **`agent_knowledge`** + Qdrant (Decision 12 — DO NOT DROP).
- `MEMORY.md` index (`project_database_migration.md`): **"Tacticl should be fully on MongoDB
  (Hetzner), NOT Firestore — migration is unfinished."** tacticl-core CLAUDE.md still documents many
  Firestore collections (in-progress state). **Confirm Firestore vs Mongo per collection before
  assuming.**

### Qdrant (vector store, arbiter)

- The arbiter's knowledge store uses configurable embedding providers writing to a **Qdrant**
  collection (arbiter CLAUDE.md, VERIFIED). Default `fastembed` (local ONNX, no key, 384–1024 dims);
  alternatives `openai` (1536), `voyage` (1024, Anthropic-recommended), `ollama`. The learning layer's
  Qdrant `product` field partitions per product (Decision 10). **Changing embedding provider/model
  after a collection exists requires recreating the Qdrant collection** (vector dims must match) — and
  Decision 12 says **NEVER drop the Qdrant collection** during migration.

### Vault — three servers (see §6)

### tacticl-core deploy target — Cloud Run (tacticl-core CLAUDE.md + ecosystem_map.md, VERIFIED)

> Note: `ecosystem_map.md` lists Cloud Run as the tacticl-core target, while
> `reference_platform_infra_host.md` shows a `tacticl-api-prod:8080` **container on Hetzner Caddy.**
> Both memories are real point-in-time observations; tacticl-core prod may have moved (or be moving) to
> the Hetzner box. **Confirm the live prod target for tacticl-core before deploying** — it is the
> single most important unverified deploy fact here.

- (Cloud Run per ecosystem_map) GCP project `tacticl`, Artifact Registry `tacticl-core`, region
  **us-east1**, public access. Spring profiles `qa` / `prod`.
- QA service name: `tacticl-core-qa` (tacticl-core CLAUDE.md, 2Gi) **vs** `tacticl-api-qa`
  (ecosystem_map.md). **Service-name discrepancy is UNVERIFIED — confirm in the GCP console /
  `deployment/cloudbuild/cloudbuild-qa.yaml`.** Prod: `tacticl-core` (4Gi).
- Deploy: QA `gcloud builds submit --config deployment/cloudbuild/cloudbuild-qa.yaml .`;
  Prod `gcloud builds submit --config deployment/cloudbuild/cloudbuild-prod.yaml .`.
- Claude Code CLI must be in the image (required by IMPLEMENTER/TESTER/DEVOPS pipeline roles) — though
  post-migration that execution moves to the arbiter's containers, so re-confirm whether tacticl-core
  still needs it.

### Other client deploy targets (`ecosystem_map.md`)

- tacticl-web → Firebase Hosting → `tacticl.web.app` (also rsync'd to platform-infra per infra memory)
- tacticl-mobile → EAS OTA (`npx eas update --channel qa | production`)
- tacticl-device → Electron Builder (macOS, `npm run build:mac`)

---

## 6. Vault Secret Paths (LLM / Voice / Auth)

### Vault servers (three — do not conflate)

| Context / location | URL | Notes |
|--------------------|-----|-------|
| Local dev (tacticl) | `https://localhost:8200` (**HTTPS**, not HTTP) | Vault context `tacticl`; for shared LLM clients use context `strategiz`. (`MEMORY.md`) |
| Prod (platform host) | `http://vault:8200` (compose) / `http://10.0.1.10:8200` (on-host) | Self-hosted Vault; Cloud Run decommissioned |
| platform-apps (arbiter) | `http://10.0.1.10:8200` | `VAULT_TOKEN` in `/opt/cidadel/.env` on platform-apps. (arbiter CLAUDE.md, VERIFIED) |

### LLM provider secrets

| Provider | Vault path | Key(s) |
|----------|-----------|--------|
| Anthropic | `secret/strategiz/anthropic` | `api-key`, **plus** `oauth-access-token`, `oauth-refresh-token` (all three at this path per arbiter CLAUDE.md line 80, VERIFIED) |
| OpenAI | `secret/strategiz/openai` (`MEMORY.md` "likely"); arbiter embeddings uses `secret/{ctx}/openai` | `api-key` |
| Grok | `secret/strategiz/grok` (`MEMORY.md` "likely") | `api-key` |
| Voyage (embeddings) | `secret/{ctx}/voyage` | `VOYAGE_API_KEY` (arbiter CLAUDE.md, VERIFIED) |

> Anthropic path config source: `AnthropicVaultConfig.java` in strategiz-core `client-anthropic-direct`.
> Common gotcha (`MEMORY.md`): **Anthropic 403 = missing API key in Vault** → check
> `secret/strategiz/anthropic` has `api-key`.

### Arbiter runtime env (arbiter CLAUDE.md, VERIFIED)

`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY`, `GRPC_PORT` (default 50051),
`OTEL_EXPORTER_OTLP_ENDPOINT`. Production sources these from Vault; do NOT commit `.env`.

### Tacticl-specific Vault secrets (tacticl-core CLAUDE.md, VERIFIED)

- GitHub PAT for repo creation: `secret/tacticl/github` → key `app-token` (`manage_repo` skill /
  `GitHubClient.createRepo`). Runbook: `deployment/runbooks/github-app-token.md`.
- Web search: `brave-search.api-key`. Web read: `jina.api-key`.
- Google OAuth (Photos/YouTube/Gmail shared): `google.client-id`, `google.client-secret`.

### Voice-WS auth signing key (Decision 4)

The short-lived voice session token is signed by tacticl-core and validated by the arbiter via a
**shared signing key in Vault**. **UNVERIFIED:** the exact Vault path for this shared signing key is
not stated in any file read — a fresh session must define/locate it (it is a NEW secret this migration
introduces).

### Deepgram / ElevenLabs secrets — **UNVERIFIED**

Provider choice is verified (Deepgram STT, ElevenLabs TTS) but the **Vault paths for their API keys
were not found** in the files read. Likely by convention `secret/strategiz/deepgram` /
`secret/strategiz/elevenlabs` or `tacticl`-context equivalents — **a guess, not verified.** Confirm
against the orchestrator SAD and any voice config classes/`client-deepgram` / `client-elevenlabs`
module configs before relying on a path.

---

## 7. Quick Navigation Index (absolute paths)

**Repos**
- tacticl-core: `/Users/cuztomizer/Documents/GitHub/tacticl-core`
- cidadel-ai-arbiter: `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter`
- Siblings (tacticl-web / tacticl-mobile / tacticl-device / cidadel-core / strategiz-core) live in the
  VS Code multi-root workspace; not under the verified `Documents/GitHub` snapshot — locate via the
  workspace.

**Canonical orchestrator docs** (paths from `reference_cloud_agent_orchestrator_docs.md`, VERIFIED; rooted at tacticl-core)
- PRD: `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-prd.md`
- SAD: `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md`
- Plan: `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/superpowers/plans/2026-05-25-cloud-agent-orchestrator.md`

**Superseded docs** (`reference_cloud_agent_orchestrator_docs.md`)
- `.../docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-prd.md` — fully superseded
- `.../docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-sad.md` — orchestration superseded; Arbiter
  execution-plane sections (workspace assembly, gRPC protocol, 4-layer knowledge, Mongo pipeline
  schema) **remain canonical** and are referenced by the new SAD.

**Migration plan + preservation doc** (from `00-session-decisions.md`)
- Migration plan: `.../docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md`
  (filename quoted from Decision-corrections §1; confirm on disk)
- Learning-layer preservation: `.../docs/architecture/learning-layer-and-codegen-prompts-preservation.md`
- Diagrams (source of truth): `.../docs/architecture/*.drawio` (+ `.png`)

**Arbiter integration docs (April 2026)**
- Spec: `.../docs/superpowers/specs/2026-04-01-tacticl-arbiter-grpc-integration-design.md`
- Plan: `.../docs/superpowers/plans/2026-04-01-tacticl-arbiter-grpc-integration.md`

**Other architecture docs (tacticl-core CLAUDE.md)**
- `.../docs/architecture/cloud-orchestrator-architecture.md`
- `.../docs/architecture/device-agent-architecture.md`
- `.../docs/architecture/pdlc-pipeline-architecture.md`

**Cross-repo ecosystem source of truth**
- `../tacticl-docs/CLAUDE.md` (dedicated docs repo `tacticl-docs`) — auto-load at session start per
  tacticl-core CLAUDE.md.

**Memory files (all VERIFIED in this session; open for full detail)**
- `.../memory/MEMORY.md` (index)
- `.../memory/ecosystem_map.md`
- `.../memory/project_arbiter_integration.md`
- `.../memory/project_cloud_agent_orchestrator.md` (most detailed orchestrator state)
- `.../memory/reference_cloud_agent_orchestrator_docs.md`
- `.../memory/reference_platform_infra_host.md`
- `.../memory/project_voice_provider_recommendations.md`
- `.../memory/project_database_migration.md` (Firestore→Mongo status; not re-read in full this session)
  (memory dir root: `/Users/cuztomizer/.claude/projects/-Users-cuztomizer-Documents-GitHub-tacticl-core/memory/`)

**Companion handover section**
- `/Users/cuztomizer/Documents/GitHub/tacticl-core/docs/handover/2026-05-30-orchestrator-migration/00-session-decisions.md`
  (the 12 locked decisions + ULTRA execution mode + corrections owed — **read first**.)

---

## 8. Open Items for the Fresh Session (verification debt)

1. **Where does the running arbiter container actually live** — platform-apps (arbiter CLAUDE.md) vs the
   `cidadel-arbiter-prod` reference on platform-infra Caddy. Determines which `deploy.sh` + Vault you
   touch (§5).
2. **tacticl-core live prod deploy target** — Cloud Run (ecosystem_map) vs `tacticl-api-prod:8080`
   container on Hetzner (infra memory). Confirm before any tacticl-core deploy (§5).
3. **tacticl-core QA Cloud Run service name** — `tacticl-core-qa` vs `tacticl-api-qa`; check the GCP
   console / `deployment/cloudbuild/cloudbuild-qa.yaml` (§5).
4. **Voice-WS shared signing-key Vault path** — NEW secret introduced by Decision 4; define/locate it (§6).
5. **Deepgram / ElevenLabs Vault secret paths** — not found in sources (§6).
6. **cidadel-core target version** for the engine migration vs current published (memory baseline
   0.4.11; April integration targeted 0.6.3) — check `gradle/libs.versions.toml` in both repos (§1).
7. **Firestore vs MongoDB current state per collection** — migration explicitly "unfinished" (§5).
8. **Persona/skill registry scoping** — shared product-tagged vs per-product namespaces is still an
   OPEN question in `00-session-decisions.md`; do not treat either as canonical until confirmed.
9. **Learning layer DO-NOT-DROP** — Mongo `agent_knowledge` + the Qdrant collection must survive the
   migration and be re-wired (`KnowledgeLoader`, `RetroAgent`/`LearningProposer`) per Decision 12.
