# Device Agent Architecture

**System**: Device daemon (runs on user's desktop/laptop/mobile)

## Overview

Devices are full-power agent executors. They connect to the cloud orchestrator via WebSocket, receive spark dispatches, decompose them into tactics, and execute them locally. Desktop devices (macOS, Windows, Linux) have an additional execution engine option: **Claude Code CLI**.

## Device Types

```
Desktop (priority 0 — preferred for spark routing):
  MACOS, WINDOWS, LINUX
  → Full daemon capabilities
  → Claude Code CLI available (NEW — default engine)

Mobile (priority 1):
  IPHONE, ANDROID
  → Daemon capabilities only
  → Claude Code NOT available (no CLI support)
```

## Execution Engines (Desktop Only)

Desktop devices have a configurable execution engine:

| Engine | Description | Default |
|--------|-------------|---------|
| `CLAUDE_CODE` | CLI subprocess — full agentic execution with file/bash/web/MCP/subagents | Yes (desktop) |
| `LEGACY` | Existing command-based protocol (TERMINAL_CMD, OPEN_URL, etc.) | Yes (mobile) |
| `AUTO` | Choose per-spark based on type/complexity | No |

Mobile devices always use LEGACY. The `executionEngine` setting only applies to desktop.

## Connection & Lifecycle

```
Device powers on
  │
  ├── Daemon starts, connects: WSS /ws/device?token={PASETO}&deviceId={id}
  ├── WebSocketAuthInterceptor validates token + deviceId
  ├── DeviceSessionManager.registerSession()
  │     ├── Creates DeviceSession in Firestore
  │     └── Calls sparkService.dispatchQueuedSparks() (resume offline work)
  │
  ├── Periodic heartbeat: device sends "ping", cloud responds "pong"
  ├── Device sends "capabilities" (browser, terminal, shortcuts, etc.)
  ├── Device sends "status" (battery, charging, network)
  │
  └── On disconnect: session marked inactive, device goes offline
```

## Spark Execution Flow

```
Cloud sends spark_dispatch via WebSocket
  │
  ├── { type: "spark_dispatch", sparkId, title, description,
  │     sparkType, priority, checkpointPolicy, repoAccess, models }
  │
  ▼
Device receives spark
  │
  ├── Check executionEngine setting:
  │
  ├── CLAUDE_CODE (desktop default):
  │   │
  │   ├── Spawn `claude` CLI subprocess with spark as prompt
  │   ├── Built-in tools: Read, Write, Edit, Bash, Glob, Grep,
  │   │    WebSearch, WebFetch, Agent (subagents)
  │   ├── Custom MCP servers per device config
  │   ├── CLI hooks for progress reporting + checkpoint enforcement
  │   ├── Subagents decompose into parallel tactics
  │   └── Reports back via existing WebSocket protocol
  │
  ├── LEGACY:
  │   │
  │   ├── Device decomposes spark into tactics locally
  │   ├── Each tactic executes via command protocol
  │   ├── Commands: TERMINAL_CMD, OPEN_URL, LAUNCH_APP, TYPE_TEXT,
  │   │    CLICK_ELEMENT, TAKE_SCREENSHOT, RUN_SHORTCUT, etc.
  │   └── Reports progress via spark_progress messages
  │
  └── AUTO:
      └── Route to CLAUDE_CODE or LEGACY based on spark type
```

## WebSocket Protocol (Device ↔ Cloud)

### Device → Cloud Messages

| Type | Purpose | Key Fields |
|------|---------|------------|
| `spark_accepted` | Spark dispatch acknowledged | sparkId |
| `spark_progress` | Periodic progress + tactic sync | sparkId, status, tokensDelta, tactics[] |
| `spark_checkpoint` | Request user approval | sparkId, tacticId, title, description, findings[], options[] |
| `spark_completed` | Execution succeeded | sparkId, result, totalTokens |
| `spark_failed` | Execution failed | sparkId, error |
| `result` | Command result (legacy) | commandId, success, message, data |
| `capabilities` | Device capabilities update | Map of capabilities |
| `status` | Battery, charging, network | Map of status fields |
| `credentials_request` | Request platform OAuth tokens | platform, sparkId, requestId |
| `ping` | Heartbeat | — |
| `claude_code_status` | Claude Code CLI availability (NEW) | version, available, health |
| `engine_selected` | Which engine chosen for spark (NEW) | sparkId, engine |

### Cloud → Device Messages

| Type | Purpose | Key Fields |
|------|---------|------------|
| `spark_dispatch` | Send spark for execution | sparkId, title, description, sparkType, priority, checkpointPolicy, repoAccess |
| `spark_cancel` | Cancel running spark | sparkId |
| `checkpoint_decision` | User's approval decision | sparkId, checkpointId, decision (APPROVED/REJECTED/MODIFIED), feedback |
| `command` | Execute device command (legacy) | commandId, commandType, payload |
| `credentials_response` | Return platform OAuth tokens | requestId, success, credentials/error |
| `pong` | Heartbeat response | timestamp |

## Checkpoint Flow

```
Device hits Tier 1+ action
  │
  ├── Sends: spark_checkpoint { sparkId, title, findings, options }
  ├── Cloud creates Checkpoint entity
  ├── Spark status → CHECKPOINT
  ├── Broadcasts to user (mobile/web)
  │
  ▼
User reviews and decides
  │
  ├── Cloud sends: checkpoint_decision { decision, feedback }
  ├── Spark status → EXECUTING
  └── Device resumes or aborts
```

## Credential Flow

```
Device needs platform credentials (e.g., Twitter OAuth)
  │
  ├── Sends: credentials_request { platform, sparkId, requestId }
  ├── Cloud validates device ownership + spark assignment
  ├── Fetches from: tacticl_users/{userId}/social_integrations/
  │
  └── Responds: credentials_response { credentials: { accessToken, ... } }
```

## Device Routing Intelligence

When multiple devices are online, `DeviceRoutingService` selects:

```
Score by (descending priority):
  1. isCharging (plugged-in preferred)
  2. batteryLevel (higher wins)
  3. deviceType.priority (0=desktop preferred, 1=mobile)
  4. requiredCapability (filter devices without it)
```

## Device Configuration

### DeviceRegistration (Firestore: tacticl_users/{userId}/devices/)

```
id, userId, deviceName, deviceType
state: PENDING_VERIFICATION → ACTIVE → SUSPENDED | REVOKED
publicKeyFingerprint, pushToken
capabilities: { browser, terminal, shortcuts, screenshots, ... }
connectivity: { batteryLevel, isCharging, wifiSSID, ... }
specs: { osVersion, ram, disk, cpu, ... }
clonedRepos: [list of cached repos]
activeDaemons: int (current concurrent executors)
daemonVersion: string
claudeCodeVersion: string (NEW)
sparkPreferences: { per-type routing overrides }
settings: DeviceSettings (embedded)
```

### DeviceSettings (embedded in DeviceRegistration)

```
maxDaemons: 1          (max concurrent tactic execution)
autoWake: false        (auto-wake for incoming sparks)
priority: 0            (routing priority, higher = preferred)
executionEngine: "CLAUDE_CODE"  (NEW — CLAUDE_CODE | LEGACY | AUTO)
claudeCodeConfig: {             (NEW — embedded)
  model: "claude-opus-4-6"
  maxTurns: 25
  maxBudgetUsd: 5.00
  allowedTools: [...]
  disallowedTools: [...]
  mcpServers: { ... }
  permissionMode: "acceptEdits"
  systemPromptOverride: null
}
```

## Claude Code Engine Integration (NEW)

### Prerequisites (Desktop Only)

1. Node.js 18+ runtime
2. Claude Code CLI (`npm install -g @anthropic-ai/claude-code`)
3. Anthropic API key (via cloud credentials or local config)
4. Optional: MCP servers per device config

### How It Works

The daemon spawns `claude` as a subprocess in non-interactive mode with `--output-format stream-json`, then parses the streaming JSON messages and maps them to the existing WebSocket protocol.

```typescript
// Device daemon — Claude Code CLI execution path
async function executeWithClaudeCode(spark: SparkPayload) {
  const config = device.settings.claudeCodeConfig;

  const args = [
    "--print", buildSparkPrompt(spark),
    "--output-format", "stream-json",
    "--model", config.model,
    "--max-turns", String(config.maxTurns),
    "--permission-mode", config.permissionMode,
  ];

  if (config.allowedTools?.length) {
    args.push("--allowedTools", config.allowedTools.join(","));
  }
  if (config.disallowedTools?.length) {
    args.push("--disallowedTools", config.disallowedTools.join(","));
  }
  if (config.systemPromptOverride) {
    args.push("--system-prompt", config.systemPromptOverride);
  }

  const proc = spawn("claude", args, {
    cwd: getWorkingDirectory(spark),
    env: { ...process.env, ANTHROPIC_API_KEY: apiKey },
  });

  // Parse streaming JSON lines from stdout
  for await (const line of readlines(proc.stdout)) {
    const msg = JSON.parse(line);

    if (msg.type === "assistant" && msg.stop_reason === "end_turn") {
      ws.send({
        type: "spark_completed",
        sparkId: spark.sparkId,
        result: { response: extractText(msg) },
        totalTokens: msg.usage?.total_tokens ?? 0,
      });
    } else if (msg.type === "tool_use") {
      // Report progress for each tool execution
      ws.send({
        type: "spark_progress",
        sparkId: spark.sparkId,
        status: "EXECUTING",
        tokensDelta: msg.usage?.output_tokens ?? 0,
        tactics: [{
          tacticId: currentTacticId,
          description: `${msg.tool}: ${summarize(msg.input)}`,
          status: "EXECUTING",
        }],
      });
    }
  }

  // Handle process exit
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

### CLI Flag → ClaudeCodeConfig Mapping

| ClaudeCodeConfig field | CLI flag | Notes |
|----------------------|----------|-------|
| `model` | `--model` | e.g., `claude-opus-4-6` |
| `maxTurns` | `--max-turns` | Max agentic loop iterations |
| `allowedTools` | `--allowedTools` | Comma-separated tool names |
| `disallowedTools` | `--disallowedTools` | Comma-separated tool names |
| `permissionMode` | `--permission-mode` | `default`, `acceptEdits`, `bypassPermissions` |
| `systemPromptOverride` | `--system-prompt` | Custom system prompt text |
| `mcpServers` | `--mcp-config` | Path to MCP config JSON file |
| `maxBudgetUsd` | `--max-cost` | Cost cap in USD |

### Checkpoint Integration via CLI Hooks

Claude Code CLI supports hooks configured in `.claude/settings.json`. The daemon writes a hook config that calls back to a local HTTP endpoint for checkpoint enforcement:

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
- **PreToolUse**: Check if action requires checkpoint (Tier 1+). If yes, send `spark_checkpoint` via WebSocket, wait for `checkpoint_decision`, return block/allow.
- **PostToolUse**: Send `spark_progress` via WebSocket with tool execution details and token delta.

### Credential Flow via MCP

When Claude Code needs platform credentials, the daemon runs a local MCP server that bridges to cloud credentials:

```typescript
// Local MCP server exposed to Claude Code via --mcp-config
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

### Fallback

If Claude Code CLI is not installed and `executionEngine = CLAUDE_CODE`:
1. Device detects missing CLI on startup (`which claude` or `claude --version`)
2. Reports `claude_code_status: { available: false }`
3. Falls back to LEGACY engine for incoming sparks
4. Reports `engine_selected: { sparkId, engine: "LEGACY", reason: "claude_code_unavailable" }`

## Tactic Data Model

```
Tactic (Firestore: tactics/)
  id, sparkId, deviceId
  description: "Run TypeScript tests"
  status: PENDING → EXECUTING → COMPLETED | FAILED
  repos: [list of git repos used]
  result: { output map }
  tokenUsage: long
  completedAt: Instant
```

Tactics are created by the device and synced to cloud via `spark_progress` messages.
