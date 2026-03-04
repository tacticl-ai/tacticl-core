# Data Framework Base Migration — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace tacticl's local Firestore base classes with cidadel's `data-framework-base`, so all entities extend `BaseEntity` and all repos extend `BaseRepository`/`SubcollectionRepository`.

**Architecture:** Add `data-framework-base` dependency, migrate 21 entities to extend `BaseEntity` with `@Collection`, migrate 20 repos to extend cidadel base repos with `FirestoreAuditingHandler`, update ~70 caller sites to new save signatures, delete local base classes.

**Tech Stack:** Java 21, cidadel `data-framework-base` 0.1.0-SNAPSHOT, Spring Boot 3.5.7, Firestore

---

## Task 1: Add data-framework-base dependency and Spring config

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `data/build.gradle.kts`
- Modify: `application/build.gradle.kts`
- Modify: `application/src/main/java/io/strategiz/social/application/TacticlApplication.java`

**Step 1: Add library to version catalog**

In `gradle/libs.versions.toml`, add after the existing cidadel entries in `[libraries]`:

```toml
cidadel-data-framework-base = { module = "io.cidadel:data-framework-base", version.ref = "cidadel" }
```

**Step 2: Add to data parent build**

In `data/build.gradle.kts`, add to the shared dependencies block:

```kotlin
add("implementation", rootProject.libs.cidadel.data.framework.base)
```

**Step 3: Add to application assembler**

In `application/build.gradle.kts`, add after the existing cidadel imports:

```kotlin
implementation(libs.cidadel.data.framework.base)
```

**Step 4: Update component scan**

In `TacticlApplication.java`, add cidadel data base packages to `scanBasePackages`, excluding Firebase config classes:

```java
@SpringBootApplication(scanBasePackages = {
    // Tacticl product code
    "io.strategiz.social",
    "io.tacticl",
    // Cidadel framework
    "io.cidadel.framework.exception",
    "io.cidadel.framework.secrets",
    "io.cidadel.framework.authorization",
    "io.cidadel.framework.logging",
    "io.cidadel.framework.llmrouter",
    "io.cidadel.framework.token",
    "io.cidadel.framework.apidocs",
    // Cidadel service + client base
    "io.cidadel.service.base",
    "io.cidadel.client.base",
    // Cidadel data base (auditing, transactions — excludes Firebase config)
    "io.cidadel.identity.data.base.audit",
    "io.cidadel.identity.data.base.transaction",
    "io.cidadel.identity.data.base.validation",
    // LLM clients
    "io.strategiz.client.anthropic",
    "io.strategiz.client.openai",
    "io.strategiz.client.grok"
})
```

Note: We deliberately do NOT scan `io.cidadel.identity.data.base.config` — that contains `FirebaseConfig` and `FirebaseVaultConfig` which hardcode `secret/strategiz/firebase` vault path. Tacticl keeps its own `FirestoreConfig`.

**Step 5: Verify build compiles**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL (new dependency resolves, no bean conflicts)

**Step 6: Commit**

```bash
git add gradle/libs.versions.toml data/build.gradle.kts application/build.gradle.kts application/src/main/java/io/strategiz/social/application/TacticlApplication.java
git commit -m "feat: add cidadel data-framework-base dependency and component scan"
```

---

## Task 2: Migrate data-social flat-collection entities to BaseEntity

**Files:**
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/Spark.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/Tactic.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/SocialPost.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/TacticlUser.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/ExecutionLog.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/Checkpoint.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/AgentAuditLog.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/ActionConfirmation.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceSession.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceCommand.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/PairingCode.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/PairingSession.java`

For EACH entity, apply this pattern:

1. Add `extends BaseEntity` and `@Collection("collection_name")`
2. Add `import io.cidadel.identity.data.base.entity.BaseEntity;` and `import io.cidadel.identity.data.base.annotation.Collection;`
3. Add `@Override` to existing `getId()` and `setId()` methods
4. Remove `isActive` field + getter/setter (inherited from BaseEntity)
5. Remove `createdAt` field + getter/setter (replaced by BaseEntity's `createdDate`)
6. Remove `updatedAt` field + getter/setter (replaced by BaseEntity's `modifiedDate`)
7. Remove any `this.isActive = true;` from constructors (BaseEntity defaults this)
8. Keep domain-specific fields like `completedAt`, `lastSeenAt`, `closedAt`, `timestamp`, `expiresAt`
9. Keep `@IgnoreExtraProperties` annotation

**Entity-specific notes:**

| Entity | Collection | Remove fields | Keep fields | Constructor changes |
|--------|-----------|--------------|-------------|-------------------|
| Spark | `sparks` | `isActive`, `createdAt` | `completedAt` | Remove `this.isActive = true` |
| Tactic | `tactics` | `isActive`, `createdAt` | `completedAt` | Remove `this.isActive = true` |
| SocialPost | `social_posts` | `isActive`, `createdAt`, `updatedAt` | `stateChangedAt` | Remove `this.isActive = true` |
| TacticlUser | `tacticl_users` | `createdAt` | — | No constructor changes |
| ExecutionLog | `execution_logs` | — | `timestamp` (domain-specific) | No changes |
| Checkpoint | `checkpoints` | `createdAt` | `decidedAt` | No changes |
| AgentAuditLog | `agent_audit_log` | `createdAt` | — | No changes |
| ActionConfirmation | `action_confirmations` | `createdAt` | `expiresAt`, `resolvedAt` | No changes |
| DeviceSession | `device_sessions` | `isActive` | `connectedAt`, `lastPingAt` | Remove `this.isActive = true` |
| DeviceCommand | `device_commands` | `createdAt` | `sentAt`, `completedAt`, `expiresAt` | No changes |
| PairingCode | `pairing_codes` | `createdAt` | `expiresAt` | No changes |
| PairingSession | `pairing_sessions` | `createdAt` | `expiresAt` | No changes |

**Example — Spark.java after migration:**

```java
package io.strategiz.social.data.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.identity.data.base.annotation.Collection;
import io.cidadel.identity.data.base.entity.BaseEntity;

@IgnoreExtraProperties
@Collection("sparks")
public class Spark extends BaseEntity {

    private String id;
    private String userId;
    private String title;
    private String description;
    private String type;
    private SparkState status;
    private SparkPriority priority;
    private String deviceId;
    private String schedule;
    private Instant nextRunAt;
    private CheckpointPolicy checkpointPolicy;
    private List<String> repoAccess;
    private Map<String, Object> result;
    private String parentSparkId;
    private long totalTokens;
    private BigDecimal estimatedCost;
    private Map<String, String> models;
    private ExecutionMode executionMode;
    private String browserSessionId;
    private long browserMinutesUsed;
    private Instant completedAt;  // domain-specific, kept

    public Spark() {
        this.status = SparkState.PENDING;
        this.priority = SparkPriority.NORMAL;
        this.checkpointPolicy = CheckpointPolicy.CHECKPOINT_MAJOR;
        this.repoAccess = new ArrayList<>();
        this.totalTokens = 0;
        this.estimatedCost = BigDecimal.ZERO;
        this.browserMinutesUsed = 0;
        // isActive defaults to true via BaseEntity
    }

    @Override
    public String getId() { return id; }

    @Override
    public void setId(String id) { this.id = id; }

    // ... all other getters/setters unchanged, minus isActive/createdAt
}
```

**Step 13: Verify build compiles**

Run: `./gradlew :data:data-social:compileJava`
Expected: BUILD SUCCESSFUL

**Step 14: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/
git commit -m "refactor: migrate data-social flat entities to extend BaseEntity"
```

---

## Task 3: Migrate data-social subcollection entities to BaseEntity

**Files:**
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceRegistration.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/SocialIntegration.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/Reminder.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/AgentToken.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/RepoGrant.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/SparkTemplate.java`

Same pattern as Task 2. Entity-specific notes:

| Entity | Collection | Remove fields | Keep fields | Constructor changes |
|--------|-----------|--------------|-------------|-------------------|
| DeviceRegistration | `devices` | `isActive`, `createdAt` | `lastSeenAt` | Remove `this.isActive = true` |
| SocialIntegration | `social_integrations` | `isActive`, `createdAt`, `updatedAt` | `tokenExpiresAt` | Remove `this.isActive = true` field init |
| Reminder | `reminders` | `createdAt` | `triggerAt`, `completedAt` | No changes |
| AgentToken | `agent_tokens` | `isActive` | — | Remove `this.isActive = true` |
| RepoGrant | `repo_grants` | `isActive` | `grantedAt` | Remove `this.isActive = true` |
| SparkTemplate | `spark_templates` | `isActive` | — | Remove `this.isActive = true` |

**Note:** The `@Collection` annotation value for subcollection entities is the subcollection name (e.g., `"devices"`, not `"tacticl_users/{userId}/devices"`) — the parent path is set in the repository.

**Step 7: Verify build compiles**

Run: `./gradlew :data:data-social:compileJava`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/
git commit -m "refactor: migrate data-social subcollection entities to extend BaseEntity"
```

---

## Task 4: Migrate data-browser entities to BaseEntity

**Files:**
- Modify: `data/data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserSession.java`
- Modify: `data/data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserActionLog.java`
- Modify: `data/data-browser/src/main/java/io/tacticl/browser/data/entity/UserFile.java`

| Entity | Collection | Remove fields | Keep fields |
|--------|-----------|--------------|-------------|
| BrowserSession | `browser_sessions` | `createdAt` | `lastActiveAt`, `closedAt` |
| BrowserActionLog | `browser_action_logs` | — | `timestamp` (domain-specific) |
| UserFile | `user_files` | `createdAt` | `expiresAt` |

**Step 4: Verify build compiles**

Run: `./gradlew :data:data-browser:compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add data/data-browser/src/main/java/io/tacticl/browser/data/entity/
git commit -m "refactor: migrate data-browser entities to extend BaseEntity"
```

---

## Task 5: Migrate flat-collection repositories to BaseRepository

**Files:**
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/TacticlUserRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/SparkRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/TacticRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/SocialPostRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/ExecutionLogRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/CheckpointRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/AgentAuditLogRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/ActionConfirmationRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/DeviceSessionRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/DeviceCommandRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/PairingCodeRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/PairingSessionRepository.java`
- Modify: `data/data-browser/src/main/java/io/tacticl/browser/data/repository/BrowserSessionRepository.java`
- Modify: `data/data-browser/src/main/java/io/tacticl/browser/data/repository/BrowserActionLogRepository.java`
- Modify: `data/data-browser/src/main/java/io/tacticl/browser/data/repository/UserFileRepository.java`

For EACH flat repository, apply this pattern:

1. Change `extends FirestoreRepository<T>` to `extends BaseRepository<T>`
2. Add imports: `io.cidadel.identity.data.base.repository.BaseRepository` and `io.cidadel.identity.data.base.audit.FirestoreAuditingHandler`
3. Remove import of local `FirestoreRepository`
4. Update constructor: add `FirestoreAuditingHandler auditingHandler` parameter, remove `String collectionName` parameter
5. Call `super(firestore, EntityClass.class, auditingHandler)` — collection name comes from `@Collection` annotation on entity
6. Add `@Override protected String getModuleName() { return "data-social"; }` (or `"data-browser"`)
7. Update custom query methods:
   - `findByField("field", value)` from cidadel's BaseRepository already filters `isActive=true` — remove any manual `.filter(Entity::isActive)` calls
   - `getCollection()` still works for custom queries
   - `executeQuery(Query)` — cidadel's BaseRepository doesn't have this; replace with inline query execution or add as custom protected method

**Example — SparkRepository.java after migration:**

```java
package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.SparkState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class SparkRepository extends BaseRepository<Spark> {

    public SparkRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
        super(firestore, Spark.class, auditingHandler);
    }

    @Override
    protected String getModuleName() {
        return "data-social";
    }

    public List<Spark> findActiveByUserId(String userId) {
        List<Spark> all = findByField("userId", userId);
        // findByField already filters isActive=true
        return all.stream()
            .filter(s -> s.getStatus() != SparkState.COMPLETED
                    && s.getStatus() != SparkState.FAILED
                    && s.getStatus() != SparkState.CANCELLED)
            .sorted((a, b) -> {
                if (b.getCreatedDate() == null || a.getCreatedDate() == null) return 0;
                return b.getCreatedDate().compareTo(a.getCreatedDate());
            })
            .toList();
    }

    public List<Spark> findByStatus(SparkState status) {
        return findByField("status", status.name());
    }

    public List<Spark> findScheduledDue(Instant now) {
        List<Spark> scheduled = findByStatus(SparkState.SCHEDULED);
        List<Spark> completed = findByStatus(SparkState.COMPLETED);

        List<Spark> combined = new ArrayList<>();
        combined.addAll(scheduled);
        combined.addAll(completed);

        return combined.stream()
            .filter(s -> s.getSchedule() != null && !s.getSchedule().isEmpty())
            .filter(s -> s.getNextRunAt() != null && !s.getNextRunAt().isAfter(now))
            .toList();
    }

    public List<Spark> findByUserIdAndStatus(String userId, SparkState status) {
        return findByField("userId", userId).stream()
            .filter(s -> s.getStatus() == status)
            .toList();
    }

    public List<Spark> findRecentByUserId(String userId, int limit) {
        List<Spark> all = findByField("userId", userId);
        return all.stream()
            .sorted((a, b) -> {
                if (b.getCreatedDate() == null || a.getCreatedDate() == null) return 0;
                return b.getCreatedDate().compareTo(a.getCreatedDate());
            })
            .limit(limit)
            .toList();
    }
}
```

**Critical note on `executeQuery`:** Cidadel's `BaseRepository` does NOT have a protected `executeQuery(Query)` method. Repos that use it (e.g., `SocialPostRepository.findDueForPublishing()`, `SocialIntegrationRepository`) need to inline the query execution:

```java
protected List<T> executeQuery(Query query) {
    try {
        return query.get().get().getDocuments().stream()
            .map(doc -> {
                T entity = doc.toObject(entityClass);
                entity.setId(doc.getId());
                return entity;
            })
            .collect(Collectors.toList());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Query interrupted", e);
    } catch (java.util.concurrent.ExecutionException e) {
        throw new RuntimeException("Query failed", e);
    }
}
```

Note: `entityClass` is a `protected final` field in `BaseRepository`, so it's accessible. However, if it's private, use `getCollection()` approach instead. Check accessibility at implementation time.

**Step 16: Verify build compiles**

Run: `./gradlew :data:data-social:compileJava && ./gradlew :data:data-browser:compileJava`
Expected: BUILD SUCCESSFUL

**Step 17: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/repository/ data/data-browser/src/main/java/io/tacticl/browser/data/repository/
git commit -m "refactor: migrate flat-collection repositories to BaseRepository"
```

---

## Task 6: Migrate subcollection repositories to SubcollectionRepository

**Files:**
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/DeviceRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/SocialIntegrationRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/ReminderRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/AgentTokenRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/RepoGrantRepository.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/repository/SparkTemplateRepository.java`

For EACH subcollection repository:

1. Change `extends FirestoreSubcollectionRepository<T>` to `extends SubcollectionRepository<T>`
2. Add imports: `io.cidadel.identity.data.base.repository.SubcollectionRepository` and `io.cidadel.identity.data.base.audit.FirestoreAuditingHandler`
3. Remove import of local `FirestoreSubcollectionRepository`
4. Update constructor: add `FirestoreAuditingHandler auditingHandler`, remove `String subcollectionName`
5. Call `super(firestore, EntityClass.class, auditingHandler)`
6. Add three abstract method implementations:
   ```java
   @Override protected String getModuleName() { return "data-social"; }
   @Override protected String getParentCollectionName() { return "tacticl_users"; }
   @Override protected String getSubcollectionName() { return "devices"; } // varies per repo
   ```
7. Update custom methods to use cidadel's method names:
   - `findAll(userId)` → `findAllInSubcollection(userId)`
   - `findById(userId, id)` → `findByIdInSubcollection(userId, id)`
   - `getCollectionForUser(userId)` → `getSubcollection(userId)`

**Example — DeviceRepository.java after migration:**

```java
package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.SubcollectionRepository;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository extends SubcollectionRepository<DeviceRegistration> {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);

    public DeviceRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
        super(firestore, DeviceRegistration.class, auditingHandler);
    }

    @Override protected String getModuleName() { return "data-social"; }
    @Override protected String getParentCollectionName() { return "tacticl_users"; }
    @Override protected String getSubcollectionName() { return "devices"; }

    public List<DeviceRegistration> findActiveByUserId(String userId) {
        List<DeviceRegistration> all = findAllInSubcollection(userId);
        logger.debug("Found {} total devices for user {}", all.size(), userId);
        // findAllInSubcollection already filters isActive=true
        return all.stream()
            .filter(d -> d.getState() == DeviceState.ACTIVE)
            .toList();
    }
}
```

**Subcollection name mapping:**

| Repository | `getSubcollectionName()` |
|-----------|-------------------------|
| DeviceRepository | `"devices"` |
| SocialIntegrationRepository | `"social_integrations"` |
| ReminderRepository | `"reminders"` |
| AgentTokenRepository | `"agent_tokens"` |
| RepoGrantRepository | `"repo_grants"` |
| SparkTemplateRepository | `"spark_templates"` |

**Step 7: Verify build compiles**

Run: `./gradlew :data:data-social:compileJava`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/repository/
git commit -m "refactor: migrate subcollection repositories to SubcollectionRepository"
```

---

## Task 7: Update flat-repo caller save sites in business-agent

**Files:**
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/SparkService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/VoiceAgentService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/UserProvisioningService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/UserConfigService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/DevicePairingService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/DeviceCommandService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/CredentialService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/DeviceRegistryService.java`

**Migration pattern for flat repos:**

Old: `repository.save(entity, entity.getId())` or `repository.save(entity, someId)`
New: `entity.setId(someId)` (if not already set) then `repository.save(entity, userId)` or `repository.save(entity)` (SecurityContext)

**SparkService.java** (~25 save calls — largest file):

All SparkService calls follow pattern `sparkRepository.save(spark, spark.getId())`. The `userId` is available on most Spark entities via `spark.getUserId()`. Migration:

```java
// Old:
sparkRepository.save(spark, spark.getId());
// New:
sparkRepository.save(spark, spark.getUserId());

// Old (initial create where sparkId is local var):
sparkRepository.save(spark, sparkId);
// New:
spark.setId(sparkId);
sparkRepository.save(spark, spark.getUserId());
```

For CheckpointRepository and TacticRepository saves in SparkService, userId comes from the parent spark's `getUserId()`.

**VoiceAgentService.java** (1 save call):
```java
// Old:
auditLogRepository.save(log, log.getId());
// New:
auditLogRepository.save(log, log.getUserId());
```

**UserProvisioningService.java** (1 save call):
```java
// Old:
userRepository.save(user, userId);
// New (userId IS the document ID for TacticlUser):
user.setId(userId);
userRepository.save(user, userId);
```

**UserConfigService.java** (1 save call):
```java
// Old:
userRepository.save(user, userId);
// New:
userRepository.save(user, userId);
// Note: user.getId() should already be userId. Verify at implementation time.
```

**DevicePairingService.java** (flat repo saves — PairingCode, PairingSession):
```java
// Old:
pairingCodeRepository.save(pairingCode, pairingCode.getId());
// New:
pairingCodeRepository.save(pairingCode, pairingCode.getUserId());
```

**DeviceCommandService.java** (4 saves):
```java
// Old:
deviceCommandRepository.save(cmd, cmd.getId());
// New:
deviceCommandRepository.save(cmd, cmd.getUserId());
```

**Step 9: Verify build compiles**

Run: `./gradlew :business:business-agent:compileJava`
Expected: BUILD SUCCESSFUL

**Step 10: Commit**

```bash
git add business/business-agent/src/main/java/
git commit -m "refactor: update business-agent flat repo save calls for BaseRepository"
```

---

## Task 8: Update subcollection-repo caller save sites in business-agent

**Files:**
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/DevicePairingService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/CredentialService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/DeviceRegistryService.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/SetReminderSkill.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/ManageDeviceSkill.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/ManageRepoSkill.java`

**Migration pattern for subcollection repos:**

Old: `repository.save(userId, entity, entity.getId())`
New: `repository.saveInSubcollection(userId, entity, userId)`

Note: For subcollection repos, the first `userId` is the parentId (the Firestore document path), and the last `userId` is the audit user. In tacticl's case these are always the same user.

Old: `repository.findById(userId, entityId)`
New: `repository.findByIdInSubcollection(userId, entityId)`

Old: `repository.findAll(userId)`
New: `repository.findAllInSubcollection(userId)`

Old: `repository.delete(userId, entityId)`
New: `repository.deleteInSubcollection(userId, entityId, userId)`

**SetReminderSkill.java** — special case with null ID:
```java
// Old:
reminderRepository.save(userId, reminder, null);
// New (cidadel auto-generates ID if entity has no ID set):
reminderRepository.saveInSubcollection(userId, reminder, userId);
// The entity's getId() is null, so SubcollectionRepository auto-generates an ID
```

**Step 7: Verify build compiles**

Run: `./gradlew :business:business-agent:compileJava`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add business/business-agent/src/main/java/
git commit -m "refactor: update business-agent subcollection repo calls for SubcollectionRepository"
```

---

## Task 9: Update caller sites in business-social, business-browser, and service layer

**Files:**
- Modify: `business/business-social/src/main/java/io/strategiz/social/business/publish/OAuthTokenExchangeService.java`
- Modify: `business/business-browser/src/main/java/io/tacticl/browser/service/BrowserSessionService.java`
- Modify: `business/business-browser/src/main/java/io/tacticl/browser/service/BrowserActionLogger.java`
- Modify: `service/service-social/src/main/java/.../SocialPostController.java`
- Modify: `service/service-social/src/main/java/.../SocialIntegrationController.java`
- Modify: `service/service-repo/src/main/java/.../RepoController.java`
- Modify: `service/service-token/src/main/java/.../TokenController.java`
- Modify: `service/service-agent/src/main/java/.../DeviceSessionManager.java`
- Modify: `service/service-agent/src/main/java/.../SettingsController.java`
- Modify: `service/service-agent/src/main/java/.../AgentController.java`
- Modify: `business/business-agent/src/main/java/.../skill/SchedulePostSkill.java`

Apply the same patterns from Tasks 7 and 8.

**Service layer notes:**
- Controllers have `@AuthUser AuthenticatedUser user` — use `user.getUserId()` as the audit userId
- `SocialPostController`: `socialPostRepository.save(post, post.getId())` → `socialPostRepository.save(post, user.getUserId())`
- `SocialIntegrationController` (subcollection): `save(user.getUserId(), integ, integ.getId())` → `saveInSubcollection(user.getUserId(), integ, user.getUserId())`
- `RepoController` (subcollection): same pattern
- `TokenController` (subcollection): same pattern
- `SettingsController` (subcollection): `save(user.getUserId(), device, device.getId())` → `saveInSubcollection(user.getUserId(), device, user.getUserId())`

**Also update findById calls:**
- Subcollection repos: `findById(userId, id)` → `findByIdInSubcollection(userId, id)`
- Flat repos: `findById(id)` stays the same (BaseRepository has the same signature)

**Step 12: Verify full build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 13: Commit**

```bash
git add business/ service/
git commit -m "refactor: update remaining caller sites for cidadel data-framework-base"
```

---

## Task 10: Update tests

**Files:**
- Modify: `business/business-agent/src/test/java/.../UserConfigServiceTest.java`
- Modify: `business/business-agent/src/test/java/.../DevicePairingServiceTest.java`
- Modify: `business/business-agent/src/test/java/.../ManageDeviceSkillTest.java`
- Modify: `business/business-agent/src/test/java/.../ManageRepoSkillTest.java`
- Modify: `business/business-agent/src/test/java/.../ManageSettingsSkillTest.java`
- Modify: `business/business-agent/src/test/java/.../ConnectionStatusSkillTest.java`
- Modify: `business/business-agent/src/test/java/.../UserDataPurgeServiceTest.java`
- Modify: `business/business-agent/src/test/java/.../DataMigrationServiceTest.java`
- Delete: `data/data-social/src/test/java/.../FirestoreSubcollectionRepositoryTest.java`
- Modify: `data/data-social/src/test/java/.../DeviceRepositoryTest.java`
- Modify: `data/data-social/src/test/java/.../UserConfigTest.java`
- Modify: `data/data-social/src/test/java/.../DeviceSettingsTest.java`

**Migration patterns for tests:**

1. Update `verify(repo).save(entity, id)` → `verify(repo).save(entity, userId)` for flat repos
2. Update `verify(repo).save(userId, entity, id)` → `verify(repo).saveInSubcollection(userId, entity, userId)` for subcollection repos
3. Update mock stubs: `when(repo.findById(id)).thenReturn(...)` stays same for flat; `when(repo.findById(userId, id)).thenReturn(...)` → `when(repo.findByIdInSubcollection(userId, id)).thenReturn(...)` for subcollection
4. Delete `FirestoreSubcollectionRepositoryTest.java` — tests the old base class being removed
5. Update `DeviceRepositoryTest.java` — needs to mock `FirestoreAuditingHandler` in constructor

**Step 11: Run all tests**

Run: `./gradlew test`
Expected: ALL TESTS PASS

**Step 12: Commit**

```bash
git add business/ data/ service/
git commit -m "test: update tests for cidadel data-framework-base migration"
```

---

## Task 11: Delete local base classes and clean up

**Files:**
- Delete: `data/data-social/src/main/java/io/strategiz/social/data/repository/FirestoreRepository.java`
- Delete: `data/data-social/src/main/java/io/strategiz/social/data/repository/FirestoreSubcollectionRepository.java`

**Step 1: Verify no remaining references**

Search for any remaining imports of the old base classes. There should be zero.

**Step 2: Delete the files**

```bash
rm data/data-social/src/main/java/io/strategiz/social/data/repository/FirestoreRepository.java
rm data/data-social/src/main/java/io/strategiz/social/data/repository/FirestoreSubcollectionRepository.java
```

**Step 3: Full build + test**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, ALL TESTS PASS

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: remove local Firestore base repository classes (replaced by cidadel data-framework-base)"
```

---

## Task 12: Update CLAUDE.md documentation

**Files:**
- Modify: `CLAUDE.md`

Update the following sections:
- **Shared Libraries**: Add `data-framework-base` to the list with description: `data-framework-base — BaseEntity, BaseRepository, SubcollectionRepository, Firestore auditing/transactions`
- **Module Dependency Rules**: Note that data-* modules now depend on `cidadel data-framework-base`
- **Key Patterns**: Update to mention entities extend `BaseEntity`, repos extend `BaseRepository`/`SubcollectionRepository`
- Remove any references to local `FirestoreRepository` / `FirestoreSubcollectionRepository`

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for data-framework-base migration"
```

---

## Summary

| Task | Scope | Files |
|------|-------|-------|
| 1 | Dependency + Spring config | 4 files |
| 2 | Flat entities → BaseEntity | 12 files |
| 3 | Subcollection entities → BaseEntity | 6 files |
| 4 | Browser entities → BaseEntity | 3 files |
| 5 | Flat repos → BaseRepository | 15 files |
| 6 | Subcollection repos → SubcollectionRepository | 6 files |
| 7 | Flat-repo callers (business-agent) | 8 files |
| 8 | Subcollection-repo callers (business-agent) | 6 files |
| 9 | Remaining callers (business-social, browser, service) | 11 files |
| 10 | Test updates | ~12 files |
| 11 | Delete old base classes | 2 files deleted |
| 12 | Documentation | 1 file |

**Total: ~85 files touched across 12 tasks**
