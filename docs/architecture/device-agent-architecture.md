# Device Agent Architecture

**System**: Device daemon (runs on user's desktop/laptop/mobile)

## Overview

Devices are full-power agent executors. They connect to the cloud orchestrator via WebSocket, receive spark dispatches, decompose them into tactics, and execute them locally. Desktop devices (macOS, Windows, Linux) have an additional execution engine option: **Claude Code Agent SDK**.

## Device Types

```
Desktop (priority 0 — preferred for spark routing):
  MACOS, WINDOWS, LINUX
  → Full daemon capabilities
  → Claude Code Agent SDK available (NEW — default engine)

Mobile (priority 1):
  IPHONE, ANDROID
  → Daemon capabilities only
  → Claude Code NOT available (no CLI support)
```

## Execution Engines (Desktop Only)

Desktop devices have a configurable execution engine:

| Engine | Description | Default |
|--------|-------------|---------|
| `CLAUDE_CODE` | Agent SDK — full agentic execution with file/bash/web/MCP/subagents | Yes (desktop) |
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
  │   ├── Agent SDK query() with spark as prompt
  │   ├── Built-in tools: Read, Write, Edit, Bash, Glob, Grep,
  │   │    WebSearch, WebFetch, Agent (subagents)
  │   ├── Custom MCP servers per device config
  │   ├── Hooks for progress reporting + checkpoint enforcement
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

```typescript
// Device daemon — Claude Code execution path
async function executeWithClaudeCode(spark: SparkPayload) {
  const config = device.settings.claudeCodeConfig;

  for await (const message of query({
    prompt: buildSparkPrompt(spark),
    options: {
      cwd: getWorkingDirectory(spark),
      model: config.model,
      maxTurns: config.maxTurns,
      maxBudgetUsd: config.maxBudgetUsd,
      allowedTools: config.allowedTools,
      mcpServers: config.mcpServers,
      permissionMode: config.permissionMode,
      hooks: {
        PreToolUse: [checkpointEnforcementHook],
        PostToolUse: [progressReportingHook],
      },
    },
  })) {
    // Map Agent SDK messages → existing WebSocket protocol
    // spark_progress, spark_checkpoint, spark_completed
  }
}
```

### Mapping Agent SDK → Existing Protocol

| Agent SDK Event | WebSocket Message | Notes |
|----------------|-------------------|-------|
| PostToolUse hook fires | `spark_progress` | Report tactic updates + token delta |
| PreToolUse blocks on Tier 1+ | `spark_checkpoint` | Wait for `checkpoint_decision` response |
| ResultMessage received | `spark_completed` | Include result + totalTokens |
| Error thrown | `spark_failed` | Include error message |
| Subagent spawned | `spark_progress` (new tactic) | Each subagent = one tactic |

### Fallback

If Claude Code CLI is not installed and `executionEngine = CLAUDE_CODE`:
1. Device detects missing CLI on startup
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
