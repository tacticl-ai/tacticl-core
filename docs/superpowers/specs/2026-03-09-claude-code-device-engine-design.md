# Claude Code Device Execution Engine — Architecture Design

**Date:** 2026-03-09
**Updated:** 2026-03-10
**Status:** Draft
**Scope:** Add Claude Code CLI as a new device execution engine option

---

## Context

Tacticl's full SDLC agent pipeline is already built and operational across both execution paths:

- **Cloud Agent** — VoiceAgentService + LlmRouter + 20+ AgentSkills + Playwright browser + multi-LLM support (Anthropic, OpenAI, Grok, Gemini). Runs on Cloud Run, scales for all users.
- **Device Agent** — WebSocket-based spark dispatch, tactic decomposition, 9 command types (TERMINAL_CMD, OPEN_URL, CLICK_ELEMENT, etc.), checkpoint flow, credential requests, daemon concurrency control.

Both paths can handle the full range of tasks. Routing is a user preference (DEVICE_FIRST, CLOUD_FIRST, CLOUD_ONLY), not a task-type decision.

## What's New

Add the **Claude Code CLI** as an additional execution engine on desktop devices. The daemon spawns `claude` as a subprocess in non-interactive mode, streams JSON output, and maps CLI events to the existing WebSocket protocol. This gives devices access to Claude Code's built-in agentic tools — file read/write/edit, bash, glob, grep, web search/fetch, MCP server integration, and subagent orchestration — as a more capable complement to the existing command-based daemon.

**Why CLI over Agent SDK:** The Agent SDK is a TypeScript wrapper that spawns the CLI under the hood. Using the CLI directly eliminates an extra dependency, simplifies the integration (spawn + parse JSON), and gives us the same capabilities. CLI flags map directly to `ClaudeCodeConfig` fields. Hooks are configured via `.claude/settings.json` and call back to a local HTTP endpoint on the daemon.

**Claude Code is the default engine on desktop devices, but everything is user-configurable.**

## What's NOT Changing

| Component | Status |
|-----------|--------|
| Cloud agent (VoiceAgentService, LlmRouter, AgentSkills) | No change |
| Spark lifecycle, state machine, routing logic | No change |
| WebSocket protocol (spark dispatch, progress, checkpoints) | Extended (new message types) |
| Existing device daemon (command-based execution) | Stays as-is, still available |
| Data model (Spark, Tactic, DeviceCommand, etc.) | Minor additions |
| Mobile app REST API | No change |

---

## Architecture

```
                    User Command
                         │
                         ▼
              ┌──────────────────────┐
              │   CLOUD ORCHESTRATOR  │
              │   (tacticl-core)      │
              │                       │
              │   Route by user pref: │
              │   CLOUD / DEVICE      │
              └───┬─────────────┬─────┘
                  │             │
          ┌───────▼───┐  ┌─────▼──────────────────┐
          │   CLOUD    │  │   DEVICE (desktop)      │
          │   AGENT    │  │                          │
          │            │  │   executionEngine config: │
          │ LlmRouter  │  │                          │
          │ AgentSkills │  │   CLAUDE_CODE (default)  │
          │ Playwright  │  │     → spawn `claude` CLI │
          │ All LLMs   │  │     → File/Bash/Web/MCP  │
          │            │  │     → Subagents           │
          │ Full SDLC  │  │                          │
          │            │  │   LEGACY                  │
          │            │  │     → Command protocol    │
          │            │  │     → TERMINAL_CMD, etc.  │
          │            │  │                          │
          │            │  │   AUTO                    │
          │            │  │     → Claude Code for     │
          │            │  │       complex tasks       │
          │            │  │     → Legacy for simple   │
          │            │  │       commands             │
          └────────────┘  └──────────────────────────┘
```

---

## Device Execution Engine Config

### New fields in DeviceSettings (per device)

```java
// Added to existing DeviceSettings entity
String executionEngine = "CLAUDE_CODE";  // CLAUDE_CODE | LEGACY | AUTO
ClaudeCodeConfig claudeCodeConfig;       // Embedded, nullable
```

### ClaudeCodeConfig (new embedded object)

```java
class ClaudeCodeConfig {
    String model = "claude-opus-4-6";           // CLI --model flag
    int maxTurns = 25;                          // CLI --max-turns flag
    BigDecimal maxBudgetUsd = new BigDecimal("5.00"); // CLI --max-cost flag
    List<String> allowedTools;                  // CLI --allowedTools flag (null = all)
    List<String> disallowedTools;               // CLI --disallowedTools flag
    Map<String, Object> mcpServers;             // Written to temp MCP config file → CLI --mcp-config
    String permissionMode = "acceptEdits";      // CLI --permission-mode flag
    String systemPromptOverride;                // CLI --system-prompt flag
}
```

### CLI Flag Mapping

| ClaudeCodeConfig field | CLI flag | Notes |
|----------------------|----------|-------|
| `model` | `--model` | e.g., `claude-opus-4-6` |
| `maxTurns` | `--max-turns` | Max agentic loop iterations |
| `maxBudgetUsd` | `--max-cost` | Cost cap in USD |
| `allowedTools` | `--allowedTools` | Comma-separated tool names |
| `disallowedTools` | `--disallowedTools` | Comma-separated tool names |
| `permissionMode` | `--permission-mode` | `default`, `acceptEdits`, `bypassPermissions` |
| `systemPromptOverride` | `--system-prompt` | Custom system prompt text |
| `mcpServers` | `--mcp-config` | Path to temp MCP config JSON file |

### ExecutionEngine enum

```java
enum ExecutionEngine {
    CLAUDE_CODE,  // CLI subprocess — full agentic execution (default)
    LEGACY,       // Existing command-based daemon protocol
    AUTO          // Choose engine based on spark type/complexity
}
```

### AUTO routing logic

When `executionEngine = AUTO`, the device chooses engine per-spark:

| Spark type | Engine | Reasoning |
|-----------|--------|-----------|
| code | CLAUDE_CODE | Benefits from file/bash/edit tools |
| devops | CLAUDE_CODE | Needs terminal and file access |
| research | CLAUDE_CODE | Benefits from web search + file output |
| creative | CLAUDE_CODE | Content generation with local context |
| social | LEGACY | Simple API calls, existing skills handle it |
| data | CLAUDE_CODE | Needs code execution, file I/O |

User can override per-type via `sparkPreferences` map (existing field on DeviceRegistration).

---

## Device Daemon Changes

### Claude Code CLI Wrapper

The device daemon (TypeScript app running on desktop) gains a new execution path that spawns the `claude` CLI as a subprocess:

```typescript
// Pseudocode for device-side spark handler
async function executeSpark(spark: SparkPayload): Promise<void> {
  const engine = device.settings.executionEngine;

  if (engine === "CLAUDE_CODE" || (engine === "AUTO" && shouldUseClaudeCode(spark))) {
    await executeWithClaudeCode(spark);
  } else {
    await executeWithLegacyDaemon(spark);  // Existing path
  }
}

async function executeWithClaudeCode(spark: SparkPayload): Promise<void> {
  const config = device.settings.claudeCodeConfig;

  // Report spark accepted
  ws.send({ type: "spark_accepted", sparkId: spark.sparkId });

  // Build CLI arguments from config
  const args = [
    "--print", buildSparkPrompt(spark),
    "--output-format", "stream-json",
    "--model", config.model,
    "--max-turns", String(config.maxTurns),
    "--permission-mode", config.permissionMode,
  ];

  if (config.maxBudgetUsd) {
    args.push("--max-cost", String(config.maxBudgetUsd));
  }
  if (config.allowedTools?.length) {
    args.push("--allowedTools", config.allowedTools.join(","));
  }
  if (config.disallowedTools?.length) {
    args.push("--disallowedTools", config.disallowedTools.join(","));
  }
  if (config.systemPromptOverride) {
    args.push("--system-prompt", config.systemPromptOverride);
  }
  if (mcpConfigPath) {
    args.push("--mcp-config", mcpConfigPath);
  }

  // Spawn CLI subprocess
  const proc = spawn("claude", args, {
    cwd: getWorkingDirectory(spark),
    env: { ...process.env, ANTHROPIC_API_KEY: apiKey },
  });

  // Parse streaming JSON lines from stdout
  for await (const line of readlines(proc.stdout)) {
    const msg = JSON.parse(line);

    if (msg.type === "result") {
      // CLI finished — report completion
      ws.send({
        type: "spark_completed",
        sparkId: spark.sparkId,
        result: { response: msg.result },
        totalTokens: msg.total_cost_usd ? estimateTokens(msg.total_cost_usd) : 0,
      });
    } else if (msg.type === "assistant") {
      // Progress update — report tool usage
      ws.send({
        type: "spark_progress",
        sparkId: spark.sparkId,
        status: "EXECUTING",
        tactics: [{
          tacticId: currentTacticId,
          description: summarizeMessage(msg),
          status: "EXECUTING",
        }],
      });
    }
  }

  // Handle unexpected exit
  const exitCode = await waitForExit(proc);
  if (exitCode !== 0) {
    ws.send({
      type: "spark_failed",
      sparkId: spark.sparkId,
      error: `Claude Code exited with code ${exitCode}`,
    });
  }
}
```

### Checkpoint Integration via CLI Hooks

Claude Code CLI supports hooks configured in `.claude/settings.json`. The daemon writes a temporary hook config before spawning the CLI, with hooks that call back to a local HTTP endpoint on the daemon:

```json
{
  "hooks": {
    "PreToolUse": [{
      "matcher": "Bash|Write|Edit",
      "hooks": ["curl -s http://localhost:$DAEMON_PORT/hooks/pre-tool-use -d @-"]
    }],
    "PostToolUse": [{
      "matcher": ".*",
      "hooks": ["curl -s http://localhost:$DAEMON_PORT/hooks/post-tool-use -d @-"]
    }]
  }
}
```

The daemon's local HTTP server processes hook callbacks:

```typescript
// PreToolUse hook handler — checkpoint enforcement
app.post("/hooks/pre-tool-use", async (req, res) => {
  const { tool_name, tool_input } = req.body;

  if (requiresCheckpoint(tool_name)) {
    // Send checkpoint to cloud via WebSocket
    ws.send({
      type: "spark_checkpoint",
      sparkId: currentSparkId,
      title: `Confirm: ${tool_name}`,
      description: describeAction(tool_name, tool_input),
      options: ["Approve", "Reject", "Modify"],
    });

    // Wait for cloud relay of user decision
    const decision = await waitForCheckpointDecision();

    if (decision.decision === "REJECTED") {
      res.json({ decision: "block", message: "User rejected action" });
      return;
    }
    if (decision.decision === "MODIFIED") {
      res.json({ decision: "block", message: decision.feedback });
      return;
    }
  }

  res.json({});  // Approved or no checkpoint needed — proceed
});

// PostToolUse hook handler — progress reporting
app.post("/hooks/post-tool-use", async (req, res) => {
  const { tool_name, tool_input } = req.body;

  ws.send({
    type: "spark_progress",
    sparkId: currentSparkId,
    status: "EXECUTING",
    tactics: [{
      tacticId: currentTacticId,
      description: `${tool_name}: ${summarize(tool_input)}`,
      status: "EXECUTING",
    }],
  });

  res.json({});
});
```

### Credential Flow via MCP

When Claude Code needs platform credentials (e.g., for social posting), the daemon runs a local MCP server that bridges to cloud credentials via WebSocket:

```typescript
// Local MCP server started before spawning CLI
// Exposed via --mcp-config pointing to temp config file
const credentialServer = createMcpServer({
  tools: [{
    name: "get_platform_credentials",
    description: "Get OAuth credentials for a connected platform",
    handler: async (args) => {
      ws.send({
        type: "credentials_request",
        platform: args.platform,
        sparkId: currentSparkId,
        requestId: uuid(),
      });
      const response = await waitForCredentialResponse();
      return response.success
        ? JSON.stringify(response.credentials)
        : `Error: ${response.error}`;
    }
  }]
});
```

---

## Subagent Support (Parallel Tactics)

Claude Code's built-in Agent tool enables parallel tactic execution. The system prompt instructs Claude Code to decompose complex sparks into subtasks:

```typescript
const sparkPrompt = `
You are Tacticl, executing a spark on the user's device.

Spark: ${spark.title}
Description: ${spark.description}
Type: ${spark.sparkType}
Repos: ${spark.repoAccess?.join(", ") ?? "none"}

Decompose this into subtasks and use the Agent tool to execute
independent subtasks in parallel. Report progress after each subtask.
`;
```

Each subagent maps to a Tactic in the existing data model. The device reports tactic creation/progress via `spark_progress` messages.

---

## Cloud-Side Changes (Minimal) — IMPLEMENTED (Phase 1)

### DeviceSettings entity update

```java
// data-social: DeviceSettings.java
public class DeviceSettings {
    // Existing
    private int maxDaemons = 1;
    private boolean autoWake = false;
    private int priority = 0;

    // New
    private String executionEngine = "CLAUDE_CODE";
    private ClaudeCodeConfig claudeCodeConfig;
}
```

### New ClaudeCodeConfig entity

```java
// data-social: ClaudeCodeConfig.java (embedded in DeviceSettings)
public class ClaudeCodeConfig {
    private String model = "claude-opus-4-6";
    private int maxTurns = 25;
    private BigDecimal maxBudgetUsd = new BigDecimal("5.00");
    private List<String> allowedTools;
    private List<String> disallowedTools;
    private Map<String, Object> mcpServers;
    private String permissionMode = "acceptEdits";
    private String systemPromptOverride;
}
```

### ManageDeviceSkill update

The existing `manage_device` agent skill gains new parameters for Claude Code config:

```
manage_device(action: "update_settings", deviceId: "...",
  settings: {
    executionEngine: "CLAUDE_CODE",
    claudeCodeConfig: {
      model: "claude-opus-4-6",
      maxBudgetUsd: 10.00,
      allowedTools: ["Read", "Write", "Edit", "Bash"],
      mcpServers: { "playwright": { "command": "npx", "args": ["@playwright/mcp@latest"] } }
    }
  }
)
```

### DeviceRegistration additions

```java
// Existing fields (no change):
int activeDaemons;
String daemonVersion;

// New field:
String claudeCodeVersion;  // Version of Claude Code CLI on device (reported by device)
```

### WebSocket protocol additions

New message types from device:

| Type | Direction | Purpose |
|------|-----------|---------|
| `claude_code_status` | Device → Cloud | Claude Code CLI version, availability, health |
| `engine_selected` | Device → Cloud | Which engine was chosen for a spark |

These are additive — existing message types unchanged.

---

## Settings REST API

Existing endpoints handle the new config transparently since ClaudeCodeConfig is embedded in DeviceSettings:

```
GET  /api/settings/devices/{deviceId}     → returns DeviceSettings including new fields
PUT  /api/settings/devices/{deviceId}     → updates DeviceSettings including new fields
```

No new endpoints needed.

---

## Prerequisites on Device

For Claude Code engine to work, the desktop device needs:

1. **Node.js 18+** — Runtime for CLI
2. **Claude Code CLI** — `npm install -g @anthropic-ai/claude-code`
3. **Anthropic API key** — Provided via cloud credentials or device-local config
4. **Optional: MCP servers** — Installed per user config

The device daemon reports Claude Code availability via `claude_code_status` message. If Claude Code is not installed and `executionEngine = CLAUDE_CODE`, the device falls back to LEGACY and reports the fallback to cloud.

---

## Migration & Rollout

1. **Phase 1** ✅: Add ClaudeCodeConfig to DeviceSettings, deploy cloud changes. No device impact — all devices continue using LEGACY (existing behavior).
2. **Phase 2**: Update device daemon to support Claude Code CLI engine. New daemon version spawns CLI subprocess for spark execution.
3. **Phase 3**: Default new device registrations to `executionEngine = CLAUDE_CODE`. Existing devices stay on LEGACY until user or auto-update changes it.
4. **Phase 4**: AUTO mode intelligence — learn which engine performs better per task type.

---

## Security Considerations

- Claude Code runs with the user's local permissions (same as existing daemon)
- `permissionMode` controls what CLI can do without asking
- `allowedTools` / `disallowedTools` restrict available tools per device
- `maxBudgetUsd` caps API spend per spark execution via `--max-cost`
- Existing tier system (Tier 0/1/2) maps to CLI hooks for checkpoint enforcement
- Credential flow uses existing secure WebSocket channel (PASETO-authenticated)
- Domain allowlist/blocklist from UserConfig enforced via CLI system prompt
- CLI hooks call back to localhost only (daemon's local HTTP server)

---

## Cost Implications

| Engine | Cost Source | Control |
|--------|-----------|---------|
| Cloud Agent | LlmRouter → tracked via LlmUsageRecorder | Existing billing |
| Legacy Daemon | No LLM cost (command execution only) | N/A |
| Claude Code CLI | Anthropic API direct (via CLI) | `maxBudgetUsd` → `--max-cost` per spark, reported via spark_completed.totalTokens |

Claude Code costs are reported back to cloud via `spark_completed` message's `totalTokens` field, enabling the existing cost estimation in SparkService.

---

## Summary

| What | Detail |
|------|--------|
| **Change** | Add Claude Code CLI as device execution engine |
| **Integration** | Spawn `claude` subprocess, parse streaming JSON, map to WebSocket protocol |
| **Default** | Claude Code is default on new desktop devices |
| **Configurable** | executionEngine per device, full Claude Code settings → CLI flags |
| **Cloud impact** | DeviceSettings + ClaudeCodeConfig entities only (Phase 1 complete) |
| **Protocol impact** | 2 new additive WebSocket message types |
| **Hooks** | CLI hooks → local HTTP callbacks on daemon for checkpoints + progress |
| **Rollout** | Phased — cloud first (done), device daemon update, then default flip |
