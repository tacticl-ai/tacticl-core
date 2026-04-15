# Platform v2 Plan B — Secrets Vault & Spark Execution

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Modules 3 (Secrets Vault) and 4 (Spark & Agent Execution) of Tacticl Platform v2, plus an internal gRPC service stub for the arbiter.

**Architecture:** Secrets metadata in MongoDB (`secret_metadata`), secret values in Vault at `tacticl/{userId}/secrets/{secretId}`. Sparks are the top-level execution unit — created on every user command, classified via Claude Haiku, tracked through SSE events. The internal gRPC stub wires ConnectionRegistryService + SecretsVaultService to arbiter-callable methods.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Spring Data MongoDB, HashiCorp Vault (`cidadel framework-secrets`), `cidadel-client-anthropic-direct` (Haiku classification), Spring MVC `SseEmitter`, Mockito unit tests throughout (no Docker/Testcontainers)

**Prerequisites:** Plan A complete — `data-connections`, `business-connections`, `service-connections` modules exist with Connection, Device, PairingToken entities and their services.

**Key patterns from Plan A:**
- Controllers extend `BaseController` (`io.cidadel.service.base.controller.BaseController`) and implement `getModuleName()`
- Services are plain `@Service` classes — do NOT extend `BaseService` (it pulls in `PlatformTransactionManager` which is not available in this module stack)
- No `@Autowired` on fields — constructor injection only
- `Optional<T>` for queries, never null
- VaultClient API: `read(String path)`, `write(String path, Map<String,Object> data)`, `delete(String path)`, `isHealthy()`
- Mockito `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` for tests (no `@WebMvcTest`)
- Parent `data/build.gradle.kts` provides: `spring-boot-starter-data-mongodb`, exception, logging
- Parent `business/build.gradle.kts` provides: exception, logging, web, jackson, test deps
- Parent `service/build.gradle.kts` provides: authorization, exception, logging, web, validation, springdoc, jackson, test deps

---

## File Structure

### Chunk 1: Secrets Vault (additions to existing modules)

```
data/data-connections/src/main/java/io/tacticl/data/connections/entity/
  TestResult.java                    NEW enum: VALID, INVALID, UNREACHABLE, UNTESTED
  SecretMetadata.java                NEW @Document("secret_metadata")
data/data-connections/src/main/java/io/tacticl/data/connections/repository/
  SecretMetadataRepository.java      NEW findByUserId, deleteByUserIdAndId
data/data-connections/src/test/java/io/tacticl/data/connections/entity/
  SecretMetadataTest.java            NEW entity factory + state tests

business/business-connections/src/main/java/io/tacticl/business/connections/service/
  SecretsVaultService.java           NEW orchestrates Vault write + MongoDB save
business/business-connections/src/test/java/io/tacticl/business/connections/service/
  SecretsVaultServiceTest.java       NEW Mockito unit tests

service/service-connections/src/main/java/io/tacticl/service/connections/controller/
  SecretController.java              NEW GET/POST/DELETE /v1/secrets, GET /v1/secrets/{secretId}/test
service/service-connections/src/main/java/io/tacticl/service/connections/dto/
  CreateSecretRequestDto.java        NEW record: name, providerHint, value
  SecretMetadataDto.java             NEW record: secretId, name, providerHint, createdAt, lastTestedAt, lastTestResult
  SecretTestResultDto.java           NEW record: result (VALID|INVALID|UNREACHABLE)
service/service-connections/src/test/java/io/tacticl/service/connections/controller/
  SecretControllerTest.java          NEW Mockito controller tests
```

### Chunk 2: Spark Data Layer (new `data-sparks` module)

```
settings.gradle.kts                  MODIFY add data-sparks, business-sparks, service-sparks
data/data-sparks/build.gradle.kts    NEW child build file

data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/
  SparkType.java                     NEW enum: CODE, DEVOPS, RESEARCH, CREATIVE, DATA
  SparkStatus.java                   NEW enum: PENDING, ROUTING, EXECUTING, CHECKPOINT, COMPLETED, FAILED, CANCELLED
  SparkRoute.java                    NEW enum: DEVICE, CLOUD
  Spark.java                         NEW @Document("sparks")
  CheckpointType.java                NEW enum: APPROVAL, CREDENTIAL_REQUEST, CONFIRMATION
  CheckpointStatus.java              NEW enum: PENDING, APPROVED, DENIED
  Checkpoint.java                    NEW @Document("checkpoints")
data/data-sparks/src/main/java/io/tacticl/data/sparks/repository/
  SparkRepository.java               NEW findByUserId, findByUserIdOrderByCreatedAtDesc
  CheckpointRepository.java          NEW findBySparkIdAndUserId, findBySparkIdAndStatus
data/data-sparks/src/test/java/io/tacticl/data/sparks/entity/
  SparkTest.java                     NEW entity factory + state transition tests
  CheckpointTest.java                NEW entity tests
```

### Chunk 3: Spark Business Layer (new `business-sparks` module)

```
business/business-sparks/build.gradle.kts         NEW child build file

business/business-sparks/src/main/java/io/tacticl/business/sparks/service/
  SparkService.java                  NEW create, list, get, cancel, markExecuting, markCompleted, markFailed
  CheckpointService.java             NEW create, resolve
  SparkClassifierService.java        NEW Claude Haiku classification → SparkType
  SparkEventEmitter.java             NEW SseEmitter fan-out per sparkId
business/business-sparks/src/test/java/io/tacticl/business/sparks/service/
  SparkServiceTest.java              NEW Mockito unit tests
  CheckpointServiceTest.java         NEW Mockito unit tests
  SparkClassifierServiceTest.java    NEW Mockito unit tests
  SparkEventEmitterTest.java         NEW emit + cleanup tests
```

### Chunk 4: Spark REST API (new `service-sparks` module)

```
service/service-sparks/build.gradle.kts            NEW child build file

service/service-sparks/src/main/java/io/tacticl/service/sparks/controller/
  SparkController.java               NEW POST /v1/sparks, GET /v1/sparks, GET /v1/sparks/{id},
                                         DELETE /v1/sparks/{id}, GET /v1/sparks/{id}/events (SSE),
                                         POST /v1/sparks/{id}/checkpoint/{checkpointId}
service/service-sparks/src/main/java/io/tacticl/service/sparks/dto/
  CreateSparkRequestDto.java         NEW record: input, sessionId
  SparkSummaryDto.java               NEW record: sparkId, status, type, route, createdAt
  SparkDetailDto.java                NEW record: sparkId, input, status, type, route, deviceId, pipelineRunId, tokenCost, createdAt, startedAt, completedAt
  CheckpointDetailDto.java           NEW record: checkpointId, sparkId, type, prompt, status, createdAt
  ResolveCheckpointRequestDto.java   NEW record: decision (APPROVE|DENY), instructions
service/service-sparks/src/test/java/io/tacticl/service/sparks/controller/
  SparkControllerTest.java           NEW Mockito controller tests
```

### Chunk 5: gRPC Internal Stub

```
service/service-connections/src/main/java/io/tacticl/service/connections/grpc/
  TacticlInternalRequest.java        NEW records: GetConnectionsRequest, GetSecretRequest, CheckpointRequest
  TacticlInternalResponse.java       NEW records: GetConnectionsResponse, GetSecretResponse, CheckpointResponse
  TacticlInternalService.java        NEW interface with 3 methods
  TacticlInternalServiceImpl.java    NEW delegates to ConnectionRegistryService, SecretsVaultService
service/service-connections/src/test/java/io/tacticl/service/connections/grpc/
  TacticlInternalServiceImplTest.java  NEW Mockito tests for all 3 methods
```

---

## Chunk 1: Secrets Vault

### Task 1: SecretMetadata entity + repository

**Files:**
- Create: `data/data-connections/src/main/java/io/tacticl/data/connections/entity/TestResult.java`
- Create: `data/data-connections/src/main/java/io/tacticl/data/connections/entity/SecretMetadata.java`
- Create: `data/data-connections/src/main/java/io/tacticl/data/connections/repository/SecretMetadataRepository.java`
- Create: `data/data-connections/src/test/java/io/tacticl/data/connections/entity/SecretMetadataTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// SecretMetadataTest.java
package io.tacticl.data.connections.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecretMetadataTest {

    @Test
    void create_setsFieldsCorrectly() {
        SecretMetadata secret = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");

        assertThat(secret.getUserId()).isEqualTo("user-1");
        assertThat(secret.getName()).isEqualTo("MY_KEY");
        assertThat(secret.getProviderHint()).isEqualTo("OpenAI");
        assertThat(secret.getId()).isNotBlank();
        assertThat(secret.getVaultPath()).isEqualTo("tacticl/user-1/secrets/" + secret.getId());
        assertThat(secret.getLastTestResult()).isEqualTo(TestResult.UNTESTED);
        assertThat(secret.getCreatedAt()).isNotNull();
        assertThat(secret.getLastTestedAt()).isNull();
    }

    @Test
    void markTested_updatesResultAndTimestamp() {
        SecretMetadata secret = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");

        secret.markTested(TestResult.VALID);

        assertThat(secret.getLastTestResult()).isEqualTo(TestResult.VALID);
        assertThat(secret.getLastTestedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core/.worktrees/platform-v2-foundation
./gradlew :data:data-connections:test --tests "io.tacticl.data.connections.entity.SecretMetadataTest" 2>&1 | tail -20
```

Expected: FAIL — class not found

- [ ] **Step 3: Implement TestResult enum**

```java
// TestResult.java
package io.tacticl.data.connections.entity;

public enum TestResult {
    VALID, INVALID, UNREACHABLE, UNTESTED
}
```

- [ ] **Step 4: Implement SecretMetadata entity**

```java
// SecretMetadata.java
package io.tacticl.data.connections.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("secret_metadata")
public class SecretMetadata {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;
    private String providerHint;
    private String vaultPath;
    private TestResult lastTestResult;
    private Instant lastTestedAt;
    private Instant createdAt;

    protected SecretMetadata() {}

    public static SecretMetadata create(String userId, String name, String providerHint) {
        SecretMetadata s = new SecretMetadata();
        s.id = UUID.randomUUID().toString();
        s.userId = userId;
        s.name = name;
        s.providerHint = providerHint;
        s.vaultPath = "tacticl/" + userId + "/secrets/" + s.id;
        s.lastTestResult = TestResult.UNTESTED;
        s.createdAt = Instant.now();
        return s;
    }

    public void markTested(TestResult result) {
        this.lastTestResult = result;
        this.lastTestedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getProviderHint() { return providerHint; }
    public String getVaultPath() { return vaultPath; }
    public TestResult getLastTestResult() { return lastTestResult; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Implement SecretMetadataRepository**

```java
// SecretMetadataRepository.java
package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.SecretMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface SecretMetadataRepository extends MongoRepository<SecretMetadata, String> {
    List<SecretMetadata> findByUserId(String userId);
    Optional<SecretMetadata> findByIdAndUserId(String id, String userId);
    Optional<SecretMetadata> findByUserIdAndName(String userId, String name);
    void deleteByIdAndUserId(String id, String userId);
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
./gradlew :data:data-connections:test --tests "io.tacticl.data.connections.entity.SecretMetadataTest" 2>&1 | tail -10
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add data/data-connections/src/
git commit -m "feat(secrets): add SecretMetadata entity and repository"
```

---

### Task 2: SecretsVaultService

**Files:**
- Create: `business/business-connections/src/main/java/io/tacticl/business/connections/service/SecretsVaultService.java`
- Create: `business/business-connections/src/test/java/io/tacticl/business/connections/service/SecretsVaultServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// SecretsVaultServiceTest.java
package io.tacticl.business.connections.service;

import io.cidadel.framework.secrets.client.VaultClient;
import io.tacticl.data.connections.entity.SecretMetadata;
import io.tacticl.data.connections.entity.TestResult;
import io.tacticl.data.connections.repository.SecretMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretsVaultServiceTest {

    @Mock SecretMetadataRepository repository;
    @Mock VaultClient vaultClient;
    @InjectMocks SecretsVaultService service;

    @Test
    void store_savesMetadataAndWritesToVault() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SecretMetadata result = service.store("user-1", "MY_KEY", "OpenAI", "sk-test-value");

        assertThat(result.getName()).isEqualTo("MY_KEY");
        assertThat(result.getProviderHint()).isEqualTo("OpenAI");
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(vaultClient).write(eq(result.getVaultPath()), captor.capture());
        assertThat(captor.getValue()).containsEntry("value", "sk-test-value");
    }

    @Test
    void listSecrets_returnsUserSecrets() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByUserId("user-1")).thenReturn(List.of(s));

        List<SecretMetadata> result = service.listSecrets("user-1");

        assertThat(result).hasSize(1);
    }

    @Test
    void delete_removesFromVaultAndMongo() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId("secret-1", "user-1")).thenReturn(Optional.of(s));

        service.delete("user-1", "secret-1");

        verify(vaultClient).delete(s.getVaultPath());
        verify(repository).deleteByIdAndUserId("secret-1", "user-1");
    }

    @Test
    void delete_wrongUser_doesNothing() {
        when(repository.findByIdAndUserId("secret-1", "other-user")).thenReturn(Optional.empty());

        service.delete("other-user", "secret-1");

        verify(vaultClient, never()).delete(any());
        verify(repository, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    void resolveValue_readsFromVault() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId(s.getId(), "user-1")).thenReturn(Optional.of(s));
        when(vaultClient.read(s.getVaultPath())).thenReturn(Map.of("value", "sk-real-value"));

        Optional<String> value = service.resolveValue("user-1", s.getId());

        assertThat(value).contains("sk-real-value");
    }

    @Test
    void testSecret_valid_returnsValidAndUpdatesMetadata() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId(s.getId(), "user-1")).thenReturn(Optional.of(s));
        when(vaultClient.read(s.getVaultPath())).thenReturn(Map.of("value", "sk-test-value"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestResult result = service.testSecret("user-1", s.getId());

        assertThat(result).isEqualTo(TestResult.VALID);
        assertThat(s.getLastTestResult()).isEqualTo(TestResult.VALID);
        assertThat(s.getLastTestedAt()).isNotNull();
    }

    @Test
    void testSecret_vaultReturnsNoValue_returnsInvalid() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId(s.getId(), "user-1")).thenReturn(Optional.of(s));
        when(vaultClient.read(s.getVaultPath())).thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestResult result = service.testSecret("user-1", s.getId());

        assertThat(result).isEqualTo(TestResult.INVALID);
    }

    @Test
    void testSecret_vaultThrows_returnsUnreachable() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId(s.getId(), "user-1")).thenReturn(Optional.of(s));
        when(vaultClient.read(any())).thenThrow(new RuntimeException("Vault unreachable"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestResult result = service.testSecret("user-1", s.getId());

        assertThat(result).isEqualTo(TestResult.UNREACHABLE);
    }

    @Test
    void testSecret_notFound_returnsInvalid() {
        when(repository.findByIdAndUserId("missing", "user-1")).thenReturn(Optional.empty());

        TestResult result = service.testSecret("user-1", "missing");

        assertThat(result).isEqualTo(TestResult.INVALID);
        verify(vaultClient, never()).read(any());
    }

    @Test
    void resolveValueByName_readsFromVaultByName() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_OPENAI_KEY", "OpenAI");
        when(repository.findByUserIdAndName("user-1", "MY_OPENAI_KEY")).thenReturn(Optional.of(s));
        when(vaultClient.read(s.getVaultPath())).thenReturn(Map.of("value", "sk-real-value"));

        Optional<String> value = service.resolveValueByName("user-1", "MY_OPENAI_KEY");

        assertThat(value).contains("sk-real-value");
    }

    @Test
    void resolveValueByName_notFound_returnsEmpty() {
        when(repository.findByUserIdAndName("user-1", "MISSING_KEY")).thenReturn(Optional.empty());

        Optional<String> value = service.resolveValueByName("user-1", "MISSING_KEY");

        assertThat(value).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :business:business-connections:test --tests "io.tacticl.business.connections.service.SecretsVaultServiceTest" 2>&1 | tail -20
```

Expected: FAIL — class not found

- [ ] **Step 3: Implement SecretsVaultService**

```java
// SecretsVaultService.java
package io.tacticl.business.connections.service;

import io.cidadel.framework.secrets.client.VaultClient;
import io.tacticl.data.connections.entity.SecretMetadata;
import io.tacticl.data.connections.repository.SecretMetadataRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SecretsVaultService {

    private final SecretMetadataRepository repository;
    private final VaultClient vaultClient;

    public SecretsVaultService(SecretMetadataRepository repository, VaultClient vaultClient) {
        this.repository = repository;
        this.vaultClient = vaultClient;
    }

    public SecretMetadata store(String userId, String name, String providerHint, String value) {
        SecretMetadata metadata = SecretMetadata.create(userId, name, providerHint);
        vaultClient.write(metadata.getVaultPath(), Map.of("value", value));
        return repository.save(metadata);
    }

    public List<SecretMetadata> listSecrets(String userId) {
        return repository.findByUserId(userId);
    }

    public void delete(String userId, String secretId) {
        repository.findByIdAndUserId(secretId, userId).ifPresent(secret -> {
            vaultClient.delete(secret.getVaultPath());
            repository.deleteByIdAndUserId(secretId, userId);
        });
    }

    /** Resolves the live secret value from Vault by ID. */
    public Optional<String> resolveValue(String userId, String secretId) {
        return repository.findByIdAndUserId(secretId, userId)
                .map(s -> readVaultValue(s.getVaultPath()));
    }

    /**
     * Resolves the live secret value from Vault by human-readable name.
     * Used by the gRPC stub — arbiter references secrets by name (e.g. "MY_OPENAI_KEY").
     */
    public Optional<String> resolveValueByName(String userId, String secretName) {
        return repository.findByUserIdAndName(userId, secretName)
                .map(s -> readVaultValue(s.getVaultPath()));
    }

    /** Tests whether a stored secret is still readable from Vault. */
    public TestResult testSecret(String userId, String secretId) {
        return repository.findByIdAndUserId(secretId, userId)
                .map(s -> {
                    try {
                        Map<String, Object> data = vaultClient.read(s.getVaultPath());
                        TestResult result = (data != null && data.containsKey("value"))
                                ? TestResult.VALID : TestResult.INVALID;
                        s.markTested(result);
                        repository.save(s);
                        return result;
                    } catch (Exception e) {
                        s.markTested(TestResult.UNREACHABLE);
                        repository.save(s);
                        return TestResult.UNREACHABLE;
                    }
                })
                .orElse(TestResult.INVALID);
    }

    private String readVaultValue(String vaultPath) {
        Map<String, Object> data = vaultClient.read(vaultPath);
        return data != null ? (String) data.get("value") : null;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :business:business-connections:test --tests "io.tacticl.business.connections.service.SecretsVaultServiceTest" 2>&1 | tail -10
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add business/business-connections/src/
git commit -m "feat(secrets): add SecretsVaultService with Vault + MongoDB"
```

---

### Task 3: SecretController + DTOs

**Files:**
- Create: `service/service-connections/src/main/java/io/tacticl/service/connections/dto/CreateSecretRequestDto.java`
- Create: `service/service-connections/src/main/java/io/tacticl/service/connections/dto/SecretMetadataDto.java`
- Create: `service/service-connections/src/main/java/io/tacticl/service/connections/dto/SecretTestResultDto.java`
- Create: `service/service-connections/src/main/java/io/tacticl/service/connections/controller/SecretController.java`
- Create: `service/service-connections/src/test/java/io/tacticl/service/connections/controller/SecretControllerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// SecretControllerTest.java
package io.tacticl.service.connections.controller;

import io.cidadel.framework.authorization.AuthenticatedUser;
import io.tacticl.business.connections.service.SecretsVaultService;
import io.tacticl.data.connections.entity.SecretMetadata;
import io.tacticl.data.connections.entity.TestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretControllerTest {

    @Mock SecretsVaultService secretsVaultService;
    @InjectMocks SecretController controller;

    private AuthenticatedUser user(String id) {
        AuthenticatedUser u = mock(AuthenticatedUser.class);
        when(u.getUserId()).thenReturn(id);
        return u;
    }

    @Test
    void listSecrets_returnsSecretList() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(secretsVaultService.listSecrets("user-1")).thenReturn(List.of(s));

        ResponseEntity<?> resp = controller.listSecrets(user("user-1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) resp.getBody()).hasSize(1);
    }

    @Test
    void createSecret_returns201() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(secretsVaultService.store("user-1", "MY_KEY", "OpenAI", "sk-value")).thenReturn(s);
        var body = new CreateSecretRequestDto("MY_KEY", "OpenAI", "sk-value");

        ResponseEntity<?> resp = controller.createSecret(user("user-1"), body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void deleteSecret_returns204() {
        ResponseEntity<?> resp = controller.deleteSecret(user("user-1"), "secret-1");

        verify(secretsVaultService).delete("user-1", "secret-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void testSecret_returnsResult() {
        when(secretsVaultService.testSecret("user-1", "secret-1")).thenReturn(TestResult.VALID);

        ResponseEntity<?> resp = controller.testSecret(user("user-1"), "secret-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isInstanceOf(SecretTestResultDto.class);
        assertThat(((SecretTestResultDto) resp.getBody()).result()).isEqualTo("VALID");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :service:service-connections:test --tests "io.tacticl.service.connections.controller.SecretControllerTest" 2>&1 | tail -20
```

Expected: FAIL — class not found

- [ ] **Step 3: Implement DTOs**

```java
// CreateSecretRequestDto.java
package io.tacticl.service.connections.dto;
public record CreateSecretRequestDto(String name, String providerHint, String value) {}

// SecretMetadataDto.java
package io.tacticl.service.connections.dto;
public record SecretMetadataDto(
    String secretId, String name, String providerHint,
    String createdAt, String lastTestedAt, String lastTestResult
) {}

// SecretTestResultDto.java
package io.tacticl.service.connections.dto;
public record SecretTestResultDto(String result) {}
```

- [ ] **Step 4: Implement SecretController**

```java
// SecretController.java
package io.tacticl.service.connections.controller;

import io.cidadel.framework.authorization.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.connections.service.SecretsVaultService;
import io.tacticl.data.connections.entity.SecretMetadata;
import io.tacticl.data.connections.entity.TestResult;
import io.tacticl.service.connections.dto.CreateSecretRequestDto;
import io.tacticl.service.connections.dto.SecretMetadataDto;
import io.tacticl.service.connections.dto.SecretTestResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/secrets")
public class SecretController extends BaseController {

    private final SecretsVaultService secretsVaultService;

    public SecretController(SecretsVaultService secretsVaultService) {
        this.secretsVaultService = secretsVaultService;
    }

    @Override
    protected String getModuleName() { return "secrets"; }

    @GetMapping
    public ResponseEntity<List<SecretMetadataDto>> listSecrets(AuthenticatedUser user) {
        List<SecretMetadataDto> dtos = secretsVaultService.listSecrets(user.getUserId())
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<SecretMetadataDto> createSecret(
            AuthenticatedUser user,
            @RequestBody CreateSecretRequestDto body) {
        SecretMetadata saved = secretsVaultService.store(
                user.getUserId(), body.name(), body.providerHint(), body.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @DeleteMapping("/{secretId}")
    public ResponseEntity<Void> deleteSecret(AuthenticatedUser user, @PathVariable String secretId) {
        secretsVaultService.delete(user.getUserId(), secretId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{secretId}/test")
    public ResponseEntity<SecretTestResultDto> testSecret(
            AuthenticatedUser user, @PathVariable String secretId) {
        TestResult result = secretsVaultService.testSecret(user.getUserId(), secretId);
        return ResponseEntity.ok(new SecretTestResultDto(result.name()));
    }

    private SecretMetadataDto toDto(SecretMetadata s) {
        return new SecretMetadataDto(
                s.getId(), s.getName(), s.getProviderHint(),
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getLastTestedAt() != null ? s.getLastTestedAt().toString() : null,
                s.getLastTestResult() != null ? s.getLastTestResult().name() : null
        );
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :service:service-connections:test --tests "io.tacticl.service.connections.controller.SecretControllerTest" 2>&1 | tail -10
```

Expected: PASS

- [ ] **Step 6: Run all service-connections tests**

```bash
./gradlew :service:service-connections:test 2>&1 | tail -15
```

Expected: All PASS

- [ ] **Step 7: Commit**

```bash
git add service/service-connections/src/
git commit -m "feat(secrets): add SecretController — GET/POST/DELETE /v1/secrets"
```

---

## Chunk 2: Spark Data Layer

### Task 4: New data-sparks module + Spark entity

**Files:**
- Modify: `settings.gradle.kts` — add three new module includes
- Create: `data/data-sparks/build.gradle.kts`
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/SparkType.java`
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/SparkStatus.java`
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/SparkRoute.java`
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/Spark.java`
- Create: `data/data-sparks/src/test/java/io/tacticl/data/sparks/entity/SparkTest.java`

- [ ] **Step 1: Add modules to settings.gradle.kts**

Open `settings.gradle.kts` and add after the last `include` line:

```kotlin
include(":data:data-sparks")
include(":business:business-sparks")
include(":service:service-sparks")
```

- [ ] **Step 2: Create data-sparks/build.gradle.kts**

```kotlin
// data-sparks — MongoDB entities + repositories for sparks and checkpoints
plugins {
    `java-library`
}

dependencies {
    // MongoDB + exception + logging inherited from data/build.gradle.kts parent
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
```

- [ ] **Step 3: Create the directory structure**

```bash
mkdir -p data/data-sparks/src/main/java/io/tacticl/data/sparks/{entity,repository}
mkdir -p data/data-sparks/src/test/java/io/tacticl/data/sparks/entity
```

- [ ] **Step 4: Write the failing tests**

```java
// SparkTest.java
package io.tacticl.data.sparks.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SparkTest {

    @Test
    void create_setsInitialState() {
        Spark spark = Spark.create("user-1", "build me a REST API");

        assertThat(spark.getId()).isNotBlank();
        assertThat(spark.getUserId()).isEqualTo("user-1");
        assertThat(spark.getInput()).isEqualTo("build me a REST API");
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.PENDING);
        assertThat(spark.getCreatedAt()).isNotNull();
        assertThat(spark.getType()).isNull();
        assertThat(spark.getRoute()).isNull();
    }

    @Test
    void classify_setsTypeAndTransitionsToRouting() {
        Spark spark = Spark.create("user-1", "build me a REST API");

        spark.classify(SparkType.CODE);

        assertThat(spark.getType()).isEqualTo(SparkType.CODE);
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.ROUTING);
    }

    @Test
    void markExecuting_setsRouteAndTimestamp() {
        Spark spark = Spark.create("user-1", "build me a REST API");
        spark.classify(SparkType.CODE);

        spark.markExecuting(SparkRoute.CLOUD, null);

        assertThat(spark.getStatus()).isEqualTo(SparkStatus.EXECUTING);
        assertThat(spark.getRoute()).isEqualTo(SparkRoute.CLOUD);
        assertThat(spark.getStartedAt()).isNotNull();
    }

    @Test
    void markCompleted_setsCompletedStatus() {
        Spark spark = Spark.create("user-1", "test");
        spark.classify(SparkType.RESEARCH);
        spark.markExecuting(SparkRoute.CLOUD, null);

        spark.markCompleted(500, "claude-haiku-4-5");

        assertThat(spark.getStatus()).isEqualTo(SparkStatus.COMPLETED);
        assertThat(spark.getTokenCost()).isEqualTo(500);
        assertThat(spark.getModelUsed()).isEqualTo("claude-haiku-4-5");
        assertThat(spark.getCompletedAt()).isNotNull();
    }

    @Test
    void cancel_setsStatusToCancelled() {
        Spark spark = Spark.create("user-1", "test");

        spark.cancel();

        assertThat(spark.getStatus()).isEqualTo(SparkStatus.CANCELLED);
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

```bash
./gradlew :data:data-sparks:test --tests "io.tacticl.data.sparks.entity.SparkTest" 2>&1 | tail -20
```

Expected: FAIL — class not found

- [ ] **Step 6: Implement enums**

```java
// SparkType.java
package io.tacticl.data.sparks.entity;
public enum SparkType { CODE, DEVOPS, RESEARCH, CREATIVE, DATA }

// SparkStatus.java
package io.tacticl.data.sparks.entity;
public enum SparkStatus { PENDING, ROUTING, EXECUTING, CHECKPOINT, COMPLETED, FAILED, CANCELLED }

// SparkRoute.java
package io.tacticl.data.sparks.entity;
public enum SparkRoute { DEVICE, CLOUD }
```

- [ ] **Step 7: Implement Spark entity**

```java
// Spark.java
package io.tacticl.data.sparks.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("sparks")
public class Spark {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String input;
    private SparkType type;
    private SparkStatus status;
    private SparkRoute route;
    private String deviceId;
    private String pipelineRunId;
    private int tokenCost;
    private String modelUsed;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    protected Spark() {}

    public static Spark create(String userId, String input) {
        Spark s = new Spark();
        s.id = UUID.randomUUID().toString();
        s.userId = userId;
        s.input = input;
        s.status = SparkStatus.PENDING;
        s.createdAt = Instant.now();
        return s;
    }

    public void classify(SparkType type) {
        this.type = type;
        this.status = SparkStatus.ROUTING;
    }

    public void markExecuting(SparkRoute route, String deviceId) {
        this.route = route;
        this.deviceId = deviceId;
        this.status = SparkStatus.EXECUTING;
        this.startedAt = Instant.now();
    }

    public void markCheckpoint() {
        this.status = SparkStatus.CHECKPOINT;
    }

    public void markExecutingFromCheckpoint() {
        this.status = SparkStatus.EXECUTING;
    }

    public void markCompleted(int tokenCost, String modelUsed) {
        this.status = SparkStatus.COMPLETED;
        this.tokenCost = tokenCost;
        this.modelUsed = modelUsed;
        this.completedAt = Instant.now();
    }

    public void markFailed() {
        this.status = SparkStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        this.status = SparkStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getInput() { return input; }
    public SparkType getType() { return type; }
    public SparkStatus getStatus() { return status; }
    public SparkRoute getRoute() { return route; }
    public String getDeviceId() { return deviceId; }
    public String getPipelineRunId() { return pipelineRunId; }
    public int getTokenCost() { return tokenCost; }
    public String getModelUsed() { return modelUsed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }
}
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
./gradlew :data:data-sparks:test --tests "io.tacticl.data.sparks.entity.SparkTest" 2>&1 | tail -10
```

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts data/data-sparks/
git commit -m "feat(sparks): add data-sparks module with Spark entity"
```

---

### Task 5: Checkpoint entity + repositories

**Files:**
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/CheckpointType.java`
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/CheckpointStatus.java`
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/Checkpoint.java`
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/repository/SparkRepository.java`
- Create: `data/data-sparks/src/main/java/io/tacticl/data/sparks/repository/CheckpointRepository.java`
- Create: `data/data-sparks/src/test/java/io/tacticl/data/sparks/entity/CheckpointTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// CheckpointTest.java
package io.tacticl.data.sparks.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CheckpointTest {

    @Test
    void create_setsInitialState() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve PR to main?");

        assertThat(cp.getId()).isNotBlank();
        assertThat(cp.getSparkId()).isEqualTo("spark-1");
        assertThat(cp.getUserId()).isEqualTo("user-1");
        assertThat(cp.getType()).isEqualTo(CheckpointType.APPROVAL);
        assertThat(cp.getPrompt()).isEqualTo("Approve PR to main?");
        assertThat(cp.getStatus()).isEqualTo(CheckpointStatus.PENDING);
        assertThat(cp.getCreatedAt()).isNotNull();
    }

    @Test
    void resolve_approve_setsStatusAndTimestamp() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve?");

        cp.resolve(CheckpointStatus.APPROVED, "looks good");

        assertThat(cp.getStatus()).isEqualTo(CheckpointStatus.APPROVED);
        assertThat(cp.getResolutionInstructions()).isEqualTo("looks good");
        assertThat(cp.getResolvedAt()).isNotNull();
    }

    @Test
    void resolve_deny_setsStatusDenied() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve?");

        cp.resolve(CheckpointStatus.DENIED, null);

        assertThat(cp.getStatus()).isEqualTo(CheckpointStatus.DENIED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :data:data-sparks:test --tests "io.tacticl.data.sparks.entity.CheckpointTest" 2>&1 | tail -20
```

Expected: FAIL

- [ ] **Step 3: Implement enums**

```java
// CheckpointType.java
package io.tacticl.data.sparks.entity;
public enum CheckpointType { APPROVAL, CREDENTIAL_REQUEST, CONFIRMATION }

// CheckpointStatus.java
package io.tacticl.data.sparks.entity;
public enum CheckpointStatus { PENDING, APPROVED, DENIED }
```

- [ ] **Step 4: Implement Checkpoint entity**

```java
// Checkpoint.java
package io.tacticl.data.sparks.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("checkpoints")
public class Checkpoint {

    @Id
    private String id;

    @Indexed
    private String sparkId;

    @Indexed
    private String userId;

    private CheckpointType type;
    private String prompt;
    private CheckpointStatus status;
    private String resolutionInstructions;
    private Instant createdAt;
    private Instant resolvedAt;

    protected Checkpoint() {}

    public static Checkpoint create(String sparkId, String userId,
                                    CheckpointType type, String prompt) {
        Checkpoint c = new Checkpoint();
        c.id = UUID.randomUUID().toString();
        c.sparkId = sparkId;
        c.userId = userId;
        c.type = type;
        c.prompt = prompt;
        c.status = CheckpointStatus.PENDING;
        c.createdAt = Instant.now();
        return c;
    }

    public void resolve(CheckpointStatus decision, String instructions) {
        this.status = decision;
        this.resolutionInstructions = instructions;
        this.resolvedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSparkId() { return sparkId; }
    public String getUserId() { return userId; }
    public CheckpointType getType() { return type; }
    public String getPrompt() { return prompt; }
    public CheckpointStatus getStatus() { return status; }
    public String getResolutionInstructions() { return resolutionInstructions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
```

- [ ] **Step 5: Implement repositories**

```java
// SparkRepository.java
package io.tacticl.data.sparks.repository;

import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SparkRepository extends MongoRepository<Spark, String> {
    Page<Spark> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Optional<Spark> findByIdAndUserId(String id, String userId);
}
```

```java
// CheckpointRepository.java
package io.tacticl.data.sparks.repository;

import io.tacticl.data.sparks.entity.Checkpoint;
import io.tacticl.data.sparks.entity.CheckpointStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface CheckpointRepository extends MongoRepository<Checkpoint, String> {
    List<Checkpoint> findBySparkIdAndUserId(String sparkId, String userId);
    Optional<Checkpoint> findByIdAndSparkIdAndUserId(String id, String sparkId, String userId);
    List<Checkpoint> findBySparkIdAndStatus(String sparkId, CheckpointStatus status);
}
```

- [ ] **Step 6: Run all data-sparks tests**

```bash
./gradlew :data:data-sparks:test 2>&1 | tail -10
```

Expected: All PASS

- [ ] **Step 7: Commit**

```bash
git add data/data-sparks/src/
git commit -m "feat(sparks): add Checkpoint entity, SparkRepository, CheckpointRepository"
```

---

## Chunk 3: Spark Business Layer

### Task 6: New business-sparks module + SparkService + CheckpointService

**Files:**
- Create: `business/business-sparks/build.gradle.kts`
- Create: `business/business-sparks/src/main/java/io/tacticl/business/sparks/service/SparkService.java`
- Create: `business/business-sparks/src/main/java/io/tacticl/business/sparks/service/CheckpointService.java`
- Create: `business/business-sparks/src/test/java/io/tacticl/business/sparks/service/SparkServiceTest.java`
- Create: `business/business-sparks/src/test/java/io/tacticl/business/sparks/service/CheckpointServiceTest.java`

- [ ] **Step 1: Create business-sparks/build.gradle.kts**

```kotlin
// business-sparks — Spark lifecycle, classification, and event emission services
plugins {
    `java-library`
}

dependencies {
    implementation(project(":data:data-sparks"))
    implementation(libs.cidadel.client.anthropic.direct)
}
```

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p business/business-sparks/src/main/java/io/tacticl/business/sparks/service
mkdir -p business/business-sparks/src/test/java/io/tacticl/business/sparks/service
```

- [ ] **Step 3: Write the failing tests**

```java
// SparkServiceTest.java
package io.tacticl.business.sparks.service;

import io.tacticl.data.sparks.entity.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparkServiceTest {

    @Mock SparkRepository sparkRepository;
    @InjectMocks SparkService sparkService;

    @Test
    void create_savesAndReturnsSpark() {
        when(sparkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Spark spark = sparkService.create("user-1", "build me a REST API");

        assertThat(spark.getStatus()).isEqualTo(SparkStatus.PENDING);
        assertThat(spark.getUserId()).isEqualTo("user-1");
        verify(sparkRepository).save(any());
    }

    @Test
    void classify_updatesSparkAndSaves() {
        Spark spark = Spark.create("user-1", "build me a REST API");
        when(sparkRepository.findByIdAndUserId(spark.getId(), "user-1")).thenReturn(Optional.of(spark));
        when(sparkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sparkService.classify(spark.getId(), "user-1", SparkType.CODE);

        assertThat(spark.getStatus()).isEqualTo(SparkStatus.ROUTING);
        verify(sparkRepository).save(spark);
    }

    @Test
    void get_returnsOptionalSpark() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkRepository.findByIdAndUserId("spark-1", "user-1")).thenReturn(Optional.of(spark));

        Optional<Spark> result = sparkService.get("user-1", "spark-1");

        assertThat(result).isPresent();
    }

    @Test
    void cancel_setsStatusCancelled() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkRepository.findByIdAndUserId(spark.getId(), "user-1")).thenReturn(Optional.of(spark));
        when(sparkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sparkService.cancel(spark.getId(), "user-1");

        assertThat(spark.getStatus()).isEqualTo(SparkStatus.CANCELLED);
    }
}
```

```java
// CheckpointServiceTest.java
package io.tacticl.business.sparks.service;

import io.tacticl.data.sparks.entity.*;
import io.tacticl.data.sparks.repository.CheckpointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckpointServiceTest {

    @Mock CheckpointRepository checkpointRepository;
    @InjectMocks CheckpointService checkpointService;

    @Test
    void create_savesCheckpoint() {
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Checkpoint cp = checkpointService.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve PR?");

        assertThat(cp.getStatus()).isEqualTo(CheckpointStatus.PENDING);
        verify(checkpointRepository).save(cp);
    }

    @Test
    void resolve_updatesCheckpointStatus() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve?");
        when(checkpointRepository.findByIdAndSparkIdAndUserId("cp-1", "spark-1", "user-1"))
                .thenReturn(Optional.of(cp));
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Checkpoint resolved = checkpointService.resolve("cp-1", "spark-1", "user-1",
                CheckpointStatus.APPROVED, "go ahead");

        assertThat(resolved.getStatus()).isEqualTo(CheckpointStatus.APPROVED);
    }

    @Test
    void resolve_notFound_throws() {
        when(checkpointRepository.findByIdAndSparkIdAndUserId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkpointService.resolve(
                "cp-1", "spark-1", "user-1", CheckpointStatus.APPROVED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 4: Run test to verify they fail**

```bash
./gradlew :business:business-sparks:test 2>&1 | tail -20
```

Expected: FAIL — class not found

- [ ] **Step 5: Implement SparkService**

```java
// SparkService.java
package io.tacticl.business.sparks.service;

import io.tacticl.data.sparks.entity.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class SparkService {

    private final SparkRepository sparkRepository;

    public SparkService(SparkRepository sparkRepository) {
        this.sparkRepository = sparkRepository;
    }

    public Spark create(String userId, String input) {
        Spark spark = Spark.create(userId, input);
        return sparkRepository.save(spark);
    }

    public Spark classify(String sparkId, String userId, SparkType type) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));
        spark.classify(type);
        return sparkRepository.save(spark);
    }

    public Spark markExecuting(String sparkId, String userId, SparkRoute route, String deviceId) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));
        spark.markExecuting(route, deviceId);
        return sparkRepository.save(spark);
    }

    public Spark markCompleted(String sparkId, String userId, int tokenCost, String modelUsed) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));
        spark.markCompleted(tokenCost, modelUsed);
        return sparkRepository.save(spark);
    }

    public Spark markFailed(String sparkId, String userId) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));
        spark.markFailed();
        return sparkRepository.save(spark);
    }

    public void cancel(String sparkId, String userId) {
        sparkRepository.findByIdAndUserId(sparkId, userId).ifPresent(spark -> {
            spark.cancel();
            sparkRepository.save(spark);
        });
    }

    public Optional<Spark> get(String userId, String sparkId) {
        return sparkRepository.findByIdAndUserId(sparkId, userId);
    }

    public Page<Spark> list(String userId, int page, int size) {
        return sparkRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }
}
```

- [ ] **Step 6: Implement CheckpointService**

```java
// CheckpointService.java
package io.tacticl.business.sparks.service;

import io.tacticl.data.sparks.entity.*;
import io.tacticl.data.sparks.repository.CheckpointRepository;
import org.springframework.stereotype.Service;

@Service
public class CheckpointService {

    private final CheckpointRepository checkpointRepository;

    public CheckpointService(CheckpointRepository checkpointRepository) {
        this.checkpointRepository = checkpointRepository;
    }

    public Checkpoint create(String sparkId, String userId, CheckpointType type, String prompt) {
        Checkpoint checkpoint = Checkpoint.create(sparkId, userId, type, prompt);
        return checkpointRepository.save(checkpoint);
    }

    public Checkpoint resolve(String checkpointId, String sparkId, String userId,
                              CheckpointStatus decision, String instructions) {
        Checkpoint checkpoint = checkpointRepository
                .findByIdAndSparkIdAndUserId(checkpointId, sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));
        checkpoint.resolve(decision, instructions);
        return checkpointRepository.save(checkpoint);
    }
}
```

- [ ] **Step 7: Run all business-sparks tests**

```bash
./gradlew :business:business-sparks:test 2>&1 | tail -10
```

Expected: All PASS

- [ ] **Step 8: Commit**

```bash
git add business/business-sparks/
git commit -m "feat(sparks): add business-sparks with SparkService and CheckpointService"
```

---

### Task 7: SparkClassifierService

**Files:**
- Create: `business/business-sparks/src/main/java/io/tacticl/business/sparks/service/SparkClassifierService.java`
- Create: `business/business-sparks/src/test/java/io/tacticl/business/sparks/service/SparkClassifierServiceTest.java`

The classifier calls Claude Haiku via `cidadel-client-anthropic-direct`. We inject `AnthropicClient` and call the Messages API with a structured prompt that returns a single uppercase token (CODE, DEVOPS, RESEARCH, CREATIVE, DATA).

- [ ] **Step 1: Write the failing tests**

```java
// SparkClassifierServiceTest.java
package io.tacticl.business.sparks.service;

import io.cidadel.client.anthropic.AnthropicClient;
import io.cidadel.client.anthropic.model.Message;
import io.cidadel.client.anthropic.model.MessageRequest;
import io.cidadel.client.anthropic.model.MessageResponse;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparkClassifierServiceTest {

    @Mock AnthropicClient anthropicClient;
    @InjectMocks SparkClassifierService classifier;

    @Test
    void classify_parsesCodeType() {
        MessageResponse response = mockResponse("CODE");
        when(anthropicClient.sendMessage(any())).thenReturn(response);

        SparkType type = classifier.classify("build me a REST API");

        assertThat(type).isEqualTo(SparkType.CODE);
    }

    @Test
    void classify_parsesResearchType() {
        MessageResponse response = mockResponse("RESEARCH");
        when(anthropicClient.sendMessage(any())).thenReturn(response);

        SparkType type = classifier.classify("what is the latest news about AI?");

        assertThat(type).isEqualTo(SparkType.RESEARCH);
    }

    @Test
    void classify_unknownResponse_defaultsToResearch() {
        MessageResponse response = mockResponse("INVALID_TOKEN");
        when(anthropicClient.sendMessage(any())).thenReturn(response);

        SparkType type = classifier.classify("do something");

        assertThat(type).isEqualTo(SparkType.RESEARCH);
    }

    @Test
    void classify_clientThrows_defaultsToResearch() {
        when(anthropicClient.sendMessage(any())).thenThrow(new RuntimeException("API error"));

        SparkType type = classifier.classify("do something");

        assertThat(type).isEqualTo(SparkType.RESEARCH);
    }

    private MessageResponse mockResponse(String text) {
        MessageResponse resp = mock(MessageResponse.class);
        Message msg = mock(Message.class);
        when(msg.getText()).thenReturn(text);
        when(resp.getFirstMessage()).thenReturn(msg);
        return resp;
    }
}
```

**Note on the AnthropicClient mock:** If the actual cidadel `AnthropicClient` has a different method signature than `sendMessage(MessageRequest)` / `getFirstMessage()`, adjust the test accordingly after confirming by checking the decompiled JAR. The pattern above mirrors the strategiz-core usage.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :business:business-sparks:test --tests "io.tacticl.business.sparks.service.SparkClassifierServiceTest" 2>&1 | tail -20
```

Expected: FAIL

- [ ] **Step 3: Implement SparkClassifierService**

```java
// SparkClassifierService.java
package io.tacticl.business.sparks.service;

import io.cidadel.client.anthropic.AnthropicClient;
import io.cidadel.client.anthropic.model.MessageRequest;
import io.tacticl.data.sparks.entity.SparkType;
import org.springframework.stereotype.Service;
import java.util.Arrays;

@Service
public class SparkClassifierService {

    private static final String SYSTEM_PROMPT = """
            You are a task classifier. Given a user request, respond with exactly one word
            indicating the type: CODE, DEVOPS, RESEARCH, CREATIVE, or DATA.
            Respond with only the word, nothing else.
            CODE = software development, APIs, scripts.
            DEVOPS = infrastructure, deployments, CI/CD.
            RESEARCH = information gathering, summaries, analysis.
            CREATIVE = writing, design, content creation.
            DATA = data processing, analysis, transformations.
            """;

    private final AnthropicClient anthropicClient;

    public SparkClassifierService(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    public SparkType classify(String input) {
        try {
            MessageRequest request = MessageRequest.builder()
                    .model("claude-haiku-4-5-20251001")
                    .maxTokens(10)
                    .system(SYSTEM_PROMPT)
                    .userMessage(input)
                    .build();
            String text = anthropicClient.sendMessage(request).getFirstMessage().getText().trim().toUpperCase();
            return Arrays.stream(SparkType.values())
                    .filter(t -> t.name().equals(text))
                    .findFirst()
                    .orElse(SparkType.RESEARCH);
        } catch (Exception e) {
            return SparkType.RESEARCH;
        }
    }
}
```

**Important:** The `MessageRequest.builder()` API above mirrors the typical cidadel/strategiz pattern. If the actual API differs, the implementer should decompile `cidadel-client-anthropic-direct` to confirm (same approach used for VaultClient in Plan A) and adjust.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :business:business-sparks:test --tests "io.tacticl.business.sparks.service.SparkClassifierServiceTest" 2>&1 | tail -10
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add business/business-sparks/src/main/java/io/tacticl/business/sparks/service/SparkClassifierService.java
git add business/business-sparks/src/test/java/io/tacticl/business/sparks/service/SparkClassifierServiceTest.java
git commit -m "feat(sparks): add SparkClassifierService via Claude Haiku"
```

---

### Task 8: SparkEventEmitter (SSE fan-out)

**Files:**
- Create: `business/business-sparks/src/main/java/io/tacticl/business/sparks/service/SparkEventEmitter.java`
- Create: `business/business-sparks/src/test/java/io/tacticl/business/sparks/service/SparkEventEmitterTest.java`

Manages a `ConcurrentHashMap<String, Set<SseEmitter>>` of active SSE subscribers per sparkId. Thread-safe add/remove, broadcasts events to all registered emitters.

- [ ] **Step 1: Write the failing tests**

```java
// SparkEventEmitterTest.java
package io.tacticl.business.sparks.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class SparkEventEmitterTest {

    SparkEventEmitter emitter = new SparkEventEmitter();

    @Test
    void register_addsEmitter() {
        SseEmitter sse = new SseEmitter(30_000L);
        emitter.register("spark-1", sse);

        assertThat(emitter.activeCount("spark-1")).isEqualTo(1);
    }

    @Test
    void unregister_removesEmitter() {
        SseEmitter sse = new SseEmitter(30_000L);
        emitter.register("spark-1", sse);

        emitter.unregister("spark-1", sse);

        assertThat(emitter.activeCount("spark-1")).isEqualTo(0);
    }

    @Test
    void emit_callsOnCompletionAndHandlesSendError() {
        // emit to a sparkId with no subscribers should not throw
        assertThatCode(() -> emitter.emit("spark-1", "STATUS_UPDATE", "EXECUTING"))
                .doesNotThrowAnyException();
    }

    @Test
    void completeAll_removesAllEmitters() {
        emitter.register("spark-1", new SseEmitter(30_000L));
        emitter.register("spark-1", new SseEmitter(30_000L));

        emitter.completeAll("spark-1");

        assertThat(emitter.activeCount("spark-1")).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :business:business-sparks:test --tests "io.tacticl.business.sparks.service.SparkEventEmitterTest" 2>&1 | tail -20
```

Expected: FAIL

- [ ] **Step 3: Implement SparkEventEmitter**

```java
// SparkEventEmitter.java
package io.tacticl.business.sparks.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SparkEventEmitter {

    private final ConcurrentHashMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String sparkId, SseEmitter emitter) {
        emitters.computeIfAbsent(sparkId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(emitter);
        emitter.onCompletion(() -> unregister(sparkId, emitter));
        emitter.onTimeout(() -> unregister(sparkId, emitter));
        emitter.onError(e -> unregister(sparkId, emitter));
        return emitter;
    }

    public void unregister(String sparkId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(sparkId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) emitters.remove(sparkId);
        }
    }

    public void emit(String sparkId, String eventName, Object data) {
        Set<SseEmitter> set = emitters.getOrDefault(sparkId, Collections.emptySet());
        Set<SseEmitter> failed = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                failed.add(emitter);
            }
        }
        failed.forEach(e -> unregister(sparkId, e));
    }

    public void completeAll(String sparkId) {
        Set<SseEmitter> set = emitters.remove(sparkId);
        if (set != null) set.forEach(SseEmitter::complete);
    }

    /** For tests only. */
    int activeCount(String sparkId) {
        return emitters.getOrDefault(sparkId, Collections.emptySet()).size();
    }
}
```

- [ ] **Step 4: Run all business-sparks tests**

```bash
./gradlew :business:business-sparks:test 2>&1 | tail -10
```

Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add business/business-sparks/src/main/java/io/tacticl/business/sparks/service/SparkEventEmitter.java
git add business/business-sparks/src/test/java/io/tacticl/business/sparks/service/SparkEventEmitterTest.java
git commit -m "feat(sparks): add SparkEventEmitter for SSE fan-out"
```

---

## Chunk 4: Spark REST API

### Task 9: service-sparks module + SparkController

**Files:**
- Create: `service/service-sparks/build.gradle.kts`
- Create: `service/service-sparks/src/main/java/io/tacticl/service/sparks/dto/CreateSparkRequestDto.java`
- Create: `service/service-sparks/src/main/java/io/tacticl/service/sparks/dto/SparkSummaryDto.java`
- Create: `service/service-sparks/src/main/java/io/tacticl/service/sparks/dto/SparkDetailDto.java`
- Create: `service/service-sparks/src/main/java/io/tacticl/service/sparks/dto/ResolveCheckpointRequestDto.java`
- Create: `service/service-sparks/src/main/java/io/tacticl/service/sparks/dto/CheckpointDetailDto.java`
- Create: `service/service-sparks/src/main/java/io/tacticl/service/sparks/controller/SparkController.java`
- Create: `service/service-sparks/src/test/java/io/tacticl/service/sparks/controller/SparkControllerTest.java`

- [ ] **Step 1: Create service-sparks/build.gradle.kts**

```kotlin
// service-sparks — Spark REST API + SSE endpoints
plugins {
    `java-library`
}

dependencies {
    implementation(project(":business:business-sparks"))
    implementation(project(":data:data-sparks"))
    implementation(libs.cidadel.service.framework.base)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p service/service-sparks/src/main/java/io/tacticl/service/sparks/{controller,dto}
mkdir -p service/service-sparks/src/test/java/io/tacticl/service/sparks/controller
```

- [ ] **Step 3: Write the failing tests**

```java
// SparkControllerTest.java
package io.tacticl.service.sparks.controller;

import io.cidadel.framework.authorization.AuthenticatedUser;
import io.tacticl.business.sparks.service.*;
import io.tacticl.data.sparks.entity.*;
import io.tacticl.service.sparks.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparkControllerTest {

    @Mock SparkService sparkService;
    @Mock CheckpointService checkpointService;
    @Mock SparkClassifierService classifierService;
    @Mock SparkEventEmitter sparkEventEmitter;
    @InjectMocks SparkController controller;

    private AuthenticatedUser user(String id) {
        AuthenticatedUser u = mock(AuthenticatedUser.class);
        when(u.getUserId()).thenReturn(id);
        return u;
    }

    @Test
    void createSpark_returns201AndSparkSummary() {
        Spark spark = Spark.create("user-1", "build me a REST API");
        when(sparkService.create("user-1", "build me a REST API")).thenReturn(spark);
        when(classifierService.classify("build me a REST API")).thenReturn(SparkType.CODE);
        when(sparkService.classify(eq(spark.getId()), eq("user-1"), eq(SparkType.CODE)))
                .thenReturn(spark);

        ResponseEntity<?> resp = controller.createSpark(
                user("user-1"), new CreateSparkRequestDto("build me a REST API", "session-1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void listSparks_returnsPage() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkService.list("user-1", 0, 20))
                .thenReturn(new PageImpl<>(List.of(spark), PageRequest.of(0, 20), 1));

        ResponseEntity<?> resp = controller.listSparks(user("user-1"), 0, 20);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSpark_foundReturns200() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkService.get("user-1", spark.getId())).thenReturn(Optional.of(spark));

        ResponseEntity<?> resp = controller.getSpark(user("user-1"), spark.getId());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSpark_notFoundReturns404() {
        when(sparkService.get("user-1", "missing")).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.getSpark(user("user-1"), "missing");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelSpark_returns204() {
        ResponseEntity<?> resp = controller.cancelSpark(user("user-1"), "spark-1");

        verify(sparkService).cancel("spark-1", "user-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void streamEvents_returnsSseEmitter() {
        SseEmitter sse = new SseEmitter(30_000L);
        when(sparkEventEmitter.register(eq("spark-1"), any())).thenReturn(sse);

        SseEmitter result = controller.streamEvents(user("user-1"), "spark-1");

        assertThat(result).isNotNull();
    }

    @Test
    void resolveCheckpoint_returns200() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve?");
        when(checkpointService.resolve("cp-1", "spark-1", "user-1",
                CheckpointStatus.APPROVED, "go ahead")).thenReturn(cp);

        ResponseEntity<?> resp = controller.resolveCheckpoint(
                user("user-1"), "spark-1", "cp-1",
                new ResolveCheckpointRequestDto("APPROVE", "go ahead"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

```bash
./gradlew :service:service-sparks:test --tests "io.tacticl.service.sparks.controller.SparkControllerTest" 2>&1 | tail -20
```

Expected: FAIL — class not found

- [ ] **Step 5: Implement DTOs**

```java
// CreateSparkRequestDto.java
// Note: `schedule?` field from SAD section 4.4 is deferred — spark scheduling is out of
// scope for Plan B. Add when scheduling support is implemented.
package io.tacticl.service.sparks.dto;
public record CreateSparkRequestDto(String input, String sessionId) {}

// SparkSummaryDto.java
package io.tacticl.service.sparks.dto;
public record SparkSummaryDto(
    String sparkId, String status, String type, String route,
    String createdAt, String completedAt
) {}

// SparkDetailDto.java
package io.tacticl.service.sparks.dto;
public record SparkDetailDto(
    String sparkId, String input, String status, String type, String route,
    String deviceId, String pipelineRunId, int tokenCost, String modelUsed,
    String createdAt, String startedAt, String completedAt
) {}

// CheckpointDetailDto.java
package io.tacticl.service.sparks.dto;
public record CheckpointDetailDto(
    String checkpointId, String sparkId, String type, String prompt,
    String status, String createdAt, String resolvedAt
) {}

// ResolveCheckpointRequestDto.java
package io.tacticl.service.sparks.dto;
public record ResolveCheckpointRequestDto(String decision, String instructions) {}
```

- [ ] **Step 6: Implement SparkController**

```java
// SparkController.java
package io.tacticl.service.sparks.controller;

import io.cidadel.framework.authorization.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.sparks.service.*;
import io.tacticl.data.sparks.entity.*;
import io.tacticl.service.sparks.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;

@RestController
@RequestMapping("/v1/sparks")
public class SparkController extends BaseController {

    private final SparkService sparkService;
    private final CheckpointService checkpointService;
    private final SparkClassifierService classifierService;
    private final SparkEventEmitter sparkEventEmitter;

    public SparkController(SparkService sparkService,
                           CheckpointService checkpointService,
                           SparkClassifierService classifierService,
                           SparkEventEmitter sparkEventEmitter) {
        this.sparkService = sparkService;
        this.checkpointService = checkpointService;
        this.classifierService = classifierService;
        this.sparkEventEmitter = sparkEventEmitter;
    }

    @Override
    protected String getModuleName() { return "sparks"; }

    @PostMapping
    public ResponseEntity<SparkSummaryDto> createSpark(
            AuthenticatedUser user,
            @RequestBody CreateSparkRequestDto body) {
        Spark spark = sparkService.create(user.getUserId(), body.input());
        SparkType type = classifierService.classify(body.input());
        spark = sparkService.classify(spark.getId(), user.getUserId(), type);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(spark));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listSparks(
            AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Spark> sparksPage = sparkService.list(user.getUserId(), page, size);
        return ResponseEntity.ok(Map.of(
                "content", sparksPage.getContent().stream().map(this::toSummary).toList(),
                "totalElements", sparksPage.getTotalElements(),
                "page", page,
                "size", size
        ));
    }

    @GetMapping("/{sparkId}")
    public ResponseEntity<?> getSpark(AuthenticatedUser user, @PathVariable String sparkId) {
        return sparkService.get(user.getUserId(), sparkId)
                .map(s -> ResponseEntity.ok(toDetail(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{sparkId}")
    public ResponseEntity<Void> cancelSpark(AuthenticatedUser user, @PathVariable String sparkId) {
        sparkService.cancel(sparkId, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{sparkId}/events", produces = "text/event-stream")
    public SseEmitter streamEvents(AuthenticatedUser user, @PathVariable String sparkId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout
        return sparkEventEmitter.register(sparkId, emitter);
    }

    @PostMapping("/{sparkId}/checkpoint/{checkpointId}")
    public ResponseEntity<CheckpointDetailDto> resolveCheckpoint(
            AuthenticatedUser user,
            @PathVariable String sparkId,
            @PathVariable String checkpointId,
            @RequestBody ResolveCheckpointRequestDto body) {
        CheckpointStatus decision = "APPROVE".equalsIgnoreCase(body.decision())
                ? CheckpointStatus.APPROVED : CheckpointStatus.DENIED;
        Checkpoint cp = checkpointService.resolve(
                checkpointId, sparkId, user.getUserId(), decision, body.instructions());
        return ResponseEntity.ok(toCheckpointDto(cp));
    }

    private SparkSummaryDto toSummary(Spark s) {
        return new SparkSummaryDto(
                s.getId(), s.getStatus().name(),
                s.getType() != null ? s.getType().name() : null,
                s.getRoute() != null ? s.getRoute().name() : null,
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getCompletedAt() != null ? s.getCompletedAt().toString() : null
        );
    }

    private SparkDetailDto toDetail(Spark s) {
        return new SparkDetailDto(
                s.getId(), s.getInput(), s.getStatus().name(),
                s.getType() != null ? s.getType().name() : null,
                s.getRoute() != null ? s.getRoute().name() : null,
                s.getDeviceId(), s.getPipelineRunId(),
                s.getTokenCost(), s.getModelUsed(),
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getStartedAt() != null ? s.getStartedAt().toString() : null,
                s.getCompletedAt() != null ? s.getCompletedAt().toString() : null
        );
    }

    private CheckpointDetailDto toCheckpointDto(Checkpoint cp) {
        return new CheckpointDetailDto(
                cp.getId(), cp.getSparkId(), cp.getType().name(), cp.getPrompt(),
                cp.getStatus().name(),
                cp.getCreatedAt() != null ? cp.getCreatedAt().toString() : null,
                cp.getResolvedAt() != null ? cp.getResolvedAt().toString() : null
        );
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
./gradlew :service:service-sparks:test 2>&1 | tail -10
```

Expected: All PASS

- [ ] **Step 8: Run full build**

```bash
./gradlew build -x test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add service/service-sparks/
git commit -m "feat(sparks): add service-sparks with SparkController — POST/GET/DELETE /v1/sparks + SSE events + checkpoint resolution"
```

---

## Chunk 5: gRPC Internal Stub

### Task 10: TacticlInternalService stub

**Files:**
- Create: `service/service-connections/src/main/java/io/tacticl/service/connections/grpc/TacticlInternalRequest.java`
- Create: `service/service-connections/src/main/java/io/tacticl/service/connections/grpc/TacticlInternalResponse.java`
- Create: `service/service-connections/src/main/java/io/tacticl/service/connections/grpc/TacticlInternalService.java`
- Create: `service/service-connections/src/main/java/io/tacticl/service/connections/grpc/TacticlInternalServiceImpl.java`
- Create: `service/service-connections/src/test/java/io/tacticl/service/connections/grpc/TacticlInternalServiceImplTest.java`

**Scope:** This is a Java-interface stub. Full gRPC transport (protobuf codegen, Netty server, mTLS) is handled in the arbiter integration plan. This task wires the business logic so it can be tested independently and slotted into the real gRPC server without further changes.

- [ ] **Step 1: Write the failing tests**

```java
// TacticlInternalServiceImplTest.java
package io.tacticl.service.connections.grpc;

import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.business.connections.service.DeviceRegistryService;
import io.tacticl.business.connections.service.SecretsVaultService;
import io.tacticl.business.connections.service.VaultTokenStore;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.Device;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TacticlInternalServiceImplTest {

    @Mock ConnectionRegistryService connectionRegistryService;
    @Mock SecretsVaultService secretsVaultService;
    @Mock DeviceRegistryService deviceRegistryService;
    @Mock VaultTokenStore vaultTokenStore;
    @InjectMocks TacticlInternalServiceImpl service;

    @Test
    void getAvailableConnections_returnsConnectionsAndDevices() {
        when(connectionRegistryService.listConnections("user-1")).thenReturn(List.of());
        when(deviceRegistryService.listDevices("user-1")).thenReturn(List.of());

        TacticlInternalResponse.GetConnectionsResponse resp =
                service.getAvailableConnections(new TacticlInternalRequest.GetConnectionsRequest("user-1"));

        assertThat(resp.connections()).isEmpty();
        assertThat(resp.devices()).isEmpty();
    }

    @Test
    void getSecret_returnsValue() {
        // Arbiter passes human-readable secret name (e.g. "MY_OPENAI_KEY"), not the MongoDB ID
        when(secretsVaultService.resolveValueByName("user-1", "MY_OPENAI_KEY"))
                .thenReturn(Optional.of("sk-test-value"));

        TacticlInternalResponse.GetSecretResponse resp =
                service.getSecret(new TacticlInternalRequest.GetSecretRequest("user-1", "MY_OPENAI_KEY"));

        assertThat(resp.value()).isEqualTo("sk-test-value");
    }

    @Test
    void getSecret_notFound_returnsEmptyValue() {
        when(secretsVaultService.resolveValueByName("user-1", "MISSING_KEY"))
                .thenReturn(Optional.empty());

        TacticlInternalResponse.GetSecretResponse resp =
                service.getSecret(new TacticlInternalRequest.GetSecretRequest("user-1", "MISSING_KEY"));

        assertThat(resp.value()).isEmpty();
    }

    @Test
    void reportCheckpoint_returnsEmptyCheckpointId() {
        // Stub: returns empty checkpointId until wired to CheckpointService in arbiter integration plan
        TacticlInternalResponse.CheckpointResponse resp =
                service.reportCheckpoint(new TacticlInternalRequest.CheckpointRequest(
                        "spark-1", "APPROVAL", "Approve PR to main?"));

        assertThat(resp.checkpointId()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :service:service-connections:test --tests "io.tacticl.service.connections.grpc.TacticlInternalServiceImplTest" 2>&1 | tail -20
```

Expected: FAIL — class not found

- [ ] **Step 3: Implement request/response types**

```java
// TacticlInternalRequest.java
package io.tacticl.service.connections.grpc;

public class TacticlInternalRequest {
    public record GetConnectionsRequest(String userId) {}
    public record GetSecretRequest(String userId, String secretName) {}
    public record CheckpointRequest(String sparkId, String type, String prompt) {}
}
```

```java
// TacticlInternalResponse.java
package io.tacticl.service.connections.grpc;

import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.Device;
import java.util.List;

public class TacticlInternalResponse {

    public record ConnectionInfo(
        String connectionId, String provider, String accessToken,
        String accountIdentity, List<String> scopes
    ) {}

    public record DeviceInfo(
        String deviceId, String name, String os,
        String status, List<String> capabilities
    ) {}

    public record GetConnectionsResponse(
        List<ConnectionInfo> connections,
        List<DeviceInfo> devices
    ) {}

    public record GetSecretResponse(String value) {}

    public record CheckpointResponse(String checkpointId) {}
}
```

```java
// TacticlInternalService.java
package io.tacticl.service.connections.grpc;

/**
 * Internal service interface for arbiter ↔ tacticl-core communication.
 * Full gRPC transport wiring is handled in the arbiter integration plan.
 * This interface defines the business contract that the gRPC server will delegate to.
 */
public interface TacticlInternalService {

    TacticlInternalResponse.GetConnectionsResponse getAvailableConnections(
            TacticlInternalRequest.GetConnectionsRequest request);

    TacticlInternalResponse.GetSecretResponse getSecret(
            TacticlInternalRequest.GetSecretRequest request);

    TacticlInternalResponse.CheckpointResponse reportCheckpoint(
            TacticlInternalRequest.CheckpointRequest request);
}
```

- [ ] **Step 4: Implement TacticlInternalServiceImpl**

```java
// TacticlInternalServiceImpl.java
package io.tacticl.service.connections.grpc;

import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.business.connections.service.DeviceRegistryService;
import io.tacticl.business.connections.service.SecretsVaultService;
import io.tacticl.business.connections.service.VaultTokenStore;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.Device;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class TacticlInternalServiceImpl implements TacticlInternalService {

    private final ConnectionRegistryService connectionRegistryService;
    private final SecretsVaultService secretsVaultService;
    private final DeviceRegistryService deviceRegistryService;
    private final VaultTokenStore vaultTokenStore;

    public TacticlInternalServiceImpl(ConnectionRegistryService connectionRegistryService,
                                       SecretsVaultService secretsVaultService,
                                       DeviceRegistryService deviceRegistryService,
                                       VaultTokenStore vaultTokenStore) {
        this.connectionRegistryService = connectionRegistryService;
        this.secretsVaultService = secretsVaultService;
        this.deviceRegistryService = deviceRegistryService;
        this.vaultTokenStore = vaultTokenStore;
    }

    @Override
    public TacticlInternalResponse.GetConnectionsResponse getAvailableConnections(
            TacticlInternalRequest.GetConnectionsRequest request) {

        List<TacticlInternalResponse.ConnectionInfo> connections =
                connectionRegistryService.listConnections(request.userId())
                        .stream()
                        .map(c -> {
                            // Resolve live token from Vault for arbiter
                            String accessToken = resolveAccessToken(c);
                            return new TacticlInternalResponse.ConnectionInfo(
                                    c.getId(), c.getProvider(), accessToken,
                                    c.getAccountIdentity(), c.getScopes());
                        }).toList();

        List<TacticlInternalResponse.DeviceInfo> devices =
                deviceRegistryService.listDevices(request.userId())
                        .stream()
                        .map(d -> new TacticlInternalResponse.DeviceInfo(
                                d.getId(), d.getName(), d.getOs(),
                                d.getStatus().name(), d.getCapabilities()))
                        .toList();

        return new TacticlInternalResponse.GetConnectionsResponse(connections, devices);
    }

    @Override
    public TacticlInternalResponse.GetSecretResponse getSecret(
            TacticlInternalRequest.GetSecretRequest request) {
        // Arbiter passes human-readable name (e.g. "MY_OPENAI_KEY"); resolve by name, not ID
        String value = secretsVaultService
                .resolveValueByName(request.userId(), request.secretName())
                .orElse("");
        return new TacticlInternalResponse.GetSecretResponse(value);
    }

    @Override
    public TacticlInternalResponse.CheckpointResponse reportCheckpoint(
            TacticlInternalRequest.CheckpointRequest request) {
        // Checkpoint creation is handled by CheckpointService in business-sparks.
        // The arbiter integration plan wires the gRPC call through to CheckpointService.
        // Stub returns empty checkpointId until wired.
        return new TacticlInternalResponse.CheckpointResponse("");
    }

    private String resolveAccessToken(Connection connection) {
        try {
            var tokens = vaultTokenStore.retrieve(connection.getVaultPath());
            return tokens != null ? tokens.accessToken() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
```

- [ ] **Step 5: Run all service-connections tests**

```bash
./gradlew :service:service-connections:test 2>&1 | tail -15
```

Expected: All PASS

- [ ] **Step 6: Run full build**

```bash
./gradlew build 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL (all tests pass)

- [ ] **Step 7: Commit**

```bash
git add service/service-connections/src/main/java/io/tacticl/service/connections/grpc/
git add service/service-connections/src/test/java/io/tacticl/service/connections/grpc/
git commit -m "feat(grpc): add TacticlInternalService stub — arbiter-callable connection, secret, and checkpoint methods"
```

---

## Final Step: Tag Plan B

After all tasks pass and `./gradlew build` is green:

- [ ] **Tag the completion**

```bash
git tag -a v2.0.0-foundation-plan-b -m "Platform v2 Plan B complete: Secrets Vault, Spark Execution, gRPC stub"
git push origin feature/platform-v2-foundation --tags
```

---

## Troubleshooting Notes

**AnthropicClient API mismatch:** If `MessageRequest.builder()` or `.getFirstMessage()` don't match the actual cidadel API, decompile the JAR: `jar -tf ~/.gradle/caches/modules-*/files-*/**/*anthropic-direct*.jar | grep -i client`. Then `javap -c` the relevant class. The test pattern stays the same — just adjust method names.

**business-sparks missing deps:** If `SseEmitter` not found, add `implementation(rootProject.libs.spring.boot.starter.web)` to `business/business-sparks/build.gradle.kts` (parent provides it but child may need explicit import for the test classpath).

**service-sparks `testRuntimeOnly` missing:** Add `testRuntimeOnly(libs.junit.platform.launcher)` to `service/service-sparks/build.gradle.kts` if tests fail to discover at runtime.

**Device.getCapabilities() not found:** If `Device` entity from Plan A doesn't have a `capabilities` list yet, add `private List<String> capabilities = new ArrayList<>();` to the entity and a getter. Not needed for the entity tests — only the gRPC stub uses it.
