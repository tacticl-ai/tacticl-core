# PDLC Pipeline Engine — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a multi-agent PDLC pipeline that routes complex development sparks through up to 12 specialized roles (PM → Researcher → Architect → ... → Retro), with async execution, full traceability, rework tracking, configurable checkpoints, and playbook-driven workflows.

**Architecture:** Data-driven playbook configs define role sequences. A pipeline orchestrator creates child sparks per role, executes them via the existing AiEngineRouterService, and streams events to clients. All state is Firestore-backed for crash recovery. Async-first with sync timeout fallback for simple sparks.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Firestore, JUnit 5 + Mockito (unit tests, no Spring context), existing AiEngineRouterService framework, WebSocket + FCM for real-time events.

**Spec:** `docs/superpowers/specs/2026-03-24-pdlc-pipeline-engine-design.md`

**Testing Pattern:** All tests use `@ExtendWith(MockitoExtension.class)` with full mocking — no `@SpringBootTest`. Constructor injection. Verify with `verify()`, `argThat()`, `ArgumentCaptor`. See existing tests in `SparkClassifierServiceTest`, `DeviceRepositoryTest` for patterns.

---

## Parallelization Strategy — 5 Waves

```
Wave 1 (ALL PARALLEL — no dependencies):
  Task 1: New enums (data-social)
  Task 2: PipelineRun + PipelineEvent entities + repos (data-social)
  Task 3: PipelineArtifact + PdlcRoleKnowledge entities + repos (data-social)
  Task 4: Modified entities — Checkpoint, UserConfig, Spark, AgentAuditLog, AiSdlcStep (data-social)
  Task 5: GitHubClient API methods (client-github)
  Task 6: GitHub agent skills (business-agent)

Wave 2 (depends on Wave 1):
  Task 7: PlaybookConfig + PlaybookRegistry (business-agent)
  Task 8: PdlcClassifierService (business-agent)
  Task 9: PipelineEventEmitter (business-agent)
  Task 10: PipelineArtifactService (business-agent)

Wave 3 (depends on Wave 2):
  Task 11: PdlcPipelineOrchestrator core (business-agent)
  Task 12: ReworkTracker + PipelineCostManager (business-agent)
  Task 13: Async execution — timeout wrapper + thread pool (business-agent)
  Task 14: PipelineRecoveryJob + PipelineWatchdog (business-agent)

Wave 4 (depends on Wave 3):
  Task 15: PdlcRoleSkill interface + RoleToolFilter + PdlcRoleRegistry (business-agent)
  Task 16: Role skills batch A — PM, Researcher, Architect, Designer (business-agent)
  Task 17: Role skills batch B — Planner, Implementer, Reviewer (business-agent)
  Task 18: Role skills batch C — Tester, Security, TechWriter, DevOps, Retro (business-agent)
  Task 19: PipelineController REST endpoints (service-agent)
  Task 20: AgentController routing + AgentCommandResponse DTO changes (service-agent)

Wave 5 (depends on Wave 4):
  Task 21: Knowledge base — embedding infrastructure + RAG retrieval (business-agent)
  Task 22: Integration wiring + build verification (all modules)
  Task 23: Architecture documentation update
```

---

## Chunk 1: Wave 1 — Data Foundation + GitHub (All Parallel)

### Task 1: New Enums

**Files:**
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PdlcRole.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineTier.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineStatus.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/RoleStatus.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineEventType.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/CheckpointType.java`

- [ ] **Step 1: Create PdlcRole enum**

```java
package io.strategiz.social.data.entity;

public enum PdlcRole {
    PM, RESEARCHER, ARCHITECT, DESIGNER, PLANNER,
    IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST,
    TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST
}
```

- [ ] **Step 2: Create PipelineTier enum**

```java
package io.strategiz.social.data.entity;

public enum PipelineTier {
    SIMPLE,     // No pipeline — single agent loop
    PLAYBOOK,   // Named workflow with specific roles
    FULL_PDLC   // Complete 12-role pipeline
}
```

- [ ] **Step 3: Create PipelineStatus enum**

```java
package io.strategiz.social.data.entity;

public enum PipelineStatus {
    CREATED, CLASSIFYING, AWAITING_CONFIRMATION,
    EXECUTING, CHECKPOINT, COMPLETED, FAILED, CANCELLED
}
```

- [ ] **Step 4: Create RoleStatus enum**

```java
package io.strategiz.social.data.entity;

public enum RoleStatus {
    PENDING, EXECUTING, COMPLETED, REJECTED,
    REWORKING, FAILED, ESCALATED, SKIPPED, AWAITING_APPROVAL
}
```

- [ ] **Step 5: Create PipelineEventType enum**

```java
package io.strategiz.social.data.entity;

public enum PipelineEventType {
    PIPELINE_STARTED, PIPELINE_COMPLETED, PIPELINE_FAILED,
    PIPELINE_CANCELLED, PIPELINE_RESUMED,
    ROLE_STARTED, ROLE_COMPLETED, ROLE_REJECTED, ROLE_SKIPPED,
    REWORK_TRIGGERED, REWORK_COMPLETED, REWORK_ESCALATED,
    ARTIFACT_PRODUCED,
    CHECKPOINT_REQUESTED, CHECKPOINT_RESOLVED, CHECKPOINT_TIMEOUT_REMINDER,
    PARALLEL_ROLES_STARTED,
    COST_THRESHOLD_WARNING, COST_CEILING_REACHED
}
```

- [ ] **Step 6: Create CheckpointType enum**

```java
package io.strategiz.social.data.entity;

public enum CheckpointType {
    TACTIC,
    PIPELINE_STAGE,
    COST_CEILING,
    REWORK_ESCALATION,
    CONFIDENCE_GATE
}
```

- [ ] **Step 7: Verify build**

Run: `./gradlew :data:data-social:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/PdlcRole.java \
       data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineTier.java \
       data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineStatus.java \
       data/data-social/src/main/java/io/strategiz/social/data/entity/RoleStatus.java \
       data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineEventType.java \
       data/data-social/src/main/java/io/strategiz/social/data/entity/CheckpointType.java
git commit -m "feat(data): add PDLC pipeline enums — PdlcRole, PipelineTier, PipelineStatus, RoleStatus, PipelineEventType, CheckpointType"
```

---

### Task 2: PipelineRun + PipelineEvent Entities & Repositories

**Files:**
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineRun.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineEvent.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/RoleResultSummary.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/repository/PipelineRunRepository.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/repository/PipelineEventRepository.java`
- Test: `data/data-social/src/test/java/io/strategiz/social/data/repository/PipelineRunRepositoryTest.java`
- Test: `data/data-social/src/test/java/io/strategiz/social/data/repository/PipelineEventRepositoryTest.java`
- Reference: `data/data-social/src/main/java/io/strategiz/social/data/entity/Spark.java` (entity pattern)
- Reference: `data/data-social/src/main/java/io/strategiz/social/data/repository/SparkRepository.java` (repo pattern)
- Reference: `data/data-social/src/test/java/io/strategiz/social/data/repository/DeviceRepositoryTest.java` (test pattern)

**Context:** Follow the existing `Spark` entity pattern — extends `BaseEntity`, Firestore annotations, constructor injection in repos. `PipelineRun` is mutable (status updates). `PipelineEvent` is append-only.

- [ ] **Step 1: Read Spark entity and SparkRepository to understand patterns**

Read: `data/data-social/src/main/java/io/strategiz/social/data/entity/Spark.java`
Read: `data/data-social/src/main/java/io/strategiz/social/data/repository/SparkRepository.java`

- [ ] **Step 2: Create RoleResultSummary record**

Embedded in PipelineRun's roleResults map. Fields: `childSparkId`, `status` (RoleStatus), `artifactId`, `iteration`, `tokens`, `cost` (BigDecimal), `durationMs`, `model`, `engine`.

- [ ] **Step 3: Create PipelineRun entity**

Collection: `pipeline_runs/`. Fields per spec section 12: `id`, `sparkId`, `userId`, `playbook`, `pipelineTier` (PipelineTier), `status` (PipelineStatus), `activatedRoles` (List<PdlcRole>), `currentRole` (PdlcRole), `roleResults` (Map<String, RoleResultSummary>), `reworkCount`, `totalTokens`, `totalCost` (BigDecimal), `classificationResult` (Map), `gitContext` (Map), `claimedBy`, `claimedAt`, `startedAt`, `completedAt`, `createdDate`, `updatedDate`.

- [ ] **Step 4: Create PipelineRunRepository**

Flat collection repository. Methods: `save(run)`, `findById(id)`, `findBySparkId(sparkId)`, `findByUserId(userId)`, `findByStatus(status)`, `findByUserIdAndStatus(userId, status)`, `claimForRecovery(id, instanceId)` (transactional CAS on claimedBy/claimedAt).

- [ ] **Step 5: Write PipelineRunRepository test**

Test: query construction for `findByUserId`, `findByStatus`, `findBySparkId`. Mock Firestore chain per DeviceRepositoryTest pattern.

- [ ] **Step 6: Create PipelineEvent entity**

Collection: `pipeline_events/`. Append-only. Fields: `id`, `pipelineRunId`, `sparkId`, `childSparkId`, `userId`, `eventType` (PipelineEventType), `role` (PdlcRole), `roleIteration`, `metadata` (Map<String, Object>), `timestamp`.

- [ ] **Step 7: Create PipelineEventRepository**

Flat collection. Methods: `save(event)`, `findByPipelineRunId(runId)`, `findByPipelineRunIdAndEventType(runId, type)`, `findByUserId(userId)`, `findByUserIdAndEventType(userId, type)`.

- [ ] **Step 8: Write PipelineEventRepository test**

Test: query construction. Verify userId filtering is always applied.

- [ ] **Step 9: Verify build and run tests**

Run: `./gradlew :data:data-social:test`
Expected: All tests pass.

- [ ] **Step 10: Commit**

```bash
git commit -m "feat(data): add PipelineRun and PipelineEvent entities with repositories"
```

---

### Task 3: PipelineArtifact + PdlcRoleKnowledge Entities & Repositories

**Files:**
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineArtifact.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PdlcRoleKnowledge.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/repository/PipelineArtifactRepository.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/repository/PdlcRoleKnowledgeRepository.java`
- Test: `data/data-social/src/test/java/io/strategiz/social/data/repository/PipelineArtifactRepositoryTest.java`
- Test: `data/data-social/src/test/java/io/strategiz/social/data/repository/PdlcRoleKnowledgeRepositoryTest.java`

- [ ] **Step 1: Create PipelineArtifact entity**

Collection: `pipeline_artifacts/`. Fields: `id`, `pipelineRunId`, `role` (PdlcRole), `sparkId`, `artifactType` (String — REQUIREMENTS, DESIGN, CODE, REVIEW, TEST_RESULTS, SECURITY_REPORT, DOCS, DEPLOY, PLAN, RESEARCH), `content` (Map<String, Object>), `version`, `createdAt`. For code artifacts, content holds GitHub refs: `{ repo, branch, commitSha, filesChanged[], prNumber, prUrl }`.

- [ ] **Step 2: Create PipelineArtifactRepository**

Methods: `save(artifact)`, `findByPipelineRunId(runId)`, `findByPipelineRunIdAndRole(runId, role)`, `findById(id)`.

- [ ] **Step 3: Write PipelineArtifactRepository test**

- [ ] **Step 4: Create PdlcRoleKnowledge entity**

Collection: `pdlc_role_knowledge/`. Fields: `id`, `role` (PdlcRole), `category` (String — BEST_PRACTICE, ANTI_PATTERN, EXAMPLE, RETRO_LEARNING), `content`, `embedding` (List<Double>), `source`, `relevanceScore`, `createdAt`.

- [ ] **Step 5: Create PdlcRoleKnowledgeRepository**

Methods: `save(knowledge)`, `findByRole(role)`, `findByRoleAndCategory(role, category)`, `findById(id)`, `updateRelevanceScore(id, score)`.

- [ ] **Step 6: Write PdlcRoleKnowledgeRepository test**

- [ ] **Step 7: Verify build and run tests**

Run: `./gradlew :data:data-social:test`

- [ ] **Step 8: Commit**

```bash
git commit -m "feat(data): add PipelineArtifact and PdlcRoleKnowledge entities with repositories"
```

---

### Task 4: Modified Entities — Checkpoint, UserConfig, Spark, AgentAuditLog, AiSdlcStep

**Files:**
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/Checkpoint.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/UserConfig.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/Spark.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/AgentAuditLog.java`
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/AiSdlcStep.java`
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/PipelineCheckpointConfig.java`

- [ ] **Step 1: Read all 5 existing entities to understand current fields**

- [ ] **Step 2: Add fields to Checkpoint**

Add: `pipelineRunId` (String), `pdlcRole` (PdlcRole), `checkpointType` (CheckpointType). All nullable with getters/setters.

- [ ] **Step 3: Create PipelineCheckpointConfig record**

Fields: `roleCheckpoints` (Map<String, CheckpointRule>), `approveBeforeDeploy` (boolean, default true), `approveRequirements` (boolean, default true), `approveArchitecture` (boolean, default true), `approveOnSecurityFindings` (boolean, default true), `securityFindingSeverityThreshold` (int, default 2), `autoApproveAll` (boolean, default false). Plus embedded `CheckpointRule` record: `beforeRole`, `afterRole`, `onRejection` (all boolean).

- [ ] **Step 4: Add fields to UserConfig**

Add: `syncTimeoutMs` (int, default 3000), `forceAsyncAll` (boolean, default false), `pipelineCheckpoints` (PipelineCheckpointConfig), `pipelineCostCeiling` (BigDecimal, default 50), `costWarningThreshold` (double, default 0.8).

- [ ] **Step 5: Add fields to Spark**

Add: `pdlcRole` (PdlcRole, nullable), `executionMode` (String — "SYNC", "ASYNC", "PIPELINE").

- [ ] **Step 6: Add fields to AgentAuditLog**

Add: `pipelineRunId` (String, nullable), `pdlcRole` (PdlcRole, nullable).

- [ ] **Step 7: Add new enum values to AiSdlcStep**

Add: `REQUIREMENTS_GATHERING("Gather and define requirements and acceptance criteria")`, `SYSTEM_DESIGN("Design system architecture and component breakdown")`, `UI_UX_DESIGN("Design user interface and user experience")`, `SECURITY_REVIEW("Review code for security vulnerabilities")`, `RETROSPECTIVE("Analyze pipeline execution and generate learnings")`.

- [ ] **Step 8: Verify build**

Run: `./gradlew :data:data-social:compileJava`

- [ ] **Step 9: Commit**

```bash
git commit -m "feat(data): add PDLC fields to Checkpoint, UserConfig, Spark, AgentAuditLog, AiSdlcStep"
```

---

### Task 5: GitHubClient API Methods

**Files:**
- Create: `client/client-github/src/main/java/io/strategiz/social/client/github/GitHubClient.java`
- Modify: `client/client-github/src/main/java/io/strategiz/social/client/github/config/ClientGitHubConfig.java` (add bean)
- Create: `client/client-github/src/main/java/io/strategiz/social/client/github/model/GitHubBranch.java`
- Create: `client/client-github/src/main/java/io/strategiz/social/client/github/model/GitHubPullRequest.java`
- Create: `client/client-github/src/main/java/io/strategiz/social/client/github/model/GitHubFileContent.java`
- Create: `client/client-github/src/main/java/io/strategiz/social/client/github/model/GitHubCommitResult.java`
- Test: `client/client-github/src/test/java/io/strategiz/social/client/github/GitHubClientTest.java`
- Reference: `client/client-twitter/src/main/java/io/strategiz/social/client/twitter/TwitterClient.java` (client pattern)

**Context:** Follow the existing Twitter/LinkedIn client pattern — extends `BaseHttpClient`, config bean, Vault secrets. GitHub REST API v3.

- [ ] **Step 1: Read TwitterClient and ClientGitHubConfig to understand patterns**

- [ ] **Step 2: Create response model classes**

`GitHubBranch`, `GitHubPullRequest`, `GitHubFileContent`, `GitHubCommitResult` — simple POJOs matching GitHub API responses.

- [ ] **Step 3: Create GitHubClient extending BaseHttpClient**

Methods (per spec section 16):
- `createBranch(repo, branchName, baseSha)` → POST `/repos/{owner}/{repo}/git/refs`
- `commitFile(repo, path, content, message, branch, sha)` → PUT `/repos/{owner}/{repo}/contents/{path}`
- `createPullRequest(repo, title, body, head, base)` → POST `/repos/{owner}/{repo}/pulls`
- `reviewPullRequest(repo, prNumber, event, body)` → POST `/repos/{owner}/{repo}/pulls/{pr}/reviews`
- `mergePullRequest(repo, prNumber, mergeMethod)` → PUT `/repos/{owner}/{repo}/pulls/{pr}/merge`
- `listFiles(repo, path, branch)` → GET `/repos/{owner}/{repo}/contents/{path}`
- `readFile(repo, path, branch)` → GET `/repos/{owner}/{repo}/contents/{path}` + base64 decode
- `listBranches(repo)` → GET `/repos/{owner}/{repo}/branches`
- `searchCode(repo, query)` → GET `/search/code`
- `getPullRequest(repo, prNumber)` → GET `/repos/{owner}/{repo}/pulls/{pr}`
- `getLatestCommitSha(repo, branch)` → GET `/repos/{owner}/{repo}/git/ref/heads/{branch}`

- [ ] **Step 4: Register GitHubClient bean in ClientGitHubConfig**

Add `@Bean` method creating `GitHubClient` with token from `GitHubConfig`.

- [ ] **Step 5: Write GitHubClient tests**

Test: URL construction, header setting, request body formatting. Mock `RestTemplate` per existing client test patterns.

- [ ] **Step 6: Verify build and run tests**

Run: `./gradlew :client:client-github:test`

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(client-github): implement GitHubClient with full REST API — branches, commits, PRs, file ops, search"
```

---

### Task 6: GitHub Agent Skills

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/GitHubReadFileSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/GitHubListFilesSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/GitHubSearchCodeSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/GitHubCreateBranchSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/GitHubCommitSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/GitHubCreatePrSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/GitHubReviewPrSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/GitHubMergePrSkill.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/skill/GitHubReadFileSkillTest.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/skill/GitHubCommitSkillTest.java`
- Reference: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/SearchWebSkill.java` (skill pattern)
- Reference: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/AgentSkill.java` (interface)

**Context:** Each skill implements `AgentSkill` interface. Tier 0 for read ops, Tier 1 for write ops. Use `Optional<GitHubClient>` injection for graceful degradation.

- [ ] **Step 1: Read SearchWebSkill and AgentSkill interface to understand patterns**

- [ ] **Step 2: Create Tier 0 skills — GitHubReadFileSkill, GitHubListFilesSkill, GitHubSearchCodeSkill**

Each implements `AgentSkill`. Tool definition includes input schema (repo, path/query, branch). `getConfirmationTier()` returns 0.

- [ ] **Step 3: Create Tier 1 skills — GitHubCreateBranchSkill, GitHubCommitSkill, GitHubCreatePrSkill, GitHubReviewPrSkill, GitHubMergePrSkill**

Each implements `AgentSkill`. `getConfirmationTier()` returns 1. Tool definitions include appropriate input schemas.

- [ ] **Step 4: Write tests for GitHubReadFileSkill and GitHubCommitSkill**

Test: tool definition schema, execute with mocked GitHubClient, graceful handling when client is empty.

- [ ] **Step 5: Verify build and run tests**

Run: `./gradlew :business:business-agent:test`

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(agent): add 8 GitHub agent skills — read, list, search, branch, commit, PR, review, merge"
```

---

## Chunk 2: Wave 2 — Playbooks, Classifier, Event Infrastructure (Parallel)

### Task 7: PlaybookConfig + PlaybookRegistry

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PlaybookConfig.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PlaybookStage.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PlaybookRegistry.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PlaybookRegistryTest.java`

**Context:** Playbooks are data-driven configs defining role sequences. 8 default playbooks. PlaybookStage includes role, required flag, dependsOn, canRejectTo, timeout. See spec section 3.

- [ ] **Step 1: Create PlaybookStage record**

Fields: `role` (PdlcRole), `required` (boolean), `dependsOn` (List<PdlcRole>), `canRejectTo` (List<PdlcRole>), `timeout` (Duration).

- [ ] **Step 2: Create PlaybookConfig record**

Fields: `name`, `displayName`, `description`, `tier` (PipelineTier), `stages` (List<PlaybookStage>), `parallelGroups` (Map<PdlcRole, List<PdlcRole>>), `defaultCheckpoints` (Map<PdlcRole, CheckpointRule>), `isSystemPlaybook` (boolean).

- [ ] **Step 3: Create PlaybookRegistry with 8 default playbooks**

`@Service` with `@PostConstruct`. Register: FULL_PDLC, BUG_FIX, SMALL_FEATURE, REFACTOR, INFRA_CHANGE, DOCS_ONLY, UI_CHANGE, SECURITY_PATCH. Each with correct role ordering, parallel groups, and timeouts per spec section 3.

- [ ] **Step 4: Write PlaybookRegistry test**

Test: all 8 playbooks registered, correct role count per playbook, getPlaybook returns correct config, unknown playbook returns empty.

- [ ] **Step 5: Verify build and run tests**

Run: `./gradlew :business:business-agent:test`

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(pipeline): add PlaybookConfig, PlaybookStage, and PlaybookRegistry with 8 default playbooks"
```

---

### Task 8: PdlcClassifierService

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PdlcClassifierService.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PdlcClassification.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PdlcClassifierServiceTest.java`
- Reference: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/SparkClassifierService.java` (similar pattern)

**Context:** Two-stage classifier. Stage 1 is existing SparkClassifierService. Stage 2 evaluates depth for code/devops sparks using multi-dimensional rubric via AiEngineRouterService. See spec section 2.

- [ ] **Step 1: Create PdlcClassification record**

Fields: `tier` (PipelineTier), `playbook` (String), `confidence` (double), `activatedRoles` (List<PdlcRole>), `skippedRoles` (List<PdlcRole>), `dimensionScores` (Map<String, Integer>), `reasoning` (String).

- [ ] **Step 2: Create PdlcClassifierService**

`@Service`. Inject: `AiEngineRouterService`, `PlaybookRegistry`. Method: `classifyDepth(title, description, sparkType)` → returns `PdlcClassification`. Build classification prompt with rubric dimensions (scope, risk, domain breadth, integration, testing, reversibility). Parse structured JSON response. Map score to tier + playbook. Confidence gating: >0.85 auto, 0.5-0.85 propose, <0.5 ask.

- [ ] **Step 3: Write PdlcClassifierService test**

Test: code spark classified as FULL_PDLC (high scores), simple code fix classified as SIMPLE, BUG_FIX playbook selected for bug descriptions, fallback to SIMPLE on classification failure, confidence gating logic.

- [ ] **Step 4: Verify build and run tests**

Run: `./gradlew :business:business-agent:test`

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(pipeline): add PdlcClassifierService with multi-dimensional rubric and playbook selection"
```

---

### Task 9: PipelineEventEmitter

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PipelineEventEmitter.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PipelineEventEmitterTest.java`

**Context:** Single service that fans out events to: 1) Firestore pipeline_events collection, 2) PipelineRun summary update, 3) WebSocket push, 4) FCM push for milestones. See spec section 9.

- [ ] **Step 1: Create PipelineEventEmitter**

`@Service`. Inject: `PipelineEventRepository`, `PipelineRunRepository`. Method: `emitEvent(PipelineRun run, PipelineEventType type, PdlcRole role, Map<String, Object> metadata)`. Creates PipelineEvent, saves to Firestore, updates PipelineRun summary. WebSocket/FCM integration can be stubbed initially (TODO markers for integration in Wave 5).

- [ ] **Step 2: Write PipelineEventEmitter test**

Test: event saved to repository, PipelineRun updated, correct event type and metadata passed through.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(pipeline): add PipelineEventEmitter for event fan-out to Firestore"
```

---

### Task 10: PipelineArtifactService

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PipelineArtifactService.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PipelineArtifactServiceTest.java`

- [ ] **Step 1: Create PipelineArtifactService**

`@Service`. Inject: `PipelineArtifactRepository`. Methods: `store(pipelineRunId, role, artifactType, content)` → returns artifactId, `getArtifact(artifactId)`, `getArtifactsForRole(pipelineRunId, role)`, `getUpstreamArtifacts(pipelineRunId, currentRole, playbook)` — returns map of role → artifact for all completed upstream roles.

- [ ] **Step 2: Write PipelineArtifactService test**

Test: store and retrieve, upstream artifacts filtering based on playbook stage ordering.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(pipeline): add PipelineArtifactService for role artifact storage and retrieval"
```

---

## Chunk 3: Wave 3 — Orchestrator Core (Partially Parallel)

### Task 11: PdlcPipelineOrchestrator Core

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PdlcPipelineOrchestrator.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PipelineStateManager.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PdlcPipelineOrchestratorTest.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PipelineStateManagerTest.java`

**Context:** The brain of the pipeline. Creates PipelineRun, iterates through playbook stages, manages role execution, handles parallel roles, checkpoints, and rework. See spec section 6.

- [ ] **Step 1: Create PipelineStateManager**

`@Service`. Inject: `PipelineRunRepository`, `PipelineEventEmitter`. Methods: `createRun(sparkId, userId, playbook, classification)` → PipelineRun, `updateRoleStatus(runId, role, status)`, `updateCurrentRole(runId, role)`, `markCompleted(runId, totalTokens, totalCost)`, `markFailed(runId, error)`, `incrementRework(runId)`.

- [ ] **Step 2: Write PipelineStateManager test**

Test: create run sets correct initial state, status transitions, rework count increment.

- [ ] **Step 3: Create PdlcPipelineOrchestrator**

`@Service`. Inject: `PipelineStateManager`, `PipelineEventEmitter`, `PipelineArtifactService`, `SparkService`, `PdlcRoleRegistry` (will be Task 15, use interface for now), `PlaybookRegistry`. Main method: `@Async("pdlcPipelineExecutor") executePipeline(String pipelineRunId)`. Implements the orchestration loop per spec section 6: iterate stages, create child sparks, build context, execute roles, handle checkpoints, aggregate metrics.

- [ ] **Step 4: Write PdlcPipelineOrchestrator test**

Test: pipeline creates child sparks per role, emits ROLE_STARTED/COMPLETED events, handles parallel roles, marks pipeline complete after last role, aggregates tokens/cost.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(pipeline): add PdlcPipelineOrchestrator and PipelineStateManager — core pipeline lifecycle"
```

---

### Task 12: ReworkTracker + PipelineCostManager

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/ReworkTracker.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PipelineCostManager.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/ReworkTrackerTest.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PipelineCostManagerTest.java`

- [ ] **Step 1: Create ReworkTracker**

`@Service`. Inject: `PipelineEventEmitter`, `PipelineStateManager`. Methods: `handleRework(pipelineRun, rejectingRole, targetRole, feedback)` — emits REWORK_TRIGGERED event, increments rework count, returns true if under max (3). `isMaxReworkExceeded(pipelineRunId, role)` — checks iteration count. `getReworkCount(pipelineRunId)`.

- [ ] **Step 2: Create PipelineCostManager**

`@Service`. Inject: `PipelineEventEmitter`, `SparkRepository`. Methods: `checkCostCeiling(pipelineRun, userConfig)` → CostCheckResult (OK, WARNING, CEILING_REACHED), `estimateRoleCost(role, historicalEvents)` → BigDecimal, `getCumulativeCost(pipelineRunId)`. Checks both per-pipeline ceiling and monthly spending limit.

- [ ] **Step 3: Write tests for both**

Test ReworkTracker: rework under limit returns true, over limit returns false, events emitted correctly.
Test PipelineCostManager: ceiling check at 80% returns WARNING, at 100% returns CEILING_REACHED, monthly limit check.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(pipeline): add ReworkTracker and PipelineCostManager"
```

---

### Task 13: Async Execution — Timeout Wrapper + Thread Pool

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PdlcPipelineExecutorConfig.java`
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/VoiceAgentService.java` (add async wrapper)
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/service/SparkService.java` (add markAsync, createChildSpark)
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/service/VoiceAgentServiceAsyncTest.java`

**Context:** See spec section 1 — timeout implementation. CompletableFuture with sync timeout, spark transitions to ASYNC mode on timeout.

- [ ] **Step 1: Create PdlcPipelineExecutorConfig**

`@Configuration`. Define `@Bean("pdlcPipelineExecutor")` ThreadPoolTaskExecutor with corePoolSize=4, maxPoolSize=8, queueCapacity=50, threadNamePrefix="pdlc-pipeline-". Also define `@Bean("simpleSparkExecutor")` for sync timeout attempts.

- [ ] **Step 2: Add methods to SparkService**

Add: `markAsync(sparkId)` — atomically sets executionMode to ASYNC. `createChildSpark(parentSparkId, role, userId)` — creates child spark with parentSparkId, pdlcRole set.

- [ ] **Step 3: Add async timeout wrapper to VoiceAgentService**

New method: `executeWithTimeout(sparkId, commandText, userId, ...)` — wraps existing `execute()` in CompletableFuture with configurable timeout. On timeout, calls `sparkService.markAsync()` and returns async response. Completion handler writes results to Firestore + pushes WebSocket.

- [ ] **Step 4: Write async wrapper tests**

Test: sync completion within timeout returns result, timeout triggers async transition, spark executionMode updated correctly.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(pipeline): add async execution with timeout-based hybrid and thread pool config"
```

---

### Task 14: PipelineRecoveryJob + PipelineWatchdog

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PipelineRecoveryJob.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PipelineWatchdog.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PipelineRecoveryJobTest.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PipelineWatchdogTest.java`

- [ ] **Step 1: Create PipelineRecoveryJob**

`@Component`. Inject: `PipelineRunRepository`, `PdlcPipelineOrchestrator`. `@PostConstruct recoverInterruptedPipelines()` — query EXECUTING runs, attempt CAS claim via `claimedBy`/`claimedAt`, resume from last completed role.

- [ ] **Step 2: Create PipelineWatchdog**

`@Component`. `@Scheduled(fixedDelay = 60000)`. Checks for roles exceeding their timeout. On timeout: mark role FAILED, emit event, escalate to user checkpoint.

- [ ] **Step 3: Write tests for both**

Test recovery: claims EXECUTING pipelines, skips already-claimed, resumes from correct role.
Test watchdog: detects timed-out roles, triggers escalation.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(pipeline): add PipelineRecoveryJob and PipelineWatchdog for crash recovery and timeout monitoring"
```

---

## Chunk 4: Wave 4 — Role Skills + API (Parallel)

### Task 15: PdlcRoleSkill Interface + RoleToolFilter + PdlcRoleRegistry

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/PdlcRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/RoleContext.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/RoleResult.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/RoleMetrics.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/GitContext.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/RoleOutcome.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/SuccessCriteria.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/RoleToolFilter.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/PdlcRoleRegistry.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/RoleToolFilterTest.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/PdlcRoleRegistryTest.java`

**Context:** See spec section 4. PdlcRoleSkill is the interface all 12 roles implement. RoleToolFilter restricts which ToolRegistry skills each role can access. PdlcRoleRegistry auto-discovers all role skill beans.

- [ ] **Step 1: Create supporting records — RoleOutcome enum, RoleMetrics, GitContext, SuccessCriteria, RoleContext, RoleResult**

Per spec section 4 definitions. RoleOutcome enum: COMPLETED, REJECTED, FAILED, ESCALATED.

- [ ] **Step 2: Create PdlcRoleSkill interface**

Per spec: `getRole()`, `getSystemPrompt()`, `getKnowledgeBase()`, `getFewShotExamples()`, `getAvailableTools()`, `getAiSdlcStepName()`, `getSuccessCriteria()`, `execute(RoleContext ctx)`.

- [ ] **Step 3: Create RoleToolFilter**

`@Service`. Inject: `ToolRegistry`. Method: `getToolsForRole(PdlcRole role)` → returns filtered `List<ToolDefinition>` based on role's `getAvailableTools()`.

- [ ] **Step 4: Create PdlcRoleRegistry**

`@Service`. Spring auto-discovers all `PdlcRoleSkill` beans via constructor injection. Methods: `getRole(PdlcRole role)`, `getAllRoles()`, `hasRole(PdlcRole role)`.

- [ ] **Step 5: Write tests**

Test RoleToolFilter: returns only allowed tools, empty list for roles with no tools.
Test PdlcRoleRegistry: all registered roles accessible, unknown role returns empty.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(pipeline): add PdlcRoleSkill interface, RoleToolFilter, PdlcRoleRegistry, and supporting records"
```

---

### Task 16: Role Skills Batch A — PM, Researcher, Architect, Designer

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/PmRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/ResearcherRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/ArchitectRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/DesignerRoleSkill.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/role/PmRoleSkillTest.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/role/ArchitectRoleSkillTest.java`

**Context:** Each role implements PdlcRoleSkill. Execution delegates to AiEngineRouterService.executeStep(). System prompts define role identity, instructions, expected output format. See spec section 4 role table for tools/engines/models.

- [ ] **Step 1: Create abstract base class AbstractPdlcRoleSkill**

Shared logic: builds AiEngineRequest from RoleContext, calls AiEngineRouterService.executeStep(), converts AiEngineResult to RoleResult. Each subclass provides role-specific system prompt, tools, step name.

- [ ] **Step 2: Implement PmRoleSkill**

Role: PM. Step: REQUIREMENTS_GATHERING. Engine: anthropic-agentic/sonnet. Tools: search_web, browse_web. System prompt: requirements gathering, PRD writing, acceptance criteria definition. Output artifact type: REQUIREMENTS.

- [ ] **Step 3: Implement ResearcherRoleSkill**

Role: RESEARCHER. Steps: WEB_RESEARCH + CODE_ANALYSIS. Tools: search_web, browse_web, github_read_file, github_list_files, github_search_code. Output: RESEARCH.

- [ ] **Step 4: Implement ArchitectRoleSkill**

Role: ARCHITECT. Step: SYSTEM_DESIGN. Engine: anthropic-agentic/opus. Tools: search_web, browse_web, github_read_file, github_list_files. Output: DESIGN.

- [ ] **Step 5: Implement DesignerRoleSkill**

Role: DESIGNER. Step: UI_UX_DESIGN. Tools: browse_web, image_analysis. Output: DESIGN (UI).

- [ ] **Step 6: Write tests for PM and Architect**

Test: correct step name, system prompt contains key instructions, execute delegates to engine, artifact produced on success.

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(pipeline): add PM, Researcher, Architect, and Designer role skills"
```

---

### Task 17: Role Skills Batch B — Planner, Implementer, Reviewer

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/PlannerRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/ImplementerRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/ReviewerRoleSkill.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/role/ImplementerRoleSkillTest.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/role/ReviewerRoleSkillTest.java`

- [ ] **Step 1: Implement PlannerRoleSkill**

Step: TASK_DECOMPOSITION. Tools: github_list_files. Output: PLAN.

- [ ] **Step 2: Implement ImplementerRoleSkill**

Step: CODE_GENERATION. Engine: claude-code-cli/opus. Tools: full toolset + all github skills. Output: CODE (GitHub refs). This role creates the feature branch and commits code.

- [ ] **Step 3: Implement ReviewerRoleSkill**

Step: CODE_REVIEW. Tools: github_read_file, github_create_pr, github_review_pr. Output: REVIEW. Can return REJECTED outcome with rework target = IMPLEMENTER.

- [ ] **Step 4: Write tests**

Test Implementer: creates branch via GitHubCreateBranchSkill, produces CODE artifact with GitHub refs.
Test Reviewer: returns REJECTED with rejection reason when issues found, COMPLETED when approved.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(pipeline): add Planner, Implementer, and Reviewer role skills"
```

---

### Task 18: Role Skills Batch C — Tester, Security, TechWriter, DevOps, Retro

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/TesterRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/SecurityAnalystRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/TechnicalWriterRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/DevOpsRoleSkill.java`
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/role/RetroAnalystRoleSkill.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/role/TesterRoleSkillTest.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/role/RetroAnalystRoleSkillTest.java`

- [ ] **Step 1: Implement TesterRoleSkill**

Steps: TEST_GENERATION + TEST_EXECUTION. Engine: claude-code-cli/sonnet. Can REJECT back to IMPLEMENTER.

- [ ] **Step 2: Implement SecurityAnalystRoleSkill**

Step: SECURITY_REVIEW. Engine: anthropic-agentic/opus. Can REJECT to IMPLEMENTER for fixes.

- [ ] **Step 3: Implement TechnicalWriterRoleSkill**

Step: DOCUMENTATION. Commits docs to GitHub branch.

- [ ] **Step 4: Implement DevOpsRoleSkill**

Step: DEPLOYMENT_SCRIPT. Engine: claude-code-cli/sonnet. Commits deploy configs.

- [ ] **Step 5: Implement RetroAnalystRoleSkill**

Step: RETROSPECTIVE. Analyzes pipeline events, computes proficiency scores, creates knowledge entries. This is a post-pipeline hook, not a playbook stage.

- [ ] **Step 6: Write tests**

Test Tester: REJECT outcome with bug report, COMPLETED with test results artifact.
Test Retro: creates RETRO_LEARNING knowledge entries, computes proficiency scores.

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(pipeline): add Tester, Security, TechWriter, DevOps, and Retro role skills"
```

---

### Task 19: PipelineController REST Endpoints

**Files:**
- Create: `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/PipelineController.java`
- Create: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/PipelineRunResponse.java`
- Create: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/PipelineEventResponse.java`
- Create: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/PlaybookResponse.java`
- Test: `service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/PipelineControllerTest.java`

**Context:** See spec section 13 endpoints. All require `@RequireAuth`. Pipeline endpoints scoped to authenticated user (admin scope for cross-user).

- [ ] **Step 1: Create DTO classes**

`PipelineRunResponse`, `PipelineEventResponse`, `PlaybookResponse` — map from entities to API responses.

- [ ] **Step 2: Create PipelineController**

Endpoints:
- `GET /api/sparks/{sparkId}/pipeline` → PipelineRunResponse
- `GET /api/sparks/{sparkId}/pipeline/events` → List<PipelineEventResponse> (paginated)
- `GET /api/sparks/{sparkId}/pipeline/artifacts/{role}` → artifact content
- `POST /api/sparks/{sparkId}/pipeline/checkpoint/{checkpointId}` → resolve checkpoint
- `GET /api/playbooks` → List<PlaybookResponse>

- [ ] **Step 3: Write PipelineController test**

Test: correct response for each endpoint, 404 when pipeline not found, auth required.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(api): add PipelineController with pipeline status, events, artifacts, checkpoint, and playbook endpoints"
```

---

### Task 20: AgentController Routing + AgentCommandResponse DTO

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java`
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/AgentCommandResponse.java` (or create if doesn't exist)
- Test: `service/service-agent/src/test/java/io/strategiz/social/service/agent/controller/AgentControllerPipelineTest.java`

**Context:** AgentController gains routing logic: SIMPLE → VoiceAgentService, PLAYBOOK/FULL → orchestrator. Response DTO adds pipeline fields. See spec sections 1 and 17.

- [ ] **Step 1: Read existing AgentController**

- [ ] **Step 2: Add pipeline fields to AgentCommandResponse**

Add: `pipelineRunId`, `pipelineTier`, `playbook`, `activatedRoles` (List<String>), `executionMode`.

- [ ] **Step 3: Add routing logic to AgentController**

After spark classification, call `PdlcClassifierService.classifyDepth()`. Check for user override (`playbook` param in request). Route: SIMPLE → existing VoiceAgentService flow (with async timeout wrapper), PLAYBOOK/FULL → create PipelineRun, dispatch to orchestrator, return async response.

- [ ] **Step 4: Write AgentController pipeline routing test**

Test: code spark triggers classifier, FULL_PDLC routes to orchestrator, SIMPLE routes to VoiceAgentService, user override playbook honored, response contains pipeline fields.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(api): add PDLC pipeline routing to AgentController with async response contract"
```

---

## Chunk 5: Wave 5 — Knowledge Base + Integration (Parallel)

### Task 21: Knowledge Base — Embedding Infrastructure + RAG Retrieval

**Files:**
- Create: `business/business-agent/src/main/java/io/strategiz/social/business/agent/pipeline/KnowledgeBaseService.java`
- Test: `business/business-agent/src/test/java/io/strategiz/social/business/agent/pipeline/KnowledgeBaseServiceTest.java`

**Context:** See spec section 11. Embeddings via OpenAI text-embedding-3-small. Firestore vector search for retrieval. Each role queries top-K knowledge entries by semantic similarity.

- [ ] **Step 1: Create KnowledgeBaseService**

`@Service`. Inject: `PdlcRoleKnowledgeRepository`, OpenAI client (for embeddings). Methods:
- `addKnowledge(role, category, content, source)` — generates embedding, saves to Firestore
- `queryKnowledge(role, contextText, topK)` — embeds context, Firestore vector search, returns top-K entries
- `updateRelevance(knowledgeId, delta)` — adjust relevance score
- `bootstrapKnowledge()` — load initial best practices per role

- [ ] **Step 2: Write KnowledgeBaseService test**

Test: knowledge stored with embedding, query returns relevant entries, relevance update works.

- [ ] **Step 3: Create bootstrap knowledge entries for each role**

Create initial BEST_PRACTICE entries for each of the 12 roles. E.g., PM: "Always define measurable acceptance criteria", Implementer: "Handle error cases for all external API calls", Reviewer: "Check for OWASP top 10 vulnerabilities".

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(pipeline): add KnowledgeBaseService with embedding generation, vector search, and bootstrap knowledge"
```

---

### Task 22: Integration Wiring + Build Verification

**Files:**
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/ai/AiSdlcStepDefaults.java` (add 5 new step defaults)
- Verify: full build across all modules

- [ ] **Step 1: Add defaults for 5 new AiSdlcStep values in AiSdlcStepDefaults**

REQUIREMENTS_GATHERING → anthropic-agentic, sonnet
SYSTEM_DESIGN → anthropic-agentic, opus
UI_UX_DESIGN → anthropic-agentic, sonnet
SECURITY_REVIEW → anthropic-agentic, opus
RETROSPECTIVE → anthropic-agentic, sonnet

- [ ] **Step 2: Verify full build**

Run: `./gradlew build -x test` (compile check)
Run: `./gradlew test` (all tests)

- [ ] **Step 3: Fix any compilation errors or test failures**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(pipeline): wire AiSdlcStepDefaults for new PDLC steps and verify full build"
```

---

### Task 23: Architecture Documentation Update

**Files:**
- Create or update: `docs/architecture/pdlc-pipeline-architecture.md`

- [ ] **Step 1: Write comprehensive architecture doc**

Cover: system overview, component diagram, data flow, Firestore collections, async execution model, playbook system, rework mechanics, knowledge base, API surface, deployment requirements (Claude Code CLI on Cloud Run).

- [ ] **Step 2: Update CLAUDE.md with PDLC pipeline section**

Add section covering: new modules, key services, pipeline flow, playbook system.

- [ ] **Step 3: Commit**

```bash
git commit -m "docs: add PDLC pipeline architecture documentation"
```
