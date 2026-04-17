# Conversational Chat Design
**Date:** 2026-04-17
**Status:** Approved

## Overview

Replace the current one-shot agent command flow with a multi-turn conversational experience that mirrors how Claude chat works: back-and-forth dialogue, visible conversation history, and a natural handoff to the PDLC pipeline once requirements are understood and approved.

---

## Goals

- Every spark type (CODE, DEVOPS, SOCIAL, RESEARCH, CREATIVE, DATA) goes through conversational requirements gathering before execution
- Agent asks one clarifying question at a time until it has enough context
- Agent presents a summary and asks "Ready to start?" using the `propose_implementation` tool
- User approves in natural language → agent calls `start_implementation` → Spark created → routed to PDLC or CloudOrchestratorService
- After execution starts, the thread stays open: user can ask for progress, alter course, or request changes throughout the pipeline lifecycle
- Multiple sessions supported — each new conversation is an independent thread (like Claude chat sessions)

---

## Architecture

### Thread-First Model

The first message **does not create a Spark**. It creates a `ConversationSession`. A Spark is only created when the user approves and `start_implementation` is called. This keeps the Spark model as a pure execution unit.

### Data Model — `ConversationSession` (MongoDB)

```
conversation_sessions collection:

ConversationSession {
  id              String
  userId          String
  title           String          — auto-generated from first message
  status          SessionStatus   — GATHERING | PROPOSING | ACTIVE | COMPLETED
  sparkId         String?         — null until approval, set when Spark is created
  detectedSparkType SparkType?    — detected from conversation context
  messages        List<ConversationMessage>
  createdAt       Instant
  updatedAt       Instant
}

ConversationMessage {
  role      String   — "user" | "assistant"
  content   String
  timestamp Instant
}
```

### Session Status Transitions

```
GATHERING  →  agent calls propose_implementation()  →  PROPOSING
PROPOSING  →  agent calls start_implementation()    →  ACTIVE
PROPOSING  →  user revises / changes mind           →  GATHERING
ACTIVE     →  Spark completes                       →  COMPLETED
```

---

## REST API — New Module: `service-conversation`

```
POST   /v1/conversations                    — create session + send first message
POST   /v1/conversations/{id}/messages      — send subsequent messages
GET    /v1/conversations                    — list sessions for user (sidebar)
GET    /v1/conversations/{id}               — get session with full message history
```

Existing `POST /v1/agent/command` is **unchanged** — still used for device routing and one-shot commands.

---

## Agent Behavior — Tool Use Pattern

On every message turn, the Anthropic API call includes two tools alongside the conversation history:

### Tool: `propose_implementation`
Called by the agent when it has gathered sufficient requirements.

```json
{
  "name": "propose_implementation",
  "description": "Call when you have enough information to propose what you will build or do. Present a concise summary of the plan.",
  "input_schema": {
    "type": "object",
    "properties": {
      "summary": { "type": "string", "description": "What you will build/do" },
      "spark_type": { "type": "string", "description": "CODE | DEVOPS | SOCIAL | RESEARCH | CREATIVE | DATA" }
    },
    "required": ["summary", "spark_type"]
  }
}
```

Backend action: save `detectedSparkType`, transition session → `PROPOSING`, return summary text to user.

### Tool: `start_implementation`
Called by the agent when it detects user approval ("yes", "go ahead", "looks good", "start it", etc.).

```json
{
  "name": "start_implementation",
  "description": "Call when the user has approved the proposed plan and wants to begin implementation.",
  "input_schema": {
    "type": "object",
    "properties": {
      "confirmed_summary": { "type": "string" }
    },
    "required": ["confirmed_summary"]
  }
}
```

Backend action:
1. `SparkService.createSpark(userId, sparkType, confirmedSummary)`
2. Route: CODE/DEVOPS → `PdlcRouter.route(...)`, all others → `CloudOrchestratorService.execute(...)`
3. Set `session.sparkId`, transition → `ACTIVE`
4. Return confirmation text + sparkId to frontend

---

## Message Flow

### POST /v1/conversations/{id}/messages

```
1. Append user message to session.messages
2. Build Anthropic messages[] from full session history
3. Call Anthropic API (Sonnet 4.5) with propose_implementation + start_implementation tools
4. On tool_use = propose_implementation:
     - session.status = PROPOSING
     - session.detectedSparkType = tool input
     - return summary text to user
5. On tool_use = start_implementation:
     - SparkService.createSpark(...)
     - PdlcRouter or CloudOrchestratorService
     - session.sparkId = spark.getId()
     - session.status = ACTIVE
     - return "Starting now..." text + sparkId
6. On text only (still conversing):
     - append assistant message
     - return text (session stays GATHERING or PROPOSING)
```

### While ACTIVE — Control During Execution

The agent receives the full session history including `sparkId`. When the user asks "how's it going?" or "change the auth to JWT":

- Progress queries → agent calls `PipelineRunRepository.findById(sparkId)` to report status
- Course corrections → agent calls `SparkService.injectCheckpoint(sparkId, instruction)`

---

## System Prompt

The conversation system prompt instructs the agent to:
- Ask one clarifying question at a time — never multiple
- Not suggest solutions until requirements are clear
- When confident it understands the full scope, call `propose_implementation`
- When the user approves, call `start_implementation`
- While a pipeline is ACTIVE, answer progress questions and accept course corrections

---

## Module Structure

```
service/
  service-conversation/           — NEW
    ConversationController.java   — 4 REST endpoints
    ConversationService.java      — session lifecycle + LLM orchestration
    build.gradle.kts              — depends on business-agent, business-pipeline, data-pipeline

data/
  data-conversation/              — NEW
    ConversationSession.java      — MongoDB document
    ConversationMessage.java      — embedded value object
    ConversationSessionRepository.java
    build.gradle.kts
```

---

## What Does NOT Change

- `POST /v1/agent/command` — unchanged, still used for device routing
- `Spark` entity — unchanged, still a pure execution unit
- `PdlcRouter` — unchanged, called by ConversationService on approval
- `CloudOrchestratorService` — unchanged, called for non-CODE/DEVOPS sparks

---

## Non-Goals

- No changes to device agent WebSocket flow
- No changes to existing Spark or Tactic models
- No UI design specified here (belongs in tacticl-web/tacticl-mobile)
