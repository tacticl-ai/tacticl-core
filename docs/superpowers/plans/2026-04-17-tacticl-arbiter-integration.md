# tacticl-core: Arbiter Integration Contract Update Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update tacticl-core to send the full playbook spec, role identities, and GitHub token in the gRPC `SubmitPipelineRequest`, and handle `ask` events from Arbiter as pipeline checkpoints surfaced in Tacticl chat.

**Architecture:** `PdlcV2Service` resolves role identities from tacticl-knowledge and the full playbook config from `PlaybookRegistry`, then sends both to Arbiter. Arbiter `ask` events arrive via the existing callback controller as `CHECKPOINT_REQUESTED` events and are resolved via the existing checkpoint endpoint. GitHub token resolution follows Option C: system token for Tacticl repos, user-granted token from `repo_grants` for user repos.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Gradle, Firestore/MongoDB, existing proto generated stubs

**Depends on:** Plan 1 (Arbiter Rewrite) — the new proto fields must exist in Arbiter before this deploys.

---

## Chunk 1: Role Identity Loader

### Task 1: RoleIdentityLoader — reads SKILL.md content from tacticl-knowledge

**Files:**
- Create: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/RoleIdentityLoader.java`
- Create: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/RoleIdentityLoaderTest.java`

tacticl-knowledge is cloned at a configured path (env var `TACTICL_KNOWLEDGE_PATH`). Role SKILL.md files live at `{knowledgePath}/wiki/auto/moc/{role}-guide.md`.

- [ ] **Step 1: Write failing test**

```java
// RoleIdentityLoaderTest.java
@ExtendWith(MockitoExtension.class)
class RoleIdentityLoaderTest {

    @TempDir
    Path knowledgeDir;

    RoleIdentityLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        // Create role file
        Path mocDir = knowledgeDir.resolve("wiki/auto/moc");
        Files.createDirectories(mocDir);
        Files.writeString(mocDir.resolve("implementer-guide.md"), "# IMPLEMENTER\nWrite code.");
        Files.writeString(mocDir.resolve("reviewer-guide.md"), "# REVIEWER\nReview PRs.");

        loader = new RoleIdentityLoader(knowledgeDir.toString());
    }

    @Test
    void loadsRoleIdentityForKnownRole() {
        Optional<String> content = loader.load("implementer");
        assertThat(content).isPresent();
        assertThat(content.get()).contains("# IMPLEMENTER");
    }

    @Test
    void returnsEmptyForUnknownRole() {
        Optional<String> content = loader.load("nonexistent");
        assertThat(content).isEmpty();
    }

    @Test
    void loadsAllRolesForPlaybook() {
        List<String> roles = List.of("implementer", "reviewer", "unknown-role");
        Map<String, String> identities = loader.loadAll(roles);
        assertThat(identities).containsKey("implementer");
        assertThat(identities).containsKey("reviewer");
        assertThat(identities).doesNotContainKey("unknown-role");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./gradlew :business:business-pipeline:test --tests "*RoleIdentityLoaderTest*" 2>&1 | tail -20
```

- [ ] **Step 3: Implement RoleIdentityLoader**

```java
// RoleIdentityLoader.java
@Service
public class RoleIdentityLoader {

    private final Path knowledgePath;

    public RoleIdentityLoader(@Value("${tacticl.knowledge.path:}") String knowledgePath) {
        this.knowledgePath = knowledgePath.isBlank() ? null : Path.of(knowledgePath);
    }

    public Optional<String> load(String role) {
        if (knowledgePath == null) return Optional.empty();
        Path file = knowledgePath.resolve("wiki/auto/moc/" + role.toLowerCase() + "-guide.md");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Map<String, String> loadAll(List<String> roles) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String role : roles) {
            load(role).ifPresent(content -> result.put(role, content));
        }
        return result;
    }
}
```

- [ ] **Step 4: Run tests to confirm pass**

```bash
./gradlew :business:business-pipeline:test --tests "*RoleIdentityLoaderTest*" 2>&1 | tail -10
```

- [ ] **Step 5: Add config property to application.properties**

In `application-prod.properties`:
```
tacticl.knowledge.path=/opt/tacticl/tacticl-knowledge
```
In `application-qa.properties`:
```
tacticl.knowledge.path=/opt/tacticl/tacticl-knowledge
```

- [ ] **Step 6: Commit**

```bash
git add business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/RoleIdentityLoader.java \
        business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/RoleIdentityLoaderTest.java \
        application-api/src/main/resources/application-prod.properties \
        application-api/src/main/resources/application-qa.properties
git commit -m "feat(pipeline): add RoleIdentityLoader to read SKILL.md content from tacticl-knowledge"
```

---

### Task 2: PlaybookSpecResolver — resolve playbook name to full spec for Arbiter

**Files:**
- Create: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PlaybookSpecResolver.java`
- Create: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PlaybookSpecResolverTest.java`

The full playbook spec tells Arbiter: which roles to run, in what sequence, where checkpoints go, and rework max.

- [ ] **Step 1: Define PlaybookSpec DTO**

```java
// dto/PlaybookSpec.java
public record PlaybookSpec(
    String name,
    List<String> roles,
    List<String> sequence,       // ordered role names
    List<String> checkpointAfter, // role names after which a checkpoint fires
    int reworkMax                 // max rework iterations (default 3)
) {}
```

- [ ] **Step 2: Write failing tests**

```java
@Test
void resolvesBugFixPlaybook() {
    PlaybookSpecResolver resolver = new PlaybookSpecResolver();
    PlaybookSpec spec = resolver.resolve("BUG_FIX");
    assertThat(spec.roles()).contains("RESEARCHER", "IMPLEMENTER", "REVIEWER", "TESTER");
    assertThat(spec.sequence()).isEqualTo(List.of("RESEARCHER", "IMPLEMENTER", "REVIEWER", "TESTER"));
    assertThat(spec.reworkMax()).isEqualTo(3);
}

@Test
void resolvesFullPdlcPlaybook() {
    PlaybookSpecResolver resolver = new PlaybookSpecResolver();
    PlaybookSpec spec = resolver.resolve("FULL_PDLC");
    assertThat(spec.roles()).hasSize(12);
    assertThat(spec.sequence().get(0)).isEqualTo("PM");
}

@Test
void throwsForUnknownPlaybook() {
    PlaybookSpecResolver resolver = new PlaybookSpecResolver();
    assertThrows(IllegalArgumentException.class, () -> resolver.resolve("UNKNOWN"));
}
```

- [ ] **Step 3: Implement PlaybookSpecResolver**

```java
@Component
public class PlaybookSpecResolver {

    private static final Map<String, PlaybookSpec> PLAYBOOKS = Map.of(
        "FULL_PDLC", new PlaybookSpec("FULL_PDLC",
            List.of("PM","RESEARCHER","ARCHITECT","DESIGNER","PLANNER","IMPLEMENTER",
                    "REVIEWER","TESTER","SECURITY_ANALYST","TECHNICAL_WRITER","DEVOPS","RETRO_ANALYST"),
            List.of("PM","RESEARCHER","ARCHITECT","DESIGNER","PLANNER","IMPLEMENTER",
                    "REVIEWER","TESTER","SECURITY_ANALYST","TECHNICAL_WRITER","DEVOPS","RETRO_ANALYST"),
            List.of("ARCHITECT","IMPLEMENTER","TESTER"), 3),

        "BUG_FIX", new PlaybookSpec("BUG_FIX",
            List.of("RESEARCHER","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("RESEARCHER","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("IMPLEMENTER"), 3),

        "SMALL_FEATURE", new PlaybookSpec("SMALL_FEATURE",
            List.of("PM","PLANNER","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("PM","PLANNER","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("IMPLEMENTER"), 3),

        "REFACTOR", new PlaybookSpec("REFACTOR",
            List.of("RESEARCHER","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("RESEARCHER","IMPLEMENTER","REVIEWER","TESTER"),
            List.of(), 3),

        "INFRA_CHANGE", new PlaybookSpec("INFRA_CHANGE",
            List.of("ARCHITECT","DEVOPS","SECURITY_ANALYST","TESTER"),
            List.of("ARCHITECT","DEVOPS","SECURITY_ANALYST","TESTER"),
            List.of("ARCHITECT"), 2),

        "DOCS_ONLY", new PlaybookSpec("DOCS_ONLY",
            List.of("RESEARCHER","TECHNICAL_WRITER"),
            List.of("RESEARCHER","TECHNICAL_WRITER"),
            List.of(), 1),

        "UI_CHANGE", new PlaybookSpec("UI_CHANGE",
            List.of("DESIGNER","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("DESIGNER","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("DESIGNER"), 3),

        "SECURITY_PATCH", new PlaybookSpec("SECURITY_PATCH",
            List.of("SECURITY_ANALYST","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("SECURITY_ANALYST","IMPLEMENTER","REVIEWER","TESTER"),
            List.of("SECURITY_ANALYST","IMPLEMENTER"), 2)
    );

    public PlaybookSpec resolve(String playbookName) {
        PlaybookSpec spec = PLAYBOOKS.get(playbookName.toUpperCase());
        if (spec == null) throw new IllegalArgumentException("Unknown playbook: " + playbookName);
        return spec;
    }
}
```

- [ ] **Step 4: Run tests to confirm pass**

```bash
./gradlew :business:business-pipeline:test --tests "*PlaybookSpecResolverTest*" 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PlaybookSpecResolver.java \
        business/business-pipeline/src/main/java/io/tacticl/business/pipeline/dto/PlaybookSpec.java \
        business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PlaybookSpecResolverTest.java
git commit -m "feat(pipeline): add PlaybookSpecResolver with all 8 playbook definitions"
```

---

## Chunk 2: GitHub Token Resolution

### Task 3: GitHubTokenResolver — system vs user-granted token (Option C)

**Files:**
- Create: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/GitHubTokenResolver.java`
- Create: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/GitHubTokenResolverTest.java`

Logic: if `repoUrl` contains a Tacticl-owned org (`tacticl-platform`, configurable) → use system `GH_TOKEN`. Otherwise → look up user's `repo_grants` in Firestore.

- [ ] **Step 1: Write failing tests**

```java
@Test
void returnsTacticlSystemTokenForTacticlRepo() {
    GitHubTokenResolver resolver = new GitHubTokenResolver(
        "ghp_system_token",
        Set.of("tacticl-platform", "tacticl"),
        repoGrantsRepository
    );
    Optional<String> token = resolver.resolve("userId123", "https://github.com/tacticl-platform/tacticl-core");
    assertThat(token).contains("ghp_system_token");
    verifyNoInteractions(repoGrantsRepository);
}

@Test
void returnsUserGrantedTokenForExternalRepo() {
    when(repoGrantsRepository.findByUserIdAndRepoUrl("userId123", "https://github.com/user/myrepo"))
        .thenReturn(Optional.of(repoGrant("ghp_user_token")));

    Optional<String> token = resolver.resolve("userId123", "https://github.com/user/myrepo");
    assertThat(token).contains("ghp_user_token");
}

@Test
void returnsEmptyWhenNoGrantForExternalRepo() {
    when(repoGrantsRepository.findByUserIdAndRepoUrl(any(), any())).thenReturn(Optional.empty());
    Optional<String> token = resolver.resolve("userId123", "https://github.com/user/private");
    assertThat(token).isEmpty();
}

@Test
void returnsEmptyWhenNoRepoUrl() {
    Optional<String> token = resolver.resolve("userId123", null);
    assertThat(token).isEmpty();
}
```

- [ ] **Step 2: Implement GitHubTokenResolver**

```java
@Service
public class GitHubTokenResolver {

    private final String systemToken;
    private final Set<String> tacticlOrgs;
    private final RepoGrantRepository repoGrantRepository;

    public GitHubTokenResolver(
        @Value("${github.system-token:}") String systemToken,
        @Value("${tacticl.github.orgs:tacticl-platform,tacticl}") String orgsConfig,
        RepoGrantRepository repoGrantRepository
    ) {
        this.systemToken = systemToken;
        this.tacticlOrgs = Arrays.stream(orgsConfig.split(","))
            .map(String::trim).collect(Collectors.toSet());
        this.repoGrantRepository = repoGrantRepository;
    }

    public Optional<String> resolve(String userId, String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) return Optional.empty();

        if (isTacticlRepo(repoUrl)) {
            return systemToken.isBlank() ? Optional.empty() : Optional.of(systemToken);
        }

        return repoGrantRepository.findByUserIdAndRepoUrl(userId, repoUrl)
            .map(RepoGrant::getAccessToken);
    }

    private boolean isTacticlRepo(String repoUrl) {
        // e.g. https://github.com/tacticl-platform/tacticl-core
        return tacticlOrgs.stream().anyMatch(org -> repoUrl.contains("github.com/" + org + "/"));
    }
}
```

- [ ] **Step 3: Run tests to confirm pass**

```bash
./gradlew :business:business-pipeline:test --tests "*GitHubTokenResolverTest*" 2>&1 | tail -10
```

- [ ] **Step 4: Add properties**

```properties
# application-prod.properties
github.system-token=${GITHUB_SYSTEM_TOKEN:}
tacticl.github.orgs=tacticl-platform,tacticl
```

- [ ] **Step 5: Commit**

```bash
git add business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/GitHubTokenResolver.java \
        business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/GitHubTokenResolverTest.java
git commit -m "feat(pipeline): add GitHubTokenResolver — system token for Tacticl repos, user grant for user repos"
```

---

## Chunk 3: SubmitPipelineRequest Enrichment

### Task 4: Update proto + SubmitPipelineRequest DTO to include new fields

**Files:**
- Modify: `client/client-ai-arbiter/src/main/proto/cidadel/ai/arbiter/pipeline/v1/arbiter_pipeline.proto`
- Modify: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/dto/SubmitPipelineRequest.java`
- Modify: `client/client-ai-arbiter/src/main/java/io/tacticl/client/arbiter/ArbiterGrpcClientImpl.java`

- [ ] **Step 1: Update proto to match Arbiter's new fields**

In `arbiter_pipeline.proto`, add to `SubmitPipelineRequest`:

```protobuf
map<string, string> role_identities    = 6;
string playbook_config_json            = 7;
string github_token                    = 8;
string user_id                         = 9;
string knowledge_namespace             = 10;
string repo_url                        = 11;
map<string, int32> role_ttl_seconds    = 12;
```

- [ ] **Step 2: Regenerate proto stubs**

```bash
./gradlew :client:client-ai-arbiter:generateProto 2>&1 | tail -10
```

- [ ] **Step 3: Update SubmitPipelineRequest DTO**

```java
public record SubmitPipelineRequest(
    String product,
    String pipelineName,
    String requestContextJson,
    String registryBasePath,
    String callbackUrl,
    // New fields
    Map<String, String> roleIdentities,
    String playbookConfigJson,
    String githubToken,
    String userId,
    String knowledgeNamespace,
    String repoUrl,
    Map<String, Integer> roleTtlSeconds
) {}
```

- [ ] **Step 4: Update ArbiterGrpcClientImpl to map new fields**

```java
var grpcRequest = PipelineRequest.newBuilder()
    .setProduct(request.product())
    .setPipelineName(request.pipelineName())
    .setRequestContextJson(request.requestContextJson())
    .setRegistryBasePath(request.registryBasePath())
    .setCallbackUrl(request.callbackUrl())
    // New fields
    .putAllRoleIdentities(request.roleIdentities() != null ? request.roleIdentities() : Map.of())
    .setPlaybookConfigJson(request.playbookConfigJson() != null ? request.playbookConfigJson() : "")
    .setGithubToken(request.githubToken() != null ? request.githubToken() : "")
    .setUserId(request.userId() != null ? request.userId() : "")
    .setKnowledgeNamespace(request.knowledgeNamespace() != null ? request.knowledgeNamespace() : "")
    .setRepoUrl(request.repoUrl() != null ? request.repoUrl() : "")
    .putAllRoleTtlSeconds(request.roleTtlSeconds() != null ? request.roleTtlSeconds() : Map.of())
    .build();
```

- [ ] **Step 5: Build to confirm no errors**

```bash
./gradlew :client:client-ai-arbiter:build 2>&1 | tail -10
```

- [ ] **Step 6: Commit**

```bash
git add client/client-ai-arbiter/
git commit -m "feat(arbiter-client): extend SubmitPipelineRequest with role identities, playbook, github token"
```

---

### Task 5: PdlcV2Service — populate new fields on submit

**Files:**
- Modify: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PdlcV2Service.java`
- Modify: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PdlcV2ServiceTest.java`

- [ ] **Step 1: Inject new dependencies into PdlcV2Service**

```java
public PdlcV2Service(
    ArbiterPipelineService arbiterPipelineService,
    PipelineRunRepository pipelineRunRepository,
    PipelineEventEmitter pipelineEventEmitter,
    PipelineCheckpointRepository checkpointRepository,
    RoleIdentityLoader roleIdentityLoader,        // NEW
    PlaybookSpecResolver playbookSpecResolver,    // NEW
    GitHubTokenResolver githubTokenResolver,      // NEW
    ObjectMapper objectMapper
) { ... }
```

- [ ] **Step 2: Enrich submitPipeline()**

```java
public PipelineRun submitPipeline(String userId, String sparkId, String sparkRequest,
                                   String repoUrl, String playbook, List<String> skipRoles,
                                   BigDecimal costCeiling) {
    // Resolve playbook spec
    PlaybookSpec spec = playbookSpecResolver.resolve(playbook);

    // Apply skip roles
    List<String> activeRoles = spec.roles().stream()
        .filter(r -> !skipRoles.contains(r))
        .toList();

    // Load role identities
    Map<String, String> roleIdentities = roleIdentityLoader.loadAll(activeRoles);

    // Resolve GitHub token
    Optional<String> githubToken = githubTokenResolver.resolve(userId, repoUrl);

    // Build knowledge namespace
    String knowledgeNamespace = "tacticl/user_" + userId;

    // Build role TTLs (seconds) per role type
    Map<String, Integer> roleTtlSeconds = buildRoleTtls(activeRoles);

    SubmitPipelineRequest request = new SubmitPipelineRequest(
        "tacticl",
        playbook,
        sparkRequest,
        registryBasePath,
        callbackUrl,
        roleIdentities,
        objectMapper.writeValueAsString(spec),
        githubToken.orElse(""),
        userId,
        knowledgeNamespace,
        repoUrl != null ? repoUrl : "",
        roleTtlSeconds
    );

    // ... existing submit logic
}

private Map<String, Integer> buildRoleTtls(List<String> roles) {
    // Role TTLs in seconds (base values — Arbiter does NOT apply a multiplier)
    Map<String, Integer> ttls = new HashMap<>();
    for (String role : roles) {
        ttls.put(role, switch (role.toUpperCase()) {
            case "IMPLEMENTER" -> 3000;
            case "ARCHITECT", "PLANNER" -> 1800;
            case "TESTER", "REVIEWER", "SECURITY_ANALYST" -> 1200;
            default -> 900;
        });
    }
    return ttls;
}
```

- [ ] **Step 3: Write test for enriched submit**

```java
@Test
void submitPipelinePopulatesRoleIdentitiesAndPlaybookSpec() throws Exception {
    // Given
    when(roleIdentityLoader.loadAll(any())).thenReturn(Map.of("IMPLEMENTER", "# IMPLEMENTER content"));
    when(playbookSpecResolver.resolve("BUG_FIX")).thenReturn(bugFixSpec());
    when(githubTokenResolver.resolve(any(), any())).thenReturn(Optional.of("ghp_test"));
    when(arbiterPipelineService.submitPipeline(any())).thenReturn(arbiterResponse("pipe-1"));

    // When
    service.submitPipeline("user-1", "spark-1", "Fix null pointer", "https://github.com/tacticl/core", "BUG_FIX", List.of(), new BigDecimal("50"));

    // Then
    ArgumentCaptor<SubmitPipelineRequest> captor = ArgumentCaptor.forClass(SubmitPipelineRequest.class);
    verify(arbiterPipelineService).submitPipeline(captor.capture());
    SubmitPipelineRequest sent = captor.getValue();
    assertThat(sent.roleIdentities()).containsKey("IMPLEMENTER");
    assertThat(sent.githubToken()).isEqualTo("ghp_test");
    assertThat(sent.userId()).isEqualTo("user-1");
    assertThat(sent.knowledgeNamespace()).isEqualTo("tacticl/user_user-1");
    assertThat(sent.playbookConfigJson()).contains("BUG_FIX");
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :business:business-pipeline:test --tests "*PdlcV2ServiceTest*" 2>&1 | tail -15
```

- [ ] **Step 5: Commit**

```bash
git add business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PdlcV2Service.java \
        business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PdlcV2ServiceTest.java
git commit -m "feat(pipeline): enrich SubmitPipelineRequest with role identities, playbook spec, github token"
```

---

## Chunk 4: Ask → Checkpoint Bridge

### Task 6: Handle Arbiter `ask` callback as CHECKPOINT_REQUESTED

Arbiter sends `ask` events via the existing callback controller. These need to surface in Tacticl chat as a question the user can answer. The existing checkpoint resolve endpoint handles the response.

**Files:**
- Modify: `business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PdlcV2Service.java`
- Modify: `business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/PdlcV2ServiceTest.java`

- [ ] **Step 1: Extend PipelineCallbackEvent to include ask fields**

In `dto/PipelineCallbackEvent.java`, add:

```java
// Existing fields kept as-is
// New fields for ask events:
private String askId;         // Arbiter's askId
private String question;      // Question text
private List<String> options; // Answer options (may be empty for free text)
```

- [ ] **Step 2: Handle `ask` event type in handleCallbackEvent()**

In `PdlcV2Service.handleCallbackEvent()`, add:

```java
case "ask" -> {
    PipelineCheckpoint checkpoint = PipelineCheckpoint.builder()
        .id(event.getAskId())         // use Arbiter's askId as checkpointId
        .pipelineRunId(run.getId())
        .sparkId(run.getSparkId())
        .phase(event.getPhase())
        .type(CheckpointType.AGENT_ASK)
        .question(event.getQuestion())
        .options(event.getOptions())
        .status(CheckpointStatus.PENDING)
        .build();
    checkpointRepository.save(checkpoint);

    // Update run state
    run.setStatus(PipelineRunStatus.PAUSED_AT_CHECKPOINT);
    run.setCurrentCheckpointId(event.getAskId());
    pipelineRunRepository.save(run);

    // Emit SSE event so chat UI shows the question
    pipelineEventEmitter.emit(run.getId(), PipelineEvent.checkpoint(checkpoint));
}
```

- [ ] **Step 3: Handle checkpoint resolution — forward answer to Arbiter**

When `POST /v1/sparks/{sparkId}/pipeline/checkpoint/{checkpointId}` is called:

```java
// In PdlcV2Service.resolveCheckpoint():
public void resolveCheckpoint(String sparkId, String checkpointId, CheckpointDecision decision) {
    PipelineCheckpoint checkpoint = checkpointRepository.findById(checkpointId).orElseThrow();

    if (checkpoint.getType() == CheckpointType.AGENT_ASK) {
        // Forward answer back to Arbiter so agent unblocks
        arbiterPipelineService.resolveCheckpoint(ResolveCheckpointRequest.builder()
            .pipelineId(run.getArbiterPipelineId())
            .checkpointId(checkpointId)
            .answer(decision.getAnswer())        // user's free text or selected option
            .build());
    }

    // ... existing approval/rework/cancel logic
}
```

- [ ] **Step 4: Write tests**

```java
@Test
void askCallbackCreatesPendingCheckpoint() {
    PipelineCallbackEvent event = PipelineCallbackEvent.builder()
        .type("ask").pipelineRunId("run-1").phase("IMPLEMENTER")
        .askId("ask-abc").question("Which approach?").options(List.of("A", "B"))
        .build();

    service.handleCallbackEvent(event);

    verify(checkpointRepository).save(argThat(c ->
        c.getId().equals("ask-abc") &&
        c.getType() == CheckpointType.AGENT_ASK &&
        c.getQuestion().equals("Which approach?")
    ));
    verify(pipelineEventEmitter).emit(any(), argThat(e -> e.getType().equals("CHECKPOINT_REQUESTED")));
}

@Test
void checkpointResolutionForwardsAnswerToArbiter() {
    PipelineCheckpoint checkpoint = pendingAskCheckpoint("ask-abc");
    when(checkpointRepository.findById("ask-abc")).thenReturn(Optional.of(checkpoint));

    CheckpointDecision decision = CheckpointDecision.builder().answer("Option A").build();
    service.resolveCheckpoint("spark-1", "ask-abc", decision);

    verify(arbiterPipelineService).resolveCheckpoint(argThat(r ->
        r.getCheckpointId().equals("ask-abc") &&
        r.getAnswer().equals("Option A")
    ));
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :business:business-pipeline:test --tests "*PdlcV2ServiceTest*" 2>&1 | tail -15
```

- [ ] **Step 6: Commit**

```bash
git add business/business-pipeline/src/main/java/io/tacticl/business/pipeline/ \
        business/business-pipeline/src/test/java/io/tacticl/business/pipeline/
git commit -m "feat(pipeline): bridge Arbiter ask events to chat checkpoints with answer forwarding"
```

---

## Chunk 5: Integration Test + Build

### Task 7: Full build and integration smoke test

- [ ] **Step 1: Run full pipeline module tests**

```bash
./gradlew :business:business-pipeline:test :service:service-pipeline:test :client:client-ai-arbiter:test 2>&1 | tail -20
```
Expected: all pass.

- [ ] **Step 2: Build entire project**

```bash
./gradlew build -x test 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify Arbiter stub still works when host not configured**

In `ArbiterPipelineServiceStub`, verify that new fields in `SubmitPipelineRequest` don't cause NPE:

```bash
./gradlew :client:client-ai-arbiter:test 2>&1 | tail -10
```

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore(pipeline): tacticl-core arbiter integration complete — role identities, playbook spec, ask→checkpoint"
```
