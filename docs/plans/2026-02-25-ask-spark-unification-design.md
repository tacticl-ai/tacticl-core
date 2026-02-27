# Ask/Spark Unification Design

**Date:** 2026-02-25
**Status:** Approved

## Summary

Unify Ask and Spark into a single concept: **Spark**. Every chat command creates a Spark. No manual spark creation. Tactics are device-side decomposition only.

## Current State (Two Parallel Hierarchies)

**Cloud path** (VoiceAgentService): `Ask` -> `AgentTask` -> `AgentInstance`
**Device path** (SparkService): `Spark` -> `Tactic` -> (device executes)

These are redundant. The Ask system was a temporary cloud-only abstraction.

## Target State (Single Hierarchy)

Every `POST /api/agent/command` creates a **Spark**. Routing decides execution path:
- **Device available**: Spark -> route to device -> device decomposes into Tactics
- **No device (cloud fallback)**: Spark -> VoiceAgentService executes in-process

```
Chat message -> POST /api/agent/command
    -> SparkService.createSpark() [ALWAYS]
    -> Route decision:
        a) Device online -> SparkDispatchService -> device decomposes into Tactics
        b) No device -> VoiceAgentService cloud execution (no tactics)
    -> Spark lifecycle tracked through completion
```

## What Gets Removed

### Entities (data-social)
- `Ask.java` - replaced by Spark
- `AskState.java` - replaced by SparkState
- `AgentTask.java` - cloud tasks not needed; device uses Tactic
- `AgentTaskState.java`
- `AgentInstance.java` - agent tracking absorbed into Spark
- `AgentInstanceState.java`

### Repositories (data-social)
- `AskRepository.java`
- `AgentTaskRepository.java`
- `AgentInstanceRepository.java`

### Services (business-agent)
- `AskService.java` - lifecycle methods move to SparkService
- `AskContext.java` - becomes SparkContext (holds sparkId)

### DTOs (service-spark)
- `CreateSparkRequest.java` - no manual creation
- `UpdateSparkRequest.java` - no manual editing

### Controller endpoints removed
- `POST /api/sparks` - no manual creation (chat creates sparks)
- `PUT /api/sparks/{id}` - no manual editing

### Controller endpoints removed from AgentController
- `GET /api/agent/activity` - moves to SparkController as `GET /api/sparks/activity`
- `GET /api/agent/asks/{askId}` - replaced by `GET /api/sparks/{id}`
- `POST /api/agent/asks/{askId}/cancel` - replaced by `POST /api/sparks/{id}/cancel`

## What Gets Modified

### VoiceAgentService (business-agent)
- Replace `AskService.createAsk()` with setting SparkContext from the sparkId passed in
- Replace `askService.markRunning/markCompleted/markFailed` with `sparkService` equivalents
- `AskContext` -> `SparkContext` (holds sparkId only, no taskId/agentId)

### AgentController (service-agent)
- `POST /api/agent/command`: Always create Spark first, then route
  - Device path: route spark to device (existing flow)
  - Cloud path: pass sparkId to VoiceAgentService, execute, update spark on completion
- Remove activity/asks endpoints (moved to SparkController)

### SparkController (service-spark)
- Remove `POST /api/sparks` (createSpark endpoint)
- Remove `PUT /api/sparks/{id}` (updateSpark endpoint)
- Add `GET /api/sparks/activity` (from AgentController)
- Keep: GET list, GET detail, DELETE, cancel, run, tactics, logs

### SparkService (business-agent)
- Add `markRunning(sparkId)` for cloud execution tracking
- Add `markCompleted(sparkId, totalTokens, modelId)` with cost estimation
- Add `markFailed(sparkId, errorMessage)` (already exists as onSparkFailed)
- Remove `updateSpark()` method (no manual editing)
- Activity broadcast payload updated to use spark fields

### DeviceCommandService (business-agent)
- Replace askId/taskId/agentId references with sparkId
- Read SparkContext instead of AskContext

### DeviceCommand entity (data-social)
- Replace askId, taskId, agentId fields with sparkId

### ActivityResponse DTO (service-agent)
- Rename activeAsks/recentAsks to activeSparks/recentSparks
- Payload structure uses spark fields

## SparkState (unchanged)

```
PENDING -> SCHEDULED (if cron schedule)
PENDING -> ROUTING -> QUEUED (no device) | EXECUTING (device found)
PENDING -> EXECUTING (cloud fallback)
EXECUTING -> CHECKPOINT -> EXECUTING (after user decision)
EXECUTING -> COMPLETED | FAILED
Any -> CANCELLED
```

## What Stays The Same

- Spark entity and SparkState enum (unchanged)
- Tactic entity (device-side decomposition, unchanged)
- SparkClassifierService (auto-classification, unchanged)
- SparkDispatchService (WebSocket dispatch, unchanged)
- DeviceRoutingService (device selection, unchanged)
- Checkpoint system (unchanged)
- ExecutionLog tracking (unchanged)
- All GET/read spark endpoints (unchanged)
