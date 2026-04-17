# PDLC v2 tacticl-core Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the tacticl-core side of PDLC v2 — new MongoDB data layer, arbiter client stub, pipeline business logic, REST controllers, and feature-flag routing — as specified in SAD section 12.

**Architecture:** Four new Gradle modules (`data-pipeline`, `client-ai-arbiter`, `business-pipeline`, `service-pipeline`) follow the existing layered pattern. The arbiter client is an interface + stub now (real gRPC swapped in when arbiter is ready). Feature flag `pdlc.v2.enabled` (default false) gates routing.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Spring Data MongoDB, JUnit 6, Mockito — no Testcontainers, no @WebMvcTest

**Spec references:** `docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-prd.md`, `docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-sad.md`

**Worktree:** `.worktrees/pdlc-v2` on branch `feature/pdlc-v2`

---

## Critical Conventions (read before writing any code)

- **Services**: plain `@Service`, do NOT extend BaseService
- **Constructor injection only** — never field `@Autowired`
- **Entities**: NOT extending BaseMongoEntity — manage `@Id`, `createdAt`, `updatedAt` manually (follow `Spark.java` pattern exactly)
- **Repositories**: extend `MongoRepository<T, String>`
- **Tests**: `@ExtendWith(MockitoExtension.class)` only — no Spring context, no Testcontainers
- **Jackson 3**: `tools.jackson.*` — never `com.fasterxml.jackson.databind.*`
- **AuthenticatedUser**: import `io.cidadel.framework.authorization.context.AuthenticatedUser`, annotation `@AuthUser` from `io.cidadel.framework.authorization.annotation.AuthUser`
- **Controllers**: extend BaseController from `io.cidadel.service.framework.base`
- **`Optional<T>`** for all findById queries — never null
- **Packages**: `io.tacticl.{data|client|business|service}.pipeline.*`

---

## File Structure

```
data/data-pipeline/
  build.gradle.kts
  src/main/java/io/tacticl/data/pipeline/
    entity/
      PipelineStatus.java        — enum: PENDING | RUNNING | PAUSED_AT_CHECKPOINT | COMPLETED | FAILED | CANCELLED
      PdlcRole.java              — enum: PM | RESEARCHER | ARCHITECT | DESIGNER | PLANNER | IMPLEMENTER | REVIEWER | TESTER | SECURITY_ANALYST | TECHNICAL_WRITER | DEVOPS | RETRO_ANALYST
      PdlcPhase.java             — enum: PRODUCT | DESIGN | DEVELOPMENT | QUALITY | DEPLOY
      CheckpointDecision.java    — enum: APPROVED | REWORK | CANCEL
      KnowledgeStatus.java       — enum: PROPOSED | APPROVED | ACTIVE | REJECTED | SUPERSEDED
      RoleState.java             — record: status, reworkCount, costUsd
      PhaseState.java            — entity class: status, startedAt, completedAt, roles map, checkpointId
      PipelineRun.java           — @Document("pipeline_runs"), full state machine
      PipelineEvent.java         — @Document("pipeline_events"), append-only event log
      PipelineCheckpoint.java    — @Document("pipeline_checkpoints"), HITL checkpoint
      AgentKnowledge.java        — @Document("agent_knowledge"), learned patterns
    repository/
      PipelineRunRepository.java
      PipelineEventRepository.java
      PipelineCheckpointRepository.java
      AgentKnowledgeRepository.java

client/client-ai-arbiter/
  build.gradle.kts
  src/main/java/io/tacticl/client/arbiter/
    ArbiterPipelineService.java     — interface
    ArbiterPipelineServiceStub.java — no-op stub (used until real gRPC ready)
    dto/
      SubmitPipelineRequest.java    — record
      SubmitPipelineResponse.java   — record
      ResolveCheckpointRequest.java — record
      PipelineStatusResponse.java   — record

business/business-pipeline/
  build.gradle.kts
  src/main/java/io/tacticl/business/pipeline/
    service/
      PdlcV2Service.java          — submits to arbiter stub, manages PipelineRun state
      PipelineEventEmitter.java   — SSE fan-out (identical pattern to SparkEventEmitter)
    router/
      PdlcRouter.java             — checks pdlc.v2.enabled, routes to PdlcV2Service

service/service-pipeline/
  build.gradle.kts
  src/main/java/io/tacticl/service/pipeline/
    controller/
      PipelineController.java         — GET/POST /v1/sparks/{sparkId}/pipeline/**
      PipelineCallbackController.java — POST /v1/internal/pipeline/callback
    dto/
      PipelineRunDto.java          — record
      PipelineEventDto.java        — record
      ResolveCheckpointDto.java    — record
      PipelineCallbackEvent.java   — record (from arbiter)
```

---

## Chunk 1: Foundation + Data Layer

### Task 1: Module Scaffold

**Files:**
- Modify: `settings.gradle.kts`
- Create: `data/data-pipeline/build.gradle.kts`
- Create: `client/client-ai-arbiter/build.gradle.kts`
- Create: `business/business-pipeline/build.gradle.kts`
- Create: `service/service-pipeline/build.gradle.kts`

- [ ] **Step 1: Add modules to settings.gradle.kts**

Open `settings.gradle.kts` and add the four new modules at the bottom (before the closing):

```kotlin
include(":data:data-pipeline")
include(":client:client-ai-arbiter")
include(":business:business-pipeline")
include(":service:service-pipeline")
```

- [ ] **Step 2: Create data-pipeline/build.gradle.kts**

```kotlin
// data-pipeline — MongoDB entities + repositories for PDLC v2 pipeline state
plugins {
    `java-library`
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
```

- [ ] **Step 3: Create client-ai-arbiter/build.gradle.kts**

```kotlin
// client-ai-arbiter — Arbiter gRPC client interface + stub (real gRPC swapped in later)
plugins {
    `java-library`
}

dependencies {
    // Parent provides: exception, secrets, client-base, web, jackson, test, junit
}
```

- [ ] **Step 4: Create business-pipeline/build.gradle.kts**

```kotlin
// business-pipeline — PDLC v2 business logic: PdlcV2Service, PipelineEventEmitter, PdlcRouter
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-sparks"))
    implementation(project(":client:client-ai-arbiter"))
    // Parent provides: exception, logging, web, jackson, test, junit
}
```

- [ ] **Step 5: Create service-pipeline/build.gradle.kts**

```kotlin
// service-pipeline — REST controllers for PDLC v2 status, events, checkpoint resolution
plugins {
    `java-library`
}

dependencies {
    implementation(project(":business:business-pipeline"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":data:data-sparks"))
    implementation(libs.cidadel.service.framework.base)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 6: Create placeholder source directories so Gradle can resolve**

```bash
mkdir -p data/data-pipeline/src/main/java/io/tacticl/data/pipeline
mkdir -p data/data-pipeline/src/test/java/io/tacticl/data/pipeline
mkdir -p client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter
mkdir -p client/client-ai-arbiter/src/test/java/io/tacticl/client/arbiter
mkdir -p business/business-pipeline/src/main/java/io/tacticl/business/pipeline
mkdir -p business/business-pipeline/src/test/java/io/tacticl/business/pipeline
mkdir -p service/service-pipeline/src/main/java/io/tacticl/service/pipeline
mkdir -p service/service-pipeline/src/test/java/io/tacticl/service/pipeline
```

- [ ] **Step 7: Verify Gradle resolves the modules**

Run: `./gradlew projects 2>&1 | grep -E "(pipeline|arbiter)"`

Expected output contains:
```
+--- Project ':business:business-pipeline'
+--- Project ':client:client-ai-arbiter'
+--- Project ':data:data-pipeline'
+--- Project ':service:service-pipeline'
```

- [ ] **Step 8: Verify build passes**

Run: `./gradlew build -x test 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts data/data-pipeline/build.gradle.kts client/client-ai-arbiter/build.gradle.kts business/business-pipeline/build.gradle.kts service/service-pipeline/build.gradle.kts
git commit -m "feat(pdlc-v2): scaffold data-pipeline, client-ai-arbiter, business-pipeline, service-pipeline modules"
```

---

### Task 2: data-pipeline Entities + Repositories

**Files:**
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PipelineStatus.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PdlcRole.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PdlcPhase.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/CheckpointDecision.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/KnowledgeStatus.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/RoleState.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PhaseState.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PipelineRun.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PipelineEvent.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PipelineCheckpoint.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/AgentKnowledge.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/repository/PipelineRunRepository.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/repository/PipelineEventRepository.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/repository/PipelineCheckpointRepository.java`
- Create: `data/data-pipeline/src/main/java/io/tacticl/data/pipeline/repository/AgentKnowledgeRepository.java`
- Test: `data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PipelineRunTest.java`
- Test: `data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PipelineEventTest.java`
- Test: `data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PipelineCheckpointTest.java`

- [ ] **Step 1: Write the failing tests**

`data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PipelineRunTest.java`:
```java
package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineRunTest {

    @Test
    void create_setsRequiredFieldsAndStatus() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "Add auth flow",
                                              "github.com/user/repo", "FULL_PDLC",
                                              List.of(), 50.0);
        assertThat(run.getId()).isNotNull();
        assertThat(run.getUserId()).isEqualTo("user-1");
        assertThat(run.getSparkId()).isEqualTo("spark-1");
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PENDING);
        assertThat(run.getPlaybook()).isEqualTo("FULL_PDLC");
        assertThat(run.getCreatedAt()).isNotNull();
        assertThat(run.getTotalCostUsd()).isEqualTo(0.0);
    }

    @Test
    void markRunning_changesStatusToRunning() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "BUG_FIX", List.of(), 10.0);
        run.markRunning();
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void markCompleted_changesStatusAndSetsCompletedAt() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "BUG_FIX", List.of(), 10.0);
        run.markRunning();
        run.markCompleted();
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void markFailed_changesStatusToFailed() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "BUG_FIX", List.of(), 10.0);
        run.markFailed("Arbiter unreachable");
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.FAILED);
        assertThat(run.getFailureReason()).isEqualTo("Arbiter unreachable");
    }

    @Test
    void pauseAtCheckpoint_changesStatusAndStoresCheckpointId() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "FULL_PDLC", List.of(), 50.0);
        run.markRunning();
        run.pauseAtCheckpoint("cp-001", "PRODUCT");
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PAUSED_AT_CHECKPOINT);
        assertThat(run.getCurrentCheckpointId()).isEqualTo("cp-001");
    }

    @Test
    void addCost_accumulatesTotalCost() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "FULL_PDLC", List.of(), 50.0);
        run.addCost(5.25);
        run.addCost(3.10);
        assertThat(run.getTotalCostUsd()).isEqualTo(8.35, org.assertj.core.data.Offset.offset(0.001));
    }
}
```

`data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PipelineEventTest.java`:
```java
package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineEventTest {

    @Test
    void create_setsAllFields() {
        PipelineEvent event = PipelineEvent.create("run-1", "ROLE_COMPLETED",
                                                    "PM", "PRODUCT", "{\"cost\":2.1}");
        assertThat(event.getId()).isNotNull();
        assertThat(event.getPipelineRunId()).isEqualTo("run-1");
        assertThat(event.getEventType()).isEqualTo("ROLE_COMPLETED");
        assertThat(event.getRole()).isEqualTo("PM");
        assertThat(event.getPhase()).isEqualTo("PRODUCT");
        assertThat(event.getPayloadJson()).isEqualTo("{\"cost\":2.1}");
        assertThat(event.getTimestamp()).isNotNull();
    }
}
```

`data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PipelineCheckpointTest.java`:
```java
package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineCheckpointTest {

    @Test
    void create_isPendingWithNoDecision() {
        PipelineCheckpoint cp = PipelineCheckpoint.create("run-1", "spark-1", "PRODUCT",
                                                           "PHASE_COMPLETE", Map.of());
        assertThat(cp.getId()).isNotNull();
        assertThat(cp.getStatus()).isEqualTo("PENDING");
        assertThat(cp.getDecision()).isNull();
        assertThat(cp.getResolvedAt()).isNull();
    }

    @Test
    void resolve_approved_setsDecisionAndResolvedAt() {
        PipelineCheckpoint cp = PipelineCheckpoint.create("run-1", "spark-1", "PRODUCT",
                                                           "PHASE_COMPLETE", Map.of());
        cp.resolve(CheckpointDecision.APPROVED, null);
        assertThat(cp.getStatus()).isEqualTo("RESOLVED");
        assertThat(cp.getDecision()).isEqualTo("APPROVED");
        assertThat(cp.getResolvedAt()).isNotNull();
    }

    @Test
    void resolve_rework_storesFeedback() {
        PipelineCheckpoint cp = PipelineCheckpoint.create("run-1", "spark-1", "PRODUCT",
                                                           "PHASE_COMPLETE", Map.of());
        cp.resolve(CheckpointDecision.REWORK, "Please add acceptance criteria");
        assertThat(cp.getDecision()).isEqualTo("REWORK");
        assertThat(cp.getFeedback()).isEqualTo("Please add acceptance criteria");
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `./gradlew :data:data-pipeline:test 2>&1 | tail -20`

Expected: `FAILED` with `PipelineRun not found` or similar compile errors

- [ ] **Step 3: Implement enums**

`PipelineStatus.java`:
```java
package io.tacticl.data.pipeline.entity;

public enum PipelineStatus {
    PENDING, RUNNING, PAUSED_AT_CHECKPOINT, COMPLETED, FAILED, CANCELLED
}
```

`PdlcRole.java`:
```java
package io.tacticl.data.pipeline.entity;

public enum PdlcRole {
    PM, RESEARCHER, ARCHITECT, DESIGNER, PLANNER,
    IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST,
    TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST
}
```

`PdlcPhase.java`:
```java
package io.tacticl.data.pipeline.entity;

public enum PdlcPhase {
    PRODUCT, DESIGN, DEVELOPMENT, QUALITY, DEPLOY
}
```

`CheckpointDecision.java`:
```java
package io.tacticl.data.pipeline.entity;

public enum CheckpointDecision {
    APPROVED, REWORK, CANCEL
}
```

`KnowledgeStatus.java`:
```java
package io.tacticl.data.pipeline.entity;

public enum KnowledgeStatus {
    PROPOSED, APPROVED, ACTIVE, REJECTED, SUPERSEDED
}
```

- [ ] **Step 4: Implement RoleState + PhaseState**

`RoleState.java`:
```java
package io.tacticl.data.pipeline.entity;

public class RoleState {
    private String status;   // PENDING | RUNNING | COMPLETED | FAILED | SKIPPED
    private int reworkCount;
    private double costUsd;

    protected RoleState() {}

    public static RoleState pending() {
        RoleState rs = new RoleState();
        rs.status = "PENDING";
        rs.reworkCount = 0;
        rs.costUsd = 0.0;
        return rs;
    }

    public void markRunning() { this.status = "RUNNING"; }
    public void markCompleted(double cost) { this.status = "COMPLETED"; this.costUsd = cost; }
    public void markFailed() { this.status = "FAILED"; }
    public void markSkipped() { this.status = "SKIPPED"; }
    public void incrementRework() { this.reworkCount++; }

    public String getStatus() { return status; }
    public int getReworkCount() { return reworkCount; }
    public double getCostUsd() { return costUsd; }
    public void setStatus(String status) { this.status = status; }
    public void setReworkCount(int reworkCount) { this.reworkCount = reworkCount; }
    public void setCostUsd(double costUsd) { this.costUsd = costUsd; }
}
```

`PhaseState.java`:
```java
package io.tacticl.data.pipeline.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class PhaseState {
    private String status;   // PENDING | RUNNING | COMPLETED | FAILED
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, RoleState> roles;
    private String checkpointId;
    private String checkpointStatus;

    protected PhaseState() {}

    public static PhaseState pending() {
        PhaseState ps = new PhaseState();
        ps.status = "PENDING";
        ps.roles = new HashMap<>();
        return ps;
    }

    public void markRunning() { this.status = "RUNNING"; this.startedAt = Instant.now(); }
    public void markCompleted() { this.status = "COMPLETED"; this.completedAt = Instant.now(); }
    public void markFailed() { this.status = "FAILED"; this.completedAt = Instant.now(); }
    public void setCheckpoint(String checkpointId) {
        this.checkpointId = checkpointId;
        this.checkpointStatus = "PENDING";
    }
    public void resolveCheckpoint(String decision) { this.checkpointStatus = decision; }

    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Map<String, RoleState> getRoles() { return roles; }
    public String getCheckpointId() { return checkpointId; }
    public String getCheckpointStatus() { return checkpointStatus; }
    public void setStatus(String status) { this.status = status; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setRoles(Map<String, RoleState> roles) { this.roles = roles; }
    public void setCheckpointId(String checkpointId) { this.checkpointId = checkpointId; }
    public void setCheckpointStatus(String checkpointStatus) { this.checkpointStatus = checkpointStatus; }
}
```

- [ ] **Step 5: Implement PipelineRun entity**

`PipelineRun.java`:
```java
package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document("pipeline_runs")
public class PipelineRun {

    @Id private String id;
    @Indexed private String userId;
    @Indexed private String sparkId;
    private String playbook;
    private PipelineStatus status;
    private String sparkRequest;
    private String repoUrl;
    private List<String> skipRoles;
    private double costCeilingUsd;
    private double totalCostUsd;
    private String currentCheckpointId;
    private String failureReason;
    private Map<String, PhaseState> phases;
    private Map<String, String> artifacts;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    protected PipelineRun() {}

    public static PipelineRun create(String userId, String sparkId, String sparkRequest,
                                     String repoUrl, String playbook, List<String> skipRoles,
                                     double costCeilingUsd) {
        PipelineRun run = new PipelineRun();
        run.id = UUID.randomUUID().toString();
        run.userId = userId;
        run.sparkId = sparkId;
        run.sparkRequest = sparkRequest;
        run.repoUrl = repoUrl;
        run.playbook = playbook;
        run.skipRoles = skipRoles;
        run.costCeilingUsd = costCeilingUsd;
        run.status = PipelineStatus.PENDING;
        run.totalCostUsd = 0.0;
        run.phases = new HashMap<>();
        run.artifacts = new HashMap<>();
        run.createdAt = Instant.now();
        run.updatedAt = run.createdAt;
        return run;
    }

    public void markRunning() {
        this.status = PipelineStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = PipelineStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void markFailed(String reason) {
        this.status = PipelineStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void markCancelled() {
        this.status = PipelineStatus.CANCELLED;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void pauseAtCheckpoint(String checkpointId, String phase) {
        this.status = PipelineStatus.PAUSED_AT_CHECKPOINT;
        this.currentCheckpointId = checkpointId;
        this.updatedAt = Instant.now();
    }

    public void resumeFromCheckpoint() {
        this.status = PipelineStatus.RUNNING;
        this.currentCheckpointId = null;
        this.updatedAt = Instant.now();
    }

    public void addCost(double cost) {
        this.totalCostUsd += cost;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSparkId() { return sparkId; }
    public String getPlaybook() { return playbook; }
    public PipelineStatus getStatus() { return status; }
    public String getSparkRequest() { return sparkRequest; }
    public String getRepoUrl() { return repoUrl; }
    public List<String> getSkipRoles() { return skipRoles; }
    public double getCostCeilingUsd() { return costCeilingUsd; }
    public double getTotalCostUsd() { return totalCostUsd; }
    public String getCurrentCheckpointId() { return currentCheckpointId; }
    public String getFailureReason() { return failureReason; }
    public Map<String, PhaseState> getPhases() { return phases; }
    public Map<String, String> getArtifacts() { return artifacts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    // Setters for Spring Data deserialization
    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }
    public void setPlaybook(String playbook) { this.playbook = playbook; }
    public void setStatus(PipelineStatus status) { this.status = status; }
    public void setSparkRequest(String sparkRequest) { this.sparkRequest = sparkRequest; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public void setSkipRoles(List<String> skipRoles) { this.skipRoles = skipRoles; }
    public void setCostCeilingUsd(double costCeilingUsd) { this.costCeilingUsd = costCeilingUsd; }
    public void setTotalCostUsd(double totalCostUsd) { this.totalCostUsd = totalCostUsd; }
    public void setCurrentCheckpointId(String currentCheckpointId) { this.currentCheckpointId = currentCheckpointId; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setPhases(Map<String, PhaseState> phases) { this.phases = phases; }
    public void setArtifacts(Map<String, String> artifacts) { this.artifacts = artifacts; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
```

- [ ] **Step 6: Implement PipelineEvent entity**

`PipelineEvent.java`:
```java
package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("pipeline_events")
public class PipelineEvent {

    @Id private String id;
    @Indexed private String pipelineRunId;
    private String eventType;
    private String role;
    private String phase;
    private Instant timestamp;
    private String payloadJson;

    protected PipelineEvent() {}

    public static PipelineEvent create(String pipelineRunId, String eventType,
                                       String role, String phase, String payloadJson) {
        PipelineEvent e = new PipelineEvent();
        e.id = UUID.randomUUID().toString();
        e.pipelineRunId = pipelineRunId;
        e.eventType = eventType;
        e.role = role;
        e.phase = phase;
        e.timestamp = Instant.now();
        e.payloadJson = payloadJson;
        return e;
    }

    public String getId() { return id; }
    public String getPipelineRunId() { return pipelineRunId; }
    public String getEventType() { return eventType; }
    public String getRole() { return role; }
    public String getPhase() { return phase; }
    public Instant getTimestamp() { return timestamp; }
    public String getPayloadJson() { return payloadJson; }

    public void setId(String id) { this.id = id; }
    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setRole(String role) { this.role = role; }
    public void setPhase(String phase) { this.phase = phase; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
```

- [ ] **Step 7: Implement PipelineCheckpoint entity**

`PipelineCheckpoint.java`:
```java
package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document("pipeline_checkpoints")
public class PipelineCheckpoint {

    @Id private String id;
    @Indexed private String pipelineRunId;
    @Indexed private String sparkId;
    private String phase;
    private String type;
    private String status;  // PENDING | RESOLVED
    private Map<String, String> artifactPaths;
    private String hitlUrl;
    private String decision;    // nullable until resolved
    private String feedback;    // nullable
    private Instant createdAt;
    private Instant resolvedAt; // nullable

    protected PipelineCheckpoint() {}

    public static PipelineCheckpoint create(String pipelineRunId, String sparkId,
                                            String phase, String type,
                                            Map<String, String> artifactPaths) {
        PipelineCheckpoint cp = new PipelineCheckpoint();
        cp.id = UUID.randomUUID().toString();
        cp.pipelineRunId = pipelineRunId;
        cp.sparkId = sparkId;
        cp.phase = phase;
        cp.type = type;
        cp.status = "PENDING";
        cp.artifactPaths = artifactPaths;
        cp.createdAt = Instant.now();
        return cp;
    }

    public void resolve(CheckpointDecision decision, String feedback) {
        this.decision = decision.name();
        this.feedback = feedback;
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPipelineRunId() { return pipelineRunId; }
    public String getSparkId() { return sparkId; }
    public String getPhase() { return phase; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public Map<String, String> getArtifactPaths() { return artifactPaths; }
    public String getHitlUrl() { return hitlUrl; }
    public String getDecision() { return decision; }
    public String getFeedback() { return feedback; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public void setId(String id) { this.id = id; }
    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }
    public void setPhase(String phase) { this.phase = phase; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setArtifactPaths(Map<String, String> artifactPaths) { this.artifactPaths = artifactPaths; }
    public void setHitlUrl(String hitlUrl) { this.hitlUrl = hitlUrl; }
    public void setDecision(String decision) { this.decision = decision; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
```

- [ ] **Step 8: Implement AgentKnowledge entity**

`AgentKnowledge.java`:
```java
package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document("agent_knowledge")
public class AgentKnowledge {

    @Id private String id;
    @Indexed private String product;
    private List<String> agentTypes;
    private String title;
    private String body;
    @Indexed private KnowledgeStatus status;
    private String proposedBy;
    private Instant proposedAt;
    private String approvedBy;
    private Instant approvedAt;
    private int hitCount;
    private Instant createdAt;

    protected AgentKnowledge() {}

    public static AgentKnowledge propose(String product, List<String> agentTypes,
                                         String title, String body, String proposedBy) {
        AgentKnowledge k = new AgentKnowledge();
        k.id = UUID.randomUUID().toString();
        k.product = product;
        k.agentTypes = agentTypes;
        k.title = title;
        k.body = body;
        k.status = KnowledgeStatus.PROPOSED;
        k.proposedBy = proposedBy;
        k.proposedAt = Instant.now();
        k.hitCount = 0;
        k.createdAt = k.proposedAt;
        return k;
    }

    public void approve(String approvedBy) {
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
        this.status = KnowledgeStatus.APPROVED;
    }

    public void incrementHitCount() { this.hitCount++; }

    public String getId() { return id; }
    public String getProduct() { return product; }
    public List<String> getAgentTypes() { return agentTypes; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public KnowledgeStatus getStatus() { return status; }
    public String getProposedBy() { return proposedBy; }
    public Instant getProposedAt() { return proposedAt; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public int getHitCount() { return hitCount; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setProduct(String product) { this.product = product; }
    public void setAgentTypes(List<String> agentTypes) { this.agentTypes = agentTypes; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setStatus(KnowledgeStatus status) { this.status = status; }
    public void setProposedBy(String proposedBy) { this.proposedBy = proposedBy; }
    public void setProposedAt(Instant proposedAt) { this.proposedAt = proposedAt; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setHitCount(int hitCount) { this.hitCount = hitCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 9: Implement repositories**

`PipelineRunRepository.java`:
```java
package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface PipelineRunRepository extends MongoRepository<PipelineRun, String> {
    Optional<PipelineRun> findByIdAndUserId(String id, String userId);
    Optional<PipelineRun> findBySparkIdAndUserId(String sparkId, String userId);
    List<PipelineRun> findByUserIdOrderByCreatedAtDesc(String userId);
    List<PipelineRun> findByStatus(PipelineStatus status);
}
```

`PipelineEventRepository.java`:
```java
package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.PipelineEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PipelineEventRepository extends MongoRepository<PipelineEvent, String> {
    Page<PipelineEvent> findByPipelineRunIdOrderByTimestampAsc(String pipelineRunId, Pageable pageable);
}
```

`PipelineCheckpointRepository.java`:
```java
package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.PipelineCheckpoint;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface PipelineCheckpointRepository extends MongoRepository<PipelineCheckpoint, String> {
    Optional<PipelineCheckpoint> findByIdAndPipelineRunId(String id, String pipelineRunId);
}
```

`AgentKnowledgeRepository.java`:
```java
package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.AgentKnowledge;
import io.tacticl.data.pipeline.entity.KnowledgeStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface AgentKnowledgeRepository extends MongoRepository<AgentKnowledge, String> {
    List<AgentKnowledge> findByProductAndStatusIn(String product, List<KnowledgeStatus> statuses);
}
```

- [ ] **Step 10: Run tests to verify they pass**

Run: `./gradlew :data:data-pipeline:test 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL` with all 9 tests passing

- [ ] **Step 11: Commit**

```bash
git add data/data-pipeline/
git commit -m "feat(pdlc-v2): add data-pipeline entities and repositories (PipelineRun, PipelineEvent, PipelineCheckpoint, AgentKnowledge)"
```

---

## Chunk 2: Client + Business Layer

### Task 3: client-ai-arbiter Interface + Stub

**Files:**
- Create: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/dto/SubmitPipelineRequest.java`
- Create: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/dto/SubmitPipelineResponse.java`
- Create: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/dto/ResolveCheckpointRequest.java`
- Create: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/dto/PipelineStatusResponse.java`
- Create: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/ArbiterPipelineService.java`
- Create: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/ArbiterPipelineServiceStub.java`
- Test: `client/client-ai-arbiter/src/test/java/io/tacticl/client/arbiter/ArbiterPipelineServiceStubTest.java`

- [ ] **Step 1: Write failing test**

`ArbiterPipelineServiceStubTest.java`:
```java
package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ArbiterPipelineServiceStubTest {

    private final ArbiterPipelineServiceStub stub = new ArbiterPipelineServiceStub();

    @Test
    void submitPipeline_returnsRunIdWithPendingStatus() {
        SubmitPipelineRequest request = new SubmitPipelineRequest(
            "run-1", "spark-1", "user-1", "FULL_PDLC",
            "Add auth flow", "github.com/user/repo", "gh-token",
            List.of(), 50.0, "https://api.tacticl.ai/v1/internal/pipeline/callback"
        );
        SubmitPipelineResponse response = stub.submitPipeline(request);
        assertThat(response.pipelineRunId()).isEqualTo("run-1");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void resolveCheckpoint_doesNotThrow() {
        ResolveCheckpointRequest request = new ResolveCheckpointRequest(
            "run-1", "cp-1", "APPROVED", null
        );
        assertThatCode(() -> stub.resolveCheckpoint(request)).doesNotThrowAnyException();
    }

    @Test
    void getPipelineStatus_returnsUnknown() {
        PipelineStatusResponse response = stub.getPipelineStatus("run-1");
        assertThat(response.pipelineRunId()).isEqualTo("run-1");
        assertThat(response.status()).isEqualTo("UNKNOWN");
    }

    @Test
    void cancelPipeline_doesNotThrow() {
        assertThatCode(() -> stub.cancelPipeline("run-1")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

Run: `./gradlew :client:client-ai-arbiter:test 2>&1 | tail -10`

Expected: `FAILED` (compile errors)

- [ ] **Step 3: Implement DTOs**

`SubmitPipelineRequest.java`:
```java
package io.tacticl.client.arbiter.dto;

import java.util.List;

public record SubmitPipelineRequest(
    String pipelineRunId,
    String sparkId,
    String userId,
    String playbook,
    String sparkRequest,
    String repoUrl,
    String githubToken,
    List<String> skipRoles,
    double costCeilingUsd,
    String callbackUrl
) {}
```

`SubmitPipelineResponse.java`:
```java
package io.tacticl.client.arbiter.dto;

public record SubmitPipelineResponse(
    String pipelineRunId,
    String status
) {}
```

`ResolveCheckpointRequest.java`:
```java
package io.tacticl.client.arbiter.dto;

public record ResolveCheckpointRequest(
    String pipelineRunId,
    String checkpointId,
    String decision,    // APPROVED | REWORK | CANCEL
    String feedback     // nullable
) {}
```

`PipelineStatusResponse.java`:
```java
package io.tacticl.client.arbiter.dto;

public record PipelineStatusResponse(
    String pipelineRunId,
    String status
) {}
```

- [ ] **Step 4: Implement interface**

`ArbiterPipelineService.java`:
```java
package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.*;

public interface ArbiterPipelineService {
    SubmitPipelineResponse submitPipeline(SubmitPipelineRequest request);
    void resolveCheckpoint(ResolveCheckpointRequest request);
    PipelineStatusResponse getPipelineStatus(String pipelineRunId);
    void cancelPipeline(String pipelineRunId);
}
```

- [ ] **Step 5: Implement stub**

`ArbiterPipelineServiceStub.java`:
```java
package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of ArbiterPipelineService.
 * Used until the real gRPC connection to cidadel-ai-arbiter is established.
 * All methods log the call and return safe defaults.
 */
@Service
public class ArbiterPipelineServiceStub implements ArbiterPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ArbiterPipelineServiceStub.class);

    @Override
    public SubmitPipelineResponse submitPipeline(SubmitPipelineRequest request) {
        log.info("[STUB] submitPipeline: runId={} sparkId={} playbook={}",
                 request.pipelineRunId(), request.sparkId(), request.playbook());
        return new SubmitPipelineResponse(request.pipelineRunId(), "PENDING");
    }

    @Override
    public void resolveCheckpoint(ResolveCheckpointRequest request) {
        log.info("[STUB] resolveCheckpoint: runId={} checkpointId={} decision={}",
                 request.pipelineRunId(), request.checkpointId(), request.decision());
    }

    @Override
    public PipelineStatusResponse getPipelineStatus(String pipelineRunId) {
        log.info("[STUB] getPipelineStatus: runId={}", pipelineRunId);
        return new PipelineStatusResponse(pipelineRunId, "UNKNOWN");
    }

    @Override
    public void cancelPipeline(String pipelineRunId) {
        log.info("[STUB] cancelPipeline: runId={}", pipelineRunId);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :client:client-ai-arbiter:test 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL` with 4 tests passing

- [ ] **Step 7: Commit**

```bash
git add client/client-ai-arbiter/
git commit -m "feat(pdlc-v2): add client-ai-arbiter interface + stub (ArbiterPipelineService)"
```

---

### Task 4: business-pipeline — PdlcV2Service + PipelineEventEmitter

**Files:**
- Create: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PdlcV2Service.java`
- Create: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PipelineEventEmitter.java`
- Test: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PdlcV2ServiceTest.java`
- Test: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PipelineEventEmitterTest.java`

- [ ] **Step 1: Write failing tests**

`PdlcV2ServiceTest.java`:
```java
package io.tacticl.business.pipeline.service;

import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.pipeline.repository.PipelineEventRepository;
import io.tacticl.data.pipeline.repository.PipelineCheckpointRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdlcV2ServiceTest {

    @Mock PipelineRunRepository pipelineRunRepository;
    @Mock PipelineEventRepository pipelineEventRepository;
    @Mock PipelineCheckpointRepository pipelineCheckpointRepository;
    @Mock SparkRepository sparkRepository;
    @Mock ArbiterPipelineService arbiterPipelineService;

    PdlcV2Service service;

    @BeforeEach
    void setUp() {
        service = new PdlcV2Service(
            pipelineRunRepository, pipelineEventRepository,
            pipelineCheckpointRepository, sparkRepository, arbiterPipelineService,
            "https://api.tacticl.ai/v1/internal/pipeline/callback"
        );
    }

    @Test
    void submitPipeline_createsPipelineRun_andCallsArbiter() {
        when(arbiterPipelineService.submitPipeline(any())).thenReturn(
            new SubmitPipelineResponse("run-1", "PENDING")
        );
        when(pipelineRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Spark mockSpark = Spark.create("user-1", "Add auth flow");
        when(sparkRepository.findByIdAndUserId("spark-1", "user-1")).thenReturn(Optional.of(mockSpark));
        when(sparkRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PipelineRun run = service.submitPipeline(
            "user-1", "spark-1", "Add auth flow",
            "github.com/user/repo", "FULL_PDLC", List.of(), "gh-token", 50.0
        );

        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PENDING);
        assertThat(run.getSparkId()).isEqualTo("spark-1");
        assertThat(run.getPlaybook()).isEqualTo("FULL_PDLC");
        verify(arbiterPipelineService).submitPipeline(any(SubmitPipelineRequest.class));
        verify(sparkRepository).save(any());
    }

    @Test
    void submitPipeline_sparkNotFound_throwsIllegalArgument() {
        when(sparkRepository.findByIdAndUserId("bad-spark", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitPipeline(
            "user-1", "bad-spark", "req", "url", "BUG_FIX", List.of(), "token", 10.0
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("bad-spark");
    }

    @Test
    void getStatus_returnsRunForUserAndSpark() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req", "url", "BUG_FIX", List.of(), 10.0);
        when(pipelineRunRepository.findBySparkIdAndUserId("spark-1", "user-1")).thenReturn(Optional.of(run));

        Optional<PipelineRun> result = service.getStatus("user-1", "spark-1");

        assertThat(result).isPresent();
        assertThat(result.get().getSparkId()).isEqualTo("spark-1");
    }

    @Test
    void getStatus_notFound_returnsEmpty() {
        when(pipelineRunRepository.findBySparkIdAndUserId("spark-1", "user-1")).thenReturn(Optional.empty());
        assertThat(service.getStatus("user-1", "spark-1")).isEmpty();
    }
}
```

`PipelineEventEmitterTest.java`:
```java
package io.tacticl.business.pipeline.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PipelineEventEmitterTest {

    PipelineEventEmitter emitter;

    @BeforeEach
    void setUp() { emitter = new PipelineEventEmitter(); }

    @Test
    void register_addsEmitterToSet() {
        SseEmitter sse = new SseEmitter();
        emitter.register("run-1", sse);
        assertThat(emitter.activeCount("run-1")).isEqualTo(1);
    }

    @Test
    void unregister_removesEmitter() {
        SseEmitter sse = new SseEmitter();
        emitter.register("run-1", sse);
        emitter.unregister("run-1", sse);
        assertThat(emitter.activeCount("run-1")).isEqualTo(0);
    }

    @Test
    void emit_toEmptySet_doesNotThrow() {
        assertThatCode(() -> emitter.emit("run-1", "ROLE_COMPLETED", "{}"))
            .doesNotThrowAnyException();
    }

    @Test
    void completeAll_removesAllEmitters() {
        emitter.register("run-1", new SseEmitter());
        emitter.register("run-1", new SseEmitter());
        emitter.completeAll("run-1");
        assertThat(emitter.activeCount("run-1")).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run tests to confirm failure**

Run: `./gradlew :business:business-pipeline:test 2>&1 | tail -10`

Expected: `FAILED` (compile errors)

- [ ] **Step 3: Implement PipelineEventEmitter**

`PipelineEventEmitter.java`:
```java
package io.tacticl.business.pipeline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PipelineEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(PipelineEventEmitter.class);

    private final ConcurrentHashMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String pipelineRunId, SseEmitter emitter) {
        emitters.computeIfAbsent(pipelineRunId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(emitter);
        emitter.onCompletion(() -> unregister(pipelineRunId, emitter));
        emitter.onTimeout(() -> unregister(pipelineRunId, emitter));
        emitter.onError(e -> unregister(pipelineRunId, emitter));
        return emitter;
    }

    public void unregister(String pipelineRunId, SseEmitter emitter) {
        emitters.computeIfPresent(pipelineRunId, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }

    public void emit(String pipelineRunId, String eventName, Object data) {
        Set<SseEmitter> set = emitters.getOrDefault(pipelineRunId, Collections.emptySet());
        Set<SseEmitter> failed = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.warn("Failed to emit SSE event to subscriber for run {}: {}", pipelineRunId, e.getMessage());
                failed.add(emitter);
            }
        }
        failed.forEach(e -> unregister(pipelineRunId, e));
    }

    public void completeAll(String pipelineRunId) {
        Set<SseEmitter> set = emitters.remove(pipelineRunId);
        if (set != null) set.forEach(SseEmitter::complete);
    }

    /** For tests only. */
    int activeCount(String pipelineRunId) {
        return emitters.getOrDefault(pipelineRunId, Collections.emptySet()).size();
    }
}
```

- [ ] **Step 4: Implement PdlcV2Service**

`PdlcV2Service.java`:
```java
package io.tacticl.business.pipeline.service;

import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.client.arbiter.dto.ResolveCheckpointRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.data.pipeline.entity.*;
import io.tacticl.data.pipeline.repository.*;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class PdlcV2Service {

    private static final Logger log = LoggerFactory.getLogger(PdlcV2Service.class);

    private final PipelineRunRepository pipelineRunRepository;
    private final PipelineEventRepository pipelineEventRepository;
    private final PipelineCheckpointRepository pipelineCheckpointRepository;
    private final SparkRepository sparkRepository;
    private final ArbiterPipelineService arbiterPipelineService;
    private final String callbackUrl;

    public PdlcV2Service(PipelineRunRepository pipelineRunRepository,
                         PipelineEventRepository pipelineEventRepository,
                         PipelineCheckpointRepository pipelineCheckpointRepository,
                         SparkRepository sparkRepository,
                         ArbiterPipelineService arbiterPipelineService,
                         @Value("${pdlc.v2.callback-url:https://api.tacticl.ai/v1/internal/pipeline/callback}")
                         String callbackUrl) {
        this.pipelineRunRepository = pipelineRunRepository;
        this.pipelineEventRepository = pipelineEventRepository;
        this.pipelineCheckpointRepository = pipelineCheckpointRepository;
        this.sparkRepository = sparkRepository;
        this.arbiterPipelineService = arbiterPipelineService;
        this.callbackUrl = callbackUrl;
    }

    public PipelineRun submitPipeline(String userId, String sparkId, String sparkRequest,
                                      String repoUrl, String playbook, List<String> skipRoles,
                                      String githubToken, double costCeilingUsd) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));

        PipelineRun run = PipelineRun.create(userId, sparkId, sparkRequest, repoUrl,
                                             playbook, skipRoles, costCeilingUsd);
        pipelineRunRepository.save(run);

        SubmitPipelineRequest request = new SubmitPipelineRequest(
            run.getId(), sparkId, userId, playbook, sparkRequest,
            repoUrl, githubToken, skipRoles, costCeilingUsd, callbackUrl
        );

        arbiterPipelineService.submitPipeline(request);
        log.info("Submitted pipeline run {} for spark {} (playbook={})", run.getId(), sparkId, playbook);

        spark.setPipelineRunId(run.getId());
        sparkRepository.save(spark);

        return run;
    }

    public Optional<PipelineRun> getStatus(String userId, String sparkId) {
        return pipelineRunRepository.findBySparkIdAndUserId(sparkId, userId);
    }

    public Optional<PipelineRun> getStatusByRunId(String userId, String pipelineRunId) {
        return pipelineRunRepository.findByIdAndUserId(pipelineRunId, userId);
    }

    public Page<PipelineEvent> getEvents(String pipelineRunId, int page, int size) {
        return pipelineEventRepository.findByPipelineRunIdOrderByTimestampAsc(
            pipelineRunId, PageRequest.of(page, size));
    }

    public void resolveCheckpoint(String userId, String sparkId, String checkpointId,
                                  CheckpointDecision decision, String feedback) {
        PipelineRun run = pipelineRunRepository.findBySparkIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Pipeline run not found for spark: " + sparkId));

        PipelineCheckpoint checkpoint = pipelineCheckpointRepository
                .findByIdAndPipelineRunId(checkpointId, run.getId())
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));

        checkpoint.resolve(decision, feedback);
        pipelineCheckpointRepository.save(checkpoint);

        arbiterPipelineService.resolveCheckpoint(new ResolveCheckpointRequest(
            run.getId(), checkpointId, decision.name(), feedback
        ));

        log.info("Resolved checkpoint {} for run {} with decision {}", checkpointId, run.getId(), decision);
    }

    public void cancelPipeline(String userId, String sparkId) {
        PipelineRun run = pipelineRunRepository.findBySparkIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Pipeline run not found for spark: " + sparkId));
        run.markCancelled();
        pipelineRunRepository.save(run);
        arbiterPipelineService.cancelPipeline(run.getId());
        log.info("Cancelled pipeline run {} for spark {}", run.getId(), sparkId);
    }
}
```

- [ ] **Step 5: Verify Spark already has setPipelineRunId() (no change needed)**

`Spark.java` already has `setPipelineRunId(String pipelineRunId)` and `getPipelineRunId()`. No change needed — `PdlcV2Service` uses `spark.setPipelineRunId(run.getId())` directly.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :business:business-pipeline:test 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL` with 8 tests passing

- [ ] **Step 7: Run data-sparks tests to verify Spark change didn't break anything**

Run: `./gradlew :data:data-sparks:test 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add business/business-pipeline/ data/data-sparks/
git commit -m "feat(pdlc-v2): add PdlcV2Service and PipelineEventEmitter in business-pipeline"
```

---

## Chunk 3: Service Layer + Routing

### Task 5: service-pipeline Controllers

**Files:**
- Create: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/dto/PipelineRunDto.java`
- Create: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/dto/PipelineEventDto.java`
- Create: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/dto/ResolveCheckpointDto.java`
- Create: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/dto/PipelineCallbackEvent.java`
- Create: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/controller/PipelineController.java`
- Create: `service/service-pipeline/src/main/java/io/tacticl/service/pipeline/controller/PipelineCallbackController.java`
- Test: `service/service-pipeline/src/test/java/io/tacticl/service/pipeline/controller/PipelineControllerTest.java`
- Test: `service/service-pipeline/src/test/java/io/tacticl/service/pipeline/controller/PipelineCallbackControllerTest.java`

- [ ] **Step 1: Write failing tests**

`PipelineControllerTest.java`:
```java
package io.tacticl.service.pipeline.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    @Mock PdlcV2Service pdlcV2Service;
    @Mock PipelineEventEmitter pipelineEventEmitter;

    PipelineController controller;

    AuthenticatedUser user = new AuthenticatedUser("user-1", "test@test.com");

    @BeforeEach
    void setUp() {
        controller = new PipelineController(pdlcV2Service, pipelineEventEmitter);
    }

    @Test
    void getPipelineStatus_found_returns200() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req", "url", "BUG_FIX", List.of(), 10.0);
        when(pdlcV2Service.getStatus("user-1", "spark-1")).thenReturn(Optional.of(run));

        ResponseEntity<?> response = controller.getPipelineStatus(user, "spark-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getPipelineStatus_notFound_returns404() {
        when(pdlcV2Service.getStatus("user-1", "spark-1")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getPipelineStatus(user, "spark-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resolveCheckpoint_callsServiceAndReturns200() {
        doNothing().when(pdlcV2Service).resolveCheckpoint(any(), any(), any(), any(), any());

        ResponseEntity<Void> response = controller.resolveCheckpoint(
            user, "spark-1", "cp-1",
            new io.tacticl.service.pipeline.dto.ResolveCheckpointDto("APPROVED", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pdlcV2Service).resolveCheckpoint(any(), any(), any(), any(), any());
    }
}
```

`PipelineCallbackControllerTest.java`:
```java
package io.tacticl.service.pipeline.controller;

import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.service.pipeline.dto.PipelineCallbackEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PipelineCallbackControllerTest {

    @Mock PipelineEventEmitter pipelineEventEmitter;

    PipelineCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new PipelineCallbackController(pipelineEventEmitter);
    }

    @Test
    void handleCallback_emitsEventAndReturns200() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            "run-1", "ROLE_COMPLETED", "PM", "PRODUCT", "{}"
        );

        ResponseEntity<Void> response = controller.handleCallback(event);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pipelineEventEmitter).emit("run-1", "ROLE_COMPLETED", "{}");
    }

    @Test
    void handleCallback_pipelineCompleted_emitsAndCompletes() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            "run-1", "PIPELINE_COMPLETED", null, null, "{}"
        );

        controller.handleCallback(event);

        verify(pipelineEventEmitter).emit("run-1", "PIPELINE_COMPLETED", "{}");
        verify(pipelineEventEmitter).completeAll("run-1");
    }
}
```

- [ ] **Step 2: Run tests to confirm failure**

Run: `./gradlew :service:service-pipeline:test 2>&1 | tail -10`

Expected: `FAILED` (compile errors)

- [ ] **Step 3: Implement DTOs**

`PipelineRunDto.java`:
```java
package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import java.time.Instant;

public record PipelineRunDto(
    String id,
    String sparkId,
    String playbook,
    PipelineStatus status,
    double totalCostUsd,
    String currentCheckpointId,
    String failureReason,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    public static PipelineRunDto from(PipelineRun run) {
        return new PipelineRunDto(
            run.getId(), run.getSparkId(), run.getPlaybook(),
            run.getStatus(), run.getTotalCostUsd(),
            run.getCurrentCheckpointId(), run.getFailureReason(),
            run.getCreatedAt(), run.getUpdatedAt(), run.getCompletedAt()
        );
    }
}
```

`PipelineEventDto.java`:
```java
package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PipelineEvent;
import java.time.Instant;

public record PipelineEventDto(
    String id,
    String eventType,
    String role,
    String phase,
    Instant timestamp,
    String payloadJson
) {
    public static PipelineEventDto from(PipelineEvent event) {
        return new PipelineEventDto(
            event.getId(), event.getEventType(), event.getRole(),
            event.getPhase(), event.getTimestamp(), event.getPayloadJson()
        );
    }
}
```

`ResolveCheckpointDto.java`:
```java
package io.tacticl.service.pipeline.dto;

public record ResolveCheckpointDto(
    String decision,   // APPROVED | REWORK | CANCEL
    String feedback    // nullable; required if decision = REWORK
) {}
```

`PipelineCallbackEvent.java`:
```java
package io.tacticl.service.pipeline.dto;

public record PipelineCallbackEvent(
    String pipelineRunId,
    String eventType,
    String role,        // nullable
    String phase,       // nullable
    String payloadJson
) {}
```

- [ ] **Step 4: Implement PipelineController**

`PipelineController.java`:
```java
package io.tacticl.service.pipeline.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.framework.base.controller.BaseController;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.service.pipeline.dto.PipelineRunDto;
import io.tacticl.service.pipeline.dto.ResolveCheckpointDto;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/sparks/{sparkId}/pipeline")
public class PipelineController extends BaseController {

    private final PdlcV2Service pdlcV2Service;
    private final PipelineEventEmitter pipelineEventEmitter;

    public PipelineController(PdlcV2Service pdlcV2Service,
                              PipelineEventEmitter pipelineEventEmitter) {
        this.pdlcV2Service = pdlcV2Service;
        this.pipelineEventEmitter = pipelineEventEmitter;
    }

    @Override
    protected String getModuleName() { return "pipeline"; }

    @GetMapping
    public ResponseEntity<PipelineRunDto> getPipelineStatus(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        return pdlcV2Service.getStatus(user.getUserId(), sparkId)
                .map(run -> ResponseEntity.ok(PipelineRunDto.from(run)))
                .orElse(ResponseEntity.<PipelineRunDto>notFound().build());
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPipelineEvents(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        pdlcV2Service.getStatus(user.getUserId(), sparkId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Pipeline run not found for spark: " + sparkId));
        SseEmitter emitter = new SseEmitter(300_000L);
        // Register against pipelineRunId (resolved from spark ownership check above)
        pdlcV2Service.getStatus(user.getUserId(), sparkId).ifPresent(run ->
            pipelineEventEmitter.register(run.getId(), emitter)
        );
        return emitter;
    }

    @PostMapping("/checkpoint/{checkpointId}")
    public ResponseEntity<Void> resolveCheckpoint(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId,
            @PathVariable String checkpointId,
            @RequestBody ResolveCheckpointDto body) {
        pdlcV2Service.resolveCheckpoint(
            user.getUserId(), sparkId, checkpointId,
            CheckpointDecision.valueOf(body.decision()),
            body.feedback()
        );
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 5: Implement PipelineCallbackController**

`PipelineCallbackController.java`:
```java
package io.tacticl.service.pipeline.controller;

import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.service.pipeline.dto.PipelineCallbackEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoint — receives HTTP push events from cidadel-ai-arbiter.
 * Protected by VPC firewall rules (Hetzner IP range only).
 * Not exposed through external auth filters.
 */
@RestController
@RequestMapping("/v1/internal/pipeline")
public class PipelineCallbackController {

    private static final String PIPELINE_COMPLETED = "PIPELINE_COMPLETED";
    private static final String PIPELINE_FAILED = "PIPELINE_FAILED";
    private static final String PIPELINE_CANCELLED = "PIPELINE_CANCELLED";

    private final PipelineEventEmitter pipelineEventEmitter;

    public PipelineCallbackController(PipelineEventEmitter pipelineEventEmitter) {
        this.pipelineEventEmitter = pipelineEventEmitter;
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(@RequestBody PipelineCallbackEvent event) {
        pipelineEventEmitter.emit(event.pipelineRunId(), event.eventType(), event.payloadJson());

        // Complete SSE stream on terminal events
        if (PIPELINE_COMPLETED.equals(event.eventType())
                || PIPELINE_FAILED.equals(event.eventType())
                || PIPELINE_CANCELLED.equals(event.eventType())) {
            pipelineEventEmitter.completeAll(event.pipelineRunId());
        }

        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :service:service-pipeline:test 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL` with 5 tests passing

- [ ] **Step 7: Commit**

```bash
git add service/service-pipeline/
git commit -m "feat(pdlc-v2): add service-pipeline controllers (PipelineController, PipelineCallbackController)"
```

---

### Task 6: PdlcRouter + application-api Wire-up

**Files:**
- Create: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/router/PdlcRouter.java`
- Test: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/router/PdlcRouterTest.java`
- Modify: `application-api/build.gradle.kts`

- [ ] **Step 1: Write failing test**

`PdlcRouterTest.java`:
```java
package io.tacticl.business.pipeline.router;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdlcRouterTest {

    @Mock PdlcV2Service pdlcV2Service;

    @Test
    void route_whenFlagEnabled_andCodeSpark_callsV2Service() {
        PdlcRouter router = new PdlcRouter(pdlcV2Service, true);
        PipelineRun mockRun = PipelineRun.create("u", "s", "r", "url", "BUG_FIX", List.of(), 10.0);
        when(pdlcV2Service.submitPipeline(any(), any(), any(), any(), any(), any(), any(), anyDouble()))
            .thenReturn(mockRun);

        Optional<PipelineRun> result = router.route(
            "user-1", "spark-1", "req", "url", SparkType.CODE, List.of(), "token", 10.0
        );

        assertThat(result).isPresent();
        verify(pdlcV2Service).submitPipeline(any(), any(), any(), any(), any(), any(), any(), anyDouble());
    }

    @Test
    void route_whenFlagDisabled_returnsEmpty() {
        PdlcRouter router = new PdlcRouter(pdlcV2Service, false);

        Optional<PipelineRun> result = router.route(
            "user-1", "spark-1", "req", "url", SparkType.CODE, List.of(), "token", 10.0
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(pdlcV2Service);
    }

    @Test
    void route_whenFlagEnabled_butNotCodeOrDevops_returnsEmpty() {
        PdlcRouter router = new PdlcRouter(pdlcV2Service, true);

        Optional<PipelineRun> result = router.route(
            "user-1", "spark-1", "req", "url", SparkType.RESEARCH, List.of(), "token", 10.0
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(pdlcV2Service);
    }

    @Test
    void route_whenFlagEnabled_andDevopsSpark_callsV2Service() {
        PdlcRouter router = new PdlcRouter(pdlcV2Service, true);
        PipelineRun mockRun = PipelineRun.create("u", "s", "r", "url", "INFRA_CHANGE", List.of(), 10.0);
        when(pdlcV2Service.submitPipeline(any(), any(), any(), any(), any(), any(), any(), anyDouble()))
            .thenReturn(mockRun);

        Optional<PipelineRun> result = router.route(
            "user-1", "spark-1", "req", "url", SparkType.DEVOPS, List.of(), "token", 10.0
        );

        assertThat(result).isPresent();
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

Run: `./gradlew :business:business-pipeline:test --tests "io.tacticl.business.pipeline.router.*" 2>&1 | tail -10`

Expected: `FAILED` (compile errors)

- [ ] **Step 3: Implement PdlcRouter**

`PdlcRouter.java`:
```java
package io.tacticl.business.pipeline.router;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.SparkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Routes PDLC-eligible sparks to v2 (arbiter) or v1 (in-JVM, when flag is off).
 * Only CODE and DEVOPS spark types are PDLC-eligible.
 */
@Service
public class PdlcRouter {

    private static final Logger log = LoggerFactory.getLogger(PdlcRouter.class);

    private final PdlcV2Service pdlcV2Service;
    private final boolean v2Enabled;

    public PdlcRouter(PdlcV2Service pdlcV2Service,
                      @Value("${pdlc.v2.enabled:false}") boolean v2Enabled) {
        this.pdlcV2Service = pdlcV2Service;
        this.v2Enabled = v2Enabled;
    }

    /**
     * Routes a spark to PDLC v2 if the flag is on and the spark type is eligible.
     * Returns empty if v2 is disabled or if the spark type is not PDLC-eligible.
     */
    public Optional<PipelineRun> route(String userId, String sparkId, String sparkRequest,
                                       String repoUrl, SparkType sparkType,
                                       List<String> skipRoles, String githubToken,
                                       double costCeilingUsd) {
        if (!v2Enabled) {
            log.debug("PDLC v2 disabled — skipping v2 route for spark {}", sparkId);
            return Optional.empty();
        }
        if (sparkType != SparkType.CODE && sparkType != SparkType.DEVOPS) {
            log.debug("Spark {} type {} is not PDLC-eligible", sparkId, sparkType);
            return Optional.empty();
        }
        String playbook = resolvePlaybook(sparkRequest);
        log.info("Routing spark {} ({}) to PDLC v2 with playbook {}", sparkId, sparkType, playbook);
        PipelineRun run = pdlcV2Service.submitPipeline(userId, sparkId, sparkRequest,
                                                        repoUrl, playbook, skipRoles,
                                                        githubToken, costCeilingUsd);
        return Optional.of(run);
    }

    private String resolvePlaybook(String sparkRequest) {
        // Default to FULL_PDLC — classifier on arbiter side will refine
        return "FULL_PDLC";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :business:business-pipeline:test 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL` with all tests passing

- [ ] **Step 5: Wire service-pipeline into application-api**

Open `application-api/build.gradle.kts` and add inside `dependencies { ... }`:

```kotlin
implementation(project(":service:service-pipeline"))
```

(Add it under the existing service layer group, after `:service:service-connections`)

- [ ] **Step 6: Full build to verify everything compiles and tests pass**

Run: `./gradlew build 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run only new module tests to confirm counts**

Run: `./gradlew :data:data-pipeline:test :client:client-ai-arbiter:test :business:business-pipeline:test :service:service-pipeline:test 2>&1 | grep -E "(tests|PASSED|FAILED|BUILD)"`

Expected: All modules `BUILD SUCCESSFUL`, total ~22+ tests passing

- [ ] **Step 8: Commit**

```bash
git add business/business-pipeline/src/main/java/io/tacticl/business/pipeline/router/ \
        business/business-pipeline/src/test/java/io/tacticl/business/pipeline/router/ \
        application-api/build.gradle.kts
git commit -m "feat(pdlc-v2): add PdlcRouter with pdlc.v2.enabled flag, wire service-pipeline into application-api"
```

---

## Final Verification

- [ ] **Full test suite**

Run: `./gradlew test 2>&1 | tail -30`

Expected: `BUILD SUCCESSFUL` — all existing tests still pass

- [ ] **Build without tests**

Run: `./gradlew build -x test 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Module graph check**

Run: `./gradlew projects 2>&1 | grep -E "pipeline|arbiter"`

Expected output:
```
+--- Project ':business:business-pipeline'
+--- Project ':client:client-ai-arbiter'
+--- Project ':data:data-pipeline'
+--- Project ':service:service-pipeline'
```

---

## What This Delivers

After all 6 tasks:

| Component | Status |
|-----------|--------|
| MongoDB entities (PipelineRun, PipelineEvent, PipelineCheckpoint, AgentKnowledge) | ✅ |
| Repositories (findBySparkIdAndUserId, paginated events, etc.) | ✅ |
| ArbiterPipelineService interface + stub | ✅ |
| PdlcV2Service (submit, status, events, resolve, cancel) | ✅ |
| PipelineEventEmitter (SSE fan-out, identical to SparkEventEmitter pattern) | ✅ |
| PipelineController (GET /v1/sparks/{id}/pipeline, SSE events, resolve checkpoint) | ✅ |
| PipelineCallbackController (POST /v1/internal/pipeline/callback) | ✅ |
| PdlcRouter (pdlc.v2.enabled flag, CODE/DEVOPS spark eligibility) | ✅ |
| application-api wire-up | ✅ |

**Not in scope (future work):**
- Real gRPC connection replacing ArbiterPipelineServiceStub
- Delete v1 PdlcPipelineOrchestrator (after production validation)
- Firestore → MongoDB migration script
- SparkType enum values (CODE, DEVOPS) — verify these exist in data-sparks before implementing PdlcRouter tests
