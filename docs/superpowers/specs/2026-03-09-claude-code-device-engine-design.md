# Claude Code Device Execution Engine — Architecture Design

**Date:** 2026-03-09
**Status:** Draft
**Scope:** Add Claude Code Agent SDK as a new device execution engine option

---

## Context

Tacticl's full SDLC agent pipeline is already built and operational across both execution paths:

- **Cloud Agent** — VoiceAgentService + LlmRouter + 20+ AgentSkills + Playwright browser + multi-LLM support (Anthropic, OpenAI, Grok, Gemini). Runs on Cloud Run, scales for all users.
- **Device Agent** — WebSocket-based spark dispatch, tactic decomposition, 9 command types (TERMINAL_CMD, OPEN_URL, CLICK_ELEMENT, etc.), checkpoint flow, credential requests, daemon concurrency control.

Both paths can handle the full range of tasks. Routing is a user preference (DEVICE_FIRST, CLOUD_FIRST, CLOUD_ONLY), not a task-type decision.

## What's New

Add the **Claude Code Agent SDK** (TypeScript) as an additional execution engine on desktop devices. This gives devices access to Claude Code's built-in agentic tools — file read/write/edit, bash, glob, grep, web search/fetch, MCP server integration, and subagent orchestration — as a more capable complement to the existing command-based daemon.

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
          │ Playwright  │  │     → Agent SDK query()  │
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
    String model = "claude-opus-4-6";           // Agent SDK model
    int maxTurns = 25;                          // Max agent turns per tactic
    BigDecimal maxBudgetUsd = new BigDecimal("5.00"); // Cost cap per spark
    List<String> allowedTools;                  // Restrict built-in tools (null = all)
    List<String> disallowedTools;               // Explicitly block tools
    Map<String, Object> mcpServers;             // Custom MCP server configs
    String permissionMode = "acceptEdits";      // Agent SDK permission mode
    String systemPromptOverride;                // Optional custom system prompt
}
```

### ExecutionEngine enum

```java
enum ExecutionEngine {
    CLAUDE_CODE,  // Agent SDK — full agentic execution (default)
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

### Claude Code Engine Wrapper

The device daemon (TypeScript app running on desktop) gains a new execution path:

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

  // Execute via Agent SDK
  for await (const message of query({
    prompt: buildSparkPrompt(spark),
    options: {
      cwd: getWorkingDirectory(spark),
      model: config.model,
      maxTurns: config.maxTurns,
      maxBudgetUsd: config.maxBudgetUsd,
      allowedTools: config.allowedTools ?? [
        "Read", "Write", "Edit", "Bash", "Glob", "Grep",
        "WebSearch", "WebFetch", "Agent"
      ],
      mcpServers: config.mcpServers ?? {},
      permissionMode: config.permissionMode,
      systemPrompt: config.systemPromptOverride ?? buildDeviceSystemPrompt(spark),
      hooks: {
        PostToolUse: [{ matcher: ".*", hooks: [reportProgressHook] }],
      },
    },
  })) {
    if ("result" in message) {
      // Report completion
      ws.send({
        type: "spark_completed",
        sparkId: spark.sparkId,
        result: { response: message.result },
        totalTokens: message.usage?.totalTokens ?? 0,
      });
    }
  }
}
```

### Checkpoint Integration

Claude Code's hook system maps to the existing checkpoint protocol:

```typescript
// PreToolUse hook for Tier 1+ actions
async function checkpointHook(input, toolUseId, context) {
  if (requiresCheckpoint(input.tool_name)) {
    // Send checkpoint to cloud
    ws.send({
      type: "spark_checkpoint",
      sparkId: currentSparkId,
      title: `Confirm: ${input.tool_name}`,
      description: describeAction(input),
      options: ["Approve", "Reject", "Modify"],
    });

    // Wait for cloud relay of user decision
    const decision = await waitForCheckpointDecision();

    if (decision.decision === "REJECTED") {
      return { decision: "block", message: "User rejected action" };
    }
    if (decision.decision === "MODIFIED") {
      return { decision: "block", message: decision.feedback };
    }
    return {};  // Approved — proceed
  }
  return {};
}
```

### Progress Reporting

Agent SDK hooks report tactic progress through existing WebSocket protocol:

```typescript
// PostToolUse hook — report progress after each tool execution
async function reportProgressHook(input, toolUseId, context) {
  ws.send({
    type: "spark_progress",
    sparkId: currentSparkId,
    status: "EXECUTING",
    tokensDelta: context.usage?.outputTokens ?? 0,
    tactics: [{
      tacticId: currentTacticId,
      description: `${input.tool_name}: ${summarize(input)}`,
      status: "EXECUTING",
      tokenUsage: context.usage?.totalTokens ?? 0,
    }],
  });
  return {};
}
```

### Credential Flow

When Claude Code needs platform credentials (e.g., for social posting via MCP):

```typescript
// Custom MCP tool that bridges to cloud credentials
const credentialTool = tool(
  "get_platform_credentials",
  "Get OAuth credentials for a connected platform",
  { platform: z.string() },
  async (args) => {
    ws.send({
      type: "credentials_request",
      platform: args.platform,
      sparkId: currentSparkId,
      requestId: uuid(),
    });
    const response = await waitForCredentialResponse();
    if (!response.success) {
      return { content: [{ type: "text", text: `Error: ${response.error}` }] };
    }
    return { content: [{ type: "text", text: JSON.stringify(response.credentials) }] };
  }
);
```

---

## Subagent Support (Parallel Tactics)

Claude Code's built-in Agent tool enables parallel tactic execution:

```typescript
// The Agent SDK prompt can decompose sparks into parallel subtasks
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

## Cloud-Side Changes (Minimal)

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

1. **Node.js 18+** — Runtime for Agent SDK
2. **Claude Code CLI** — `npm install -g @anthropic-ai/claude-code`
3. **Anthropic API key** — Provided via cloud credentials or device-local config
4. **Optional: MCP servers** — Installed per user config

The device daemon reports Claude Code availability via `claude_code_status` message. If Claude Code is not installed and `executionEngine = CLAUDE_CODE`, the device falls back to LEGACY and reports the fallback to cloud.

---

## Migration & Rollout

1. **Phase 1**: Add ClaudeCodeConfig to DeviceSettings, deploy cloud changes. No device impact — all devices continue using LEGACY (existing behavior).
2. **Phase 2**: Update device daemon to support Claude Code engine. New daemon version includes Agent SDK integration.
3. **Phase 3**: Default new device registrations to `executionEngine = CLAUDE_CODE`. Existing devices stay on LEGACY until user or auto-update changes it.
4. **Phase 4**: AUTO mode intelligence — learn which engine performs better per task type.

---

## Security Considerations

- Claude Code runs with the user's local permissions (same as existing daemon)
- `permissionMode` controls what Agent SDK can do without asking
- `allowedTools` / `disallowedTools` restrict available tools per device
- `maxBudgetUsd` caps API spend per spark execution
- Existing tier system (Tier 0/1/2) maps to Agent SDK hooks for checkpoint enforcement
- Credential flow uses existing secure WebSocket channel (PASETO-authenticated)
- Domain allowlist/blocklist from UserConfig enforced via Agent SDK system prompt

---

## Cost Implications

| Engine | Cost Source | Control |
|--------|-----------|---------|
| Cloud Agent | LlmRouter → tracked via LlmUsageRecorder | Existing billing |
| Legacy Daemon | No LLM cost (command execution only) | N/A |
| Claude Code | Agent SDK → Anthropic API direct | `maxBudgetUsd` per spark, reported via spark_completed.totalTokens |

Claude Code costs are reported back to cloud via `spark_completed` message's `totalTokens` field, enabling the existing cost estimation in SparkService.

---

## Summary

| What | Detail |
|------|--------|
| **Change** | Add Claude Code Agent SDK as device execution engine |
| **Scope** | Device daemon (TypeScript) + minor cloud config additions |
| **Default** | Claude Code is default on new desktop devices |
| **Configurable** | executionEngine per device, full Claude Code settings |
| **Cloud impact** | DeviceSettings + ClaudeCodeConfig entities only |
| **Protocol impact** | 2 new additive WebSocket message types |
| **Rollout** | Phased — cloud first, device daemon update, then default flip |
