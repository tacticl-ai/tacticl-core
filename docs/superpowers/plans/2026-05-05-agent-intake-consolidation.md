# Agent Intake Consolidation — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract a single `AgentCommandService` and `TranscriptionService` so all channels (HTTP, Telegram, future) share orchestration and transcription. Wire Telegram voice messages through the same path. Ship Telegram DM commands. Spec: `docs/superpowers/specs/2026-05-05-agent-intake-consolidation-design.md`.

**Architecture:** Two new services in `business-agent`. `AgentController` and `TelegramSparkInitiator` become thin adapters. New `client-whisper` module owns the OpenAI Whisper REST call. New `VoiceMessageHandler` wires the Telegram voice branch.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Gradle (Kotlin DSL), JUnit 6, Mockito, Spring `MockRestServiceServer` for HTTP client tests.

---

## File Structure

**Create:**
- `business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommand.java` — input record.
- `business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommandResult.java` — output record.
- `business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommandService.java` — orchestration core.
- `business/business-agent/src/main/java/io/tacticl/business/agent/transcription/TranscriptionService.java` — interface.
- `business/business-agent/src/main/java/io/tacticl/business/agent/transcription/WhisperTranscriptionService.java` — impl.
- `client/client-whisper/build.gradle.kts` — new module.
- `client/client-whisper/src/main/java/io/tacticl/client/whisper/WhisperClient.java` — REST client.
- `client/client-whisper/src/main/java/io/tacticl/client/whisper/config/WhisperConfig.java` — config.
- `client/client-whisper/src/main/java/io/tacticl/client/whisper/config/WhisperVaultConfig.java` — Vault loader.
- `client/client-whisper/src/main/java/io/tacticl/client/whisper/dto/TranscriptionResponse.java` — response DTO.
- `business/business-telegram/src/main/java/io/tacticl/business/telegram/event/VoiceMessageHandler.java` — Telegram voice adapter.
- `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/WhoamiCommand.java` — DM command.
- `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/ProjectsCommand.java` — DM command.
- `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/UnlinkCommand.java` — DM command.
- `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/TelegramFile.java` — `getFile` response DTO.
- All matching `*Test.java` files in `src/test/java/...`.

**Modify:**
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java` — slim `executeCommand`, add `executeVoice`.
- `business/business-telegram/src/main/java/io/tacticl/business/telegram/spark/TelegramSparkInitiator.java` — slim to thin adapter.
- `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramDispatchService.java` — wire voice branch.
- `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramCommandRegistrar.java` — register `/whoami`, `/projects`, `/unlink` for `all_private_chats`.
- `client/client-telegram/src/main/java/io/tacticl/client/telegram/TelegramBotClient.java` — add `getFile` + `downloadFile`.
- `settings.gradle.kts` — register `:client:client-whisper`.
- `client/build.gradle.kts` — include client-whisper if parent applies cross-cutting deps.
- `application/build.gradle.kts` — add `:client:client-whisper` dependency.
- `application/src/main/resources/application*.properties` — Whisper config keys.
- `docs/runbooks/telegram-bot.md` — note voice flow + DM commands.

---

## Chunk 1: AgentCommand DTOs + AgentCommandService extraction

### Task 1: `AgentCommand` input record

**Files:**
- Create: `business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommand.java`

- [ ] **Step 1: Create the record**

```java
package io.tacticl.business.agent.command;

import io.tacticl.data.sparks.entity.SparkInitiatorSource;

/**
 * Single input shape for the agent orchestration core. All channels (HTTP,
 * Telegram, future) build one of these and hand it to {@link AgentCommandService}.
 *
 * <p>Nullable fields:
 * <ul>
 *   <li>{@code model} — null lets the service apply the default (Sonnet for
 *       non-pipeline path).</li>
 *   <li>{@code costCeilingUsd} — null lets the service apply the documented
 *       default ($50 today; future {@code UserConfig} lookup belongs in the
 *       service).</li>
 *   <li>{@code initiatorSource} — null is treated as direct HTTP user.</li>
 *   <li>{@code projectId} — only set for channel-scoped sparks (Telegram
 *       group, future Slack channel).</li>
 *   <li>{@code repoUrl} — pipeline router accepts null as "no repo".</li>
 * </ul>
 */
public record AgentCommand(
        String userId,
        String text,
        String model,
        Double costCeilingUsd,
        SparkInitiatorSource initiatorSource,
        String projectId,
        String repoUrl) {

    public static AgentCommand fromHttp(String userId, String text, String model) {
        return new AgentCommand(userId, text, model, null, null, null, null);
    }

    public static AgentCommand fromTelegramGroup(String userId, String text,
                                                  String projectId, String repoUrl) {
        return new AgentCommand(userId, text, null, null,
                SparkInitiatorSource.TELEGRAM_GROUP, projectId, repoUrl);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommand.java
git commit -m "feat(business-agent): AgentCommand input record for unified intake"
```

---

### Task 2: `AgentCommandResult` output record

**Files:**
- Create: `business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommandResult.java`

- [ ] **Step 1: Create the record**

```java
package io.tacticl.business.agent.command;

import java.util.List;

/**
 * Single output shape from {@link AgentCommandService}. Adapters map this to
 * their channel-native response (HTTP {@code AgentCommandResponse}, Telegram
 * outbound text). All fields except {@code sparkId} are nullable: pipeline
 * runs return {@code pipelineRunId} + tier; sync cloud runs return
 * {@code responseText} + token cost; failures return {@code succeeded=false}.
 */
public record AgentCommandResult(
        String sparkId,
        String sparkStatus,
        String executionMode,
        String pipelineRunId,
        String pipelineTier,
        String responseText,
        List<Object> actions,
        boolean succeeded,
        String model,
        int tokensUsed) {

    public static AgentCommandResult pipeline(String sparkId, String runId, String tier) {
        return new AgentCommandResult(sparkId, "EXECUTING", "ASYNC",
                runId, tier, null, List.of(), true, null, 0);
    }

    public static AgentCommandResult cloudCompleted(String sparkId, String text, String model, int tokens) {
        return new AgentCommandResult(sparkId, "COMPLETED", "SYNC",
                null, "SIMPLE", text, List.of(), true, model, tokens);
    }

    public static AgentCommandResult cloudFailed(String sparkId, String message) {
        return new AgentCommandResult(sparkId, "FAILED", "SYNC",
                null, "SIMPLE", message, List.of(), false, null, 0);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommandResult.java
git commit -m "feat(business-agent): AgentCommandResult output record"
```

---

### Task 3: `AgentCommandService` — TDD

**Files:**
- Create: `business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommandService.java`
- Test: `business/business-agent/src/test/java/io/tacticl/business/agent/command/AgentCommandServiceTest.java`

The service moves the body of `AgentController.executeCommand` (lines 58–121) into a reusable bean. Behavior must be identical for the HTTP path, plus three additions: (a) `initiatorSource`/`projectId` propagated to `SparkService.create`, (b) `costCeilingUsd` defaulted when null, (c) returns structured `AgentCommandResult` instead of HTTP DTO.

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.business.agent.command;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkInitiatorSource;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentCommandServiceTest {

    private SparkService sparks;
    private SparkClassifierService classifier;
    private AnthropicDirectClient anthropic;
    private PdlcRouter pdlcRouter;
    private AgentCommandService service;

    @BeforeEach
    void setUp() {
        sparks = mock(SparkService.class);
        classifier = mock(SparkClassifierService.class);
        anthropic = mock(AnthropicDirectClient.class);
        pdlcRouter = mock(PdlcRouter.class);
        service = new AgentCommandService(sparks, classifier, anthropic, pdlcRouter);
    }

    @Test
    void routesCodeSparkToPdlcAndReturnsRunId() {
        Spark spark = new Spark();
        spark.setId("spark-1");
        when(sparks.create(eq("u1"), eq("ship it"), eq(SparkInitiatorSource.TELEGRAM_GROUP), eq("u1"), eq("p1")))
                .thenReturn(spark);
        when(classifier.classify("ship it")).thenReturn(SparkType.CODE);
        when(sparks.classify("spark-1", "u1", SparkType.CODE)).thenReturn(spark);
        when(sparks.markExecuting(eq("spark-1"), eq("u1"), eq(SparkRoute.CLOUD), any())).thenReturn(spark);
        PipelineRun run = new PipelineRun();
        run.setId("run-1");
        when(pdlcRouter.route(eq("u1"), eq("spark-1"), eq("ship it"), eq("https://r"),
                eq(SparkType.CODE), eq(List.of()), any(), eq(50.0))).thenReturn(Optional.of(run));

        AgentCommand cmd = AgentCommand.fromTelegramGroup("u1", "ship it", "p1", "https://r");
        AgentCommandResult result = service.execute(cmd);

        assertThat(result.sparkId()).isEqualTo("spark-1");
        assertThat(result.pipelineRunId()).isEqualTo("run-1");
        assertThat(result.executionMode()).isEqualTo("ASYNC");
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void fallsBackToAnthropicForNonPipelineSparks() {
        Spark spark = new Spark();
        spark.setId("spark-2");
        when(sparks.create(eq("u1"), eq("haiku please"), any(), eq("u1"), any())).thenReturn(spark);
        when(classifier.classify("haiku please")).thenReturn(SparkType.CREATIVE);
        when(sparks.classify("spark-2", "u1", SparkType.CREATIVE)).thenReturn(spark);
        when(sparks.markExecuting(eq("spark-2"), eq("u1"), eq(SparkRoute.CLOUD), any())).thenReturn(spark);

        LlmResponse llm = mock(LlmResponse.class);
        when(llm.getContent()).thenReturn("the rain falls / softly on stones / spring returns");
        when(llm.getTotalTokens()).thenReturn(42);
        when(anthropic.generateContent(eq("claude-sonnet-4-6"), anyList(), anyString())).thenReturn(llm);

        AgentCommandResult result = service.execute(AgentCommand.fromHttp("u1", "haiku please", null));

        assertThat(result.responseText()).contains("rain falls");
        assertThat(result.tokensUsed()).isEqualTo(42);
        assertThat(result.executionMode()).isEqualTo("SYNC");
        verify(sparks).markCompleted("spark-2", "u1", 42, "claude-sonnet-4-6");
    }

    @Test
    void marksSparkFailedWhenAnthropicThrows() {
        Spark spark = new Spark();
        spark.setId("spark-3");
        when(sparks.create(eq("u1"), eq("hi"), any(), eq("u1"), any())).thenReturn(spark);
        when(classifier.classify("hi")).thenReturn(SparkType.GENERAL);
        when(sparks.classify("spark-3", "u1", SparkType.GENERAL)).thenReturn(spark);
        when(sparks.markExecuting(eq("spark-3"), eq("u1"), eq(SparkRoute.CLOUD), any())).thenReturn(spark);
        when(anthropic.generateContent(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("upstream 503"));

        AgentCommandResult result = service.execute(AgentCommand.fromHttp("u1", "hi", null));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.sparkStatus()).isEqualTo("FAILED");
        verify(sparks).markFailed("spark-3", "u1");
    }

    @Test
    void honoursCallerCostCeilingWhenProvided() {
        Spark spark = new Spark();
        spark.setId("spark-4");
        when(sparks.create(any(), any(), any(), any(), any())).thenReturn(spark);
        when(classifier.classify(any())).thenReturn(SparkType.DEVOPS);
        when(sparks.classify(any(), any(), any())).thenReturn(spark);
        when(sparks.markExecuting(any(), any(), any(), any())).thenReturn(spark);
        when(pdlcRouter.route(any(), any(), any(), any(), any(), any(), any(), eq(12.5)))
                .thenReturn(Optional.of(new PipelineRun()));

        AgentCommand cmd = new AgentCommand("u1", "deploy", null, 12.5,
                SparkInitiatorSource.TELEGRAM_GROUP, "p1", null);
        service.execute(cmd);

        verify(pdlcRouter).route(eq("u1"), any(), eq("deploy"), any(),
                eq(SparkType.DEVOPS), eq(List.of()), any(), eq(12.5));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :business:business-agent:test --tests AgentCommandServiceTest -i
```

Expected: compile failure — `AgentCommandService` does not exist yet.

- [ ] **Step 3: Implement `AgentCommandService`**

```java
package io.tacticl.business.agent.command;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkInitiatorSource;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Single orchestration core for agent commands. Both the HTTP controller
 * (mobile/web) and Telegram in-process callers funnel through this bean so
 * spark creation, classification, pipeline routing, and the simple-cloud
 * fallback live in exactly one place.
 *
 * <p>Replaces the body of {@code AgentController.executeCommand} (now a thin
 * adapter) and the duplicated routing logic in
 * {@code TelegramSparkInitiator}.
 */
@Service
public class AgentCommandService {

    private static final Logger log = LoggerFactory.getLogger(AgentCommandService.class);

    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    private static final double DEFAULT_COST_CEILING_USD = 50.0;
    private static final String SYSTEM_PROMPT = """
            You are Tacticl, a personal AI assistant that can remote into devices and automate tasks.
            You help users with research, code, social media, content creation, data analysis, and more.
            Be concise, helpful, and action-oriented. When you can help directly, do so.
            """;

    private final SparkService sparks;
    private final SparkClassifierService classifier;
    private final AnthropicDirectClient anthropic;
    private final PdlcRouter pdlcRouter;

    public AgentCommandService(SparkService sparks,
                                SparkClassifierService classifier,
                                AnthropicDirectClient anthropic,
                                PdlcRouter pdlcRouter) {
        this.sparks = sparks;
        this.classifier = classifier;
        this.anthropic = anthropic;
        this.pdlcRouter = pdlcRouter;
    }

    public AgentCommandResult execute(AgentCommand cmd) {
        SparkInitiatorSource source = cmd.initiatorSource();
        Spark spark = sparks.create(cmd.userId(), cmd.text(), source, cmd.userId(), cmd.projectId());
        SparkType type = classifier.classify(cmd.text());
        spark = sparks.classify(spark.getId(), cmd.userId(), type);

        if (type == SparkType.CODE || type == SparkType.DEVOPS) {
            double ceiling = cmd.costCeilingUsd() != null ? cmd.costCeilingUsd() : DEFAULT_COST_CEILING_USD;
            Optional<PipelineRun> run = pdlcRouter.route(cmd.userId(), spark.getId(), cmd.text(),
                    cmd.repoUrl(), type, List.of(), null, ceiling);
            if (run.isPresent()) {
                sparks.markExecuting(spark.getId(), cmd.userId(), SparkRoute.CLOUD, null);
                log.info("[AgentCommand] Spark {} routed to PDLC run {}", spark.getId(), run.get().getId());
                return AgentCommandResult.pipeline(spark.getId(), run.get().getId(), "FULL_PDLC");
            }
            log.warn("[AgentCommand] PDLC disabled for spark {} type {}; falling through to cloud loop",
                    spark.getId(), type);
        }

        spark = sparks.markExecuting(spark.getId(), cmd.userId(), SparkRoute.CLOUD, null);
        String model = cmd.model() != null ? cmd.model() : DEFAULT_MODEL;
        try {
            List<LlmMessage> messages = List.of(LlmMessage.user(cmd.text()));
            LlmResponse llm = anthropic.generateContent(model, messages, SYSTEM_PROMPT);
            String text = llm != null && llm.getContent() != null ? llm.getContent() : "I processed your request.";
            int tokens = llm != null && llm.getTotalTokens() != null ? llm.getTotalTokens() : 0;
            sparks.markCompleted(spark.getId(), cmd.userId(), tokens, model);
            return AgentCommandResult.cloudCompleted(spark.getId(), text, model, tokens);
        } catch (RuntimeException e) {
            log.error("[AgentCommand] Spark {} failed: {}", spark.getId(), e.getMessage(), e);
            sparks.markFailed(spark.getId(), cmd.userId());
            return AgentCommandResult.cloudFailed(spark.getId(),
                    "I couldn't process that right now. Please try again.");
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :business:business-agent:test --tests AgentCommandServiceTest -i
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommandService.java \
        business/business-agent/src/test/java/io/tacticl/business/agent/command/AgentCommandServiceTest.java
git commit -m "feat(business-agent): AgentCommandService — single orchestration core"
```

---

### Task 4: Slim `AgentController.executeCommand`

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java`
- Test: `service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/AgentControllerTest.java` (update existing or add)

- [ ] **Step 1: Write/extend failing test**

Add a focused test that the controller now calls `AgentCommandService` and maps the result to `AgentCommandResponse`. Mock the service. Existing controller behaviour tests can stay.

```java
@Test
void delegatesToAgentCommandService() {
    AuthenticatedUser user = mock(AuthenticatedUser.class);
    when(user.getUserId()).thenReturn("u1");

    AgentCommandRequest req = new AgentCommandRequest();
    req.setText("hi");
    req.setModel("claude-haiku-4-5");

    when(agentCommandService.execute(any(AgentCommand.class)))
            .thenReturn(AgentCommandResult.cloudCompleted("s1", "ok", "claude-haiku-4-5", 7));

    ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(req, user);

    assertThat(resp.getBody().getResponse()).isEqualTo("ok");
    assertThat(resp.getBody().getSparkId()).isEqualTo("s1");

    ArgumentCaptor<AgentCommand> captor = ArgumentCaptor.forClass(AgentCommand.class);
    verify(agentCommandService).execute(captor.capture());
    assertThat(captor.getValue().userId()).isEqualTo("u1");
    assertThat(captor.getValue().text()).isEqualTo("hi");
    assertThat(captor.getValue().model()).isEqualTo("claude-haiku-4-5");
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :service:service-agent:test --tests AgentControllerTest -i
```

Expected: compile failure or `AgentCommandService` not in constructor.

- [ ] **Step 3: Slim the controller**

Replace the body of `executeCommand` with:

```java
@PostMapping("/command")
@RequireAuth
public ResponseEntity<AgentCommandResponse> executeCommand(
        @Valid @RequestBody AgentCommandRequest request,
        @AuthUser AuthenticatedUser user) {
    String text = request.getText();
    log.info("Agent command from user {}: {}", user.getUserId(),
            text.length() > 100 ? text.substring(0, 100) + "..." : text);

    AgentCommand cmd = AgentCommand.fromHttp(user.getUserId(), text, request.getModel());
    AgentCommandResult result = agentCommandService.execute(cmd);
    return ResponseEntity.ok(toResponse(result));
}

private AgentCommandResponse toResponse(AgentCommandResult result) {
    if (result.pipelineRunId() != null) {
        return AgentCommandResponse.pipeline(result.sparkId(), result.pipelineRunId(),
                result.pipelineTier(), result.pipelineTier(), result.actions());
    }
    AgentCommandResponse resp = new AgentCommandResponse(
            result.responseText(), result.actions(), result.succeeded(), result.model());
    resp.setSparkId(result.sparkId());
    resp.setSparkStatus(result.sparkStatus());
    resp.setExecutionMode(result.executionMode());
    resp.setPipelineTier(result.pipelineTier());
    return resp;
}
```

Update the constructor to inject `AgentCommandService` instead of the four collaborators it used to need. Drop unused imports.

- [ ] **Step 4: Run tests to verify pass**

```bash
./gradlew :service:service-agent:test -i
```

- [ ] **Step 5: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java \
        service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/AgentControllerTest.java
git commit -m "refactor(service-agent): AgentController is a thin adapter on AgentCommandService"
```

---

### Task 5: Slim `TelegramSparkInitiator`

**Files:**
- Modify: `business/business-telegram/src/main/java/io/tacticl/business/telegram/spark/TelegramSparkInitiator.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/spark/TelegramSparkInitiatorTest.java`

The initiator keeps three responsibilities — permission check, provenance, outbound reply — and delegates everything else.

- [ ] **Step 1: Update existing test for new collaborator + behaviour**

Replace the direct `SparkService` + `PdlcRouter` mocks with a single `AgentCommandService` mock. Cover three cases: permission denied; happy path returns "▶️ Started"; service-thrown failure replies with the friendly error.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :business:business-telegram:test --tests TelegramSparkInitiatorTest -i
```

- [ ] **Step 3: Implement**

```java
public void initiate(long chatId, String tacticlUserId, String text,
                     TelegramProjectLink link, String repoUrl) {
    if (text == null || text.isBlank()) {
        reply(chatId, "Spark text is required.");
        return;
    }
    if (text.length() > MAX_SPARK_TEXT_CHARS) {
        reply(chatId, "Your spark text is too long (max " + MAX_SPARK_TEXT_CHARS + " chars).");
        return;
    }
    PermissionCheck check = permissions.require(chatId, tacticlUserId, MemberRole.CONTRIBUTOR);
    if (!check.allowed()) {
        reply(chatId, "You need contributor role to start a spark.");
        return;
    }

    AgentCommand cmd = AgentCommand.fromTelegramGroup(tacticlUserId, text, link.getProjectId(), repoUrl);
    AgentCommandResult result;
    try {
        result = agentCommandService.execute(cmd);
    } catch (RuntimeException e) {
        log.error("Agent command failed for spark in chat {}", chatId, e);
        reply(chatId, "⚠️ Couldn't start the spark. Try again or check with an admin.");
        return;
    }

    if (!result.succeeded()) {
        reply(chatId, "⚠️ " + result.responseText());
        return;
    }
    if (result.pipelineRunId() != null) {
        reply(chatId, "▶️ Started — I'll post updates here.");
    } else {
        reply(chatId, result.responseText());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :business:business-telegram:test --tests TelegramSparkInitiatorTest -i
```

- [ ] **Step 5: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/spark/TelegramSparkInitiator.java \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/spark/TelegramSparkInitiatorTest.java
git commit -m "refactor(business-telegram): TelegramSparkInitiator delegates to AgentCommandService"
```

---

## Chunk 2: TranscriptionService + client-whisper

### Task 6: Create `client-whisper` module skeleton

**Files:**
- Create: `client/client-whisper/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Add `client-whisper/build.gradle.kts`**

Mirror an existing client module like `client-brave-search` for plugin block + dependencies. Required deps: framework-secrets, framework-exception, client-base, spring-web, jackson, junit, mockito, spring-test.

- [ ] **Step 2: Register module**

In `settings.gradle.kts`:
```
include(":client:client-whisper")
```

- [ ] **Step 3: Verify Gradle sees it**

```bash
./gradlew :client:client-whisper:projects
```

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts client/client-whisper/build.gradle.kts
git commit -m "feat(client-whisper): scaffold module"
```

---

### Task 7: `WhisperConfig` + `WhisperVaultConfig`

**Files:**
- Create: `client/client-whisper/src/main/java/io/tacticl/client/whisper/config/WhisperConfig.java`
- Create: `client/client-whisper/src/main/java/io/tacticl/client/whisper/config/WhisperVaultConfig.java`

Follow `client-brave-search` config pattern. Vault path `secret/strategiz/openai`, key `api-key`. Property `tacticl.whisper.enabled` gates the whole client (default `false` so dev environments without Vault still boot).

- [ ] **Step 1: Implement** (~30 lines each, lift from existing client)

- [ ] **Step 2: Commit**

```bash
git add client/client-whisper/src/main/java/io/tacticl/client/whisper/config/
git commit -m "feat(client-whisper): config + Vault-backed key loading"
```

---

### Task 8: `WhisperClient` REST call — TDD

**Files:**
- Create: `client/client-whisper/src/main/java/io/tacticl/client/whisper/WhisperClient.java`
- Create: `client/client-whisper/src/main/java/io/tacticl/client/whisper/dto/TranscriptionResponse.java`
- Test: `client/client-whisper/src/test/java/io/tacticl/client/whisper/WhisperClientTest.java`

OpenAI Whisper endpoint: `POST https://api.openai.com/v1/audio/transcriptions`, multipart with `file` (binary) + `model` (`whisper-1`). Response JSON `{"text": "..."}`.

- [ ] **Step 1: Write the failing test using `MockRestServiceServer`**

```java
@Test
void postsMultipartAndReturnsTranscript() {
    MockRestServiceServer server = MockRestServiceServer.createServer(restClientBuilder.build());
    server.expect(requestTo("https://api.openai.com/v1/audio/transcriptions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer sk-test"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andRespond(withSuccess("{\"text\":\"hello world\"}", MediaType.APPLICATION_JSON));

    String text = client.transcribe("hi.ogg".getBytes(), "audio/ogg");

    assertThat(text).isEqualTo("hello world");
    server.verify();
}
```

- [ ] **Step 2: Run to verify fail**

- [ ] **Step 3: Implement** (~70 lines: extend `BaseHttpClient`, build multipart `LinkedMultiValueMap` with `ByteArrayResource`, call, parse `TranscriptionResponse`)

- [ ] **Step 4: Run to verify pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(client-whisper): WhisperClient — POST /v1/audio/transcriptions"
```

---

### Task 9: `TranscriptionService` interface + Whisper impl

**Files:**
- Create: `business/business-agent/src/main/java/io/tacticl/business/agent/transcription/TranscriptionService.java`
- Create: `business/business-agent/src/main/java/io/tacticl/business/agent/transcription/WhisperTranscriptionService.java`
- Test: `business/business-agent/src/test/java/io/tacticl/business/agent/transcription/WhisperTranscriptionServiceTest.java`

- [ ] **Step 1: Write the failing test** (mock `WhisperClient`, assert delegation; cover blank input → `IllegalArgumentException`).

- [ ] **Step 2: Run to verify fail**

- [ ] **Step 3: Implement**

```java
public interface TranscriptionService {
    String transcribe(byte[] audio, String mimeType);
}

@Service
@ConditionalOnProperty(name = "tacticl.whisper.enabled", havingValue = "true")
public class WhisperTranscriptionService implements TranscriptionService {
    private final WhisperClient client;
    public WhisperTranscriptionService(WhisperClient client) { this.client = client; }
    @Override public String transcribe(byte[] audio, String mimeType) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("audio bytes required");
        }
        return client.transcribe(audio, mimeType);
    }
}
```

- [ ] **Step 4: Run to verify pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(business-agent): TranscriptionService + Whisper impl"
```

---

## Chunk 3: HTTP `/v1/agent/voice` endpoint

### Task 10: `executeVoice` controller method — TDD

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java`
- Test: `service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/AgentControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void voiceEndpointTranscribesThenExecutes() {
    MockMultipartFile file = new MockMultipartFile("audio", "v.ogg", "audio/ogg", "bytes".getBytes());
    AuthenticatedUser user = mock(AuthenticatedUser.class);
    when(user.getUserId()).thenReturn("u1");
    when(transcriptionService.transcribe(any(), eq("audio/ogg"))).thenReturn("hello world");
    when(agentCommandService.execute(any()))
            .thenReturn(AgentCommandResult.cloudCompleted("s1", "ok", "claude-sonnet-4-6", 7));

    ResponseEntity<AgentCommandResponse> resp = controller.executeVoice(file, null, user);

    ArgumentCaptor<AgentCommand> cap = ArgumentCaptor.forClass(AgentCommand.class);
    verify(agentCommandService).execute(cap.capture());
    assertThat(cap.getValue().text()).isEqualTo("hello world");
    assertThat(resp.getBody().getResponse()).isEqualTo("ok");
}
```

- [ ] **Step 2: Run to verify fail**

- [ ] **Step 3: Implement**

```java
@PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@RequireAuth
public ResponseEntity<AgentCommandResponse> executeVoice(
        @RequestPart("audio") MultipartFile audio,
        @RequestPart(value = "model", required = false) String model,
        @AuthUser AuthenticatedUser user) throws IOException {
    if (audio.isEmpty()) {
        return ResponseEntity.badRequest().body(new AgentCommandResponse(
                "Audio file is empty.", List.of(), false, null));
    }
    String transcript = transcriptionService.transcribe(audio.getBytes(), audio.getContentType());
    AgentCommand cmd = AgentCommand.fromHttp(user.getUserId(), transcript, model);
    AgentCommandResult result = agentCommandService.execute(cmd);
    return ResponseEntity.ok(toResponse(result));
}
```

- [ ] **Step 4: Run to verify pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(service-agent): POST /v1/agent/voice — multipart audio intake"
```

---

## Chunk 4: Telegram client `getFile` + `downloadFile`

### Task 11: `TelegramFile` DTO + `getFile`

**Files:**
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/TelegramFile.java`
- Modify: `client/client-telegram/src/main/java/io/tacticl/client/telegram/TelegramBotClient.java`
- Test: `client/client-telegram/src/test/java/io/tacticl/client/telegram/TelegramBotClientTest.java`

`getFile` returns `{"ok": true, "result": {"file_id": "...", "file_unique_id": "...", "file_size": N, "file_path": "voice/file_5.ogg"}}`.

- [ ] **Step 1: Write the failing test** (`MockRestServiceServer` expects `getFile?file_id=…`, returns the canonical envelope; assert returned record).

- [ ] **Step 2: Implement**

```java
public record TelegramFile(String file_id, String file_unique_id, Long file_size, String file_path) {}

public Optional<TelegramFile> getFile(String fileId) {
    String url = config.getApiUrl() + "/getFile?file_id=" + fileId;
    rateLimiter.consume(1);
    GetFileResponse resp = restClient.get().uri(url).retrieve().body(GetFileResponse.class);
    return resp != null && resp.ok() ? Optional.ofNullable(resp.result()) : Optional.empty();
}

private record GetFileResponse(boolean ok, TelegramFile result) {}
```

- [ ] **Step 3: Run + commit**

```bash
git commit -m "feat(client-telegram): getFile API + TelegramFile DTO"
```

---

### Task 12: `downloadFile` helper

**Files:**
- Modify: `client/client-telegram/src/main/java/io/tacticl/client/telegram/TelegramBotClient.java`
- Test: `client/client-telegram/src/test/java/io/tacticl/client/telegram/TelegramBotClientTest.java`

URL pattern: `https://api.telegram.org/file/bot<token>/<file_path>`. The download URL is **not** the bot API URL — it's a separate CDN host.

- [ ] **Step 1: Write the failing test**

- [ ] **Step 2: Implement**

```java
public byte[] downloadFile(String filePath) {
    String url = "https://api.telegram.org/file/bot" + config.getBotToken() + "/" + filePath;
    rateLimiter.consume(1);
    ResponseEntity<byte[]> resp = restClient.get().uri(url).retrieve().toEntity(byte[].class);
    return resp.getBody() != null ? resp.getBody() : new byte[0];
}
```

- [ ] **Step 3: Run + commit**

```bash
git commit -m "feat(client-telegram): downloadFile — fetch from Telegram CDN"
```

---

## Chunk 5: Telegram `VoiceMessageHandler`

### Task 13: `VoiceMessageHandler` — TDD

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/event/VoiceMessageHandler.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/event/VoiceMessageHandlerTest.java`

Flow: identify chat → resolve user (must be linked) → require active project → download voice → transcribe → call `TelegramSparkInitiator.initiate(chatId, userId, transcript, link, null)`.

- [ ] **Step 1: Write the failing test** with five branches:
  1. unlinked user → friendly reply, no transcription call
  2. no active project → friendly reply
  3. happy path → transcribe + initiate
  4. download empty → friendly reply
  5. transcription throws → friendly reply

- [ ] **Step 2: Run to verify fail**

- [ ] **Step 3: Implement**

```java
@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class VoiceMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(VoiceMessageHandler.class);
    private final TelegramBotClient bot;
    private final TranscriptionService transcription;
    private final TelegramIdentityResolver identity;
    private final TelegramProjectLinkRepository projects;
    private final TelegramSparkInitiator initiator;
    private final TelegramOutboundQueue outbound;

    public VoiceMessageHandler(TelegramBotClient bot,
                                TranscriptionService transcription,
                                TelegramIdentityResolver identity,
                                TelegramProjectLinkRepository projects,
                                TelegramSparkInitiator initiator,
                                TelegramOutboundQueue outbound) {
        this.bot = bot;
        this.transcription = transcription;
        this.identity = identity;
        this.projects = projects;
        this.initiator = initiator;
        this.outbound = outbound;
    }

    public void handle(Message msg) {
        long chatId = msg.chat().id();
        long fromId = msg.from() != null ? msg.from().id() : 0L;
        Optional<String> userId = identity.resolveByChatId(fromId);
        if (userId.isEmpty()) {
            reply(chatId, "You must link your Tacticl account first.");
            return;
        }
        Optional<TelegramProjectLink> link = projects.findByChatIdAndIsActiveTrue(chatId);
        if (link.isEmpty()) {
            reply(chatId, "No active project in this group. Use /init first.");
            return;
        }
        String fileId = msg.voice().file_id();
        Optional<TelegramFile> file = bot.getFile(fileId);
        if (file.isEmpty() || file.get().file_path() == null) {
            reply(chatId, "⚠️ Couldn't download voice message.");
            return;
        }
        byte[] audio;
        try {
            audio = bot.downloadFile(file.get().file_path());
        } catch (RuntimeException e) {
            log.warn("Voice download failed for chat {}: {}", chatId, e.getMessage());
            reply(chatId, "⚠️ Couldn't download voice message.");
            return;
        }
        if (audio.length == 0) {
            reply(chatId, "⚠️ Couldn't download voice message.");
            return;
        }
        String transcript;
        try {
            transcript = transcription.transcribe(audio, msg.voice().mime_type());
        } catch (RuntimeException e) {
            log.warn("Voice transcription failed for chat {}: {}", chatId, e.getMessage());
            reply(chatId, "⚠️ Couldn't transcribe voice. Try sending text.");
            return;
        }
        log.info("Voice transcribed for chat {} ({} chars)", chatId, transcript.length());
        initiator.initiate(chatId, userId.get(), transcript, link.get(), null);
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
```

- [ ] **Step 4: Run to verify pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(business-telegram): VoiceMessageHandler — voice → transcript → spark"
```

---

### Task 14: Wire `VoiceMessageHandler` into `TelegramDispatchService`

**Files:**
- Modify: `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramDispatchService.java`
- Modify: `business/business-telegram/src/test/java/io/tacticl/business/telegram/TelegramDispatchServiceTest.java`

Replace the silent `voice` drop with a `voiceHandler.handle(msg)` call. Inject `VoiceMessageHandler` (treat as nullable / optional bean: when `tacticl.whisper.enabled=false`, the bean is absent → fall back to current debug-log drop). Implement via `Optional<VoiceMessageHandler>` constructor injection.

- [ ] **Step 1: Update test** to cover (a) handler present → invoked, (b) handler absent → current log behaviour preserved.

- [ ] **Step 2: Implement**

- [ ] **Step 3: Run + commit**

```bash
git commit -m "refactor(telegram): dispatch voice messages to VoiceMessageHandler"
```

---

## Chunk 6: Telegram DM commands (Task 37)

### Task 15: `WhoamiCommand`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/WhoamiCommand.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/command/WhoamiCommandTest.java`

DM-only. Replies with linked user info: handle, email (from `TacticlUser`), or "not linked" prompt.

- [ ] **Step 1–5:** TDD branches: unlinked → prompt, linked → "Linked as @x (email)".

```bash
git commit -m "feat(telegram): /whoami DM command"
```

---

### Task 16: `ProjectsCommand`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/ProjectsCommand.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/command/ProjectsCommandTest.java`

DM-only. Lists active `TelegramProjectLink`s where the sender has any `TelegramMemberGrant` (or is owner). Format: one line per project with role.

- [ ] **Step 1–5:** TDD branches: unlinked → prompt, no projects → "you're not in any projects yet", multi-project → newline list with role.

```bash
git commit -m "feat(telegram): /projects DM command"
```

---

### Task 17: `UnlinkCommand` with owner-orphan guard

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/UnlinkCommand.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/command/UnlinkCommandTest.java`

DM-only. Steps:
1. If unlinked → reply "you weren't linked" and return.
2. For each project where sender is OWNER, count other OWNER grants. If any project has zero other owners → reply with the list and bail (do not unlink).
3. Otherwise: delete all `TelegramMemberGrant`s for sender + `TelegramUserLinker.unlink(sender)` + reply.

- [ ] **Step 1–5:** TDD branches: unlinked, sole owner of one project (blocked), shared owner (allowed → unlink).

```bash
git commit -m "feat(telegram): /unlink DM command with owner-orphan guard"
```

---

### Task 18: Register DM commands in router + registrar

**Files:**
- Modify: `business/business-telegram/src/main/java/io/tacticl/business/telegram/router/TelegramCommandRouter.java` (only if it doesn't auto-discover handlers)
- Modify: `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramCommandRegistrar.java`

- [ ] Audit how the router discovers handlers (list/map of beans). Add the three new commands following that pattern.
- [ ] In `TelegramCommandRegistrar`, append `whoami`, `projects`, `unlink` to the `all_private_chats` scope command list.
- [ ] Update existing registrar test.
- [ ] Commit:

```bash
git commit -m "feat(telegram): publish /whoami /projects /unlink in private scope"
```

---

## Chunk 7: Wire-up + config

### Task 19: Application wiring

**Files:**
- Modify: `application/build.gradle.kts` — add `implementation(project(":client:client-whisper"))`.
- Modify: `application/src/main/resources/application.properties` — add:
  ```
  tacticl.whisper.enabled=false
  tacticl.whisper.base-url=https://api.openai.com
  tacticl.whisper.model=whisper-1
  ```
- Modify: `application/src/main/resources/application-qa.properties` and `application-prod.properties` — set `tacticl.whisper.enabled=true`.

- [ ] **Step 1: Apply**

- [ ] **Step 2: Verify boot**

```bash
./gradlew build -x test
```

- [ ] **Step 3: Commit**

```bash
git commit -m "chore(application): wire client-whisper + Whisper config keys"
```

---

### Task 20: Runbook update

**Files:**
- Modify: `docs/runbooks/telegram-bot.md`

Document:
- Voice messages now transcribed via Whisper and treated as `/spark` content.
- `/whoami`, `/projects`, `/unlink` (DM scope).
- Prerequisite: `tacticl.whisper.enabled=true` + Vault key `secret/strategiz/openai/api-key`.

- [ ] Commit:

```bash
git commit -m "docs(telegram): voice + DM commands runbook update"
```

---

## Chunk 8: Verification

### Task 21: Full build + test

- [ ] Run:

```bash
./gradlew build
```

Expected: green.

- [ ] If failures: read the report under `build/reports/tests/test/index.html` and fix root causes (no `--no-verify`, no test-skipping shortcuts).

---

## Plan Summary

| Chunk | Focus | Depends on | Parallelizable? |
|---|---|---|---|
| 1 | AgentCommandService extraction | — | No (touches AgentController + TelegramSparkInitiator together) |
| 2 | TranscriptionService + Whisper client | — | Yes (independent files) |
| 3 | `/v1/agent/voice` endpoint | Chunks 1, 2 | No |
| 4 | Telegram getFile/downloadFile | — | Yes (independent files) |
| 5 | VoiceMessageHandler + dispatch wiring | Chunks 1, 2, 4 | No |
| 6 | DM commands | — | Yes (independent files; touches registrar last) |
| 7 | Wiring + config | All | No |
| 8 | Verification | All | — |

**Wave 1 (parallel):** 1, 2, 4, 6.
**Wave 2 (after wave 1 lands):** 3, 5.
**Wave 3:** 7, 8.

**Reference skills:** @superpowers:test-driven-development, @superpowers:subagent-driven-development.
