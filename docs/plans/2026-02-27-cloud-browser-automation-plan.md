# Cloud Browser Automation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Playwright-based headless browser automation to Tacticl's cloud execution path, enabling the agent to navigate, click, type, fill forms, download/upload files, and interact with websites — controlled by user execution preferences with tiered security.

**Architecture:** Two new Gradle modules (`data-browser`, `business-browser`) following existing patterns. Playwright Java SDK runs in-process. BrowserSessionService manages ephemeral and persistent Chromium contexts. 13 new AgentSkill implementations auto-register in ToolRegistry. Execution routing in AgentController checks user's `executionPreference` setting. Existing checkpoint system extended for browser-specific triggers (login, CAPTCHA, purchases).

**Tech Stack:** Java 21, Spring Boot 3.5.7, Playwright Java 1.50.0, Google Cloud Storage (profile/file persistence), Firestore (session/action tracking), noVNC/WebSocket (live view — future phase)

**Design Doc:** `docs/plans/2026-02-27-cloud-browser-automation-design.md`

---

## Phase 1: Foundation (data-browser module)

### Task 1: Create data-browser module scaffold

**Files:**
- Create: `data-browser/build.gradle.kts`
- Modify: `settings.gradle.kts` (add `"data-browser"` to includes)

**Step 1: Add module to settings.gradle.kts**

In `settings.gradle.kts`, add `"data-browser"` to the `include()` block, after `"data-social"`:

```kotlin
include(
    "application",
    "service-social",
    "service-agent",
    "service-spark",
    "service-checkpoint",
    "service-repo",
    "service-token",
    "business-social",
    "business-agent",
    "data-social",
    "data-browser",       // NEW
    "client-twitter",
    // ... rest unchanged
)
```

**Step 2: Create build.gradle.kts**

Create `data-browser/build.gradle.kts` matching `data-social` pattern exactly:

```kotlin
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Google Cloud Firestore
    implementation(libs.google.cloud.firestore)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
}
```

**Step 3: Create package directories**

```bash
mkdir -p data-browser/src/main/java/io/tacticl/browser/data/entity
mkdir -p data-browser/src/main/java/io/tacticl/browser/data/repository
mkdir -p data-browser/src/test/java/io/tacticl/browser/data
```

**Step 4: Verify build compiles**

Run: `./gradlew :data-browser:build`
Expected: BUILD SUCCESSFUL (empty module compiles)

**Step 5: Commit**

```bash
git add data-browser/ settings.gradle.kts
git commit -m "feat: scaffold data-browser module"
```

---

### Task 2: Create browser session entities

**Files:**
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserSession.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserSessionType.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserSessionStatus.java`

**Step 1: Create BrowserSessionType enum**

```java
package io.tacticl.browser.data.entity;

/** Type of browser session. */
public enum BrowserSessionType {
    EPHEMERAL,
    PERSISTENT
}
```

**Step 2: Create BrowserSessionStatus enum**

```java
package io.tacticl.browser.data.entity;

/** Lifecycle status of a browser session. */
public enum BrowserSessionStatus {
    ACTIVE,
    IDLE,
    CLOSED
}
```

**Step 3: Create BrowserSession entity**

Follow `Spark.java` pattern exactly — POJO with `@IgnoreExtraProperties`, no-arg constructor with defaults, standard getters/setters:

```java
package io.tacticl.browser.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Represents a cloud browser session. Stored in browser_sessions Firestore collection. */
@IgnoreExtraProperties
public class BrowserSession {

    private String id;
    private String userId;
    private String sparkId;
    private BrowserSessionType type;
    private BrowserSessionStatus status;
    private Instant createdAt;
    private Instant lastActiveAt;
    private Instant closedAt;
    private String profilePath;
    private String currentUrl;
    private int pagesOpen;
    private long memoryUsageMb;
    private boolean liveViewEnabled;
    private long durationSeconds;
    private boolean isActive;

    public BrowserSession() {
        this.type = BrowserSessionType.EPHEMERAL;
        this.status = BrowserSessionStatus.ACTIVE;
        this.pagesOpen = 0;
        this.memoryUsageMb = 0;
        this.liveViewEnabled = false;
        this.durationSeconds = 0;
        this.isActive = true;
    }

    // Getters and setters for all fields (standard Java bean pattern)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSparkId() { return sparkId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }

    public BrowserSessionType getType() { return type; }
    public void setType(BrowserSessionType type) { this.type = type; }

    public BrowserSessionStatus getStatus() { return status; }
    public void setStatus(BrowserSessionStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public String getProfilePath() { return profilePath; }
    public void setProfilePath(String profilePath) { this.profilePath = profilePath; }

    public String getCurrentUrl() { return currentUrl; }
    public void setCurrentUrl(String currentUrl) { this.currentUrl = currentUrl; }

    public int getPagesOpen() { return pagesOpen; }
    public void setPagesOpen(int pagesOpen) { this.pagesOpen = pagesOpen; }

    public long getMemoryUsageMb() { return memoryUsageMb; }
    public void setMemoryUsageMb(long memoryUsageMb) { this.memoryUsageMb = memoryUsageMb; }

    public boolean isLiveViewEnabled() { return liveViewEnabled; }
    public void setLiveViewEnabled(boolean liveViewEnabled) { this.liveViewEnabled = liveViewEnabled; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
```

**Step 4: Verify build**

Run: `./gradlew :data-browser:build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add data-browser/src/
git commit -m "feat: add BrowserSession entity and enums"
```

---

### Task 3: Create BrowserActionLog and UserFile entities

**Files:**
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserActionLog.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/UserFile.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/UserFileType.java`

**Step 1: Create UserFileType enum**

```java
package io.tacticl.browser.data.entity;

/** Type of user file operation. */
public enum UserFileType {
    DOWNLOAD,
    UPLOAD
}
```

**Step 2: Create BrowserActionLog entity**

```java
package io.tacticl.browser.data.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Logs individual browser actions within a session. Stored in browser_action_logs collection. */
@IgnoreExtraProperties
public class BrowserActionLog {

    private String id;
    private String sessionId;
    private String sparkId;
    private String skillName;
    private Instant timestamp;
    private String url;
    private String elementRef;
    private Map<String, Object> inputData;
    private String result;
    private String screenshotUrl;
    private int tier;
    private Boolean userApproved;
    private long durationMs;

    public BrowserActionLog() {
        this.inputData = new HashMap<>();
        this.tier = 0;
        this.durationMs = 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSparkId() { return sparkId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }

    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getElementRef() { return elementRef; }
    public void setElementRef(String elementRef) { this.elementRef = elementRef; }

    public Map<String, Object> getInputData() { return inputData; }
    public void setInputData(Map<String, Object> inputData) { this.inputData = inputData; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }

    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }

    public Boolean getUserApproved() { return userApproved; }
    public void setUserApproved(Boolean userApproved) { this.userApproved = userApproved; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
```

**Step 3: Create UserFile entity**

```java
package io.tacticl.browser.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Tracks files downloaded/uploaded by browser sessions. Stored in user_files collection. */
@IgnoreExtraProperties
public class UserFile {

    private String id;
    private String userId;
    private String sparkId;
    private String sessionId;
    private UserFileType type;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String gcsPath;
    private String sourceUrl;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean isActive;

    public UserFile() {
        this.sizeBytes = 0;
        this.isActive = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSparkId() { return sparkId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public UserFileType getType() { return type; }
    public void setType(UserFileType type) { this.type = type; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getGcsPath() { return gcsPath; }
    public void setGcsPath(String gcsPath) { this.gcsPath = gcsPath; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
```

**Step 4: Verify build**

Run: `./gradlew :data-browser:build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add data-browser/src/
git commit -m "feat: add BrowserActionLog and UserFile entities"
```

---

### Task 4: Create data-browser repositories

**Files:**
- Create: `data-browser/src/main/java/io/tacticl/browser/data/repository/BrowserSessionRepository.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/repository/BrowserActionLogRepository.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/repository/UserFileRepository.java`

**Important:** These extend `FirestoreRepository` from `data-social`. The `data-browser` module needs access to `FirestoreRepository`. Two options: (a) add `implementation(project(":data-social"))` to data-browser, or (b) duplicate `FirestoreRepository` in data-browser. Option (a) is simpler — add `data-social` as dependency.

**Step 1: Add data-social dependency**

In `data-browser/build.gradle.kts`, add:

```kotlin
dependencies {
    // Internal - reuse FirestoreRepository base class
    implementation(project(":data-social"))
    // ... rest unchanged
}
```

**Step 2: Create BrowserSessionRepository**

```java
package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.tacticl.browser.data.entity.BrowserSession;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for browser_sessions Firestore collection. */
@Repository
public class BrowserSessionRepository extends FirestoreRepository<BrowserSession> {

    public BrowserSessionRepository(Firestore firestore) {
        super(firestore, BrowserSession.class, "browser_sessions");
    }

    public List<BrowserSession> findActiveByUserId(String userId) {
        return executeQuery(getCollection()
            .whereEqualTo("userId", userId)
            .whereEqualTo("isActive", true)
            .whereEqualTo("status", "ACTIVE"));
    }

    public List<BrowserSession> findBySparkId(String sparkId) {
        return executeQuery(getCollection()
            .whereEqualTo("sparkId", sparkId)
            .whereEqualTo("isActive", true));
    }
}
```

**Step 3: Create BrowserActionLogRepository**

```java
package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import io.tacticl.browser.data.entity.BrowserActionLog;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for browser_action_logs Firestore collection. */
@Repository
public class BrowserActionLogRepository extends FirestoreRepository<BrowserActionLog> {

    public BrowserActionLogRepository(Firestore firestore) {
        super(firestore, BrowserActionLog.class, "browser_action_logs");
    }

    public List<BrowserActionLog> findBySessionId(String sessionId) {
        return executeQuery(getCollection()
            .whereEqualTo("sessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING));
    }
}
```

**Step 4: Create UserFileRepository**

```java
package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.tacticl.browser.data.entity.UserFile;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for user_files Firestore collection. */
@Repository
public class UserFileRepository extends FirestoreRepository<UserFile> {

    public UserFileRepository(Firestore firestore) {
        super(firestore, UserFile.class, "user_files");
    }

    public List<UserFile> findByUserIdAndActive(String userId) {
        return executeQuery(getCollection()
            .whereEqualTo("userId", userId)
            .whereEqualTo("isActive", true));
    }

    public List<UserFile> findBySessionId(String sessionId) {
        return executeQuery(getCollection()
            .whereEqualTo("sessionId", sessionId)
            .whereEqualTo("isActive", true));
    }
}
```

**Step 5: Verify build**

Run: `./gradlew :data-browser:build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add data-browser/
git commit -m "feat: add data-browser repositories"
```

---

### Task 5: Add ExecutionPreference enum and BrowserSettings to data model

**Files:**
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/ExecutionPreference.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserSettings.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserQuota.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/CheckpointType.java`

**Step 1: Create ExecutionPreference enum**

```java
package io.tacticl.browser.data.entity;

/** User preference for how commands should be routed. */
public enum ExecutionPreference {
    DEVICE_FIRST,
    CLOUD_FIRST,
    CLOUD_ONLY
}
```

**Step 2: Create BrowserSettings**

```java
package io.tacticl.browser.data.entity;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** User-configurable browser automation settings. Embedded in TacticlUser preferences. */
@IgnoreExtraProperties
public class BrowserSettings {

    private List<String> domainAllowlist;
    private List<String> domainBlocklist;
    private List<String> autoBlockCategories;
    private boolean allowFileDownloads;
    private boolean allowFileUploads;
    private long maxFileSize;
    private long maxSpendPerAction;

    public BrowserSettings() {
        this.domainBlocklist = new ArrayList<>();
        this.autoBlockCategories = new ArrayList<>();
        this.allowFileDownloads = true;
        this.allowFileUploads = true;
        this.maxFileSize = 52428800; // 50MB
        this.maxSpendPerAction = 0;
    }

    public List<String> getDomainAllowlist() { return domainAllowlist; }
    public void setDomainAllowlist(List<String> domainAllowlist) { this.domainAllowlist = domainAllowlist; }

    public List<String> getDomainBlocklist() { return domainBlocklist; }
    public void setDomainBlocklist(List<String> domainBlocklist) { this.domainBlocklist = domainBlocklist; }

    public List<String> getAutoBlockCategories() { return autoBlockCategories; }
    public void setAutoBlockCategories(List<String> autoBlockCategories) { this.autoBlockCategories = autoBlockCategories; }

    public boolean isAllowFileDownloads() { return allowFileDownloads; }
    public void setAllowFileDownloads(boolean allowFileDownloads) { this.allowFileDownloads = allowFileDownloads; }

    public boolean isAllowFileUploads() { return allowFileUploads; }
    public void setAllowFileUploads(boolean allowFileUploads) { this.allowFileUploads = allowFileUploads; }

    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }

    public long getMaxSpendPerAction() { return maxSpendPerAction; }
    public void setMaxSpendPerAction(long maxSpendPerAction) { this.maxSpendPerAction = maxSpendPerAction; }
}
```

**Step 3: Create BrowserQuota**

```java
package io.tacticl.browser.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Tracks browser session usage quota per user. Embedded in TacticlUser preferences. */
@IgnoreExtraProperties
public class BrowserQuota {

    private String planTier;
    private long minutesUsed;
    private long minutesLimit;
    private Instant resetDate;

    public BrowserQuota() {
        this.planTier = "FREE";
        this.minutesUsed = 0;
        this.minutesLimit = 10;
    }

    public String getPlanTier() { return planTier; }
    public void setPlanTier(String planTier) { this.planTier = planTier; }

    public long getMinutesUsed() { return minutesUsed; }
    public void setMinutesUsed(long minutesUsed) { this.minutesUsed = minutesUsed; }

    public long getMinutesLimit() { return minutesLimit; }
    public void setMinutesLimit(long minutesLimit) { this.minutesLimit = minutesLimit; }

    public Instant getResetDate() { return resetDate; }
    public void setResetDate(Instant resetDate) { this.resetDate = resetDate; }
}
```

**Step 4: Create CheckpointType enum**

```java
package io.tacticl.browser.data.entity;

/** Browser-specific checkpoint types for the agent checkpoint system. */
public enum CheckpointType {
    LOGIN_REQUIRED,
    CAPTCHA_DETECTED,
    PURCHASE_CONFIRMATION,
    DOWNLOAD_APPROVAL,
    BROWSER_ERROR,
    AMBIGUOUS_STATE
}
```

**Step 5: Verify build**

Run: `./gradlew :data-browser:build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add data-browser/src/
git commit -m "feat: add ExecutionPreference, BrowserSettings, BrowserQuota, CheckpointType"
```

---

## Phase 2: Business Layer (business-browser module)

### Task 6: Create business-browser module scaffold with Playwright dependency

**Files:**
- Create: `business-browser/build.gradle.kts`
- Modify: `settings.gradle.kts` (add `"business-browser"`)
- Modify: `gradle/libs.versions.toml` (add playwright version + library)

**Step 1: Add playwright to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
playwright = "1.50.0"
google-cloud-storage = "2.45.0"
```

And to `[libraries]`:

```toml
playwright = { module = "com.microsoft.playwright:playwright", version.ref = "playwright" }
google-cloud-storage = { module = "com.google.cloud:google-cloud-storage", version.ref = "google-cloud-storage" }
```

**Step 2: Add module to settings.gradle.kts**

Add `"business-browser"` after `"business-agent"`:

```kotlin
include(
    // ...
    "business-agent",
    "business-browser",   // NEW
    "data-social",
    "data-browser",       // already added
    // ...
)
```

**Step 3: Create build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Internal modules
    implementation(project(":data-browser"))
    implementation(project(":data-social"))

    // Cidadel shared infrastructure
    implementation(libs.cidadel.service.framework.base)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.client.base)

    // Playwright browser automation
    implementation(libs.playwright)

    // Google Cloud Storage (profile + file persistence)
    implementation(libs.google.cloud.storage)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

**Step 4: Create package directories**

```bash
mkdir -p business-browser/src/main/java/io/tacticl/browser/service
mkdir -p business-browser/src/main/java/io/tacticl/browser/skill
mkdir -p business-browser/src/main/java/io/tacticl/browser/config
mkdir -p business-browser/src/test/java/io/tacticl/browser
```

**Step 5: Verify build**

Run: `./gradlew :business-browser:build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add business-browser/ settings.gradle.kts gradle/libs.versions.toml
git commit -m "feat: scaffold business-browser module with Playwright dependency"
```

---

### Task 7: Create BrowserConfig and BrowserSessionService

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/config/BrowserConfig.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/config/BrowserProperties.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/service/BrowserSessionService.java`

**Step 1: Create BrowserProperties**

```java
package io.tacticl.browser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for the browser automation module. */
@ConfigurationProperties(prefix = "tacticl.browser")
public class BrowserProperties {

    private boolean enabled = false;
    private int maxConcurrentContexts = 3;
    private int ephemeralTimeoutSeconds = 60;
    private int persistentIdleTimeoutSeconds = 300;
    private int pageLoadTimeoutSeconds = 30;
    private int maxPagesPerContext = 5;
    private String profileBucket = "tacticl-browser-profiles";
    private String filesBucket = "tacticl-user-files";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxConcurrentContexts() { return maxConcurrentContexts; }
    public void setMaxConcurrentContexts(int maxConcurrentContexts) { this.maxConcurrentContexts = maxConcurrentContexts; }

    public int getEphemeralTimeoutSeconds() { return ephemeralTimeoutSeconds; }
    public void setEphemeralTimeoutSeconds(int ephemeralTimeoutSeconds) { this.ephemeralTimeoutSeconds = ephemeralTimeoutSeconds; }

    public int getPersistentIdleTimeoutSeconds() { return persistentIdleTimeoutSeconds; }
    public void setPersistentIdleTimeoutSeconds(int persistentIdleTimeoutSeconds) { this.persistentIdleTimeoutSeconds = persistentIdleTimeoutSeconds; }

    public int getPageLoadTimeoutSeconds() { return pageLoadTimeoutSeconds; }
    public void setPageLoadTimeoutSeconds(int pageLoadTimeoutSeconds) { this.pageLoadTimeoutSeconds = pageLoadTimeoutSeconds; }

    public int getMaxPagesPerContext() { return maxPagesPerContext; }
    public void setMaxPagesPerContext(int maxPagesPerContext) { this.maxPagesPerContext = maxPagesPerContext; }

    public String getProfileBucket() { return profileBucket; }
    public void setProfileBucket(String profileBucket) { this.profileBucket = profileBucket; }

    public String getFilesBucket() { return filesBucket; }
    public void setFilesBucket(String filesBucket) { this.filesBucket = filesBucket; }
}
```

**Step 2: Create BrowserConfig**

```java
package io.tacticl.browser.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for Playwright browser automation. Only active when tacticl.browser.enabled=true. */
@Configuration
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
@EnableConfigurationProperties(BrowserProperties.class)
public class BrowserConfig {

    private static final Logger log = LoggerFactory.getLogger(BrowserConfig.class);

    private Playwright playwright;
    private Browser browser;

    @Bean
    public Playwright playwright() {
        log.info("[BROWSER] Initializing Playwright runtime");
        this.playwright = Playwright.create();
        return this.playwright;
    }

    @Bean
    public Browser browser(Playwright playwright) {
        log.info("[BROWSER] Launching shared Chromium instance");
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(true)
            .setArgs(List.of(
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--disable-extensions",
                "--disable-background-networking",
                "--no-first-run"
            )));
        return this.browser;
    }

    @PreDestroy
    public void cleanup() {
        log.info("[BROWSER] Shutting down browser and Playwright");
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
```

**Step 3: Create BrowserSessionService**

```java
package io.tacticl.browser.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import io.tacticl.browser.config.BrowserProperties;
import io.tacticl.browser.data.entity.BrowserSession;
import io.tacticl.browser.data.entity.BrowserSessionStatus;
import io.tacticl.browser.data.entity.BrowserSessionType;
import io.tacticl.browser.data.repository.BrowserSessionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Manages Playwright browser contexts — ephemeral and persistent sessions. */
@Service
public class BrowserSessionService {

    private static final Logger log = LoggerFactory.getLogger(BrowserSessionService.class);

    private final Browser browser;
    private final BrowserProperties properties;
    private final BrowserSessionRepository sessionRepository;
    private final Map<String, BrowserContext> persistentContexts = new ConcurrentHashMap<>();

    public BrowserSessionService(Browser browser, BrowserProperties properties,
            BrowserSessionRepository sessionRepository) {
        this.browser = browser;
        this.properties = properties;
        this.sessionRepository = sessionRepository;
    }

    /** Create an ephemeral browser context for a one-off task. Caller must close it when done. */
    public BrowserContext createEphemeral(String userId, String sparkId) {
        log.info("[BROWSER] Creating ephemeral session for user={} spark={}", userId, sparkId);

        BrowserContext context = browser.newContext();
        trackSession(userId, sparkId, BrowserSessionType.EPHEMERAL, null);
        return context;
    }

    /** Get or create a persistent browser context for a user. Reuses if already open. */
    public BrowserContext getPersistent(String userId, String sparkId) {
        BrowserContext existing = persistentContexts.get(userId);
        if (existing != null) {
            log.debug("[BROWSER] Reusing persistent session for user={}", userId);
            return existing;
        }

        log.info("[BROWSER] Creating persistent session for user={}", userId);

        // TODO: Phase 2 — pull profile from GCS before creating context
        BrowserContext context = browser.newContext();
        persistentContexts.put(userId, context);
        trackSession(userId, sparkId, BrowserSessionType.PERSISTENT, null);
        return context;
    }

    /** Release and close a persistent session for a user. */
    public void releasePersistent(String userId) {
        BrowserContext context = persistentContexts.remove(userId);
        if (context != null) {
            log.info("[BROWSER] Closing persistent session for user={}", userId);
            // TODO: Phase 2 — sync profile to GCS before closing
            context.close();
        }
    }

    /** Get count of active persistent sessions. */
    public int getActiveSessionCount() {
        return persistentContexts.size();
    }

    private void trackSession(String userId, String sparkId, BrowserSessionType type, String profilePath) {
        BrowserSession session = new BrowserSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setSparkId(sparkId);
        session.setType(type);
        session.setStatus(BrowserSessionStatus.ACTIVE);
        session.setCreatedAt(Instant.now());
        session.setLastActiveAt(Instant.now());
        session.setProfilePath(profilePath);
        sessionRepository.save(session, session.getId());
    }
}
```

**Step 4: Verify build**

Run: `./gradlew :business-browser:build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add business-browser/src/
git commit -m "feat: add BrowserConfig, BrowserProperties, BrowserSessionService"
```

---

### Task 8: Create BrowserSecurityService

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/service/BrowserSecurityService.java`

**Step 1: Create BrowserSecurityService**

```java
package io.tacticl.browser.service;

import io.tacticl.browser.data.entity.BrowserSettings;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Enforces domain allowlists/blocklists and file safety rules for browser automation. */
@Service
public class BrowserSecurityService {

    private static final Logger log = LoggerFactory.getLogger(BrowserSecurityService.class);

    private static final List<String> BLOCKED_EXTENSIONS = List.of(
        ".exe", ".bat", ".sh", ".msi", ".cmd", ".ps1", ".vbs", ".scr"
    );

    /** Check if a URL is allowed by the user's browser settings. */
    public boolean isDomainAllowed(String url, BrowserSettings settings) {
        if (settings == null) {
            return true;
        }

        String domain = extractDomain(url);
        if (domain == null) {
            log.warn("[BROWSER-SECURITY] Could not extract domain from URL: {}", url);
            return false;
        }

        // Check blocklist first
        if (settings.getDomainBlocklist() != null) {
            for (String blocked : settings.getDomainBlocklist()) {
                if (domain.endsWith(blocked)) {
                    log.info("[BROWSER-SECURITY] Domain blocked: {} (matches {})", domain, blocked);
                    return false;
                }
            }
        }

        // If allowlist is set, domain must be on it
        if (settings.getDomainAllowlist() != null && !settings.getDomainAllowlist().isEmpty()) {
            for (String allowed : settings.getDomainAllowlist()) {
                if (domain.endsWith(allowed)) {
                    return true;
                }
            }
            log.info("[BROWSER-SECURITY] Domain not on allowlist: {}", domain);
            return false;
        }

        return true;
    }

    /** Check if a file download is safe based on extension and size. */
    public boolean isFileSafe(String fileName, long sizeBytes, BrowserSettings settings) {
        // Check extension
        String lowerName = fileName.toLowerCase();
        for (String blocked : BLOCKED_EXTENSIONS) {
            if (lowerName.endsWith(blocked)) {
                log.warn("[BROWSER-SECURITY] Blocked file type: {}", fileName);
                return false;
            }
        }

        // Check size
        long maxSize = settings != null ? settings.getMaxFileSize() : 52428800;
        if (sizeBytes > maxSize) {
            log.warn("[BROWSER-SECURITY] File too large: {} bytes (max {})", sizeBytes, maxSize);
            return false;
        }

        return true;
    }

    /** Detect if a page likely requires login based on URL patterns. */
    public boolean isLoginPage(String url) {
        String lower = url.toLowerCase();
        return lower.contains("/login") || lower.contains("/signin") || lower.contains("/sign-in")
                || lower.contains("/auth") || lower.contains("oauth") || lower.contains("/sso");
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost();
        }
        catch (Exception e) {
            return null;
        }
    }
}
```

**Step 2: Verify build**

Run: `./gradlew :business-browser:build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add business-browser/src/
git commit -m "feat: add BrowserSecurityService with domain and file safety checks"
```

---

### Task 9: Create BrowserActionLogService

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/service/BrowserActionLogService.java`

**Step 1: Create BrowserActionLogService**

```java
package io.tacticl.browser.service;

import io.tacticl.browser.data.entity.BrowserActionLog;
import io.tacticl.browser.data.repository.BrowserActionLogRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Logs browser actions for audit trail and replay capability. */
@Service
public class BrowserActionLogService {

    private static final Logger log = LoggerFactory.getLogger(BrowserActionLogService.class);

    private final BrowserActionLogRepository actionLogRepository;

    public BrowserActionLogService(BrowserActionLogRepository actionLogRepository) {
        this.actionLogRepository = actionLogRepository;
    }

    /** Log a browser action. */
    public void logAction(String sessionId, String sparkId, String skillName, String url,
            String elementRef, Map<String, Object> inputData, String result, int tier,
            Boolean userApproved, long durationMs) {
        BrowserActionLog actionLog = new BrowserActionLog();
        actionLog.setId(UUID.randomUUID().toString());
        actionLog.setSessionId(sessionId);
        actionLog.setSparkId(sparkId);
        actionLog.setSkillName(skillName);
        actionLog.setTimestamp(Instant.now());
        actionLog.setUrl(url);
        actionLog.setElementRef(elementRef);
        actionLog.setInputData(inputData);
        actionLog.setResult(result);
        actionLog.setTier(tier);
        actionLog.setUserApproved(userApproved);
        actionLog.setDurationMs(durationMs);

        actionLogRepository.save(actionLog, actionLog.getId());
        log.debug("[BROWSER] Logged action: skill={} url={} duration={}ms", skillName, url, durationMs);
    }
}
```

**Step 2: Verify build**

Run: `./gradlew :business-browser:build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add business-browser/src/
git commit -m "feat: add BrowserActionLogService for audit trail"
```

---

### Task 10: Create core browser skills (navigate, snapshot, screenshot, scroll, wait)

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserNavigateSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserSnapshotSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserScreenshotSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserScrollSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserWaitSkill.java`

All Tier 0 (auto-execute) read-only skills. Each follows the `BrowseWebSkill` pattern exactly: `@Component`, implements `AgentSkill`, constructor-injected dependencies, `ObjectMapper` for schema building.

**Note:** These skills need access to `AgentSkill` interface from `business-agent`. To avoid circular dependency, the `AgentSkill` interface should ideally be in a shared location. However, for now the simplest approach is: `business-browser` depends on `business-agent` is wrong (circular). Instead, `business-agent` should depend on `business-browser`, and the skills in `business-browser` implement `AgentSkill` from `business-agent`.

**Resolution:** Move `AgentSkill` interface to `data-social` or a new shared module. Simplest fix: just add `implementation(project(":business-agent"))` won't work (circular). So we need `business-browser` to declare skills with its own interface, or move `AgentSkill` to a shared spot.

**Best approach:** Copy the `AgentSkill` interface into `data-browser` (it's a tiny interface with 5 methods). Skills in `business-browser` implement `io.tacticl.browser.data.entity.AgentSkill`... no that's messy.

**Actual best approach:** Keep skills in `business-browser` but have them implement `AgentSkill` from `business-agent`. Make `business-agent` depend on `business-browser`. The skills register in `ToolRegistry` via Spring's `List<AgentSkill>` injection — this works because `business-agent` depends on `business-browser` at compile time, so it sees the skill classes. The `AgentSkill` interface lives in `business-agent`, and `business-browser` depends on `business-agent` only for the interface. Wait — that's circular.

**Final resolution:** Extract `AgentSkill` and `ToolDefinition` into their own tiny module or put them in `data-social` which both modules already depend on. The cleanest fix: move `AgentSkill` interface to `data-social` (it already hosts entity types that both modules use). `ToolDefinition` comes from `cidadel-client-base`, already available.

**Step 1: Move AgentSkill to data-social**

Move `business-agent/src/main/java/io/strategiz/social/business/agent/skill/AgentSkill.java` to `data-social/src/main/java/io/strategiz/social/data/skill/AgentSkill.java`. Update package to `io.strategiz.social.data.skill`. Update all existing skill imports in `business-agent` to use the new package. Add `cidadel-client-base` dependency to `data-social` for `ToolDefinition`.

In `data-social/build.gradle.kts`, add:
```kotlin
implementation(libs.cidadel.client.base)
```

**Step 2: Create BrowserNavigateSkill**

```java
package io.tacticl.browser.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.strategiz.client.base.llm.model.ToolDefinition;
import io.strategiz.social.data.skill.AgentSkill;
import io.tacticl.browser.service.BrowserSecurityService;
import io.tacticl.browser.service.BrowserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Navigate to a URL and return the page's accessibility snapshot. Tier 0. */
@Component
public class BrowserNavigateSkill implements AgentSkill {

    private static final Logger log = LoggerFactory.getLogger(BrowserNavigateSkill.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BrowserSessionService sessionService;
    private final BrowserSecurityService securityService;

    public BrowserNavigateSkill(BrowserSessionService sessionService, BrowserSecurityService securityService) {
        this.sessionService = sessionService;
        this.securityService = securityService;
    }

    @Override
    public String getName() { return "browser_navigate"; }

    @Override
    public String getDescription() {
        return "Navigate to a URL in a cloud browser and return the page content as an accessibility snapshot";
    }

    @Override
    public ToolDefinition getToolDefinition() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode url = properties.putObject("url");
        url.put("type", "string");
        url.put("description", "The URL to navigate to");

        ObjectNode persistent = properties.putObject("persistent");
        persistent.put("type", "boolean");
        persistent.put("description", "Whether to use a persistent session with saved cookies/login state. Default false.");

        schema.putArray("required").add("url");

        return new ToolDefinition(getName(), getDescription(), schema);
    }

    @Override
    public String execute(JsonNode input, String userId) {
        String url = input.get("url").asText();
        boolean persistent = input.has("persistent") && input.get("persistent").asBoolean();

        // Domain check
        if (!securityService.isDomainAllowed(url, null)) {
            return "Navigation blocked: this domain is not allowed by your browser settings.";
        }

        try {
            BrowserContext context = persistent
                ? sessionService.getPersistent(userId, null)
                : sessionService.createEphemeral(userId, null);

            Page page = context.newPage();
            page.navigate(url);
            page.waitForLoadState();

            String title = page.title();
            String snapshot = page.content();
            // Truncate for Claude context
            if (snapshot.length() > 8000) {
                snapshot = snapshot.substring(0, 8000) + "\n[Page content truncated]";
            }

            if (!persistent) {
                context.close();
            }

            // Check if it's a login page
            if (securityService.isLoginPage(url)) {
                return "Navigated to " + url + " (\"" + title + "\")\n\n"
                    + "NOTE: This appears to be a login page. Use browser_session_login to let the user sign in.\n\n"
                    + snapshot;
            }

            return "Navigated to " + url + " (\"" + title + "\")\n\n" + snapshot;
        }
        catch (Exception e) {
            log.error("[BROWSER] Navigation failed for user={} url={}", userId, url, e);
            return "Failed to navigate to " + url + ": " + e.getMessage();
        }
    }

    @Override
    public int getConfirmationTier() { return 0; }
}
```

**Step 3: Create BrowserSnapshotSkill, BrowserScreenshotSkill, BrowserScrollSkill, BrowserWaitSkill**

Follow the same pattern as BrowserNavigateSkill. Each skill:
- `@Component`, implements `AgentSkill`
- Constructor-injected `BrowserSessionService`
- Builds JSON schema in `getToolDefinition()`
- Returns text result from `execute()`
- Tier 0

(Full source code for each in the implementation — patterns are identical, just different Playwright API calls.)

**Step 4: Verify build**

Run: `./gradlew :business-browser:build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add data-social/ business-browser/src/ business-agent/src/
git commit -m "feat: add Tier 0 browser skills (navigate, snapshot, screenshot, scroll, wait)"
```

---

### Task 11: Create mutation browser skills (click, type, fill_form, select)

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserClickSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserTypeSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserFillFormSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserSelectSkill.java`

All Tier 1 (requires confirmation). Same pattern as Task 10 but `getConfirmationTier()` returns `1`.

Key Playwright API calls:
- Click: `page.locator(selector).click()`
- Type: `page.locator(selector).fill(text)`
- Fill form: iterate fields, `page.locator(field.selector).fill(field.value)`
- Select: `page.locator(selector).selectOption(value)`

Skills accept element references by text/role/CSS selector. Return updated page snapshot after action.

**Step 1-4: Implement each skill following BrowserNavigateSkill pattern**

**Step 5: Verify build**

Run: `./gradlew :business-browser:build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add business-browser/src/
git commit -m "feat: add Tier 1 browser skills (click, type, fill_form, select)"
```

---

### Task 12: Create file browser skills (download, upload) and extract skill

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserDownloadSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserUploadSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserExtractSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserSessionLoginSkill.java`

Download/Upload are Tier 1. Extract is Tier 0. SessionLogin is Tier 1.

Download uses `page.waitForDownload()` → save to temp → upload to GCS.
Upload uses `page.setInputFiles()`.
Extract uses `page.evaluate()` to run JS extraction.
SessionLogin triggers a checkpoint — returns message telling user to open live view.

**Step 1-4: Implement each skill**

**Step 5: Verify build**

Run: `./gradlew :business-browser:build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add business-browser/src/
git commit -m "feat: add file browser skills (download, upload, extract, session_login)"
```

---

## Phase 3: Integration

### Task 13: Wire business-browser into business-agent

**Files:**
- Modify: `business-agent/build.gradle.kts` (add `implementation(project(":business-browser"))`)
- Modify: `business-agent/build.gradle.kts` (add `implementation(project(":data-browser"))`)

**Step 1: Add dependency**

In `business-agent/build.gradle.kts`:

```kotlin
dependencies {
    // Internal modules
    implementation(project(":data-social"))
    implementation(project(":data-browser"))       // NEW
    implementation(project(":business-social"))
    implementation(project(":business-browser"))    // NEW
    // ... rest unchanged
}
```

**Step 2: Verify browser skills auto-register in ToolRegistry**

The `ToolRegistry` constructor takes `List<AgentSkill>`. Since browser skills are `@Component` and implement `AgentSkill`, Spring injects them automatically. No code change needed in `ToolRegistry`.

**Step 3: Verify full build**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL — all 13 browser skills should appear in ToolRegistry logs on startup.

**Step 4: Commit**

```bash
git add business-agent/build.gradle.kts
git commit -m "feat: wire business-browser into business-agent for skill auto-registration"
```

---

### Task 14: Update execution routing in AgentController

**Files:**
- Modify: `service-agent/.../AgentController.java` (add execution preference routing)
- Modify: `data-social/.../TacticlUser.java` (add `executionPreference` field)

**Step 1: Add executionPreference to TacticlUser**

In `TacticlUser.java`, add field:

```java
private String executionPreference; // "DEVICE_FIRST", "CLOUD_FIRST", "CLOUD_ONLY"

public String getExecutionPreference() { return executionPreference; }
public void setExecutionPreference(String executionPreference) { this.executionPreference = executionPreference; }
```

**Step 2: Update AgentController.executeCommand()**

Replace the current routing logic (lines 105-115 of `AgentController.java`):

```java
// Determine execution preference (default: DEVICE_FIRST)
String preference = getExecutionPreference(user.getUserId());

switch (preference) {
    case "CLOUD_ONLY":
        return ResponseEntity.ok(executeInCloud(request, user.getUserId(), spark));

    case "CLOUD_FIRST":
        // Try cloud first, fall back to device
        return ResponseEntity.ok(executeInCloud(request, user.getUserId(), spark));

    case "DEVICE_FIRST":
    default:
        // Try device first, fall back to cloud (existing behavior)
        if (deviceRoutingService.hasOnlineDevice(user.getUserId())) {
            Optional<AgentCommandResponse> delegated = delegateToDevice(spark, user.getUserId());
            if (delegated.isPresent()) {
                return ResponseEntity.ok(delegated.get());
            }
        }
        return ResponseEntity.ok(executeInCloud(request, user.getUserId(), spark));
}
```

Add helper method:

```java
private String getExecutionPreference(String userId) {
    return userProvisioningService.getUser(userId)
        .map(TacticlUser::getExecutionPreference)
        .orElse("DEVICE_FIRST");
}
```

**Step 3: Verify build**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add service-agent/ data-social/
git commit -m "feat: add execution preference routing in AgentController"
```

---

### Task 15: Add Spark entity extension for browser tracking

**Files:**
- Modify: `data-social/.../Spark.java` (add `executionMode`, `browserSessionId`, `browserMinutesUsed`)

**Step 1: Add fields to Spark.java**

After the existing `models` field, add:

```java
private String executionMode; // "DEVICE", "CLOUD", "CLOUD_BROWSER"
private String browserSessionId;
private long browserMinutesUsed;
```

With standard getters/setters. Update constructor to set defaults:

```java
this.browserMinutesUsed = 0;
```

**Step 2: Verify build**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add data-social/
git commit -m "feat: extend Spark entity with browser execution tracking fields"
```

---

### Task 16: Add application.yml browser configuration

**Files:**
- Modify: `application/src/main/resources/application.yml` (or `.properties`) — add browser config section

**Step 1: Add browser configuration**

```yaml
tacticl:
  browser:
    enabled: ${BROWSER_ENABLED:false}
    max-concurrent-contexts: 3
    ephemeral-timeout-seconds: 60
    persistent-idle-timeout-seconds: 300
    page-load-timeout-seconds: 30
    max-pages-per-context: 5
    profile-bucket: tacticl-browser-profiles
    files-bucket: tacticl-user-files
```

**Step 2: Verify build**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add application/src/main/resources/
git commit -m "feat: add browser automation configuration properties"
```

---

### Task 17: Update application module dependencies

**Files:**
- Modify: `application/build.gradle.kts` (ensure data-browser and business-browser are in classpath)

**Step 1: Add dependencies if not already transitive**

Check if `business-browser` is already pulled in transitively via `business-agent`. If not, add explicitly:

```kotlin
implementation(project(":business-browser"))
implementation(project(":data-browser"))
```

**Step 2: Full build with tests**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL with all existing tests passing

**Step 3: Commit**

```bash
git add application/build.gradle.kts
git commit -m "feat: add browser modules to application classpath"
```

---

## Phase 4: Testing

### Task 18: Write unit tests for BrowserSecurityService

**Files:**
- Create: `business-browser/src/test/java/io/tacticl/browser/service/BrowserSecurityServiceTest.java`

Test cases:
- Domain on blocklist → blocked
- Domain not on allowlist when allowlist set → blocked
- Domain on allowlist → allowed
- No settings → all allowed
- File with blocked extension → blocked
- File over max size → blocked
- Login URL detection → true for `/login`, `/signin`, `/auth`, `oauth`
- Non-login URL → false

**Step 1: Write tests**
**Step 2: Run tests**

Run: `./gradlew :business-browser:test`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add business-browser/src/test/
git commit -m "test: add BrowserSecurityService unit tests"
```

---

### Task 19: Write unit tests for BrowserSessionService

**Files:**
- Create: `business-browser/src/test/java/io/tacticl/browser/service/BrowserSessionServiceTest.java`

Test cases (with mocked `Browser` and `BrowserSessionRepository`):
- createEphemeral creates context and tracks session
- getPersistent creates new context on first call
- getPersistent reuses existing context on second call
- releasePersistent closes and removes context
- getActiveSessionCount returns correct count

**Step 1: Write tests**
**Step 2: Run tests**

Run: `./gradlew :business-browser:test`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add business-browser/src/test/
git commit -m "test: add BrowserSessionService unit tests"
```

---

### Task 20: Write integration smoke test

**Files:**
- Create: `business-browser/src/test/java/io/tacticl/browser/skill/BrowserNavigateSkillTest.java`

Simple test that verifies the skill can be instantiated with mocked dependencies and returns expected tool definition schema.

**Step 1: Write test**
**Step 2: Run full test suite**

Run: `./gradlew test`
Expected: All tests PASS across all modules

**Step 3: Commit**

```bash
git add business-browser/src/test/
git commit -m "test: add BrowserNavigateSkill smoke test"
```

---

## Phase 5: Deployment Prep (Future — tracked but not blocking)

### Task 21: Update Dockerfile for Chromium + Playwright

Update Docker base image from Alpine to Ubuntu Jammy, install Playwright system deps + Chromium. Update Cloud Run memory from 4Gi to 8Gi, add min-instances=1 for warm starts.

### Task 22: Create GCS buckets and lifecycle rules

Create `tacticl-browser-profiles` and `tacticl-user-files` buckets with appropriate lifecycle rules.

### Task 23: Live view implementation (noVNC)

Implement `BrowserLiveViewService` with Xvfb + x11vnc + noVNC. Add WebSocket endpoint for live view requests.

### Task 24: GCS profile sync for persistent sessions

Implement `BrowserProfileStorageService` to sync Chromium user-data dirs to/from GCS.
