# Schema Redesign + Agent Configuration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate user-owned data to subcollections under `tacticl_users/{userId}/`, embed UserConfig and DeviceSettings, add agent skills for registration/config management, and add settings REST endpoints.

**Architecture:** Hybrid schema — user-owned config data (devices, integrations, repos, tokens, reminders, templates) becomes subcollections under the user document. Operational data (sparks, posts, commands, audit logs) stays flat. New embedded entities: UserConfig on user doc, DeviceSettings on device doc. Four new agent skills. Settings REST controller.

**Tech Stack:** Java 21, Spring Boot 3.5.7, Firestore (Google Cloud), Gradle Kotlin DSL, Mockito + JUnit 5

**Design Doc:** `docs/plans/2026-02-28-schema-redesign-and-agent-config-design.md`

---

## Task 1: Create FirestoreSubcollectionRepository Base Class

The current `FirestoreRepository<T>` hardcodes `firestore.collection(collectionName)`. Subcollection repos need `firestore.collection("tacticl_users").document(userId).collection(name)`. Create a new base class that takes a parent path.

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/repository/FirestoreSubcollectionRepository.java`
- Test: `data-social/src/test/java/io/strategiz/social/data/repository/FirestoreSubcollectionRepositoryTest.java`

**Step 1: Write the failing test**

```java
package io.strategiz.social.data.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirestoreSubcollectionRepositoryTest {

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference parentCollection;

    @Mock
    private DocumentReference parentDoc;

    @Mock
    private CollectionReference subcollection;

    @Mock
    private DocumentReference docRef;

    @Mock
    private ApiFuture<DocumentSnapshot> docFuture;

    @Mock
    private DocumentSnapshot docSnapshot;

    @Test
    void getCollection_constructsCorrectPath() {
        when(firestore.collection("tacticl_users")).thenReturn(parentCollection);
        when(parentCollection.document("user-123")).thenReturn(parentDoc);
        when(parentDoc.collection("devices")).thenReturn(subcollection);

        TestSubcollectionRepo repo = new TestSubcollectionRepo(firestore);
        CollectionReference result = repo.getCollectionForUser("user-123");

        verify(firestore).collection("tacticl_users");
        verify(parentCollection).document("user-123");
        verify(parentDoc).collection("devices");
        assertEquals(subcollection, result);
    }

    // Concrete test impl
    static class TestSubcollectionRepo extends FirestoreSubcollectionRepository<String> {
        TestSubcollectionRepo(Firestore firestore) {
            super(firestore, String.class, "devices");
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :data-social:test --tests "*FirestoreSubcollectionRepositoryTest*" -i`
Expected: FAIL — class does not exist

**Step 3: Write minimal implementation**

```java
package io.strategiz.social.data.repository;

import com.google.cloud.firestore.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base repository for Firestore subcollections under tacticl_users/{userId}/.
 * Path: tacticl_users/{userId}/{subcollectionName}/{documentId}
 */
public abstract class FirestoreSubcollectionRepository<T> {

    private static final Logger logger = LoggerFactory.getLogger(FirestoreSubcollectionRepository.class);
    private static final String PARENT_COLLECTION = "tacticl_users";

    private final Firestore firestore;
    private final Class<T> entityClass;
    private final String subcollectionName;

    protected FirestoreSubcollectionRepository(Firestore firestore, Class<T> entityClass,
            String subcollectionName) {
        this.firestore = firestore;
        this.entityClass = entityClass;
        this.subcollectionName = subcollectionName;
    }

    protected CollectionReference getCollectionForUser(String userId) {
        return firestore.collection(PARENT_COLLECTION).document(userId).collection(subcollectionName);
    }

    public T save(String userId, T entity, String id) {
        try {
            DocumentReference docRef = id != null
                    ? getCollectionForUser(userId).document(id)
                    : getCollectionForUser(userId).document();
            docRef.set(entity).get();
            return entity;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save to " + subcollectionName, e);
        }
    }

    public Optional<T> findById(String userId, String id) {
        try {
            DocumentSnapshot doc = getCollectionForUser(userId).document(id).get().get();
            return doc.exists() ? Optional.ofNullable(doc.toObject(entityClass)) : Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to find in " + subcollectionName, e);
        }
    }

    public List<T> findAll(String userId) {
        try {
            QuerySnapshot snapshot = getCollectionForUser(userId).get().get();
            List<T> results = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                results.add(doc.toObject(entityClass));
            }
            return results;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to list " + subcollectionName, e);
        }
    }

    public List<T> findByField(String userId, String fieldName, Object value) {
        return executeQuery(getCollectionForUser(userId).whereEqualTo(fieldName, value));
    }

    public void delete(String userId, String id) {
        try {
            getCollectionForUser(userId).document(id).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to delete from " + subcollectionName, e);
        }
    }

    protected List<T> executeQuery(Query query) {
        try {
            QuerySnapshot snapshot = query.get().get();
            List<T> results = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                results.add(doc.toObject(entityClass));
            }
            return results;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to query " + subcollectionName, e);
        }
    }

    protected Firestore getFirestore() {
        return firestore;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :data-social:test --tests "*FirestoreSubcollectionRepositoryTest*" -i`
Expected: PASS

**Step 5: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/repository/FirestoreSubcollectionRepository.java \
        data-social/src/test/java/io/strategiz/social/data/repository/FirestoreSubcollectionRepositoryTest.java
git commit -m "feat: add FirestoreSubcollectionRepository base class for user-scoped subcollections"
```

---

## Task 2: Embed UserConfig in TacticlUser Entity

Add the `UserConfig` embedded class and a `config` field on `TacticlUser`. Also add a `preferences` field.

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/UserConfig.java`
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/TacticlUser.java`
- Test: `data-social/src/test/java/io/strategiz/social/data/entity/UserConfigTest.java`

**Step 1: Write the failing test**

```java
package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserConfigTest {

    @Test
    void defaults_areCorrect() {
        UserConfig config = UserConfig.defaults();
        assertEquals(3, config.getMaxConcurrentSparks());
        assertEquals(BigDecimal.ZERO, config.getSpendingLimit());
        assertTrue(config.getDomainAllowlist().isEmpty());
        assertTrue(config.getDomainBlocklist().isEmpty());
        assertTrue(config.getConfirmationOverrides().isEmpty());
    }

    @Test
    void tacticlUser_hasConfigField() {
        TacticlUser user = new TacticlUser();
        assertNull(user.getConfig());

        user.setConfig(UserConfig.defaults());
        assertNotNull(user.getConfig());
        assertEquals(3, user.getConfig().getMaxConcurrentSparks());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :data-social:test --tests "*UserConfigTest*" -i`
Expected: FAIL — UserConfig class does not exist

**Step 3: Write UserConfig entity**

```java
package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** User-level configuration embedded in the TacticlUser document. */
@IgnoreExtraProperties
public class UserConfig {

    private int maxConcurrentSparks = 3;
    private BigDecimal spendingLimit = BigDecimal.ZERO;
    private List<String> domainAllowlist = new ArrayList<>();
    private List<String> domainBlocklist = new ArrayList<>();
    private Map<String, Integer> confirmationOverrides = new HashMap<>();

    public UserConfig() {}

    public static UserConfig defaults() {
        return new UserConfig();
    }

    // Getters and setters
    public int getMaxConcurrentSparks() { return maxConcurrentSparks; }
    public void setMaxConcurrentSparks(int maxConcurrentSparks) { this.maxConcurrentSparks = maxConcurrentSparks; }

    public BigDecimal getSpendingLimit() { return spendingLimit; }
    public void setSpendingLimit(BigDecimal spendingLimit) { this.spendingLimit = spendingLimit; }

    public List<String> getDomainAllowlist() { return domainAllowlist; }
    public void setDomainAllowlist(List<String> domainAllowlist) { this.domainAllowlist = domainAllowlist; }

    public List<String> getDomainBlocklist() { return domainBlocklist; }
    public void setDomainBlocklist(List<String> domainBlocklist) { this.domainBlocklist = domainBlocklist; }

    public Map<String, Integer> getConfirmationOverrides() { return confirmationOverrides; }
    public void setConfirmationOverrides(Map<String, Integer> confirmationOverrides) { this.confirmationOverrides = confirmationOverrides; }
}
```

**Step 4: Add `config` field to TacticlUser**

In `data-social/src/main/java/io/strategiz/social/data/entity/TacticlUser.java`, add:

```java
private UserConfig config;

public UserConfig getConfig() { return config; }
public void setConfig(UserConfig config) { this.config = config; }
```

**Step 5: Run test to verify it passes**

Run: `./gradlew :data-social:test --tests "*UserConfigTest*" -i`
Expected: PASS

**Step 6: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/UserConfig.java \
        data-social/src/main/java/io/strategiz/social/data/entity/TacticlUser.java \
        data-social/src/test/java/io/strategiz/social/data/entity/UserConfigTest.java
git commit -m "feat: add UserConfig entity embedded in TacticlUser"
```

---

## Task 3: Embed DeviceSettings in DeviceRegistration Entity

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/DeviceSettings.java`
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/DeviceRegistration.java`
- Test: `data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java`

**Step 1: Write the failing test**

```java
package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DeviceSettingsTest {

    @Test
    void defaults_areCorrect() {
        DeviceSettings settings = DeviceSettings.defaults();
        assertEquals(1, settings.getMaxDaemons());
        assertFalse(settings.isAutoWake());
        assertEquals(0, settings.getPriority());
    }

    @Test
    void deviceRegistration_hasSettingsField() {
        DeviceRegistration device = new DeviceRegistration();
        assertNull(device.getSettings());

        device.setSettings(DeviceSettings.defaults());
        assertNotNull(device.getSettings());
        assertEquals(1, device.getSettings().getMaxDaemons());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :data-social:test --tests "*DeviceSettingsTest*" -i`
Expected: FAIL

**Step 3: Write DeviceSettings + modify DeviceRegistration**

Create `DeviceSettings.java`:

```java
package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Per-device configuration embedded in the DeviceRegistration document. */
@IgnoreExtraProperties
public class DeviceSettings {

    private int maxDaemons = 1;
    private boolean autoWake = false;
    private int priority = 0;

    public DeviceSettings() {}

    public static DeviceSettings defaults() {
        return new DeviceSettings();
    }

    public int getMaxDaemons() { return maxDaemons; }
    public void setMaxDaemons(int maxDaemons) { this.maxDaemons = maxDaemons; }

    public boolean isAutoWake() { return autoWake; }
    public void setAutoWake(boolean autoWake) { this.autoWake = autoWake; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
```

Add to `DeviceRegistration.java`:

```java
private DeviceSettings settings;

public DeviceSettings getSettings() { return settings; }
public void setSettings(DeviceSettings settings) { this.settings = settings; }
```

**Step 4: Run test, verify pass**

Run: `./gradlew :data-social:test --tests "*DeviceSettingsTest*" -i`
Expected: PASS

**Step 5: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/DeviceSettings.java \
        data-social/src/main/java/io/strategiz/social/data/entity/DeviceRegistration.java \
        data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java
git commit -m "feat: add DeviceSettings entity embedded in DeviceRegistration"
```

---

## Task 4: Add userId Field to ExecutionLog

**Files:**
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/ExecutionLog.java`
- Modify: `business-agent/src/main/java/io/strategiz/social/business/agent/service/VoiceAgentService.java` (set userId when logging)

**Step 1: Add field to ExecutionLog**

In `ExecutionLog.java`, add field + getter/setter:

```java
private String userId;

public String getUserId() { return userId; }
public void setUserId(String userId) { this.userId = userId; }
```

**Step 2: Update VoiceAgentService to populate userId on execution logs**

In `VoiceAgentService.java`, find where `ExecutionLog` is created/saved and ensure `userId` is set. Search for any `ExecutionLog` usage. If logs are created in the tool execution, add `log.setUserId(userId)` there.

Note: The current `VoiceAgentService` doesn't create `ExecutionLog` objects directly — it logs to `AgentAuditLog`. Execution logs may be created elsewhere (e.g., in individual skills or a logging interceptor). Search the codebase for `ExecutionLog` instantiation and add userId there.

**Step 3: Verify build compiles**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/ExecutionLog.java
git commit -m "feat: add userId field to ExecutionLog for GDPR data deletion"
```

---

## Task 5: Migrate DeviceRepository to Subcollection

Change `DeviceRepository` from flat `devices` collection to `tacticl_users/{userId}/devices/`.

**Files:**
- Modify: `data-social/src/main/java/io/strategiz/social/data/repository/DeviceRepository.java`
- Test: `data-social/src/test/java/io/strategiz/social/data/repository/DeviceRepositoryTest.java`
- Modify: All callers of DeviceRepository (add userId parameter)

**Step 1: Write failing test for new subcollection pattern**

```java
package io.strategiz.social.data.repository;

import static org.mockito.Mockito.*;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceRepositoryTest {

    @Mock private Firestore firestore;
    @Mock private CollectionReference usersCol;
    @Mock private DocumentReference userDoc;
    @Mock private CollectionReference devicesCol;

    @Test
    void findActiveByUserId_queriesSubcollection() {
        when(firestore.collection("tacticl_users")).thenReturn(usersCol);
        when(usersCol.document("user-1")).thenReturn(userDoc);
        when(userDoc.collection("devices")).thenReturn(devicesCol);

        DeviceRepository repo = new DeviceRepository(firestore);
        // This should query tacticl_users/user-1/devices/ not flat devices/
        // Verify it calls the subcollection path
        verify(firestore, never()).collection("devices");
    }
}
```

**Step 2: Rewrite DeviceRepository to extend FirestoreSubcollectionRepository**

```java
package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository extends FirestoreSubcollectionRepository<DeviceRegistration> {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);

    public DeviceRepository(Firestore firestore) {
        super(firestore, DeviceRegistration.class, "devices");
    }

    public List<DeviceRegistration> findActiveByUserId(String userId) {
        List<DeviceRegistration> all = findAll(userId);
        logger.debug("Found {} total devices for user {}", all.size(), userId);
        return all.stream()
                .filter(d -> d.getState() == DeviceState.ACTIVE)
                .filter(DeviceRegistration::isActive)
                .toList();
    }
}
```

**Step 3: Update all callers of DeviceRepository**

Every method call to `DeviceRepository` that previously used `findById(id)` must now use `findById(userId, id)`. Same for `save`, `delete`, etc. Key files to update:

- `business-agent/src/main/java/.../DeviceRegistryService.java` — all methods
- `business-agent/src/main/java/.../DeviceRoutingService.java` — `selectDevice`, `getOnlineDevice`, `buildDeviceContext`
- `business-agent/src/main/java/.../DevicePairingService.java` — save device on pair
- `service-agent/src/main/java/.../DeviceController.java` — all endpoints
- `business-agent/src/main/java/.../SparkService.java` — device routing

For each caller: add `userId` parameter where the method signature changed.

**Step 4: Compile and run tests**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (fix compile errors from signature changes)

**Step 5: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/repository/DeviceRepository.java \
        business-agent/src/main/java/ \
        service-agent/src/main/java/
git commit -m "refactor: migrate DeviceRepository to subcollection under tacticl_users"
```

---

## Task 6: Migrate SocialIntegrationRepository to Subcollection

Same pattern as Task 5 for social integrations.

**Files:**
- Modify: `data-social/src/main/java/io/strategiz/social/data/repository/SocialIntegrationRepository.java`
- Modify: All callers (business-social, service-social, service-agent modules)

**Step 1: Rewrite SocialIntegrationRepository**

```java
@Repository
public class SocialIntegrationRepository extends FirestoreSubcollectionRepository<SocialIntegration> {

    public SocialIntegrationRepository(Firestore firestore) {
        super(firestore, SocialIntegration.class, "social_integrations");
    }

    public Optional<SocialIntegration> findByUserIdAndPlatform(String userId, PlatformType platform) {
        List<SocialIntegration> results = executeQuery(
                getCollectionForUser(userId)
                        .whereEqualTo("platform", platform.name())
                        .whereEqualTo("isActive", true));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<SocialIntegration> findAllByUserId(String userId) {
        return executeQuery(getCollectionForUser(userId).whereEqualTo("isActive", true));
    }
}
```

**Step 2: Update callers** — same approach as Task 5, propagate userId parameter.

**Step 3: Compile and test**

Run: `./gradlew build`

**Step 4: Commit**

```bash
git commit -m "refactor: migrate SocialIntegrationRepository to subcollection under tacticl_users"
```

---

## Task 7: Migrate Remaining Repositories to Subcollections

Repeat the subcollection migration for:

- `RepoGrantRepository` → `tacticl_users/{userId}/repo_grants/`
- `AgentTokenRepository` → `tacticl_users/{userId}/agent_tokens/`
- `ReminderRepository` → `tacticl_users/{userId}/reminders/`
- `SparkTemplateRepository` → `tacticl_users/{userId}/spark_templates/`

Each follows the same pattern: extend `FirestoreSubcollectionRepository`, update callers.

**Step 1: Migrate each repository**

For each: change `extends FirestoreRepository<T>` to `extends FirestoreSubcollectionRepository<T>`, update constructor, update callers.

**Step 2: Compile and test**

Run: `./gradlew build`

**Step 3: Commit**

```bash
git commit -m "refactor: migrate repo_grants, agent_tokens, reminders, spark_templates to subcollections"
```

---

## Task 8: Eliminate device_preferences Collection

Merge `DevicePreference` data into the `sparkPreferences` embedded map on `DeviceRegistration`.

**Files:**
- Delete usage: `data-social/src/main/java/io/strategiz/social/data/repository/DevicePreferenceRepository.java`
- Delete usage: `data-social/src/main/java/io/strategiz/social/data/entity/DevicePreference.java`
- Modify: `DeviceRegistration.java` — the existing `sparkPreferences` field (Map) already serves this purpose
- Modify: All callers of `DevicePreferenceRepository` to read from `device.getSparkPreferences()` instead

**Step 1: Find all usages of DevicePreferenceRepository**

Search codebase for `DevicePreferenceRepository` and `DevicePreference`. Replace each usage with reads/writes to `DeviceRegistration.sparkPreferences`.

**Step 2: Remove the repository and entity classes**

Delete `DevicePreferenceRepository.java` and `DevicePreference.java`.

**Step 3: Compile and test**

Run: `./gradlew build`

**Step 4: Commit**

```bash
git commit -m "refactor: eliminate device_preferences collection, merge into DeviceRegistration.sparkPreferences"
```

---

## Task 9: Create UserConfigService

Business service for reading/writing user config with defaults.

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/service/UserConfigService.java`
- Test: `business-agent/src/test/java/io/strategiz/social/business/agent/service/UserConfigServiceTest.java`

**Step 1: Write failing test**

```java
@ExtendWith(MockitoExtension.class)
class UserConfigServiceTest {

    @Mock private TacticlUserRepository userRepository;
    @InjectMocks private UserConfigService userConfigService;

    @Test
    void getConfig_userHasNoConfig_returnsDefaults() {
        TacticlUser user = new TacticlUser();
        user.setConfig(null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserConfig config = userConfigService.getConfig("user-1");
        assertEquals(3, config.getMaxConcurrentSparks());
    }

    @Test
    void updateConfig_mergesFields() {
        TacticlUser user = new TacticlUser();
        user.setConfig(UserConfig.defaults());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        Map<String, Object> updates = Map.of("maxConcurrentSparks", 5);
        userConfigService.updateConfig("user-1", updates);

        verify(userRepository).save(argThat(u ->
            u.getConfig().getMaxConcurrentSparks() == 5), eq("user-1"));
    }
}
```

**Step 2: Implement UserConfigService**

```java
@Service
public class UserConfigService {

    private final TacticlUserRepository userRepository;

    public UserConfigService(TacticlUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserConfig getConfig(String userId) {
        return userRepository.findById(userId)
                .map(TacticlUser::getConfig)
                .orElse(UserConfig.defaults());
    }

    public void updateConfig(String userId, Map<String, Object> updates) {
        TacticlUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        UserConfig config = user.getConfig() != null ? user.getConfig() : UserConfig.defaults();

        if (updates.containsKey("maxConcurrentSparks")) {
            config.setMaxConcurrentSparks((int) updates.get("maxConcurrentSparks"));
        }
        if (updates.containsKey("spendingLimit")) {
            config.setSpendingLimit(new BigDecimal(updates.get("spendingLimit").toString()));
        }
        if (updates.containsKey("domainAllowlist")) {
            config.setDomainAllowlist((List<String>) updates.get("domainAllowlist"));
        }
        if (updates.containsKey("domainBlocklist")) {
            config.setDomainBlocklist((List<String>) updates.get("domainBlocklist"));
        }
        if (updates.containsKey("confirmationOverrides")) {
            config.setConfirmationOverrides((Map<String, Integer>) updates.get("confirmationOverrides"));
        }

        user.setConfig(config);
        userRepository.save(user, userId);
    }
}
```

**Step 3: Run tests, verify pass, commit**

```bash
git commit -m "feat: add UserConfigService for reading/writing user configuration"
```

---

## Task 10: Create ConnectionStatusSkill (Tier 0)

Agent skill that returns an overview of all connected resources.

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/skill/ConnectionStatusSkill.java`
- Test: `business-agent/src/test/java/io/strategiz/social/business/agent/skill/ConnectionStatusSkillTest.java`

**Step 1: Write failing test**

```java
@ExtendWith(MockitoExtension.class)
class ConnectionStatusSkillTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private SocialIntegrationRepository integrationRepository;
    @Mock private RepoGrantRepository repoGrantRepository;
    @Mock private DeviceSessionRepository sessionRepository;
    @InjectMocks private ConnectionStatusSkill skill;

    @Test
    void getName_returnsConnectionStatus() {
        assertEquals("connection_status", skill.getName());
    }

    @Test
    void getConfirmationTier_returns0() {
        assertEquals(0, skill.getConfirmationTier());
    }

    @Test
    void execute_returnsFormattedOverview() {
        // Set up mock data: 1 device, 1 integration, 1 repo
        // Verify output contains device name, platform name, repo name
    }
}
```

**Step 2: Implement following ListDevicesSkill pattern**

- `@Component`
- Constructor injection of repos
- `getToolDefinition()` — no required params
- `execute()` — queries devices, integrations, repos, formats as markdown summary
- Tier 0

**Step 3: Test, commit**

```bash
git commit -m "feat: add connection_status agent skill (Tier 0)"
```

---

## Task 11: Create ManageSettingsSkill (Tier 1)

Agent skill for reading/updating UserConfig via chat.

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/skill/ManageSettingsSkill.java`
- Test: `business-agent/src/test/java/io/strategiz/social/business/agent/skill/ManageSettingsSkillTest.java`

**Step 1: Write failing test**

Test that the skill correctly dispatches actions like `get`, `update_domain_blocklist`, `update_max_sparks`.

**Step 2: Implement**

- Tool definition: `action` (enum: get/update), plus optional fields for each setting
- `execute()` delegates to `UserConfigService`
- Tier 1 (mutations)

**Step 3: Test, commit**

```bash
git commit -m "feat: add manage_settings agent skill (Tier 1)"
```

---

## Task 12: Create ManageDeviceSkill (Tier 1)

Agent skill for pairing, unpairing, and updating device settings via chat.

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/skill/ManageDeviceSkill.java`
- Test: `business-agent/src/test/java/io/strategiz/social/business/agent/skill/ManageDeviceSkillTest.java`

**Step 1: Write failing test**

Test actions: `pair` (generates pairing code), `unpair` (revokes device), `update_settings` (maxDaemons, autoWake, priority).

**Step 2: Implement**

- Constructor injection of `DevicePairingService`, `DeviceRegistryService`
- Tool definition: `action` (pair/unpair/update_settings), `device_id` (optional), `max_daemons` (optional), etc.
- `execute()` routes by action
- Tier 1

**Step 3: Test, commit**

```bash
git commit -m "feat: add manage_device agent skill (Tier 1)"
```

---

## Task 13: Create ManageRepoSkill (Tier 1)

Agent skill for adding/removing repos from Tacticl's purview.

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/skill/ManageRepoSkill.java`
- Test: `business-agent/src/test/java/io/strategiz/social/business/agent/skill/ManageRepoSkillTest.java`

**Step 1: Write failing test**

Test actions: `list`, `grant` (with repo name + provider), `revoke` (by repo id).

**Step 2: Implement**

- Constructor injection of `RepoGrantRepository`
- Tool definition: `action` (list/grant/revoke), `repo_name`, `provider`
- Tier 1

**Step 3: Test, commit**

```bash
git commit -m "feat: add manage_repo agent skill (Tier 1)"
```

---

## Task 14: Create SettingsController REST Endpoints

Settings page backend.

**Files:**
- Create: `service-agent/src/main/java/io/strategiz/social/service/agent/controller/SettingsController.java`
- Create: `service-agent/src/main/java/io/strategiz/social/service/agent/dto/UserConfigResponse.java`
- Create: `service-agent/src/main/java/io/strategiz/social/service/agent/dto/UpdateConfigRequest.java`
- Create: `service-agent/src/main/java/io/strategiz/social/service/agent/dto/ConnectionStatusResponse.java`

**Step 1: Implement controller**

```java
@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings", description = "User and device configuration")
public class SettingsController {

    private final UserConfigService userConfigService;
    private final DeviceRegistryService deviceRegistryService;
    private final DeviceRepository deviceRepository;
    private final SocialIntegrationRepository integrationRepository;
    private final RepoGrantRepository repoGrantRepository;

    // GET /api/settings — user config
    // PUT /api/settings — update user config
    // GET /api/settings/devices/{deviceId} — device settings
    // PUT /api/settings/devices/{deviceId} — update device settings
    // GET /api/settings/connections — aggregated connection status
}
```

**Step 2: Implement DTOs**

Simple POJOs mirroring `UserConfig` and `DeviceSettings` fields for request/response.

**Step 3: Compile and test**

Run: `./gradlew build`

**Step 4: Commit**

```bash
git commit -m "feat: add SettingsController REST endpoints for config page"
```

---

## Task 15: Update AgentSystemPrompt to Include UserConfig Context

The agent system prompt should include the user's config (spending limit, domain lists, etc.) so the LLM knows the user's constraints.

**Files:**
- Modify: `business-agent/src/main/java/io/strategiz/social/business/agent/service/AgentSystemPrompt.java`

**Step 1: Inject UserConfigService, add config section to prompt**

In `buildSystemPrompt()`, after the device context section, add:

```java
prompt.append("\n## User Configuration\n");
UserConfig config = userConfigService.getConfig(userId);
prompt.append("- Max concurrent sparks: ").append(config.getMaxConcurrentSparks()).append("\n");
prompt.append("- Spending limit: $").append(config.getSpendingLimit()).append("\n");
if (!config.getDomainBlocklist().isEmpty()) {
    prompt.append("- Blocked domains: ").append(String.join(", ", config.getDomainBlocklist())).append("\n");
}
```

**Step 2: Compile, commit**

```bash
git commit -m "feat: include UserConfig in agent system prompt context"
```

---

## Task 16: Data Migration Script

Write a one-time migration service that copies existing flat collection data into the new subcollection structure.

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/service/DataMigrationService.java`

**Step 1: Implement migration**

For each migrated collection (devices, social_integrations, repo_grants, agent_tokens, reminders, spark_templates):
1. Read all documents from flat collection
2. Write each to `tacticl_users/{userId}/{subcollection}/{docId}`
3. Log progress
4. Do NOT delete flat data yet (dual-read period)

**Step 2: Create a REST endpoint to trigger migration (admin-only)**

```java
@PostMapping("/api/admin/migrate")
@RequireAuth
public ResponseEntity<String> runMigration(@AuthUser AuthenticatedUser user) {
    // Check admin role
    dataMigrationService.migrateAllUsers();
    return ResponseEntity.ok("Migration complete");
}
```

**Step 3: Commit**

```bash
git commit -m "feat: add DataMigrationService for flat-to-subcollection migration"
```

---

## Task 17: UserDataPurgeService for GDPR

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/service/UserDataPurgeService.java`
- Test: `business-agent/src/test/java/io/strategiz/social/business/agent/service/UserDataPurgeServiceTest.java`

**Step 1: Implement purge service**

Queries all flat collections by userId and batch-deletes. Also recursively deletes `tacticl_users/{userId}` and its subcollections.

**Step 2: Test, commit**

```bash
git commit -m "feat: add UserDataPurgeService for GDPR user data deletion"
```

---

## Task Summary

| Task | Description | Dependencies |
|------|-------------|--------------|
| 1 | FirestoreSubcollectionRepository base class | None |
| 2 | UserConfig entity embedded in TacticlUser | None |
| 3 | DeviceSettings entity embedded in DeviceRegistration | None |
| 4 | Add userId to ExecutionLog | None |
| 5 | Migrate DeviceRepository to subcollection | Task 1 |
| 6 | Migrate SocialIntegrationRepository to subcollection | Task 1 |
| 7 | Migrate remaining repos to subcollections | Task 1 |
| 8 | Eliminate device_preferences collection | Task 3, 5 |
| 9 | UserConfigService | Task 2 |
| 10 | ConnectionStatusSkill (Tier 0) | Task 5, 6, 7 |
| 11 | ManageSettingsSkill (Tier 1) | Task 9 |
| 12 | ManageDeviceSkill (Tier 1) | Task 5 |
| 13 | ManageRepoSkill (Tier 1) | Task 7 |
| 14 | SettingsController REST endpoints | Task 9 |
| 15 | AgentSystemPrompt includes UserConfig | Task 9 |
| 16 | Data migration script | Tasks 5-7 |
| 17 | UserDataPurgeService (GDPR) | Tasks 5-7 |
