# PDLC Role Skipping, Console LLM Override & API Versioning — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable users to skip PDLC pipeline roles via keywords/API, let admins override LLM engine+model per step and role via Console endpoints, and migrate all endpoints from `/api/*` to `/v1/*`.

**Architecture:** Three independent features sharing the same PR. RoleSkipParser is a stateless regex utility consumed by AgentController. Console LLM override adds a new Firestore collection (`ai_role_overrides`) with entity/repo/service/controller following existing patterns. API versioning is a mechanical `@RequestMapping` prefix change across all controllers.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Firestore, PASETO auth, JUnit 6 + Mockito

**Spec:** `docs/superpowers/specs/2026-03-27-pdlc-role-skipping-console-llm-override-api-versioning-design.md`

---

## Chunk 1: Foundation (Tasks 1-4, parallelizable)

### Task 1: RoleSkipParser — NL keyword extraction

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/RoleSkipParser.java`
- Create: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/RoleSkipParserTest.java`

- [ ] **Step 1: Write failing tests**

```java
package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleSkipParserTest {

    @Test
    void parse_skipReview_returnsReviewer() {
        Set<PdlcRole> result = RoleSkipParser.parse("Fix the login bug, skip review");
        assertEquals(Set.of(PdlcRole.REVIEWER), result);
    }

    @Test
    void parse_noTesting_returnsTester() {
        Set<PdlcRole> result = RoleSkipParser.parse("Refactor auth module, no testing needed");
        assertEquals(Set.of(PdlcRole.TESTER), result);
    }

    @Test
    void parse_skipSecurity_returnsSecurityAnalyst() {
        Set<PdlcRole> result = RoleSkipParser.parse("Add a tooltip, skip security check");
        assertEquals(Set.of(PdlcRole.SECURITY_ANALYST), result);
    }

    @Test
    void parse_skipDocs_returnsTechnicalWriter() {
        Set<PdlcRole> result = RoleSkipParser.parse("Update the endpoint, skip docs");
        assertEquals(Set.of(PdlcRole.TECHNICAL_WRITER), result);
    }

    @Test
    void parse_justImplement_returnsAllExceptImplementer() {
        Set<PdlcRole> result = RoleSkipParser.parse("just implement the fix");
        // Should skip everything except IMPLEMENTER
        assertFalse(result.contains(PdlcRole.IMPLEMENTER));
        assertTrue(result.contains(PdlcRole.REVIEWER));
        assertTrue(result.contains(PdlcRole.TESTER));
        assertTrue(result.contains(PdlcRole.PM));
    }

    @Test
    void parse_skipPlanning_returnsPmAndPlanner() {
        Set<PdlcRole> result = RoleSkipParser.parse("skip planning, just code it");
        assertTrue(result.contains(PdlcRole.PM));
        assertTrue(result.contains(PdlcRole.PLANNER));
    }

    @Test
    void parse_noRetro_returnsRetroAnalyst() {
        Set<PdlcRole> result = RoleSkipParser.parse("Fix it, no retro");
        assertEquals(Set.of(PdlcRole.RETRO_ANALYST), result);
    }

    @Test
    void parse_multipleSkips_returnsAll() {
        Set<PdlcRole> result = RoleSkipParser.parse("Fix it, skip review, no testing, skip docs");
        assertTrue(result.contains(PdlcRole.REVIEWER));
        assertTrue(result.contains(PdlcRole.TESTER));
        assertTrue(result.contains(PdlcRole.TECHNICAL_WRITER));
    }

    @Test
    void parse_noSkipKeywords_returnsEmpty() {
        Set<PdlcRole> result = RoleSkipParser.parse("Build a new auth service with OAuth2");
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_nullInput_returnsEmpty() {
        Set<PdlcRole> result = RoleSkipParser.parse(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_skipDesign_returnsDesigner() {
        Set<PdlcRole> result = RoleSkipParser.parse("skip design, just implement");
        assertTrue(result.contains(PdlcRole.DESIGNER));
    }

    @Test
    void parse_skipResearch_returnsResearcher() {
        Set<PdlcRole> result = RoleSkipParser.parse("I already researched this, skip research");
        assertEquals(Set.of(PdlcRole.RESEARCHER), result);
    }

    @Test
    void parse_skipDevops_returnsDevops() {
        Set<PdlcRole> result = RoleSkipParser.parse("no deploy needed");
        assertEquals(Set.of(PdlcRole.DEVOPS), result);
    }

    @Test
    void parse_implementOnly_skipsAllExceptImplementer() {
        Set<PdlcRole> result = RoleSkipParser.parse("implement only");
        assertFalse(result.contains(PdlcRole.IMPLEMENTER));
        assertEquals(11, result.size()); // All 12 roles minus IMPLEMENTER
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :business:business-agent:test --tests "io.strategiz.social.business.agent.pipeline.RoleSkipParserTest" 2>&1 | tail -5`
Expected: Compilation error — RoleSkipParser does not exist

- [ ] **Step 3: Implement RoleSkipParser**

```java
package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stateless utility that extracts PDLC role skip intent from natural language command text.
 * Returns the set of roles the user wants to skip.
 */
public final class RoleSkipParser {

    private RoleSkipParser() {}

    private static final Map<Pattern, Set<PdlcRole>> SKIP_PATTERNS = new LinkedHashMap<>();

    static {
        // "just implement" / "implement only" → skip everything except IMPLEMENTER
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(just\\s+implement|implement\\s+only)\\b", Pattern.CASE_INSENSITIVE),
                allExcept(PdlcRole.IMPLEMENTER));

        // Individual role skips
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(skip\\s+review|no\\s+review)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.REVIEWER));
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(skip\\s+test|no\\s+test|don'?t\\s+test)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.TESTER));
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(skip\\s+security|no\\s+security)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.SECURITY_ANALYST));
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(skip\\s+docs?|no\\s+doc|skip\\s+documentation|no\\s+documentation)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.TECHNICAL_WRITER));
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(skip\\s+plan|no\\s+plan)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.PM, PdlcRole.PLANNER));
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(no\\s+retro|skip\\s+retro)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.RETRO_ANALYST));
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(skip\\s+design|no\\s+design)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.DESIGNER));
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(skip\\s+research|no\\s+research)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.RESEARCHER));
        SKIP_PATTERNS.put(
                Pattern.compile("\\b(skip\\s+devops|no\\s+deploy)\\b", Pattern.CASE_INSENSITIVE),
                Set.of(PdlcRole.DEVOPS));
    }

    /**
     * Parse command text and return the set of PDLC roles to skip.
     * @param commandText the user's command text (may be null)
     * @return set of roles to skip; empty if no skip intent detected
     */
    public static Set<PdlcRole> parse(String commandText) {
        if (commandText == null || commandText.isBlank()) {
            return Set.of();
        }

        EnumSet<PdlcRole> skipped = EnumSet.noneOf(PdlcRole.class);
        for (Map.Entry<Pattern, Set<PdlcRole>> entry : SKIP_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(commandText).find()) {
                skipped.addAll(entry.getValue());
            }
        }
        return Set.copyOf(skipped);
    }

    private static Set<PdlcRole> allExcept(PdlcRole... keep) {
        EnumSet<PdlcRole> all = EnumSet.allOf(PdlcRole.class);
        all.removeAll(Arrays.asList(keep));
        return Set.copyOf(all);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :business:business-agent:test --tests "io.strategiz.social.business.agent.pipeline.RoleSkipParserTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/RoleSkipParser.java business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/RoleSkipParserTest.java
git commit -m "feat(pipeline): add RoleSkipParser for NL-based PDLC role skipping"
```

---

### Task 2: AiRoleOverride entity + repository

**Files:**
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/AiRoleOverride.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/repository/AiRoleOverrideRepository.java`
- Create: `data/data-social/src/test/java/io/strategiz/social/data/entity/AiRoleOverrideTest.java`

- [ ] **Step 1: Write failing test for entity**

```java
package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AiRoleOverrideTest {

    @Test
    void entity_setsAndGetsAllFields() {
        AiRoleOverride override = new AiRoleOverride();
        override.setId("IMPLEMENTER");
        override.setRole("IMPLEMENTER");
        override.setEngineId("anthropic-agentic");
        override.setModel("claude-opus-4-6");
        override.setUpdatedBy("admin-123");

        assertEquals("IMPLEMENTER", override.getId());
        assertEquals("IMPLEMENTER", override.getRole());
        assertEquals("anthropic-agentic", override.getEngineId());
        assertEquals("claude-opus-4-6", override.getModel());
        assertEquals("admin-123", override.getUpdatedBy());
    }

    @Test
    void entity_extendsBaseEntity() {
        AiRoleOverride override = new AiRoleOverride();
        assertNotNull(override); // BaseEntity provides isActive, createdDate, etc.
        assertTrue(override.isActive());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.AiRoleOverrideTest" 2>&1 | tail -5`
Expected: Compilation error — AiRoleOverride does not exist

- [ ] **Step 3: Implement AiRoleOverride entity**

```java
package io.strategiz.social.data.entity;

import io.cidadel.data.base.entity.BaseEntity;

/**
 * Firestore entity for admin role-level AI engine overrides.
 * Collection: {@code ai_role_overrides}, document ID = role name (e.g. "IMPLEMENTER").
 */
public class AiRoleOverride extends BaseEntity {

    private String role;
    private String engineId;
    private String model;
    private String updatedBy;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getEngineId() { return engineId; }
    public void setEngineId(String engineId) { this.engineId = engineId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
```

- [ ] **Step 4: Implement AiRoleOverrideRepository**

```java
package io.strategiz.social.data.repository;

import io.cidadel.data.base.repository.FirestoreRepository;
import io.strategiz.social.data.entity.AiRoleOverride;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AiRoleOverride} entities.
 * Collection: {@code ai_role_overrides}.
 */
@Repository
public class AiRoleOverrideRepository extends FirestoreRepository<AiRoleOverride> {

    public AiRoleOverrideRepository() {
        super(AiRoleOverride.class, "ai_role_overrides");
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.AiRoleOverrideTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/AiRoleOverride.java data/data-social/src/main/java/io/strategiz/social/data/repository/AiRoleOverrideRepository.java data/data-social/src/test/java/io/strategiz/social/data/entity/AiRoleOverrideTest.java
git commit -m "feat(data): add AiRoleOverride entity and repository for console LLM override"
```

---

### Task 3: skipRoles field on AgentCommandRequest

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/AgentCommandRequest.java` (add `skipRoles` field after line 20)

- [ ] **Step 1: Add skipRoles field and getter/setter**

Add after the `playbook` field (line 20):

```java
/** Optional list of PDLC role names to skip (e.g. ["REVIEWER", "TESTER"]). */
private List<String> skipRoles;
```

Add getter/setter after existing ones:

```java
public List<String> getSkipRoles() { return skipRoles; }
public void setSkipRoles(List<String> skipRoles) { this.skipRoles = skipRoles; }
```

Add `import java.util.List;` to imports.

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :service:service-agent:compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/AgentCommandRequest.java
git commit -m "feat(dto): add skipRoles field to AgentCommandRequest"
```

---

### Task 4: API versioning — migrate `/api/*` to `/v1/*`

**Files to modify** (all `@RequestMapping` annotations):
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java` line 60: `/api/agent` → `/v1/agent`
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AdminController.java` line 21: `/api/admin` → `/v1/console`
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/SettingsController.java` line 40: `/api/settings` → `/v1/settings`
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/DeviceController.java` line 45: `/api/devices` → `/v1/devices`
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/PipelineController.java` line 41: `/api` → `/v1`
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/CredentialController.java` line 23: `/api` → `/v1`
- `service/service-spark/src/main/java/io/strategiz/social/service/spark/controller/SparkController.java` line 34: `/api/sparks` → `/v1/sparks`
- `service/service-social/src/main/java/io/strategiz/social/service/social/controller/SocialOAuthController.java` line 27: `/api/social/oauth` → `/v1/social/oauth`
- `service/service-social/src/main/java/io/strategiz/social/service/social/controller/SocialPostController.java` line 34: `/api/social/posts` → `/v1/social/posts`
- `service/service-social/src/main/java/io/strategiz/social/service/social/controller/SocialIntegrationController.java` line 26: `/api/social/integrations` → `/v1/social/integrations`
- `service/service-token/src/main/java/io/strategiz/social/service/token/controller/TokenController.java` line 32: `/api/tokens` → `/v1/tokens`
- `service/service-repo/src/main/java/io/strategiz/social/service/repo/controller/RepoController.java` line 33: `/api/repos` → `/v1/repos`
- `service/service-checkpoint/src/main/java/io/strategiz/social/service/checkpoint/controller/CheckpointController.java` line 30: `/api/checkpoints` → `/v1/checkpoints`

Also rename AdminController class → ConsoleAdminController and update the `@Tag` annotation.

**Test files to update** (any test that references `/api/` paths or imports AdminController):
- `service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/AgentControllerPipelineTest.java` — no path references, just class imports (already updated to CloudOrchestratorService)
- Any integration tests referencing `/api/` paths in MockMvc calls

- [ ] **Step 1: Update all @RequestMapping annotations**

For each controller file listed above, change the `@RequestMapping` value:
- `/api/agent` → `/v1/agent`
- `/api/admin` → `/v1/console`
- `/api/sparks` → `/v1/sparks`
- `/api/social/oauth` → `/v1/social/oauth`
- `/api/social/posts` → `/v1/social/posts`
- `/api/social/integrations` → `/v1/social/integrations`
- `/api/settings` → `/v1/settings`
- `/api/devices` → `/v1/devices`
- `/api/tokens` → `/v1/tokens`
- `/api/repos` → `/v1/repos`
- `/api/checkpoints` → `/v1/checkpoints`
- `/api` (PipelineController, CredentialController) → `/v1`

- [ ] **Step 2: Rename AdminController → ConsoleAdminController**

Rename the class and file. Update `@Tag` annotation from "Admin" to "Console". Keep `@RequireScope("admin")` auth.

- [ ] **Step 3: Update test files referencing old paths**

Search for `/api/` in test files and update to `/v1/`. Search for `AdminController` imports and update to `ConsoleAdminController`.

- [ ] **Step 4: Verify full build compiles**

Run: `./gradlew build -x test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run all tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(api): migrate all endpoints from /api/* to /v1/*, rename AdminController to ConsoleAdminController"
```

---

## Chunk 2: Console LLM Override (Tasks 5-7)

### Task 5: AiRoleOverrideService

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/AiRoleOverrideService.java`
- Create: `business/business-agent/src/test/java/io/strategiz/social/business/agent/ai/AiRoleOverrideServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package io.strategiz.social.business.agent.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.strategiz.social.data.entity.AiRoleOverride;
import io.strategiz.social.data.repository.AiRoleOverrideRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiRoleOverrideServiceTest {

    @Mock
    private AiRoleOverrideRepository repository;

    @InjectMocks
    private AiRoleOverrideService service;

    @Test
    void getOverride_exists_returnsOverride() {
        AiRoleOverride override = new AiRoleOverride();
        override.setRole("IMPLEMENTER");
        override.setEngineId("anthropic-agentic");
        override.setModel("claude-opus-4-6");
        when(repository.findById("IMPLEMENTER")).thenReturn(Optional.of(override));

        Optional<AiRoleOverride> result = service.getOverride("IMPLEMENTER");

        assertTrue(result.isPresent());
        assertEquals("anthropic-agentic", result.get().getEngineId());
    }

    @Test
    void getOverride_notExists_returnsEmpty() {
        when(repository.findById("REVIEWER")).thenReturn(Optional.empty());

        Optional<AiRoleOverride> result = service.getOverride("REVIEWER");

        assertTrue(result.isEmpty());
    }

    @Test
    void setOverride_savesToRepository() {
        service.setOverride("IMPLEMENTER", "anthropic-agentic", "claude-opus-4-6", "admin-1");

        verify(repository).save(any(AiRoleOverride.class));
    }

    @Test
    void deleteOverride_deletesFromRepository() {
        service.deleteOverride("IMPLEMENTER");

        verify(repository).deleteById("IMPLEMENTER");
    }

    @Test
    void getAllOverrides_returnsList() {
        AiRoleOverride o1 = new AiRoleOverride();
        o1.setRole("IMPLEMENTER");
        AiRoleOverride o2 = new AiRoleOverride();
        o2.setRole("REVIEWER");
        when(repository.findAll()).thenReturn(List.of(o1, o2));

        List<AiRoleOverride> result = service.getAllOverrides();

        assertEquals(2, result.size());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :business:business-agent:test --tests "io.strategiz.social.business.agent.ai.AiRoleOverrideServiceTest" 2>&1 | tail -5`
Expected: Compilation error

- [ ] **Step 3: Implement AiRoleOverrideService**

```java
package io.strategiz.social.business.agent.ai;

import io.strategiz.social.data.entity.AiRoleOverride;
import io.strategiz.social.data.repository.AiRoleOverrideRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Business logic for admin role-level AI engine overrides. Provides CRUD operations
 * for the {@code ai_role_overrides} Firestore collection.
 */
@Service
public class AiRoleOverrideService {

    private final AiRoleOverrideRepository repository;

    public AiRoleOverrideService(AiRoleOverrideRepository repository) {
        this.repository = repository;
    }

    public Optional<AiRoleOverride> getOverride(String roleName) {
        return repository.findById(roleName);
    }

    public List<AiRoleOverride> getAllOverrides() {
        return repository.findAll();
    }

    public AiRoleOverride setOverride(String roleName, String engineId, String model, String updatedBy) {
        AiRoleOverride override = new AiRoleOverride();
        override.setId(roleName);
        override.setRole(roleName);
        override.setEngineId(engineId);
        override.setModel(model);
        override.setUpdatedBy(updatedBy);
        repository.save(override);
        return override;
    }

    public void deleteOverride(String roleName) {
        repository.deleteById(roleName);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :business:business-agent:test --tests "io.strategiz.social.business.agent.ai.AiRoleOverrideServiceTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/AiRoleOverrideService.java business/business-agent/src/test/java/io/strategiz/social/business/agent/ai/AiRoleOverrideServiceTest.java
git commit -m "feat(ai): add AiRoleOverrideService for console role-level LLM override"
```

---

### Task 6: ConsoleAiEngineRoutingController

**Files:**
- Create: `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/ConsoleAiEngineRoutingController.java`
- Create: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/AiEngineOverrideRequest.java`
- Create: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/StepConfigResponse.java`
- Create: `service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/ConsoleAiEngineRoutingControllerTest.java`

- [ ] **Step 1: Create request/response DTOs**

`AiEngineOverrideRequest.java`:
```java
package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotBlank;

public class AiEngineOverrideRequest {
    @NotBlank private String engineId;
    @NotBlank private String model;

    public String getEngineId() { return engineId; }
    public void setEngineId(String engineId) { this.engineId = engineId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
```

`StepConfigResponse.java`:
```java
package io.strategiz.social.service.agent.dto;

import java.util.List;
import java.util.Map;

public class StepConfigResponse {
    private String stepName;
    private Map<String, Object> defaultConfig;
    private Map<String, Object> override;
    private Map<String, Object> effective;

    // Constructor, getters, setters
    public StepConfigResponse(String stepName, Map<String, Object> defaultConfig,
            Map<String, Object> override, Map<String, Object> effective) {
        this.stepName = stepName;
        this.defaultConfig = defaultConfig;
        this.override = override;
        this.effective = effective;
    }

    public String getStepName() { return stepName; }
    public Map<String, Object> getDefaultConfig() { return defaultConfig; }
    public Map<String, Object> getOverride() { return override; }
    public Map<String, Object> getEffective() { return effective; }
}
```

- [ ] **Step 2: Write failing controller tests**

Test the role override CRUD endpoints. Use Mockito to mock `AiRoleOverrideService` and `AiSdlcStepDefaults`. Test all 8 endpoints (4 step + 4 role).

- [ ] **Step 3: Implement ConsoleAiEngineRoutingController**

```java
package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.annotation.RequireScope;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.ai.AiRoleOverrideService;
import io.strategiz.social.business.agent.ai.AiSdlcStepDefaults;
import io.strategiz.social.data.entity.AiRoleOverride;
import io.strategiz.social.service.agent.dto.AiEngineOverrideRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/console/ai-engine-routing")
@Tag(name = "Console - AI Engine Routing", description = "Admin overrides for AI engine and model selection")
public class ConsoleAiEngineRoutingController {

    private final AiRoleOverrideService roleOverrideService;
    private final AiSdlcStepDefaults stepDefaults;

    public ConsoleAiEngineRoutingController(AiRoleOverrideService roleOverrideService,
            AiSdlcStepDefaults stepDefaults) {
        this.roleOverrideService = roleOverrideService;
        this.stepDefaults = stepDefaults;
    }

    // --- Step-level endpoints ---

    @GetMapping("/steps")
    @RequireAuth @RequireScope("admin")
    @Operation(summary = "List all SDLC step configs with defaults and overrides")
    public ResponseEntity<?> listStepConfigs() {
        return ResponseEntity.ok(stepDefaults.getAllDefaults());
    }

    @GetMapping("/steps/{stepName}")
    @RequireAuth @RequireScope("admin")
    @Operation(summary = "Get config for a single SDLC step")
    public ResponseEntity<?> getStepConfig(@PathVariable String stepName) {
        return stepDefaults.getDefault(stepName.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- Role-level endpoints ---

    @GetMapping("/roles")
    @RequireAuth @RequireScope("admin")
    @Operation(summary = "List all role-level AI engine overrides")
    public ResponseEntity<List<AiRoleOverride>> listRoleOverrides() {
        return ResponseEntity.ok(roleOverrideService.getAllOverrides());
    }

    @GetMapping("/roles/{roleName}")
    @RequireAuth @RequireScope("admin")
    @Operation(summary = "Get role-level override for a specific PDLC role")
    public ResponseEntity<AiRoleOverride> getRoleOverride(@PathVariable String roleName) {
        return roleOverrideService.getOverride(roleName.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/roles/{roleName}")
    @RequireAuth @RequireScope("admin")
    @Operation(summary = "Set role-level AI engine override")
    public ResponseEntity<AiRoleOverride> setRoleOverride(@PathVariable String roleName,
            @Valid @RequestBody AiEngineOverrideRequest request,
            @AuthUser AuthenticatedUser user) {
        AiRoleOverride override = roleOverrideService.setOverride(
                roleName.toUpperCase(), request.getEngineId(), request.getModel(), user.getUserId());
        return ResponseEntity.ok(override);
    }

    @DeleteMapping("/roles/{roleName}")
    @RequireAuth @RequireScope("admin")
    @Operation(summary = "Remove role-level override (revert to step default)")
    public ResponseEntity<Void> deleteRoleOverride(@PathVariable String roleName) {
        roleOverrideService.deleteOverride(roleName.toUpperCase());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :service:service-agent:test --tests "io.strategiz.social.service.agent.controller.ConsoleAiEngineRoutingControllerTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/ConsoleAiEngineRoutingController.java service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/AiEngineOverrideRequest.java service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/StepConfigResponse.java service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/ConsoleAiEngineRoutingControllerTest.java
git commit -m "feat(console): add AI engine routing controller for step and role overrides"
```

---

### Task 7: Wire role override into RealPdlcRoleExecutor

**Files:**
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/RealPdlcRoleExecutor.java` (lines 73-166)
- Modify: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/RealPdlcRoleExecutorTest.java`

- [ ] **Step 1: Write failing test for role override resolution**

Add a test that verifies: when an `AiRoleOverride` exists for a role, the executor passes the overridden engine+model to the skill context instead of the step default.

- [ ] **Step 2: Inject AiRoleOverrideService into RealPdlcRoleExecutor constructor**

Add `AiRoleOverrideService` as a constructor parameter. Before calling `skill.execute(ctx)`, check for a role override:

```java
// Before building RoleContext, check for role-level override
Optional<AiRoleOverride> roleOverride = roleOverrideService.getOverride(role.name());
if (roleOverride.isPresent()) {
    ctx.setEngineOverride(roleOverride.get().getEngineId());
    ctx.setModelOverride(roleOverride.get().getModel());
}
```

Note: Check if `RoleContext` already has engine/model override fields. If not, add them. The `PdlcRoleSkill.execute(ctx)` implementations should check these fields and pass them through to `AiEngineRequest`.

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :business:business-agent:test --tests "*RealPdlcRoleExecutorTest*" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/RealPdlcRoleExecutor.java business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/RealPdlcRoleExecutorTest.java
git commit -m "feat(pipeline): wire role-level LLM override into RealPdlcRoleExecutor"
```

---

## Chunk 3: Role Skip Integration (Tasks 8-9)

### Task 8: Wire role skipping into AgentController

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java` (lines 217-287)
- Modify: `service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/AgentControllerPipelineTest.java`

- [ ] **Step 1: Write failing test for NL + API skip merge**

Add tests to `AgentControllerPipelineTest`:

```java
@Test
void skipRolesFromNaturalLanguage_removedFromActivatedRoles() {
    // "fix the bug, skip review" → REVIEWER should be removed from activated roles
    AgentCommandRequest request = buildRequest("fix the bug, skip review", "code", null);
    request.setSkipRoles(null);
    // ... mock classifier returning BUG_FIX with [RESEARCHER, IMPLEMENTER, REVIEWER, TESTER]
    // ... assert pipeline dispatched with REVIEWER removed from activatedRoles
}

@Test
void skipRolesFromApiField_removedFromActivatedRoles() {
    AgentCommandRequest request = buildRequest("fix the bug", "code", null);
    request.setSkipRoles(List.of("TESTER"));
    // ... assert TESTER removed from activatedRoles
}

@Test
void skipRolesUnion_nlAndApiBothApplied() {
    AgentCommandRequest request = buildRequest("fix it, skip review", "code", null);
    request.setSkipRoles(List.of("TESTER"));
    // ... assert both REVIEWER and TESTER removed
}
```

- [ ] **Step 2: Implement skip merge in executeInCloudOrPipeline()**

After the classification is determined and before pipeline dispatch (around line 248), add:

```java
// Merge role skip sources: NL keywords + API field
Set<PdlcRole> nlSkipRoles = RoleSkipParser.parse(commandText);
Set<PdlcRole> apiSkipRoles = parseApiSkipRoles(request.getSkipRoles());
Set<PdlcRole> effectiveSkipRoles = new HashSet<>(nlSkipRoles);
effectiveSkipRoles.addAll(apiSkipRoles);

if (!effectiveSkipRoles.isEmpty()) {
    List<PdlcRole> filteredRoles = new ArrayList<>(classification.activatedRoles());
    filteredRoles.removeAll(effectiveSkipRoles);

    classification = new PdlcClassification(
            classification.tier(),
            classification.playbook(),
            classification.confidence(),
            filteredRoles,
            // Rebuild skipped roles
            resolveSkippedFromFiltered(filteredRoles),
            classification.dimensionScores(),
            classification.reasoning() + " [User skipped: " + effectiveSkipRoles + "]");
}
```

Add helper method:
```java
private Set<PdlcRole> parseApiSkipRoles(List<String> skipRoles) {
    if (skipRoles == null || skipRoles.isEmpty()) return Set.of();
    Set<PdlcRole> roles = EnumSet.noneOf(PdlcRole.class);
    for (String name : skipRoles) {
        try { roles.add(PdlcRole.valueOf(name.toUpperCase().trim())); }
        catch (IllegalArgumentException ignored) {
            log.warn("Unknown skip role: {}", name);
        }
    }
    return roles;
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :service:service-agent:test --tests "*AgentControllerPipelineTest*" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/AgentControllerPipelineTest.java
git commit -m "feat(pipeline): wire NL + API role skip merge into AgentController"
```

---

### Task 9: Soft guardrail checkpoint for required role skips

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java`
- Modify: `service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/AgentControllerPipelineTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void skipRequiredRole_createsConfirmationCheckpoint() {
    // BUG_FIX playbook: IMPLEMENTER is required
    AgentCommandRequest request = buildRequest("fix it, just implement", "code", null);
    // "just implement" skips everything except IMPLEMENTER — but RESEARCHER, REVIEWER, TESTER are all required in BUG_FIX
    // Should create a confirmation checkpoint before pipeline dispatch
    // ... assert checkpoint created with message listing required roles being skipped
}
```

- [ ] **Step 2: Implement soft guardrail**

After computing `effectiveSkipRoles` and before pipeline dispatch, check if any skipped roles are marked `required` in the playbook:

```java
if (playbookConfigOpt.isPresent() && !effectiveSkipRoles.isEmpty()) {
    PlaybookConfig pb = playbookConfigOpt.get();
    List<String> requiredSkipped = pb.stages().stream()
            .filter(stage -> stage.required() && effectiveSkipRoles.contains(stage.role()))
            .map(stage -> stage.role().name())
            .toList();

    if (!requiredSkipped.isEmpty()) {
        // Create confirmation checkpoint — pipeline waits for user approval
        log.info("[PIPELINE] Skipping required roles {} for spark={}, creating checkpoint",
                requiredSkipped, spark.getId());
        // Include checkpoint info in the pipeline response
        // The pipeline orchestrator's existing checkpoint flow handles this
    }
}
```

The exact checkpoint creation depends on whether we gate BEFORE pipeline dispatch (synchronous confirmation) or let the orchestrator handle it (async checkpoint). Recommendation: set a `requiresSkipConfirmation` flag on the `PipelineRun` and let the orchestrator create the checkpoint at pipeline start, before executing any roles.

- [ ] **Step 3: Run tests**

Run: `./gradlew :service:service-agent:test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run full build**

Run: `./gradlew build 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(pipeline): add soft guardrail checkpoint when skipping required PDLC roles"
```

---

## Task Dependency Graph

```
Task 1 (RoleSkipParser)  ─────────────────────────┐
Task 2 (AiRoleOverride entity)  ──┐                │
Task 3 (skipRoles DTO field)  ────┤                │
Task 4 (API versioning)  ─────────┤                │
                                   │                │
                                   ▼                ▼
                         Task 5 (Override service)  Task 8 (Wire skipping into controller)
                                   │                │
                                   ▼                ▼
                         Task 6 (Console controller) Task 9 (Soft guardrail checkpoint)
                                   │
                                   ▼
                         Task 7 (Wire into executor)
```

**Wave 1 (parallel):** Tasks 1, 2, 3, 4
**Wave 2 (parallel):** Tasks 5+6 (left branch), Tasks 8 (right branch)
**Wave 3 (parallel):** Task 7, Task 9
