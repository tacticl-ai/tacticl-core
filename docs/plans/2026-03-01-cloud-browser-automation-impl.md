# Cloud Browser Automation — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement full cloud browser automation so the agent can interact with websites (navigate, click, type, fill forms, download/upload) when no user device is online.

**Architecture:** Two new Gradle modules (`data-browser`, `business-browser`) following existing patterns. Java Playwright (com.microsoft.playwright) runs Chromium in-process. 13 browser skills register in ToolRegistry via Spring component scanning. Routing logic in AgentController updated to respect user's `executionPreference`. GCS operations via a new `client-gcs` module using RestClient (client-base pattern).

**Tech Stack:** Java 21, Spring Boot 3.5.7, Playwright (Java), Google Cloud Storage REST API, Firestore

**Design Doc:** `docs/plans/2026-02-27-cloud-browser-automation-design.md` (APPROVED)

---

## Task 1: Gradle Module Scaffolding & Dependencies

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `data-browser/build.gradle.kts`
- Create: `business-browser/build.gradle.kts`
- Create: `client-gcs/build.gradle.kts`
- Modify: `application/build.gradle.kts`
- Modify: `business-agent/build.gradle.kts`

**What to do:**

1. Add Playwright and GCS to `gradle/libs.versions.toml`:
```toml
# Under [versions]
playwright = "1.49.0"

# Under [libraries]
playwright = { module = "com.microsoft.playwright:playwright", version.ref = "playwright" }
google-cloud-storage = { module = "com.google.cloud:google-cloud-storage", version = "2.45.0" }
```

2. Add new modules to `settings.gradle.kts` (add after `"client-jina"`):
```kotlin
"data-browser",
"business-browser",
"client-gcs"
```

3. Create `data-browser/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.google.cloud.firestore)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

4. Create `client-gcs/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.client.base)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.databind)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

5. Create `business-browser/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":data-browser"))
    implementation(project(":data-social"))
    implementation(project(":business-agent"))
    implementation(project(":client-gcs"))
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.client.base)
    implementation(libs.cidadel.framework.llm.router)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.google.cloud.firestore)
    implementation(libs.jackson.databind)
    implementation(libs.playwright)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

6. Add to `application/build.gradle.kts` dependencies:
```kotlin
implementation(project(":data-browser"))
implementation(project(":business-browser"))
implementation(project(":client-gcs"))
```

7. Add to `business-agent/build.gradle.kts` dependencies:
```kotlin
implementation(project(":data-browser"))
```
(business-agent needs data-browser for the BrowserSession entity references in Spark)

8. Create empty source directories:
```
data-browser/src/main/java/io/tacticl/browser/data/entity/
data-browser/src/main/java/io/tacticl/browser/data/repository/
business-browser/src/main/java/io/tacticl/browser/service/
business-browser/src/main/java/io/tacticl/browser/skill/
business-browser/src/main/java/io/tacticl/browser/config/
client-gcs/src/main/java/io/tacticl/client/gcs/client/
client-gcs/src/main/java/io/tacticl/client/gcs/config/
client-gcs/src/main/java/io/tacticl/client/gcs/dto/
```

9. Run `./gradlew build -x test` to verify modules compile.

10. Commit: `feat: Add data-browser, business-browser, client-gcs module scaffolding`

---

## Task 2: Data Layer — Browser Entities

**Files:**
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserSession.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserSessionType.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserSessionStatus.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/BrowserActionLog.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/UserFile.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/UserFileType.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/entity/CheckpointType.java`

**Entity patterns:** Follow existing tacticl pattern — `@IgnoreExtraProperties`, no BaseEntity extension, simple getters/setters, no-arg constructor.

### BrowserSession.java
```java
package io.tacticl.browser.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import java.time.Instant;

@IgnoreExtraProperties
public class BrowserSession {
    private String id;
    private String userId;
    private String sparkId;
    private BrowserSessionType type;        // EPHEMERAL or PERSISTENT
    private BrowserSessionStatus status;    // ACTIVE, IDLE, CLOSED
    private String profilePath;             // GCS path for persistent profiles
    private String currentUrl;
    private int pagesOpen;
    private long memoryUsageMb;
    private boolean liveViewEnabled;
    private long durationSeconds;
    private Instant createdAt;
    private Instant lastActiveAt;
    private Instant closedAt;

    public BrowserSession() {
        this.type = BrowserSessionType.EPHEMERAL;
        this.status = BrowserSessionStatus.ACTIVE;
        this.pagesOpen = 0;
        this.memoryUsageMb = 0;
        this.liveViewEnabled = false;
        this.durationSeconds = 0;
    }
    // All getters/setters
}
```

### BrowserSessionType.java
```java
package io.tacticl.browser.data.entity;
public enum BrowserSessionType { EPHEMERAL, PERSISTENT }
```

### BrowserSessionStatus.java
```java
package io.tacticl.browser.data.entity;
public enum BrowserSessionStatus { ACTIVE, IDLE, CLOSED }
```

### BrowserActionLog.java
```java
package io.tacticl.browser.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import java.time.Instant;

@IgnoreExtraProperties
public class BrowserActionLog {
    private String id;
    private String sessionId;
    private String sparkId;
    private String skillName;
    private String url;
    private String elementRef;
    private String inputData;
    private String result;
    private String screenshotUrl;
    private int tier;
    private boolean userApproved;
    private long durationMs;
    private Instant timestamp;

    public BrowserActionLog() {
        this.userApproved = true;
        this.durationMs = 0;
    }
    // All getters/setters
}
```

### UserFile.java
```java
package io.tacticl.browser.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import java.time.Instant;

@IgnoreExtraProperties
public class UserFile {
    private String id;
    private String userId;
    private String sparkId;
    private String sessionId;
    private UserFileType type;      // DOWNLOAD or UPLOAD
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String gcsPath;
    private String sourceUrl;
    private Instant createdAt;
    private Instant expiresAt;

    public UserFile() {
        this.sizeBytes = 0;
    }
    // All getters/setters
}
```

### UserFileType.java
```java
package io.tacticl.browser.data.entity;
public enum UserFileType { DOWNLOAD, UPLOAD }
```

### CheckpointType.java
```java
package io.tacticl.browser.data.entity;
public enum CheckpointType {
    ACTION_CONFIRMATION,    // existing (re-declared here for browser use)
    LOGIN_REQUIRED,
    CAPTCHA_DETECTED,
    PURCHASE_CONFIRMATION,
    DOWNLOAD_APPROVAL,
    BROWSER_ERROR,
    AMBIGUOUS_STATE
}
```

Run `./gradlew :data-browser:build -x test` to verify.

Commit: `feat: Add browser data entities — BrowserSession, BrowserActionLog, UserFile`

---

## Task 3: Data Layer — Browser Repositories

**Files:**
- Create: `data-browser/src/main/java/io/tacticl/browser/data/repository/BrowserSessionRepository.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/repository/BrowserActionLogRepository.java`
- Create: `data-browser/src/main/java/io/tacticl/browser/data/repository/UserFileRepository.java`

**Pattern:** Extend `FirestoreRepository<T>` from data-social. NOTE: data-browser needs to depend on data-social for the base repo class, OR duplicate the base class. Since data modules should only depend on framework, the cleanest approach is to copy `FirestoreRepository.java` into data-browser. However, to avoid duplication, add `implementation(project(":data-social"))` to data-browser's build.gradle.kts.

### BrowserSessionRepository.java
```java
package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.tacticl.browser.data.entity.BrowserSession;
import io.tacticl.browser.data.entity.BrowserSessionStatus;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class BrowserSessionRepository extends FirestoreRepository<BrowserSession> {

    public BrowserSessionRepository(Firestore firestore) {
        super(firestore, BrowserSession.class, "browser_sessions");
    }

    public List<BrowserSession> findActiveByUserId(String userId) {
        return findByField("userId", userId).stream()
            .filter(s -> s.getStatus() == BrowserSessionStatus.ACTIVE
                      || s.getStatus() == BrowserSessionStatus.IDLE)
            .toList();
    }

    public List<BrowserSession> findBySparkId(String sparkId) {
        return findByField("sparkId", sparkId);
    }
}
```

### BrowserActionLogRepository.java
```java
package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.tacticl.browser.data.entity.BrowserActionLog;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class BrowserActionLogRepository extends FirestoreRepository<BrowserActionLog> {

    public BrowserActionLogRepository(Firestore firestore) {
        super(firestore, BrowserActionLog.class, "browser_action_logs");
    }

    public List<BrowserActionLog> findBySessionId(String sessionId) {
        return findByField("sessionId", sessionId);
    }

    public List<BrowserActionLog> findBySparkId(String sparkId) {
        return findByField("sparkId", sparkId);
    }
}
```

### UserFileRepository.java
```java
package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.tacticl.browser.data.entity.UserFile;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class UserFileRepository extends FirestoreRepository<UserFile> {

    public UserFileRepository(Firestore firestore) {
        super(firestore, UserFile.class, "user_files");
    }

    public List<UserFile> findByUserId(String userId) {
        return findByField("userId", userId);
    }

    public List<UserFile> findBySparkId(String sparkId) {
        return findByField("sparkId", sparkId);
    }
}
```

Update `data-browser/build.gradle.kts` to add:
```kotlin
implementation(project(":data-social"))
```

Run `./gradlew :data-browser:build -x test` to verify.

Commit: `feat: Add browser repositories — BrowserSession, BrowserActionLog, UserFile`

---

## Task 4: GCS Client Module

**Files:**
- Create: `client-gcs/src/main/java/io/tacticl/client/gcs/config/GcsConfig.java`
- Create: `client-gcs/src/main/java/io/tacticl/client/gcs/config/GcsVaultConfig.java`
- Create: `client-gcs/src/main/java/io/tacticl/client/gcs/config/ClientGcsConfig.java`
- Create: `client-gcs/src/main/java/io/tacticl/client/gcs/client/GcsClient.java`
- Create: `client-gcs/src/main/java/io/tacticl/client/gcs/dto/GcsUploadResult.java`

Follow the client-brave-search pattern exactly: Config → VaultConfig → ClientConfig → Client.

### GcsConfig.java
```java
package io.tacticl.client.gcs.config;

public class GcsConfig {
    private String projectId = "tacticl";
    private String profileBucket = "tacticl-browser-profiles";
    private String filesBucket = "tacticl-user-files";
    private String serviceAccountKey;

    public boolean isConfigured() {
        return serviceAccountKey != null && !serviceAccountKey.isEmpty();
    }
    // All getters/setters
}
```

### GcsVaultConfig.java
```java
package io.tacticl.client.gcs.config;

import io.strategiz.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class GcsVaultConfig {
    private final SecretManager secretManager;
    private final GcsConfig gcsConfig;

    public GcsVaultConfig(SecretManager secretManager, GcsConfig gcsConfig) {
        this.secretManager = secretManager;
        this.gcsConfig = gcsConfig;
    }

    @PostConstruct
    public void loadFromVault() {
        String key = secretManager.readSecret("gcs.service-account-key", null);
        if (key != null) {
            gcsConfig.setServiceAccountKey(key);
        }
    }
}
```

### ClientGcsConfig.java
```java
package io.tacticl.client.gcs.config;

import io.tacticl.client.gcs.client.GcsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class ClientGcsConfig {

    @Bean
    public GcsConfig gcsConfig() {
        return new GcsConfig();
    }

    @Bean
    public GcsClient gcsClient(GcsConfig config) {
        return new GcsClient(config);
    }
}
```

### GcsClient.java
```java
package io.tacticl.client.gcs.client;

import io.tacticl.client.gcs.config.GcsConfig;
import io.tacticl.client.gcs.dto.GcsUploadResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Client for Google Cloud Storage — browser profile and user file storage. */
public class GcsClient {
    private static final Logger logger = LoggerFactory.getLogger(GcsClient.class);
    private static final String GCS_BASE_URL = "https://storage.googleapis.com";

    private final GcsConfig config;
    private final RestClient restClient;

    public GcsClient(GcsConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
            .baseUrl(GCS_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    /** Upload a file to a GCS bucket. */
    public GcsUploadResult upload(String bucket, String objectName, byte[] data, String contentType) {
        // Uses GCS JSON API: POST /upload/storage/v1/b/{bucket}/o
        try {
            restClient.post()
                .uri("/upload/storage/v1/b/{bucket}/o?uploadType=media&name={name}", bucket, objectName)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .body(data)
                .retrieve()
                .toBodilessEntity();

            String gcsPath = String.format("gs://%s/%s", bucket, objectName);
            return new GcsUploadResult(gcsPath, objectName, data.length);
        } catch (Exception e) {
            logger.error("GCS upload failed: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("GCS upload failed: " + e.getMessage(), e);
        }
    }

    /** Download a file from GCS. */
    public byte[] download(String bucket, String objectName) {
        try {
            return restClient.get()
                .uri("/storage/v1/b/{bucket}/o/{object}?alt=media", bucket, objectName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .retrieve()
                .body(byte[].class);
        } catch (Exception e) {
            logger.error("GCS download failed: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("GCS download failed: " + e.getMessage(), e);
        }
    }

    /** Delete a file from GCS. */
    public void delete(String bucket, String objectName) {
        try {
            restClient.delete()
                .uri("/storage/v1/b/{bucket}/o/{object}", bucket, objectName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            logger.error("GCS delete failed: bucket={}, object={}", bucket, objectName, e);
        }
    }

    public String getProfileBucket() { return config.getProfileBucket(); }
    public String getFilesBucket() { return config.getFilesBucket(); }

    private String getAccessToken() {
        // In Cloud Run, use the metadata server for default credentials
        // In local dev, fall back to service account key from Vault
        try {
            RestClient metadataClient = RestClient.builder()
                .baseUrl("http://metadata.google.internal")
                .build();
            String response = metadataClient.get()
                .uri("/computeMetadata/v1/instance/service-accounts/default/token")
                .header("Metadata-Flavor", "Google")
                .retrieve()
                .body(String.class);
            // Parse JSON response for access_token
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readTree(response).get("access_token").asText();
        } catch (Exception e) {
            logger.warn("Metadata server unavailable, using config key");
            return config.getServiceAccountKey();
        }
    }
}
```

### GcsUploadResult.java
```java
package io.tacticl.client.gcs.dto;

public class GcsUploadResult {
    private final String gcsPath;
    private final String objectName;
    private final long sizeBytes;

    public GcsUploadResult(String gcsPath, String objectName, long sizeBytes) {
        this.gcsPath = gcsPath;
        this.objectName = objectName;
        this.sizeBytes = sizeBytes;
    }

    public String getGcsPath() { return gcsPath; }
    public String getObjectName() { return objectName; }
    public long getSizeBytes() { return sizeBytes; }
}
```

Run `./gradlew :client-gcs:build -x test`.

Commit: `feat: Add client-gcs module for browser profile and file storage`

---

## Task 5: Existing Entity Updates — Spark, UserConfig, TacticlUser

**Files:**
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/Spark.java`
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/UserConfig.java`
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/TacticlUser.java`
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/ExecutionPreference.java`
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/ExecutionMode.java`
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/BrowserSettings.java`

### ExecutionPreference.java
```java
package io.strategiz.social.data.entity;
public enum ExecutionPreference {
    DEVICE_FIRST,   // Try device, fall back to cloud browser (default)
    CLOUD_FIRST,    // Try cloud browser, fall back to device
    CLOUD_ONLY      // Never route to device
}
```

### ExecutionMode.java
```java
package io.strategiz.social.data.entity;
public enum ExecutionMode {
    DEVICE,          // Executed on user's device
    CLOUD,           // Cloud execution (LLM tools only, no browser)
    CLOUD_BROWSER    // Cloud execution with browser automation
}
```

### BrowserSettings.java
```java
package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public class BrowserSettings {
    private List<String> domainAllowlist = new ArrayList<>();
    private List<String> domainBlocklist = new ArrayList<>();
    private List<String> autoBlockCategories = new ArrayList<>();
    private boolean allowFileDownloads = true;
    private boolean allowFileUploads = true;
    private long maxFileSize = 52428800; // 50MB
    private int maxSpendPerAction = 0;

    public BrowserSettings() {}

    public static BrowserSettings defaults() {
        return new BrowserSettings();
    }
    // All getters/setters
}
```

### Spark.java additions (add 3 new fields after `models`):
```java
private ExecutionMode executionMode;
private String browserSessionId;
private long browserMinutesUsed;
```
Default `executionMode` to null in constructor (will be set during routing). Add getters/setters.

### UserConfig.java additions (add after `confirmationOverrides`):
```java
private ExecutionPreference executionPreference = ExecutionPreference.DEVICE_FIRST;
private BrowserSettings browserSettings;
```
Initialize `browserSettings` to null in defaults (lazy init). Add getters/setters.

### TacticlUser.java — no changes needed (UserConfig is embedded, new fields auto-appear)

Run `./gradlew :data-social:build -x test`.

Commit: `feat: Add ExecutionPreference, ExecutionMode, BrowserSettings to data model`

---

## Task 6: Browser Session Service

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/service/BrowserSessionService.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/config/BrowserConfig.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/config/BrowserProperties.java`

### BrowserProperties.java
```java
package io.tacticl.browser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tacticl.browser")
public class BrowserProperties {
    private boolean enabled = false;
    private int maxConcurrentContexts = 3;
    private int ephemeralTimeoutSeconds = 60;
    private int persistentIdleTimeoutSeconds = 300;
    private int pageLoadTimeoutSeconds = 30;
    private int maxPagesPerContext = 5;

    // All getters/setters
}
```

### BrowserConfig.java
```java
package io.tacticl.browser.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
@EnableConfigurationProperties(BrowserProperties.class)
public class BrowserConfig {
    private static final Logger log = LoggerFactory.getLogger(BrowserConfig.class);
    private Playwright playwright;

    @Bean
    public Playwright playwright() {
        log.info("Initializing Playwright runtime...");
        this.playwright = Playwright.create();
        return playwright;
    }

    @Bean
    public Browser chromiumBrowser(Playwright playwright, BrowserProperties props) {
        log.info("Launching Chromium browser (max {} contexts)", props.getMaxConcurrentContexts());
        return playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(true)
            .setArgs(java.util.List.of(
                "--no-sandbox",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--block-new-web-contents"
            )));
    }

    @PreDestroy
    public void cleanup() {
        if (playwright != null) {
            log.info("Shutting down Playwright...");
            playwright.close();
        }
    }
}
```

### BrowserSessionService.java
```java
package io.tacticl.browser.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.tacticl.browser.config.BrowserProperties;
import io.tacticl.browser.data.entity.BrowserSession;
import io.tacticl.browser.data.entity.BrowserSessionStatus;
import io.tacticl.browser.data.entity.BrowserSessionType;
import io.tacticl.browser.data.repository.BrowserSessionRepository;
import io.tacticl.client.gcs.client.GcsClient;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserSessionService {
    private static final Logger log = LoggerFactory.getLogger(BrowserSessionService.class);

    private final Browser browser;
    private final BrowserProperties properties;
    private final BrowserSessionRepository sessionRepository;
    private final Optional<GcsClient> gcsClient;
    private final Map<String, BrowserContext> activeContexts = new ConcurrentHashMap<>(); // userId → context
    private final Map<String, String> sessionIds = new ConcurrentHashMap<>(); // userId → sessionId

    public BrowserSessionService(Browser browser, BrowserProperties properties,
            BrowserSessionRepository sessionRepository, Optional<GcsClient> gcsClient) {
        this.browser = browser;
        this.properties = properties;
        this.sessionRepository = sessionRepository;
        this.gcsClient = gcsClient;
    }

    /** Get or create an ephemeral browser context for the user. */
    public BrowserContext getEphemeralContext(String userId, String sparkId) {
        // If user already has an active context, return it
        BrowserContext existing = activeContexts.get(userId);
        if (existing != null) {
            return existing;
        }

        // Check concurrent context limit
        if (activeContexts.size() >= properties.getMaxConcurrentContexts()) {
            throw new RuntimeException("Maximum concurrent browser sessions reached ("
                + properties.getMaxConcurrentContexts() + ")");
        }

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
            .setViewportSize(1280, 720)
            .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"));

        activeContexts.put(userId, context);

        // Track in Firestore
        String sessionId = UUID.randomUUID().toString();
        sessionIds.put(userId, sessionId);
        BrowserSession session = new BrowserSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setSparkId(sparkId);
        session.setType(BrowserSessionType.EPHEMERAL);
        session.setStatus(BrowserSessionStatus.ACTIVE);
        session.setCreatedAt(Instant.now());
        session.setLastActiveAt(Instant.now());
        sessionRepository.save(session, sessionId);

        log.info("[BROWSER] Created ephemeral session {} for user={}, spark={}", sessionId, userId, sparkId);
        return context;
    }

    /** Get the active page for a user, creating one if needed. */
    public Page getPage(String userId, String sparkId) {
        BrowserContext context = getEphemeralContext(userId, sparkId);
        var pages = context.pages();
        if (pages.isEmpty()) {
            Page page = context.newPage();
            page.setDefaultTimeout(properties.getPageLoadTimeoutSeconds() * 1000.0);
            return page;
        }
        return pages.get(pages.size() - 1); // return last active page
    }

    /** Release a user's browser session. */
    public void releaseSession(String userId) {
        BrowserContext context = activeContexts.remove(userId);
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                log.warn("Error closing browser context for user={}", userId, e);
            }
        }

        String sessionId = sessionIds.remove(userId);
        if (sessionId != null) {
            sessionRepository.findById(sessionId).ifPresent(session -> {
                session.setStatus(BrowserSessionStatus.CLOSED);
                session.setClosedAt(Instant.now());
                long durationSec = java.time.Duration.between(session.getCreatedAt(), Instant.now()).getSeconds();
                session.setDurationSeconds(durationSec);
                sessionRepository.save(session, sessionId);
            });
        }

        log.info("[BROWSER] Released session for user={}", userId);
    }

    /** Check if user has an active browser session. */
    public boolean hasActiveSession(String userId) {
        return activeContexts.containsKey(userId);
    }

    /** Get the current session ID for a user. */
    public Optional<String> getSessionId(String userId) {
        return Optional.ofNullable(sessionIds.get(userId));
    }

    @PreDestroy
    void cleanup() {
        log.info("[BROWSER] Cleaning up {} active sessions", activeContexts.size());
        activeContexts.values().forEach(ctx -> {
            try { ctx.close(); } catch (Exception e) { /* ignore */ }
        });
        activeContexts.clear();
        sessionIds.clear();
    }
}
```

Run `./gradlew :business-browser:build -x test`.

Commit: `feat: Add BrowserSessionService with Playwright lifecycle management`

---

## Task 7: Browser Security Service

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/service/BrowserSecurityService.java`

```java
package io.tacticl.browser.service;

import io.strategiz.social.data.entity.BrowserSettings;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.business.agent.service.UserConfigService;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserSecurityService {
    private static final Logger log = LoggerFactory.getLogger(BrowserSecurityService.class);

    private static final Set<String> BLOCKED_DOMAINS = Set.of(
        "169.254.169.254",  // GCP metadata server
        "metadata.google.internal"
    );

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
        ".exe", ".bat", ".sh", ".msi", ".cmd", ".ps1", ".vbs"
    );

    private static final long MAX_FILE_SIZE = 52_428_800; // 50MB

    private final UserConfigService userConfigService;

    public BrowserSecurityService(UserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    /** Check if a URL is allowed for navigation. */
    public boolean isUrlAllowed(String url, String userId) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;

            // Block internal infrastructure
            if (BLOCKED_DOMAINS.contains(host)) {
                log.warn("[BROWSER-SEC] Blocked infrastructure URL: {} for user={}", url, userId);
                return false;
            }

            // Check user's browser settings
            UserConfig config = userConfigService.getConfig(userId);
            BrowserSettings browserSettings = config.getBrowserSettings();
            if (browserSettings == null) return true; // no restrictions

            // Check blocklist
            if (!browserSettings.getDomainBlocklist().isEmpty()
                    && browserSettings.getDomainBlocklist().stream().anyMatch(host::endsWith)) {
                log.warn("[BROWSER-SEC] Domain blocked by user: {} for user={}", host, userId);
                return false;
            }

            // Check allowlist (if set, ONLY allowlisted domains allowed)
            if (!browserSettings.getDomainAllowlist().isEmpty()
                    && browserSettings.getDomainAllowlist().stream().noneMatch(host::endsWith)) {
                log.warn("[BROWSER-SEC] Domain not in allowlist: {} for user={}", host, userId);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("[BROWSER-SEC] URL validation failed: {}", url, e);
            return false;
        }
    }

    /** Check if a file download is allowed. */
    public boolean isDownloadAllowed(String fileName, long sizeBytes, String userId) {
        UserConfig config = userConfigService.getConfig(userId);
        BrowserSettings settings = config.getBrowserSettings();

        if (settings != null && !settings.isAllowFileDownloads()) return false;

        long maxSize = settings != null ? settings.getMaxFileSize() : MAX_FILE_SIZE;
        if (sizeBytes > maxSize) return false;

        return BLOCKED_EXTENSIONS.stream().noneMatch(ext -> fileName.toLowerCase().endsWith(ext));
    }

    /** Check if file upload is allowed. */
    public boolean isUploadAllowed(String userId) {
        UserConfig config = userConfigService.getConfig(userId);
        BrowserSettings settings = config.getBrowserSettings();
        return settings == null || settings.isAllowFileUploads();
    }
}
```

Run `./gradlew :business-browser:build -x test`.

Commit: `feat: Add BrowserSecurityService with domain controls and file safety`

---

## Task 8: Browser Action Logger

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/service/BrowserActionLogger.java`

```java
package io.tacticl.browser.service;

import io.tacticl.browser.data.entity.BrowserActionLog;
import io.tacticl.browser.data.repository.BrowserActionLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserActionLogger {
    private static final Logger log = LoggerFactory.getLogger(BrowserActionLogger.class);

    private final BrowserActionLogRepository actionLogRepository;

    public BrowserActionLogger(BrowserActionLogRepository actionLogRepository) {
        this.actionLogRepository = actionLogRepository;
    }

    public void logAction(String sessionId, String sparkId, String skillName,
            String url, String elementRef, String result, int tier, long durationMs) {
        try {
            BrowserActionLog actionLog = new BrowserActionLog();
            actionLog.setId(UUID.randomUUID().toString());
            actionLog.setSessionId(sessionId);
            actionLog.setSparkId(sparkId);
            actionLog.setSkillName(skillName);
            actionLog.setUrl(url);
            actionLog.setElementRef(elementRef);
            actionLog.setResult(result);
            actionLog.setTier(tier);
            actionLog.setDurationMs(durationMs);
            actionLog.setTimestamp(Instant.now());
            actionLogRepository.save(actionLog, actionLog.getId());
        } catch (Exception e) {
            log.error("Failed to log browser action: {}", skillName, e);
        }
    }
}
```

Commit: `feat: Add BrowserActionLogger for audit trail`

---

## Task 9: Core Browser Skills (navigate, snapshot, screenshot)

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserNavigateSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserSnapshotSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserScreenshotSkill.java`

All skills implement `AgentSkill` from business-agent, follow the SearchWebSkill pattern.

### BrowserNavigateSkill.java
```java
package io.tacticl.browser.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import io.strategiz.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.SparkContext;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.tacticl.browser.service.BrowserActionLogger;
import io.tacticl.browser.service.BrowserSecurityService;
import io.tacticl.browser.service.BrowserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserNavigateSkill implements AgentSkill {
    private static final Logger log = LoggerFactory.getLogger(BrowserNavigateSkill.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BrowserSessionService sessionService;
    private final BrowserSecurityService securityService;
    private final BrowserActionLogger actionLogger;

    public BrowserNavigateSkill(BrowserSessionService sessionService,
            BrowserSecurityService securityService, BrowserActionLogger actionLogger) {
        this.sessionService = sessionService;
        this.securityService = securityService;
        this.actionLogger = actionLogger;
    }

    @Override public String getName() { return "browser_navigate"; }

    @Override public String getDescription() {
        return "Navigate to a URL in a cloud browser and return the page's accessibility snapshot for analysis";
    }

    @Override public ToolDefinition getToolDefinition() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode url = props.putObject("url");
        url.put("type", "string");
        url.put("description", "The URL to navigate to");
        ObjectNode waitFor = props.putObject("wait_for");
        waitFor.put("type", "string");
        waitFor.put("description", "Optional text to wait for on the page before returning");
        schema.putArray("required").add("url");
        return new ToolDefinition(getName(), getDescription(), schema);
    }

    @Override
    public String execute(JsonNode input, String userId) {
        String url = input.get("url").asText();
        String waitFor = input.has("wait_for") ? input.get("wait_for").asText() : null;
        long start = System.currentTimeMillis();

        if (!securityService.isUrlAllowed(url, userId)) {
            return "Navigation blocked: URL is not allowed by your browser security settings.";
        }

        try {
            String sparkId = SparkContext.get() != null ? SparkContext.get().getSparkId() : null;
            Page page = sessionService.getPage(userId, sparkId);
            page.navigate(url);

            if (waitFor != null) {
                page.waitForSelector("text=" + waitFor,
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            }

            // Return accessibility snapshot for Claude to reason over
            String snapshot = page.accessibility().snapshot().toString();
            String title = page.title();

            actionLogger.logAction(
                sessionService.getSessionId(userId).orElse(null),
                sparkId, getName(), url, null, "OK", getConfirmationTier(),
                System.currentTimeMillis() - start);

            return String.format("Navigated to: %s\nTitle: %s\n\nPage snapshot:\n%s", url, title, snapshot);
        } catch (Exception e) {
            log.error("Browser navigate failed: url={}, user={}", url, userId, e);
            return "Navigation failed: " + e.getMessage();
        }
    }

    @Override public int getConfirmationTier() { return 0; }
}
```

### BrowserSnapshotSkill.java
```java
package io.tacticl.browser.skill;

// Similar pattern to BrowserNavigateSkill
// Takes no required params, returns accessibility tree of current page
// getName() = "browser_snapshot", tier 0
// Calls page.accessibility().snapshot() on existing page
```

### BrowserScreenshotSkill.java
```java
package io.tacticl.browser.skill;

// Takes optional "full_page" boolean param
// getName() = "browser_screenshot", tier 0
// Calls page.screenshot(), encodes as base64
// Stores to GCS via GcsClient if available, returns GCS URL
// Falls back to returning base64 if no GCS
```

Each skill follows the exact same pattern: `@Component`, `@ConditionalOnProperty`, constructor injection, ObjectNode schema, SparkContext for sparkId.

Commit: `feat: Add core browser skills — navigate, snapshot, screenshot`

---

## Task 10: Interaction Browser Skills (click, type, fill_form, select)

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserClickSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserTypeSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserFillFormSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserSelectSkill.java`

All Tier 1 (mutations). Same pattern as Task 9 but with `getConfirmationTier() = 1`.

### BrowserClickSkill
- Params: `selector` (string, CSS/text selector), `button` (string, optional: "left"/"right")
- Uses: `page.click(selector)` or `page.getByText(selector).click()`
- Returns: accessibility snapshot of page after click

### BrowserTypeSkill
- Params: `selector` (string), `text` (string), `submit` (boolean, optional — press Enter after)
- Uses: `page.fill(selector, text)` or `page.getByRole(...).fill(text)`
- Returns: confirmation message

### BrowserFillFormSkill
- Params: `fields` (array of objects: `{selector, value}`)
- Uses: iterates fields, calls `page.fill()` for each
- Returns: summary of fields filled

### BrowserSelectSkill
- Params: `selector` (string), `value` (string)
- Uses: `page.selectOption(selector, value)`
- Returns: confirmation

Commit: `feat: Add interaction browser skills — click, type, fill_form, select`

---

## Task 11: Utility Browser Skills (scroll, wait, extract)

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserScrollSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserWaitSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserExtractSkill.java`

All Tier 0 (read-only).

### BrowserScrollSkill
- Params: `direction` (enum: up/down), `amount` (int, pixels, optional default 500)
- Uses: `page.evaluate("window.scrollBy(0, " + amount + ")")`
- Returns: new accessibility snapshot

### BrowserWaitSkill
- Params: `text` (string, optional), `selector` (string, optional), `timeout` (int, ms, default 10000)
- Uses: `page.waitForSelector()` or `page.waitForTimeout()`
- Returns: confirmation or timeout error

### BrowserExtractSkill
- Params: `selector` (string, optional CSS selector), `extract_type` (enum: text/html/attributes)
- Uses: `page.evalOnSelector()` or `page.content()` then parse
- Returns: extracted data as structured text

Commit: `feat: Add utility browser skills — scroll, wait, extract`

---

## Task 12: File Browser Skills (download, upload) & Session Login

**Files:**
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserDownloadSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserUploadSkill.java`
- Create: `business-browser/src/main/java/io/tacticl/browser/skill/BrowserSessionLoginSkill.java`

All Tier 1.

### BrowserDownloadSkill
- Params: `selector` (string — element that triggers download)
- Uses Playwright download handling: `page.waitForDownload(() -> page.click(selector))`
- Saves to GCS: `gs://tacticl-user-files/{userId}/downloads/{fileName}`
- Creates UserFile entity in Firestore
- Security: checks `isDownloadAllowed()` before proceeding
- Returns: file metadata (name, size, GCS path, content type)

### BrowserUploadSkill
- Params: `selector` (string — file input), `gcs_path` (string — GCS path of file to upload)
- Downloads from GCS to temp, uses `page.setInputFiles(selector, path)`
- Security: checks `isUploadAllowed()`
- Returns: confirmation

### BrowserSessionLoginSkill
- Params: `url` (string — the login page URL)
- Navigates to URL, takes screenshot, creates checkpoint
- Uses SparkService.onSparkCheckpoint() with type LOGIN_REQUIRED
- Returns: "Login checkpoint created. Waiting for user to authenticate."

Commit: `feat: Add file and session browser skills — download, upload, session_login`

---

## Task 13: Routing Logic Updates

**Files:**
- Modify: `service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java`
- Modify: `business-agent/src/main/java/io/strategiz/social/business/agent/service/AgentSystemPrompt.java`
- Modify: `business-agent/src/main/java/io/strategiz/social/business/agent/service/SparkService.java`

### AgentController.java changes

Replace the routing logic in `executeCommand()` (lines 110-120) with execution-preference-aware routing:

```java
@PostMapping("/command")
@RequireAuth
public ResponseEntity<AgentCommandResponse> executeCommand(
        @Valid @RequestBody AgentCommandRequest request,
        @AuthUser AuthenticatedUser user) {
    // ... existing spark creation code unchanged ...

    // Get user's execution preference
    UserConfig config = userConfigService.getConfig(user.getUserId());
    ExecutionPreference pref = config.getExecutionPreference() != null
        ? config.getExecutionPreference()
        : ExecutionPreference.DEVICE_FIRST;

    switch (pref) {
        case CLOUD_ONLY:
            // Never try device, go straight to cloud
            return ResponseEntity.ok(executeInCloud(request, user.getUserId(), spark));

        case CLOUD_FIRST:
            // Try cloud first, fall back to device
            AgentCommandResponse cloudResult = executeInCloud(request, user.getUserId(), spark);
            // Cloud always succeeds (even if agent says "I can't"), so just return
            return ResponseEntity.ok(cloudResult);

        case DEVICE_FIRST:
        default:
            // Existing behavior: try device first
            if (deviceRoutingService.hasOnlineDevice(user.getUserId())) {
                Optional<AgentCommandResponse> delegated = delegateToDevice(spark, user.getUserId());
                if (delegated.isPresent()) {
                    return ResponseEntity.ok(delegated.get());
                }
            }
            // Fall back to cloud (now with browser skills available)
            return ResponseEntity.ok(executeInCloud(request, user.getUserId(), spark));
    }
}
```

Add `UserConfigService` to constructor injection.

### AgentSystemPrompt.java changes

Add browser capability context to the system prompt. After the device context section (line 106), add:

```java
// Add browser automation context
prompt.append("\n## Cloud Browser\n");
if (browserEnabled) {
    prompt.append("Cloud browser automation is AVAILABLE. You can navigate websites, ");
    prompt.append("click buttons, fill forms, take screenshots, download/upload files, ");
    prompt.append("and extract data — all from the cloud without a device.\n");
    prompt.append("Use browser_navigate to open a URL, then browser_snapshot or ");
    prompt.append("browser_screenshot to see the page, then interact with browser_click, ");
    prompt.append("browser_type, browser_fill_form, etc.\n");
} else {
    prompt.append("Cloud browser automation is not available.\n");
}
```

Add `@Value("${tacticl.browser.enabled:false}") boolean browserEnabled` field.

### SparkService.java changes

In `markCloudCompleted()`, set `executionMode`:
```java
spark.setExecutionMode(ExecutionMode.CLOUD);
```

If browser session was used, update to `CLOUD_BROWSER`:
```java
public void markCloudCompletedWithBrowser(String sparkId, long totalTokens,
        String modelId, String browserSessionId, long browserMinutesUsed) {
    // ... existing markCloudCompleted logic ...
    spark.setExecutionMode(ExecutionMode.CLOUD_BROWSER);
    spark.setBrowserSessionId(browserSessionId);
    spark.setBrowserMinutesUsed(browserMinutesUsed);
}
```

Commit: `feat: Update routing to respect executionPreference, add browser context to system prompt`

---

## Task 14: Spring Configuration & Feature Flag

**Files:**
- Create/modify: `application/src/main/resources/application.yml` (or `.properties`)

Add browser configuration:
```yaml
tacticl:
  browser:
    enabled: ${BROWSER_ENABLED:false}
    max-concurrent-contexts: 3
    ephemeral-timeout-seconds: 60
    persistent-idle-timeout-seconds: 300
    page-load-timeout-seconds: 30
    max-pages-per-context: 5
```

For QA profile (`application-qa.yml`):
```yaml
tacticl:
  browser:
    enabled: true
```

For prod profile (`application-prod.yml`):
```yaml
tacticl:
  browser:
    enabled: true
```

Commit: `feat: Add browser configuration properties with feature flag`

---

## Task 15: Infrastructure — Docker & Cloud Run Updates

**Files:**
- Modify: `deployment/cloudbuild/cloudbuild-qa.yaml`
- Modify: `deployment/cloudbuild/cloudbuild-prod.yaml`
- Modify or create: `Dockerfile`

### Dockerfile changes
Switch from Alpine to Ubuntu base, install Chromium dependencies:
```dockerfile
FROM eclipse-temurin:21-jre-jammy

# Install Chromium dependencies for Playwright
RUN apt-get update && apt-get install -y \
    libglib2.0-0 libnss3 libnspr4 libdbus-1-3 libatk1.0-0 \
    libatk-bridge2.0-0 libcups2 libdrm2 libxkbcommon0 libatspi2.0-0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 libpango-1.0-0 \
    libcairo2 libasound2 libwayland-client0 \
    && rm -rf /var/lib/apt/lists/*

# Install Playwright browsers at build time
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright
RUN mkdir -p /opt/playwright

COPY build/libs/application-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Cloud Run config updates
QA: memory 4Gi → 6Gi, CPU 2 → 4
Prod: memory 4Gi → 8Gi, CPU 2 → 4, min-instances 1

Commit: `feat: Update Docker and Cloud Run config for Playwright/Chromium`

---

## Task 16: Integration Wiring & Build Verification

**Files:**
- Modify: `business-agent/build.gradle.kts` (ensure data-browser dep)
- Verify: all module compilation

Run full build:
```bash
./gradlew clean build -x test
```

Fix any compilation issues. Ensure:
1. All 13 browser skills register in ToolRegistry (check Spring scan paths)
2. `@ConditionalOnProperty` gates all browser beans when disabled
3. No circular dependencies between modules

If component scanning doesn't pick up `io.tacticl.browser` package:
- Add `@ComponentScan(basePackages = {"io.strategiz.social", "io.tacticl"})` to `TacticlApplication.java`

Commit: `feat: Wire browser modules into application, verify full build`

---

## Dependency Graph

```
Task 1 (scaffolding)
  ├─ Task 2 (entities) ─── Task 3 (repositories)
  ├─ Task 4 (GCS client)
  └─ Task 5 (entity updates)
       │
       ├─ Task 6 (session service) ─── Task 7 (security) ─── Task 8 (logger)
       │    │
       │    ├─ Task 9 (core skills: navigate, snapshot, screenshot)
       │    ├─ Task 10 (interaction skills: click, type, fill_form, select)
       │    ├─ Task 11 (utility skills: scroll, wait, extract)
       │    └─ Task 12 (file skills: download, upload, session_login)
       │
       └─ Task 13 (routing + system prompt)
              │
              Task 14 (config) ─── Task 15 (Docker/Cloud Run) ─── Task 16 (integration)
```

Tasks 2, 4, 5 can run in parallel after Task 1.
Tasks 6-8 can start once Task 2+3+5 are done.
Tasks 9-12 can run in parallel once Task 6-8 are done.
Task 13 can start once Task 5 is done.
Tasks 14-16 are sequential at the end.
