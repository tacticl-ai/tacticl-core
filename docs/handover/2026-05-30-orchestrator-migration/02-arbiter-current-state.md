# 02 — cidadel-ai-arbiter: Current State (the GROWING / destination side)

> Companion to `00-session-decisions.md` (the "why" — locked architectural decisions). This section
> is the verified "what exists today" in `cidadel-ai-arbiter` — the repo the orchestrator migration
> BUILDS ON. Every fact below was read from the actual files on disk
> (`/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter`). Where something could not be verified it
> is marked `UNVERIFIED:`.
>
> **Accuracy note for the next session:** an earlier pass read a STALE/skeleton snapshot and was wrong
> on file names, the proto layout, and counts. The facts below come from a clean re-`find` + direct
> reads of `server.ts`, the providers, the proto files, and `grpc-service.ts`. The repo is
> substantially MORE complete than a "scaffold" framing implies — the full PDLC pipeline engine, the
> learning loop, and observability already live here.

Repo root: `/Users/cuztomizer/Documents/GitHub/cidadel-ai-arbiter`

## TL;DR

- arbiter is a **standalone TypeScript/Node service** (npm workspaces, ESM, `"type": "module"`,
  Node `>=22`, TS `^5.7`, Vitest `^3.0`), NOT a Java/Gradle module. It is the centralized AI execution
  plane consumed over **gRPC**.
- It already runs FOUR things in production: a **multi-provider LLM gateway** (`ArbiterService`), a
  **full PDLC pipeline engine** (`ArbiterPipelineService` → `Shell`, ephemeral Docker containers), a
  **closed-loop retro learning system** (`RetroAgent` + `LearningProposer` + Mongo + Qdrant), and
  **OpenTelemetry observability** (traces/metrics + 4 Grafana dashboards).
- There is **NO conversation orchestrator, NO voice plane, NO persona/skill registry, and NO
  Temporal** in this repo yet. Verified by directory + content inspection. The migration CREATES these.
- The class named `OrchestratorSession`
  (`packages/server/src/orchestrator/orchestrator-session.ts`) DOES exist but is a **Claude Code SDK
  wrapper used INTERNALLY by arbiter for intelligent exception handling / failure escalation** — it is
  NOT the new conversation orchestrator. Its directory is literally `orchestrator/`, so the name
  collision with the planned `SessionWorkflow` orchestrator is a real trap.

## Git state (verified)

- Branch: `main`. Working tree clean except one stray: `M packages/.DS_Store` (tracked noise; ignore
  or untrack).
- Total tracked files: **127** (`git ls-files | wc -l`).
- Last 8 commits (`git log --oneline -8`):
  ```
  364d0fa fix(anthropic): single OAuth refresh owner via ARBITER_OAUTH_REFRESH_OWNER
  add9b04 ops(deploy): fix deploy target — platform-apps, not platform-infra
  764737a feat(anthropic): add claude-opus-4-7 and claude-opus-4-8 to supported models
  be732d9 fix(anthropic): read OAuth token fresh from Vault per call
  fbc4521 ops(deploy): prune dangling images + old build cache after deploy
  2bd456d chore(arbiter): raise BATCH_TIMEOUT_MS 15 min → 60 min
  2531220 feat(arbiter): cost observability with mode + cache labels, BATCH routing, cacheable segments
  d1e5cea fix(agent-image): bump TA-Lib to 0.6.8 (only wheel-available version)
  ```
  Recent work is provider OAuth hardening, deploy ops, and cost observability — NOT orchestrator/
  voice/persona work. All of that is net-new.

## Top-level layout (verified via `ls -1A`)

```
CLAUDE.md             # 108 lines — read in full, summarized below
package.json          # workspace root: workspaces ["packages/*"], engines node>=22
package-lock.json
tsconfig.json
.gitignore            # node_modules/, dist/, .env, .env.*, .worktrees/, docs/superpowers/, .DS_Store
.dockerignore
agent-entrypoint.sh   # the cidadel-agent runner image entrypoint
cloudbuild-agent.yaml # Cloud Build config for the agent image
Dockerfile.agent      # the agent runner image (Claude Code CLI etc.) — distinct from the server image
docs/                 # ONLY docs/superpowers/plans/2026-04-17-arbiter-rewrite.md
                      #   (NOTE: docs/superpowers/ is gitignored — this is a LOCAL plan, not tracked.
                      #    There is NO docs/architecture.md and NO docs/README in this repo.)
packages/             # core, server (the two workspaces)
scripts/              # deploy.sh, index-knowledge.ts, migrate-knowledge-firestore-to-mongo.ts,
                      #   run-initial-index.sh, setup-github-secrets.sh, setup-qdrant-hetzner.sh
.worktrees/           # (empty; gitignored)
node_modules/
```

> The `migrate-knowledge-firestore-to-mongo.ts` script confirms the learning store moved Firestore →
> Mongo already (matches decision §12 — "committed since April 2026").

### Root `package.json` (verified)

`name: cidadel-ai-arbiter`, `private: true`, `workspaces: ["packages/*"]`, `engines.node: >=22`.
Scripts `build`/`test`/`lint` each run `--workspaces`.

### Deployment (from CLAUDE.md — verified)

- Runs **only on `platform-apps`** (`root@178.156.253.208`, private `10.0.1.1`), ProxyJump through
  `platform-infra` (`178.156.141.55`). Deploy: `./scripts/deploy.sh [prod|qa|both]`.
- Prod image lineage: `packages/server/Dockerfile` base → remote
  `/opt/cidadel/arbiter-build/Dockerfile.oauth` hot-patch layer → tagged `:patched-latest` + `:latest`.
- Vault at `http://10.0.1.10:8200`; provider secrets at `secret/strategiz/anthropic`
  (`oauth-access-token`, `oauth-refresh-token`, `api-key`). Default Vault context `strategiz`
  (`VAULT_CONTEXT`).
- gRPC default port `50051` (`GRPC_PORT`/`PORT`); separate Express HTTP on `8080` (`HTTP_PORT`); OTel
  via `OTEL_EXPORTER_OTLP_ENDPOINT`.
- CLAUDE.md also documents configurable embedding providers (`fastembed` default, plus
  `openai`/`voyage`/`ollama`) and warns that changing the embedding model requires recreating the
  Qdrant collection.

## Package layout

### `packages/core` — `@cidadel/ai-arbiter` (v0.1.0, PUBLIC: `private: false`)

Library. `main: dist/index.js`, `types: dist/index.d.ts`, `build: tsc`, tests via Vitest. All AI SDKs
are **optional peerDependencies** (`@anthropic-ai/sdk ^0.52.0`, `@anthropic-ai/claude-code ^1.0.0`,
`openai ^4.80.0`, `@google/generative-ai ^0.24.0`, `@openai/codex ^0.1.0`) with
`peerDependenciesMeta.*.optional = true` — providers degrade gracefully when their SDK is absent. The
same SDKs are also devDependencies for build/test.

Full `.ts` tree under `packages/core/src` (verified — 14 source files + 13 test files):
```
packages/core/src/index.ts                       # barrel — exact exports below
packages/core/src/types.ts                       # GenerateRequest/Response/Event, Message, ToolDefinition, ToolUseBlock, TokenUsage, ContentBlock, ModelInfo, EngineInfo
packages/core/src/router.ts                       # Router — Map<providerName, Provider>, routes by req.engine; listModels()/listEngines()
packages/core/src/arbiter.ts                      # Arbiter facade — builds providers from ArbiterConfig, wraps a Router
packages/core/src/providers/provider.ts           # Provider interface (name, supportedModels, generate, generateStream, isAvailable)
packages/core/src/providers/anthropic.ts          # AnthropicProvider (name 'anthropic') — generate + generateStream + BATCH + cache_control
packages/core/src/providers/openai-compatible.ts  # OpenAiCompatibleProvider — shared OpenAI-wire base
packages/core/src/providers/openai.ts             # OpenAiProvider (extends compatible, api.openai.com/v1)
packages/core/src/providers/openai-direct.ts      # OpenAiDirectProvider — OpenAI key, codex/o-series models (not the CLI)
packages/core/src/providers/grok.ts               # GrokProvider (extends compatible, api.x.ai/v1)
packages/core/src/providers/gemini.ts             # GeminiProvider (@google/generative-ai)
packages/core/src/providers/claude-code.ts        # ClaudeCodeProvider (name 'claude-code') — @anthropic-ai/claude-code query()
packages/core/src/providers/codex-cli.ts          # CodexCliProvider (name 'codex-cli') — spawns `codex exec --json`
packages/core/src/auth/vault-client.ts            # VaultClient — Vault KV v2 read/update (read-merge-write)
packages/core/src/auth/oauth-token-manager.ts     # OAuthTokenManager — Anthropic OAuth refresh against console.anthropic.com
packages/core/src/__tests__/...                   # 13 vitest specs (see "Tests")
```

`core/src/index.ts` exports (verified, EXACT): `* from './types.js'`, `type Provider`,
`AnthropicProvider`, `OpenAiCompatibleProvider`, `OpenAiProvider`, `GrokProvider`, `GeminiProvider`,
`ClaudeCodeProvider`, `OpenAiDirectProvider`, `CodexCliProvider`, `VaultClient`, `OAuthTokenManager`,
`Router`, `Arbiter`, `type ArbiterConfig`.

> CORRECTIONS owed to the task brief's assumed layout:
> - There is **no `packages/core/src/providers/registry.ts`**. The provider lookup is **`Router`**
>   (`core/src/router.ts`), a `Map<string, Provider>` keyed by `provider.name`, dispatched on
>   `req.engine`.
> - There is **no `packages/core/src/auth/index.ts`, `scopes.ts`, or `paseto-validator.ts`**. The
>   real auth dir holds only `vault-client.ts` + `oauth-token-manager.ts`. **There is no PASETO
>   validator and no scopes module anywhere in arbiter.** This matters: decision §4
>   (`00-session-decisions.md`) needs a short-lived session token validated by a shared signing key —
>   that validation code does NOT exist yet and is fully net-new.
> - Providers `openai`, `grok` (and `openai-direct`) extend `OpenAiCompatibleProvider`. So the
>   provider set is: `anthropic`, `openai`, `openai-direct`, `grok`, `gemini`, `claude-code`,
>   `codex-cli` (7 concrete providers). The task brief's "openai*" was right to flag a family.

### `packages/server` — `@cidadel/ai-arbiter-server` (v0.1.0, `private: true`)

The deployable. `main: dist/index.js`, `start: node dist/server.js`, `dev: tsx watch src/server.ts`.
Dependencies (verified): `@cidadel/ai-arbiter: "*"` (workspace) + the five AI SDKs (non-optional
here), `@grpc/grpc-js ^1.12.0`, `@grpc/proto-loader ^0.7.0`,
`@opentelemetry/{api,exporter-trace-otlp-grpc,resources,sdk-node}`, `@qdrant/js-client-rest ^1.17.0`,
`dockerode ^4.0.10`, `express ^5.1.0`, `fastembed ^1.14.1`, `handlebars ^4.7.9`, `mongodb ^7.1.1`,
`openai ^4.80.0`.
**Mongo, Qdrant, fastembed, Express, dockerode, and OTel are ALL already wired** — the migration does
NOT need to add these. (Temporal, Deepgram, ElevenLabs, and a WS lib ARE missing and must be added.)

Full tree under `packages/server/src` (verified — entry + grpc + 3 subsystems + observability):
```
packages/server/src/server.ts                       # MAIN entry — boots OTel, providers, Router, Shell, both gRPC services + Express
packages/server/src/index.ts                        # re-exports createServiceHandlers (test/composition)
packages/server/src/grpc-service.ts                 # createServiceHandlers(router, metrics) — ArbiterService handlers
packages/server/src/otel.ts                         # OTel bootstrap (imported FIRST in server.ts)
packages/server/src/verify-proto.ts                 # proto sanity-check utility
packages/server/src/utils/execFileNoThrow.ts

# orchestrator/ — the INTERNAL Claude Code recovery brain (NOT the conversation orchestrator)
packages/server/src/orchestrator/orchestrator-session.ts   # OrchestratorSession (Claude Code SDK persistent session)
packages/server/src/orchestrator/orchestrator-boot.md      # its boot prompt
packages/server/src/orchestrator/escalation-handler.ts     # EscalationHandler — Express route /orchestrator/escalate → OrchestratorSession

# shell/ — the PDLC pipeline engine (decision §2 bans the word "shell"; the CODE still uses it)
packages/server/src/shell/shell.ts                  # Shell — 1170 lines, the pipeline orchestrator that wires everything
packages/server/src/shell/grpc-pipeline-service.ts  # createPipelineServiceHandlers(shell, tracker, subAgentTracker)
packages/server/src/shell/container-manager.ts      # ContainerManager (dockerode) — ephemeral agent containers, bind mounts, OAuth creds, NdjsonSession
packages/server/src/shell/container-config.ts       # resource classes, default image/network/timeout constants
packages/server/src/shell/workspace-assembler.ts    # WorkspaceAssembler — builds .agent/ tree, renders boot.md, clones repo, Qdrant RAG inject
packages/server/src/shell/registry-client.ts        # createRegistryClient / MongoRegistryClient — pipeline+agent definitions
packages/server/src/shell/registry-cache.ts         # RegistryCache — 60s TTL over RegistryClient
packages/server/src/shell/pipeline-tracker.ts       # PipelineTracker — in-mem state machine, dependency/parallel resolution, skip_when, per-agent TTL
packages/server/src/shell/sub-agent-router.ts       # Express routes /internal/spawn-sub-agent, /internal/stream-agent-progress
packages/server/src/shell/sub-agent-tracker.ts      # SubAgentTracker — orchestrator-container child agents
packages/server/src/shell/watchdog.ts               # Watchdog — per-agent TTL enforcement across all pipelines
packages/server/src/shell/callback-handler.ts       # CallbackHandler — Express /agent/complete → pipeline state transitions
packages/server/src/shell/agent-api.ts              # AgentApi — Express /api/notify, /api/ask, /api/ask/answer/:id (per-agent bearer auth)
packages/server/src/shell/agent-token-store.ts      # AgentTokenStore — per-agent callback bearer tokens
packages/server/src/shell/ndjson-session.ts         # NdjsonSession — docker exec + Claude Code CLI stream-json parse
packages/server/src/shell/backpressure.ts           # BackpressureManager — concurrency limiting
packages/server/src/shell/api-key-rotator.ts        # ApiKeyRotator — distribute Anthropic API keys across containers
packages/server/src/shell/github-client.ts          # GitHubClient — repo clone for workspace
packages/server/src/shell/template-renderer.ts      # TemplateRenderer — handlebars boot.md / knowledge rendering
packages/server/src/shell/workspace-archiver.ts     # WorkspaceArchiver — archive workspaces post-run (date-partitioned)
packages/server/src/shell/workspace-upload-utils.ts # workspace upload helpers
packages/server/src/shell/knowledge-loader.ts       # KnowledgeLoader — authored (registry) + learned (Mongo) knowledge per (product, agentType)
packages/server/src/shell/knowledge-indexer.ts      # KnowledgeIndexer — index knowledge into Qdrant
packages/server/src/shell/qdrant-client.ts          # KnowledgeStore (Qdrant) — collection 'tacticl_knowledge', partitioned by product/userId/role
packages/server/src/shell/embedding-client.ts       # createEmbeddingClient — fastembed/openai/voyage/ollama
packages/server/src/shell/mongo-client.ts           # getMongoDb / closeMongoClient — Mongo db name 'cidadel'

# retro/ — closed-loop learning
packages/server/src/retro/retro-agent.ts            # RetroAgent — scans archived workspaces, proposes learnings
packages/server/src/retro/retro-boot.md             # its boot prompt
packages/server/src/retro/learning-proposer.ts      # LearningProposer — writes proposed learnings to Mongo for human review

# observability/
packages/server/src/observability/otel-setup.ts
packages/server/src/observability/metrics.ts        # ShellMetrics — recordTokenUsage(workload/model/mode/cached*)
packages/server/src/observability/pricing.ts        # token → cost
packages/server/src/observability/trace-propagation.ts
packages/server/src/observability/grafana-dashboards/{agent-health,pipeline-operations,retro-agent,token-economics}.json

# tests
packages/server/src/__tests__/grpc-service.test.ts
packages/server/src/__tests__/utils/execFileNoThrow.test.ts
```

> NOTE on `shell/`: decision §2 says "**there is no shell** — drop that word everywhere." The CODE
> still has a `shell/` dir + a `Shell` class (1170 lines). That IS the existing PDLC engine — the
> thing the migration keeps and re-wires; only the *naming/framing* (→ "full product backend" /
> "pipeline engine") is a correction owed.

## The gRPC contract (verified — TWO proto files, NOT one)

There are **two** proto files (the task brief said one):
- `packages/server/proto/arbiter/v1/arbiter.proto` — package **`cidadel.ai.arbiter.v1`** —
  **`ArbiterService`** (the LLM gateway).
- `packages/server/proto/arbiter-pipeline.proto` — package **`cidadel.ai.arbiter.pipeline.v1`** —
  **`ArbiterPipelineService`** (the PDLC engine).

`server.ts` loads both via `@grpc/proto-loader` (`keepCase: true`, longs String, enums String,
defaults true, oneofs true) and registers both services on one `grpc.Server`, plus a
`grpc.health.v1.Health` service (loaded from `@grpc/proto-loader`'s bundled health.proto, inline
fallback if missing). Server binds **insecure** (`grpc.ServerCredentials.createInsecure()`) on
`0.0.0.0:${GRPC_PORT}` (default `50051`).

### `ArbiterService` (LLM gateway) — verified from `grpc-service.ts`

Handlers exported by `createServiceHandlers(router, metrics?)`:
- `Generate(GenerateRequest) → GenerateResponse` (unary) — `router.generate()`, records token usage.
- `GenerateStream(GenerateRequest) → stream GenerateEvent` (server-streaming) — `router.generateStream()`.
- `ListModels(...) → {models[]}` — `router.listModels()`.
- `ListEngines(...) → {engines[]}` — `router.listEngines()`.

Proto `GenerateRequest` fields (snake_case on the wire, mapped to camelCase core type): `engine,
model, prompt, system_prompt, history[], max_tokens, temperature, tools[]
{name,description,input_schema_json}, request_id, max_turns, max_budget_usd, permission_mode, task,
mode (realtime|batch), cacheable_segments[] {content,role,cacheable}`. `GenerateResponse`: `content,
model, engine, usage{prompt/completion/total_tokens}, stop_reason, tool_calls[]
{id,name,input_json}, request_id, cost_usd`. `GenerateEvent`: `type, content, tool_name, tool_input,
tool_result, usage, cost_usd, request_id, model, engine`. This mirrors `core/src/types.ts` exactly.

### `ArbiterPipelineService` (PDLC engine) — verified from `grpc-pipeline-service.ts`

Handlers from `createPipelineServiceHandlers(shell, tracker, subAgentTracker)`. Proto messages
confirmed: `SubmitPipelineRequest` (`product, pipeline_name, request_context_json,
registry_base_path, callback_url, role_identities, playbook_config_json, github_token, user_id,
knowledge_namespace, repo_url, role_ttl_seconds`) → `SubmitPipelineResponse` (`pipeline_id, status`),
plus pipeline-progress/streaming + ask-resolution messages (`ProtoPipelineProgressRequest` etc. —
`UNVERIFIED:` the full RPC list and exact RPC names beyond Submit, since I read only the message-shape
head of that file; `Shell.SubmitPipelineRequest`/`Response` interfaces in `shell.ts` confirm the
submit surface).

> IMPLICATION: tacticl-core ALREADY talks to this `ArbiterPipelineService` over gRPC (the existing
> Arbiter integration, memory `project_arbiter_integration.md`). The migration's `start_pipeline`
> skill — invoked by a persona inside the new `SessionWorkflow` — should drive THIS service. The
> pipeline plane is done and proven; do NOT rebuild it.

## Server bootstrap (verified — `packages/server/src/server.ts`, 670 lines)

1. `import './otel.js'` FIRST.
2. Load both protos; `grpc.loadPackageDefinition`.
3. Require `VAULT_ADDR` + `VAULT_TOKEN` (else exit). `new VaultClient(addr, token)`.
4. Build providers, each guarded by a Vault read (skipped if its secret/SDK is missing):
   `anthropic` (with `OAuthTokenManager` + per-call fresh-token `tokenProvider`; auto-refresh ONLY if
   `ARBITER_OAUTH_REFRESH_OWNER=true`), `openai`, `gemini`, `grok`, `claude-code`, `codex-cli`
   (ChatGPT-login auth.json from `secret/{ctx}/openai-codex`), `openai-direct`. Exit if zero
   providers. `new Router(providers)`.
5. Connect Mongo from `secret/{ctx}/mongodb` `connection-string` (`getMongoDb`, db `cidadel`).
6. Build `RegistryClient`→`RegistryCache`, `KnowledgeLoader(registryCache, mongoDb)`,
   `TemplateRenderer`; optionally `KnowledgeStore` (Qdrant) + embedder if `QDRANT_URL` set
   (embedding provider resolution: env → LLM-linked default → `fastembed` fallback).
7. `WorkspaceAssembler`, `PipelineTracker`, `ShellMetrics`, `BackpressureManager`,
   `WorkspaceArchiver`, `ApiKeyRotator`, `ContainerManager` (OAuth token+refresh providers wired),
   `CallbackHandler`, `AgentTokenStore` → **`new Shell({...})`**, then `AgentApi`, `Watchdog.start()`,
   `SubAgentTracker`.
8. **`OrchestratorSession` is OPTIONAL and OFF by default** — only booted if
   `ENABLE_ORCHESTRATOR=true`; on success wraps it in `EscalationHandler`. (So the recovery brain is
   not even running in prod unless that flag is set.)
9. Register `ArbiterService` + `ArbiterPipelineService` + Health on the gRPC server.
10. Stand up an **Express HTTP server on `HTTP_PORT` (8080)** mounting: `callbackHandler.router`
    (`/agent/complete`), `agentApi.router()` (`/api/notify`, `/api/ask`, `/api/ask/answer/:id`),
    `createSubAgentRouter` (`/internal/*`), `escalationHandler.router` (`/orchestrator/escalate`, if
    enabled), and `/health`.
11. gRPC `bindAsync` insecure; graceful shutdown on SIGTERM/SIGINT (stop watchdog, shutdown
    orchestrator, destroy containers, close Mongo, close HTTP, `tryShutdown` gRPC).

## What already exists that the migration will BUILD ON

### 1. Providers + Router (`packages/core`)

`Provider` interface (`core/src/providers/provider.ts`, EXACT):
```ts
export interface Provider {
  readonly name: string;
  readonly supportedModels: string[];
  generate(req: GenerateRequest): Promise<GenerateResponse>;
  generateStream(req: GenerateRequest): AsyncGenerator<GenerateEvent>;
  isAvailable(): boolean;
}
```
- **`anthropic`** (`AnthropicProvider`) — `@anthropic-ai/sdk`. **Richest provider.** `generate()` +
  `generateStream()` (yields `{type:'token', content}` on `text_delta`, then `{type:'done', usage}`
  with cache read/write token fields), **BATCH mode** (`req.mode==='batch'`, 5s poll / 60-min
  timeout), and **prompt caching** via `cacheableSegments` → `cache_control: ephemeral` (max 4
  breakpoints). Handles OAuth tokens (`sk-ant-oat*` → `authToken` + `anthropic-beta:
  oauth-2025-04-20`) vs API keys (`x-api-key`). Supported models include `claude-opus-4-8`,
  `claude-opus-4-7`, `claude-sonnet-4-6`, `claude-haiku-4-5`, …. **This is the provider the persona
  plane should call for streaming conversational turns** — token streaming is already implemented.
- **`openai` / `grok` / `openai-direct`** — `openai` SDK; first two extend `OpenAiCompatibleProvider`
  (grok → `api.x.ai/v1`); `openai-direct` is the codex/o-series direct variant.
- **`gemini`** (`GeminiProvider`) — `@google/generative-ai`, `generate()` + `generateStream()`.
- **`claude-code`** (`ClaudeCodeProvider`) — wraps `query()` from `@anthropic-ai/claude-code`; the
  agentic CLI engine for code-writing roles. `generate()` (maxTurns default 1) + `generateStream()`
  (maxTurns default 25, emits `token` + `tool_use`). Reads OAuth fresh per call.
- **`codex-cli`** (`CodexCliProvider`) — spawns `codex exec --json`, parses JSONL; ChatGPT-login
  auth.json restored from Vault per spawn (atomic write).

`Router` (`core/src/router.ts`) = `Map<name, Provider>`, routes by `req.engine`, plus
`listModels()`/`listEngines()`. `Arbiter` (`core/src/arbiter.ts`) = a config-driven facade that
builds providers and wraps a Router (note: `server.ts` does NOT use `Arbiter` — it constructs
providers + `Router` directly).

### 2. gRPC server + ArbiterService handlers (verified, `grpc-service.ts`, 296 lines)

`createServiceHandlers(router, metrics?)` returns `{Generate, GenerateStream, ListModels,
ListEngines}` with full proto↔core mapping (`mapProtoToCore`). Token usage is recorded into
`ShellMetrics` on `Generate` and on the stream's `done` event.

### 3. The PDLC pipeline engine (`shell/`) — the centerpiece the migration keeps

Already implemented and proven:
- **`ContainerManager`** (dockerode): one ephemeral container per agent, bind-mounts the assembled
  workspace, writes Claude OAuth credentials (access+refresh so the CLI self-refreshes), resource
  classes, and `NdjsonSession` streaming via `docker exec` + Claude Code CLI `stream-json`.
- **`WorkspaceAssembler`**: builds the `.agent/` tree (`identity.md`, `assignment.md`, `report.sh`,
  `state.json`, `knowledge/`), renders `boot.md` (handlebars), clones the project repo, and does
  **Qdrant RAG injection** (`writeRetrievedKnowledge` → `.agent/knowledge/retrieved.md`, partitioned
  by `product`+`userId`+`role`).
- **`PipelineTracker`**: in-mem per-run state machine; dependency + parallel-group resolution
  (`getNextAgents`), `skip_when` conditions (`symbols_provided`, `in_skip_roles`), per-agent
  RUNNING/BLOCKED_WAITING_FOR_HUMAN/COMPLETED/FAILED with TTL.
- **`Shell`** (1170 lines) ties it together: submit → register → background run → dispatch agents →
  poll or NDJSON-stream → record results → dispatch next → callback + archive. (Docstring still calls
  tacticl-core's `PdlcPipelineOrchestrator` the caller.)
- Plus `SubAgentRouter`/`SubAgentTracker`, `Watchdog` (TTL), `Backpressure`, `CallbackHandler`,
  `AgentApi` (report.sh notify/ask), `RegistryClient`/`RegistryCache`.

### 4. The learning loop (`retro/` + `KnowledgeLoader` + Mongo + Qdrant) — DO NOT DROP

Per decision §12 and preservation doc (handover §05), this is the accumulated moat:
- **`KnowledgeLoader`** — loads "authored" (registry) + "learned" (**Mongo**, status `approved`)
  knowledge per `(product, agentType)`, written into the agent workspace at boot. (Prompt
  augmentation half of the loop.)
- **`RetroAgent`** (`retro/retro-agent.ts` + `retro-boot.md`) — scans archived workspaces
  (`{archiveBasePath}/{product}/YYYY/MM/DD/...`), identifies cross-cutting patterns, proposes
  learnings.
- **`LearningProposer`** — writes proposed learnings to **Mongo** (`status: 'proposed'`,
  `proposed_by: 'retro-agent'`, categories incl. `failure_pattern`, `knowledge_gap`,
  `template_improvement`, etc.) for human review. (Capture half of the loop.)
- **`KnowledgeStore`** (Qdrant, collection **`tacticl_knowledge`**, partitioned by
  `product`/`userId`/`role`/`visibility`) + **`createEmbeddingClient`** (fastembed default;
  openai/voyage/ollama) + **`getMongoDb`** (db `cidadel`). The Shell also persists `learning` push
  events from a RETRO_ANALYST agent into Qdrant when `knowledgeStore`+`embedder` are present.
- **`OrchestratorSession`** + **`EscalationHandler`** — the Claude Code recovery brain: when an agent
  is stuck/asks, escalate via HTTP `/orchestrator/escalate` to `OrchestratorSession` for guidance.

> The migration MUST re-wire these into the rebuilt `PipelineWorkflow` (Temporal child workflow): the
> SAME `KnowledgeLoader` (inject) + `RetroAgent`/`LearningProposer` (capture). Stores survive untouched.

### 5. Observability (`observability/`)

OTel setup (boots first), `ShellMetrics` (token usage by workload/model/mode + cache labels),
`pricing` (token→cost), trace propagation, and **4 committed Grafana dashboards**
(`agent-health`, `pipeline-operations`, `retro-agent`, `token-economics`).

### 6. Auth primitives (`core/src/auth/`)

`VaultClient` (KV v2 read/update, read-merge-write — NEVER replace) + `OAuthTokenManager` (Anthropic
OAuth refresh, single-owner via `ARBITER_OAUTH_REFRESH_OWNER`, refresh against
`console.anthropic.com/v1/oauth/token` with the Claude Code client id). These secure **provider**
auth. There is **NO caller/request auth** (no PASETO, no scopes, insecure gRPC). The HTTP `AgentApi`
uses per-agent bearer tokens, but that is internal agent↔arbiter only.

## `OrchestratorSession` — exists, but is NOT the new conversation orchestrator

`packages/server/src/orchestrator/orchestrator-session.ts`. Header docstring (verbatim intent): *"A
long-lived Claude Code session that provides intelligent exception handling and template refinement
for the arbiter shell … NOT the conversation orchestrator for the voice/persona plane."* Verified
shape:
- `OrchestratorSessionConfig { model?, cwd?, bootPromptPath?, bootPrompt?, tokenProvider?,
  maxTurnsPerQuery? }` (default model `claude-sonnet-4-6`, default `maxTurnsPerQuery: 3`).
- Wraps `query()` from `@anthropic-ai/claude-code` driven by a self-controlled
  `AsyncIterable<SDKUserMessage>`; lifecycle `boot()` (loads `orchestrator-boot.md`) / `query(msg)`
  (one in-flight at a time) / `shutdown()` (asks for an insights summary, then ends) / `isHealthy()` /
  `getSessionId()`. Refreshes Claude OAuth per call.
- Consumed by `EscalationHandler` (HTTP `/orchestrator/escalate`) for failure recovery. **Off by
  default** (`ENABLE_ORCHESTRATOR`).

**Implication:** the new conversation/persona orchestrator (Temporal-backed `SessionWorkflow`,
decision §3) is NET-NEW and does NOT reuse this class. The name collision is dangerous — put the new
code in a clearly distinct package, and consider renaming this class (e.g. `RecoverySession`).

## What does NOT exist yet (verified — the migration CREATES these)

Confirmed by directory check (`packages/` = only `core` + `server`) and content search:
- **`packages/conversation`** — ABSENT
- **`packages/voice`** — ABSENT
- **`packages/personas`** — ABSENT
- **`packages/orchestrator`** (top-level package) — ABSENT
- **No Temporal** — no `temporalio`/`@temporalio/*` dep, no worker/workflow/activity code. The durable
  `SessionWorkflow` + child `PipelineWorkflow` (decision §3) are net-new.
- **No voice plane** — no Deepgram or ElevenLabs client/import anywhere. STT/TTS I/O adapters
  (decision §8) are net-new.
- **No `SessionWorkflow`, no PersonaRouter, no Persona/Skill registry** — none of the persona-routing
  / registry machinery exists here. (The legacy Java `ConversationService`/persona code in
  tacticl-core is being deleted — the SHRINKING side; see handover §01/§03.)
- **No WebSocket server** — network surfaces today are gRPC (`:50051`) + the Express HTTP API
  (`:8080`, internal). The voice WS (decision §4 — the ONLY direct browser→arbiter path) is net-new.
- **No caller-auth / PASETO / session-token validation** — must be built (decision §4); nothing to
  extend.

So the destination is a complete two-package gRPC service that already owns the pipeline engine,
learning loop, and observability. The orchestrator migration ADDS the conversation brain (durable
Temporal `SessionWorkflow`), persona/skill registries, the voice plane, and the voice-WS auth/edge —
on top of the existing `Provider`/`Router`, `ArbiterPipelineService`/`Shell`, retro, and Mongo+Qdrant
foundations.

## Concrete reuse hooks for the migration (where to plug in)

- **Persona LLM turns (streaming text + tool_use)** → `AnthropicProvider.generateStream()` already
  yields tokens; resolve via `Router` by `engine: 'anthropic'`. Tool defs are typed (`ToolDefinition`
  `{name, description, inputSchemaJson}` in `core/src/types.ts`; `tools[]` on `GenerateRequest`;
  `toolCalls`/`ToolUseBlock` on `GenerateResponse`). NOTE: persona tool_use over the *non-Claude-Code*
  Anthropic path needs the SDK's `tool_use` blocks surfaced — `AnthropicProvider` currently parses
  text + usage; **`UNVERIFIED:` whether it already returns `toolCalls`** (the proto/response type has
  the field; confirm the provider populates it before relying on it for persona tool routing).
- **`start_pipeline` skill** → call the existing `ArbiterPipelineService` (Submit + stream). Pipeline
  events are TEXT (decision §8 — pipeline is SILENT); the persona narrates them.
- **Knowledge inject / learning capture** → reuse `KnowledgeLoader`, `RetroAgent`, `LearningProposer`,
  `KnowledgeStore` (Qdrant), `getMongoDb` (already wired in `Shell`/`server.ts`). Partition by
  `productId` (decision §10) — the partition fields (`product`, `userId`, `role`) already exist.
- **Caching persona/skill registries** → mirror `RegistryCache` (60s TTL) or fetch from Mongo (the
  `mongodb` dep + `MongoRegistryClient` pattern + 5-min refresh interval already exist).
- **Provider auth** → `VaultClient` + `OAuthTokenManager` handle it. **Caller auth (voice WS session
  token)** → net-new; no PASETO/scopes to extend.
- **Cost/usage telemetry** → `observability/pricing.ts` + `metrics.ts` + dashboards already exist.

## Tests (verified)

13 vitest specs in `core/src/__tests__/`: `arbiter`, `router`, `types`, all providers (`anthropic`,
`claude-code`, `codex-cli` + `codex-cli.integration`, `gemini`, `grok`, `openai`, `openai-direct`),
plus `auth/oauth-token-manager` and `auth/vault-client`. 2 in `server/src/__tests__/`: `grpc-service`,
`utils/execFileNoThrow`. So **arbiter IS tested** (correcting the earlier stale-snapshot claim of "no
tests"). The migration's "keep Vitest green" gate (decision §"Execution mode") runs against these.

## Open items / cautions for the next session

- **Name collision:** `OrchestratorSession` (recovery brain) vs. the new conversation
  `SessionWorkflow`; the `orchestrator/` dir already holds the OLD meaning. Pick distinct names early.
- **"Shell" reframe owed:** engine code still uses `shell/` + `Shell` (1170 lines); decision §2 bans
  the word in docs/framing. The engine itself is kept.
- **No caller auth exists:** insecure gRPC + no PASETO/scopes. Voice-WS short-lived session token +
  shared-key validation (decision §4) is fully net-new.
- **Two proto files / two packages:** `cidadel.ai.arbiter.v1` (LLM) and
  `cidadel.ai.arbiter.pipeline.v1` (pipeline) — not one. New conversation RPCs (if any) need a third
  proto/package or an extension of these.
- **`OrchestratorSession` is OFF in prod** unless `ENABLE_ORCHESTRATOR=true` — don't assume it runs.
- **`UNVERIFIED:`** the full RPC list of `ArbiterPipelineService` beyond `SubmitPipeline` (only the
  message-shape head of `grpc-pipeline-service.ts` was read); whether `AnthropicProvider.generate`
  actually populates `toolCalls`; and the exact internal difference between `openai`/`openai-direct`.
- **`packages/.DS_Store` is tracked and dirty** — incidental; untrack or ignore.
