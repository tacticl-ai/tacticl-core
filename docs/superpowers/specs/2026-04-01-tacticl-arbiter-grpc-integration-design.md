# Tacticl ↔ Arbiter gRPC Integration Design

## Problem

Tacticl-core currently makes LLM calls via direct REST clients (anthropic-direct, openai-direct, grok-direct) and Claude Code / Codex CLI subprocesses. This means:

1. **Duplicated infrastructure** — API keys, OAuth token rotation, rate limiting managed per-consumer
2. **CLI subprocess overhead** — spawning `claude` process per execution adds ~11s latency vs native SDK (~0.8s)
3. **No centralized cost tracking** — each consumer tracks tokens independently
4. **Context isolation risk** — concurrent PDLC pipelines sharing CLI subprocesses need careful isolation

## Solution

Route all LLM traffic through **cidadel-ai-arbiter** via gRPC. The arbiter is a Node.js gRPC service (Cloud Run) that centralizes provider management, credential rotation, and cost tracking.

For agentic code execution, add a new **`claude-code-cli` engine** to the arbiter that spawns Claude Code CLI subprocesses with per-request workspace isolation (temp directory + git clone). Each request is a separate OS process with its own working directory — zero context leakage between concurrent pipelines.

## Architecture

```
Tacticl (Cloud Run)                    Arbiter (Cloud Run)
┌─────────────────────────┐            ┌────────────────────────────┐
│ PDLC Pipeline           │            │ gRPC Server (port 50051)   │
│   IMPLEMENTER           │──gRPC───→  │                            │
│   REVIEWER              │            │ engine=claude-code-cli:    │
│   TESTER                │            │   → mkdtemp + git clone    │
│                         │            │   → claude --print ...     │
│ Classification:         │            │   → parse stream-json      │
│   SPARK_CLASSIFIER      │──gRPC───→  │   → return + cleanup       │
│                         │            │                            │
│                         │            │ engine=anthropic/openai/…: │
│                         │            │   → REST API call          │
└─────────────────────────┘            └────────────────────────────┘
```

## Scope: 3 Repos

### 1. cidadel-ai-arbiter (Node.js)

- Extend proto with `WorkspaceConfig` message
- Add `workspace` field to `GenerateRequest`
- Create `ClaudeCodeCliProvider` — spawns `claude --print --output-format stream-json` per request
- Workspace provisioning: `mkdtemp` → `git clone` → run → cleanup
- Register in `server.ts`
- Ensure Dockerfile has Claude Code CLI installed

### 2. cidadel-core (Java)

- Sync proto file with arbiter
- Regenerate Java gRPC stubs (automatic via protobuf plugin)
- Update `ArbiterClient.generate()` to accept `WorkspaceConfig`
- Publish new version to GitHub Packages

### 3. tacticl-core (Java)

- Bump cidadel version to include `client-ai-arbiter`
- Add `client-ai-arbiter` dependency to `business-agent`
- Remove direct LLM client dependencies (`client-anthropic-direct`, `client-openai-direct`, `client-grok-direct`)
- Remove CLI engine dependencies (`client-claude-code`, `client-codex`)
- Create `ArbiterCliAiEngine` — new `AiEngine` for CLI execution via arbiter
- Rewrite `AiEngineAdapterConfig` — 6 provider-specific beans → 3 arbiter-backed beans
- Update `AiSdlcStepDefaults` — remap all engine IDs
- Add `arbiter.client.*` config to application properties

## Engine Mapping

### tacticl-side engines (after migration)

| Engine ID | Class | Purpose |
|-----------|-------|---------|
| `arbiter-api` | `ApiAiEngineAdapter(ArbiterLlmProvider)` | Single-turn: classification, commit messages, content |
| `arbiter-agentic` | `AgenticApiAiEngine(ArbiterLlmProvider)` | Multi-turn local tool loop: social, creative, research |
| `arbiter-cli` | `ArbiterCliAiEngine(ArbiterClient)` | Full agentic CLI via arbiter: code gen, testing, devops |

### How arbiter resolves internally

| tacticl engine | arbiter engine | Model example | Arbiter behavior |
|----------------|---------------|---------------|------------------|
| `arbiter-api` | anthropic/openai/grok | claude-haiku-4-5 | REST API call |
| `arbiter-agentic` | anthropic/openai/grok | claude-sonnet-4-5 | REST API call (local tool loop in tacticl) |
| `arbiter-cli` | claude-code-cli | claude-opus-4-6 | Spawn `claude` CLI subprocess |

### Step defaults mapping

| Before | After |
|--------|-------|
| `anthropic-api` | `arbiter-api` |
| `openai-api` | `arbiter-api` |
| `grok-api` | `arbiter-api` |
| `anthropic-agentic` | `arbiter-agentic` |
| `openai-agentic` | `arbiter-agentic` |
| `grok-agentic` | `arbiter-agentic` |
| `claude-code-cli` | `arbiter-cli` |
| `codex-cli` | `arbiter-cli` |

## Context Isolation

| Layer | Mechanism |
|-------|-----------|
| Tacticl threads | Each PDLC role creates fresh `AiEngineRequest` — no shared state |
| gRPC transport | Each request independent, no session affinity |
| Arbiter CLI spawn | Each request → new `claude` process → new PID, new context |
| Filesystem | Each request → `mkdtemp()` → `git clone` → isolated directory |
| Cleanup | Process exits + `rm -rf` temp dir after response sent |

## Proto Extension

```protobuf
message WorkspaceConfig {
  string repo_url = 1;
  string git_ref = 2;
  string workspace_id = 3;
}

message GenerateRequest {
  // ...existing fields 1-12...
  WorkspaceConfig workspace = 13;
}
```

## Configuration

### tacticl-core application.properties
```properties
# Arbiter gRPC client
arbiter.client.enabled=true
arbiter.client.host=cidadel-ai-arbiter-iboj74jsea-ue.a.run.app
arbiter.client.port=443
arbiter.client.use-tls=true
```

### Removed properties
```properties
# These are removed — arbiter handles credentials centrally
anthropic.direct.enabled=true
anthropic.direct.oauth-vault-context=strategiz
openai.direct.enabled=true
grok.direct.enabled=true
```

## Deployment Order

1. Deploy arbiter with `claude-code-cli` engine to QA
2. Publish cidadel-core with updated proto + client
3. Deploy tacticl-core with arbiter integration to QA
4. E2E: trigger PDLC pipeline, verify isolation across roles
5. Promote all to production
