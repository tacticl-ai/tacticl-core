# Tacticl Platform v2 — Foundation, Connection Registry & Device Registry

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all Firestore-backed v1 code with a clean MongoDB-on-Hetzner stack; implement Connection Registry (OAuth integrations) and Device Registry (device pairing) as the first two control-plane modules.

**Architecture:** Approach C — tacticl-core owns all connection tokens and device state in MongoDB; secret values (OAuth tokens) stored in HashiCorp Vault via the existing cidadel `framework-secrets` library. New modules follow the established layered pattern: `data-connections`, `business-connections`, `service-connections`. Old social/PDLC-v1/browser modules are deleted entirely.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Spring Data MongoDB, HashiCorp Vault (cidadel `framework-secrets`), PASETO auth (cidadel `framework-authorization`), JUnit 6, Mockito 5, Gradle 9.4.0 (Kotlin DSL).

**Spec:** `docs/superpowers/specs/` (this repo) + `tacticl-docs/product/platform/2026-04-13-tacticl-platform-v2-sad.md`

**This is Plan A of 2.** Plan B covers Secrets Vault, Spark Orchestration, gRPC, and deployment.

---

## File Map

### DELETE (v1 dead code — entire modules)
```
data/data-social/            — 54 Firestore entity files, Spark/PDLC/social entities
data/data-browser/           — Browser session Firestore persistence
business/business-social/    — Social publishing, scheduling, analytics
business/business-browser/   — Playwright automation for social
service/service-social/      — Social REST controllers
client/client-twitter/       — Twitter/X API v2 client
client/client-linkedin/      — LinkedIn Marketing API client
client/client-instagram/     — Instagram Graph API client
client/client-siliconflow/   — SiliconFlow video generation client
client/client-gcs/           — Google Cloud Storage client
```

### MODIFY (existing files)
```
settings.gradle.kts                                         — Remove deleted modules, add new ones
gradle/libs.versions.toml                                   — Remove firestore version, no other changes
data/build.gradle.kts                                       — Remove google-cloud-firestore, add MongoDB starter
business/build.gradle.kts                                   — Remove firestore transitive dep
application-api/build.gradle.kts                            — Wire in new service-connections module
application-api/src/main/resources/application.properties  — Remove GCP/Firestore, add MongoDB URI
application-api/src/main/java/.../TacticlApplication.java  — Update @SpringBootApplication scan packages
```

### CREATE (new modules)

**`data/data-connections/`**
```
src/main/java/io/tacticl/data/connections/base/BaseMongoEntity.java
src/main/java/io/tacticl/data/connections/entity/Connection.java
src/main/java/io/tacticl/data/connections/entity/ConnectionStatus.java
src/main/java/io/tacticl/data/connections/entity/Device.java
src/main/java/io/tacticl/data/connections/entity/DeviceStatus.java
src/main/java/io/tacticl/data/connections/entity/PairingToken.java
src/main/java/io/tacticl/data/connections/repository/ConnectionRepository.java
src/main/java/io/tacticl/data/connections/repository/DeviceRepository.java
src/main/java/io/tacticl/data/connections/repository/PairingTokenRepository.java
build.gradle.kts
src/test/java/io/tacticl/data/connections/repository/ConnectionRepositoryTest.java
src/test/java/io/tacticl/data/connections/repository/DeviceRepositoryTest.java
```

**`business/business-connections/`**
```
src/main/java/io/tacticl/business/connections/provider/OAuthProvider.java
src/main/java/io/tacticl/business/connections/provider/OAuthTokens.java
src/main/java/io/tacticl/business/connections/provider/GitHubOAuthProvider.java
src/main/java/io/tacticl/business/connections/service/ConnectionRegistryService.java
src/main/java/io/tacticl/business/connections/service/DeviceRegistryService.java
src/main/java/io/tacticl/business/connections/service/DevicePresenceTracker.java
build.gradle.kts
src/test/java/io/tacticl/business/connections/provider/GitHubOAuthProviderTest.java
src/test/java/io/tacticl/business/connections/service/ConnectionRegistryServiceTest.java
src/test/java/io/tacticl/business/connections/service/DeviceRegistryServiceTest.java
```

**`service/service-connections/`**
```
src/main/java/io/tacticl/service/connections/controller/ConnectionController.java
src/main/java/io/tacticl/service/connections/controller/DeviceController.java
src/main/java/io/tacticl/service/connections/dto/ConnectionSummaryDto.java
src/main/java/io/tacticl/service/connections/dto/OAuthCallbackRequestDto.java
src/main/java/io/tacticl/service/connections/dto/PairingTokenResponseDto.java
src/main/java/io/tacticl/service/connections/dto/DeviceSummaryDto.java
build.gradle.kts
src/test/java/io/tacticl/service/connections/controller/ConnectionControllerTest.java
src/test/java/io/tacticl/service/connections/controller/DeviceControllerTest.java
```

---

## Chunk 1: Foundation — Delete Dead Code & Add MongoDB

### Task 1: Delete v1 modules

**Files:** `settings.gradle.kts`, `data/data-social/`, `data/data-browser/`, `business/business-social/`, `business/business-browser/`, `service/service-social/`, `client/client-twitter/`, `client/client-linkedin/`, `client/client-instagram/`, `client/client-siliconflow/`, `client/client-gcs/`

- [ ] **Step 1: Delete dead module directories**

```bash
cd /path/to/tacticl-core
rm -rf data/data-social
rm -rf data/data-browser
rm -rf business/business-social
rm -rf business/business-browser
rm -rf service/service-social
rm -rf client/client-twitter
rm -rf client/client-linkedin
rm -rf client/client-instagram
rm -rf client/client-siliconflow
rm -rf client/client-gcs
```

- [ ] **Step 2: Remove deleted modules from settings.gradle.kts**

Open `settings.gradle.kts`. Remove these lines:
```kotlin
// DELETE these include() lines:
include(":data:data-social")
include(":data:data-browser")
include(":business:business-social")
include(":business:business-browser")
include(":service:service-social")
include(":client:client-twitter")
include(":client:client-linkedin")
include(":client:client-instagram")
include(":client:client-siliconflow")
include(":client:client-gcs")
```

Also add the three new modules at the end:
```kotlin
include(":data:data-connections")
include(":business:business-connections")
include(":service:service-connections")
```

- [ ] **Step 3: Replace data/build.gradle.kts with the complete new content**

The parent `data/build.gradle.kts` applies to all subprojects. Replace the entire file content with:

```kotlin
// data/build.gradle.kts
subprojects {
    dependencies {
        api("org.springframework.boot:spring-boot-starter-data-mongodb")
        implementation(libs.cidadel.framework.exception)
        implementation(libs.cidadel.framework.logging)
    }
}
```

This removes `google-cloud-firestore` and `cidadel.data.framework.base` (Firestore-backed base classes) from the entire data layer. The MongoDB starter is shared by all data submodules via the parent.

- [ ] **Step 4: Remove firestore version from gradle/libs.versions.toml**

Remove the line:
```toml
google-cloud-firestore = "3.28.0"
```

And remove the corresponding library alias block if present. Leave all cidadel entries intact.

- [ ] **Step 5: Remove firestore from business/build.gradle.kts**

If `business/build.gradle.kts` references `google-cloud-firestore` or `cidadel.data.framework.base`, remove those lines. The business layer should not depend on the data layer parent — only on specific data modules via individual module `build.gradle.kts`.

- [ ] **Step 6: Update application-api/build.gradle.kts**

Remove references to deleted modules AND the Firestore data framework base:
```kotlin
// REMOVE any of these:
implementation(project(":service:service-social"))
implementation(project(":business:business-social"))
implementation(libs.cidadel.data.framework.base)   // Firestore-backed — must be removed
// and any other deleted module references
```

Add service-connections:
```kotlin
implementation(project(":service:service-connections"))
```

Also verify that `spring-cloud-gcp-starter-data-firestore` or `firebase-admin` are not in `application-api/build.gradle.kts` — remove if present.

- [ ] **Step 7: Verify the build resolves (no source errors yet)**

```bash
./gradlew :application-api:dependencies --configuration compileClasspath 2>&1 | head -50
```

Expected: Dependency tree resolves without "Could not resolve" errors for deleted modules. Compilation errors in remaining service modules referencing deleted code are expected — these will be resolved in the next steps.

- [ ] **Step 8: Commit the cleanup**

```bash
git add -A
git commit -m "chore: delete v1 social/browser/PDLC Firestore modules, add new module includes"
```

---

### Task 2: Configure MongoDB & Update Application Properties

**Files:** `application-api/src/main/resources/application.properties`, `application-api/src/main/java/.../TacticlApplication.java`

- [ ] **Step 1: Update application.properties**

Replace the Firestore block:
```properties
# REMOVE:
spring.cloud.gcp.firestore.enabled=true
spring.cloud.gcp.project-id=tacticl
```

Add MongoDB:
```properties
# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/tacticlDb
spring.data.mongodb.auto-index-creation=true
```

Also remove any feature flags that referenced deleted modules (twitter, linkedin, instagram, siliconflow, gcs). Keep:
```properties
spring.application.name=tacticl-core
spring.profiles.active=local
server.port=8080

# Vault
spring.cloud.vault.enabled=true
spring.cloud.vault.uri=http://localhost:8200
cidadel.vault.enabled=true
cidadel.vault.context=tacticl

# Auth
cidadel.authorization.access-token-cookie=tacticl-access-token
cidadel.authorization.expected-issuer=cidadel.io
cidadel.authorization.expected-audience=tacticl

# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/tacticlDb
spring.data.mongodb.auto-index-creation=true

# LLM Routing
llm.router.default-model=claude-haiku-4-5
llm.agent.generation-model=claude-sonnet-4-5

# WebSocket CORS
tacticl.websocket.allowed-origins=http://localhost:5173,http://localhost:3000

# Feature flags
tacticl.brave-search.enabled=true
tacticl.jina.enabled=true
tacticl.github.enabled=true
tacticl.google.enabled=true
```

- [ ] **Step 2: Update TacticlApplication.java scan packages**

The main class at `application-api/src/main/java/io/strategiz/social/application/TacticlApplication.java` has a `scanBasePackages` list. Update it to include the new `io.tacticl` package and remove references to deleted packages:

```java
@SpringBootApplication(scanBasePackages = {
    "io.tacticl",                              // All new v2 code
    "io.cidadel.framework",                    // Cidadel framework
    "io.cidadel.client.anthropic",
    "io.cidadel.client.openai",
    "io.cidadel.client.grok",
    "io.cidadel.framework.ai.engine",
    "io.cidadel.business.ai.engine"
})
@EnableScheduling
public class TacticlApplication {
    public static void main(String[] args) {
        SpringApplication.run(TacticlApplication.class, args);
    }
}
```

Note: Remove `io.strategiz.social` from scan packages — old code is deleted.

- [ ] **Step 3: Verify application starts (even if no controllers yet)**

```bash
./gradlew :application-api:bootRun
```

Expected: Spring context starts, logs show MongoDB connection attempt. Will fail if MongoDB isn't running locally — that's acceptable at this stage. The goal is no compile errors.

If MongoDB not running locally, verify with:
```bash
./gradlew :application-api:compileJava
```
Expected: BUILD SUCCESSFUL (0 errors).

- [ ] **Step 4: Commit**

```bash
git add application-api/src/main/resources/application.properties
git add application-api/src/main/java/io/strategiz/social/application/TacticlApplication.java
git commit -m "feat: replace Firestore config with MongoDB, update component scan packages"
```

---

## Chunk 2: Data Layer — Connection, Device & PairingToken Entities

### Task 3: BaseMongoEntity + data-connections build

**Files:** `data/data-connections/build.gradle.kts`, `data/data-connections/src/main/java/io/tacticl/data/connections/base/BaseMongoEntity.java`

- [ ] **Step 1: Create data-connections/build.gradle.kts**

```kotlin
dependencies {
    // MongoDB starter inherited from data/build.gradle.kts parent — do NOT duplicate here
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Testcontainers for embedded MongoDB (Flapdoodle spring31x is incompatible with Spring Boot 4)
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:junit-jupiter")
}
```

Add to `gradle/libs.versions.toml` if not already present:
```toml
[versions]
testcontainers = "1.20.4"

[libraries]
testcontainers-mongodb = { module = "org.testcontainers:mongodb", version.ref = "testcontainers" }
testcontainers-junit = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }
```

Also add to `application-api/src/test/resources/application-test.properties` (create if absent):
```properties
spring.data.mongodb.uri=mongodb://localhost:27017/tacticlDbTest
```

`@DataMongoTest` will auto-configure a Testcontainers MongoDB instance when `@Testcontainers` + `@Container` annotations are present. The `@DataMongoTest` slice works with Testcontainers — no additional `@AutoConfigureDataMongo` needed.

- [ ] **Step 2: Write the test first (BaseMongoEntity persistence)**

```java
// data/data-connections/src/test/java/io/tacticl/data/connections/base/BaseMongoEntityTest.java
package io.tacticl.data.connections.base;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;

@DataMongoTest
@Testcontainers
class BaseMongoEntityTest {

    @Autowired
    MongoTemplate mongoTemplate;

    @Test
    void savedEntity_hasIdAndAuditDates() {
        var entity = new TestEntity("hello");
        var saved = mongoTemplate.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull().isBefore(Instant.now().plusSeconds(1));
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
```

Create a minimal `TestEntity` in the same test package:
```java
import org.springframework.data.mongodb.core.mapping.Document;

@Document("test_entities")
class TestEntity extends BaseMongoEntity {
    private String value;
    TestEntity(String value) { this.value = value; }
    String getValue() { return value; }
}
```

- [ ] **Step 3: Run the test — expect compilation failure**

```bash
./gradlew :data:data-connections:test --tests "*BaseMongoEntityTest" 2>&1 | tail -20
```

Expected: Compilation error — `BaseMongoEntity` does not exist yet.

- [ ] **Step 4: Implement BaseMongoEntity**

```java
// data/data-connections/src/main/java/io/tacticl/data/connections/base/BaseMongoEntity.java
package io.tacticl.data.connections.base;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import java.time.Instant;

public abstract class BaseMongoEntity {

    @Id
    private String id;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

Also create `data/data-connections/src/main/java/io/tacticl/data/connections/config/MongoAuditingConfig.java`:
```java
package io.tacticl.data.connections.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {}
```

- [ ] **Step 5: Run the test — expect PASS**

```bash
./gradlew :data:data-connections:test --tests "*BaseMongoEntityTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 6: Commit**

```bash
git add data/data-connections/
git commit -m "feat(data-connections): add BaseMongoEntity with audit dates, @DataMongoTest passes"
```

---

### Task 4: Connection entity + repository

**Files:** `entity/Connection.java`, `entity/ConnectionStatus.java`, `repository/ConnectionRepository.java`, `ConnectionRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/io/tacticl/data/connections/repository/ConnectionRepositoryTest.java
package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.ConnectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class ConnectionRepositoryTest {

    @Autowired ConnectionRepository repo;

    @BeforeEach
    void clean() { repo.deleteAll(); }

    @Test
    void findByUserId_returnsOnlyUserConnections() {
        repo.save(Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo", "user")));
        repo.save(Connection.create("user1", "SLACK", "vault/path/2", "@alice", List.of("chat")));
        repo.save(Connection.create("user2", "GITHUB", "vault/path/3", "@bob", List.of("repo")));

        var results = repo.findByUserId("user1");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> "user1".equals(c.getUserId()));
    }

    @Test
    void findByUserIdAndProvider_returnsMatchingConnection() {
        repo.save(Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo")));

        var result = repo.findByUserIdAndProvider("user1", "GITHUB");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    void findByUserIdAndProvider_returnsEmpty_whenNotFound() {
        var result = repo.findByUserIdAndProvider("user1", "SLACK");
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
./gradlew :data:data-connections:test --tests "*ConnectionRepositoryTest" 2>&1 | tail -10
```

Expected: Compile error — Connection, ConnectionStatus, ConnectionRepository not found.

- [ ] **Step 3: Implement ConnectionStatus enum**

```java
// src/main/java/io/tacticl/data/connections/entity/ConnectionStatus.java
package io.tacticl.data.connections.entity;

public enum ConnectionStatus { CONNECTED, EXPIRED, ERROR }
```

- [ ] **Step 4: Implement Connection entity**

```java
// src/main/java/io/tacticl/data/connections/entity/Connection.java
package io.tacticl.data.connections.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Document("connections")
@CompoundIndex(def = "{'userId': 1, 'provider': 1}", unique = true)
public class Connection extends BaseMongoEntity {

    @Indexed
    private String userId;
    private String provider;
    private ConnectionStatus status;
    private String accountIdentity;
    private String vaultPath;
    private List<String> scopes;
    private Instant tokenExpiresAt;
    private Instant lastRefreshedAt;

    public static Connection create(String userId, String provider, String vaultPath,
                                    String accountIdentity, List<String> scopes) {
        var c = new Connection();
        c.userId = userId;
        c.provider = provider;
        c.status = ConnectionStatus.CONNECTED;
        c.accountIdentity = accountIdentity;
        c.vaultPath = vaultPath;
        c.scopes = scopes;
        return c;
    }

    public void markExpired() { this.status = ConnectionStatus.EXPIRED; }
    public void markError() { this.status = ConnectionStatus.ERROR; }
    public void markConnected(Instant expiresAt) {
        this.status = ConnectionStatus.CONNECTED;
        this.tokenExpiresAt = expiresAt;
        this.lastRefreshedAt = Instant.now();
    }

    // Getters
    public String getUserId() { return userId; }
    public String getProvider() { return provider; }
    public ConnectionStatus getStatus() { return status; }
    public String getAccountIdentity() { return accountIdentity; }
    public String getVaultPath() { return vaultPath; }
    public List<String> getScopes() { return scopes; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public Instant getLastRefreshedAt() { return lastRefreshedAt; }
}
```

- [ ] **Step 5: Implement ConnectionRepository**

```java
// src/main/java/io/tacticl/data/connections/repository/ConnectionRepository.java
package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.Connection;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends MongoRepository<Connection, String> {
    List<Connection> findByUserId(String userId);
    Optional<Connection> findByUserIdAndProvider(String userId, String provider);
    void deleteByUserIdAndProvider(String userId, String provider);
}
```

- [ ] **Step 6: Run — expect PASS**

```bash
./gradlew :data:data-connections:test --tests "*ConnectionRepositoryTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 7: Commit**

```bash
git add data/data-connections/src/
git commit -m "feat(data-connections): add Connection entity and ConnectionRepository"
```

---

### Task 5: Device entity + repository + PairingToken

**Files:** `entity/Device.java`, `entity/DeviceStatus.java`, `entity/PairingToken.java`, `repository/DeviceRepository.java`, `repository/PairingTokenRepository.java`, `DeviceRepositoryTest.java`

- [ ] **Step 1: Write failing tests**

```java
// src/test/java/io/tacticl/data/connections/repository/DeviceRepositoryTest.java
package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.Device;
import io.tacticl.data.connections.entity.DeviceStatus;
import io.tacticl.data.connections.entity.PairingToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class DeviceRepositoryTest {

    @Autowired DeviceRepository deviceRepo;
    @Autowired PairingTokenRepository tokenRepo;

    @BeforeEach
    void clean() { deviceRepo.deleteAll(); tokenRepo.deleteAll(); }

    @Test
    void findByUserId_returnsDevices() {
        deviceRepo.save(Device.create("user1", "My Mac", "MACOS", "1.0.0", List.of("CLAUDE_CODE")));
        deviceRepo.save(Device.create("user2", "Work PC", "WINDOWS", "1.0.0", List.of("TERMINAL")));

        assertThat(deviceRepo.findByUserId("user1")).hasSize(1);
    }

    @Test
    void pairingToken_unusedAndNotExpired_foundByToken() {
        var token = PairingToken.create("user1", "tctl_pair_v1_abc123",
            Instant.now().plusSeconds(900));
        tokenRepo.save(token);

        var found = tokenRepo.findByTokenAndUsedFalseAndExpiresAtAfter("tctl_pair_v1_abc123", Instant.now());

        assertThat(found).isPresent();
    }

    @Test
    void pairingToken_expired_notFound() {
        var token = PairingToken.create("user1", "tctl_pair_v1_expired",
            Instant.now().minusSeconds(60));
        tokenRepo.save(token);

        var found = tokenRepo.findByTokenAndUsedFalseAndExpiresAtAfter("tctl_pair_v1_expired", Instant.now());

        assertThat(found).isEmpty();
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew :data:data-connections:test --tests "*DeviceRepositoryTest" 2>&1 | tail -10
```

- [ ] **Step 3: Implement DeviceStatus enum**

```java
// entity/DeviceStatus.java
package io.tacticl.data.connections.entity;
public enum DeviceStatus { ONLINE, OFFLINE }
```

- [ ] **Step 4: Implement Device entity**

```java
// entity/Device.java
package io.tacticl.data.connections.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Document("devices")
public class Device extends BaseMongoEntity {

    @Indexed
    private String userId;
    private String name;
    private String os;
    private String agentVersion;
    private List<String> capabilities;
    private DeviceStatus status;
    private Instant lastSeenAt;
    private DeviceSettings settings;

    // Embedded settings document (matches SAD §3.3 schema)
    public static class DeviceSettings {
        private String executionEngine = "AUTO";  // CLAUDE_CODE | LEGACY | AUTO
        private int maxDaemons = 3;
        private boolean autoWake = false;

        public String getExecutionEngine() { return executionEngine; }
        public void setExecutionEngine(String executionEngine) { this.executionEngine = executionEngine; }
        public int getMaxDaemons() { return maxDaemons; }
        public void setMaxDaemons(int maxDaemons) { this.maxDaemons = maxDaemons; }
        public boolean isAutoWake() { return autoWake; }
        public void setAutoWake(boolean autoWake) { this.autoWake = autoWake; }
    }

    public static Device create(String userId, String name, String os,
                                 String agentVersion, List<String> capabilities) {
        var d = new Device();
        d.userId = userId;
        d.name = name;
        d.os = os;
        d.agentVersion = agentVersion;
        d.capabilities = capabilities;
        d.status = DeviceStatus.OFFLINE;
        d.settings = new DeviceSettings();
        return d;
    }

    public void markOnline() { this.status = DeviceStatus.ONLINE; this.lastSeenAt = Instant.now(); }
    public void markOffline() { this.status = DeviceStatus.OFFLINE; }
    public void setName(String name) { this.name = name; }
    public void setSettings(DeviceSettings settings) { this.settings = settings; }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getOs() { return os; }
    public String getAgentVersion() { return agentVersion; }
    public List<String> getCapabilities() { return capabilities; }
    public DeviceStatus getStatus() { return status; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public DeviceSettings getSettings() { return settings; }
}
```

- [ ] **Step 5: Implement PairingToken entity**

```java
// entity/PairingToken.java
package io.tacticl.data.connections.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("pairing_tokens")
public class PairingToken extends BaseMongoEntity {

    @Indexed(expireAfter = "0s")   // TTL index — MongoDB auto-deletes expired docs
    private Instant expiresAt;

    private String userId;
    private String token;
    private boolean used;

    public static PairingToken create(String userId, String token, Instant expiresAt) {
        var pt = new PairingToken();
        pt.userId = userId;
        pt.token = token;
        pt.expiresAt = expiresAt;
        pt.used = false;
        return pt;
    }

    public void markUsed() { this.used = true; }

    public String getUserId() { return userId; }
    public String getToken() { return token; }
    public boolean isUsed() { return used; }
    public Instant getExpiresAt() { return expiresAt; }
}
```

- [ ] **Step 6: Implement DeviceRepository and PairingTokenRepository**

```java
// repository/DeviceRepository.java
package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.Device;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface DeviceRepository extends MongoRepository<Device, String> {
    List<Device> findByUserId(String userId);
}
```

```java
// repository/PairingTokenRepository.java
package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.PairingToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.Instant;
import java.util.Optional;

public interface PairingTokenRepository extends MongoRepository<PairingToken, String> {
    Optional<PairingToken> findByTokenAndUsedFalseAndExpiresAtAfter(String token, Instant now);
}
```

- [ ] **Step 7: Run — expect PASS**

```bash
./gradlew :data:data-connections:test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests passed.

- [ ] **Step 8: Commit**

```bash
git add data/data-connections/src/
git commit -m "feat(data-connections): add Device, PairingToken entities and repositories"
```

---

## Chunk 3: Business Layer — OAuth Service & Connection Registry

### Task 6: OAuthProvider interface + GitHubOAuthProvider

**Files:** `business/business-connections/build.gradle.kts`, `provider/OAuthProvider.java`, `provider/OAuthTokens.java`, `provider/GitHubOAuthProvider.java`, `GitHubOAuthProviderTest.java`

- [ ] **Step 1: Create business/business-connections/build.gradle.kts**

```kotlin
dependencies {
    implementation(project(":data:data-connections"))
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.framework.secrets)   // VaultClient
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.mockito.core)
}
```

- [ ] **Step 2: Write the failing test for GitHubOAuthProvider**

```java
// src/test/java/io/tacticl/business/connections/provider/GitHubOAuthProviderTest.java
package io.tacticl.business.connections.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GitHubOAuthProviderTest {

    private GitHubOAuthProvider provider;

    @BeforeEach
    void setUp() {
        // RestClient mock — not used in auth URL test
        provider = new GitHubOAuthProvider("test-client-id", "test-client-secret");
    }

    @Test
    void generateAuthUrl_containsClientIdAndState() {
        String url = provider.generateAuthUrl("state-abc", "https://app.tacticl.ai/callback");

        assertThat(url)
            .contains("github.com/login/oauth/authorize")
            .contains("client_id=test-client-id")
            .contains("state=state-abc")
            .contains("scope=repo+user")
            .contains("redirect_uri=");
    }

    @Test
    void getType_returnsGitHub() {
        assertThat(provider.getType()).isEqualTo(OAuthProvider.Type.GITHUB);
    }
}
```

- [ ] **Step 3: Run — expect compile failure**

```bash
./gradlew :business:business-connections:test --tests "*GitHubOAuthProviderTest" 2>&1 | tail -10
```

- [ ] **Step 4: Implement OAuthTokens value record**

```java
// src/main/java/io/tacticl/business/connections/provider/OAuthTokens.java
package io.tacticl.business.connections.provider;

import java.time.Instant;

public record OAuthTokens(
    String accessToken,
    String refreshToken,   // null if provider doesn't issue refresh tokens
    Instant expiresAt,
    String accountIdentity
) {}
```

- [ ] **Step 5: Implement OAuthProvider interface**

```java
// src/main/java/io/tacticl/business/connections/provider/OAuthProvider.java
package io.tacticl.business.connections.provider;

public interface OAuthProvider {

    Type getType();
    String generateAuthUrl(String state, String redirectUri);
    OAuthTokens exchangeCode(String code, String redirectUri);
    OAuthTokens refreshToken(String refreshToken);

    enum Type {
        GITHUB, INSTAGRAM, TWITTER, LINKEDIN, SLACK,
        GOOGLE_PHOTOS, GOOGLE_DRIVE, DROPBOX
    }
}
```

- [ ] **Step 6: Implement GitHubOAuthProvider**

```java
// src/main/java/io/tacticl/business/connections/provider/GitHubOAuthProvider.java
package io.tacticl.business.connections.provider;

import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.Instant;
import java.util.Map;

public class GitHubOAuthProvider implements OAuthProvider {

    private static final String AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;

    public GitHubOAuthProvider(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = RestClient.create();
    }

    @Override
    public Type getType() { return Type.GITHUB; }

    @Override
    public String generateAuthUrl(String state, String redirectUri) {
        // UriComponentsBuilder handles URL encoding automatically — do NOT manually encode
        return UriComponentsBuilder.fromUriString(AUTH_URL)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", "repo user")
            .queryParam("state", state)
            .build().toUriString();
    }

    @Override
    public OAuthTokens exchangeCode(String code, String redirectUri) {
        var response = restClient.post()
            .uri(TOKEN_URL)
            .header("Accept", "application/json")
            .body(Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "redirect_uri", redirectUri
            ))
            .retrieve()
            .body(Map.class);

        var accessToken = (String) response.get("access_token");
        var identity = fetchAccountIdentity(accessToken);

        // GitHub access tokens don't expire (no expiry or refresh token)
        return new OAuthTokens(accessToken, null, null, identity);
    }

    @Override
    public OAuthTokens refreshToken(String refreshToken) {
        // GitHub classic tokens don't expire — no-op
        throw new UnsupportedOperationException("GitHub tokens do not support refresh");
    }

    private String fetchAccountIdentity(String accessToken) {
        var user = restClient.get()
            .uri(USER_URL)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(Map.class);
        return "@" + user.get("login");
    }
}
```

- [ ] **Step 7: Run — expect PASS**

```bash
./gradlew :business:business-connections:test --tests "*GitHubOAuthProviderTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 8: Commit**

```bash
git add business/business-connections/
git commit -m "feat(business-connections): add OAuthProvider interface and GitHubOAuthProvider"
```

---

### Task 7: ConnectionRegistryService

**Files:** `service/ConnectionRegistryService.java`, `ConnectionRegistryServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/io/tacticl/business/connections/service/ConnectionRegistryServiceTest.java
package io.tacticl.business.connections.service;

import io.tacticl.business.connections.provider.OAuthProvider;
import io.tacticl.business.connections.provider.OAuthTokens;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.ConnectionStatus;
import io.tacticl.data.connections.repository.ConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionRegistryServiceTest {

    @Mock ConnectionRepository connectionRepository;
    @Mock OAuthProvider githubProvider;
    @Mock VaultTokenStore vaultTokenStore;

    private ConnectionRegistryService service;

    @BeforeEach
    void setUp() {
        when(githubProvider.getType()).thenReturn(OAuthProvider.Type.GITHUB);
        service = new ConnectionRegistryService(
            List.of(githubProvider), connectionRepository, vaultTokenStore);
    }

    @Test
    void generateAuthUrl_delegatesToProvider() {
        when(githubProvider.generateAuthUrl("state-1", "https://app/cb"))
            .thenReturn("https://github.com/auth?state=state-1");

        var url = service.generateAuthUrl("GITHUB", "state-1", "https://app/cb");

        assertThat(url).contains("github.com");
    }

    @Test
    void generateAuthUrl_unknownProvider_throws() {
        assertThatThrownBy(() -> service.generateAuthUrl("SLACK", "state", "uri"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SLACK");
    }

    @Test
    void handleCallback_savesConnectionAndStoresToken() {
        var tokens = new OAuthTokens("access-token", null, null, "@alice");
        when(githubProvider.exchangeCode("code-xyz", "https://app/cb")).thenReturn(tokens);
        when(connectionRepository.findByUserIdAndProvider("user1", "GITHUB")).thenReturn(Optional.empty());
        when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var connection = service.handleCallback("user1", "GITHUB", "code-xyz", "https://app/cb");

        assertThat(connection.getAccountIdentity()).isEqualTo("@alice");
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        verify(vaultTokenStore).store(eq("user1"), anyString(), eq(tokens));
        verify(connectionRepository).save(any(Connection.class));
    }

    @Test
    void disconnect_deletesConnectionAndRevokesToken() {
        var connection = Connection.create("user1", "GITHUB", "vault/path", "@alice", List.of("repo"));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection));

        service.disconnect("user1", "conn-1");

        verify(vaultTokenStore).revoke("vault/path");
        verify(connectionRepository).delete(connection);
    }

    @Test
    void disconnect_wrongUser_throws() {
        var connection = Connection.create("user2", "GITHUB", "vault/path", "@alice", List.of("repo"));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.disconnect("user1", "conn-1"))
            .isInstanceOf(SecurityException.class);
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew :business:business-connections:test --tests "*ConnectionRegistryServiceTest" 2>&1 | tail -10
```

- [ ] **Step 3: Implement VaultTokenStore interface**

```java
// src/main/java/io/tacticl/business/connections/service/VaultTokenStore.java
package io.tacticl.business.connections.service;

import io.tacticl.business.connections.provider.OAuthTokens;

public interface VaultTokenStore {
    void store(String userId, String connectionId, OAuthTokens tokens);
    OAuthTokens retrieve(String vaultPath);
    void revoke(String vaultPath);
}
```

- [ ] **Step 4: Implement ConnectionRegistryService**

```java
// src/main/java/io/tacticl/business/connections/service/ConnectionRegistryService.java
package io.tacticl.business.connections.service;

import io.tacticl.business.connections.provider.OAuthProvider;
import io.tacticl.business.connections.provider.OAuthTokens;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.repository.ConnectionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class ConnectionRegistryService {

    private final Map<String, OAuthProvider> providers;
    private final ConnectionRepository connectionRepository;
    private final VaultTokenStore vaultTokenStore;

    public ConnectionRegistryService(List<OAuthProvider> providers,
                                     ConnectionRepository connectionRepository,
                                     VaultTokenStore vaultTokenStore) {
        this.providers = providers.stream()
            .collect(Collectors.toMap(p -> p.getType().name(), Function.identity()));
        this.connectionRepository = connectionRepository;
        this.vaultTokenStore = vaultTokenStore;
    }

    public String generateAuthUrl(String provider, String state, String redirectUri) {
        return resolveProvider(provider).generateAuthUrl(state, redirectUri);
    }

    public Connection handleCallback(String userId, String providerName,
                                     String code, String redirectUri) {
        var provider = resolveProvider(providerName);
        var tokens = provider.exchangeCode(code, redirectUri);

        var connection = connectionRepository.findByUserIdAndProvider(userId, providerName)
            .orElseGet(() -> Connection.create(userId, providerName,
                buildVaultPath(userId, providerName), tokens.accountIdentity(),
                List.of()));

        var connectionId = connection.getId() != null ? connection.getId() : UUID.randomUUID().toString();
        vaultTokenStore.store(userId, connectionId, tokens);

        if (tokens.expiresAt() != null) {
            connection.markConnected(tokens.expiresAt());
        }
        return connectionRepository.save(connection);
    }

    public List<Connection> listConnections(String userId) {
        return connectionRepository.findByUserId(userId);
    }

    public Connection getConnection(String userId, String connectionId) {
        var connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        if (!connection.getUserId().equals(userId)) {
            throw new SecurityException("Connection does not belong to user");
        }
        return connection;
    }

    public void disconnect(String userId, String connectionId) {
        var connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));

        if (!connection.getUserId().equals(userId)) {
            throw new SecurityException("Connection does not belong to user");
        }

        vaultTokenStore.revoke(connection.getVaultPath());
        connectionRepository.delete(connection);
    }

    private OAuthProvider resolveProvider(String provider) {
        var p = providers.get(provider);
        if (p == null) {
            throw new IllegalArgumentException("Unknown OAuth provider: " + provider);
        }
        return p;
    }

    private String buildVaultPath(String userId, String provider) {
        return String.format("tacticl/%s/connections/%s", userId, provider.toLowerCase());
    }
}
```

- [ ] **Step 5: Run — expect PASS**

```bash
./gradlew :business:business-connections:test --tests "*ConnectionRegistryServiceTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 6: Commit**

```bash
git add business/business-connections/src/
git commit -m "feat(business-connections): add ConnectionRegistryService with OAuth flow and Vault token storage"
```

---

### Task 7.5: VaultTokenStoreImpl + ConnectionsConfig (Spring wiring)

**Files:** `service/VaultTokenStoreImpl.java`, `config/ConnectionsConfig.java`

The `VaultTokenStore` interface needs a concrete implementation and a `@Configuration` class that wires all beans including `GitHubOAuthProvider` with credentials from Vault.

- [ ] **Step 1: Implement VaultTokenStoreImpl**

```java
// src/main/java/io/tacticl/business/connections/service/VaultTokenStoreImpl.java
package io.tacticl.business.connections.service;

import io.cidadel.framework.secrets.VaultClient;
import io.tacticl.business.connections.provider.OAuthTokens;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;

@Component
public class VaultTokenStoreImpl implements VaultTokenStore {

    private final VaultClient vaultClient;

    public VaultTokenStoreImpl(VaultClient vaultClient) {
        this.vaultClient = vaultClient;
    }

    @Override
    public void store(String userId, String connectionId, OAuthTokens tokens) {
        vaultClient.write(buildPath(userId, connectionId), Map.of(
            "accessToken", tokens.accessToken(),
            "refreshToken", tokens.refreshToken() != null ? tokens.refreshToken() : "",
            "expiresAt", tokens.expiresAt() != null ? tokens.expiresAt().toString() : ""
        ));
    }

    @Override
    public OAuthTokens retrieve(String vaultPath) {
        var data = vaultClient.read(vaultPath);
        return new OAuthTokens(
            (String) data.get("accessToken"),
            (String) data.get("refreshToken"),
            data.containsKey("expiresAt") && !((String) data.get("expiresAt")).isEmpty()
                ? Instant.parse((String) data.get("expiresAt")) : null,
            null
        );
    }

    @Override
    public void revoke(String vaultPath) {
        vaultClient.delete(vaultPath);
    }

    private String buildPath(String userId, String connectionId) {
        return String.format("tacticl/%s/connections/%s", userId, connectionId);
    }
}
```

Note: Verify exact method signatures on cidadel's `VaultClient` — adjust `write`/`read`/`delete` names if the cidadel API differs (e.g., `put`/`get`/`remove`).

- [ ] **Step 2: Create ConnectionsConfig @Configuration**

```java
// src/main/java/io/tacticl/business/connections/config/ConnectionsConfig.java
package io.tacticl.business.connections.config;

import io.tacticl.business.connections.provider.GitHubOAuthProvider;
import io.tacticl.business.connections.provider.OAuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConnectionsConfig {

    @Bean
    public OAuthProvider gitHubOAuthProvider(
            @Value("${tacticl.github.client-id}") String clientId,
            @Value("${tacticl.github.client-secret}") String clientSecret) {
        return new GitHubOAuthProvider(clientId, clientSecret);
    }
}
```

Add to `application.properties`:
```properties
tacticl.github.client-id=${GITHUB_CLIENT_ID:placeholder}
tacticl.github.client-secret=${GITHUB_CLIENT_SECRET:placeholder}
```

Add secrets to Vault at path `tacticl/github`: keys `client-id` and `client-secret`.

- [ ] **Step 3: Run full business-connections tests**

```bash
./gradlew :business:business-connections:test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add business/business-connections/src/ application-api/src/main/resources/application.properties
git commit -m "feat(business-connections): add VaultTokenStoreImpl and ConnectionsConfig Spring wiring"
```

---

### Task 8: DeviceRegistryService

**Files:** `service/DeviceRegistryService.java`, `DeviceRegistryServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/io/tacticl/business/connections/service/DeviceRegistryServiceTest.java
package io.tacticl.business.connections.service;

import io.tacticl.data.connections.entity.Device;
import io.tacticl.data.connections.entity.PairingToken;
import io.tacticl.data.connections.repository.DeviceRepository;
import io.tacticl.data.connections.repository.PairingTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceRegistryServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock PairingTokenRepository pairingTokenRepository;
    @InjectMocks DeviceRegistryService service;

    @Test
    void generatePairingToken_returnsTctlPrefixedToken() {
        when(pairingTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.generatePairingToken("user1");

        assertThat(result.token()).startsWith("tctl_pair_v1_");
        assertThat(result.expiresAt()).isAfter(Instant.now());
        verify(pairingTokenRepository).save(any(PairingToken.class));
    }

    @Test
    void completePairing_validToken_createsDevice() {
        var token = PairingToken.create("user1", "tctl_pair_v1_abc",
            Instant.now().plusSeconds(900));
        when(pairingTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(
            eq("tctl_pair_v1_abc"), any())).thenReturn(Optional.of(token));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var device = service.completePairing("tctl_pair_v1_abc", "My Mac", "MACOS",
            "1.0.0", java.util.List.of("CLAUDE_CODE"));

        assertThat(device.getUserId()).isEqualTo("user1");
        assertThat(device.getName()).isEqualTo("My Mac");
        verify(pairingTokenRepository).save(argThat(PairingToken::isUsed));
    }

    @Test
    void completePairing_invalidToken_throws() {
        when(pairingTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completePairing("bad-token", "Mac", "MACOS", "1.0", java.util.List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or expired pairing token");
    }

    @Test
    void unpair_wrongUser_throws() {
        var device = Device.create("user2", "Mac", "MACOS", "1.0", java.util.List.of());
        when(deviceRepository.findById("dev-1")).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> service.unpair("user1", "dev-1"))
            .isInstanceOf(SecurityException.class);
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew :business:business-connections:test --tests "*DeviceRegistryServiceTest" 2>&1 | tail -10
```

- [ ] **Step 3: Implement DeviceRegistryService**

```java
// src/main/java/io/tacticl/business/connections/service/DeviceRegistryService.java
package io.tacticl.business.connections.service;

import io.tacticl.data.connections.entity.Device;
import io.tacticl.data.connections.entity.PairingToken;
import io.tacticl.data.connections.repository.DeviceRepository;
import io.tacticl.data.connections.repository.PairingTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class DeviceRegistryService {

    private static final long PAIRING_TTL_SECONDS = 900; // 15 minutes

    private final DeviceRepository deviceRepository;
    private final PairingTokenRepository pairingTokenRepository;

    public DeviceRegistryService(DeviceRepository deviceRepository,
                                  PairingTokenRepository pairingTokenRepository) {
        this.deviceRepository = deviceRepository;
        this.pairingTokenRepository = pairingTokenRepository;
    }

    public record PairingTokenResult(String token, Instant expiresAt) {}

    public PairingTokenResult generatePairingToken(String userId) {
        var rawToken = "tctl_pair_v1_" + UUID.randomUUID().toString().replace("-", "");
        var expiresAt = Instant.now().plusSeconds(PAIRING_TTL_SECONDS);
        var token = PairingToken.create(userId, rawToken, expiresAt);
        pairingTokenRepository.save(token);
        return new PairingTokenResult(rawToken, expiresAt);
    }

    public Device completePairing(String rawToken, String name, String os,
                                   String agentVersion, List<String> capabilities) {
        var pairingToken = pairingTokenRepository
            .findByTokenAndUsedFalseAndExpiresAtAfter(rawToken, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired pairing token"));

        pairingToken.markUsed();
        pairingTokenRepository.save(pairingToken);

        var device = Device.create(pairingToken.getUserId(), name, os, agentVersion, capabilities);
        return deviceRepository.save(device);
    }

    public List<Device> listDevices(String userId) {
        return deviceRepository.findByUserId(userId);
    }

    public void unpair(String userId, String deviceId) {
        var device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (!device.getUserId().equals(userId)) {
            throw new SecurityException("Device does not belong to user");
        }

        deviceRepository.delete(device);
    }

    public Device rename(String userId, String deviceId, String newName) {
        var device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (!device.getUserId().equals(userId)) {
            throw new SecurityException("Device does not belong to user");
        }

        device.setName(newName);
        return deviceRepository.save(device);
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :business:business-connections:test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests passed.

- [ ] **Step 5: Commit**

```bash
git add business/business-connections/src/
git commit -m "feat(business-connections): add DeviceRegistryService with pairing token flow"
```

---

## Chunk 4: Service Layer — REST Controllers

### Task 9: ConnectionController

**Files:** `service/service-connections/build.gradle.kts`, all DTOs, `ConnectionController.java`, `ConnectionControllerTest.java`

- [ ] **Step 1: Create service/service-connections/build.gradle.kts**

```kotlin
dependencies {
    implementation(project(":business:business-connections"))
    implementation(project(":data:data-connections"))
    implementation(libs.cidadel.framework.authorization)
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.logging)
    implementation(libs.cidadel.service.framework.base)
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.mockito.core)
}
```

- [ ] **Step 2: Create DTOs**

```java
// dto/OAuthUrlResponseDto.java
package io.tacticl.service.connections.dto;
public record OAuthUrlResponseDto(String authUrl, String state) {}

// dto/OAuthCallbackRequestDto.java
public record OAuthCallbackRequestDto(String code, String state, String redirectUri) {}

// dto/ConnectionSummaryDto.java
public record ConnectionSummaryDto(
    String connectionId, String provider, String status,
    String accountIdentity, String lastRefreshedAt) {}

// dto/PairingTokenResponseDto.java
public record PairingTokenResponseDto(String token, String expiresAt) {}

// dto/DeviceSummaryDto.java
public record DeviceSummaryDto(
    String deviceId, String name, String os, String status,
    java.util.List<String> capabilities, String lastSeenAt) {}
```

- [ ] **Step 3: Write the failing controller test**

```java
// src/test/java/io/tacticl/service/connections/controller/ConnectionControllerTest.java
package io.tacticl.service.connections.controller;

import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.ConnectionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Exclude SecurityAutoConfiguration — PASETO filter is tested in integration tests, not here
@WebMvcTest(value = ConnectionController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class ConnectionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ConnectionRegistryService connectionRegistryService;

    @Test
    void getConnections_returns200WithList() throws Exception {
        var conn = Connection.create("user1", "GITHUB", "vault/path", "@alice", List.of("repo"));
        when(connectionRegistryService.listConnections("user1")).thenReturn(List.of(conn));

        mvc.perform(get("/v1/connections").header("X-User-Id", "user1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].provider").value("GITHUB"))
            .andExpect(jsonPath("$[0].accountIdentity").value("@alice"));
    }

    @Test
    void getOAuthUrl_returns200WithUrl() throws Exception {
        when(connectionRegistryService.generateAuthUrl(eq("GITHUB"), anyString(), anyString()))
            .thenReturn("https://github.com/auth");

        mvc.perform(get("/v1/connections/oauth/GITHUB/url")
                .param("redirectUri", "https://app/cb")
                .header("X-User-Id", "user1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authUrl").value("https://github.com/auth"));
    }

    @Test
    void deleteConnection_returns204() throws Exception {
        mvc.perform(delete("/v1/connections/conn-1").header("X-User-Id", "user1"))
            .andExpect(status().isNoContent());

        verify(connectionRegistryService).disconnect("user1", "conn-1");
    }
}
```

- [ ] **Step 4: Run — expect compile failure**

```bash
./gradlew :service:service-connections:test --tests "*ConnectionControllerTest" 2>&1 | tail -10
```

- [ ] **Step 5: Implement ConnectionController**

```java
// src/main/java/io/tacticl/service/connections/controller/ConnectionController.java
package io.tacticl.service.connections.controller;

import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.service.connections.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/connections")
public class ConnectionController {

    private final ConnectionRegistryService connectionRegistryService;

    public ConnectionController(ConnectionRegistryService connectionRegistryService) {
        this.connectionRegistryService = connectionRegistryService;
    }

    @GetMapping
    public ResponseEntity<List<ConnectionSummaryDto>> listConnections(
            @RequestHeader("X-User-Id") String userId) {
        var connections = connectionRegistryService.listConnections(userId).stream()
            .map(c -> new ConnectionSummaryDto(
                c.getId(), c.getProvider(), c.getStatus().name(),
                c.getAccountIdentity(),
                c.getLastRefreshedAt() != null ? c.getLastRefreshedAt().toString() : null))
            .toList();
        return ResponseEntity.ok(connections);
    }

    @GetMapping("/oauth/{provider}/url")
    public ResponseEntity<OAuthUrlResponseDto> generateAuthUrl(
            @PathVariable String provider,
            @RequestParam String redirectUri,
            @RequestHeader("X-User-Id") String userId) {
        var state = UUID.randomUUID().toString();
        var url = connectionRegistryService.generateAuthUrl(provider, state, redirectUri);
        return ResponseEntity.ok(new OAuthUrlResponseDto(url, state));
    }

    @PostMapping("/oauth/{provider}/callback")
    public ResponseEntity<ConnectionSummaryDto> handleCallback(
            @PathVariable String provider,
            @RequestBody OAuthCallbackRequestDto request,
            @RequestHeader("X-User-Id") String userId) {
        var connection = connectionRegistryService.handleCallback(
            userId, provider, request.code(), request.redirectUri());
        return ResponseEntity.ok(new ConnectionSummaryDto(
            connection.getId(), connection.getProvider(), connection.getStatus().name(),
            connection.getAccountIdentity(), null));
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<ConnectionSummaryDto> getConnection(
            @PathVariable String connectionId,
            @RequestHeader("X-User-Id") String userId) {
        var c = connectionRegistryService.getConnection(userId, connectionId);
        return ResponseEntity.ok(new ConnectionSummaryDto(
            c.getId(), c.getProvider(), c.getStatus().name(),
            c.getAccountIdentity(),
            c.getLastRefreshedAt() != null ? c.getLastRefreshedAt().toString() : null));
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> disconnect(
            @PathVariable String connectionId,
            @RequestHeader("X-User-Id") String userId) {
        connectionRegistryService.disconnect(userId, connectionId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 6: Run — expect PASS**

```bash
./gradlew :service:service-connections:test --tests "*ConnectionControllerTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 7: Commit**

```bash
git add service/service-connections/
git commit -m "feat(service-connections): add ConnectionController with OAuth URL, callback, and disconnect endpoints"
```

---

### Task 10: DeviceController

**Files:** `DeviceController.java`, `DeviceControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/io/tacticl/service/connections/controller/DeviceControllerTest.java
package io.tacticl.service.connections.controller;

import io.tacticl.business.connections.service.DeviceRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = DeviceController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class DeviceControllerTest {

    @Autowired MockMvc mvc;
    @MockBean DeviceRegistryService deviceRegistryService;

    @Test
    void postPair_returns200WithToken() throws Exception {
        var result = new DeviceRegistryService.PairingTokenResult(
            "tctl_pair_v1_abc", Instant.now().plusSeconds(900));
        when(deviceRegistryService.generatePairingToken("user1")).thenReturn(result);

        mvc.perform(post("/v1/connections/devices/pair").header("X-User-Id", "user1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("tctl_pair_v1_abc"));
    }

    @Test
    void getDevices_returns200() throws Exception {
        when(deviceRegistryService.listDevices("user1")).thenReturn(List.of());

        mvc.perform(get("/v1/connections/devices").header("X-User-Id", "user1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void deleteDevice_returns204() throws Exception {
        mvc.perform(delete("/v1/connections/devices/dev-1").header("X-User-Id", "user1"))
            .andExpect(status().isNoContent());

        verify(deviceRegistryService).unpair("user1", "dev-1");
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew :service:service-connections:test --tests "*DeviceControllerTest" 2>&1 | tail -10
```

- [ ] **Step 3: Implement DeviceController**

```java
// src/main/java/io/tacticl/service/connections/controller/DeviceController.java
package io.tacticl.service.connections.controller;

import io.tacticl.business.connections.service.DeviceRegistryService;
import io.tacticl.service.connections.dto.DeviceSummaryDto;
import io.tacticl.service.connections.dto.PairingTokenResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/connections/devices")
public class DeviceController {

    private final DeviceRegistryService deviceRegistryService;

    public DeviceController(DeviceRegistryService deviceRegistryService) {
        this.deviceRegistryService = deviceRegistryService;
    }

    @PostMapping("/pair")
    public ResponseEntity<PairingTokenResponseDto> generatePairingToken(
            @RequestHeader("X-User-Id") String userId) {
        var result = deviceRegistryService.generatePairingToken(userId);
        return ResponseEntity.ok(new PairingTokenResponseDto(
            result.token(), result.expiresAt().toString()));
    }

    @GetMapping
    public ResponseEntity<List<DeviceSummaryDto>> listDevices(
            @RequestHeader("X-User-Id") String userId) {
        var devices = deviceRegistryService.listDevices(userId).stream()
            .map(d -> new DeviceSummaryDto(
                d.getId(), d.getName(), d.getOs(), d.getStatus().name(),
                d.getCapabilities(),
                d.getLastSeenAt() != null ? d.getLastSeenAt().toString() : null))
            .toList();
        return ResponseEntity.ok(devices);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> unpair(
            @PathVariable String deviceId,
            @RequestHeader("X-User-Id") String userId) {
        deviceRegistryService.unpair(userId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<DeviceSummaryDto> rename(
            @PathVariable String deviceId,
            @RequestBody java.util.Map<String, String> body,
            @RequestHeader("X-User-Id") String userId) {
        var device = deviceRegistryService.rename(userId, deviceId, body.get("name"));
        return ResponseEntity.ok(new DeviceSummaryDto(
            device.getId(), device.getName(), device.getOs(), device.getStatus().name(),
            device.getCapabilities(), null));
    }
}
```

- [ ] **Step 4: Run all service-connections tests**

```bash
./gradlew :service:service-connections:test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests passed.

- [ ] **Step 5: Run the full build**

```bash
./gradlew build -x test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run all tests**

```bash
./gradlew test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, all tests passed.

- [ ] **Step 7: Commit**

```bash
git add service/service-connections/src/
git commit -m "feat(service-connections): add DeviceController with pair, list, unpair, rename endpoints"
```

---

## Final Verification

- [ ] **Verify no Firestore imports remain in new modules**

```bash
grep -r "google.cloud.firestore\|com.google.firebase" \
  data/data-connections business/business-connections service/service-connections \
  2>/dev/null | grep -v ".gradle" | grep -v "build/"
```

Expected: No output (zero Firestore references in new modules).

- [ ] **Verify all tests pass**

```bash
./gradlew test 2>&1 | grep -E "tests|BUILD"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Tag the completion**

```bash
git tag v2.0.0-foundation-connections
```

---

**Plan complete and saved.** Plan B (Secrets Vault + Spark Orchestration + gRPC + Deploy) covers the remaining modules.
