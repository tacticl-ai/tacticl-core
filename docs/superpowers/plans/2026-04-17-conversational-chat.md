# Conversational Chat Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the one-shot agent command flow with a multi-turn conversational session experience — the agent gathers requirements through back-and-forth dialogue, presents a plan, detects approval, then creates a Spark and routes it to PDLC or cloud execution.

**Architecture:** Thread-first model — the first message creates a `ConversationSession`, not a Spark. Messages accumulate in the session. The LLM uses marker tokens (`<<<PROPOSE>>>`, `<<<START>>>`) in its responses to signal state transitions; the backend strips the markers before returning text to the client. A Spark is only created when `<<<START>>>` is detected and the user has approved.

**Tech Stack:** Java 25, Spring Boot 4, Spring Data MongoDB, `AnthropicDirectClient` (cidadel), `SparkService` + `PdlcRouter` (existing business modules).

**Spec:** `docs/superpowers/specs/2026-04-17-conversational-chat-design.md`

---

## File Map

### New files — `data:data-conversation`
| File | Responsibility |
|------|---------------|
| `data/data-conversation/build.gradle.kts` | Module descriptor |
| `data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/SessionStatus.java` | Enum: GATHERING, PROPOSING, ACTIVE, COMPLETED |
| `data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/ConversationMessage.java` | Embedded value: role + content + timestamp |
| `data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/ConversationSession.java` | MongoDB document, owns message list + state |
| `data/data-conversation/src/main/java/io/tacticl/data/conversation/repository/ConversationSessionRepository.java` | Spring Data MongoRepository |
| `data/data-conversation/src/test/java/io/tacticl/data/conversation/entity/ConversationSessionTest.java` | Unit tests for state transition methods |

### New files — `service:service-conversation`
| File | Responsibility |
|------|---------------|
| `service/service-conversation/build.gradle.kts` | Module descriptor |
| `service/service-conversation/src/main/java/io/tacticl/service/conversation/dto/CreateConversationRequest.java` | POST /v1/conversations body |
| `service/service-conversation/src/main/java/io/tacticl/service/conversation/dto/SendMessageRequest.java` | POST /v1/conversations/{id}/messages body |
| `service/service-conversation/src/main/java/io/tacticl/service/conversation/dto/ConversationResponse.java` | Session info response |
| `service/service-conversation/src/main/java/io/tacticl/service/conversation/dto/MessageResponse.java` | Single message-exchange response |
| `service/service-conversation/src/main/java/io/tacticl/service/conversation/service/ConversationService.java` | LLM orchestration, marker detection, state transitions |
| `service/service-conversation/src/main/java/io/tacticl/service/conversation/controller/ConversationController.java` | 4 REST endpoints |
| `service/service-conversation/src/test/java/io/tacticl/service/conversation/service/ConversationServiceTest.java` | Unit tests (mocked Anthropic client) |
| `service/service-conversation/src/test/java/io/tacticl/service/conversation/controller/ConversationControllerTest.java` | Controller unit tests (MockitoExtension + direct invocation) |

### Modified files
| File | Change |
|------|--------|
| `settings.gradle.kts` | Add `include(":data:data-conversation")` and `include(":service:service-conversation")` |
| `application-api/build.gradle.kts` | Add `implementation(project(":service:service-conversation"))` |

---

## Chunk 1: Data Layer

### Task 1: data-conversation module scaffold

**Files:**
- Create: `data/data-conversation/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `data/data-conversation/build.gradle.kts`**

```kotlin
// data-conversation — MongoDB entities + repositories for conversation sessions
plugins {
    `java-library`
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
```

- [ ] **Step 2: Register in `settings.gradle.kts`**

Append these two lines at the bottom of `settings.gradle.kts`:
```kotlin
include(":data:data-conversation")
include(":service:service-conversation")
```

- [ ] **Step 3: Verify Gradle sees the module**

Run: `./gradlew :data:data-conversation:dependencies --configuration compileClasspath`

Expected: task resolves without "project not found" error. Spring Data MongoDB appears in the tree (inherited from `data/build.gradle.kts`).

---

### Task 2: SessionStatus enum

**Files:**
- Create: `data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/SessionStatus.java`

- [ ] **Step 1: Create enum**

```java
package io.tacticl.data.conversation.entity;

public enum SessionStatus {
    GATHERING,
    PROPOSING,
    ACTIVE,
    COMPLETED
}
```

---

### Task 3: ConversationMessage value object

**Files:**
- Create: `data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/ConversationMessage.java`

- [ ] **Step 1: Create value object**

```java
package io.tacticl.data.conversation.entity;

import java.time.Instant;

public class ConversationMessage {

    private String role;
    private String content;
    private Instant timestamp;

    protected ConversationMessage() {}

    public static ConversationMessage user(String content) {
        ConversationMessage m = new ConversationMessage();
        m.role = "user";
        m.content = content;
        m.timestamp = Instant.now();
        return m;
    }

    public static ConversationMessage assistant(String content) {
        ConversationMessage m = new ConversationMessage();
        m.role = "assistant";
        m.content = content;
        m.timestamp = Instant.now();
        return m;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getTimestamp() { return timestamp; }
}
```

---

### Task 4: ConversationSession entity

**Files:**
- Create: `data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/ConversationSession.java`
- Create: `data/data-conversation/src/test/java/io/tacticl/data/conversation/entity/ConversationSessionTest.java`

- [ ] **Step 1: Write failing tests first**

```java
package io.tacticl.data.conversation.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConversationSessionTest {

    @Test
    void create_setsGatheringStatus() {
        ConversationSession s = ConversationSession.create("user-1", "build me a login page");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.GATHERING);
        assertThat(s.getUserId()).isEqualTo("user-1");
        assertThat(s.getMessages()).isEmpty();
        assertThat(s.getId()).isNotBlank();
    }

    @Test
    void addMessage_appendsToList() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.addMessage(ConversationMessage.user("hello"));
        s.addMessage(ConversationMessage.assistant("hi"));
        assertThat(s.getMessages()).hasSize(2);
    }

    @Test
    void markProposing_transitionsStatus() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.markProposing("CODE", "Build a React todo app with Node backend");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.PROPOSING);
        assertThat(s.getDetectedSparkType()).isEqualTo("CODE");
        assertThat(s.getProposalText()).isEqualTo("Build a React todo app with Node backend");
    }

    @Test
    void markActive_setsSparkId() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.markProposing("CODE", "my plan");
        s.markActive("spark-123");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(s.getSparkId()).isEqualTo("spark-123");
    }

    @Test
    void markActive_requiresProposingFirst() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        assertThatThrownBy(() -> s.markActive("spark-123"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revertToGathering_fromProposing() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.markProposing("CODE", "my plan");
        s.revertToGathering();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.GATHERING);
    }

    @Test
    void title_truncatesLongInput() {
        String longInput = "a".repeat(100);
        ConversationSession s = ConversationSession.create("user-1", longInput);
        assertThat(s.getTitle().length()).isLessThanOrEqualTo(60);
    }
}
```

- [ ] **Step 2: Run tests — verify they FAIL**

Run: `./gradlew :data:data-conversation:test`

Expected: compilation failure (class not found). Confirms TDD red state.

- [ ] **Step 3: Implement ConversationSession**

```java
package io.tacticl.data.conversation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document("conversation_sessions")
public class ConversationSession {

    @Id private String id;
    @Indexed private String userId;
    private String title;
    private SessionStatus status;
    private String detectedSparkType;
    private String proposalText;
    private String sparkId;
    private List<ConversationMessage> messages;
    private Instant createdAt;
    private Instant updatedAt;

    protected ConversationSession() {}

    public static ConversationSession create(String userId, String firstMessage) {
        ConversationSession s = new ConversationSession();
        s.id = UUID.randomUUID().toString();
        s.userId = userId;
        s.title = firstMessage.length() > 57
            ? firstMessage.substring(0, 57) + "..."
            : firstMessage;
        s.status = SessionStatus.GATHERING;
        s.messages = new ArrayList<>();
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public void addMessage(ConversationMessage message) {
        this.messages.add(message);
        this.updatedAt = Instant.now();
    }

    public void markProposing(String detectedSparkType, String proposalText) {
        this.detectedSparkType = detectedSparkType;
        this.proposalText = proposalText;
        this.status = SessionStatus.PROPOSING;
        this.updatedAt = Instant.now();
    }

    public void markActive(String sparkId) {
        if (this.status != SessionStatus.PROPOSING) {
            throw new IllegalStateException("Can only activate from PROPOSING state, current: " + this.status);
        }
        this.sparkId = sparkId;
        this.status = SessionStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void revertToGathering() {
        this.status = SessionStatus.GATHERING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = SessionStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public SessionStatus getStatus() { return status; }
    public String getDetectedSparkType() { return detectedSparkType; }
    public String getProposalText() { return proposalText; }
    public String getSparkId() { return sparkId; }
    public List<ConversationMessage> getMessages() { return messages; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 4: Run tests — verify they PASS**

Run: `./gradlew :data:data-conversation:test`

Expected: BUILD SUCCESSFUL, all 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add data/data-conversation/
git commit -m "feat(data-conversation): add ConversationSession entity + tests"
```

---

### Task 5: ConversationSessionRepository

**Files:**
- Create: `data/data-conversation/src/main/java/io/tacticl/data/conversation/repository/ConversationSessionRepository.java`

- [ ] **Step 1: Create repository**

```java
package io.tacticl.data.conversation.repository;

import io.tacticl.data.conversation.entity.ConversationSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ConversationSessionRepository extends MongoRepository<ConversationSession, String> {
    Optional<ConversationSession> findByIdAndUserId(String id, String userId);
    List<ConversationSession> findByUserIdOrderByUpdatedAtDesc(String userId);
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :data:data-conversation:build -x test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add data/data-conversation/src/main/java/io/tacticl/data/conversation/repository/
git commit -m "feat(data-conversation): add ConversationSessionRepository"
```

---

## Chunk 2: ConversationService

### Task 6: service-conversation module scaffold + DTOs

**Files:**
- Create: `service/service-conversation/build.gradle.kts`
- Create: DTOs (4 files)

- [ ] **Step 1: Create `service/service-conversation/build.gradle.kts`**

```kotlin
// service-conversation — conversational requirements gathering before spark execution
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":data:data-conversation"))
    implementation(project(":data:data-pipeline"))
    implementation(project(":business:business-sparks"))
    implementation(project(":business:business-pipeline"))
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.token.issuance)
    implementation(libs.cidadel.client.anthropic.direct)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 2: Create `CreateConversationRequest.java`**

```java
package io.tacticl.service.conversation.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateConversationRequest {

    @NotBlank
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
```

- [ ] **Step 3: Create `SendMessageRequest.java`**

```java
package io.tacticl.service.conversation.dto;

import jakarta.validation.constraints.NotBlank;

public class SendMessageRequest {

    @NotBlank
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
```

- [ ] **Step 4: Create `ConversationResponse.java`**

```java
package io.tacticl.service.conversation.dto;

import io.tacticl.data.conversation.entity.ConversationMessage;
import io.tacticl.data.conversation.entity.ConversationSession;
import java.time.Instant;
import java.util.List;

public class ConversationResponse {

    private String id;
    private String title;
    private String status;
    private String sparkId;
    private List<ConversationMessage> messages;
    private Instant createdAt;
    private Instant updatedAt;

    public static ConversationResponse from(ConversationSession session) {
        ConversationResponse r = new ConversationResponse();
        r.id = session.getId();
        r.title = session.getTitle();
        r.status = session.getStatus().name();
        r.sparkId = session.getSparkId();
        r.messages = session.getMessages();
        r.createdAt = session.getCreatedAt();
        r.updatedAt = session.getUpdatedAt();
        return r;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getSparkId() { return sparkId; }
    public List<ConversationMessage> getMessages() { return messages; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 5: Create `MessageResponse.java`**

```java
package io.tacticl.service.conversation.dto;

public class MessageResponse {

    private String content;
    private String sessionStatus;
    private String sparkId;
    private String pipelineRunId;

    public MessageResponse(String content, String sessionStatus, String sparkId, String pipelineRunId) {
        this.content = content;
        this.sessionStatus = sessionStatus;
        this.sparkId = sparkId;
        this.pipelineRunId = pipelineRunId;
    }

    public String getContent() { return content; }
    public String getSessionStatus() { return sessionStatus; }
    public String getSparkId() { return sparkId; }
    public String getPipelineRunId() { return pipelineRunId; }
}
```

- [ ] **Step 6: Verify module compiles**

Run: `./gradlew :service:service-conversation:compileJava`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add service/service-conversation/
git commit -m "feat(service-conversation): add module scaffold and DTOs"
```

---

### Task 7: ConversationService

**Files:**
- Create: `service/service-conversation/src/main/java/io/tacticl/service/conversation/service/ConversationService.java`
- Create: `service/service-conversation/src/test/java/io/tacticl/service/conversation/service/ConversationServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package io.tacticl.service.conversation.service;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import io.tacticl.service.conversation.dto.MessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ConversationSessionRepository sessionRepository;
    @Mock AnthropicDirectClient anthropicClient;
    @Mock SparkService sparkService;
    @Mock SparkClassifierService sparkClassifierService;
    @Mock PdlcRouter pdlcRouter;
    @Mock PipelineRunRepository pipelineRunRepository;

    ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(
            sessionRepository, anthropicClient, sparkService,
            sparkClassifierService, pdlcRouter, pipelineRunRepository);
    }

    @Test
    void createSession_returnsGatheringSession() {
        ConversationSession saved = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.save(any())).thenReturn(saved);

        ConversationSession result = service.createSession("user-1", "build me a todo app");

        assertThat(result.getStatus()).isEqualTo(SessionStatus.GATHERING);
        verify(sessionRepository).save(any(ConversationSession.class));
    }

    @Test
    void sendMessage_plainResponse_staysGathering() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmResponse llmResponse = mockLlmResponse("What framework do you prefer?");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llmResponse);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "hi");

        assertThat(response.getContent()).isEqualTo("What framework do you prefer?");
        assertThat(response.getSessionStatus()).isEqualTo("GATHERING");
        assertThat(response.getSparkId()).isNull();
    }

    @Test
    void sendMessage_withProposeMarker_transitionsToProposing() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sparkClassifierService.classify(anyString())).thenReturn(SparkType.CODE);

        LlmResponse llmResponse = mockLlmResponse("Here's my plan:\n- React frontend\n- Node backend\nReady to start?\n<<<PROPOSE>>>");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llmResponse);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "React and Node");

        assertThat(response.getContent()).doesNotContain("<<<PROPOSE>>>");
        assertThat(response.getSessionStatus()).isEqualTo("PROPOSING");
    }

    @Test
    void sendMessage_withStartMarker_createsSparkAndPipeline() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        session.markProposing("CODE", "Build a React todo app with Node backend");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Spark spark = Spark.create("user-1", "build me a todo app");
        when(sparkService.create(anyString(), anyString())).thenReturn(spark);
        when(sparkService.classify(anyString(), anyString(), any())).thenReturn(spark);
        when(sparkService.markExecuting(anyString(), anyString(), any(), any())).thenReturn(spark);

        PipelineRun run = PipelineRun.create("user-1", spark.getId(), "build me a todo app", null, "FULL_PDLC", java.util.List.of(), 50.0);
        when(pdlcRouter.route(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyDouble()))
            .thenReturn(Optional.of(run));

        LlmResponse llmResponse = mockLlmResponse("Great, starting now!\n<<<START>>>");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llmResponse);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "yes go ahead");

        assertThat(response.getContent()).doesNotContain("<<<START>>>");
        assertThat(response.getSessionStatus()).isEqualTo("ACTIVE");
        assertThat(response.getSparkId()).isNotNull();
        assertThat(response.getPipelineRunId()).isNotNull();
        verify(sparkService).create(anyString(), anyString());
    }

    @Test
    void sendMessage_sessionNotFound_throwsException() {
        when(sessionRepository.findByIdAndUserId("missing", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage("missing", "user-1", "hello"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Session not found");
    }

    private LlmResponse mockLlmResponse(String content) {
        LlmResponse response = mock(LlmResponse.class);
        when(response.getContent()).thenReturn(content);
        when(response.getTotalTokens()).thenReturn(100);
        return response;
    }
}
```

- [ ] **Step 2: Run tests — verify they FAIL**

Run: `./gradlew :service:service-conversation:test`

Expected: compilation failure (ConversationService not found). TDD red.

- [ ] **Step 3: Implement ConversationService**

```java
package io.tacticl.service.conversation.service;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.conversation.entity.ConversationMessage;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import io.tacticl.service.conversation.dto.ConversationResponse;
import io.tacticl.service.conversation.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String PROPOSE_MARKER = "<<<PROPOSE>>>";
    private static final String START_MARKER = "<<<START>>>";
    private static final String CONVERSATION_MODEL = "claude-sonnet-4-6";

    private static final String GATHERING_SYSTEM_PROMPT = """
            You are Tacticl, a personal AI assistant gathering requirements before starting work.

            Rules:
            1. Ask ONE clarifying question at a time. Never more than one per message.
            2. Be conversational and concise. Match the user's energy.
            3. When you fully understand what's needed, present a bullet-point plan summary and \
            ask "Ready to start?". End that exact message with this marker on its own line: <<<PROPOSE>>>
            4. If the user approves ("yes", "go ahead", "start", "looks good", "perfect", etc.), \
            write a short confirmation. End that message with this marker on its own line: <<<START>>>
            5. If the user revises your proposal, go back to clarifying — do NOT use <<<PROPOSE>>> \
            until you have a final plan again.

            Be natural. You're a helpful colleague, not a form.
            """;

    private static final String ACTIVE_SYSTEM_PROMPT_TEMPLATE = """
            You are Tacticl, a personal AI assistant. You previously gathered requirements and \
            started a %s task for this user (spark ID: %s).

            Current pipeline status: %s

            You can:
            - Answer questions about what's happening in the pipeline
            - Acknowledge course corrections the user wants to make (note: applying live changes \
            to a running pipeline requires human review via the checkpoint system)

            Keep responses concise and informative.
            """;

    private final ConversationSessionRepository sessionRepository;
    private final AnthropicDirectClient anthropicClient;
    private final SparkService sparkService;
    private final SparkClassifierService sparkClassifierService;
    private final PdlcRouter pdlcRouter;
    private final PipelineRunRepository pipelineRunRepository;

    public ConversationService(ConversationSessionRepository sessionRepository,
                               AnthropicDirectClient anthropicClient,
                               SparkService sparkService,
                               SparkClassifierService sparkClassifierService,
                               PdlcRouter pdlcRouter,
                               PipelineRunRepository pipelineRunRepository) {
        this.sessionRepository = sessionRepository;
        this.anthropicClient = anthropicClient;
        this.sparkService = sparkService;
        this.sparkClassifierService = sparkClassifierService;
        this.pdlcRouter = pdlcRouter;
        this.pipelineRunRepository = pipelineRunRepository;
    }

    public ConversationSession createSession(String userId, String firstMessage) {
        ConversationSession session = ConversationSession.create(userId, firstMessage);
        return sessionRepository.save(session);
    }

    public MessageResponse sendMessage(String sessionId, String userId, String userMessage) {
        ConversationSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.addMessage(ConversationMessage.user(userMessage));

        String systemPrompt = buildSystemPrompt(session);
        List<LlmMessage> messages = buildMessages(session);

        LlmResponse llmResponse = anthropicClient.generateContent(CONVERSATION_MODEL, messages, systemPrompt);
        String rawContent = llmResponse != null && llmResponse.getContent() != null
                ? llmResponse.getContent()
                : "I didn't quite catch that. Could you try again?";

        String sparkId = null;
        String pipelineRunId = null;

        if (rawContent.contains(START_MARKER)) {
            String cleanContent = rawContent.replace(START_MARKER, "").trim();
            session.addMessage(ConversationMessage.assistant(cleanContent));

            StartResult result = startImplementation(session, userId);
            session.markActive(result.sparkId());
            sessionRepository.save(session);

            return new MessageResponse(cleanContent, SessionStatus.ACTIVE.name(),
                    result.sparkId(), result.pipelineRunId());

        } else if (rawContent.contains(PROPOSE_MARKER)) {
            String cleanContent = rawContent.replace(PROPOSE_MARKER, "").trim();
            SparkType detectedType = sparkClassifierService.classify(userMessage);
            session.markProposing(detectedType.name(), cleanContent);
            session.addMessage(ConversationMessage.assistant(cleanContent));
            sessionRepository.save(session);

            return new MessageResponse(cleanContent, SessionStatus.PROPOSING.name(), null, null);

        } else {
            // Plain conversational turn — still gathering or staying in proposing if user revised
            if (session.getStatus() == SessionStatus.PROPOSING) {
                session.revertToGathering();
            }
            session.addMessage(ConversationMessage.assistant(rawContent));
            sessionRepository.save(session);

            return new MessageResponse(rawContent, session.getStatus().name(), null, null);
        }
    }

    public List<ConversationResponse> listSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(ConversationResponse::from)
                .toList();
    }

    public Optional<ConversationResponse> getSession(String sessionId, String userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .map(ConversationResponse::from);
    }

    private String buildSystemPrompt(ConversationSession session) {
        if (session.getStatus() == SessionStatus.ACTIVE && session.getSparkId() != null) {
            String status = pipelineRunRepository
                    .findBySparkIdAndUserId(session.getSparkId(), session.getUserId())
                    .map(run -> run.getStatus().name())
                    .orElse("UNKNOWN");
            return ACTIVE_SYSTEM_PROMPT_TEMPLATE.formatted(
                    session.getDetectedSparkType(), session.getSparkId(), status);
        }
        return GATHERING_SYSTEM_PROMPT;
    }

    private List<LlmMessage> buildMessages(ConversationSession session) {
        List<LlmMessage> messages = new ArrayList<>();
        for (ConversationMessage msg : session.getMessages()) {
            if ("user".equals(msg.getRole())) {
                messages.add(LlmMessage.user(msg.getContent()));
            } else {
                messages.add(LlmMessage.assistant(msg.getContent()));
            }
        }
        return messages;
    }

    private StartResult startImplementation(ConversationSession session, String userId) {
        // Use the full proposal text so PDLC receives the complete requirements, not the truncated title
        String sparkInput = session.getProposalText() != null
                ? session.getProposalText()
                : session.getTitle();
        SparkType sparkType = session.getDetectedSparkType() != null
                ? SparkType.valueOf(session.getDetectedSparkType())
                : SparkType.CODE;

        Spark spark = sparkService.create(userId, sparkInput);
        spark = sparkService.classify(spark.getId(), userId, sparkType);

        if (sparkType == SparkType.CODE || sparkType == SparkType.DEVOPS) {
            Optional<PipelineRun> runOpt = pdlcRouter.route(
                    userId, spark.getId(), sparkInput, null, sparkType, List.of(), null, 50.0);
            if (runOpt.isPresent()) {
                sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);
                log.info("Conversation {} routed spark {} to pipeline run {}",
                        session.getId(), spark.getId(), runOpt.get().getId());
                return new StartResult(spark.getId(), runOpt.get().getId());
            }
        }

        sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);
        log.info("Conversation {} created spark {} for type {}", session.getId(), spark.getId(), sparkType);
        return new StartResult(spark.getId(), null);
    }

    private record StartResult(String sparkId, String pipelineRunId) {}
}
```

- [ ] **Step 4: Run tests — verify they PASS**

Run: `./gradlew :service:service-conversation:test`

Expected: BUILD SUCCESSFUL, all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add service/service-conversation/src/
git commit -m "feat(service-conversation): add ConversationService with marker-based state transitions"
```

---

## Chunk 3: Controller + Integration

### Task 8: ConversationController

**Files:**
- Create: `service/service-conversation/src/main/java/io/tacticl/service/conversation/controller/ConversationController.java`
- Create: `service/service-conversation/src/test/java/io/tacticl/service/conversation/controller/ConversationControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Use direct method invocation (same pattern as existing service tests) — avoids Spring MVC filter chain issues with Cidadel `@RequireAuth`.

```java
package io.tacticl.service.conversation.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.service.conversation.dto.ConversationResponse;
import io.tacticl.service.conversation.dto.CreateConversationRequest;
import io.tacticl.service.conversation.dto.MessageResponse;
import io.tacticl.service.conversation.dto.SendMessageRequest;
import io.tacticl.service.conversation.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock ConversationService conversationService;
    ConversationController controller;

    AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        controller = new ConversationController(conversationService);
        user = mock(AuthenticatedUser.class);
        when(user.getUserId()).thenReturn("user-1");
    }

    @Test
    void createConversation_delegatesToServiceAndReturns200() {
        ConversationSession session = ConversationSession.create("user-1", "build a todo app");
        when(conversationService.createSession("user-1", "build a todo app")).thenReturn(session);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setMessage("build a todo app");

        ResponseEntity<ConversationResponse> response = controller.createConversation(request, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo("GATHERING");
    }

    @Test
    void sendMessage_delegatesToServiceAndReturnsContent() {
        MessageResponse msg = new MessageResponse("What tech stack?", "GATHERING", null, null);
        when(conversationService.sendMessage("sess-1", "user-1", "help me")).thenReturn(msg);

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage("help me");

        ResponseEntity<MessageResponse> response = controller.sendMessage("sess-1", request, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getContent()).isEqualTo("What tech stack?");
        assertThat(response.getBody().getSessionStatus()).isEqualTo("GATHERING");
    }

    @Test
    void listSessions_returnsListFromService() {
        ConversationResponse r = ConversationResponse.from(
            ConversationSession.create("user-1", "build a todo app"));
        when(conversationService.listSessions("user-1")).thenReturn(List.of(r));

        ResponseEntity<List<ConversationResponse>> response = controller.listSessions(user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getStatus()).isEqualTo("GATHERING");
    }

    @Test
    void getSession_notFound_returns404() {
        when(conversationService.getSession("missing", "user-1")).thenReturn(Optional.empty());

        ResponseEntity<ConversationResponse> response = controller.getSession("missing", user);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void getSession_found_returns200() {
        ConversationSession session = ConversationSession.create("user-1", "my session");
        when(conversationService.getSession("sess-1", "user-1"))
            .thenReturn(Optional.of(ConversationResponse.from(session)));

        ResponseEntity<ConversationResponse> response = controller.getSession("sess-1", user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo("GATHERING");
    }
}
```

- [ ] **Step 2: Run tests — verify they FAIL**

Run: `./gradlew :service:service-conversation:test --tests "*.ConversationControllerTest"`

Expected: compilation failure (ConversationController not found). TDD red.

- [ ] **Step 3: Implement ConversationController**

```java
package io.tacticl.service.conversation.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.service.conversation.dto.ConversationResponse;
import io.tacticl.service.conversation.dto.CreateConversationRequest;
import io.tacticl.service.conversation.dto.MessageResponse;
import io.tacticl.service.conversation.dto.SendMessageRequest;
import io.tacticl.service.conversation.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthUser AuthenticatedUser user) {
        ConversationSession session = conversationService.createSession(
                user.getUserId(), request.getMessage());
        return ResponseEntity.ok(ConversationResponse.from(session));
    }

    @PostMapping("/{sessionId}/messages")
    @RequireAuth
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthUser AuthenticatedUser user) {
        MessageResponse response = conversationService.sendMessage(
                sessionId, user.getUserId(), request.getMessage());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<ConversationResponse>> listSessions(
            @AuthUser AuthenticatedUser user) {
        return ResponseEntity.ok(conversationService.listSessions(user.getUserId()));
    }

    @GetMapping("/{sessionId}")
    @RequireAuth
    public ResponseEntity<ConversationResponse> getSession(
            @PathVariable String sessionId,
            @AuthUser AuthenticatedUser user) {
        return conversationService.getSession(sessionId, user.getUserId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 4: Run all service-conversation tests**

Run: `./gradlew :service:service-conversation:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add service/service-conversation/src/main/java/io/tacticl/service/conversation/controller/
git add service/service-conversation/src/test/
git commit -m "feat(service-conversation): add ConversationController with full CRUD endpoints"
```

---

### Task 9: Wire into application

**Files:**
- Modify: `application-api/build.gradle.kts`

- [ ] **Step 1: Add service-conversation to application-api**

In `application-api/build.gradle.kts`, add after the last `service:*` line:
```kotlin
    implementation(project(":service:service-conversation"))
```

- [ ] **Step 2: Verify MongoConfig scans new package**

Open `application-api/src/main/java/io/strategiz/social/application/config/MongoConfig.java`.

Confirm `basePackages = {"io.tacticl"}` is present. The new `ConversationSessionRepository` is in `io.tacticl.data.conversation.repository` — already covered. No change needed.

- [ ] **Step 3: Full build**

Run: `./gradlew build -x test --no-daemon`

Expected: BUILD SUCCESSFUL. All modules compile.

- [ ] **Step 4: Run all tests**

Run: `./gradlew test --no-daemon`

Expected: BUILD SUCCESSFUL. Zero test failures.

- [ ] **Step 5: Final commit**

```bash
git add application-api/build.gradle.kts settings.gradle.kts
git commit -m "feat: wire service-conversation into application; register modules in settings"
```

---

## Done

The conversational chat feature is complete when:
- `POST /v1/conversations` creates a session (GATHERING status)
- `POST /v1/conversations/{id}/messages` drives back-and-forth conversation with the LLM
- `<<<PROPOSE>>>` marker transitions session to PROPOSING and strips the marker from the response
- `<<<START>>>` marker creates a Spark, routes to PDLC (CODE/DEVOPS) or cloud, transitions to ACTIVE
- `GET /v1/conversations` lists all sessions for a user
- `GET /v1/conversations/{id}` returns full message history
- All tests pass, full build succeeds
