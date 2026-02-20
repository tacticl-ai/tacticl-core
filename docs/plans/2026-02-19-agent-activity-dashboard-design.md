# Agent Activity Dashboard & Electron Desktop Agent

## Overview

Build a real-time agent activity dashboard and Electron desktop agent for Tacticl. The dashboard tracks the full lifecycle of user requests across devices, agents, tasks, and commands. The Electron agent enables full desktop automation.

## Data Model

### Identity Hierarchy

```
askId       → User's request ("Plan my content for the week")
  deviceId  → Physical machine bound to this ask (MacBook Pro)
  taskId    → Unit of work decomposed from the ask ("Research trends")
    agentId → LLM instance working this task
    commandId → Single device/cloud action (search_web, take_screenshot)
```

Every command traces: `commandId → taskId → askId → deviceId + agentId`.

### Entities

#### Ask (NEW)
```
id:           UUID (askId)
userId:       String
deviceId:     String (primary device — default device, overridable)
commandText:  String (user's original request)
state:        PENDING | RUNNING | COMPLETED | FAILED | CANCELLED
tasks:        List<taskId>
totalTokens:  int (sum of all agent instances)
estimatedCost: BigDecimal (calculated from model pricing)
deviceFallbackEnabled: boolean (auto-reroute to another device if primary goes offline)
createdAt:    Instant
completedAt:  Instant (nullable)
```

#### AgentTask (NEW)
```
id:           UUID (taskId)
askId:        String (parent ask)
userId:       String
description:  String (what this task does)
agentId:      String (LLM instance assigned to this task)
state:        PENDING | ASSIGNED | RUNNING | COMPLETED | FAILED | CANCELLED
commands:     List<commandId>
createdAt:    Instant
completedAt:  Instant (nullable)
```

#### AgentInstance (NEW)
```
id:           UUID (agentId)
taskId:       String (task this agent is working on)
userId:       String
deviceId:     String (device this agent executes on)
modelId:      String (e.g., "claude-sonnet-4-5")
state:        INITIALIZING | RUNNING | COMPLETED | FAILED | CANCELLED
tokenCount:   int
createdAt:    Instant
completedAt:  Instant (nullable)
```

#### DeviceCommand (EXISTING — add fields)
```
+ askId:      String (parent ask)
+ taskId:     String (parent task)
+ agentId:    String (agent that issued this command)
```
Existing fields: id, userId, deviceId, sessionId, commandType, payload, tier, state, result, createdAt, sentAt, completedAt, expiresAt.

#### DeviceRegistration (EXISTING — no changes)
id, userId, deviceName, deviceType (IPHONE, ANDROID, MACOS, WINDOWS, LINUX), capabilities, connectivity, state, lastSeenAt.

### State Machines

```
Ask:     PENDING → RUNNING → COMPLETED | FAILED | CANCELLED
Task:    PENDING → ASSIGNED → RUNNING → COMPLETED | FAILED | CANCELLED
Agent:   INITIALIZING → RUNNING → COMPLETED | FAILED | CANCELLED
Command: QUEUED → SENT → EXECUTING → COMPLETED | FAILED | EXPIRED | CANCELLED
```

## Device Strategy

### Device Registration
When someone installs Tacticl (mobile or desktop), the app:
1. Generates a UUID deviceId, stores locally (SecureStore / electron-store)
2. Collects device info (name, type, OS, capabilities)
3. POST /api/devices/register
4. First device auto-verifies; subsequent devices get 6-digit code
5. Connects via WebSocket with deviceId + auth token

### Device Types
- Mobile (iOS/Android): Dashboard + voice input + lightweight commands (open URL, deep links, notifications)
- Desktop (Electron): Full automation — screenshots, app control, typing, clicking, shell commands
- Cloud: Server-side skills (search_web, content_gen, browse_web) — not a physical device

### Default Device
User sets a default device in settings. All asks route there unless overridden. An ask is bound to one primary device.

## Electron Desktop Agent

### Architecture
Electron app = Chromium (renders React dashboard UI) + Node.js (background agent with OS access).

```
┌─────────────────────────────────┐
│ Electron App                    │
│ ┌─────────────┐ ┌────────────┐ │
│ │ Renderer    │ │ Main       │ │
│ │ (React UI)  │ │ (Node.js)  │ │
│ │ Dashboard   │ │ WS Client  │ │
│ │ Settings    │ │ Cmd Exec   │ │
│ │ Chat        │ │ OS APIs    │ │
│ └─────────────┘ └────────────┘ │
└─────────────────────────────────┘
```

### Capabilities (macOS)
- Screenshots: `screencapture` CLI or Electron `desktopCapturer`
- App control: AppleScript via `osascript`
- Typing/clicking: Accessibility API (requires user permission grant)
- Shell commands: Node.js `child_process`
- File system: Full Node.js `fs` access
- URL opening: `shell.openExternal()`

### Capabilities (Windows)
- Screenshots: `nircmd` or PowerShell
- App control: UI Automation API
- Typing/clicking: `robotjs` or `nut.js`
- Shell commands: Node.js `child_process`

### Project Structure
New repo: `tacticl-desktop`
```
tacticl-desktop/
  src/
    main/           # Electron main process
      index.ts      # App entry, window management
      ws-client.ts  # WebSocket connection (same protocol as mobile)
      executor.ts   # Command execution (screenshots, app control, etc.)
      store.ts      # electron-store for deviceId, settings
    renderer/       # React UI (shared or similar to mobile dashboard)
      App.tsx
      screens/
    preload.ts      # Bridge between main and renderer
  package.json
  electron-builder.yml
```

## Mobile App Tab Restructure

### New Tabs (4)
| Tab | Icon | Content |
|-----|------|---------|
| Home | home | Activity dashboard — live ask/task/command pipeline |
| Devices | cellphone-link | Device fleet — per-device detail view |
| History | clock-outline | Searchable audit log |
| Settings | cog | Default device, integrations, preferences |

Posts and Accounts tabs removed. Social integrations move under Settings.

## Activity Dashboard (Home Tab)

### Layout

**Status Row (top)**
- Devices online: "2/3 online"
- Active asks count
- Commands in flight

**Active Asks (main area)**
Expandable cards for each running ask:

```
┌─────────────────────────────────────┐
│ ⚡ RUNNING · 💻 MacBook Pro          │
│ "Plan my content for the week"      │
│ askId: ask-abc · Started 12s ago    │
│                                     │
│ Task 1: Research trends             │
│ 🤖 agent-x7y8 · claude-sonnet-4-5  │
│   💻 MacBook: open_url ✅ 0.8s     │
│   ☁️ Cloud:   search_web 🔄 ...    │
│                                     │
│ Task 2: Draft posts                 │
│ ⏳ PENDING (waiting for Task 1)     │
└─────────────────────────────────────┘
```

**Recent Asks (below)**
- Last ~10 completed asks, collapsed
- Tap to expand task/command breakdown
- "See all" → History tab

**Pending Confirmations**
- Tier 1/2 actions awaiting approval surface as actionable cards

### Devices Tab
- Card per registered device
- Shows: name, type, online/offline, battery, capabilities
- Tap device → all asks/tasks/commands running on or recently run on that device

## Backend Changes

### New REST Endpoints
```
GET  /api/agent/activity              # Active asks + tasks + commands (dashboard)
GET  /api/agent/asks                  # List asks (paginated, filterable)
GET  /api/agent/asks/{askId}          # Ask detail with tasks and commands
POST /api/agent/asks/{askId}/cancel   # Cancel an ask (cascades to tasks/commands)
```

### New Firestore Collections
```
asks              # Ask entities
agent_tasks       # AgentTask entities
agent_instances   # AgentInstance entities
```

### WebSocket Activity Broadcast
On every state change (ask/task/command), push to all user's connected devices:

```json
{
  "type": "activity",
  "askId": "ask-abc",
  "askState": "RUNNING",
  "commandText": "Plan my content for the week",
  "deviceId": "device-c3d4",
  "tasks": [
    {
      "taskId": "task-001",
      "description": "Research trending topics",
      "state": "RUNNING",
      "agentId": "agent-x7y8",
      "modelId": "claude-sonnet-4-5",
      "commands": [
        {
          "commandId": "cmd-aaa",
          "commandType": "SEARCH_WEB",
          "deviceId": null,
          "state": "COMPLETED",
          "elapsedMs": 2100
        }
      ]
    }
  ]
}
```

### Agent Orchestration Changes
Current flow: user command → single Claude conversation → sequential skill calls.

New flow:
1. User submits ask → `Ask` entity created (PENDING)
2. Orchestrator agent decomposes ask into tasks → `AgentTask` entities created
3. Each task assigned an `AgentInstance` (LLM conversation)
4. Agent executes commands → `DeviceCommand` entities (linked by askId, taskId, agentId)
5. Task completes → agent completes → ask completes when all tasks done
6. Every state transition broadcasts via WebSocket

For now (single-task simplification): one ask = one task = one agent. The model supports multi-task decomposition when the orchestrator is ready.

## Cancellation

User can cancel at any level via `POST /api/agent/asks/{askId}/cancel`.

Cascade: Cancel ask → cancel all tasks → terminate all agents → abort in-flight commands.

Dashboard: long-press or swipe an active ask card → cancel button.

## Failure Propagation

```
Command fails
  → Agent decides: retry (up to 2 attempts) or mark task FAILED
    → If task FAILED and other tasks still running: Ask continues
    → Ask FAILED only when all tasks resolved and at least one failed
    → Ask COMPLETED when all tasks completed successfully
```

One failed task does not kill the whole ask. The agent adapts — "Research trends" failing doesn't prevent "Draft posts" from proceeding with what it has.

## Notifications

- **Mobile**: Push notification via FCM when ask completes/fails and app is backgrounded
- **Desktop (Electron)**: System notification via Electron Notification API
- **In-app**: WebSocket activity broadcast updates dashboard in real-time

Triggers: ask completed, ask failed, confirmation needed (Tier 1/2), device went offline during active ask.

## Cost Visibility

Each `AgentInstance` tracks `tokenCount`. Each `Ask` aggregates `totalTokens` and `estimatedCost` from all its agents.

Dashboard shows per-ask: "3 tasks, 18K tokens, ~$0.06". History tab shows cumulative daily/weekly cost.

Model pricing lookup table maintained in backend config.

## Concurrency Limits

```
Per user:    Max 5 concurrent asks
Per device:  Max 3 concurrent asks targeting same device
```

If limit reached, new asks queue as PENDING and start when a slot opens. Dashboard shows queue position.

## Device Offline Mid-Ask

```
Device disconnects during active ask
  → In-flight commands: wait 30s for reconnect
    → Reconnected: resume (re-send QUEUED/SENT commands)
    → Not reconnected: commands → FAILED ("device offline")
  → If deviceFallbackEnabled on Ask:
    → Auto-reroute remaining commands to another online device with matching capabilities
  → If not: agent gets failure → can retry or fail task
```

## Command Results Persistence

`DeviceCommand.result` (Map) persisted to Firestore on every completion. For large results (screenshots), store as reference: `result: { type: "screenshot", storageUrl: "gs://tacticl-assets/..." }` — don't inline base64.

Dashboard reads results from Firestore for past commands, WebSocket for live updates.

## Implementation Order

### Phase 1: Backend Data Model
- Ask, AgentTask, AgentInstance entities + repositories
- Update DeviceCommand with askId, taskId, agentId fields
- Activity REST endpoint
- WebSocket activity broadcast

### Phase 2: Mobile Dashboard
- Tab restructure (Home, Devices, History, Settings)
- Activity dashboard UI with live pipeline
- WebSocket activity message handling
- Devices tab

### Phase 3: Electron Desktop Agent
- New repo: tacticl-desktop
- Electron + React scaffold
- WebSocket client (reuse protocol from mobile)
- Command executor (screenshots, app control, shell)
- Device registration flow
- Dashboard UI (shared with mobile or web-based)

### Phase 4: Agent Orchestration
- Ask → multi-task decomposition
- Parallel task execution
- Task dependency management
- Agent instance lifecycle management
