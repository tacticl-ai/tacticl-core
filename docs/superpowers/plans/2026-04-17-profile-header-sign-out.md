# Profile Header + Sign Out — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a MongoDB-backed `UserProfile` entity, a `GET /v1/users/me` endpoint populated lazily from PASETO token claims, and wire the profile avatar dropdown + sign-out into the tacticl-web header.

**Architecture:** Three new Gradle modules (`data-profile`, `business-profile`, `service-profile`) follow the established layered pattern. `UserProfile` documents live in the `user_profiles` MongoDB collection, keyed by `cidadelUserId` FK. On first `GET /v1/users/me` the service upserts from token claims; subsequent calls read the cached document. Sign-out is client-side token clearing.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Spring Data MongoDB, cidadel `framework-authorization` 1.0.21 (`AuthenticatedUser`), Flapdoodle embedded MongoDB (repository tests), Mockito (service tests), `@WebMvcTest` (controller tests), React + TypeScript (tacticl-web frontend, separate repo)

---

## Scope

This plan covers **tacticl-core backend** only. The tacticl-web frontend is a separate repo — frontend steps are described in Chunk 5 as a reference spec for a separate plan.

---

## File Map

### New — `data/data-profile`
| File | Responsibility |
|------|----------------|
| `data/data-profile/build.gradle.kts` | Module build config |
| `data/data-profile/src/main/java/io/tacticl/data/profile/base/BaseMongoEntity.java` | MongoDB base: id, isActive, createdAt, updatedAt |
| `data/data-profile/src/main/java/io/tacticl/data/profile/config/MongoAuditingConfig.java` | `@EnableMongoAuditing` for this module |
| `data/data-profile/src/main/java/io/tacticl/data/profile/entity/UserProfile.java` | `@Document("user_profiles")` |
| `data/data-profile/src/main/java/io/tacticl/data/profile/repository/UserProfileRepository.java` | `MongoRepository` with `findByCidadelUserIdAndIsActiveTrue` |
| `data/data-profile/src/test/java/io/tacticl/data/profile/repository/UserProfileRepositoryTest.java` | Repository integration test (Flapdoodle) |

### New — `business/business-profile`
| File | Responsibility |
|------|----------------|
| `business/business-profile/build.gradle.kts` | Module build config |
| `business/business-profile/src/main/java/io/tacticl/business/profile/service/UserProfileService.java` | `getOrCreate`: find-or-insert, race condition handling |
| `business/business-profile/src/test/java/io/tacticl/business/profile/service/UserProfileServiceTest.java` | Unit test (mock repository) |

### New — `service/service-profile`
| File | Responsibility |
|------|----------------|
| `service/service-profile/build.gradle.kts` | Module build config |
| `service/service-profile/src/main/java/io/tacticl/service/profile/dto/UserProfileResponse.java` | Response record |
| `service/service-profile/src/main/java/io/tacticl/service/profile/controller/ProfileController.java` | `GET /v1/users/me` |
| `service/service-profile/src/test/java/io/tacticl/service/profile/controller/ProfileControllerTest.java` | `@WebMvcTest` controller test |

### Modified
| File | Change |
|------|--------|
| `settings.gradle.kts` | Add 3 new `include(":…")` lines |
| `application-api/build.gradle.kts` | Add `project(":service:service-profile")` |
| `application-api/src/main/resources/application.yml` | Add `spring.data.mongodb.auto-index-creation: true` |
| `application-api/src/main/resources/application-local.yml` | Create if absent — local dev MongoDB URI |

---

## Chunk 1: Module Scaffolding + MongoDB Config

### Task 1: Verify MongoDB URI config and enable auto-index-creation

**Files:**
- Check: `application-api/src/main/resources/`
- Modify: `application-api/src/main/resources/application.yml`
- Create: `application-api/src/main/resources/application-local.yml` (if absent)

- [ ] **Step 1: Check what MongoDB config already exists**

```bash
find /Users/cuztomizer/Documents/GitHub/tacticl-core/application-api/src/main/resources -name "*.yml" | xargs grep -l "mongodb" 2>/dev/null
```

Expected: files listing existing MongoDB URI config. The app already uses MongoDB (data-connections, data-sparks etc.), so some form of URI config exists. Identify where the URI is set before adding anything.

- [ ] **Step 2: Add auto-index-creation to application.yml**

In `application-api/src/main/resources/application.yml`, add (merge with existing `spring:` block if present):

```yaml
spring:
  data:
    mongodb:
      auto-index-creation: true
```

- [ ] **Step 3: Create application-local.yml for local dev bootRun**

If `application-api/src/main/resources/application-local.yml` does not exist, create it:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/tacticl
      auto-index-creation: true
```

If it already exists, add only the missing lines.

- [ ] **Step 4: Commit**

```bash
git add application-api/src/main/resources/
git commit -m "chore(config): enable MongoDB auto-index-creation, add local dev URI"
```

---

### Task 2: Register modules and wire application-api

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `application-api/build.gradle.kts`
- Create: `data/data-profile/build.gradle.kts`
- Create: `business/business-profile/build.gradle.kts`
- Create: `service/service-profile/build.gradle.kts`

- [ ] **Step 1: Register modules in settings.gradle.kts**

Append to the end of `settings.gradle.kts`:

```kotlin
include(":data:data-profile")
include(":business:business-profile")
include(":service:service-profile")
```

- [ ] **Step 2: Wire service-profile into application-api**

In `application-api/build.gradle.kts`, inside `dependencies { }`, add alongside the other `service:service-*` entries:

```kotlin
implementation(project(":service:service-profile"))
```

- [ ] **Step 3: Create data/data-profile/build.gradle.kts**

Note: `data/build.gradle.kts` (parent) already provides `spring-boot-starter-data-mongodb`, `framework-exception`, `framework-logging` to all data submodules. Check an existing sibling (e.g., `data/data-sparks/build.gradle.kts`) to confirm the pattern, then create:

```kotlin
plugins {
    `java-library`
}

dependencies {
    // parent data/build.gradle.kts provides: spring-boot-starter-data-mongodb, framework-exception, framework-logging
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring4x:4.20.0")
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
```

Check `gradle/libs.versions.toml` for an existing flapdoodle alias first:
```bash
grep -i flapdoodle /Users/cuztomizer/Documents/GitHub/tacticl-core/gradle/libs.versions.toml
```
If an alias exists (e.g., `libs.flapdoodle.embed.mongo`), use that instead of the string literal.

- [ ] **Step 4: Create business/business-profile/build.gradle.kts**

Note: `business/build.gradle.kts` (parent) already provides `framework-exception`, `framework-logging`, `spring-boot-starter-web`, `jackson`, and all test deps (`spring-boot-starter-test`, `junit-jupiter`, `junit-platform-launcher`). Only add what isn't in the parent:

```kotlin
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // parent business/build.gradle.kts provides: framework-exception, framework-logging, spring-web, jackson, test deps
    implementation(project(":data:data-profile"))
    implementation(libs.cidadel.framework.authorization)  // needed for AuthenticatedUser; not in business parent
}
```

- [ ] **Step 5: Create service/service-profile/build.gradle.kts**

Note: `service/build.gradle.kts` (parent) already provides `framework-authorization`, `framework-exception`, `framework-logging`, `spring-boot-starter-web`, `spring-boot-starter-validation`, `springdoc-openapi`, `jackson`, and test deps (`spring-boot-starter-test`, `junit-jupiter`). Only add what isn't in the parent:

```kotlin
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // parent service/build.gradle.kts provides: framework-authorization, framework-exception, spring-web, test deps
    implementation(project(":business:business-profile"))
    implementation(libs.cidadel.service.framework.base)   // BaseController
    implementation(libs.cidadel.framework.api.docs)       // OpenAPI/Swagger

    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 6: Verify the build resolves with empty modules**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core
./gradlew projects
```
Expected: `data-profile`, `business-profile`, `service-profile` appear in the module tree.

```bash
./gradlew build -x test
```
Expected: BUILD SUCCESSFUL (no source files yet, modules resolve cleanly)

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts application-api/build.gradle.kts \
        data/data-profile/build.gradle.kts \
        business/business-profile/build.gradle.kts \
        service/service-profile/build.gradle.kts
git commit -m "chore(modules): scaffold data-profile, business-profile, service-profile modules"
```

---

## Chunk 2: data-profile Module

### Task 3: UserProfile entity, repository, and tests

**Files:**
- Create: `data/data-profile/src/main/java/io/tacticl/data/profile/base/BaseMongoEntity.java`
- Create: `data/data-profile/src/main/java/io/tacticl/data/profile/config/MongoAuditingConfig.java`
- Create: `data/data-profile/src/main/java/io/tacticl/data/profile/entity/UserProfile.java`
- Create: `data/data-profile/src/main/java/io/tacticl/data/profile/repository/UserProfileRepository.java`
- Test: `data/data-profile/src/test/java/io/tacticl/data/profile/repository/UserProfileRepositoryTest.java`

- [ ] **Step 1: Write the failing repository test**

Create `data/data-profile/src/test/java/io/tacticl/data/profile/repository/UserProfileRepositoryTest.java`:

```java
package io.tacticl.data.profile.repository;

import io.tacticl.data.profile.entity.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class UserProfileRepositoryTest {

    @Autowired
    private UserProfileRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByCidadelUserIdAndIsActiveTrue_returnsProfile_whenExists() {
        var profile = UserProfile.create("user-123", "Gabriel J.", "g@example.com");
        repository.save(profile);

        var found = repository.findByCidadelUserIdAndIsActiveTrue("user-123");

        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Gabriel J.");
        assertThat(found.get().getEmail()).isEqualTo("g@example.com");
        assertThat(found.get().getAvatarUrl()).isNull();
    }

    @Test
    void findByCidadelUserIdAndIsActiveTrue_returnsEmpty_whenInactive() {
        var profile = UserProfile.create("user-456", "Inactive User", "inactive@example.com");
        profile.setActive(false);
        repository.save(profile);

        var found = repository.findByCidadelUserIdAndIsActiveTrue("user-456");

        assertThat(found).isEmpty();
    }

    @Test
    void findByCidadelUserIdAndIsActiveTrue_returnsEmpty_whenNotFound() {
        assertThat(repository.findByCidadelUserIdAndIsActiveTrue("nonexistent")).isEmpty();
    }

    @Test
    void save_setsCreatedAtAndUpdatedAt() {
        var profile = UserProfile.create("user-789", "Audit User", "audit@example.com");
        var saved = repository.save(profile);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
./gradlew :data:data-profile:test
```
Expected: FAIL — `UserProfile`, `UserProfileRepository` do not exist yet.

- [ ] **Step 3: Create BaseMongoEntity**

Create `data/data-profile/src/main/java/io/tacticl/data/profile/base/BaseMongoEntity.java`:

```java
package io.tacticl.data.profile.base;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

public abstract class BaseMongoEntity {

    @Id
    private String id;

    private boolean isActive = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 4: Create MongoAuditingConfig**

Create `data/data-profile/src/main/java/io/tacticl/data/profile/config/MongoAuditingConfig.java`:

```java
package io.tacticl.data.profile.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {}
```

- [ ] **Step 5: Create UserProfile entity**

Create `data/data-profile/src/main/java/io/tacticl/data/profile/entity/UserProfile.java`:

```java
package io.tacticl.data.profile.entity;

import io.tacticl.data.profile.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user_profiles")
public class UserProfile extends BaseMongoEntity {

    @Indexed(unique = true)
    private String cidadelUserId;

    private String displayName;
    private String email;
    private String avatarUrl;

    public static UserProfile create(String cidadelUserId, String displayName, String email) {
        var profile = new UserProfile();
        profile.cidadelUserId = cidadelUserId;
        profile.displayName = displayName;
        profile.email = email;
        return profile;
    }

    public String getCidadelUserId() { return cidadelUserId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getAvatarUrl() { return avatarUrl; }
}
```

- [ ] **Step 6: Create UserProfileRepository**

Create `data/data-profile/src/main/java/io/tacticl/data/profile/repository/UserProfileRepository.java`:

```java
package io.tacticl.data.profile.repository;

import io.tacticl.data.profile.entity.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
    Optional<UserProfile> findByCidadelUserIdAndIsActiveTrue(String cidadelUserId);
}
```

- [ ] **Step 7: Run tests — verify they pass**

```bash
./gradlew :data:data-profile:test
```
Expected: 4 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add data/data-profile/src/
git commit -m "feat(data-profile): add UserProfile MongoDB entity and repository"
```

---

## Chunk 3: business-profile Module

### Task 4: UserProfileService

**Files:**
- Create: `business/business-profile/src/main/java/io/tacticl/business/profile/service/UserProfileService.java`
- Test: `business/business-profile/src/test/java/io/tacticl/business/profile/service/UserProfileServiceTest.java`

- [ ] **Step 1: Write the failing service tests**

Create `business/business-profile/src/test/java/io/tacticl/business/profile/service/UserProfileServiceTest.java`:

```java
package io.tacticl.business.profile.service;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.data.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userProfileRepository);
    }

    private AuthenticatedUser user(String userId, String name, String email) {
        return AuthenticatedUser.builder()
            .userId(userId).name(name).email(email).build();
    }

    @Test
    void getOrCreate_returnsExisting_whenProfileFound() {
        var existing = UserProfile.create("u1", "Gabriel", "g@example.com");
        when(userProfileRepository.findByCidadelUserIdAndIsActiveTrue("u1"))
            .thenReturn(Optional.of(existing));

        var result = userProfileService.getOrCreate(user("u1", "Gabriel", "g@example.com"));

        assertThat(result.getDisplayName()).isEqualTo("Gabriel");
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void getOrCreate_createsAndReturnsProfile_whenNotFound() {
        var created = UserProfile.create("u2", "New User", "new@example.com");
        when(userProfileRepository.findByCidadelUserIdAndIsActiveTrue("u2"))
            .thenReturn(Optional.empty());
        when(userProfileRepository.save(any())).thenReturn(created);

        var result = userProfileService.getOrCreate(user("u2", "New User", "new@example.com"));

        assertThat(result.getDisplayName()).isEqualTo("New User");
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void getOrCreate_reReadsOnDuplicateKey_andReturnsExisting() {
        var existing = UserProfile.create("u3", "Race User", "race@example.com");
        when(userProfileRepository.findByCidadelUserIdAndIsActiveTrue("u3"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any())).thenThrow(new DuplicateKeyException("dup"));

        var result = userProfileService.getOrCreate(user("u3", "Race User", "race@example.com"));

        assertThat(result.getDisplayName()).isEqualTo("Race User");
    }

    @Test
    void getOrCreate_throws_whenNameIsNull() {
        assertThatThrownBy(() -> userProfileService.getOrCreate(user("u4", null, "e@example.com")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void getOrCreate_throws_whenEmailIsNull() {
        assertThatThrownBy(() -> userProfileService.getOrCreate(user("u5", "Name", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
    }
}
```

Note on `AuthenticatedUser.builder()`: confirm this builder exists by checking the class in the JAR. If the builder takes different parameters, adjust accordingly — the key is to produce an `AuthenticatedUser` with a known `userId`, `name`, and `email`.

- [ ] **Step 2: Run test — verify it fails**

```bash
./gradlew :business:business-profile:test
```
Expected: FAIL — `UserProfileService` does not exist.

- [ ] **Step 3: Implement UserProfileService**

Create `business/business-profile/src/main/java/io/tacticl/business/profile/service/UserProfileService.java`:

```java
package io.tacticl.business.profile.service;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.data.profile.repository.UserProfileRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfile getOrCreate(AuthenticatedUser user) {
        if (user.getName() == null) {
            throw new IllegalArgumentException("Token missing name claim");
        }
        if (user.getEmail() == null) {
            throw new IllegalArgumentException("Token missing email claim");
        }
        return userProfileRepository.findByCidadelUserIdAndIsActiveTrue(user.getUserId())
            .orElseGet(() -> insertProfile(user));
    }

    private UserProfile insertProfile(AuthenticatedUser user) {
        try {
            return userProfileRepository.save(
                UserProfile.create(user.getUserId(), user.getName(), user.getEmail())
            );
        } catch (DuplicateKeyException e) {
            return userProfileRepository.findByCidadelUserIdAndIsActiveTrue(user.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                    "UserProfile not found after DuplicateKeyException for userId: " + user.getUserId()
                ));
        }
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
./gradlew :business:business-profile:test
```
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add business/business-profile/src/
git commit -m "feat(business-profile): add UserProfileService with getOrCreate and DuplicateKeyException handling"
```

---

## Chunk 4: service-profile Module

### Task 5: ProfileController and DTO

**Pattern note:** All controller tests in this codebase use `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` and call controller methods directly — no MockMvc, no Spring context. See `SparkControllerTest.java` and `PipelineControllerTest.java` for reference. `AuthenticatedUser` is mocked with `mock(AuthenticatedUser.class)`.

**Exact FQNs** (confirmed from `framework-authorization-1.0.21.jar` and `service-framework-base` JAR):
- `@AuthUser` → `io.cidadel.framework.authorization.annotation.AuthUser`
- `@RequireAuth` → `io.cidadel.framework.authorization.annotation.RequireAuth`
- `AuthenticatedUser` → `io.cidadel.framework.authorization.context.AuthenticatedUser`
- `BaseController` → `io.cidadel.service.base.controller.BaseController`

**Files:**
- Create: `service/service-profile/src/main/java/io/tacticl/service/profile/dto/UserProfileResponse.java`
- Create: `service/service-profile/src/main/java/io/tacticl/service/profile/controller/ProfileController.java`
- Test: `service/service-profile/src/test/java/io/tacticl/service/profile/controller/ProfileControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Create `service/service-profile/src/test/java/io/tacticl/service/profile/controller/ProfileControllerTest.java`:

```java
package io.tacticl.service.profile.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.profile.service.UserProfileService;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.service.profile.dto.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private ProfileController profileController;

    private AuthenticatedUser mockUser(String id, String name, String email) {
        AuthenticatedUser u = mock(AuthenticatedUser.class);
        when(u.getUserId()).thenReturn(id);
        when(u.getName()).thenReturn(name);
        when(u.getEmail()).thenReturn(email);
        return u;
    }

    @Test
    void getProfile_returns200_withDisplayNameAndEmail() {
        var profile = UserProfile.create("user-1", "Gabriel J.", "g@example.com");
        when(userProfileService.getOrCreate(any(AuthenticatedUser.class))).thenReturn(profile);

        var response = profileController.getProfile(mockUser("user-1", "Gabriel J.", "g@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().displayName()).isEqualTo("Gabriel J.");
        assertThat(response.getBody().email()).isEqualTo("g@example.com");
        assertThat(response.getBody().avatarUrl()).isNull();
    }

    @Test
    void getProfile_propagatesException_whenServiceThrows() {
        when(userProfileService.getOrCreate(any(AuthenticatedUser.class)))
            .thenThrow(new IllegalArgumentException("Token missing name claim"));

        assertThatThrownBy(() ->
            profileController.getProfile(mockUser("user-1", null, "e@example.com"))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("name");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
./gradlew :service:service-profile:test
```
Expected: FAIL — `ProfileController` and `UserProfileResponse` do not exist yet.

- [ ] **Step 3: Create UserProfileResponse DTO**

Create `service/service-profile/src/main/java/io/tacticl/service/profile/dto/UserProfileResponse.java`:

```java
package io.tacticl.service.profile.dto;

public record UserProfileResponse(
    String displayName,
    String email,
    String avatarUrl
) {}
```

- [ ] **Step 4: Create ProfileController**

Create `service/service-profile/src/main/java/io/tacticl/service/profile/controller/ProfileController.java`:

```java
package io.tacticl.service.profile.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.profile.service.UserProfileService;
import io.tacticl.service.profile.dto.UserProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class ProfileController extends BaseController {

    private final UserProfileService userProfileService;

    public ProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Override
    protected String getModuleName() {
        return "profile";
    }

    @GetMapping("/me")
    @RequireAuth
    public ResponseEntity<UserProfileResponse> getProfile(@AuthUser AuthenticatedUser user) {
        var profile = userProfileService.getOrCreate(user);
        return ResponseEntity.ok(new UserProfileResponse(
            profile.getDisplayName(),
            profile.getEmail(),
            profile.getAvatarUrl()
        ));
    }
}
```

- [ ] **Step 5: Verify compilation in isolation before running tests**

```bash
./gradlew :service:service-profile:compileJava
```
Expected: BUILD SUCCESSFUL with no import errors. If any import fails, check that `libs.cidadel.service.framework.base` is wired in the module's `build.gradle.kts` and that the FQNs above match — run `jar tf ~/.gradle/caches/modules-2/files-2.1/io.cidadel/service-framework-base/**/*.jar | grep BaseController` to confirm.

- [ ] **Step 6: Run controller tests — verify they pass**

```bash
./gradlew :service:service-profile:test
```
Expected: 2 tests PASS.

- [ ] **Step 7: Run full build + all tests**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL, all tests pass across all modules.

- [ ] **Step 8: Commit**

```bash
git add service/service-profile/src/
git commit -m "feat(service-profile): add GET /v1/users/me ProfileController"
```

---

## Chunk 5: Frontend Reference (tacticl-web — create separate plan in that repo)

This chunk describes what tacticl-web needs. It is **not** implemented here.

### New / modified files in tacticl-web

| File | Responsibility |
|------|----------------|
| `src/api/profile.ts` | `getProfile(): Promise<UserProfileResponse>` → `GET /v1/users/me` |
| `src/context/AuthContext.tsx` | Add `profile` field to auth state; fetch on mount after auth |
| `src/components/header/ProfileAvatar.tsx` | Avatar circle (initials fallback) + dropdown with name/email/sign-out |
| `src/components/header/Header.tsx` | Render `<ProfileAvatar />` in top-right, replacing or supplementing existing icons |

### ProfileAvatar behavior

- Show `avatarUrl` as `<img>` if present; otherwise gradient circle with first initial of `displayName`
- Click → dropdown overlay: `displayName` (bold), `email` (muted), `<hr>`, red "Sign out" button
- Sign out handler: clear auth token from wherever it lives (`localStorage`, cookie, context) → redirect to login route
- Loading state: show skeleton/spinner while `getProfile()` is in flight
- Error state: if `GET /v1/users/me` fails, show a generic avatar with no dropdown (don't break the header)
