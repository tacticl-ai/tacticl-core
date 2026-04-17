# Arbiter Integration Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire tacticl-core's PDLC pipeline integration to the running cidadel-ai-arbiter so events are persisted, run state stays accurate, checkpoints unblock the arbiter, and the API surface is complete.

**Architecture:** `PipelineCallbackController` becomes a thin auth gateway that delegates all logic to `PdlcV2Service`. `PdlcV2Service` gains a `handleCallbackEvent` method that persists events, drives `PipelineRun` state transitions, creates `PipelineCheckpoint` docs on demand, and fans out via `PipelineEventEmitter`. `resolveCheckpoint` gains a live gRPC call to resume the arbiter pipeline. A new `ResolveCheckpoint` RPC is added to the proto.

**Tech Stack:** Java 25, Spring Boot 4.0.3, gRPC (blocking stub + deadline), MongoDB (Spring Data), Jackson 3 (`tools.jackson.*`), JUnit 6 + Mockito, AssertJ

---

## Chunk 1: ResolveCheckpoint RPC

### Task 1: Add ResolveCheckpoint to proto

**Files:**
- Modify: `client/client-ai-arbiter/src/main/proto/cidadel/ai/arbiter/pipeline/v1/arbiter_pipeline.proto`

- [ ] **Step 1: Write the failing compilation test**

```bash
./gradlew :client:client-ai-arbiter:compileJava 2>&1 | grep "ResolveCheckpoint"
```
Expected: no match (RPC doesn't exist yet)

- [ ] **Step 2: Add ResolveCheckpoint to the proto**

Add after the `GetPipelineResult` RPC in the service definition, and add the two messages at the bottom of the file:

```protobuf
service ArbiterPipelineService {
  rpc SubmitPipeline(SubmitPipelineRequest) returns (SubmitPipelineResponse);
  rpc StreamPipelineProgress(PipelineProgressRequest) returns (stream PipelineProgressEvent);
  rpc CancelPipeline(CancelPipelineRequest) returns (CancelPipelineResponse);
  rpc GetPipelineResult(GetPipelineResultRequest) returns (GetPipelineResultResponse);
  rpc ResolveCheckpoint(ResolveCheckpointRequest) returns (ResolveCheckpointResponse);
}
```

Add at the end of the file:

```protobuf
message ResolveCheckpointRequest {
  string pipeline_id = 1;
  string checkpoint_id = 2;
  string decision = 3;
  string feedback = 4;
}

message ResolveCheckpointResponse {
  bool accepted = 1;
}
```

- [ ] **Step 3: Regenerate stubs**

```bash
./gradlew :client:client-ai-arbiter:generateProto
```
Expected: BUILD SUCCESSFUL, generated `ResolveCheckpointRequest.java` + `ResolveCheckpointResponse.java`

- [ ] **Step 4: Verify generated stubs include the new RPC**

```bash
./gradlew :client:client-ai-arbiter:compileJava
grep -r "resolveCheckpoint" client/client-ai-arbiter/build/generated
```
Expected: method present in generated `ArbiterPipelineServiceGrpc.java`

- [ ] **Step 5: Commit**

```bash
git add client/client-ai-arbiter/src/main/proto/
git commit -m "feat(arbiter-proto): add ResolveCheckpoint RPC"
```

---

### Task 2: Implement resolveCheckpoint in client + interface

**Files:**
- Modify: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/ArbiterPipelineService.java`
- Modify: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/ArbiterGrpcClientImpl.java`
- Modify: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/ArbiterPipelineServiceStub.java`
- Test: `client/client-ai-arbiter/src/test/java/io/tacticl/client/arbiter/ArbiterGrpcClientImplTest.java`

- [ ] **Step 1: Write failing test for resolveCheckpoint**

Create `client/client-ai-arbiter/src/test/java/io/tacticl/client/arbiter/ArbiterGrpcClientImplTest.java`:

```java
package io.tacticl.client.arbiter;

import cidadel.ai.arbiter.pipeline.v1.ArbiterPipelineServiceGrpc;
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointRequest;
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArbiterGrpcClientImplTest {

    @Mock ArbiterPipelineServiceGrpc.ArbiterPipelineServiceBlockingStub stub;

    ArbiterGrpcClientImpl client;

    @BeforeEach
    void setUp() {
        client = new ArbiterGrpcClientImpl(stub, "gs://tacticl/registry");
    }

    @Test
    void resolveCheckpoint_sendsCorrectProtoRequest() {
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.resolveCheckpoint(any())).thenReturn(
            ResolveCheckpointResponse.newBuilder().setAccepted(true).build()
        );

        client.resolveCheckpoint("arb-pipeline-1", "cp-111", "APPROVED", "looks good");

        ArgumentCaptor<ResolveCheckpointRequest> captor =
            ArgumentCaptor.forClass(ResolveCheckpointRequest.class);
        verify(stub).resolveCheckpoint(captor.capture());
        assertThat(captor.getValue().getPipelineId()).isEqualTo("arb-pipeline-1");
        assertThat(captor.getValue().getCheckpointId()).isEqualTo("cp-111");
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVED");
        assertThat(captor.getValue().getFeedback()).isEqualTo("looks good");
    }

    @Test
    void resolveCheckpoint_nullFeedback_sendsEmptyString() {
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.resolveCheckpoint(any())).thenReturn(
            ResolveCheckpointResponse.newBuilder().setAccepted(true).build()
        );

        client.resolveCheckpoint("arb-pipeline-1", "cp-111", "REWORK", null);

        ArgumentCaptor<ResolveCheckpointRequest> captor =
            ArgumentCaptor.forClass(ResolveCheckpointRequest.class);
        verify(stub).resolveCheckpoint(captor.capture());
        assertThat(captor.getValue().getFeedback()).isEqualTo("");
    }
}
```

- [ ] **Step 2: Run test — expect compile failure** (method doesn't exist yet)

```bash
./gradlew :client:client-ai-arbiter:test 2>&1 | tail -20
```

- [ ] **Step 3: Add resolveCheckpoint to ArbiterPipelineService interface**

```java
// Add to ArbiterPipelineService.java:
void resolveCheckpoint(String arbiterPipelineId, String checkpointId,
                       String decision, String feedback);
```

- [ ] **Step 4: Implement in ArbiterGrpcClientImpl**

Add the following imports to `ArbiterGrpcClientImpl.java`:
```java
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointRequest;
import java.util.concurrent.TimeUnit;
```

Add the method and update `submitPipeline` + `cancelPipeline` to use deadlines:

```java
@Override
public void resolveCheckpoint(String arbiterPipelineId, String checkpointId,
                              String decision, String feedback) {
    ResolveCheckpointRequest protoReq = ResolveCheckpointRequest.newBuilder()
        .setPipelineId(arbiterPipelineId)
        .setCheckpointId(checkpointId)
        .setDecision(decision)
        .setFeedback(feedback != null ? feedback : "")
        .build();
    log.info("Resolving checkpoint {} in arbiter pipeline {}: decision={}",
             checkpointId, arbiterPipelineId, decision);
    stub.withDeadlineAfter(10, TimeUnit.SECONDS).resolveCheckpoint(protoReq);
}
```

Also update `submitPipeline` line 54 and `cancelPipeline` line 66 to use 10s deadline:
```java
// submitPipeline — replace: stub.submitPipeline(protoReq)
stub.withDeadlineAfter(10, TimeUnit.SECONDS).submitPipeline(protoReq)

// cancelPipeline — replace: stub.cancelPipeline(protoReq)
stub.withDeadlineAfter(10, TimeUnit.SECONDS).cancelPipeline(protoReq)
```

- [ ] **Step 5: Add no-op to ArbiterPipelineServiceStub**

```java
// Add to ArbiterPipelineServiceStub.java:
@Override
public void resolveCheckpoint(String arbiterPipelineId, String checkpointId,
                              String decision, String feedback) {
    log.info("[stub] resolveCheckpoint no-op: pipelineId={} checkpointId={}", 
             arbiterPipelineId, checkpointId);
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
./gradlew :client:client-ai-arbiter:test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add client/client-ai-arbiter/src/
git commit -m "feat(arbiter-client): implement resolveCheckpoint RPC + add 10s deadlines to all stub calls"
```

---

## Chunk 2: PipelineRun entity fixes

### Task 3: Fix PipelineRun.pauseAtCheckpoint and add phase/role updaters

**Files:**
- Modify: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PipelineRun.java`
- Test: `data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PipelineRunTest.java`

- [ ] **Step 1: Write failing tests**

Create `data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PipelineRunTest.java`:

```java
package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineRunTest {

    private PipelineRun run() {
        return PipelineRun.create("user-1", "spark-1", "req", "repo",
                                  "FULL_PDLC", List.of(), 100.0);
    }

    @Test
    void pauseAtCheckpoint_setsPhaseCheckpoint() {
        PipelineRun run = run();

        run.pauseAtCheckpoint("cp-1", "PRODUCT");

        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PAUSED_AT_CHECKPOINT);
        assertThat(run.getCurrentCheckpointId()).isEqualTo("cp-1");
        assertThat(run.getPhases()).containsKey("PRODUCT");
        assertThat(run.getPhases().get("PRODUCT").getCheckpointId()).isEqualTo("cp-1");
    }

    @Test
    void markRoleStarted_createsPhaseAndRoleIfAbsent() {
        PipelineRun run = run();

        run.markRoleStarted("PRODUCT", "PM");

        assertThat(run.getPhases()).containsKey("PRODUCT");
        assertThat(run.getPhases().get("PRODUCT").getRoles()).containsKey("PM");
        assertThat(run.getPhases().get("PRODUCT").getRoles().get("PM").getStatus())
            .isEqualTo("RUNNING");
        assertThat(run.getPhases().get("PRODUCT").getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void markRoleCompleted_updatesRoleCostAndRunCost() {
        PipelineRun run = run();
        run.markRoleStarted("PRODUCT", "PM");

        run.markRoleCompleted("PRODUCT", "PM", 2.10);

        assertThat(run.getPhases().get("PRODUCT").getRoles().get("PM").getCostUsd())
            .isEqualTo(2.10);
        assertThat(run.getTotalCostUsd()).isEqualTo(2.10);
        assertThat(run.getPhases().get("PRODUCT").getRoles().get("PM").getStatus())
            .isEqualTo("COMPLETED");
    }

    @Test
    void markRoleRework_incrementsReworkCount() {
        PipelineRun run = run();
        run.markRoleStarted("DEVELOPMENT", "IMPLEMENTER");
        run.markRoleCompleted("DEVELOPMENT", "IMPLEMENTER", 5.0);

        run.markRoleRework("DEVELOPMENT", "IMPLEMENTER");

        assertThat(run.getPhases().get("DEVELOPMENT").getRoles().get("IMPLEMENTER").getReworkCount())
            .isEqualTo(1);
    }

    @Test
    void setArtifact_storesArtifactByKey() {
        PipelineRun run = run();

        run.setArtifact("phase1Prd", "path/to/prd.md");

        assertThat(run.getArtifacts()).containsEntry("phase1Prd", "path/to/prd.md");
    }
}
```

- [ ] **Step 2: Run tests — expect failures**

```bash
./gradlew :data:data-pipeline:test
```
Expected: FAIL — `markRoleStarted`, `markRoleCompleted`, `markRoleRework`, `setArtifact` do not exist; `pauseAtCheckpoint` doesn't update phases

- [ ] **Step 3: Fix pauseAtCheckpoint and add updater methods to PipelineRun**

In `PipelineRun.java`, replace `pauseAtCheckpoint` and add four new methods:

```java
public void pauseAtCheckpoint(String checkpointId, String phase) {
    this.status = PipelineStatus.PAUSED_AT_CHECKPOINT;
    this.currentCheckpointId = checkpointId;
    this.updatedAt = Instant.now();
    if (phase != null) {
        phases.computeIfAbsent(phase, k -> PhaseState.pending()).setCheckpoint(checkpointId);
    }
}

public void markRoleStarted(String phase, String role) {
    PhaseState phaseState = phases.computeIfAbsent(phase, k -> PhaseState.pending());
    if (!"RUNNING".equals(phaseState.getStatus())) phaseState.markRunning();
    phaseState.getRoles().computeIfAbsent(role, k -> RoleState.pending()).markRunning();
    this.updatedAt = Instant.now();
}

public void markRoleCompleted(String phase, String role, double costUsd) {
    PhaseState phaseState = phases.computeIfAbsent(phase, k -> PhaseState.pending());
    phaseState.getRoles().computeIfAbsent(role, k -> RoleState.pending()).markCompleted(costUsd);
    this.totalCostUsd += costUsd;
    this.updatedAt = Instant.now();
}

public void markRoleRework(String phase, String role) {
    PhaseState phaseState = phases.computeIfAbsent(phase, k -> PhaseState.pending());
    phaseState.getRoles().computeIfAbsent(role, k -> RoleState.pending()).incrementRework();
    this.updatedAt = Instant.now();
}

public void setArtifact(String key, String path) {
    artifacts.put(key, path);
    this.updatedAt = Instant.now();
}
```

Note: the existing `addCost` method is now redundant for role completions since `markRoleCompleted` adds cost directly. Keep `addCost` — it's still used by the existing path.

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :data:data-pipeline:test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add data/data-pipeline/src/
git commit -m "feat(pipeline-entity): fix pauseAtCheckpoint phase tracking, add markRole*/setArtifact methods"
```

---

## Chunk 3: Callback event processing

### Task 4: Add handleCallbackEvent to PdlcV2Service

**Files:**
- Modify: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PdlcV2Service.java`
- Create: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PdlcV2ServiceCallbackTest.java`

- [ ] **Step 1: Write failing tests**

Create `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PdlcV2ServiceCallbackTest.java`:

```java
package io.tacticl.business.pipeline.service;

import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.data.pipeline.entity.*;
import io.tacticl.data.pipeline.repository.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import io.tacticl.service.pipeline.dto.PipelineCallbackEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdlcV2ServiceCallbackTest {

    @Mock PipelineRunRepository pipelineRunRepository;
    @Mock PipelineEventRepository pipelineEventRepository;
    @Mock PipelineCheckpointRepository pipelineCheckpointRepository;
    @Mock SparkRepository sparkRepository;
    @Mock ArbiterPipelineService arbiterPipelineService;
    @Mock PipelineEventEmitter pipelineEventEmitter;

    PdlcV2Service service;

    @BeforeEach
    void setUp() {
        service = new PdlcV2Service(
            pipelineRunRepository, pipelineEventRepository,
            pipelineCheckpointRepository, sparkRepository,
            arbiterPipelineService, pipelineEventEmitter,
            "https://callback.tacticl.ai/v1/internal/pipeline/callback"
        );
    }

    private PipelineRun pendingRun(String id) {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "do stuff",
                                             "github.com/u/repo", "FULL_PDLC", List.of(), 100.0);
        // Hack: set the id via reflection-free setId (it has a setter)
        run.setId(id);
        run.setArbiterPipelineId("arb-1");
        return run;
    }

    @Test
    void handleCallbackEvent_alwaysPersistsEvent() {
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.empty());

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "run-1", "ROLE_COMPLETED", "PM", "PRODUCT", "{\"costUsd\":2.1}"));

        verify(pipelineEventRepository).save(any(PipelineEvent.class));
    }

    @Test
    void handleCallbackEvent_pipelineStarted_marksRunning() {
        PipelineRun run = pendingRun("run-1");
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "run-1", "PIPELINE_STARTED", null, null, "{}"));

        assertThat(run.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        verify(pipelineRunRepository).save(run);
    }

    @Test
    void handleCallbackEvent_roleStarted_updatesPhaseAndRole() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "run-1", "ROLE_STARTED", "PM", "PRODUCT", "{}"));

        assertThat(run.getPhases()).containsKey("PRODUCT");
        assertThat(run.getPhases().get("PRODUCT").getRoles()).containsKey("PM");
        assertThat(run.getPhases().get("PRODUCT").getRoles().get("PM").getStatus())
            .isEqualTo("RUNNING");
        verify(pipelineRunRepository).save(run);
    }

    @Test
    void handleCallbackEvent_roleCompleted_updatesCostAndRole() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        run.markRoleStarted("PRODUCT", "PM");
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "run-1", "ROLE_COMPLETED", "PM", "PRODUCT", "{\"costUsd\":2.1}"));

        assertThat(run.getTotalCostUsd()).isEqualTo(2.1);
        assertThat(run.getPhases().get("PRODUCT").getRoles().get("PM").getStatus())
            .isEqualTo("COMPLETED");
    }

    @Test
    void handleCallbackEvent_checkpointRequested_createsCheckpointAndPausesRun() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "run-1", "CHECKPOINT_REQUESTED", null, "PRODUCT",
            "{\"checkpointId\":\"cp-111\",\"type\":\"PHASE_COMPLETE\",\"artifactPaths\":{\"tier1\":\"prd.md\"}}"));

        ArgumentCaptor<PipelineCheckpoint> cpCaptor =
            ArgumentCaptor.forClass(PipelineCheckpoint.class);
        verify(pipelineCheckpointRepository).save(cpCaptor.capture());
        assertThat(cpCaptor.getValue().getPhase()).isEqualTo("PRODUCT");
        assertThat(cpCaptor.getValue().getType()).isEqualTo("PHASE_COMPLETE");
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PAUSED_AT_CHECKPOINT);
    }

    @Test
    void handleCallbackEvent_pipelineCompleted_marksCompletedAndClosesSSE() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "run-1", "PIPELINE_COMPLETED", null, null, "{\"totalCostUsd\":47.5}"));

        assertThat(run.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
        verify(pipelineRunRepository).save(run);
        verify(pipelineEventEmitter).completeAll("run-1");
    }

    @Test
    void handleCallbackEvent_pipelineFailed_marksFailedWithReason() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "run-1", "PIPELINE_FAILED", null, null, "{\"reason\":\"IMPLEMENTER exceeded max rework\"}"));

        assertThat(run.getStatus()).isEqualTo(PipelineStatus.FAILED);
        assertThat(run.getFailureReason()).isEqualTo("IMPLEMENTER exceeded max rework");
        verify(pipelineEventEmitter).completeAll("run-1");
    }

    @Test
    void handleCallbackEvent_unknownRunId_doesNotThrow() {
        when(pipelineRunRepository.findById("unknown")).thenReturn(Optional.empty());

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "unknown", "ROLE_COMPLETED", "PM", "PRODUCT", "{}"));

        verify(pipelineEventRepository).save(any());
        verify(pipelineRunRepository, never()).save(any());
    }

    @Test
    void handleCallbackEvent_alwaysEmitsToSse() {
        PipelineRun run = pendingRun("run-1");
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));

        service.handleCallbackEvent(new PipelineCallbackEvent(
            "run-1", "ROLE_STARTED", "PM", "PRODUCT", "{}"));

        verify(pipelineEventEmitter).emit("run-1", "ROLE_STARTED", "{}");
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./gradlew :business:business-pipeline:test 2>&1 | tail -20
```
Expected: `handleCallbackEvent` not found on `PdlcV2Service`; constructor mismatch (`PipelineEventEmitter` not in constructor)

- [ ] **Step 3: Add PipelineEventEmitter to PdlcV2Service constructor**

In `PdlcV2Service.java`, add the import and field:

```java
import io.tacticl.business.pipeline.service.PipelineEventEmitter;
```

Add field after `callbackUrl`:
```java
private final PipelineEventEmitter pipelineEventEmitter;
```

Update constructor signature and body (add `PipelineEventEmitter pipelineEventEmitter` as last param before `callbackUrl`):

```java
public PdlcV2Service(PipelineRunRepository pipelineRunRepository,
                     PipelineEventRepository pipelineEventRepository,
                     PipelineCheckpointRepository pipelineCheckpointRepository,
                     SparkRepository sparkRepository,
                     ArbiterPipelineService arbiterPipelineService,
                     PipelineEventEmitter pipelineEventEmitter,
                     @Value("${pdlc.v2.callback-url:https://api.tacticl.ai/v1/internal/pipeline/callback}")
                     String callbackUrl) {
    this.pipelineRunRepository = pipelineRunRepository;
    this.pipelineEventRepository = pipelineEventRepository;
    this.pipelineCheckpointRepository = pipelineCheckpointRepository;
    this.sparkRepository = sparkRepository;
    this.arbiterPipelineService = arbiterPipelineService;
    this.pipelineEventEmitter = pipelineEventEmitter;
    this.callbackUrl = callbackUrl;
}
```

- [ ] **Step 4: Add handleCallbackEvent method to PdlcV2Service**

Add the following imports at the top of `PdlcV2Service.java`:

```java
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.tacticl.service.pipeline.dto.PipelineCallbackEvent;
import java.util.HashMap;
import java.util.Map;
```

Add a `JsonMapper` field (initialized in constructor or as a static final):

```java
private static final JsonMapper MAPPER = JsonMapper.builder().build();
```

Add the method and helpers:

```java
public void handleCallbackEvent(PipelineCallbackEvent event) {
    pipelineEventRepository.save(PipelineEvent.create(
        event.pipelineRunId(), event.eventType(), event.role(), event.phase(), event.payloadJson()
    ));

    pipelineRunRepository.findById(event.pipelineRunId()).ifPresentOrElse(
        run -> processRunEvent(run, event),
        () -> log.warn("Received callback for unknown pipelineRunId={} eventType={}",
                       event.pipelineRunId(), event.eventType())
    );

    pipelineEventEmitter.emit(event.pipelineRunId(), event.eventType(), event.payloadJson());

    if (isTerminalEvent(event.eventType())) {
        pipelineEventEmitter.completeAll(event.pipelineRunId());
    }
}

private void processRunEvent(PipelineRun run, PipelineCallbackEvent event) {
    JsonNode payload = parsePayload(event.payloadJson());
    switch (event.eventType()) {
        case "PIPELINE_STARTED" -> {
            run.markRunning();
            pipelineRunRepository.save(run);
        }
        case "ROLE_STARTED" -> {
            run.markRoleStarted(event.phase(), event.role());
            pipelineRunRepository.save(run);
        }
        case "ROLE_COMPLETED" -> {
            double cost = payload.path("costUsd").asDouble(0.0);
            run.markRoleCompleted(event.phase(), event.role(), cost);
            String artifactPath = payload.path("artifactPath").asText(null);
            if (artifactPath != null && !artifactPath.isBlank()) {
                run.setArtifact(event.phase() + "_" + event.role(), artifactPath);
            }
            pipelineRunRepository.save(run);
        }
        case "ROLE_REWORK" -> {
            run.markRoleRework(event.phase(), event.role());
            pipelineRunRepository.save(run);
        }
        case "CHECKPOINT_REQUESTED" -> handleCheckpointRequested(run, event, payload);
        case "PIPELINE_COMPLETED" -> {
            run.markCompleted();
            pipelineRunRepository.save(run);
        }
        case "PIPELINE_FAILED" -> {
            String reason = payload.path("reason").asText("Unknown failure");
            run.markFailed(reason);
            pipelineRunRepository.save(run);
        }
        case "PIPELINE_CANCELLED" -> {
            run.markCancelled();
            pipelineRunRepository.save(run);
        }
        default -> log.debug("Unhandled pipeline event type={} for run={}",
                             event.eventType(), run.getId());
    }
}

private void handleCheckpointRequested(PipelineRun run, PipelineCallbackEvent event, JsonNode payload) {
    String type = payload.path("type").asText("PHASE_COMPLETE");
    Map<String, String> artifactPaths = new HashMap<>();
    JsonNode paths = payload.path("artifactPaths");
    if (paths.isObject()) {
        paths.fields().forEachRemaining(e -> artifactPaths.put(e.getKey(), e.getValue().asText()));
    }

    PipelineCheckpoint cp = PipelineCheckpoint.create(
        run.getId(), run.getSparkId(), event.phase(), type, artifactPaths
    );
    String hitlUrl = payload.path("hitlUrl").asText(null);
    if (hitlUrl != null && !hitlUrl.isBlank()) cp.setHitlUrl(hitlUrl);
    pipelineCheckpointRepository.save(cp);

    run.pauseAtCheckpoint(cp.getId(), event.phase());
    pipelineRunRepository.save(run);
    log.info("Created checkpoint {} for run {} phase={}", cp.getId(), run.getId(), event.phase());
}

private JsonNode parsePayload(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) return MAPPER.createObjectNode();
    try {
        return MAPPER.readTree(payloadJson);
    } catch (Exception e) {
        log.warn("Failed to parse callback payloadJson: {}", e.getMessage());
        return MAPPER.createObjectNode();
    }
}

private boolean isTerminalEvent(String eventType) {
    return "PIPELINE_COMPLETED".equals(eventType)
        || "PIPELINE_FAILED".equals(eventType)
        || "PIPELINE_CANCELLED".equals(eventType);
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
./gradlew :business:business-pipeline:test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add business/business-pipeline/src/
git commit -m "feat(pipeline-service): add handleCallbackEvent — persists events, drives run state, creates checkpoints, fans out SSE"
```

---

### Task 5: Refactor PipelineCallbackController to delegate + add auth

**Files:**
- Modify: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/controller/PipelineCallbackController.java`
- Modify: `service/service-pipeline/src/test/java/io/tacticl/service/pipeline/controller/PipelineCallbackControllerTest.java`

- [ ] **Step 1: Rewrite the controller test**

Replace the full contents of `PipelineCallbackControllerTest.java`:

```java
package io.tacticl.service.pipeline.controller;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.service.pipeline.dto.PipelineCallbackEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineCallbackControllerTest {

    private static final PipelineCallbackEvent ROLE_COMPLETED_EVENT =
        new PipelineCallbackEvent("run-1", "ROLE_COMPLETED", "PM", "PRODUCT", "{}");

    @Test
    void handleCallback_noSecret_delegatesToService() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        PipelineCallbackController controller = new PipelineCallbackController(service, "");

        ResponseEntity<Void> resp = controller.handleCallback(null, ROLE_COMPLETED_EVENT);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).handleCallbackEvent(ROLE_COMPLETED_EVENT);
    }

    @Test
    void handleCallback_correctSecret_delegatesToService() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        PipelineCallbackController controller = new PipelineCallbackController(service, "super-secret");

        ResponseEntity<Void> resp = controller.handleCallback("super-secret", ROLE_COMPLETED_EVENT);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).handleCallbackEvent(ROLE_COMPLETED_EVENT);
    }

    @Test
    void handleCallback_wrongSecret_returns401() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        PipelineCallbackController controller = new PipelineCallbackController(service, "super-secret");

        ResponseEntity<Void> resp = controller.handleCallback("wrong", ROLE_COMPLETED_EVENT);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void handleCallback_secretConfigured_missingHeader_returns401() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        PipelineCallbackController controller = new PipelineCallbackController(service, "super-secret");

        ResponseEntity<Void> resp = controller.handleCallback(null, ROLE_COMPLETED_EVENT);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }
}
```

- [ ] **Step 2: Run tests — expect failure** (old controller doesn't match new expectations)

```bash
./gradlew :service:service-pipeline:test 2>&1 | tail -20
```

- [ ] **Step 3: Rewrite PipelineCallbackController**

Replace the full contents of `PipelineCallbackController.java`:

```java
package io.tacticl.service.pipeline.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.service.pipeline.dto.PipelineCallbackEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint — receives HTTP push events from cidadel-ai-arbiter.
 * Protected by VPC firewall rules (Hetzner IP range only).
 * Optionally protected by shared secret via X-Arbiter-Secret header.
 */
@RestController
@RequestMapping("/v1/internal/pipeline")
public class PipelineCallbackController extends BaseController {

    @Override
    protected String getModuleName() { return "pipeline-callback"; }

    private final PdlcV2Service pdlcV2Service;
    private final String callbackSecret;

    public PipelineCallbackController(
            PdlcV2Service pdlcV2Service,
            @Value("${pdlc.v2.callback.secret:}") String callbackSecret) {
        this.pdlcV2Service = pdlcV2Service;
        this.callbackSecret = callbackSecret;
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestHeader(value = "X-Arbiter-Secret", required = false) String incomingSecret,
            @RequestBody PipelineCallbackEvent event) {
        if (!callbackSecret.isBlank() && !callbackSecret.equals(incomingSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        pdlcV2Service.handleCallbackEvent(event);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :service:service-pipeline:test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add service/service-pipeline/src/
git commit -m "feat(pipeline-callback): delegate to PdlcV2Service, add shared-secret auth via X-Arbiter-Secret"
```

---

## Chunk 4: Checkpoint resolution

### Task 6: Fix resolveCheckpoint to call arbiter

**Files:**
- Modify: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PdlcV2Service.java`
- Create: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PdlcV2ServiceCheckpointTest.java`

- [ ] **Step 1: Write failing tests**

Create `PdlcV2ServiceCheckpointTest.java`:

```java
package io.tacticl.business.pipeline.service;

import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.data.pipeline.entity.*;
import io.tacticl.data.pipeline.repository.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdlcV2ServiceCheckpointTest {

    @Mock PipelineRunRepository pipelineRunRepository;
    @Mock PipelineEventRepository pipelineEventRepository;
    @Mock PipelineCheckpointRepository pipelineCheckpointRepository;
    @Mock SparkRepository sparkRepository;
    @Mock ArbiterPipelineService arbiterPipelineService;
    @Mock PipelineEventEmitter pipelineEventEmitter;

    PdlcV2Service service;

    @BeforeEach
    void setUp() {
        service = new PdlcV2Service(
            pipelineRunRepository, pipelineEventRepository,
            pipelineCheckpointRepository, sparkRepository,
            arbiterPipelineService, pipelineEventEmitter,
            "https://callback.url"
        );
    }

    private PipelineRun pausedRun() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req",
                                             "repo", "FULL_PDLC", List.of(), 100.0);
        run.setId("run-1");
        run.setArbiterPipelineId("arb-1");
        run.markRunning();
        run.pauseAtCheckpoint("cp-1", "PRODUCT");
        return run;
    }

    private PipelineCheckpoint pendingCheckpoint() {
        return PipelineCheckpoint.create("run-1", "spark-1", "PRODUCT",
                                         "PHASE_COMPLETE", Map.of());
    }

    @Test
    void resolveCheckpoint_approved_callsArbiterAndResumesRun() {
        PipelineRun run = pausedRun();
        PipelineCheckpoint cp = pendingCheckpoint();
        cp.setId("cp-1");

        when(pipelineRunRepository.findBySparkIdAndUserId("spark-1", "user-1"))
            .thenReturn(Optional.of(run));
        when(pipelineCheckpointRepository.findByIdAndPipelineRunId("cp-1", "run-1"))
            .thenReturn(Optional.of(cp));

        service.resolveCheckpoint("user-1", "spark-1", "cp-1", CheckpointDecision.APPROVED, null);

        assertThat(cp.getStatus()).isEqualTo("RESOLVED");
        assertThat(cp.getDecision()).isEqualTo("APPROVED");
        verify(pipelineCheckpointRepository).save(cp);
        verify(arbiterPipelineService).resolveCheckpoint("arb-1", "cp-1", "APPROVED", null);
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        assertThat(run.getCurrentCheckpointId()).isNull();
        verify(pipelineRunRepository).save(run);
    }

    @Test
    void resolveCheckpoint_rework_callsArbiterWithFeedback() {
        PipelineRun run = pausedRun();
        PipelineCheckpoint cp = pendingCheckpoint();
        cp.setId("cp-1");

        when(pipelineRunRepository.findBySparkIdAndUserId("spark-1", "user-1"))
            .thenReturn(Optional.of(run));
        when(pipelineCheckpointRepository.findByIdAndPipelineRunId("cp-1", "run-1"))
            .thenReturn(Optional.of(cp));

        service.resolveCheckpoint("user-1", "spark-1", "cp-1",
                                  CheckpointDecision.REWORK, "add more edge case tests");

        verify(arbiterPipelineService).resolveCheckpoint("arb-1", "cp-1", "REWORK",
                                                          "add more edge case tests");
    }

    @Test
    void resolveCheckpoint_noArbiterPipelineId_skipsArbiterCall() {
        PipelineRun run = pausedRun();
        run.setArbiterPipelineId(null);
        PipelineCheckpoint cp = pendingCheckpoint();
        cp.setId("cp-1");

        when(pipelineRunRepository.findBySparkIdAndUserId("spark-1", "user-1"))
            .thenReturn(Optional.of(run));
        when(pipelineCheckpointRepository.findByIdAndPipelineRunId("cp-1", "run-1"))
            .thenReturn(Optional.of(cp));

        service.resolveCheckpoint("user-1", "spark-1", "cp-1", CheckpointDecision.APPROVED, null);

        verifyNoInteractions(arbiterPipelineService);
    }

    @Test
    void resolveCheckpoint_unknownSpark_throws() {
        when(pipelineRunRepository.findBySparkIdAndUserId("spark-x", "user-1"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.resolveCheckpoint("user-1", "spark-x", "cp-1", CheckpointDecision.APPROVED, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run tests — expect failure** (resolveCheckpoint doesn't call arbiter)

```bash
./gradlew :business:business-pipeline:test --tests "*.PdlcV2ServiceCheckpointTest" 2>&1 | tail -20
```

- [ ] **Step 3: Fix resolveCheckpoint in PdlcV2Service**

Replace the `resolveCheckpoint` method (lines 92–104):

```java
public void resolveCheckpoint(String userId, String sparkId, String checkpointId,
                              CheckpointDecision decision, String feedback) {
    PipelineRun run = pipelineRunRepository.findBySparkIdAndUserId(sparkId, userId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Pipeline run not found for spark: " + sparkId));

    PipelineCheckpoint checkpoint = pipelineCheckpointRepository
            .findByIdAndPipelineRunId(checkpointId, run.getId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Checkpoint not found: " + checkpointId));

    checkpoint.resolve(decision, feedback);
    pipelineCheckpointRepository.save(checkpoint);

    if (run.getArbiterPipelineId() != null) {
        arbiterPipelineService.resolveCheckpoint(
            run.getArbiterPipelineId(), checkpointId, decision.name(), feedback
        );
    }

    run.resumeFromCheckpoint();
    pipelineRunRepository.save(run);
    log.info("Resolved checkpoint {} for run {} with decision {}", checkpointId, run.getId(), decision);
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :business:business-pipeline:test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add business/business-pipeline/src/
git commit -m "feat(pipeline-service): resolveCheckpoint now calls arbiter gRPC to unblock pipeline"
```

---

## Chunk 5: API surface

### Task 7: Add cancel endpoint to PipelineController

**Files:**
- Modify: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/controller/PipelineController.java`
- Modify: `service/service-pipeline/src/test/java/io/tacticl/service/pipeline/controller/PipelineControllerTest.java`

- [ ] **Step 1: Write failing test**

Read `PipelineControllerTest.java` first, then add:

```java
@Test
void cancelPipeline_returns200() {
    // arrange: mock user + service
    when(pdlcV2Service.getStatus(anyString(), eq("spark-1")))
        .thenReturn(Optional.of(somePipelineRun()));

    // act
    ResponseEntity<Void> resp = controller.cancelPipeline(mockUser("user-1"), "spark-1");

    // assert
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(pdlcV2Service).cancelPipeline("user-1", "spark-1");
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
./gradlew :service:service-pipeline:test --tests "*.PipelineControllerTest.cancelPipeline*" 2>&1 | tail -10
```

- [ ] **Step 3: Add cancel endpoint to PipelineController**

Add import:
```java
import org.springframework.web.bind.annotation.DeleteMapping;
```

Add method (replacing the TODO comment at line 73):

```java
@DeleteMapping
public ResponseEntity<Void> cancelPipeline(
        @AuthUser AuthenticatedUser user,
        @PathVariable String sparkId) {
    pdlcV2Service.cancelPipeline(user.getUserId(), sparkId);
    return ResponseEntity.ok().build();
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :service:service-pipeline:test
```

- [ ] **Step 5: Commit**

```bash
git add service/service-pipeline/src/
git commit -m "feat(pipeline-api): add DELETE /v1/sparks/{sparkId}/pipeline cancel endpoint"
```

---

### Task 8: Add event history endpoint

**Files:**
- Modify: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/controller/PipelineController.java`
- Modify: `service/service-pipeline/src/test/java/io/tacticl/service/pipeline/controller/PipelineControllerTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void getEventHistory_returnsPagedEvents() {
    PipelineRun run = somePipelineRun();
    when(pdlcV2Service.getStatus("user-1", "spark-1")).thenReturn(Optional.of(run));
    when(pdlcV2Service.getEvents(run.getId(), 0, 50)).thenReturn(Page.empty());

    ResponseEntity<?> resp = controller.getEventHistory(mockUser("user-1"), "spark-1", 0, 50);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
./gradlew :service:service-pipeline:test --tests "*.PipelineControllerTest.getEventHistory*" 2>&1 | tail -10
```

- [ ] **Step 3: Add history endpoint to PipelineController**

Add imports:
```java
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestParam;
```

Add method:

```java
@GetMapping("/events/history")
public ResponseEntity<Page<PipelineEventDto>> getEventHistory(
        @AuthUser AuthenticatedUser user,
        @PathVariable String sparkId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {
    PipelineRun run = pdlcV2Service.getStatus(user.getUserId(), sparkId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Pipeline run not found for spark: " + sparkId));
    return ResponseEntity.ok(pdlcV2Service.getEvents(run.getId(), page, size)
            .map(PipelineEventDto::from));
}
```

Create `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/dto/PipelineEventDto.java`:

```java
package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PipelineEvent;
import java.time.Instant;

public record PipelineEventDto(
    String id,
    String pipelineRunId,
    String eventType,
    String role,
    String phase,
    Instant timestamp,
    String payloadJson
) {
    public static PipelineEventDto from(PipelineEvent e) {
        return new PipelineEventDto(e.getId(), e.getPipelineRunId(), e.getEventType(),
                                    e.getRole(), e.getPhase(), e.getTimestamp(), e.getPayloadJson());
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :service:service-pipeline:test
```

- [ ] **Step 5: Commit**

```bash
git add service/service-pipeline/src/
git commit -m "feat(pipeline-api): add GET /v1/sparks/{sparkId}/pipeline/events/history endpoint"
```

---

## Chunk 6: Final verification

### Task 9: Full build and integration test

- [ ] **Step 1: Full build**

```bash
./gradlew build -x test
```
Expected: BUILD SUCCESSFUL — all modules compile cleanly

- [ ] **Step 2: Full test suite**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL, no failures

- [ ] **Step 3: Verify callback secret property exists in application properties**

Check that `pdlc.v2.callback.secret` is documented in `application/src/main/resources/application.yml` (or `application-qa.yml`). If not, add a commented-out placeholder:

```yaml
pdlc:
  v2:
    callback:
      secret: ${PDLC_CALLBACK_SECRET:}  # Set in Vault; must match arbiter's configured secret
```

- [ ] **Step 4: Verify proto is in sync with arbiter**

Confirm with the arbiter team that `ResolveCheckpoint` is implemented server-side. If not, `PdlcV2Service.resolveCheckpoint` will throw a gRPC `UNIMPLEMENTED` on the first real call.

- [ ] **Step 5: Final commit**

```bash
git add application/src/main/resources/
git commit -m "chore(config): document pdlc.v2.callback.secret property"
```

---

## Summary of changes

| File | Change |
|---|---|
| `arbiter_pipeline.proto` | Add `ResolveCheckpoint` RPC + messages |
| `ArbiterPipelineService` | Add `resolveCheckpoint` to interface |
| `ArbiterGrpcClientImpl` | Implement `resolveCheckpoint`; add 10s deadline to all calls |
| `ArbiterPipelineServiceStub` | Add no-op `resolveCheckpoint` |
| `PipelineRun` | Fix `pauseAtCheckpoint` phase tracking; add `markRoleStarted/Completed/Rework`, `setArtifact` |
| `PdlcV2Service` | Inject `PipelineEventEmitter`; add `handleCallbackEvent` + helpers; fix `resolveCheckpoint` to call arbiter + resume run |
| `PipelineCallbackController` | Remove `PipelineEventEmitter` dep; inject `PdlcV2Service`; add shared-secret auth |
| `PipelineController` | Add cancel endpoint; add event history endpoint |
| `PipelineEventDto` | New DTO for event history response |
