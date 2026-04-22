# Telegram Group-Project Model Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn each Tacticl project into a Telegram group where an owner and granted members collaborate via commands, PDLC checkpoints, and role threads.

**Architecture:** Extend existing `client-telegram` / `business-telegram` / `service-telegram` modules. Add two MongoDB entities (`TelegramProjectLink`, `TelegramMemberGrant`) to `data-telegram`. Introduce a per-chat outbound queue with rate limiting, a command router replacing the current inline if/else, and a new `PipelineEventChannel` implementation that formats PDLC role outputs for Telegram and routes checkpoint callbacks through a `TelegramCheckpointResolver`. All new code is gated by `tacticl.telegram.enabled`.

**Tech Stack:** Java 25, Spring Boot 4.0.3, MongoDB (Spring Data), Jackson 3 (`tools.jackson.*`), bucket4j rate limiting, JUnit 6 / Mockito, Flapdoodle for integration tests.

**Spec:** `docs/superpowers/specs/2026-04-20-telegram-group-project-model-design.md`

**Open-question resolutions used by this plan:**
1. **Multi-project quota**: not enforced in V1; follow-up.
2. **Billing breakdown**: stamp each Spark with `initiatorUserId` + `initiatorSource=TELEGRAM_GROUP`. Aggregation/report is follow-up.
3. **Orphan handling**: owner unlink or delete → auto-transfer to oldest admin; if none, mark `ORPHANED` and archive after 7 days.
4. **Claim semantics**: `/init` is first-come, first-serve; reversible via `/transfer`.
5. **Audit**: fold into existing `agent_audit_log` with a new `source=TELEGRAM_GROUP` discriminator. No new collection.

**Conventions used throughout:**
- Package root: `io.tacticl.{layer}.telegram.*`
- All new services gated by `@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")`
- Constructor injection only.
- Unit tests use Mockito; integration tests use Flapdoodle for MongoDB.
- Commits use `feat(telegram): …`, `test(telegram): …`, or `refactor(telegram): …` conventional-commit prefixes.

---

## File Structure

**New files** (grouped by module):

**`data/data-telegram/`**
- `entity/TelegramProjectLink.java` — group↔project binding, owner, forum topics, status
- `entity/ProjectStatus.java` — enum: `ACTIVE | ARCHIVED | ORPHANED`
- `entity/TelegramMemberGrant.java` — per-project, per-user permission
- `entity/MemberRole.java` — enum: `OBSERVER | CONTRIBUTOR | RUNNER | ADMIN | OWNER`
- `repository/TelegramProjectLinkRepository.java`
- `repository/TelegramMemberGrantRepository.java`

**`client/client-telegram/dto/`** (additions to existing module)
- `ChatMemberUpdate.java` — `my_chat_member` / `chat_member` payloads
- `ChatMember.java` — status + user
- `MessageEntity.java` — command entities
- `Voice.java` — voice message
- `PhotoSize.java`
- `Document.java`
- `CallbackQuery.java` — already exists; audit for completeness
- Update `Update.java` to add `my_chat_member`, `chat_member`, `edited_message`
- Update `Message.java` to add `voice`, `photo`, `document`, `migrate_to_chat_id`, `migrate_from_chat_id`, `entities`, `reply_to_message`, `message_thread_id`, `is_topic_message`

**`client/client-telegram/dto/forum/`**
- `CreateForumTopicRequest.java`, `ForumTopic.java`

**`client/client-telegram/` (methods on `TelegramBotClient`)**
- `createForumTopic`, `editMessageText`, `pinChatMessage`, `answerCallbackQuery`, `sendVoiceTranscript`, `sendDocument`, `leaveChat`, `setMyCommands`

**`business/business-telegram/`**
- `identity/TelegramIdentityResolver.java` — resolves `telegramUserId → tacticlUserId`
- `permission/MemberPermissionService.java` — CRUD grants, role checks
- `permission/PermissionCheck.java` — result type
- `router/TelegramCommandRouter.java` — dispatch `/cmd` → `CommandHandler`
- `router/CommandHandler.java` — interface
- `command/InitCommand.java`
- `command/HelpCommand.java`
- `command/StatusCommand.java`
- `command/MembersCommand.java`
- `command/GrantCommand.java`
- `command/RevokeCommand.java`
- `command/TransferCommand.java`
- `command/ArchiveCommand.java`
- `command/LeaveCommand.java`
- `command/CancelCommand.java`
- `command/ApproveCommand.java`
- `command/RejectCommand.java`
- `command/SparkCommand.java`
- `command/ProjectsCommand.java` — DM
- `command/WhoamiCommand.java` — DM
- `command/UnlinkCommand.java` — DM
- `event/GroupMembershipHandler.java` — `my_chat_member`
- `event/GroupMigrationHandler.java` — `migrate_to_chat_id` / `migrate_from_chat_id`
- `event/CallbackQueryHandler.java`
- `event/VoiceMessageHandler.java`
- `outbound/TelegramOutboundQueue.java`
- `outbound/OutboundMessage.java`
- `outbound/OutboundDrainer.java` — `@Scheduled` drainer
- `pipeline/TelegramEventChannel.java` — implements `PipelineEventChannel` (new interface in `business-pipeline`)
- `pipeline/TelegramCheckpointResolver.java`
- `pipeline/TelegramMessageFormatter.java`
- `pipeline/PinnedStatusService.java`
- `spark/TelegramSparkInitiator.java` — wraps `SparkService.create` + PDLC router for group-initiated sparks
- `audit/TelegramAuditLogger.java` — writes to `agent_audit_log`

**`business/business-pipeline/` (additions)**
- `channel/PipelineEventChannel.java` — interface
- Refactor `PipelineEventEmitter` to fan out to `List<PipelineEventChannel>`

**`data/data-sparks/` (additions)**
- Add `initiatorSource` enum field + `initiatorUserId` to `Spark.java`
- New enum `SparkInitiatorSource.java`: `REST | TELEGRAM_GROUP | DEVICE | SCHEDULED | VOICE`

**Modified files:**
- `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramDispatchService.java` — delegate to `TelegramCommandRouter` + event handlers
- `client/client-telegram/src/main/java/io/tacticl/client/telegram/TelegramBotClient.java` — new API methods
- `application-api/src/main/resources/application.properties` — new feature flags (`tacticl.telegram.group-project.enabled`, outbound queue tuning)
- `service/service-telegram/.../controller/TelegramWebhookController.java` — expand `allowed_updates` registration payload to include `my_chat_member`, `chat_member`, `edited_message`

---

## Chunk 1: Data Model Foundation

Goal: add `TelegramProjectLink`, `TelegramMemberGrant`, their enums, and repositories. Enrich `Spark` with initiator source/user. All green on unit + integration tests.

### Task 1: `MemberRole` enum

**Files:**
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/MemberRole.java`
- Test: `data/data-telegram/src/test/java/io/tacticl/data/telegram/entity/MemberRoleTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.data.telegram.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemberRoleTest {

    @Test
    void rolesAreOrderedByPower() {
        assertTrue(MemberRole.OWNER.atLeast(MemberRole.ADMIN));
        assertTrue(MemberRole.ADMIN.atLeast(MemberRole.RUNNER));
        assertTrue(MemberRole.RUNNER.atLeast(MemberRole.CONTRIBUTOR));
        assertTrue(MemberRole.CONTRIBUTOR.atLeast(MemberRole.OBSERVER));
        assertFalse(MemberRole.OBSERVER.atLeast(MemberRole.CONTRIBUTOR));
    }

    @Test
    void runnerCanRunTier1ButNotTier2() {
        assertTrue(MemberRole.RUNNER.canRunTier(1));
        assertFalse(MemberRole.RUNNER.canRunTier(2));
        assertTrue(MemberRole.OWNER.canRunTier(2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-telegram:test --tests MemberRoleTest`
Expected: FAIL (MemberRole does not exist).

- [ ] **Step 3: Implement MemberRole**

```java
package io.tacticl.data.telegram.entity;

public enum MemberRole {
    OBSERVER(0),
    CONTRIBUTOR(1),
    RUNNER(2),
    ADMIN(3),
    OWNER(4);

    private final int rank;

    MemberRole(int rank) { this.rank = rank; }

    public boolean atLeast(MemberRole other) { return this.rank >= other.rank; }

    public boolean canRunTier(int tier) {
        return switch (tier) {
            case 0 -> atLeast(CONTRIBUTOR);
            case 1 -> atLeast(RUNNER);
            case 2 -> atLeast(OWNER);
            default -> false;
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-telegram:test --tests MemberRoleTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/MemberRole.java \
        data/data-telegram/src/test/java/io/tacticl/data/telegram/entity/MemberRoleTest.java
git commit -m "feat(telegram): add MemberRole enum for group permission tiers"
```

### Task 2: `ProjectStatus` enum

**Files:**
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/ProjectStatus.java`

- [ ] **Step 1: Implement** (no test — pure enum, covered by TelegramProjectLink tests)

```java
package io.tacticl.data.telegram.entity;

public enum ProjectStatus {
    ACTIVE,
    ARCHIVED,
    ORPHANED
}
```

- [ ] **Step 2: Commit**

```bash
git add data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/ProjectStatus.java
git commit -m "feat(telegram): add ProjectStatus enum"
```

### Task 3: `TelegramProjectLink` entity

**Files:**
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/TelegramProjectLink.java`
- Test: `data/data-telegram/src/test/java/io/tacticl/data/telegram/entity/TelegramProjectLinkTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.data.telegram.entity;

import io.tacticl.data.pipeline.enums.PdlcRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TelegramProjectLinkTest {

    @Test
    void createsActiveLinkWithDefaults() {
        var link = TelegramProjectLink.create("proj-1", -1001L, "user-a", "Team Tacticl");
        assertEquals("proj-1", link.getProjectId());
        assertEquals(-1001L, link.getChatId());
        assertEquals("user-a", link.getOwnerUserId());
        assertEquals("Team Tacticl", link.getGroupTitle());
        assertEquals(ProjectStatus.ACTIVE, link.getStatus());
        assertNotNull(link.getInitializedAt());
        assertNull(link.getForumTopics());
    }

    @Test
    void archiveMarksStatus() {
        var link = TelegramProjectLink.create("p", 1L, "u", "t");
        link.archive();
        assertEquals(ProjectStatus.ARCHIVED, link.getStatus());
    }

    @Test
    void orphanMarksStatus() {
        var link = TelegramProjectLink.create("p", 1L, "u", "t");
        link.orphan();
        assertEquals(ProjectStatus.ORPHANED, link.getStatus());
    }

    @Test
    void setForumTopicsStoresMap() {
        var link = TelegramProjectLink.create("p", 1L, "u", "t");
        link.setForumTopics(Map.of(PdlcRole.ARCHITECT, 42L));
        assertEquals(42L, link.getForumTopics().get(PdlcRole.ARCHITECT));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-telegram:test --tests TelegramProjectLinkTest`
Expected: FAIL.

- [ ] **Step 3: Implement `TelegramProjectLink`**

```java
package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import io.tacticl.data.pipeline.enums.PdlcRole;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("telegram_project_links")
public class TelegramProjectLink extends BaseMongoEntity {

    @Indexed(unique = true)
    private long chatId;

    @Indexed
    private String projectId;

    @Indexed
    private String ownerUserId;

    private String groupTitle;
    private ProjectStatus status;
    private Map<PdlcRole, Long> forumTopics;
    private Long pinnedStatusMessageId;
    private Instant initializedAt;
    private Instant statusChangedAt;

    public static TelegramProjectLink create(String projectId, long chatId, String ownerUserId, String groupTitle) {
        var link = new TelegramProjectLink();
        link.projectId = projectId;
        link.chatId = chatId;
        link.ownerUserId = ownerUserId;
        link.groupTitle = groupTitle;
        link.status = ProjectStatus.ACTIVE;
        link.initializedAt = Instant.now();
        link.statusChangedAt = link.initializedAt;
        return link;
    }

    public void archive() { setStatus(ProjectStatus.ARCHIVED); }
    public void orphan()  { setStatus(ProjectStatus.ORPHANED); }
    public void reactivate() { setStatus(ProjectStatus.ACTIVE); }

    private void setStatus(ProjectStatus s) {
        this.status = s;
        this.statusChangedAt = Instant.now();
    }

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getGroupTitle() { return groupTitle; }
    public void setGroupTitle(String groupTitle) { this.groupTitle = groupTitle; }
    public ProjectStatus getStatus() { return status; }
    public Map<PdlcRole, Long> getForumTopics() { return forumTopics; }
    public void setForumTopics(Map<PdlcRole, Long> forumTopics) { this.forumTopics = forumTopics; }
    public Long getPinnedStatusMessageId() { return pinnedStatusMessageId; }
    public void setPinnedStatusMessageId(Long id) { this.pinnedStatusMessageId = id; }
    public Instant getInitializedAt() { return initializedAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
}
```

If `PdlcRole` import doesn't resolve, add a `build.gradle.kts` dependency:
```kotlin
dependencies {
    implementation(project(":data:data-pipeline"))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-telegram:test --tests TelegramProjectLinkTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/TelegramProjectLink.java \
        data/data-telegram/src/test/java/io/tacticl/data/telegram/entity/TelegramProjectLinkTest.java \
        data/data-telegram/build.gradle.kts
git commit -m "feat(telegram): add TelegramProjectLink entity"
```

### Task 4: `TelegramMemberGrant` entity

**Files:**
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/TelegramMemberGrant.java`
- Test: `data/data-telegram/src/test/java/io/tacticl/data/telegram/entity/TelegramMemberGrantTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.data.telegram.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TelegramMemberGrantTest {

    @Test
    void createStoresFields() {
        var g = TelegramMemberGrant.create("proj-1", -100L, "u-1", 42L, MemberRole.RUNNER, "u-owner");
        assertEquals("proj-1", g.getProjectId());
        assertEquals(-100L, g.getChatId());
        assertEquals("u-1", g.getTacticlUserId());
        assertEquals(42L, g.getTelegramUserId());
        assertEquals(MemberRole.RUNNER, g.getRole());
        assertEquals("u-owner", g.getGrantedByUserId());
        assertNotNull(g.getGrantedAt());
    }

    @Test
    void updateRoleChangesRoleAndTimestamp() throws InterruptedException {
        var g = TelegramMemberGrant.create("p", 1L, "u", 2L, MemberRole.OBSERVER, "o");
        var before = g.getGrantedAt();
        Thread.sleep(5);
        g.updateRole(MemberRole.ADMIN, "o");
        assertEquals(MemberRole.ADMIN, g.getRole());
        assertTrue(g.getGrantedAt().isAfter(before));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-telegram:test --tests TelegramMemberGrantTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

```java
package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("telegram_member_grants")
@CompoundIndex(name = "project_user_unique", def = "{'projectId': 1, 'tacticlUserId': 1}", unique = true)
public class TelegramMemberGrant extends BaseMongoEntity {

    @Indexed
    private String projectId;
    @Indexed
    private long chatId;
    @Indexed
    private String tacticlUserId;
    private long telegramUserId;
    private MemberRole role;
    private String grantedByUserId;
    private Instant grantedAt;

    public static TelegramMemberGrant create(String projectId, long chatId, String tacticlUserId,
                                             long telegramUserId, MemberRole role, String grantedByUserId) {
        var g = new TelegramMemberGrant();
        g.projectId = projectId;
        g.chatId = chatId;
        g.tacticlUserId = tacticlUserId;
        g.telegramUserId = telegramUserId;
        g.role = role;
        g.grantedByUserId = grantedByUserId;
        g.grantedAt = Instant.now();
        return g;
    }

    public void updateRole(MemberRole newRole, String grantedByUserId) {
        this.role = newRole;
        this.grantedByUserId = grantedByUserId;
        this.grantedAt = Instant.now();
    }

    public String getProjectId() { return projectId; }
    public long getChatId() { return chatId; }
    public String getTacticlUserId() { return tacticlUserId; }
    public long getTelegramUserId() { return telegramUserId; }
    public MemberRole getRole() { return role; }
    public String getGrantedByUserId() { return grantedByUserId; }
    public Instant getGrantedAt() { return grantedAt; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-telegram:test --tests TelegramMemberGrantTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/TelegramMemberGrant.java \
        data/data-telegram/src/test/java/io/tacticl/data/telegram/entity/TelegramMemberGrantTest.java
git commit -m "feat(telegram): add TelegramMemberGrant entity"
```

### Task 5: Repositories + Flapdoodle integration test

**Files:**
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/repository/TelegramProjectLinkRepository.java`
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/repository/TelegramMemberGrantRepository.java`
- Test: `data/data-telegram/src/test/java/io/tacticl/data/telegram/repository/TelegramProjectRepositoriesIntegrationTest.java`

- [ ] **Step 1: Write the failing integration test**

Follow the project's Flapdoodle pattern (see `data-profile` for reference). The test saves, loads, and enforces uniqueness:

```java
package io.tacticl.data.telegram.repository;

import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@ImportAutoConfiguration({
    EmbeddedMongoAutoConfiguration.class,
    MongoAutoConfiguration.class,
    MongoReactiveAutoConfiguration.class
})
class TelegramProjectRepositoriesIntegrationTest {

    @Autowired TelegramProjectLinkRepository projectRepo;
    @Autowired TelegramMemberGrantRepository grantRepo;

    @BeforeEach
    void setup() {
        projectRepo.deleteAll();
        grantRepo.deleteAll();
    }

    @Test
    void chatIdIsUnique() {
        projectRepo.save(TelegramProjectLink.create("p-1", 100L, "u-a", "Group A"));
        var dup = TelegramProjectLink.create("p-2", 100L, "u-b", "Group B");
        assertThatThrownBy(() -> projectRepo.save(dup))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findByChatIdReturnsMatch() {
        projectRepo.save(TelegramProjectLink.create("p-1", 200L, "u-a", "G"));
        var found = projectRepo.findByChatIdAndIsActiveTrue(200L);
        assertThat(found).isPresent();
        assertThat(found.get().getProjectId()).isEqualTo("p-1");
    }

    @Test
    void grantProjectUserUnique() {
        grantRepo.save(TelegramMemberGrant.create("p-1", 1L, "u-1", 10L, MemberRole.OBSERVER, "owner"));
        var dup = TelegramMemberGrant.create("p-1", 1L, "u-1", 10L, MemberRole.RUNNER, "owner");
        assertThatThrownBy(() -> grantRepo.save(dup))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findByProjectReturnsMembers() {
        grantRepo.save(TelegramMemberGrant.create("p-1", 1L, "u-1", 10L, MemberRole.RUNNER, "o"));
        grantRepo.save(TelegramMemberGrant.create("p-1", 1L, "u-2", 20L, MemberRole.OBSERVER, "o"));
        grantRepo.save(TelegramMemberGrant.create("p-2", 2L, "u-1", 10L, MemberRole.OWNER, "o"));

        assertThat(grantRepo.findByProjectIdAndIsActiveTrue("p-1")).hasSize(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-telegram:test --tests TelegramProjectRepositoriesIntegrationTest`
Expected: FAIL (repos not defined).

- [ ] **Step 3: Implement repositories**

```java
// TelegramProjectLinkRepository.java
package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.ProjectStatus;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramProjectLinkRepository extends MongoRepository<TelegramProjectLink, String> {
    Optional<TelegramProjectLink> findByChatIdAndIsActiveTrue(long chatId);
    Optional<TelegramProjectLink> findByProjectIdAndIsActiveTrue(String projectId);
    List<TelegramProjectLink> findByOwnerUserIdAndIsActiveTrue(String ownerUserId);
    List<TelegramProjectLink> findByStatusAndIsActiveTrue(ProjectStatus status);
}
```

```java
// TelegramMemberGrantRepository.java
package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramMemberGrantRepository extends MongoRepository<TelegramMemberGrant, String> {
    List<TelegramMemberGrant> findByProjectIdAndIsActiveTrue(String projectId);
    Optional<TelegramMemberGrant> findByProjectIdAndTacticlUserIdAndIsActiveTrue(String projectId, String userId);
    List<TelegramMemberGrant> findByTacticlUserIdAndIsActiveTrue(String tacticlUserId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-telegram:test --tests TelegramProjectRepositoriesIntegrationTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/data-telegram/src/main/java/io/tacticl/data/telegram/repository/ \
        data/data-telegram/src/test/java/io/tacticl/data/telegram/repository/
git commit -m "feat(telegram): add project link + member grant repositories"
```

### Task 6: Spark initiator fields

**Files:**
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/enums/SparkInitiatorSource.java`
- Modify: `data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/Spark.java` — add two fields + setters
- Test: `data/data-sparks/src/test/java/io/tacticl/data/sparks/entity/SparkInitiatorSourceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.data.sparks.entity;

import io.tacticl.data.sparks.enums.SparkInitiatorSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparkInitiatorSourceTest {

    @Test
    void sparkStampsInitiatorSourceAndUser() {
        var s = new Spark();
        s.setInitiatorSource(SparkInitiatorSource.TELEGRAM_GROUP);
        s.setInitiatorUserId("u-42");
        assertEquals(SparkInitiatorSource.TELEGRAM_GROUP, s.getInitiatorSource());
        assertEquals("u-42", s.getInitiatorUserId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-sparks:test --tests SparkInitiatorSourceTest`
Expected: FAIL.

- [ ] **Step 3: Add enum + fields**

```java
// SparkInitiatorSource.java
package io.tacticl.data.sparks.enums;

public enum SparkInitiatorSource {
    REST, TELEGRAM_GROUP, DEVICE, SCHEDULED, VOICE
}
```

Add to `Spark.java`:

```java
private SparkInitiatorSource initiatorSource;
private String initiatorUserId;

public SparkInitiatorSource getInitiatorSource() { return initiatorSource; }
public void setInitiatorSource(SparkInitiatorSource s) { this.initiatorSource = s; }
public String getInitiatorUserId() { return initiatorUserId; }
public void setInitiatorUserId(String u) { this.initiatorUserId = u; }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-sparks:test --tests SparkInitiatorSourceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/data-sparks/src/main/java/io/tacticl/data/sparks/enums/SparkInitiatorSource.java \
        data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/Spark.java \
        data/data-sparks/src/test/java/io/tacticl/data/sparks/entity/SparkInitiatorSourceTest.java
git commit -m "feat(sparks): stamp initiator source + userId for multi-source provenance"
```

---

## Chunk 2: DTO Expansion + Client API Methods

Goal: expand `client-telegram` DTOs so the webhook can deserialize group events (`my_chat_member`, `callback_query`, `voice`, `message_thread_id`, migrations) and add the new outbound API methods (`createForumTopic`, `editMessageText`, `pinChatMessage`, `answerCallbackQuery`, `leaveChat`, `setMyCommands`).

### Task 7: Extend `Message` DTO

**Files:**
- Modify: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/Message.java`
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/MessageEntity.java`
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/Voice.java`
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/PhotoSize.java`
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/Document.java`
- Test: `client/client-telegram/src/test/java/io/tacticl/client/telegram/dto/MessageDeserializationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.client.telegram.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class MessageDeserializationTest {

    private static final JsonMapper M = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void parsesMigrateToChatId() {
        String json = """
            {"message_id":1,"date":1,"chat":{"id":-100,"type":"group"},"migrate_to_chat_id":-1001}
            """;
        Message m = M.readValue(json, Message.class);
        assertEquals(-1001L, m.migrate_to_chat_id());
    }

    @Test
    void parsesVoiceAndThreadId() {
        String json = """
            {"message_id":1,"date":1,"chat":{"id":1,"type":"group"},
             "voice":{"file_id":"abc","duration":3},"message_thread_id":42,"is_topic_message":true}
            """;
        Message m = M.readValue(json, Message.class);
        assertEquals("abc", m.voice().file_id());
        assertEquals(42L, m.message_thread_id());
        assertTrue(m.is_topic_message());
    }

    @Test
    void parsesEntities() {
        String json = """
            {"message_id":1,"date":1,"chat":{"id":1,"type":"group"},"text":"/grant @alice runner",
             "entities":[{"type":"bot_command","offset":0,"length":6}]}
            """;
        Message m = M.readValue(json, Message.class);
        assertEquals(1, m.entities().size());
        assertEquals("bot_command", m.entities().get(0).type());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :client:client-telegram:test --tests MessageDeserializationTest`
Expected: FAIL.

- [ ] **Step 3: Add DTOs**

```java
// MessageEntity.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageEntity(String type, int offset, int length) {}
```

```java
// Voice.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Voice(String file_id, String file_unique_id, int duration, String mime_type) {}
```

```java
// PhotoSize.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PhotoSize(String file_id, String file_unique_id, int width, int height, long file_size) {}
```

```java
// Document.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Document(String file_id, String file_unique_id, String file_name, String mime_type, long file_size) {}
```

Update `Message.java`:

```java
package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
    long message_id,
    long date,
    Chat chat,
    User from,
    String text,
    List<MessageEntity> entities,
    Voice voice,
    List<PhotoSize> photo,
    Document document,
    Long migrate_to_chat_id,
    Long migrate_from_chat_id,
    Long message_thread_id,
    boolean is_topic_message,
    Message reply_to_message
) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :client:client-telegram:test --tests MessageDeserializationTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/ \
        client/client-telegram/src/test/java/io/tacticl/client/telegram/dto/MessageDeserializationTest.java
git commit -m "feat(telegram): extend Message DTO with voice, thread, migration, entities"
```

### Task 8: `ChatMember` + `ChatMemberUpdate` DTOs

**Files:**
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/ChatMember.java`
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/ChatMemberUpdate.java`
- Test: `client/client-telegram/src/test/java/io/tacticl/client/telegram/dto/ChatMemberUpdateTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.client.telegram.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class ChatMemberUpdateTest {

    private static final JsonMapper M = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void parsesMyChatMemberBotAdded() {
        String json = """
        {"chat":{"id":-1001,"type":"supergroup","title":"Team"},
         "from":{"id":42,"is_bot":false,"username":"alice"},
         "date":1,
         "old_chat_member":{"status":"left","user":{"id":100,"is_bot":true,"username":"tacticl_bot"}},
         "new_chat_member":{"status":"member","user":{"id":100,"is_bot":true,"username":"tacticl_bot"}}}
        """;
        ChatMemberUpdate u = M.readValue(json, ChatMemberUpdate.class);
        assertEquals(-1001L, u.chat().id());
        assertEquals("member", u.new_chat_member().status());
        assertEquals("tacticl_bot", u.new_chat_member().user().username());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :client:client-telegram:test --tests ChatMemberUpdateTest`
Expected: FAIL.

- [ ] **Step 3: Implement DTOs**

```java
// ChatMember.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMember(String status, User user) {}
```

```java
// ChatMemberUpdate.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMemberUpdate(
    Chat chat,
    User from,
    long date,
    ChatMember old_chat_member,
    ChatMember new_chat_member
) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :client:client-telegram:test --tests ChatMemberUpdateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/ChatMember.java \
        client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/ChatMemberUpdate.java \
        client/client-telegram/src/test/java/io/tacticl/client/telegram/dto/ChatMemberUpdateTest.java
git commit -m "feat(telegram): add ChatMember + ChatMemberUpdate DTOs"
```

### Task 9: Extend `Update` DTO

**Files:**
- Modify: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/Update.java`
- Test: `client/client-telegram/src/test/java/io/tacticl/client/telegram/dto/UpdateExtendedTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.client.telegram.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class UpdateExtendedTest {

    private static final JsonMapper M = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void parsesMyChatMemberInUpdate() {
        String json = """
        {"update_id":1,
         "my_chat_member":{"chat":{"id":-1,"type":"group"},
                           "from":{"id":42,"is_bot":false,"username":"alice"},
                           "date":1,
                           "old_chat_member":{"status":"left","user":{"id":100,"is_bot":true}},
                           "new_chat_member":{"status":"member","user":{"id":100,"is_bot":true}}}}
        """;
        Update u = M.readValue(json, Update.class);
        assertNotNull(u.my_chat_member());
        assertEquals("member", u.my_chat_member().new_chat_member().status());
    }

    @Test
    void parsesCallbackQuery() {
        String json = """
        {"update_id":2,
         "callback_query":{"id":"cb-1","from":{"id":42,"is_bot":false},
                           "data":"approve:spark-1:cp-1",
                           "message":{"message_id":10,"date":1,"chat":{"id":-1,"type":"group"}}}}
        """;
        Update u = M.readValue(json, Update.class);
        assertEquals("cb-1", u.callback_query().id());
        assertEquals("approve:spark-1:cp-1", u.callback_query().data());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :client:client-telegram:test --tests UpdateExtendedTest`
Expected: FAIL.

- [ ] **Step 3: Update `Update.java`**

```java
package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Update(
    long update_id,
    Message message,
    Message edited_message,
    CallbackQuery callback_query,
    ChatMemberUpdate my_chat_member,
    ChatMemberUpdate chat_member
) {}
```

Inspect `CallbackQuery.java`; ensure it has fields `id`, `from`, `data`, `message`. Add any missing.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :client:client-telegram:test --tests UpdateExtendedTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/Update.java \
        client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/CallbackQuery.java \
        client/client-telegram/src/test/java/io/tacticl/client/telegram/dto/UpdateExtendedTest.java
git commit -m "feat(telegram): extend Update DTO with my_chat_member + callback_query"
```

### Task 10: `TelegramBotClient` — new API methods

**Files:**
- Modify: `client/client-telegram/src/main/java/io/tacticl/client/telegram/TelegramBotClient.java`
- Create DTOs: `CreateForumTopicRequest`, `ForumTopic`, `EditMessageTextRequest`, `AnswerCallbackQueryRequest`, `BotCommand`, `SetMyCommandsRequest`
- Test: `client/client-telegram/src/test/java/io/tacticl/client/telegram/TelegramBotClientExtendedTest.java`

- [ ] **Step 1: Write the failing test (MockRestServiceServer)**

Model after existing `TelegramBotClientTest.java`. Verify each method issues the correct HTTP call, parses response, and throws on non-`ok`.

```java
@Test
void createForumTopicReturnsThreadId() { /* MockRestServiceServer expecting /createForumTopic */ }
@Test
void editMessageTextReturnsUpdatedMessage() { /* .../editMessageText */ }
@Test
void pinChatMessageReturnsTrue() { /* .../pinChatMessage */ }
@Test
void answerCallbackQuerySucceeds() { /* .../answerCallbackQuery */ }
@Test
void leaveChatSucceeds() { /* .../leaveChat */ }
@Test
void setMyCommandsSucceeds() { /* .../setMyCommands */ }
```

Full test code: model each case after `sendMessage_success_returnsMessageId` in existing `TelegramBotClientTest.java`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :client:client-telegram:test --tests TelegramBotClientExtendedTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

For each new API method on `TelegramBotClient`, follow the existing `setWebhook` pattern (POST JSON, parse `ApiResponse<T>`):

```java
public ForumTopic createForumTopic(long chatId, String name) {
    checkRateLimit();
    Map<String, Object> payload = Map.of("chat_id", chatId, "name", name);
    return executePost("/createForumTopic", payload, ForumTopic.class);
}

public Message editMessageText(long chatId, long messageId, String text, InlineKeyboardMarkup markup) {
    checkRateLimit();
    Map<String, Object> payload = new HashMap<>();
    payload.put("chat_id", chatId);
    payload.put("message_id", messageId);
    payload.put("text", text);
    if (markup != null) payload.put("reply_markup", markup);
    return executePost("/editMessageText", payload, Message.class);
}

public boolean pinChatMessage(long chatId, long messageId) {
    checkRateLimit();
    return executePost("/pinChatMessage",
        Map.of("chat_id", chatId, "message_id", messageId, "disable_notification", true),
        Boolean.class);
}

public boolean answerCallbackQuery(String callbackQueryId, String text) {
    checkRateLimit();
    Map<String, Object> payload = new HashMap<>();
    payload.put("callback_query_id", callbackQueryId);
    if (text != null) payload.put("text", text);
    return executePost("/answerCallbackQuery", payload, Boolean.class);
}

public boolean leaveChat(long chatId) {
    checkRateLimit();
    return executePost("/leaveChat", Map.of("chat_id", chatId), Boolean.class);
}

public boolean setMyCommands(List<BotCommand> commands, String scopeType) {
    checkRateLimit();
    Map<String, Object> payload = Map.of(
        "commands", commands,
        "scope", Map.of("type", scopeType)
    );
    return executePost("/setMyCommands", payload, Boolean.class);
}
```

Extract the repetitive POST/parse logic into a private `executePost(String path, Object body, Class<T> responseType)`.

Create DTOs:

```java
public record ForumTopic(long message_thread_id, String name, int icon_color, String icon_custom_emoji_id) {}
public record BotCommand(String command, String description) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :client:client-telegram:test --tests TelegramBotClientExtendedTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/client-telegram/src/
git commit -m "feat(telegram): add forum/edit/pin/callback/leave API methods on client"
```

### Task 11: Register additional webhook update types

**Files:**
- Modify: `client/client-telegram/src/main/java/io/tacticl/client/telegram/TelegramBotClient.java` — `setWebhook` `allowed_updates`

- [ ] **Step 1: Update `setWebhook` payload**

Change `allowed_updates` to include `my_chat_member`, `chat_member`, `edited_message`:

```java
"allowed_updates", List.of("message", "edited_message", "callback_query",
                           "my_chat_member", "chat_member"),
```

- [ ] **Step 2: Extend client test**

Verify the payload sent in a `setWebhook_registersExtraUpdateTypes` test.

- [ ] **Step 3: Run + commit**

```bash
./gradlew :client:client-telegram:test
git add client/client-telegram/src/
git commit -m "feat(telegram): register my_chat_member + chat_member webhook events"
```

---

## Chunk 3: Identity & Permission Resolver

Goal: service objects that map a Telegram user to a Tacticl account + permission in a specific project.

### Task 12: `TelegramIdentityResolver`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/identity/TelegramIdentityResolver.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/identity/TelegramIdentityResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.business.telegram.identity;

import io.tacticl.data.telegram.entity.TelegramLink;
import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TelegramIdentityResolverTest {

    @Test
    void resolveByTelegramUserIdReturnsUserId() {
        var repo = mock(TelegramLinkRepository.class);
        var link = TelegramLink.create("u-7", 99L, "alice", "Alice");
        when(repo.findByChatId(99L)).thenReturn(Optional.of(link));

        var resolver = new TelegramIdentityResolver(repo);
        assertEquals(Optional.of("u-7"), resolver.resolveByChatId(99L));
    }

    @Test
    void unlinkedReturnsEmpty() {
        var repo = mock(TelegramLinkRepository.class);
        when(repo.findByChatId(anyLong())).thenReturn(Optional.empty());

        var resolver = new TelegramIdentityResolver(repo);
        assertTrue(resolver.resolveByChatId(99L).isEmpty());
    }
}
```

Note: our `TelegramLink` currently stores `chatId` = DM chat id. For group projects we need identity by `telegramUserId`. Since `chatId` on DM maps 1:1 to `telegramUserId` (DM chats use the user's id as chat id), we can use `chatId` as proxy. Clarify: if a mismatch exists in older data, add a follow-up migration.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :business:business-telegram:test --tests TelegramIdentityResolverTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

```java
package io.tacticl.business.telegram.identity;

import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramIdentityResolver {

    private final TelegramLinkRepository linkRepo;

    public TelegramIdentityResolver(TelegramLinkRepository linkRepo) {
        this.linkRepo = linkRepo;
    }

    public Optional<String> resolveByChatId(long telegramUserId) {
        return linkRepo.findByChatId(telegramUserId).map(l -> l.getUserId());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :business:business-telegram:test --tests TelegramIdentityResolverTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/identity/ \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/identity/
git commit -m "feat(telegram): add TelegramIdentityResolver"
```

### Task 13: `MemberPermissionService`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/permission/MemberPermissionService.java`
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/permission/PermissionCheck.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/permission/MemberPermissionServiceTest.java`

- [ ] **Step 1: Write the failing test**

Covers: `grant`, `revoke`, `findRole` (with owner inference from project), `require(role)` returning explicit `allowed/denied` reasons.

```java
package io.tacticl.business.telegram.permission;

import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramMemberGrantRepository;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MemberPermissionServiceTest {

    @Test
    void ownerRoleInferredFromProjectLink() {
        var projRepo = mock(TelegramProjectLinkRepository.class);
        var grantRepo = mock(TelegramMemberGrantRepository.class);
        var link = TelegramProjectLink.create("p-1", 1L, "u-owner", "G");
        when(projRepo.findByChatIdAndIsActiveTrue(1L)).thenReturn(Optional.of(link));

        var svc = new MemberPermissionService(projRepo, grantRepo);
        assertEquals(MemberRole.OWNER, svc.findRole(1L, "u-owner").orElseThrow());
    }

    @Test
    void defaultsToObserverForUnknownMember() {
        var projRepo = mock(TelegramProjectLinkRepository.class);
        var grantRepo = mock(TelegramMemberGrantRepository.class);
        var link = TelegramProjectLink.create("p-1", 1L, "u-owner", "G");
        when(projRepo.findByChatIdAndIsActiveTrue(1L)).thenReturn(Optional.of(link));
        when(grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue("p-1", "u-x"))
            .thenReturn(Optional.empty());

        var svc = new MemberPermissionService(projRepo, grantRepo);
        assertEquals(MemberRole.OBSERVER, svc.findRole(1L, "u-x").orElseThrow());
    }

    @Test
    void grantUpsertsExistingGrant() {
        var projRepo = mock(TelegramProjectLinkRepository.class);
        var grantRepo = mock(TelegramMemberGrantRepository.class);
        var link = TelegramProjectLink.create("p-1", 1L, "u-owner", "G");
        when(projRepo.findByChatIdAndIsActiveTrue(1L)).thenReturn(Optional.of(link));
        var existing = TelegramMemberGrant.create("p-1", 1L, "u-x", 20L, MemberRole.OBSERVER, "u-owner");
        when(grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue("p-1", "u-x"))
            .thenReturn(Optional.of(existing));

        var svc = new MemberPermissionService(projRepo, grantRepo);
        svc.grant(1L, "u-x", 20L, MemberRole.RUNNER, "u-owner");

        assertEquals(MemberRole.RUNNER, existing.getRole());
        verify(grantRepo).save(existing);
    }

    @Test
    void requireDeniesBelowMinimum() {
        var projRepo = mock(TelegramProjectLinkRepository.class);
        var grantRepo = mock(TelegramMemberGrantRepository.class);
        var link = TelegramProjectLink.create("p-1", 1L, "u-owner", "G");
        when(projRepo.findByChatIdAndIsActiveTrue(1L)).thenReturn(Optional.of(link));
        when(grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue(anyString(), anyString()))
            .thenReturn(Optional.empty());

        var svc = new MemberPermissionService(projRepo, grantRepo);
        PermissionCheck c = svc.require(1L, "u-observer", MemberRole.RUNNER);
        assertFalse(c.allowed());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :business:business-telegram:test --tests MemberPermissionServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

```java
// PermissionCheck.java
package io.tacticl.business.telegram.permission;
import io.tacticl.data.telegram.entity.MemberRole;

public record PermissionCheck(boolean allowed, MemberRole actual, MemberRole required, String reason) {
    public static PermissionCheck allow(MemberRole actual) {
        return new PermissionCheck(true, actual, null, null);
    }
    public static PermissionCheck deny(MemberRole actual, MemberRole required, String reason) {
        return new PermissionCheck(false, actual, required, reason);
    }
}
```

```java
// MemberPermissionService.java
package io.tacticl.business.telegram.permission;

import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramMemberGrantRepository;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class MemberPermissionService {

    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramMemberGrantRepository grantRepo;

    public MemberPermissionService(TelegramProjectLinkRepository projectRepo,
                                   TelegramMemberGrantRepository grantRepo) {
        this.projectRepo = projectRepo;
        this.grantRepo = grantRepo;
    }

    public Optional<MemberRole> findRole(long chatId, String tacticlUserId) {
        return projectRepo.findByChatIdAndIsActiveTrue(chatId).map(project -> {
            if (project.getOwnerUserId().equals(tacticlUserId)) return MemberRole.OWNER;
            return grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue(project.getProjectId(), tacticlUserId)
                .map(TelegramMemberGrant::getRole)
                .orElse(MemberRole.OBSERVER);
        });
    }

    public PermissionCheck require(long chatId, String tacticlUserId, MemberRole minimum) {
        MemberRole actual = findRole(chatId, tacticlUserId).orElse(MemberRole.OBSERVER);
        return actual.atLeast(minimum)
            ? PermissionCheck.allow(actual)
            : PermissionCheck.deny(actual, minimum, "insufficient role");
    }

    public void grant(long chatId, String tacticlUserId, long telegramUserId, MemberRole role, String grantedBy) {
        TelegramProjectLink project = projectRepo.findByChatIdAndIsActiveTrue(chatId)
            .orElseThrow(() -> new IllegalStateException("No active project for chatId " + chatId));
        grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue(project.getProjectId(), tacticlUserId)
            .ifPresentOrElse(
                g -> { g.updateRole(role, grantedBy); grantRepo.save(g); },
                () -> grantRepo.save(TelegramMemberGrant.create(
                    project.getProjectId(), chatId, tacticlUserId, telegramUserId, role, grantedBy))
            );
    }

    public void revoke(long chatId, String tacticlUserId) {
        projectRepo.findByChatIdAndIsActiveTrue(chatId).ifPresent(project ->
            grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue(project.getProjectId(), tacticlUserId)
                .ifPresent(g -> { g.delete(); grantRepo.save(g); }));
    }

    public List<TelegramMemberGrant> listGrants(String projectId) {
        return grantRepo.findByProjectIdAndIsActiveTrue(projectId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :business:business-telegram:test --tests MemberPermissionServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/permission/ \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/permission/
git commit -m "feat(telegram): add MemberPermissionService with grant/revoke/require"
```

---

## Chunk 4: Outbound Queue + Rate Limiting

Goal: replace direct `bot.sendMessage(...)` calls from business logic with an enqueue + drain pipeline so per-chat pacing is honored.

### Task 14: `OutboundMessage` record + queue service

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/outbound/OutboundMessage.java`
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/outbound/TelegramOutboundQueue.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/outbound/TelegramOutboundQueueTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramOutboundQueueTest {

    @Test
    void enqueueAndPollFifo() {
        var q = new TelegramOutboundQueue(100);
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "a")));
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "b")));

        assertEquals("a", q.poll(1L).orElseThrow().request().text());
        assertEquals("b", q.poll(1L).orElseThrow().request().text());
        assertTrue(q.poll(1L).isEmpty());
    }

    @Test
    void capacityBoundedPerChat() {
        var q = new TelegramOutboundQueue(2);
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "a")));
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "b")));
        assertFalse(q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "c"))));
    }

    @Test
    void snapshotKeysReturnsAllActiveChats() {
        var q = new TelegramOutboundQueue(10);
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "a")));
        q.enqueue(2L, new OutboundMessage(SendMessageRequest.plain(2L, "b")));
        assertTrue(q.activeChatIds().containsAll(java.util.List.of(1L, 2L)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :business:business-telegram:test --tests TelegramOutboundQueueTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

```java
// OutboundMessage.java
package io.tacticl.business.telegram.outbound;
import io.tacticl.client.telegram.dto.SendMessageRequest;

public record OutboundMessage(SendMessageRequest request) {}
```

```java
// TelegramOutboundQueue.java
package io.tacticl.business.telegram.outbound;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramOutboundQueue {

    private final int perChatCapacity;
    private final ConcurrentMap<Long, ArrayBlockingQueue<OutboundMessage>> queues = new ConcurrentHashMap<>();

    public TelegramOutboundQueue(
        @org.springframework.beans.factory.annotation.Value("${tacticl.telegram.outbound.capacity:200}")
        int perChatCapacity
    ) {
        this.perChatCapacity = perChatCapacity;
    }

    public boolean enqueue(long chatId, OutboundMessage msg) {
        return queues
            .computeIfAbsent(chatId, k -> new ArrayBlockingQueue<>(perChatCapacity))
            .offer(msg);
    }

    public Optional<OutboundMessage> poll(long chatId) {
        var q = queues.get(chatId);
        if (q == null) return Optional.empty();
        return Optional.ofNullable(q.poll());
    }

    public Set<Long> activeChatIds() { return queues.keySet(); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :business:business-telegram:test --tests TelegramOutboundQueueTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/outbound/ \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/outbound/
git commit -m "feat(telegram): per-chat bounded outbound queue"
```

### Task 15: `OutboundDrainer` — scheduled drainer honoring per-chat 1 msg/s

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/outbound/OutboundDrainer.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/outbound/OutboundDrainerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboundDrainerTest {

    @Test
    void drainsOneMessagePerChatPerTick() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        AtomicLong now = new AtomicLong(1_000L);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(now.get()), ZoneOffset.UTC);
        var drainer = new OutboundDrainer(queue, bot, () -> now.get() * 1000L);

        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "a")));
        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "b")));

        drainer.drain();                           // sends "a"
        drainer.drain();                           // still within 1s: no send
        now.addAndGet(1);                          // +1 second
        drainer.drain();                           // sends "b"

        verify(bot, times(2)).sendMessage(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :business:business-telegram:test --tests OutboundDrainerTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

```java
package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.TelegramBotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class OutboundDrainer {

    private static final Logger logger = LoggerFactory.getLogger(OutboundDrainer.class);
    private static final long PER_CHAT_MIN_INTERVAL_MS = 1_000L;

    private final TelegramOutboundQueue queue;
    private final TelegramBotClient bot;
    private final LongSupplier clock;
    private final ConcurrentMap<Long, Long> lastSentMs = new ConcurrentHashMap<>();

    public OutboundDrainer(TelegramOutboundQueue queue, TelegramBotClient bot) {
        this(queue, bot, System::currentTimeMillis);
    }

    OutboundDrainer(TelegramOutboundQueue queue, TelegramBotClient bot, LongSupplier clock) {
        this.queue = queue;
        this.bot = bot;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tacticl.telegram.outbound.drain-ms:50}")
    public void drain() {
        long now = clock.getAsLong();
        for (long chatId : queue.activeChatIds()) {
            long last = lastSentMs.getOrDefault(chatId, 0L);
            if (now - last < PER_CHAT_MIN_INTERVAL_MS) continue;
            queue.poll(chatId).ifPresent(msg -> {
                try {
                    bot.sendMessage(msg.request());
                    lastSentMs.put(chatId, now);
                } catch (RuntimeException e) {
                    logger.error("Outbound send failed for chat {}", chatId, e);
                }
            });
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :business:business-telegram:test --tests OutboundDrainerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/outbound/OutboundDrainer.java \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/outbound/OutboundDrainerTest.java
git commit -m "feat(telegram): scheduled outbound drainer with per-chat pacing"
```

---

## Chunk 5: Group Event Handling (membership + migration)

Goal: route `my_chat_member` (bot added/removed) and `migrate_to_chat_id` / `migrate_from_chat_id` to side-effects.

### Task 16: `GroupMembershipHandler`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/event/GroupMembershipHandler.java`
- Test: `business/business-telegram/src/test/java/io/tacticl/business/telegram/event/GroupMembershipHandlerTest.java`

Behavior:
- Bot added (`status: left → member` or `administrator`) → enqueue a "👋 I'm ready; run `/init`" welcome.
- Bot removed (`status: member → left/kicked`) → mark matching `TelegramProjectLink.status = ORPHANED`, cancel in-flight PDLC runs, DM the owner.

- [ ] **Step 1: Write the failing test** (bot-added flow, bot-removed flow)

Specify two scenarios, assert enqueue vs repo state change.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :business:business-telegram:test --tests GroupMembershipHandlerTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

```java
package io.tacticl.business.telegram.event;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.ChatMemberUpdate;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class GroupMembershipHandler {

    private static final Logger logger = LoggerFactory.getLogger(GroupMembershipHandler.class);

    private static final String WELCOME =
        "👋 Hi! I'm ready to run a Tacticl project in this group. " +
        "A linked Tacticl user can claim this group by sending /init.";

    private final TelegramConfig config;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramOutboundQueue outbound;

    public GroupMembershipHandler(TelegramConfig config,
                                  TelegramProjectLinkRepository projectRepo,
                                  TelegramOutboundQueue outbound) {
        this.config = config;
        this.projectRepo = projectRepo;
        this.outbound = outbound;
    }

    public void handle(ChatMemberUpdate update) {
        String newStatus = update.new_chat_member() != null ? update.new_chat_member().status() : null;
        String oldStatus = update.old_chat_member() != null ? update.old_chat_member().status() : null;
        String username  = update.new_chat_member() != null && update.new_chat_member().user() != null
                           ? update.new_chat_member().user().username() : null;

        // Only act on events about the bot itself
        if (username == null || !username.equalsIgnoreCase(config.getBotUsername())) return;

        long chatId = update.chat().id();

        boolean added = isPresent(newStatus) && !isPresent(oldStatus);
        boolean removed = isPresent(oldStatus) && !isPresent(newStatus);

        if (added) {
            outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, WELCOME)));
        } else if (removed) {
            projectRepo.findByChatIdAndIsActiveTrue(chatId).ifPresent(link -> {
                link.orphan();
                projectRepo.save(link);
                logger.info("Project {} orphaned by bot removal from chat {}", link.getProjectId(), chatId);
            });
        }
    }

    private boolean isPresent(String status) {
        return "member".equals(status) || "administrator".equals(status) || "creator".equals(status);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :business:business-telegram:test --tests GroupMembershipHandlerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/event/GroupMembershipHandler.java \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/event/GroupMembershipHandlerTest.java
git commit -m "feat(telegram): handle bot add/remove via my_chat_member"
```

### Task 17: `GroupMigrationHandler`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/event/GroupMigrationHandler.java`
- Test: `.../event/GroupMigrationHandlerTest.java`

Behavior: when `message.migrate_to_chat_id` is set, find the `TelegramProjectLink` by old `chat.id()` and update its `chatId` to the new supergroup id. Clear `forumTopics` (they don't survive migration) and clear `pinnedStatusMessageId`.

- [ ] **Step 1: Write failing test** (stub: set up old link, feed migration event, assert new chatId + cleared topics)
- [ ] **Step 2: Run to verify fail**
- [ ] **Step 3: Implement** (~25 lines — repo lookup by old id → mutate → save)
- [ ] **Step 4: Run to verify pass**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(telegram): handle supergroup migration, remap chat id"
```

---

## Chunk 6: Command Router + Core Commands

Goal: replace the inline if/else in `TelegramDispatchService` with a `TelegramCommandRouter` that dispatches to `CommandHandler`s. Implement the commands.

### Task 18: `CommandHandler` interface + `TelegramCommandRouter`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/router/CommandHandler.java`
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/router/CommandContext.java`
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/router/TelegramCommandRouter.java`
- Test: `.../router/TelegramCommandRouterTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void routesToRegisteredHandler() {
    var h = mock(CommandHandler.class);
    when(h.commandName()).thenReturn("/init");
    when(h.scope()).thenReturn(CommandHandler.Scope.GROUP);
    var router = new TelegramCommandRouter(List.of(h));

    var ctx = new CommandContext(-100L, 42L, "/init", "alice", Message.mock());
    router.dispatch(ctx);
    verify(h).handle(ctx);
}
```

- [ ] **Step 2: Run to verify fail**
- [ ] **Step 3: Implement**

```java
// CommandHandler.java
package io.tacticl.business.telegram.router;

public interface CommandHandler {
    enum Scope { GROUP, DM, ANY }
    String commandName();
    Scope scope();
    void handle(CommandContext ctx);
}
```

```java
// CommandContext.java
package io.tacticl.business.telegram.router;

import io.tacticl.client.telegram.dto.Message;

public record CommandContext(
    long chatId,
    long telegramUserId,
    String text,
    String senderUsername,
    Message raw
) {
    public String chatType() { return raw.chat().type(); }
    public boolean isGroup() {
        String t = chatType();
        return "group".equals(t) || "supergroup".equals(t);
    }
    public String argsAfterCommand() {
        int idx = text.indexOf(' ');
        return idx < 0 ? "" : text.substring(idx + 1).trim();
    }
}
```

```java
// TelegramCommandRouter.java
package io.tacticl.business.telegram.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramCommandRouter {

    private static final Logger logger = LoggerFactory.getLogger(TelegramCommandRouter.class);

    private final Map<String, List<CommandHandler>> handlers;

    public TelegramCommandRouter(List<CommandHandler> all) {
        this.handlers = all.stream().collect(Collectors.groupingBy(CommandHandler::commandName));
    }

    public boolean dispatch(CommandContext ctx) {
        String token = ctx.text().split("\\s+", 2)[0].split("@", 2)[0]; // strip @botname
        List<CommandHandler> candidates = handlers.getOrDefault(token, List.of());
        for (CommandHandler h : candidates) {
            if (matches(h.scope(), ctx.isGroup())) {
                try { h.handle(ctx); } catch (Exception e) { logger.error("Handler {} failed", token, e); }
                return true;
            }
        }
        return false;
    }

    private boolean matches(CommandHandler.Scope scope, boolean group) {
        return scope == CommandHandler.Scope.ANY
            || (scope == CommandHandler.Scope.GROUP && group)
            || (scope == CommandHandler.Scope.DM && !group);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(telegram): command router dispatching by name + scope"
```

### Task 19: `InitCommand` — claim a group as a Tacticl project

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/InitCommand.java`
- Test: `.../command/InitCommandTest.java`

Required behavior:
1. Verify sender is linked (use `TelegramIdentityResolver`). If not → DM nudge + return.
2. Verify group not already claimed. If it is → reply "Already linked to project X".
3. Create a new Tacticl project (call `ProjectService.create(ownerUserId, name)`). For V1 the project name is the group title. Project abstraction may not exist yet — see note.
4. Persist `TelegramProjectLink`.
5. Enqueue welcome message citing owner + available commands.

**Note on `ProjectService`:** Tacticl currently does not have an explicit `Project` entity separate from sparks. For V1, treat `projectId` as "the group's project bucket": generate a UUID, store it on `TelegramProjectLink`, and attach that id to each spark via a new `Spark.projectId` field (Task 26). No separate `Project` collection is created. Follow-up: formalize a `Project` entity once multiple surfaces need it.

- [ ] **Step 1: Write failing test** — 3 scenarios (unlinked, already claimed, happy path)
- [ ] **Step 2: Run to verify fail**
- [ ] **Step 3: Implement** (~60 lines)
- [ ] **Step 4: Run to verify pass**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(telegram): /init claims group as Tacticl project"
```

### Task 20: `GrantCommand`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/GrantCommand.java`
- Test: `.../command/GrantCommandTest.java`

Behavior:
1. Must be sent in group, by owner or admin. Permission check via `MemberPermissionService.require(..., ADMIN)`.
2. Parse args: `/grant @username <role>` (role case-insensitive, one of: observer, contributor, runner, admin).
3. Resolve `@username` → `telegramUserId` via group membership probe (`getChatMember`). Need a new `TelegramBotClient.getChatMember(chatId, userId)` and a lookup by username — Telegram has NO server-side username→id lookup on bot API. Solution: maintain a cache seeded from `message.from` in every received message (see `Task 22`). If cache-miss, reply "I haven't seen @alice speak in this group yet; ask them to say hi first."
4. Resolve `telegramUserId → tacticlUserId` via `TelegramIdentityResolver`. If none, nudge "@alice must link their Tacticl account first."
5. Call `MemberPermissionService.grant(...)`.
6. Reply confirmation in-group.

- [ ] **Step 1–5:** TDD for each branch (unauthorized, unparsable role, unknown-user, unlinked user, happy path).

```bash
git commit -m "feat(telegram): /grant sets member permission"
```

### Task 21: `RevokeCommand`, `TransferCommand`, `MembersCommand`

Each follows the `GrantCommand` pattern. TDD cycles condensed — test the 2–3 branches each.

- `RevokeCommand` — admin+, revoke grant. Reject revoking owner.
- `TransferCommand` — owner only. Atomically: update `TelegramProjectLink.ownerUserId`, downgrade former owner to `ADMIN` grant, ensure the new owner has no redundant grant.
- `MembersCommand` — anyone. List all grants + owner. Format as Markdown table.

- [ ] Implement each with ≥2 test cases.
- [ ] Commit after each:

```bash
git commit -m "feat(telegram): /revoke clears a member grant"
git commit -m "feat(telegram): /transfer rotates ownership"
git commit -m "feat(telegram): /members lists project permissions"
```

### Task 22: Username cache

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/identity/TelegramUsernameCache.java`
- Test: `.../identity/TelegramUsernameCacheTest.java`

Simple `ConcurrentMap<Long /*chatId*/, Map<String /*username lowercase*/, Long /*telegramUserId*/>>`. Populated on every inbound message (`TelegramDispatchService` calls `cache.observe(chatId, from)`). Used by `GrantCommand` + `TransferCommand`.

TTL: none for V1 (entries are small, membership is stable). Add eviction if size > 10k.

- [ ] TDD: observe + lookup + case-insensitive match.
- [ ] Commit:

```bash
git commit -m "feat(telegram): cache @username → telegramUserId per chat"
```

### Task 23: `HelpCommand`, `StatusCommand`, `ArchiveCommand`, `LeaveCommand`

- `HelpCommand`: renders commands available to the sender's current role.
- `StatusCommand`: returns active spark count + last activity + cost-to-date. Depends on `PipelineStateManager` query by `projectId` (Chunk 8).
- `ArchiveCommand`: owner-only. Marks `TelegramProjectLink.archive()`, cancels active sparks for project, enqueues farewell, stays in group.
- `LeaveCommand`: owner/admin. Archives + calls `bot.leaveChat(chatId)`.

Each: TDD 2 cases, commit.

```bash
git commit -m "feat(telegram): /help + /status + /archive + /leave"
```

---

## Chunk 7: Spark Dispatch from Group

Goal: `/spark <text>` or plain natural-language message in a group starts a PDLC run bound to the project.

### Task 24: `Spark.projectId` field

Add to `data-sparks/Spark.java` (mirror Task 6): `private String projectId;` + getter/setter. Unit test + commit (tiny).

```bash
git commit -m "feat(sparks): add projectId for group-scoped provenance"
```

### Task 25: `TelegramSparkInitiator`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/spark/TelegramSparkInitiator.java`
- Test: `.../spark/TelegramSparkInitiatorTest.java`

Dependencies: `SparkService`, `PdlcRouter`, `MemberPermissionService`, `TelegramOutboundQueue`.

Flow:
1. `require(chatId, userId, CONTRIBUTOR)` — else reply "insufficient permission".
2. Determine spark tier. For Phase 2, default to Tier 1 (mutation). Tier 2 gates on `OWNER`.
3. `SparkService.create(userId, text)` — pass `initiatorSource=TELEGRAM_GROUP`, `initiatorUserId=userId`, `projectId=link.projectId`.
4. `PdlcRouter.route(...)` — pass through repo/tier/cost ceiling. If `pdlc.v2.enabled=false`, call `CloudOrchestratorService` (old path) — scope that later.
5. Enqueue acknowledgment: "▶️ Started — I'll post updates here."

- [ ] TDD: permission-denied branch, happy-path branch, PDLC-disabled branch.

```bash
git commit -m "feat(telegram): initiate sparks from group messages"
```

### Task 26: `SparkCommand` + plain-text mention handler

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/command/SparkCommand.java`
- Modify: `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramDispatchService.java` — plain-text branch calls `TelegramSparkInitiator` when message mentions `@tacticl_bot` or is a reply-to-bot.

TDD: 3 scenarios (explicit `/spark`, mention, reply-to-bot).

```bash
git commit -m "feat(telegram): /spark + @mention triggers project spark"
```

---

## Chunk 8: Pipeline Event Channel + Inline Checkpoints

Goal: PDLC role outputs post into the group (general or per-role topic); checkpoints render as inline keyboards; callbacks route through a permission-aware resolver.

### Task 27: Introduce `PipelineEventChannel` interface

**Files:**
- Create: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/channel/PipelineEventChannel.java`
- Modify: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PipelineEventEmitter.java` to fan out to `List<PipelineEventChannel>`.
- Test: `.../service/PipelineEventEmitterTest.java` — verify all registered channels receive emits.

```java
public interface PipelineEventChannel {
    void emit(String pipelineRunId, String eventName, Object payload);
    default void complete(String pipelineRunId) {}
}
```

Existing SSE logic wraps into a `SseEventChannel` implementation.

- [ ] TDD: 3 channels registered → all 3 invoked once per emit.

```bash
git commit -m "refactor(pipeline): pluggable PipelineEventChannel (SSE + extensible)"
```

### Task 28: `TelegramMessageFormatter`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/pipeline/TelegramMessageFormatter.java`
- Test: `.../pipeline/TelegramMessageFormatterTest.java`

Responsibilities: turn role events (`RESEARCHER_STARTED`, `ARCHITECT_ARTIFACT_READY`, `REVIEWER_NEEDS_APPROVAL`) into `SendMessageRequest`. Uses Markdown2, keeps ≤ 4096 chars, truncates + adds artifact link when overflow.

- [ ] TDD per event type.

```bash
git commit -m "feat(telegram): format PDLC events as Telegram messages"
```

### Task 29: `TelegramEventChannel`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/pipeline/TelegramEventChannel.java`
- Test: `.../pipeline/TelegramEventChannelTest.java`

Lookup: `pipelineRunId → sparkId → spark.projectId → TelegramProjectLink`. If no link or `status != ACTIVE`, skip silently. Otherwise, enqueue the formatted message(s) via `TelegramOutboundQueue`. For role-specific events with a known `forumTopics[role]`, set `message_thread_id` on the `SendMessageRequest`.

- [ ] TDD: project-less run skipped, linked run routed to general, linked run with topics routed to correct thread.

```bash
git commit -m "feat(telegram): pipeline events stream to group (topics-aware)"
```

### Task 30: Inline keyboards for checkpoints

Extend `TelegramMessageFormatter`: for `CHECKPOINT_PENDING` events, include an `InlineKeyboardMarkup` with three buttons:
```
callback_data = "cp:approve:<checkpointId>"
callback_data = "cp:changes:<checkpointId>"
callback_data = "cp:reject:<checkpointId>"
```
(Telegram limits `callback_data` to 64 bytes — OK with short ids.)

- [ ] TDD: event → expected markup shape.

```bash
git commit -m "feat(telegram): inline keyboard for checkpoint approval"
```

### Task 31: `CallbackQueryHandler` + `TelegramCheckpointResolver`

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/event/CallbackQueryHandler.java`
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/pipeline/TelegramCheckpointResolver.java`
- Test for each

Flow:
1. Parse `callback_data` → `(action, checkpointId)`.
2. Resolve `telegramUserId → tacticlUserId`. If absent → `answerCallbackQuery("Link your Tacticl account first")`.
3. Permission: `require(chatId, userId, RUNNER)`. If denied → `answerCallbackQuery("Need runner permission")`.
4. Call `PdlcV2Service.resolveCheckpoint(userId, sparkId, checkpointId, decision, feedback=null)` (sparkId derived via checkpoint lookup; make a `CheckpointRepository.findById` call).
5. `answerCallbackQuery("✅ Approved")` + edit the original message to remove buttons via `editMessageText` (disable keyboard).

- [ ] TDD per branch.

```bash
git commit -m "feat(telegram): checkpoint callbacks routed to PDLC resolver"
```

---

## Chunk 9: Forum Topics + Pinned Status Message

Goal: auto-create per-role topics when the group supports them, and maintain a debounced pinned status message.

### Task 32: Forum topic auto-creation

After `/init`, if `chat.type == "supergroup"` and `chat.is_forum == true` (extend `Chat` DTO with `is_forum`), iterate `PdlcRole.values()` and call `bot.createForumTopic(chatId, role.name())`. Persist ids into `TelegramProjectLink.forumTopics`.

Gate behind chat capability probe; if Telegram rejects (`CHAT_NOT_FORUM`), log + skip.

- [ ] Extend `Chat.java` DTO with `boolean is_forum` + test.
- [ ] Integrate with `InitCommand`.
- [ ] TDD: forum-enabled chat → topics created + persisted; non-forum → skipped.

```bash
git commit -m "feat(telegram): auto-create per-role forum topics on /init"
```

### Task 33: `PinnedStatusService` with debounced edits

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/pipeline/PinnedStatusService.java`
- Test: `.../pipeline/PinnedStatusServiceTest.java`

Behavior:
- On first status request, send + pin message, store `pinnedStatusMessageId`.
- Subsequent calls enqueue a debounced edit (one edit / 10s). Implementation: per-chat `AtomicReference<PendingStatus>` + `@Scheduled(fixedDelay=2000)` that flushes stale pending updates.
- Content template: current phase, last activity, pending checkpoints, spend-to-date, active sparks.

- [ ] TDD: two rapid updates within 10s → one `editMessageText` call after drain.

```bash
git commit -m "feat(telegram): debounced pinned status message"
```

---

## Chunk 10: Voice, Media, Audit, DM Commands, Wire-Up, E2E

### Task 34: Voice message handler

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/event/VoiceMessageHandler.java`
- Depends on existing Whisper client in `business-voice` (audit for its entry method; if unavailable, raise as dependency).

Flow: download voice file via `bot.getFile(file_id)` → transcribe → hand the transcript to `TelegramSparkInitiator` as if it were text.

- [ ] TDD: mocked whisper → initiator invoked with transcript.

```bash
git commit -m "feat(telegram): voice → whisper → spark"
```

### Task 35: Media attachment output

In `TelegramMessageFormatter`, for events referencing a `PipelineArtifact` with a file ≤ 50 MB, use `bot.sendDocument` instead of `sendMessage`. Otherwise include artifact URL.

- [ ] TDD: small artifact → `sendDocument` enqueued; large → URL in text.

```bash
git commit -m "feat(telegram): attach small artifacts as documents"
```

### Task 36: Audit logging

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/audit/TelegramAuditLogger.java`
- Integrates with existing `agent_audit_log` collection (see `data-sparks` / wherever `AgentAuditLog` lives) — add a `source = "TELEGRAM_GROUP"` discriminator field if it doesn't exist.

Every command handler calls `audit.record(chatId, telegramUserId, tacticlUserId, action, payload)`.

- [ ] TDD: two-three paths assert audit row written.

```bash
git commit -m "feat(telegram): audit every group command into agent_audit_log"
```

### Task 37: DM command completion

Add DM-scoped handlers:
- `WhoamiCommand` — replies with linked Tacticl identity (email + handle).
- `ProjectsCommand` — list projects the sender is a member of + their role.
- `UnlinkCommand` — calls `TelegramUserLinker.unlink(...)` + removes all `TelegramMemberGrant`s (owner-orphan guard per resolution #3).

- [ ] TDD each.

```bash
git commit -m "feat(telegram): DM commands /whoami, /projects, /unlink"
```

### Task 38: BotFather command list registration

At startup (new `TelegramCommandRegistrar` similar to `TelegramWebhookRegistrar`), call `bot.setMyCommands(...)` twice:
- scope `all_private_chats` → DM commands.
- scope `all_group_chats` → group commands.

- [ ] Integration test mocks `TelegramBotClient.setMyCommands`.

```bash
git commit -m "feat(telegram): publish command list via setMyCommands on startup"
```

### Task 39: Wire-up `TelegramDispatchService`

Modify the existing service to:
1. Cache `from` via `TelegramUsernameCache.observe`.
2. If `update.my_chat_member() != null` → `GroupMembershipHandler.handle(...)`.
3. If `update.callback_query() != null` → `CallbackQueryHandler.handle(...)`.
4. If `update.message().migrate_to_chat_id != null` → `GroupMigrationHandler.handle(...)`.
5. If `update.message().voice() != null` → `VoiceMessageHandler.handle(...)`.
6. Else if `update.message().text()` starts with `/` → `TelegramCommandRouter.dispatch(...)`.
7. Else if group mentions bot or replies to bot → `TelegramSparkInitiator`.
8. Else (DM text without `/`) → existing "tap your dashboard link" response.

Replace the existing Phase 1 text content via the new handlers. Keep `@ConditionalOnProperty`.

- [ ] Refresh `TelegramDispatchServiceTest` with new branches (all mocked collaborators).

```bash
git commit -m "refactor(telegram): dispatch fans out to handlers + router"
```

### Task 40: Config + feature flags + Vault

Add to `application-api/src/main/resources/application.properties`:
```
tacticl.telegram.outbound.capacity=200
tacticl.telegram.outbound.drain-ms=50
tacticl.telegram.status-debounce-ms=10000
```

Update the runbook `docs/runbooks/telegram-bot.md` (create if missing) with: Privacy Mode OFF, new commands, orphan/archive semantics.

- [ ] Commit:

```bash
git commit -m "chore(telegram): outbound + debounce config + runbook update"
```

### Task 41: End-to-end smoke test (manual)

After a fresh QA deploy:

1. Create a Telegram group "Tacticl QA Smoke".
2. Add `@tacticl_qa_bot` as admin with topics enabled.
3. Verify bot posts welcome. Send `/init`. Expect project-created reply + pinned status.
4. Invite second user (teammate account). Teammate runs `/whoami` in DM → prompted to link. Link via `/v1/telegram/link` deeplink.
5. Owner: `/grant @teammate runner`. Teammate: `/spark plan a haiku`. Expect status updates in general (or per-role topic).
6. On a `CHECKPOINT_PENDING`: teammate taps `Approve`. Pipeline resumes.
7. `/members` → lists both.
8. `/archive` → bot farewell + status `ARCHIVED` in DB.
9. Add bot back to same group → `my_chat_member` re-welcomes; `/init` succeeds (new project).

Document results in `docs/runbooks/telegram-bot.md` + a short note on any rate-limit observations.

- [ ] Commit:

```bash
git commit -m "docs(telegram): smoke-test runbook for group-project flow"
```

---

## Plan Summary

| Chunk | Focus | Est. |
|---|---|---|
| 1 | Data model + Spark fields | 1d |
| 2 | DTOs + client methods | 1.5d |
| 3 | Identity + permissions | 1d |
| 4 | Outbound queue + drainer | 0.5d |
| 5 | Group events (add/remove, migrate) | 1d |
| 6 | Command router + core commands | 3d |
| 7 | Group spark dispatch | 1d |
| 8 | Pipeline channel + checkpoints | 2d |
| 9 | Forum topics + pinned status | 1.5d |
| 10 | Voice + media + audit + DM + wire + smoke | 2.5d |

**Total:** ~14–15 focused days. Chunks 1–3 must ship first; 4–10 can parallelize across subagents once foundations land.

**Reference skills:** @superpowers:subagent-driven-development, @superpowers:executing-plans, @superpowers:test-driven-development.
