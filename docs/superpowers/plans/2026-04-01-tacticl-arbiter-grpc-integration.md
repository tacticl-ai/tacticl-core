# Tacticl ↔ Arbiter gRPC Integration — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route all tacticl-core LLM traffic through the cidadel-ai-arbiter gRPC service, adding a Claude Code CLI engine for agentic code execution with per-request workspace isolation.

**Architecture:** Three-repo change. Arbiter gets a new `claude-code-cli` provider that spawns CLI subprocesses per request. cidadel-core's Java gRPC client gets workspace support. tacticl-core replaces all direct LLM clients with arbiter-backed engines.

**Tech Stack:** Node.js/TypeScript (arbiter), Java 25/Spring Boot 4/Gradle (cidadel-core, tacticl-core), gRPC/Protobuf, Claude Code CLI

**Spec:** `docs/superpowers/specs/2026-04-01-tacticl-arbiter-grpc-integration-design.md`

**Version note:** tacticl-core bumps cidadel from `0.4.19` → `0.6.3` (14 minor versions). This is expected — the `0.5.x` series added CLI engines, `0.6.x` added `client-ai-arbiter`. No breaking API changes in the modules tacticl consumes (framework-*, business-ai-engine). The new `client-ai-arbiter` module is additive.

**Fallback strategy:** Since all traffic routes through the arbiter, provider-level fallback (Anthropic → OpenAI) happens *inside* the arbiter, not in tacticl. Step default fallback lists are therefore minimal — they only list alternate tacticl-side engine types (e.g., `arbiter-agentic` as fallback for `arbiter-cli`). If the arbiter itself is down, calls fail — arbiter has Cloud Run auto-scaling and health checks.

---

## Chunk 1: Proto & Types Extension (cidadel-ai-arbiter)

### Task 1: Add WorkspaceConfig to proto

**Repo:** `cidadel-ai-arbiter`

**Files:**
- Modify: `packages/server/proto/arbiter/v1/arbiter.proto`

- [ ] **Step 1: Add WorkspaceConfig message and field to proto**

Add after `ToolUseBlock` message (line 68):

```protobuf
message WorkspaceConfig {
  string repo_url = 1;
  string git_ref = 2;
  string workspace_id = 3;
}
```

Add field to `GenerateRequest` after `permission_mode` (field 12):

```protobuf
  WorkspaceConfig workspace = 13;
```

- [ ] **Step 2: Verify proto compiles**

Run: `cd packages/server && npx proto-loader-gen-types --keepCase --longs=String --enums=String --defaults --oneofs --grpcLib=@grpc/grpc-js --outDir=src/generated proto/arbiter/v1/arbiter.proto`

If project doesn't use generated types (it uses dynamic loading), verify with: `npm run build`

Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add packages/server/proto/arbiter/v1/arbiter.proto
git commit -m "feat(proto): add WorkspaceConfig to GenerateRequest for CLI workspace isolation"
```

### Task 2: Add WorkspaceConfig to core types

**Repo:** `cidadel-ai-arbiter`

**Files:**
- Modify: `packages/core/src/types.ts`

- [ ] **Step 1: Add WorkspaceConfig interface**

Add after `EngineInfo` interface (line 75):

```typescript
export interface WorkspaceConfig {
  repoUrl: string;
  gitRef?: string;
  workspaceId?: string;
}
```

- [ ] **Step 2: Add workspace field to GenerateRequest**

Add to `GenerateRequest` interface after `permissionMode`:

```typescript
  workspace?: WorkspaceConfig;
```

- [ ] **Step 3: Commit**

```bash
git add packages/core/src/types.ts
git commit -m "feat(types): add WorkspaceConfig interface to GenerateRequest"
```

### Task 3: Map workspace in gRPC service handler

**Repo:** `cidadel-ai-arbiter`

**Files:**
- Modify: `packages/server/src/grpc-service.ts`

- [ ] **Step 1: Add workspace to ProtoGenerateRequest interface**

Add to `ProtoGenerateRequest` after `permission_mode`:

```typescript
  workspace: { repo_url: string; git_ref: string; workspace_id: string } | null;
```

- [ ] **Step 2: Map workspace in mapProtoToCore function**

Add after the `permissionMode` mapping (around line 106):

```typescript
  if (proto.workspace && proto.workspace.repo_url) {
    req.workspace = {
      repoUrl: proto.workspace.repo_url,
      gitRef: proto.workspace.git_ref || undefined,
      workspaceId: proto.workspace.workspace_id || undefined,
    };
  }
```

- [ ] **Step 3: Run tests**

Run: `cd packages/server && npm test`
Expected: All existing tests pass (new field is optional, no breaking changes)

- [ ] **Step 4: Commit**

```bash
git add packages/server/src/grpc-service.ts
git commit -m "feat(grpc): map WorkspaceConfig from proto to core request"
```

---

## Chunk 2: Claude Code CLI Provider (cidadel-ai-arbiter)

### Task 4: Create ClaudeCodeCliProvider

**Repo:** `cidadel-ai-arbiter`

**Files:**
- Create: `packages/core/src/providers/claude-code-cli.ts`

- [ ] **Step 1: Write the provider test**

Create: `packages/core/src/__tests__/providers/claude-code-cli.test.ts`

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ClaudeCodeCliProvider } from '../../providers/claude-code-cli.js';

// Mock child_process.spawn
vi.mock('child_process', () => ({
  spawn: vi.fn(),
}));

// Mock fs/promises
vi.mock('fs/promises', () => ({
  mkdtemp: vi.fn(),
  rm: vi.fn(),
}));

describe('ClaudeCodeCliProvider', () => {
  it('has correct name and supported models', () => {
    const provider = new ClaudeCodeCliProvider({ oauthToken: 'test-token' });
    expect(provider.name).toBe('claude-code-cli');
    expect(provider.supportedModels).toContain('claude-opus-4-6');
    expect(provider.supportedModels).toContain('claude-sonnet-4-6');
  });

  it('isAvailable returns true when oauthToken is set', () => {
    const provider = new ClaudeCodeCliProvider({ oauthToken: 'test-token' });
    expect(provider.isAvailable()).toBe(true);
  });

  it('isAvailable returns false when no credentials', () => {
    const provider = new ClaudeCodeCliProvider({});
    expect(provider.isAvailable()).toBe(false);
  });

  it('buildArgs includes required CLI flags', () => {
    const provider = new ClaudeCodeCliProvider({ oauthToken: 'test' });
    // Access private method via any cast for testing
    const args = (provider as any).buildArgs(
      {
        engine: 'claude-code-cli',
        model: 'claude-opus-4-6',
        prompt: 'Write hello world',
        systemPrompt: 'You are a coder',
        maxTurns: 10,
        permissionMode: 'acceptEdits',
      },
      '/tmp/test-dir',
    );

    expect(args).toContain('--print');
    expect(args).toContain('--output-format');
    expect(args).toContain('stream-json');
    expect(args).toContain('--model');
    expect(args).toContain('claude-opus-4-6');
    expect(args).toContain('--max-turns');
    expect(args).toContain('10');
    expect(args).toContain('--system-prompt');
    expect(args).toContain('You are a coder');
    expect(args).toContain('--permission-mode');
    expect(args).toContain('acceptEdits');
    // Prompt is last positional arg
    expect(args[args.length - 1]).toBe('Write hello world');
  });

  it('buildArgs uses --max-cost for budget', () => {
    const provider = new ClaudeCodeCliProvider({ oauthToken: 'test' });
    const args = (provider as any).buildArgs(
      {
        engine: 'claude-code-cli',
        model: 'claude-opus-4-6',
        prompt: 'Test',
        maxBudgetUsd: 5.0,
      },
      '/tmp/test-dir',
    );

    expect(args).toContain('--max-cost');
    expect(args).toContain('5');
    expect(args).not.toContain('--max-budget-usd');
  });

  it('parseOutput extracts result from stream-json lines', () => {
    const provider = new ClaudeCodeCliProvider({ oauthToken: 'test' });
    const lines = [
      '{"type":"system","subtype":"init","model":"claude-opus-4-6"}',
      '{"type":"assistant","message":{"content":[{"type":"text","text":"Hello"}]}}',
      '{"type":"result","subtype":"success","result":"Done","total_cost_usd":0.05,"usage":{"input_tokens":100,"output_tokens":50}}',
    ];

    const response = (provider as any).parseOutput(lines, {
      engine: 'claude-code-cli',
      model: 'claude-opus-4-6',
      prompt: 'test',
    });

    expect(response.content).toBe('Done');
    expect(response.engine).toBe('claude-code-cli');
    expect(response.stopReason).toBe('end_turn');
    expect(response.costUsd).toBe(0.05);
    expect(response.usage.promptTokens).toBe(100);
    expect(response.usage.completionTokens).toBe(50);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/core && npx vitest run src/__tests__/providers/claude-code-cli.test.ts`
Expected: FAIL — module not found

- [ ] **Step 3: Implement ClaudeCodeCliProvider**

Create `packages/core/src/providers/claude-code-cli.ts`:

```typescript
import { spawn } from 'child_process';
import { mkdtemp, rm } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { createInterface } from 'readline';
import type { Provider } from './provider.js';
import type {
  GenerateRequest,
  GenerateResponse,
  GenerateEvent,
} from '../types.js';

const SUPPORTED_MODELS = [
  'claude-opus-4-6',
  'claude-sonnet-4-6',
  'claude-haiku-4-5',
  'claude-opus-4-5',
  'claude-sonnet-4-5',
  'claude-3-5-sonnet',
  'claude-3-5-haiku',
];

export interface ClaudeCodeCliConfig {
  cliPath?: string;
  oauthToken?: string;
  apiKey?: string;
  defaultMaxTurns?: number;
  defaultPermissionMode?: string;
  timeoutMs?: number;
}

export class ClaudeCodeCliProvider implements Provider {
  readonly name = 'claude-code-cli';
  readonly supportedModels = SUPPORTED_MODELS;
  private readonly cliPath: string;
  private readonly oauthToken: string;
  private readonly apiKey: string;
  private readonly defaultMaxTurns: number;
  private readonly defaultPermissionMode: string;
  private readonly timeoutMs: number;

  constructor(config: ClaudeCodeCliConfig = {}) {
    this.cliPath = config.cliPath ?? 'claude';
    this.oauthToken = config.oauthToken ?? '';
    this.apiKey = config.apiKey ?? '';
    this.defaultMaxTurns = config.defaultMaxTurns ?? 25;
    this.defaultPermissionMode = config.defaultPermissionMode ?? 'bypassPermissions';
    this.timeoutMs = config.timeoutMs ?? 600_000;
  }

  async generate(req: GenerateRequest): Promise<GenerateResponse> {
    const workDir = await this.provisionWorkspace(req);
    try {
      const args = this.buildArgs(req, workDir);
      const env = this.buildEnv();
      const lines = await this.runCli(args, env, workDir);
      return this.parseOutput(lines, req);
    } finally {
      await rm(workDir, { recursive: true, force: true }).catch(() => {});
    }
  }

  async *generateStream(req: GenerateRequest): AsyncGenerator<GenerateEvent> {
    const workDir = await this.provisionWorkspace(req);
    try {
      const args = this.buildArgs(req, workDir);
      const env = this.buildEnv();

      const proc = spawn(this.cliPath, args, {
        env: { ...process.env, ...env },
        cwd: workDir,
        stdio: ['pipe', 'pipe', 'pipe'],
      });
      proc.stdin?.end();

      const rl = createInterface({ input: proc.stdout! });

      yield {
        type: 'started',
        engine: this.name,
        model: req.model,
        requestId: req.requestId,
      };

      for await (const line of rl) {
        const event = this.parseStreamLine(line, req);
        if (event) yield event;
      }

      await new Promise<void>((resolve, reject) => {
        proc.on('close', (code) => {
          if (code !== 0) reject(new Error(`CLI exited with code ${code}`));
          else resolve();
        });
      });
    } finally {
      await rm(workDir, { recursive: true, force: true }).catch(() => {});
    }
  }

  isAvailable(): boolean {
    return !!this.oauthToken || !!this.apiKey || !!process.env['CLAUDE_CODE_OAUTH_TOKEN'];
  }

  private async provisionWorkspace(req: GenerateRequest): Promise<string> {
    const dir = await mkdtemp(join(tmpdir(), 'arbiter-cli-'));

    if (req.workspace?.repoUrl) {
      const ref = req.workspace.gitRef ?? 'main';
      await new Promise<void>((resolve, reject) => {
        const proc = spawn(
          'git',
          ['clone', '--branch', ref, '--depth', '1', '--single-branch', req.workspace!.repoUrl, '.'],
          { cwd: dir, stdio: 'pipe' },
        );
        proc.on('close', (code) => {
          if (code !== 0)
            reject(new Error(`git clone failed with code ${code}`));
          else resolve();
        });
        proc.on('error', reject);
      });
    }

    return dir;
  }

  private buildArgs(req: GenerateRequest, cwd: string): string[] {
    const args = [
      '--print',
      '--output-format',
      'stream-json',
      '--model',
      req.model,
      '--max-turns',
      String(req.maxTurns ?? this.defaultMaxTurns),
      '--permission-mode',
      req.permissionMode ?? this.defaultPermissionMode,
    ];

    if (req.systemPrompt) {
      args.push('--system-prompt', req.systemPrompt);
    }
    if (req.maxBudgetUsd && req.maxBudgetUsd > 0) {
      args.push('--max-cost', String(req.maxBudgetUsd));
    }

    // Prompt is positional, always last
    args.push(req.prompt);
    return args;
  }

  private buildEnv(): Record<string, string> {
    const env: Record<string, string> = {};
    if (this.oauthToken) {
      env['CLAUDE_CODE_OAUTH_TOKEN'] = this.oauthToken;
    }
    if (this.apiKey) {
      env['ANTHROPIC_API_KEY'] = this.apiKey;
    }
    return env;
  }

  private runCli(
    args: string[],
    env: Record<string, string>,
    cwd: string,
  ): Promise<string[]> {
    return new Promise((resolve, reject) => {
      const proc = spawn(this.cliPath, args, {
        env: { ...process.env, ...env },
        cwd,
        stdio: ['pipe', 'pipe', 'pipe'],
      });
      proc.stdin?.end();

      const lines: string[] = [];
      const rl = createInterface({ input: proc.stdout! });
      rl.on('line', (line) => lines.push(line));

      const timeout = setTimeout(() => {
        proc.kill('SIGTERM');
        reject(new Error(`CLI timed out after ${this.timeoutMs}ms`));
      }, this.timeoutMs);

      proc.on('close', (code) => {
        clearTimeout(timeout);
        if (code !== 0)
          reject(new Error(`CLI exited with code ${code}`));
        else resolve(lines);
      });

      proc.on('error', (err) => {
        clearTimeout(timeout);
        reject(err);
      });
    });
  }

  private parseOutput(
    lines: string[],
    req: GenerateRequest,
  ): GenerateResponse {
    let content = '';
    let costUsd: number | undefined;
    let inputTokens = 0;
    let outputTokens = 0;
    let stopReason: string | undefined;

    for (const line of lines) {
      try {
        const event = JSON.parse(line);
        if (event.type === 'result') {
          costUsd = event.total_cost_usd;
          inputTokens = event.usage?.input_tokens ?? 0;
          outputTokens = event.usage?.output_tokens ?? 0;
          if (event.subtype === 'success') {
            content = event.result;
            stopReason = 'end_turn';
          } else {
            stopReason = event.subtype;
          }
        }
      } catch {
        // Skip non-JSON lines
      }
    }

    return {
      content,
      model: req.model,
      engine: this.name,
      stopReason,
      costUsd,
      usage: {
        promptTokens: inputTokens,
        completionTokens: outputTokens,
        totalTokens: inputTokens + outputTokens,
      },
      requestId: req.requestId,
    };
  }

  private parseStreamLine(
    line: string,
    req: GenerateRequest,
  ): GenerateEvent | null {
    try {
      const event = JSON.parse(line);
      switch (event.type) {
        case 'system':
          return {
            type: 'started',
            engine: this.name,
            model: event.model,
            requestId: req.requestId,
          };
        case 'assistant':
          if (event.message?.content) {
            for (const block of event.message.content) {
              if (block.type === 'text') {
                return {
                  type: 'token',
                  content: block.text,
                  requestId: req.requestId,
                };
              }
              if (block.type === 'tool_use') {
                return {
                  type: 'tool_use',
                  toolName: block.name,
                  toolInput: JSON.stringify(block.input),
                  requestId: req.requestId,
                };
              }
            }
          }
          return null;
        case 'result':
          return {
            type: 'done',
            engine: this.name,
            model: req.model,
            costUsd: event.total_cost_usd,
            usage: {
              promptTokens: event.usage?.input_tokens ?? 0,
              completionTokens: event.usage?.output_tokens ?? 0,
              totalTokens:
                (event.usage?.input_tokens ?? 0) +
                (event.usage?.output_tokens ?? 0),
            },
            requestId: req.requestId,
          };
        default:
          return null;
      }
    } catch {
      return null;
    }
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd packages/core && npx vitest run src/__tests__/providers/claude-code-cli.test.ts`
Expected: PASS

- [ ] **Step 5: Export from index**

Modify `packages/core/src/index.ts` — add:

```typescript
export { ClaudeCodeCliProvider } from './providers/claude-code-cli.js';
export type { ClaudeCodeCliConfig } from './providers/claude-code-cli.js';
```

- [ ] **Step 6: Commit**

```bash
git add packages/core/src/providers/claude-code-cli.ts packages/core/src/__tests__/providers/claude-code-cli.test.ts packages/core/src/index.ts
git commit -m "feat(provider): add ClaudeCodeCliProvider — spawns CLI subprocess with workspace isolation"
```

### Task 5: Register ClaudeCodeCliProvider in server

**Repo:** `cidadel-ai-arbiter`

**Files:**
- Modify: `packages/server/src/server.ts`

- [ ] **Step 1: Import ClaudeCodeCliProvider**

Add to imports (line 14):

```typescript
import {
  VaultClient,
  OAuthTokenManager,
  AnthropicProvider,
  OpenAiProvider,
  GrokProvider,
  GeminiProvider,
  ClaudeCodeProvider,
  ClaudeCodeCliProvider,
  CodexProvider,
  Router,
} from '@cidadel/ai-arbiter';
```

- [ ] **Step 2: Register provider after Claude Code SDK block**

Add after the Codex provider block (after line 125):

```typescript
  // Claude Code CLI (uses Anthropic OAuth token — spawns CLI subprocess)
  try {
    const anthropicSecrets = await vault.readSecret(`secret/${vaultContext}/anthropic`);
    const oauthToken = anthropicSecrets['oauth-access-token'];
    if (oauthToken) {
      providers.push(new ClaudeCodeCliProvider({ oauthToken }));
      console.log('Provider registered: claude-code-cli');
    }
  } catch (err) {
    console.warn('Skipping claude-code-cli provider:', err instanceof Error ? err.message : err);
  }
```

- [ ] **Step 3: Run full test suite**

Run: `npm test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add packages/server/src/server.ts
git commit -m "feat(server): register ClaudeCodeCliProvider in gRPC server"
```

### Task 6: Ensure Dockerfile has Claude Code CLI

**Repo:** `cidadel-ai-arbiter`

**Files:**
- Modify: `packages/server/Dockerfile`

- [ ] **Step 1: Read current Dockerfile**

Check if `claude` CLI is already installed. If not, add npm global install:

```dockerfile
# Install Claude Code CLI globally
RUN npm install -g @anthropic-ai/claude-code
```

Place after `npm ci --omit=dev` or equivalent dependency installation step.

- [ ] **Step 2: Verify build**

Run: `docker build -t arbiter-test -f packages/server/Dockerfile .`
Expected: Build succeeds, `claude --version` works in container

- [ ] **Step 3: Commit**

```bash
git add packages/server/Dockerfile
git commit -m "feat(docker): install Claude Code CLI in arbiter container"
```

### Task 7: Deploy arbiter to QA

**Repo:** `cidadel-ai-arbiter`

- [ ] **Step 1: Push branch and deploy**

```bash
git push origin main
gcloud builds submit --config deployment/cloudbuild-qa.yaml .
```

- [ ] **Step 2: Verify health check**

```bash
grpcurl cidadel-ai-arbiter-qa-iboj74jsea-ue.a.run.app:443 grpc.health.v1.Health/Check
```

Expected: `{ "status": "SERVING" }`

- [ ] **Step 3: Verify new engine is listed**

```bash
grpcurl cidadel-ai-arbiter-qa-iboj74jsea-ue.a.run.app:443 cidadel.ai.arbiter.v1.ArbiterService/ListEngines
```

Expected: Response includes engine with `id: "claude-code-cli"`

---

## Chunk 3: cidadel-core Client Update

### Task 8: Sync proto and regenerate stubs

**Repo:** `cidadel-core`

**Files:**
- Modify: `client/client-ai-arbiter/src/main/proto/arbiter/v1/arbiter.proto`

- [ ] **Step 1: Copy updated proto from arbiter repo**

Copy `WorkspaceConfig` message and `workspace` field from the arbiter repo's proto. The file should match `cidadel-ai-arbiter/packages/server/proto/arbiter/v1/arbiter.proto` exactly.

- [ ] **Step 2: Verify proto codegen**

Run: `./gradlew :client:client-ai-arbiter:build`
Expected: Build succeeds, generated Java classes include `WorkspaceConfig` and updated `GenerateRequest` with `getWorkspace()` / `setWorkspace()`.

- [ ] **Step 3: Commit**

```bash
git add client/client-ai-arbiter/src/main/proto/arbiter/v1/arbiter.proto
git commit -m "feat(proto): sync arbiter.proto — add WorkspaceConfig to GenerateRequest"
```

### Task 9: Update ArbiterClient to accept WorkspaceConfig

**Repo:** `cidadel-core`

**Files:**
- Modify: `client/client-ai-arbiter/src/main/java/io/cidadel/client/arbiter/ArbiterClient.java`

- [ ] **Step 1: Add overloaded generate method with workspace**

Add after the existing `generate` method with full params (line 111):

```java
/**
 * Generate a response with full parameter control including workspace config.
 * @param workspace optional workspace config for CLI engines (may be null)
 */
public GenerateResponse generate(String engine, String model, String prompt, String systemPrompt,
        List<Message> history, List<ToolDefinition> tools, int maxTokens, double temperature,
        int maxTurns, double maxBudgetUsd, WorkspaceConfig workspace, String requestId) {

    GenerateRequest.Builder builder = GenerateRequest.newBuilder()
        .setEngine(engine)
        .setModel(model)
        .setPrompt(prompt)
        .setMaxTokens(maxTokens)
        .setTemperature(temperature)
        .setMaxTurns(maxTurns)
        .setMaxBudgetUsd(maxBudgetUsd);

    if (requestId != null) {
        builder.setRequestId(requestId);
    }

    if (systemPrompt != null) {
        builder.setSystemPrompt(systemPrompt);
    }
    if (history != null && !history.isEmpty()) {
        builder.addAllHistory(history);
    }
    if (tools != null && !tools.isEmpty()) {
        builder.addAllTools(tools);
    }
    if (workspace != null) {
        builder.setWorkspace(workspace);
    }

    try {
        return getStub().generate(builder.build());
    }
    catch (StatusRuntimeException e) {
        logger.error("Arbiter Generate RPC failed: status={}, description={}", e.getStatus().getCode(),
                e.getStatus().getDescription(), e);
        throw e;
    }
}
```

Add import for `WorkspaceConfig`:

```java
import cidadel.ai.arbiter.v1.WorkspaceConfig;
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :client:client-ai-arbiter:test`
Expected: All existing tests pass (new overload is additive)

- [ ] **Step 3: Commit**

```bash
git add client/client-ai-arbiter/src/main/java/io/cidadel/client/arbiter/ArbiterClient.java
git commit -m "feat(client): add workspace-aware generate overload to ArbiterClient"
```

### Task 10: Bump version and publish

**Repo:** `cidadel-core`

- [ ] **Step 1: Bump version from 0.6.2 to 0.6.3**

In `build.gradle.kts` line 8, change `version = "0.6.2"` to `version = "0.6.3"`.

- [ ] **Step 2: Build and test all**

Run: `./gradlew build`
Expected: Full build + tests pass

- [ ] **Step 3: Publish**

Push to trigger GitHub Actions publish, or:

```bash
./gradlew publish
```

- [ ] **Step 4: Commit and push**

```bash
git add build.gradle.kts
git commit -m "chore: bump to 0.6.3 — ArbiterClient workspace support"
git push origin main
```

---

## Chunk 4: tacticl-core Integration — Dependencies & Config

### Task 11: Bump cidadel version and add arbiter dependency

**Repo:** `tacticl-core`

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Update version catalog**

Change cidadel version:

```toml
cidadel = "0.6.3"
```

Add `client-ai-arbiter` library entry (after line 50, in the Cidadel AI engine section):

```toml
cidadel-client-ai-arbiter = { module = "io.cidadel:client-ai-arbiter", version.ref = "cidadel" }
```

Remove direct LLM client entries (lines 41-43):

```toml
# DELETE these lines:
cidadel-client-anthropic-direct = { module = "io.cidadel:client-anthropic-direct", version.ref = "cidadel" }
cidadel-client-openai-direct = { module = "io.cidadel:client-openai-direct", version.ref = "cidadel" }
cidadel-client-grok-direct = { module = "io.cidadel:client-grok-direct", version.ref = "cidadel" }
```

Remove CLI engine entries (lines 49-50):

```toml
# DELETE these lines:
cidadel-client-claude-code = { module = "io.cidadel:client-claude-code", version.ref = "cidadel" }
cidadel-client-codex = { module = "io.cidadel:client-codex", version.ref = "cidadel" }
```

- [ ] **Step 2: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: bump cidadel to 0.6.3, add client-ai-arbiter, remove direct LLM clients"
```

### Task 12: Swap dependencies in business-agent

**Repo:** `tacticl-core`

**Files:**
- Modify: `business/business-agent/build.gradle.kts`

- [ ] **Step 1: Replace LLM client deps with arbiter**

Replace lines 23-31 (the Cidadel LLM clients + AI engine sections):

```kotlin
    // Cidadel LLM clients — REMOVE these 3:
    // implementation(libs.cidadel.client.anthropic.direct)
    // implementation(libs.cidadel.client.openai.direct)
    // implementation(libs.cidadel.client.grok.direct)

    // Cidadel AI engine — REMOVE CLI engines:
    // implementation(libs.cidadel.client.claude.code)
    // implementation(libs.cidadel.client.codex)
```

Replace with:

```kotlin
    // Cidadel AI Arbiter (gRPC client — replaces direct LLM clients + CLI engines)
    implementation(libs.cidadel.client.ai.arbiter)

    // Cidadel AI engine (framework + business layer — kept for AiEngine interfaces)
    implementation(libs.cidadel.business.ai.engine)
```

**Keep these existing deps** (they are NOT removed):
- `libs.cidadel.framework.llm.router` — still used by `CloudOrchestratorService` and `AgenticApiAiEngine`
- `libs.cidadel.service.framework.base`, `libs.cidadel.framework.secrets`, etc. — unchanged infrastructure

- [ ] **Step 2: Verify component scanning picks up arbiter config**

The `ArbiterClient` and `ArbiterLlmProviderConfig` live in package `io.cidadel.client.arbiter`. Verify tacticl-core's component scan includes this package. Check the `@SpringBootApplication` class in `application-api/` — it should scan `io.cidadel` or have `@ComponentScan` that covers it. If not, add `@ComponentScan(basePackages = {"io.strategiz", "io.cidadel"})` to the application class.

- [ ] **Step 3: Verify build compiles (will have errors — expected)**

Run: `./gradlew :business:business-agent:compileJava 2>&1 | head -30`
Expected: Compilation errors in `AiEngineAdapterConfig.java` (references removed clients). This is expected — we fix it in Task 14.

- [ ] **Step 3: Commit**

```bash
git add business/business-agent/build.gradle.kts
git commit -m "build(business-agent): replace direct LLM clients with client-ai-arbiter"
```

### Task 13: Add arbiter config to application properties

**Repo:** `tacticl-core`

**Files:**
- Modify: `application-api/src/main/resources/application.properties`
- Modify: `application-api/src/main/resources/application-qa.properties`
- Modify: `application-api/src/main/resources/application-prod.properties`

- [ ] **Step 1: Update base application.properties**

Remove direct client config (lines 33-46):

```properties
# DELETE these:
anthropic.direct.enabled=true
anthropic.direct.max-tokens=4096
anthropic.direct.temperature=0.3
anthropic.direct.oauth-vault-context=strategiz
openai.direct.enabled=true
openai.direct.max-tokens=4096
openai.direct.temperature=0.3
grok.direct.enabled=true
grok.direct.max-tokens=4096
grok.direct.temperature=0.3
```

Add arbiter config (in the same location):

```properties
# AI Arbiter gRPC client (replaces direct LLM clients)
arbiter.client.enabled=true
arbiter.client.host=localhost
arbiter.client.port=50051
arbiter.client.use-tls=false
```

- [ ] **Step 2: Update application-qa.properties**

Remove (lines 6-13):

```properties
# DELETE these:
anthropic.direct.enabled=true
anthropic.direct.oauth-vault-context=strategiz-qa
openai.direct.enabled=true
grok.direct.enabled=true
```

Add:

```properties
# AI Arbiter gRPC (QA)
arbiter.client.enabled=true
arbiter.client.host=cidadel-ai-arbiter-qa-iboj74jsea-ue.a.run.app
arbiter.client.port=443
arbiter.client.use-tls=true
```

- [ ] **Step 3: Update application-prod.properties**

Remove (lines 6-13):

```properties
# DELETE these:
anthropic.direct.enabled=true
anthropic.direct.oauth-vault-context=strategiz
openai.direct.enabled=true
grok.direct.enabled=true
```

Add:

```properties
# AI Arbiter gRPC (Production)
arbiter.client.enabled=true
arbiter.client.host=cidadel-ai-arbiter-iboj74jsea-ue.a.run.app
arbiter.client.port=443
arbiter.client.use-tls=true
```

- [ ] **Step 4: Commit**

```bash
git add application-api/src/main/resources/application.properties application-api/src/main/resources/application-qa.properties application-api/src/main/resources/application-prod.properties
git commit -m "config: replace direct LLM client config with arbiter gRPC client config"
```

---

## Chunk 5: tacticl-core Integration — Engine Wiring

### Task 14: Create ArbiterCliAiEngine

**Repo:** `tacticl-core`

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/ArbiterCliAiEngine.java`
- Create: `business/business-agent/src/test/java/io/strategiz/social/business/agent/ai/ArbiterCliAiEngineTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.strategiz.social.business.agent.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cidadel.ai.arbiter.v1.GenerateResponse;
import cidadel.ai.arbiter.v1.TokenUsage;
import cidadel.ai.arbiter.v1.WorkspaceConfig;
import io.cidadel.client.arbiter.ArbiterClient;
import io.cidadel.framework.ai.engine.AiEngineCapability;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArbiterCliAiEngineTest {

    @Mock
    private ArbiterClient arbiterClient;

    private ArbiterCliAiEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ArbiterCliAiEngine(arbiterClient, "arbiter-cli", "Arbiter CLI");
    }

    @Test
    void getEngineId_returnsConfiguredId() {
        assertEquals("arbiter-cli", engine.getEngineId());
    }

    @Test
    void getCapabilities_includesAgenticExecution() {
        Set<AiEngineCapability> caps = engine.getCapabilities();
        assertTrue(caps.contains(AiEngineCapability.AGENTIC_EXECUTION));
        assertTrue(caps.contains(AiEngineCapability.TEXT_GENERATION));
    }

    @Test
    void execute_sendsRequestToArbiterAndMapsResponse() {
        GenerateResponse grpcResponse = GenerateResponse.newBuilder()
                .setContent("Implementation complete")
                .setModel("claude-opus-4-6")
                .setEngine("claude-code-cli")
                .setStopReason("end_turn")
                .setCostUsd(0.15)
                .setUsage(TokenUsage.newBuilder()
                        .setPromptTokens(500)
                        .setCompletionTokens(200)
                        .setTotalTokens(700)
                        .build())
                .build();

        when(arbiterClient.generate(
                eq("claude-code-cli"), eq("claude-opus-4-6"),
                eq("Implement auth"), eq("You are IMPLEMENTER"),
                anyList(), anyList(), anyInt(), anyDouble(),
                eq(25), anyDouble(), any()))
                .thenReturn(grpcResponse);

        AiEngineRequest request = new AiEngineRequest();
        request.setPrompt("Implement auth");
        request.setSystemPrompt("You are IMPLEMENTER");
        request.setModel("claude-opus-4-6");
        request.setMaxTurns(25);
        request.setMetadata(Map.of(
                "workspaceRepoUrl", "https://github.com/org/repo.git",
                "workspaceGitRef", "feature-branch"));

        AiEngineResult result = engine.execute(request);

        assertTrue(result.isSuccess());
        assertEquals("Implementation complete", result.getContent());
        assertEquals("arbiter-cli", result.getEngineId());
        assertEquals(500, result.getPromptTokens());
        assertEquals(200, result.getCompletionTokens());
    }

    @Test
    void execute_extractsWorkspaceFromMetadata() {
        GenerateResponse grpcResponse = GenerateResponse.newBuilder()
                .setContent("Done")
                .setModel("claude-opus-4-6")
                .setEngine("claude-code-cli")
                .setStopReason("end_turn")
                .build();

        when(arbiterClient.generate(
                anyString(), anyString(), anyString(), any(),
                anyList(), anyList(), anyInt(), anyDouble(),
                anyInt(), anyDouble(), argThat(ws ->
                        ws != null
                                && ws.getRepoUrl().equals("https://github.com/org/repo.git")
                                && ws.getGitRef().equals("main"))))
                .thenReturn(grpcResponse);

        AiEngineRequest request = new AiEngineRequest();
        request.setPrompt("Fix bug");
        request.setModel("claude-opus-4-6");
        request.setMetadata(Map.of(
                "workspaceRepoUrl", "https://github.com/org/repo.git",
                "workspaceGitRef", "main"));

        AiEngineResult result = engine.execute(request);
        assertTrue(result.isSuccess());
    }

    @Test
    void execute_handlesArbiterError() {
        when(arbiterClient.generate(
                anyString(), anyString(), anyString(), any(),
                anyList(), anyList(), anyInt(), anyDouble(),
                anyInt(), anyDouble(), any()))
                .thenThrow(new RuntimeException("gRPC unavailable"));

        AiEngineRequest request = new AiEngineRequest();
        request.setPrompt("Test");
        request.setModel("claude-opus-4-6");

        AiEngineResult result = engine.execute(request);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("gRPC unavailable"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :business:business-agent:test --tests "*.ArbiterCliAiEngineTest" 2>&1 | tail -5`
Expected: FAIL — class not found

- [ ] **Step 3: Implement ArbiterCliAiEngine**

```java
package io.strategiz.social.business.agent.ai;

import cidadel.ai.arbiter.v1.GenerateResponse;
import cidadel.ai.arbiter.v1.TokenUsage;
import cidadel.ai.arbiter.v1.WorkspaceConfig;
import io.cidadel.client.arbiter.ArbiterClient;
import io.cidadel.framework.ai.engine.AiEngine;
import io.cidadel.framework.ai.engine.AiEngineCapability;
import io.cidadel.framework.ai.engine.AiEngineCostTier;
import io.cidadel.framework.ai.engine.model.AiEngineEvent;
import io.cidadel.framework.ai.engine.model.AiEngineEventType;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI engine that delegates agentic code execution to the Arbiter's Claude Code CLI
 * engine. Each request is executed as a separate CLI subprocess on the arbiter,
 * with workspace isolation via per-request temp directories.
 *
 * <p>Workspace configuration (repo URL, git ref) is extracted from the request's
 * metadata map and sent to the arbiter as a {@link WorkspaceConfig} proto message.</p>
 */
public class ArbiterCliAiEngine implements AiEngine {

    private static final Logger logger = LoggerFactory.getLogger(ArbiterCliAiEngine.class);

    private static final Set<AiEngineCapability> CAPABILITIES = Set.of(
            AiEngineCapability.TEXT_GENERATION,
            AiEngineCapability.TOOL_USE,
            AiEngineCapability.AGENTIC_EXECUTION);

    private static final String ARBITER_ENGINE = "claude-code-cli";

    private final ArbiterClient arbiterClient;

    private final String engineId;

    private final String displayName;

    public ArbiterCliAiEngine(ArbiterClient arbiterClient, String engineId, String displayName) {
        this.arbiterClient = arbiterClient;
        this.engineId = engineId;
        this.displayName = displayName;
    }

    @Override
    public AiEngineResult execute(AiEngineRequest request) {
        Instant start = Instant.now();
        Consumer<AiEngineEvent> listener = request.getEventListener();
        List<AiEngineEvent> events = new ArrayList<>();

        fireEvent(listener, events, new AiEngineEvent(AiEngineEventType.STARTED,
                "Executing via " + displayName));

        try {
            String model = request.getModel() != null ? request.getModel() : "claude-opus-4-6";
            int maxTurns = request.getMaxTurns() != null ? request.getMaxTurns() : 25;
            WorkspaceConfig workspace = buildWorkspaceConfig(request);

            GenerateResponse response = arbiterClient.generate(
                    ARBITER_ENGINE, model, request.getPrompt(), request.getSystemPrompt(),
                    List.of(), List.of(), 8192, 0.7, maxTurns, 0.0, workspace);

            Duration executionTime = Duration.between(start, Instant.now());
            fireEvent(listener, events, new AiEngineEvent(AiEngineEventType.COMPLETED,
                    "Completed successfully"));

            AiEngineResult result = AiEngineResult.success(response.getContent(), engineId, model);
            result.setExecutionTime(executionTime);
            result.setEvents(events);

            TokenUsage usage = response.getUsage();
            if (usage != null) {
                result.setPromptTokens(usage.getPromptTokens());
                result.setCompletionTokens(usage.getCompletionTokens());
                result.setTotalTokens(usage.getTotalTokens());
            }

            if (response.getCostUsd() > 0) {
                result.setCostUsd(java.math.BigDecimal.valueOf(response.getCostUsd()));
            }

            return result;
        }
        catch (Exception ex) {
            Duration executionTime = Duration.between(start, Instant.now());
            logger.error("Arbiter CLI execution failed: {}", ex.getMessage(), ex);
            fireEvent(listener, events, new AiEngineEvent(AiEngineEventType.FAILED, ex.getMessage()));

            AiEngineResult errorResult = AiEngineResult.error(ex.getMessage(), engineId);
            errorResult.setExecutionTime(executionTime);
            errorResult.setEvents(events);
            return errorResult;
        }
    }

    @Override
    public String getEngineId() {
        return engineId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Set<AiEngineCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public AiEngineCostTier getCostTier() {
        return AiEngineCostTier.HIGH;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private WorkspaceConfig buildWorkspaceConfig(AiEngineRequest request) {
        if (request.getMetadata() == null) {
            return null;
        }

        Object repoUrl = request.getMetadata().get("workspaceRepoUrl");
        if (repoUrl == null) {
            return null;
        }

        WorkspaceConfig.Builder builder = WorkspaceConfig.newBuilder()
                .setRepoUrl(repoUrl.toString());

        Object gitRef = request.getMetadata().get("workspaceGitRef");
        if (gitRef != null) {
            builder.setGitRef(gitRef.toString());
        }

        Object workspaceId = request.getMetadata().get("workspaceId");
        if (workspaceId != null) {
            builder.setWorkspaceId(workspaceId.toString());
        }

        return builder.build();
    }

    private void fireEvent(Consumer<AiEngineEvent> listener, List<AiEngineEvent> events,
            AiEngineEvent event) {
        events.add(event);
        if (listener != null) {
            try {
                listener.accept(event);
            }
            catch (Exception ex) {
                logger.warn("Event listener threw exception: {}", ex.getMessage());
            }
        }
    }

}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :business:business-agent:test --tests "*.ArbiterCliAiEngineTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/ArbiterCliAiEngine.java business/business-agent/src/test/java/io/strategiz/social/business/agent/ai/ArbiterCliAiEngineTest.java
git commit -m "feat(ai): add ArbiterCliAiEngine — delegates agentic execution to arbiter CLI"
```

### Task 15: Rewrite AiEngineAdapterConfig

**Repo:** `tacticl-core`

**Files:**
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/AiEngineAdapterConfig.java`

- [ ] **Step 1: Rewrite with arbiter-backed engines**

Replace entire file content:

```java
package io.strategiz.social.business.agent.ai;

import io.cidadel.client.arbiter.ArbiterClient;
import io.cidadel.client.arbiter.ArbiterLlmProvider;
import io.cidadel.framework.ai.engine.AiEngine;
import io.cidadel.framework.ai.engine.AiEngineCostTier;
import io.cidadel.framework.ai.engine.ApiAiEngineAdapter;
import io.strategiz.social.business.agent.service.ToolRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers AI engine beans backed by the Arbiter gRPC service.
 *
 * <p>Three engine types:
 * <ul>
 *   <li>{@code arbiter-api} — single-turn API calls (classification, commit messages)</li>
 *   <li>{@code arbiter-agentic} — multi-turn local tool loop, LLM calls via arbiter</li>
 *   <li>{@code arbiter-cli} — full agentic CLI execution via arbiter's Claude Code CLI</li>
 * </ul>
 */
@Configuration
@ConditionalOnBean(ArbiterClient.class)
public class AiEngineAdapterConfig {

    @Bean
    @ConditionalOnBean(ArbiterLlmProvider.class)
    public AiEngine arbiterApiEngine(ArbiterLlmProvider provider) {
        return new ApiAiEngineAdapter(provider, "arbiter-api", "Arbiter API", AiEngineCostTier.MEDIUM);
    }

    @Bean
    @ConditionalOnBean(ArbiterLlmProvider.class)
    public AiEngine arbiterAgenticEngine(ArbiterLlmProvider provider, ToolRegistry toolRegistry) {
        return new AgenticApiAiEngine(provider, toolRegistry, "arbiter-agentic", "Arbiter Agentic",
                AiEngineCostTier.MEDIUM, 5);
    }

    @Bean
    public AiEngine arbiterCliEngine(ArbiterClient arbiterClient) {
        return new ArbiterCliAiEngine(arbiterClient, "arbiter-cli", "Arbiter Claude Code CLI");
    }

}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :business:business-agent:compileJava`
Expected: Compiles successfully

- [ ] **Step 3: Commit**

```bash
git add business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/AiEngineAdapterConfig.java
git commit -m "feat(ai): rewrite AiEngineAdapterConfig — 3 arbiter-backed engines replace 6 direct"
```

### Task 16: Update AiSdlcStepDefaults

**Repo:** `tacticl-core`

**Files:**
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/AiSdlcStepDefaults.java`

- [ ] **Step 1: Remap all engine IDs**

Replace the static block content:

```java
    static {
        // Classification — cheap, fast API calls via arbiter
        DEFAULTS.put(AiSdlcStep.SPARK_CLASSIFICATION.name(),
                new AiStepEngineConfig("arbiter-api", "claude-haiku-4-5", List.of()));
        DEFAULTS.put(AiSdlcStep.TASK_DECOMPOSITION.name(),
                new AiStepEngineConfig("arbiter-api", "claude-sonnet-4-5", List.of()));

        // Code lifecycle — full agentic CLI via arbiter
        DEFAULTS.put(AiSdlcStep.CODE_GENERATION.name(),
                new AiStepEngineConfig("arbiter-cli", "claude-opus-4-6", List.of("arbiter-agentic")));
        DEFAULTS.put(AiSdlcStep.CODE_REVIEW.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of("arbiter-api")));
        DEFAULTS.put(AiSdlcStep.CODE_REFACTORING.name(),
                new AiStepEngineConfig("arbiter-cli", "claude-sonnet-4-5", List.of("arbiter-agentic")));
        DEFAULTS.put(AiSdlcStep.BUG_DIAGNOSIS.name(),
                new AiStepEngineConfig("arbiter-cli", "claude-sonnet-4-5", List.of("arbiter-agentic")));
        DEFAULTS.put(AiSdlcStep.BUG_FIX.name(),
                new AiStepEngineConfig("arbiter-cli", "claude-opus-4-6", List.of("arbiter-agentic")));
        DEFAULTS.put(AiSdlcStep.TEST_GENERATION.name(),
                new AiStepEngineConfig("arbiter-cli", "claude-sonnet-4-5", List.of("arbiter-agentic")));
        DEFAULTS.put(AiSdlcStep.TEST_EXECUTION.name(),
                new AiStepEngineConfig("arbiter-cli", "claude-sonnet-4-5", List.of("arbiter-agentic")));

        // Content — single-turn API via arbiter
        DEFAULTS.put(AiSdlcStep.PR_DESCRIPTION.name(),
                new AiStepEngineConfig("arbiter-api", "claude-sonnet-4-5", List.of()));
        DEFAULTS.put(AiSdlcStep.DOCUMENTATION.name(),
                new AiStepEngineConfig("arbiter-api", "claude-sonnet-4-5", List.of()));
        DEFAULTS.put(AiSdlcStep.COMMIT_MESSAGE.name(),
                new AiStepEngineConfig("arbiter-api", "claude-haiku-4-5", List.of()));

        // Research — agentic with tools via arbiter
        DEFAULTS.put(AiSdlcStep.WEB_RESEARCH.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of("arbiter-api")));
        DEFAULTS.put(AiSdlcStep.CODE_ANALYSIS.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of("arbiter-api")));

        // Social & Creative — agentic with tools via arbiter
        DEFAULTS.put(AiSdlcStep.SOCIAL_CONTENT.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of("arbiter-api")));
        DEFAULTS.put(AiSdlcStep.CREATIVE_WRITING.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of("arbiter-api")));
        DEFAULTS.put(AiSdlcStep.IMAGE_ANALYSIS.name(),
                new AiStepEngineConfig("arbiter-api", "claude-sonnet-4-5", List.of()));

        // DevOps — full agentic CLI via arbiter
        DEFAULTS.put(AiSdlcStep.DEPLOYMENT_SCRIPT.name(),
                new AiStepEngineConfig("arbiter-cli", "claude-sonnet-4-5", List.of("arbiter-agentic")));
        DEFAULTS.put(AiSdlcStep.MONITORING_ANALYSIS.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of("arbiter-api")));

        // PDLC Pipeline Roles — agentic via arbiter
        DEFAULTS.put(AiSdlcStep.REQUIREMENTS_GATHERING.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of()));
        DEFAULTS.put(AiSdlcStep.SYSTEM_DESIGN.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-opus-4-6", List.of()));
        DEFAULTS.put(AiSdlcStep.UI_UX_DESIGN.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of()));
        DEFAULTS.put(AiSdlcStep.SECURITY_REVIEW.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-opus-4-6", List.of()));
        DEFAULTS.put(AiSdlcStep.RETROSPECTIVE.name(),
                new AiStepEngineConfig("arbiter-agentic", "claude-sonnet-4-5", List.of()));
    }
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :business:business-agent:compileJava`
Expected: Compiles successfully

- [ ] **Step 3: Commit**

```bash
git add business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/AiSdlcStepDefaults.java
git commit -m "feat(ai): remap all SDLC step defaults to arbiter-backed engines"
```

### Task 17: Update existing tests for new engine IDs

**Repo:** `tacticl-core`

**Files:**
- Modify: `business/business-agent/src/test/java/io/strategiz/social/business/agent/ai/AiSdlcStepDefaultsTest.java`
- Modify: any other test files referencing old engine IDs

- [ ] **Step 1: Update AiSdlcStepDefaultsTest assertions**

The existing test has assertions against old engine IDs. Apply these replacements:

| Old assertion | New assertion |
|---|---|
| `"anthropic-api"` | `"arbiter-api"` |
| `"anthropic-agentic"` | `"arbiter-agentic"` |
| `"openai-api"` | `"arbiter-api"` |
| `"openai-agentic"` | `"arbiter-agentic"` |
| `"grok-api"` | `"arbiter-api"` |
| `"grok-agentic"` | `"arbiter-agentic"` |
| `"claude-code-cli"` | `"arbiter-cli"` |
| `"codex-cli"` | `"arbiter-cli"` |

Specific tests to update:
- `sparkClassificationUsesAnthropicApiWithHaiku` → assert `"arbiter-api"` engine
- `codeGenerationUsesClaudeCodeCli` → assert `"arbiter-cli"` engine
- `webResearchUsesCodexCli` → assert `"arbiter-agentic"` engine and `"claude-sonnet-4-5"` model
- `agenticStepsUseAgenticEngineIds` → assert `"arbiter-agentic"` engine
- `nonAgenticStepsUseApiEngineIds` → assert `"arbiter-api"` engine

Also update fallback assertions — many will now be `List.of()` (empty) or single-item lists.

- [ ] **Step 2: Find and update references to removed client classes**

Run: `grep -rn "AnthropicDirectClient\|OpenAiDirectClient\|GrokDirectClient\|ClaudeCodeAiEngine\|CodexAiEngine" business/business-agent/src/`

Update imports and references. If any class directly depends on a removed client, refactor to use `ArbiterClient` or `ArbiterLlmProvider`.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew :business:business-agent:test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add -A business/business-agent/src/test/
git commit -m "test: update tests for arbiter engine IDs, remove direct client references"
```

### Task 18: Full build verification

**Repo:** `tacticl-core`

- [ ] **Step 1: Clean build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL — all modules compile and tests pass

- [ ] **Step 2: Commit any remaining fixes**

```bash
git add -A
git commit -m "fix: resolve remaining compilation issues from arbiter migration"
```

---

## Chunk 6: Deploy & Verify

### Task 19: Deploy tacticl-core to QA

**Repo:** `tacticl-core`

- [ ] **Step 1: Push and deploy**

```bash
git push origin main
gcloud builds submit --config deployment/cloudbuild/cloudbuild-qa.yaml .
```

- [ ] **Step 2: Verify startup logs**

Check Cloud Run logs for:
- `Arbiter gRPC client ready: cidadel-ai-arbiter-qa-...`
- `ArbiterLlmProvider initialized`
- No errors about missing Anthropic/OpenAI/Grok clients

### Task 20: E2E verification

- [ ] **Step 1: Test classification (arbiter-api path)**

Send a chat command and verify spark classification works:
```bash
curl -X POST https://tacticl-core-qa-....run.app/v1/agent/command \
  -H "Content-Type: application/json" \
  -d '{"text": "What is the weather?", "sessionId": "test-1"}'
```

Expected: Response returns, spark is classified successfully

- [ ] **Step 2: Test agentic execution (arbiter-agentic path)**

Send a social content command:
```bash
curl -X POST https://tacticl-core-qa-....run.app/v1/agent/command \
  -H "Content-Type: application/json" \
  -d '{"text": "Draft a tweet about AI", "sessionId": "test-2"}'
```

Expected: Agent executes tool loop, returns content

- [ ] **Step 3: Test CLI execution (arbiter-cli path)**

Trigger a code spark that routes to PDLC pipeline:
```bash
curl -X POST https://tacticl-core-qa-....run.app/v1/agent/command \
  -H "Content-Type: application/json" \
  -d '{"text": "Add a health check endpoint to the API", "sessionId": "test-3"}'
```

Expected: PDLC pipeline starts, IMPLEMENTER role executes via arbiter CLI, returns code changes

- [ ] **Step 4: Verify context isolation**

Trigger two concurrent pipelines and verify no cross-contamination in logs or outputs.
