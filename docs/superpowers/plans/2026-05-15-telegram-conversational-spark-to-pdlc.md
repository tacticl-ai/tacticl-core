# Telegram Conversational Spark → PDLC Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Execute in a dedicated worktree (use superpowers:using-git-worktrees first).

**Goal:** Users start a "spark" in a Telegram group; the bot gathers requirements in a long-lived conversation; on alignment, the spark hands off to the PDLC pipeline running against a user-supplied GitHub repo URL; pipeline events stream back into the same conversation. No cost ceiling for testing. First testable end-to-end PDLC run originating from Telegram.

**Architecture:** Reuse the existing `ConversationService` (gather → propose → active state machine, lives in `service-conversation`). Today only the web REST endpoint feeds it; this plan adds a Telegram adapter that finds-or-creates a `ConversationSession` per (projectId, userId), routes every plain-text and `/spark` message through it, and renders assistant responses to Telegram via the existing outbound queue. The session entity gains `projectId`, `initiatorSource`, and `repoUrl` so the pipeline handoff can pass a real repo. A new `ConversationEventChannel` (peer to `TelegramEventChannel`) records pipeline progress as assistant turns in the session history so a future web client can render the same continuous thread. Cost ceiling default goes from $50 to $10,000 (effectively unlimited for our test).

**Tech Stack:** Java 25, Spring Boot 4, MongoDB (Hetzner), Gradle 9.4 (Kotlin DSL). Modules touched: `data-conversation`, `service-conversation`, `business-agent`, `business-pipeline`, `business-telegram`, `application-api`. Tests use JUnit 6 (managed by Spring Boot BOM) + Mockito.

**Out of scope (deferred to a separate plan):**
- Agent-driven repo creation (the agent calling `create_github_repo` as a tool). Repo URL is user-supplied via `/repo` for this iteration.
- GitHub OAuth per-user. We pass `githubToken=""` to the arbiter for now (works for public repos, no PR write-back).
- Unifying `Spark` and `ConversationSession` into one entity. They cross-link via `sparkId`; refactor later.
- Web UI changes in `tacticl-web`. The conversation REST surface already exists; web work is its own follow-up.

---

## Pre-flight

Before Task 1:

1. Create a worktree:
   ```bash
   git worktree add ../tacticl-core-conv-spark conv-spark
   cd ../tacticl-core-conv-spark
   ```
2. Sanity-build to confirm baseline green:
   ```bash
   ./gradlew build -x test --no-daemon
   ```
3. Read these files to understand the cut points the plan touches:
   - `business/business-telegram/.../TelegramDispatchService.java` (entry point that branches to spark vs command)
   - `business/business-telegram/.../spark/TelegramSparkInitiator.java` (current adapter that bypasses ConversationService)
   - `business/business-telegram/.../command/SparkCommand.java` (current `/spark` handler)
   - `service/service-conversation/.../service/ConversationService.java` (the conversation engine to wire into)
   - `data/data-conversation/.../entity/ConversationSession.java` (entity we extend)
   - `business/business-pipeline/.../router/PdlcRouter.java` and `PdlcV2Service.java` (handoff target — unchanged)

---

## Chunk 1: Conversation entity + service enrichments

The conversation primitive already supports gather → propose → active. It does not know about projects, sources, or repo URLs — which is why the existing path passes `repoUrl=null` to the pipeline. This chunk adds those fields and a project-scoped lookup, and lifts the cost ceiling.

### File structure (chunk 1)

| Action | File | Responsibility |
|---|---|---|
| Modify | `data/data-conversation/.../entity/ConversationSession.java` | New fields + factory variant |
| Modify | `data/data-conversation/.../repository/ConversationSessionRepository.java` | Active-session lookup query |
| Modify | `service/service-conversation/.../service/ConversationService.java` | Pass `repoUrl` + raise ceiling; project-scoped sendMessage |
| Modify | `business/business-agent/.../command/AgentCommandService.java` | Bump `DEFAULT_COST_CEILING_USD` |
| Test (new) | `data/data-conversation/src/test/.../ConversationSessionTest.java` | Add tests for new fields/factory |
| Test (new) | `service/service-conversation/src/test/.../ConversationServiceTest.java` | Extend existing test for repoUrl pass-through |

### Task 1: Add `projectId`, `initiatorSource`, `repoUrl` to ConversationSession

**Files:**
- Modify: `data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/ConversationSession.java`
- Test: `data/data-conversation/src/test/java/io/tacticl/data/conversation/entity/ConversationSessionTest.java`

- [ ] **Step 1: Write failing tests**

Add to `ConversationSessionTest.java`:

```java
@Test
void createForTelegramGroupSetsProjectIdAndSource() {
    ConversationSession s = ConversationSession.createForTelegramGroup(
        "user-1", "proj-1", "build me a daily summary bot");
    assertThat(s.getUserId()).isEqualTo("user-1");
    assertThat(s.getProjectId()).isEqualTo("proj-1");
    assertThat(s.getInitiatorSource()).isEqualTo("TELEGRAM_GROUP");
    assertThat(s.getStatus()).isEqualTo(SessionStatus.GATHERING);
    assertThat(s.getRepoUrl()).isNull();
}

@Test
void setRepoUrlUpdatesUpdatedAt() throws InterruptedException {
    ConversationSession s = ConversationSession.create("user-1", "hello");
    Instant initial = s.getUpdatedAt();
    Thread.sleep(2);
    s.setRepoUrl("https://github.com/foo/bar");
    assertThat(s.getRepoUrl()).isEqualTo("https://github.com/foo/bar");
    assertThat(s.getUpdatedAt()).isAfter(initial);
}
```

- [ ] **Step 2: Run tests, verify they fail**

```bash
./gradlew :data:data-conversation:test --tests ConversationSessionTest
```
Expected: `cannot find symbol createForTelegramGroup` / `setRepoUrl`.

- [ ] **Step 3: Add fields and methods to ConversationSession**

Insert next to existing fields (after `private String sparkId;`):

```java
@Indexed private String projectId;
private String initiatorSource;
private String repoUrl;
```

Add factory variant after `create(...)`:

```java
public static ConversationSession createForTelegramGroup(String userId, String projectId, String firstMessage) {
    ConversationSession s = create(userId, firstMessage);
    s.projectId = projectId;
    s.initiatorSource = "TELEGRAM_GROUP";
    return s;
}
```

Add setters and getters:

```java
public String getProjectId() { return projectId; }
public String getInitiatorSource() { return initiatorSource; }
public String getRepoUrl() { return repoUrl; }

public void setRepoUrl(String repoUrl) {
    this.repoUrl = repoUrl;
    this.updatedAt = Instant.now();
}
```

- [ ] **Step 4: Run tests, verify they pass**

```bash
./gradlew :data:data-conversation:test --tests ConversationSessionTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/data-conversation/
git commit -m "feat(data-conversation): add projectId, initiatorSource, repoUrl to ConversationSession"
```

---

### Task 2: Repository — active session lookup by project

**Files:**
- Modify: `data/data-conversation/src/main/java/io/tacticl/data/conversation/repository/ConversationSessionRepository.java`
- Test (integration): see Task 7 (end-to-end). For unit speed we don't add a Mongo IT now; Spring derives the query from method name, mismatches will surface at startup.

- [ ] **Step 1: Add query method**

```java
import io.tacticl.data.conversation.entity.SessionStatus;
import java.util.Collection;

Optional<ConversationSession> findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
        String projectId, String userId, Collection<SessionStatus> statuses);
```

- [ ] **Step 2: Build**

```bash
./gradlew :data:data-conversation:build -x test
```
Expected: BUILD SUCCESSFUL. (Spring Data validates derived queries at runtime; this also catches any obvious typo.)

- [ ] **Step 3: Commit**

```bash
git add data/data-conversation/src/main/java/io/tacticl/data/conversation/repository/
git commit -m "feat(data-conversation): findFirstByProjectIdAndUserIdAndStatusIn for active-session lookup"
```

---

### Task 3: ConversationService — pass `repoUrl` to the pipeline + raise the ceiling

**Files:**
- Modify: `service/service-conversation/src/main/java/io/tacticl/service/conversation/service/ConversationService.java`
- Test: `service/service-conversation/src/test/java/io/tacticl/service/conversation/service/ConversationServiceTest.java`

The current handoff hard-codes `repoUrl=null` and `costCeilingUsd=50.0` at line 186.

- [ ] **Step 1: Write failing test**

Add to `ConversationServiceTest.java` (model the existing `sendMessage_*` tests there — reuse their mock setup style):

```java
@Test
void startImplementationPassesRepoUrlAndHighCeilingToPdlcRouter() {
    // Arrange — session in PROPOSING with repoUrl + CODE type
    ConversationSession session = ConversationSession.createForTelegramGroup(
        "user-1", "proj-1", "build X");
    session.markProposing("CODE", "Plan summary");
    session.setRepoUrl("https://github.com/owner/repo");
    when(sessionRepository.findByIdAndUserId("sid", "user-1")).thenReturn(Optional.of(session));

    // LLM returns the START marker so we hit startImplementation()
    LlmResponse llm = new LlmResponse();
    llm.setContent("Got it, starting now. <<<START>>>");
    when(anthropicClient.generateContent(eq("claude-sonnet-4-6"), anyList(), anyString()))
        .thenReturn(llm);

    Spark spark = mock(Spark.class);
    when(spark.getId()).thenReturn("spark-1");
    when(sparkService.create("user-1", "Plan summary")).thenReturn(spark);
    when(sparkService.classify("spark-1", "user-1", SparkType.CODE)).thenReturn(spark);

    PipelineRun run = mock(PipelineRun.class);
    when(run.getId()).thenReturn("run-1");
    when(pdlcRouter.route(eq("user-1"), eq("spark-1"), eq("Plan summary"),
            eq("https://github.com/owner/repo"), eq(SparkType.CODE),
            eq(List.of()), isNull(), eq(10_000.0))).thenReturn(Optional.of(run));

    // Act
    MessageResponse resp = conversationService.sendMessage("sid", "user-1", "go");

    // Assert
    assertThat(resp.getSparkId()).isEqualTo("spark-1");
    assertThat(resp.getPipelineRunId()).isEqualTo("run-1");
    verify(pdlcRouter).route(eq("user-1"), eq("spark-1"), eq("Plan summary"),
            eq("https://github.com/owner/repo"), eq(SparkType.CODE),
            eq(List.of()), isNull(), eq(10_000.0));
}
```

- [ ] **Step 2: Run test, confirm it fails**

```bash
./gradlew :service:service-conversation:test --tests ConversationServiceTest.startImplementationPassesRepoUrlAndHighCeilingToPdlcRouter
```
Expected: FAIL — `pdlcRouter.route` invoked with `null` repo and `50.0` ceiling.

- [ ] **Step 3: Patch `startImplementation`**

Change the body of `startImplementation` (around line 172) so the call site reads:

```java
if (sparkType == SparkType.CODE || sparkType == SparkType.DEVOPS) {
    Optional<PipelineRun> runOpt = pdlcRouter.route(
            userId,
            spark.getId(),
            sparkInput,
            session.getRepoUrl(),     // was: null
            sparkType,
            List.of(),
            null,
            10_000.0);                // was: 50.0
    if (runOpt.isPresent()) {
        sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);
        log.info("Conversation {} routed spark {} to pipeline run {} repo={}",
                session.getId(), spark.getId(), runOpt.get().getId(), session.getRepoUrl());
        return new StartResult(spark.getId(), runOpt.get().getId());
    }
}
```

Extract `10_000.0` to a private constant for clarity:

```java
private static final double DEFAULT_COST_CEILING_USD = 10_000.0; // effectively uncapped for testing
```

(We will keep this constant in sync with the AgentCommandService bump in Task 4 so HTTP and conversation paths both see the new ceiling.)

- [ ] **Step 4: Run test, verify it passes**

```bash
./gradlew :service:service-conversation:test --tests ConversationServiceTest
```
Expected: PASS for the new test; existing tests should remain green.

- [ ] **Step 5: Commit**

```bash
git add service/service-conversation/
git commit -m "feat(service-conversation): pass session repoUrl + raise ceiling to 10k for PDLC handoff"
```

---

### Task 4: `AgentCommandService` — match the ceiling lift on the HTTP path

**Files:**
- Modify: `business/business-agent/src/main/java/io/tacticl/business/agent/command/AgentCommandService.java`
- Test: `business/business-agent/src/test/java/io/tacticl/business/agent/command/AgentCommandServiceTest.java`

`AgentCommandService.DEFAULT_COST_CEILING_USD = 50.0` (line 37) is the HTTP/legacy direct-spark fallback. The test path created in Task 6 routes through `ConversationService` (Task 3 already handled), but this constant is still consumed by the existing HTTP controller and any direct callers. Lift to match.

- [ ] **Step 1: Write failing test** (or extend an existing test that exercises this default)

In `AgentCommandServiceTest.java`, add or modify an existing test exercising a `CODE`/`DEVOPS` command without an explicit `costCeilingUsd` to assert the call to `pdlcRouter.route(...)` uses `10_000.0`:

```java
@Test
void executeUsesTenThousandDefaultCeilingForCodeSpark() {
    AgentCommand cmd = AgentCommand.fromHttp("user-1", "fix bug", null);
    // existing mocks: sparks.create, classifier.classify -> CODE, pdlcRouter.route empty
    when(classifier.classify("fix bug")).thenReturn(SparkType.CODE);

    Spark spark = mock(Spark.class);
    when(spark.getId()).thenReturn("spark-1");
    when(sparks.create(eq("user-1"), eq("fix bug"), isNull(), eq("user-1"), isNull()))
        .thenReturn(spark);
    when(sparks.classify("spark-1", "user-1", SparkType.CODE)).thenReturn(spark);
    when(pdlcRouter.route(eq("user-1"), eq("spark-1"), eq("fix bug"),
            isNull(), eq(SparkType.CODE), eq(List.of()), isNull(), eq(10_000.0)))
        .thenReturn(Optional.empty());

    agentCommandService.execute(cmd);

    verify(pdlcRouter).route(eq("user-1"), eq("spark-1"), eq("fix bug"),
            isNull(), eq(SparkType.CODE), eq(List.of()), isNull(), eq(10_000.0));
}
```

- [ ] **Step 2: Run test, confirm it fails**

```bash
./gradlew :business:business-agent:test --tests AgentCommandServiceTest.executeUsesTenThousandDefaultCeilingForCodeSpark
```
Expected: FAIL — verify mismatch on ceiling argument.

- [ ] **Step 3: Bump the constant**

```java
private static final double DEFAULT_COST_CEILING_USD = 10_000.0;
```

- [ ] **Step 4: Verify all tests in module pass**

```bash
./gradlew :business:business-agent:test
```
Expected: PASS (including this and pre-existing).

- [ ] **Step 5: Commit**

```bash
git add business/business-agent/
git commit -m "fix(business-agent): raise default cost ceiling to 10k (no-cap for testing)"
```

---

## Chunk 2: Telegram conversational adapter

Today the Telegram dispatcher routes every spark-like message straight to `TelegramSparkInitiator` → `AgentCommandService` (which creates a one-shot spark, classifies it, and immediately submits to the pipeline). This chunk inserts the conversational layer in between: lookup the active `ConversationSession` for the project, append the message through `ConversationService.sendMessage(...)`, and post the assistant reply to Telegram.

### File structure (chunk 2)

| Action | File | Responsibility |
|---|---|---|
| Create | `business/business-telegram/.../conversation/TelegramConversationAdapter.java` | Bridge Telegram → ConversationService, render reply |
| Modify | `business/business-telegram/.../command/SparkCommand.java` | Delegate to the new adapter instead of `TelegramSparkInitiator` |
| Modify | `business/business-telegram/.../TelegramDispatchService.java` | Plain-text bot-mention/reply path goes through the adapter |
| Keep | `business/business-telegram/.../spark/TelegramSparkInitiator.java` | **Unchanged**; remains available as a one-shot fallback if needed, but the SparkCommand + dispatcher will no longer call it. We can remove it in a follow-up after the new path is proven. |
| Test (new) | `business/business-telegram/src/test/.../conversation/TelegramConversationAdapterTest.java` | Unit tests for find-or-create, append, reply rendering |

### Task 5: TelegramConversationAdapter

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/conversation/TelegramConversationAdapter.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/conversation/TelegramConversationAdapterTest.java`

The adapter is intentionally thin: it does NOT own conversation state or LLM calls — that all lives in `ConversationService`. It is responsible for:

1. Permission check (CONTRIBUTOR — same as today's `TelegramSparkInitiator`).
2. Text validation (≤2000 chars — same as today).
3. Find-or-create the active `ConversationSession` for `(projectId, userId)` using statuses `GATHERING` or `PROPOSING` (an `ACTIVE` session means a pipeline is running — append a side-note message, don't claim it as the "active gathering session").
4. Call `ConversationService.sendMessage(...)` and enqueue the assistant text on the outbound queue.

Decision: For now, when a project has an `ACTIVE` session (pipeline running) and the user sends a fresh message, we still append through that session so context survives — the existing `ACTIVE_SYSTEM_PROMPT_TEMPLATE` already handles this by telling the agent it's in execution mode. Only when the session reaches `COMPLETED` do we start a new session. This keeps history per-project continuous.

- [ ] **Step 1: Write failing test** (find-or-create-then-send)

```java
class TelegramConversationAdapterTest {

    @Mock ConversationService conversationService;
    @Mock ConversationSessionRepository sessionRepository;
    @Mock MemberPermissionService permissions;
    @Mock TelegramOutboundQueue outbound;

    TelegramConversationAdapter adapter;

    @BeforeEach void setup() {
        MockitoAnnotations.openMocks(this);
        adapter = new TelegramConversationAdapter(
            conversationService, sessionRepository, permissions, outbound);
    }

    @Test
    void newConversation_createsSessionAndSendsFirstMessage() {
        // Permission OK
        when(permissions.require(123L, "user-1", MemberRole.CONTRIBUTOR))
            .thenReturn(PermissionCheck.allow());
        // No existing active session
        when(sessionRepository.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
            eq("proj-1"), eq("user-1"), anyCollection()))
            .thenReturn(Optional.empty());
        // createSession returns a fresh session with id "sess-1"
        ConversationSession created = ConversationSession.createForTelegramGroup(
            "user-1", "proj-1", "build me X");
        ReflectionTestUtils.setField(created, "id", "sess-1");
        when(conversationService.createSession("user-1", "proj-1", "build me X"))
            .thenReturn(created);
        when(conversationService.sendMessage("sess-1", "user-1", "build me X"))
            .thenReturn(new MessageResponse("Hi — what should it do?", "GATHERING", null, null));

        TelegramProjectLink link = mock(TelegramProjectLink.class);
        when(link.getProjectId()).thenReturn("proj-1");

        adapter.handle(123L, "user-1", "build me X", link);

        // Outbound message was the assistant reply
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(123L), captor.capture());
        assertThat(captor.getValue().request().getText()).isEqualTo("Hi — what should it do?");
    }

    @Test
    void existingActiveSession_isReused() {
        when(permissions.require(123L, "user-1", MemberRole.CONTRIBUTOR))
            .thenReturn(PermissionCheck.allow());
        ConversationSession existing = ConversationSession.createForTelegramGroup(
            "user-1", "proj-1", "previous request");
        ReflectionTestUtils.setField(existing, "id", "sess-existing");
        when(sessionRepository.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
            eq("proj-1"), eq("user-1"), anyCollection()))
            .thenReturn(Optional.of(existing));
        when(conversationService.sendMessage("sess-existing", "user-1", "yes go ahead"))
            .thenReturn(new MessageResponse("Starting!", "ACTIVE", "spark-1", "run-1"));

        TelegramProjectLink link = mock(TelegramProjectLink.class);
        when(link.getProjectId()).thenReturn("proj-1");

        adapter.handle(123L, "user-1", "yes go ahead", link);

        verify(conversationService, never()).createSession(any(), any(), any());
        verify(conversationService).sendMessage("sess-existing", "user-1", "yes go ahead");
    }

    @Test
    void permissionDenied_repliesWithoutTouchingConversationService() {
        when(permissions.require(eq(123L), eq("user-1"), any()))
            .thenReturn(PermissionCheck.deny("You need contributor role."));

        TelegramProjectLink link = mock(TelegramProjectLink.class);
        adapter.handle(123L, "user-1", "anything", link);

        verifyNoInteractions(conversationService);
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(123L), captor.capture());
        assertThat(captor.getValue().request().getText()).contains("contributor");
    }

    @Test
    void overLongText_rejected() {
        when(permissions.require(eq(123L), eq("user-1"), any()))
            .thenReturn(PermissionCheck.allow());
        String tooLong = "x".repeat(2001);
        TelegramProjectLink link = mock(TelegramProjectLink.class);

        adapter.handle(123L, "user-1", tooLong, link);

        verifyNoInteractions(conversationService);
    }
}
```

- [ ] **Step 2: Run tests, confirm they fail (no class)**

```bash
./gradlew :business:business-telegram:test --tests TelegramConversationAdapterTest
```
Expected: compile failure — class missing.

- [ ] **Step 3: Add `createSession(userId, projectId, firstMessage)` overload to ConversationService**

Replace existing single-arg createSession to delegate, and add the project-scoped variant. Insert above the existing `createSession`:

```java
public ConversationSession createSession(String userId, String projectId, String firstMessage) {
    ConversationSession session = ConversationSession.createForTelegramGroup(userId, projectId, firstMessage);
    return sessionRepository.save(session);
}
```

(The existing `createSession(userId, firstMessage)` stays — used by the web `ConversationController`.)

- [ ] **Step 4: Implement TelegramConversationAdapter**

```java
package io.tacticl.business.telegram.conversation;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.service.conversation.dto.MessageResponse;
import io.tacticl.service.conversation.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Bridges inbound Telegram group messages onto the ConversationService:
 * find-or-create an active session per (projectId, userId), feed the message
 * through the conversation engine, render the assistant reply to chat.
 *
 * <p>This is the replacement entry point for TelegramSparkInitiator on the
 * conversational path. TelegramSparkInitiator stays in the codebase but is
 * no longer wired from /spark or plain-text mention paths after this change.
 */
@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramConversationAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramConversationAdapter.class);
    private static final int MAX_TEXT_CHARS = 2000;
    private static final List<SessionStatus> RESUMABLE = List.of(
        SessionStatus.GATHERING, SessionStatus.PROPOSING, SessionStatus.ACTIVE);

    private final ConversationService conversationService;
    private final ConversationSessionRepository sessionRepository;
    private final MemberPermissionService permissions;
    private final TelegramOutboundQueue outbound;

    public TelegramConversationAdapter(ConversationService conversationService,
                                       ConversationSessionRepository sessionRepository,
                                       MemberPermissionService permissions,
                                       TelegramOutboundQueue outbound) {
        this.conversationService = conversationService;
        this.sessionRepository = sessionRepository;
        this.permissions = permissions;
        this.outbound = outbound;
    }

    public void handle(long chatId, String tacticlUserId, String text, TelegramProjectLink link) {
        if (text == null || text.isBlank()) {
            reply(chatId, "What would you like to spark?");
            return;
        }
        if (text.length() > MAX_TEXT_CHARS) {
            reply(chatId, "Message too long (max " + MAX_TEXT_CHARS + " chars).");
            return;
        }
        PermissionCheck check = permissions.require(chatId, tacticlUserId, MemberRole.CONTRIBUTOR);
        if (!check.allowed()) {
            reply(chatId, "You need contributor role to spark in this project.");
            return;
        }

        String projectId = link.getProjectId();
        Optional<ConversationSession> active = sessionRepository
            .findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(projectId, tacticlUserId, RESUMABLE);

        ConversationSession session = active.orElseGet(() ->
            conversationService.createSession(tacticlUserId, projectId, text));

        try {
            MessageResponse response = conversationService.sendMessage(session.getId(), tacticlUserId, text);
            String body = response.getReply();
            if (response.getPipelineRunId() != null) {
                body = body + "\n\n▶️ Pipeline started — I'll post updates here.";
            }
            reply(chatId, body);
        } catch (RuntimeException e) {
            log.error("Conversation turn failed for session {} in chat {}", session.getId(), chatId, e);
            reply(chatId, "⚠️ I couldn't process that. Try again in a moment.");
        }
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
```

**Note**: `MessageResponse.getReply()` may not exist yet — current fields are `(reply, status, sparkId, pipelineRunId)` based on its DTO. Confirm by opening `service/service-conversation/.../dto/MessageResponse.java` and align field names before pasting (the test in Step 1 uses positional constructor args, which already works regardless of getter names).

- [ ] **Step 5: Run tests, verify they pass**

```bash
./gradlew :business:business-telegram:test --tests TelegramConversationAdapterTest
```
Expected: PASS all four cases.

- [ ] **Step 6: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/conversation/ \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/conversation/ \
        service/service-conversation/src/main/java/io/tacticl/service/conversation/service/ConversationService.java
git commit -m "feat(business-telegram): TelegramConversationAdapter bridges Telegram into ConversationService"
```

---

### Task 6: Wire `SparkCommand` + dispatcher plain-text path to the adapter

**Files:**
- Modify: `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/SparkCommand.java`
- Modify: `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramDispatchService.java`
- Test (existing): `business/business-telegram/src/test/java/io/tacticl/business/telegram/spark/TelegramSparkInitiatorTest.java` — leave; that path still works for direct callers.

- [ ] **Step 1: SparkCommand — delegate to adapter**

Replace the `TelegramSparkInitiator initiator` constructor field with `TelegramConversationAdapter adapter`. In `handle(...)`, replace the final call:

```java
// before
initiator.initiate(chatId, tacticlUserId.get(), text, linkOpt.get(), null);

// after
adapter.handle(chatId, tacticlUserId.get(), text, linkOpt.get());
```

- [ ] **Step 2: TelegramDispatchService.handlePlainText — replace `sparkInitiator.initiate(...)` with adapter call**

In `handlePlainText` (around line 270 of the current file), replace the trailing line:

```java
// before
sparkInitiator.initiate(chatId, tacticlUserId.get(), cleaned, linkOpt.get(), null);

// after
conversationAdapter.handle(chatId, tacticlUserId.get(), cleaned, linkOpt.get());
```

Update the constructor + field for `TelegramDispatchService` to inject `TelegramConversationAdapter` in place of `TelegramSparkInitiator`. (Keep `TelegramSparkInitiator` bean registered — `VoiceMessageHandler` likely still references it; we'll inventory that in Step 4.)

- [ ] **Step 3: VoiceMessageHandler — confirm it now points to the adapter too**

Read `business/business-telegram/.../event/VoiceMessageHandler.java`. If it calls `TelegramSparkInitiator.initiate(...)`, swap to `TelegramConversationAdapter.handle(...)` with the same signature shape. Voice transcripts deserve the conversational path as much as typed text.

- [ ] **Step 4: Build + run telegram tests**

```bash
./gradlew :business:business-telegram:build
```
Expected: BUILD SUCCESSFUL. Address any test breakage (rewire mocks of `sparkInitiator` → `conversationAdapter` in `TelegramDispatchServiceTest` / `SparkCommandTest` if those tests exist).

- [ ] **Step 5: Commit**

```bash
git add business/business-telegram/
git commit -m "refactor(business-telegram): /spark + bot-mention + voice route through ConversationAdapter"
```

---

## Chunk 3: User-supplied repo URL via `/repo`

Repo auto-creation is deferred. For the test, the user sets the repo for the active conversation explicitly via `/repo <github-url>`.

### File structure (chunk 3)

| Action | File | Responsibility |
|---|---|---|
| Create | `business/business-telegram/.../command/RepoCommand.java` | `/repo <url>` handler |
| Modify | `business/business-telegram/.../TelegramCommandRegistrar.java` | Register `/repo` with BotFather |
| Test (new) | `business/business-telegram/src/test/.../command/RepoCommandTest.java` | Unit tests for URL validation and session update |

### Task 7: `/repo <url>` command sets repoUrl on the active session

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/RepoCommand.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/command/RepoCommandTest.java`

Behaviour:

- DM-illegal, group only (scope `GROUP`).
- Caller must be linked + have CONTRIBUTOR.
- Must have a project link for the chat (else: "Use /init first").
- Validates the URL with a simple regex `^https://github\\.com/[^/\\s]+/[^/\\s]+/?$` (no trailing path).
- If no active session: replies "No active spark — start one by mentioning me or `/spark <idea>`."
- If active session in `GATHERING` or `PROPOSING`: sets `repoUrl`, persists, replies "✅ Repo set: `<url>`. You can keep going."
- If active session in `ACTIVE` (pipeline already running): replies "⚠️ Pipeline already running with `<existing-or-empty>`; cannot change mid-run."

- [ ] **Step 1: Write failing tests**

```java
class RepoCommandTest {

    @Mock TelegramIdentityResolver identity;
    @Mock MemberPermissionService permissions;
    @Mock TelegramProjectLinkRepository projectRepo;
    @Mock ConversationSessionRepository sessionRepo;
    @Mock TelegramOutboundQueue outbound;

    RepoCommand cmd;

    @BeforeEach void setup() {
        MockitoAnnotations.openMocks(this);
        cmd = new RepoCommand(identity, permissions, projectRepo, sessionRepo, outbound);
    }

    @Test
    void setsRepoUrlOnGatheringSession() {
        when(identity.resolveByChatId(42L)).thenReturn(Optional.of("user-1"));
        when(permissions.require(123L, "user-1", MemberRole.CONTRIBUTOR))
            .thenReturn(PermissionCheck.allow());
        TelegramProjectLink link = mock(TelegramProjectLink.class);
        when(link.getProjectId()).thenReturn("proj-1");
        when(projectRepo.findByChatIdAndIsActiveTrue(123L)).thenReturn(Optional.of(link));

        ConversationSession session = ConversationSession.createForTelegramGroup(
            "user-1", "proj-1", "build X");
        when(sessionRepo.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
            eq("proj-1"), eq("user-1"), anyCollection())).thenReturn(Optional.of(session));

        CommandContext ctx = new CommandContext(123L, 42L,
            "/repo https://github.com/owner/repo", "alice", mock(Message.class));
        cmd.handle(ctx);

        assertThat(session.getRepoUrl()).isEqualTo("https://github.com/owner/repo");
        verify(sessionRepo).save(session);
        verify(outbound).enqueue(eq(123L), argThat(m ->
            m.request().getText().contains("Repo set")));
    }

    @Test
    void rejectsInvalidUrl() {
        when(identity.resolveByChatId(42L)).thenReturn(Optional.of("user-1"));
        when(permissions.require(eq(123L), eq("user-1"), any()))
            .thenReturn(PermissionCheck.allow());
        CommandContext ctx = new CommandContext(123L, 42L,
            "/repo notaurl", "alice", mock(Message.class));
        cmd.handle(ctx);

        verify(outbound).enqueue(eq(123L), argThat(m ->
            m.request().getText().toLowerCase().contains("invalid")));
        verifyNoInteractions(sessionRepo);
    }

    @Test
    void blockedWhenPipelineActive() {
        when(identity.resolveByChatId(42L)).thenReturn(Optional.of("user-1"));
        when(permissions.require(eq(123L), eq("user-1"), any()))
            .thenReturn(PermissionCheck.allow());
        TelegramProjectLink link = mock(TelegramProjectLink.class);
        when(link.getProjectId()).thenReturn("proj-1");
        when(projectRepo.findByChatIdAndIsActiveTrue(123L)).thenReturn(Optional.of(link));

        ConversationSession session = ConversationSession.createForTelegramGroup(
            "user-1", "proj-1", "x");
        session.markProposing("CODE", "plan");
        session.markActive("spark-1");
        when(sessionRepo.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
            eq("proj-1"), eq("user-1"), anyCollection())).thenReturn(Optional.of(session));

        CommandContext ctx = new CommandContext(123L, 42L,
            "/repo https://github.com/x/y", "alice", mock(Message.class));
        cmd.handle(ctx);

        verify(outbound).enqueue(eq(123L), argThat(m ->
            m.request().getText().contains("already running")));
        verify(sessionRepo, never()).save(any());
    }
}
```

- [ ] **Step 2: Run tests, confirm they fail**

```bash
./gradlew :business:business-telegram:test --tests RepoCommandTest
```
Expected: compile failure.

- [ ] **Step 3: Implement RepoCommand**

```java
package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class RepoCommand implements CommandHandler {

    private static final Pattern GH_URL =
        Pattern.compile("^https://github\\.com/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+/?$");
    private static final List<SessionStatus> EDITABLE = List.of(
        SessionStatus.GATHERING, SessionStatus.PROPOSING);
    private static final List<SessionStatus> RESUMABLE = List.of(
        SessionStatus.GATHERING, SessionStatus.PROPOSING, SessionStatus.ACTIVE);

    private final TelegramIdentityResolver identity;
    private final MemberPermissionService permissions;
    private final TelegramProjectLinkRepository projectRepo;
    private final ConversationSessionRepository sessionRepo;
    private final TelegramOutboundQueue outbound;

    public RepoCommand(TelegramIdentityResolver identity,
                       MemberPermissionService permissions,
                       TelegramProjectLinkRepository projectRepo,
                       ConversationSessionRepository sessionRepo,
                       TelegramOutboundQueue outbound) {
        this.identity = identity;
        this.permissions = permissions;
        this.projectRepo = projectRepo;
        this.sessionRepo = sessionRepo;
        this.outbound = outbound;
    }

    @Override public String commandName() { return "/repo"; }
    @Override public Scope scope() { return Scope.GROUP; }
    @Override public String description() { return "Set the GitHub repo for the active spark"; }

    @Override
    public void handle(CommandContext ctx) {
        long chatId = ctx.chatId();
        Optional<String> userOpt = identity.resolveByChatId(ctx.telegramUserId());
        if (userOpt.isEmpty()) {
            reply(chatId, "Link your Tacticl account first.");
            return;
        }
        String userId = userOpt.get();
        PermissionCheck check = permissions.require(chatId, userId, MemberRole.CONTRIBUTOR);
        if (!check.allowed()) {
            reply(chatId, "You need contributor role.");
            return;
        }

        String arg = ctx.argsAfterCommand();
        if (arg.isBlank() || !GH_URL.matcher(arg.trim()).matches()) {
            reply(chatId, "Invalid URL. Use: /repo https://github.com/<owner>/<repo>");
            return;
        }
        String url = arg.trim();

        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            reply(chatId, "No active project in this group. Use /init first.");
            return;
        }

        Optional<ConversationSession> sessionOpt = sessionRepo
            .findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
                linkOpt.get().getProjectId(), userId, RESUMABLE);
        if (sessionOpt.isEmpty()) {
            reply(chatId, "No active spark — start one by mentioning me or `/spark <idea>`.");
            return;
        }
        ConversationSession session = sessionOpt.get();
        if (!EDITABLE.contains(session.getStatus())) {
            reply(chatId, "⚠️ Pipeline already running; cannot change repo mid-run.");
            return;
        }
        session.setRepoUrl(url);
        sessionRepo.save(session);
        reply(chatId, "✅ Repo set: " + url);
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
```

- [ ] **Step 4: Register `/repo` with TelegramCommandRegistrar**

Open `business/business-telegram/.../TelegramCommandRegistrar.java`. The registrar enumerates `CommandHandler` beans and publishes their `commandName` / `description` to BotFather on startup. Confirm `RepoCommand` is auto-picked up (Spring injects all beans implementing `CommandHandler`). If it requires manual registration, add `repoCommand` to the list. Verify no code change is needed beyond Spring scanning.

- [ ] **Step 5: Run tests + build**

```bash
./gradlew :business:business-telegram:test --tests RepoCommandTest
./gradlew :business:business-telegram:build
```
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/command/RepoCommand.java \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/command/RepoCommandTest.java
git commit -m "feat(business-telegram): /repo <url> command sets repoUrl on active spark"
```

---

## Chunk 4: Pipeline events → conversation history

When the pipeline emits events on the existing arbiter callback path, today they fan out to `TelegramEventChannel` (formats to chat) and `SseEventChannel` (for the web). They do NOT update the `ConversationSession` history. This chunk adds a third sink that appends an assistant turn to the session whose `sparkId` matches the event. That way, when a user later asks "what's the pipeline doing?" the conversation engine has the right context, and the eventual web UI can render the unified thread.

### File structure (chunk 4)

| Action | File | Responsibility |
|---|---|---|
| Create | `business/business-pipeline/.../channel/ConversationEventChannel.java` | New `PipelineEventChannel` impl — append summary to ConversationSession.messages |
| Test (new) | `business/business-pipeline/src/test/.../channel/ConversationEventChannelTest.java` | Unit tests: session found, event appended, terminal marks COMPLETED |

### Task 8: ConversationEventChannel

**Files:**
- Create: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/channel/ConversationEventChannel.java`
- Test: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/channel/ConversationEventChannelTest.java`

Behaviour:

- Resolve the active conversation: `sessionRepository.findBySparkId(sparkId)`. Add this method to the repository.
- Translate `PipelineCallbackEvent` to a short assistant message:
  - `ROLE_STARTED` → `"▶️ {role} working"`
  - `ROLE_COMPLETED` → `"✅ {role} done"`
  - `CHECKPOINT_REQUESTED` → `"⏸ Waiting on you ({role})"`
  - `PIPELINE_COMPLETED` → `"🎉 Pipeline complete."`
  - `PIPELINE_FAILED` → `"❌ Pipeline failed: {reason}"`
  - `PIPELINE_CANCELLED` → `"⛔ Pipeline cancelled."`
- Append via `session.addMessage(ConversationMessage.assistant(...))` + save.
- On terminal events, `session.markCompleted()`.

This sink is purely additive — `TelegramEventChannel` continues to render to Telegram. Both can run side-by-side; users will see chat updates AND have history persisted to the session.

- [ ] **Step 1: Add `findBySparkId` to ConversationSessionRepository**

```java
Optional<ConversationSession> findBySparkId(String sparkId);
```

- [ ] **Step 2: Write failing test**

```java
class ConversationEventChannelTest {

    @Mock ConversationSessionRepository sessionRepo;
    ConversationEventChannel channel;

    @BeforeEach void setup() {
        MockitoAnnotations.openMocks(this);
        channel = new ConversationEventChannel(sessionRepo);
    }

    @Test
    void roleStarted_appendsAssistantTurn() {
        ConversationSession session = ConversationSession.createForTelegramGroup(
            "u-1", "p-1", "build X");
        session.markProposing("CODE", "plan");
        session.markActive("spark-1");
        when(sessionRepo.findBySparkId("spark-1")).thenReturn(Optional.of(session));

        channel.emit(new PipelineCallbackEvent(
            "run-1", "ROLE_STARTED", "PM", "PM", null));

        assertThat(session.getMessages().get(session.getMessages().size() - 1).getContent())
            .contains("PM").contains("working");
        verify(sessionRepo).save(session);
    }

    @Test
    void pipelineCompleted_marksSessionCompleted() {
        ConversationSession session = ConversationSession.createForTelegramGroup(
            "u-1", "p-1", "build X");
        session.markProposing("CODE", "plan");
        session.markActive("spark-1");
        when(sessionRepo.findBySparkId("spark-1")).thenReturn(Optional.of(session));

        channel.emit(new PipelineCallbackEvent(
            "run-1", "PIPELINE_COMPLETED", null, null, null));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(sessionRepo).save(session);
    }

    @Test
    void missingSession_isNoop() {
        when(sessionRepo.findBySparkId(any())).thenReturn(Optional.empty());
        // ConversationEventChannel does NOT have sparkId on the event directly — it has pipelineRunId.
        // The channel needs to look up the spark via run → spark — see Step 3.
        channel.emit(new PipelineCallbackEvent(
            "run-x", "ROLE_STARTED", "PM", "PM", null));
        verify(sessionRepo, never()).save(any());
    }
}
```

- [ ] **Step 3: Implement ConversationEventChannel**

The `PipelineCallbackEvent` carries `pipelineRunId`, not `sparkId`. We need the run → spark map. Inject `PipelineRunRepository` and look up `run.getSparkId()`.

```java
package io.tacticl.business.pipeline.channel;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.data.conversation.entity.ConversationMessage;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Optional;

/**
 * Mirrors pipeline events into the originating ConversationSession.messages so
 * the user-facing thread (web chat or Telegram) reflects pipeline progress.
 * Additive to TelegramEventChannel/SseEventChannel — those continue to fan out
 * to live channels; this one is the durable record on the session.
 */
@Component
public class ConversationEventChannel implements PipelineEventChannel {

    private static final Logger log = LoggerFactory.getLogger(ConversationEventChannel.class);

    private final ConversationSessionRepository sessionRepo;
    private final PipelineRunRepository runRepo;

    public ConversationEventChannel(ConversationSessionRepository sessionRepo,
                                    PipelineRunRepository runRepo) {
        this.sessionRepo = sessionRepo;
        this.runRepo = runRepo;
    }

    @Override
    public void emit(PipelineCallbackEvent event) {
        if (event == null || event.pipelineRunId() == null) return;
        Optional<PipelineRun> runOpt = runRepo.findById(event.pipelineRunId());
        if (runOpt.isEmpty()) return;
        String sparkId = runOpt.get().getSparkId();
        Optional<ConversationSession> sessionOpt = sessionRepo.findBySparkId(sparkId);
        if (sessionOpt.isEmpty()) return;
        ConversationSession session = sessionOpt.get();
        String summary = summarize(event);
        if (summary == null) return;
        session.addMessage(ConversationMessage.assistant(summary));
        if (isTerminal(event.eventType())) {
            if (session.getStatus() != SessionStatus.COMPLETED) {
                session.markCompleted();
            }
        }
        sessionRepo.save(session);
    }

    @Override
    public void complete(String pipelineRunId) {
        // No emitter-side cleanup; the channel doesn't hold per-run state.
    }

    private static String summarize(PipelineCallbackEvent e) {
        return switch (e.eventType()) {
            case "ROLE_STARTED" -> "▶️ " + e.role() + " working";
            case "ROLE_COMPLETED" -> "✅ " + e.role() + " done";
            case "CHECKPOINT_REQUESTED" -> "⏸ Waiting on you (" + e.role() + ")";
            case "PIPELINE_COMPLETED" -> "🎉 Pipeline complete.";
            case "PIPELINE_FAILED" -> "❌ Pipeline failed.";
            case "PIPELINE_CANCELLED" -> "⛔ Pipeline cancelled.";
            default -> null;
        };
    }

    private static boolean isTerminal(String type) {
        return "PIPELINE_COMPLETED".equals(type)
            || "PIPELINE_FAILED".equals(type)
            || "PIPELINE_CANCELLED".equals(type);
    }
}
```

- [ ] **Step 4: Verify `PipelineEventEmitter` picks up the new channel**

Open `business/business-pipeline/.../service/PipelineEventEmitter.java`. It collects `PipelineEventChannel` beans (likely via constructor injection of `List<PipelineEventChannel>`). Confirm so. If it uses a static set, register the new channel.

- [ ] **Step 5: Run tests + module build**

```bash
./gradlew :business:business-pipeline:test --tests ConversationEventChannelTest
./gradlew :business:business-pipeline:build
```
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add business/business-pipeline/src/main/java/io/tacticl/business/pipeline/channel/ConversationEventChannel.java \
        business/business-pipeline/src/test/java/io/tacticl/business/pipeline/channel/ConversationEventChannelTest.java \
        data/data-conversation/src/main/java/io/tacticl/data/conversation/repository/ConversationSessionRepository.java
git commit -m "feat(business-pipeline): ConversationEventChannel mirrors pipeline progress into session history"
```

---

## Chunk 5: Verification, deployment, smoke test

### Task 9: Full-module build + lint

- [ ] **Step 1: Build everything**

```bash
./gradlew clean build
```
Expected: BUILD SUCCESSFUL. Fix any cascade test breakage in dependent modules (look especially at any test that mocked `TelegramSparkInitiator` constructor wiring).

- [ ] **Step 2: Sanity-check the boot-up wiring**

Locally, with the Hetzner SSH tunnel running (`ssh -p 443 -L 27017:mongodb:27017 -L 8201:localhost:8200 -N root@178.156.141.55`) and `VAULT_TOKEN` exported:

```bash
TACTICL_TELEGRAM_ENABLED=true ./gradlew :application-api:bootRun
```

Look for these log lines (no exceptions):

- `Conversation … router for plain text…` (or absence of `TelegramSparkInitiator` invocation log)
- `Arbiter gRPC client ready (registryBasePath=…)` (from `ArbiterClientConfig`)
- `Telegram webhook registered at …` (only with `tacticl.telegram.public-base-url` set; localhost dev usually skips this)

### Task 10: Deploy QA + manual smoke test

- [ ] **Step 1: Deploy QA**

```bash
./scripts/deploy.sh qa
```

- [ ] **Step 2: Confirm Telegram webhook live**

```bash
curl -s "https://api.telegram.org/bot<QA_BOT_TOKEN>/getWebhookInfo" | jq
```
Expect `"url": "https://api-qa.tacticl.ai/v1/telegram/webhook"`.

- [ ] **Step 3: Sync agent registry to the QA arbiter**

```bash
bash scripts/sync-agent-registry.sh
```

- [ ] **Step 4: Run the smoke test in a Telegram forum supergroup**

1. Add the QA bot to a fresh forum supergroup; verify Privacy Mode is OFF on BotFather.
2. As a linked Tacticl user: `/init` — confirm welcome + forum topics created.
3. Mention the bot: `@tacticl_qa_bot help me build a markdown-to-PDF converter in Python`.
4. Engage with the gathering questions for 2–3 turns until the bot proposes a plan ending in `<<<PROPOSE>>>` (rendered as visible text — the marker is intentionally part of the design today).
5. Send `/repo https://github.com/<your-test-org>/markdown-to-pdf-scratch` — confirm `✅ Repo set` reply.
6. Reply `yes go ahead` (or similar approval).
7. Confirm: `▶️ Pipeline started — I'll post updates here.`
8. Watch the conversation thread for `▶️ PM working`, `✅ PM done`, … through to `🎉 Pipeline complete.` or a failure indicator.
9. If a `CHECKPOINT_REQUESTED` arrives, resolve it via the existing inline keyboard (`TelegramCheckpointResolver`).
10. Confirm the pipeline made *any* visible activity in the test repo (commit, branch — or failed gracefully if the repo was empty / no GitHub token).

### Task 11: Final commit + PR

- [ ] **Step 1: Update CLAUDE.md if needed**

If the architecture description in `CLAUDE.md` ("Chat message → POST /v1/agent/command …") still describes the bypass path, append a note that Telegram now routes through `ConversationService`. Keep the change small — one paragraph or one entry under `## Spark Lifecycle`.

- [ ] **Step 2: PR**

```bash
git push -u origin conv-spark
gh pr create --title "Telegram conversational spark → PDLC handoff" --body "$(cat <<'EOF'
## Summary
- Telegram /spark + mention + voice messages now route through the existing ConversationService (gather → propose → active), instead of bypassing into AgentCommandService.
- ConversationSession gains projectId, initiatorSource, repoUrl; lookup-active-by-project enables resuming the right thread per (projectId, userId).
- New /repo <github-url> command lets users supply the GitHub repo for the active spark before alignment.
- Cost ceiling default lifted from $50 to $10,000 on both conversation and HTTP paths (no-cap for current testing).
- ConversationEventChannel mirrors pipeline events into session.messages so future web UI can render a single continuous thread.

## Test plan
- [ ] `./gradlew clean build` green
- [ ] Deploy QA, /init a test group, run /spark + /repo + alignment, observe pipeline events in chat
- [ ] Inspect Mongo: `conversation_sessions` row has repoUrl, status transitions through GATHERING → PROPOSING → ACTIVE → COMPLETED
- [ ] Pipeline run shows the right repoUrl in `pipeline_runs.repoUrl`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Risks + open items

1. **`tacticl-web` doesn't know about the new fields yet.** Existing GET `/v1/conversations` and `/v1/conversations/{id}` still work — the new fields are additive. The web team can pick them up in a follow-up.
2. **Repo URL is per-session, not per-project.** If a user starts a second spark in the same group, they must `/repo` again. We can promote `repoUrl` to `TelegramProjectLink` in a follow-up.
3. **Agent doesn't yet enforce that a repo is set before proposing alignment.** A user could `<<<PROPOSE>>>` without setting `/repo` and the pipeline would run with `repoUrl=null` (same broken state as today). We can either (a) update the gathering system prompt to remind users to set `/repo` first, or (b) have `ConversationService.startImplementation` refuse to call the pipeline when `repoUrl` is null and prompt for it. Recommend (a) for this iteration to keep behaviour permissive.
4. **No GitHub token bridging.** Arbiter sees `githubToken=""`. Public repo testing only.
5. **No auto repo creation.** Deferred to a separate plan; agent skill `create_github_repo` lives on the arbiter agent registry.
6. **Existing `TelegramSparkInitiator`** is now unreferenced from /spark, plain-text, and voice paths. Leave it for one release cycle, then delete in a cleanup PR.
7. **Mongo `@Version` on ConversationSession**: not present today. If two Telegram messages race for the same session, the second save may overwrite the first's history append. Low risk for our test (single user, slow human cadence), but flag for a follow-up if we ship to wider use.
