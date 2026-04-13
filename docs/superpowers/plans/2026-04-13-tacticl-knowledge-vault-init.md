# Tacticl Knowledge Vault — Initialization Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the `tacticl-knowledge` Obsidian vault repo — seeded with all known codebase conventions, architectural decisions, gotchas, and role Maps of Content for all 12 PDLC roles.

**Architecture:** A new git repo at `/Users/cuztomizer/Documents/GitHub/tacticl-knowledge/` structured as an Obsidian vault following Karpathy's LLM Wiki pattern. Content is split into `raw/` (immutable), `wiki/auto/` (RETRO_ANALYST auto-commits), `wiki/proposed/` (awaiting human approval), and `approved/` (human-approved learnings). Each PDLC role gets a MOC (Map of Content) as its curated entry point into the vault.

**Tech Stack:** Markdown + YAML frontmatter, Obsidian `.obsidian/` JSON config, Git

---

## Files to Create

| Path | Purpose |
|------|---------|
| `tacticl-knowledge/schema.md` | RETRO_ANALYST rules: how to write/maintain vault pages |
| `tacticl-knowledge/.obsidian/app.json` | Obsidian app config |
| `tacticl-knowledge/.obsidian/graph.json` | Graph view config (show backlinks) |
| `tacticl-knowledge/wiki/auto/conventions/jackson-3-imports.md` | Jackson 3 import rules |
| `tacticl-knowledge/wiki/auto/conventions/gradle-module-structure.md` | Module layering rules |
| `tacticl-knowledge/wiki/auto/conventions/naming-patterns.md` | BaseService/BaseController/BaseEntity naming |
| `tacticl-knowledge/wiki/auto/conventions/constructor-injection.md` | No @Autowired on fields |
| `tacticl-knowledge/wiki/auto/conventions/optional-return.md` | Return Optional<T> for queries |
| `tacticl-knowledge/wiki/auto/conventions/base-classes.md` | Required base class extensions |
| `tacticl-knowledge/wiki/auto/entities/spark-entity.md` | Spark Firestore entity |
| `tacticl-knowledge/wiki/auto/entities/pipeline-run-entity.md` | PipelineRun MongoDB entity |
| `tacticl-knowledge/wiki/auto/entities/social-post-entity.md` | SocialPost state machine |
| `tacticl-knowledge/wiki/auto/entities/device-entity.md` | DeviceRegistration entity |
| `tacticl-knowledge/approved/gotchas/vault-https-localhost.md` | Vault uses HTTPS not HTTP |
| `tacticl-knowledge/approved/gotchas/subcollection-userid-param.md` | findById requires userId |
| `tacticl-knowledge/approved/gotchas/junit-bom-managed.md` | Don't pin JUnit versions |
| `tacticl-knowledge/approved/gotchas/anthropic-403-api-key.md` | 403 = missing Vault key |
| `tacticl-knowledge/approved/decisions/auth-paseto.md` | PASETO over JWT decision |
| `tacticl-knowledge/approved/decisions/firestore-hybrid-schema.md` | Nested vs flat collections |
| `tacticl-knowledge/approved/decisions/jackson-3-migration.md` | Why tools.jackson.* |
| `tacticl-knowledge/wiki/auto/moc/pm-guide.md` | PM role entry point |
| `tacticl-knowledge/wiki/auto/moc/researcher-guide.md` | RESEARCHER role entry point |
| `tacticl-knowledge/wiki/auto/moc/architect-guide.md` | ARCHITECT role entry point |
| `tacticl-knowledge/wiki/auto/moc/designer-guide.md` | DESIGNER role entry point |
| `tacticl-knowledge/wiki/auto/moc/planner-guide.md` | PLANNER role entry point |
| `tacticl-knowledge/wiki/auto/moc/implementer-guide.md` | IMPLEMENTER role entry point |
| `tacticl-knowledge/wiki/auto/moc/reviewer-guide.md` | REVIEWER role entry point |
| `tacticl-knowledge/wiki/auto/moc/tester-guide.md` | TESTER role entry point |
| `tacticl-knowledge/wiki/auto/moc/security-analyst-guide.md` | SECURITY_ANALYST role entry point |
| `tacticl-knowledge/wiki/auto/moc/technical-writer-guide.md` | TECHNICAL_WRITER role entry point |
| `tacticl-knowledge/wiki/auto/moc/devops-guide.md` | DEVOPS role entry point |
| `tacticl-knowledge/wiki/auto/moc/retro-analyst-guide.md` | RETRO_ANALYST role entry point |
| `tacticl-knowledge/raw/role-templates/retro-analyst-boot.md` | RETRO_ANALYST boot.md template |

---

## Chunk 1: Vault Init + Schema

### Task 1: Initialize repo, Obsidian config, and schema.md

**Files:**
- Create: `tacticl-knowledge/` (new git repo)
- Create: `tacticl-knowledge/schema.md`
- Create: `tacticl-knowledge/.obsidian/app.json`
- Create: `tacticl-knowledge/.obsidian/graph.json`
- Create: `tacticl-knowledge/README.md`

- [ ] **Step 1: Initialize the repo**

```bash
cd /Users/cuztomizer/Documents/GitHub
mkdir tacticl-knowledge
cd tacticl-knowledge
git init
mkdir -p wiki/auto/conventions wiki/auto/entities wiki/auto/moc wiki/proposed/patterns wiki/proposed/decisions wiki/proposed/gotchas approved/patterns approved/decisions approved/gotchas raw/role-templates raw/run-summaries raw/spark-archives .obsidian
```

- [ ] **Step 2: Create README.md**

Write `/Users/cuztomizer/Documents/GitHub/tacticl-knowledge/README.md`:

```markdown
# tacticl-knowledge

The Tacticl PDLC Knowledge Vault. An Obsidian vault maintained by the RETRO_ANALYST agent after every pipeline run. Based on Karpathy's LLM Wiki pattern.

## Structure

- `schema.md` — Rules for how RETRO_ANALYST writes and maintains this vault
- `raw/` — Immutable source material (never modified after writing)
- `wiki/auto/` — RETRO_ANALYST auto-commits: safe facts and conventions
- `wiki/proposed/` — Awaiting human approval: higher-risk learnings
- `approved/` — Human-approved learnings

## Opening in Obsidian

Open this repo as an Obsidian vault. The graph view shows all knowledge connections.

## Related

- [PDLC v2 SAD](../tacticl-core/docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-sad.md)
- [Knowledge Vault Design](../tacticl-core/docs/superpowers/specs/2026-04-13-tacticl-pdlc-knowledge-vault-design.md)
```

- [ ] **Step 3: Create .obsidian/app.json**

Write `/Users/cuztomizer/Documents/GitHub/tacticl-knowledge/.obsidian/app.json`:

```json
{
  "useMarkdownLinks": false,
  "newLinkFormat": "relative",
  "attachmentFolderPath": "raw",
  "alwaysUpdateLinks": true,
  "promptDelete": true
}
```

- [ ] **Step 4: Create .obsidian/graph.json**

Write `/Users/cuztomizer/Documents/GitHub/tacticl-knowledge/.obsidian/graph.json`:

```json
{
  "collapse-filter": false,
  "search": "",
  "showTags": true,
  "showAttachments": false,
  "hideUnresolved": false,
  "showOrphans": true,
  "collapse-color-groups": false,
  "colorGroups": [
    { "query": "path:wiki/auto/moc", "color": { "a": 1, "rgb": 6829055 } },
    { "query": "path:wiki/auto/conventions", "color": { "a": 1, "rgb": 4390399 } },
    { "query": "path:approved", "color": { "a": 1, "rgb": 3055104 } },
    { "query": "path:wiki/proposed", "color": { "a": 1, "rgb": 16744448 } }
  ],
  "collapse-display": false,
  "showArrow": true,
  "textFadeMultiplier": 0,
  "nodeSizeMultiplier": 1.2,
  "lineSizeMultiplier": 1,
  "collapse-forces": false,
  "centerStrength": 0.518713248970312,
  "repelStrength": 10,
  "linkStrength": 1,
  "linkDistance": 250,
  "scale": 1,
  "close": false
}
```

- [ ] **Step 5: Create schema.md**

Write `/Users/cuztomizer/Documents/GitHub/tacticl-knowledge/schema.md`:

```markdown
# Knowledge Vault Schema

**For RETRO_ANALYST:** These are your rules. Follow them exactly. Every wiki page you write must conform to this schema. Health checks enforce it.

---

## Page Format

Every wiki page MUST have this structure — no exceptions:

```markdown
---
tags: [convention|pattern|decision|gotcha|entity]
roles: [PM, RESEARCHER, ARCHITECT, DESIGNER, PLANNER, IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST, TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST]
auto-approved: true|false
created: YYYY-MM-DD
last-updated: YYYY-MM-DD
pipeline-run: run-{id}
---

# {Title}

## What
One sentence. What is this?

## Why
Why does this matter? What breaks if ignored?

## How
Concrete instructions. Do not use "consider" or "may want to". Use "do" and "do not".

## Example
Code, config, or command examples. Always include at least one.

## Related
- [[page-name]] — one-line description of the relationship
```

---

## Atomic Notes

One concept per file. If you find yourself writing "also, ..." — that is a new page.

---

## Backlinks

When you create a new page, you MUST:
1. Add a `[[this-new-page]]` entry to every page listed under `## Related`
2. Add the new page to the MOC of every role listed in `roles:` frontmatter

When you update a page, check if any Related pages need their backlinks updated.

---

## AUTO vs PROPOSE

**Write to `wiki/auto/` (auto-commit, no approval needed) when ALL of these are true:**
- The content is factual — it describes how the codebase currently works, not how it should work
- The same pattern appeared in ≥3 pipeline runs with no human rejection
- It is NOT security-related, NOT architectural (doesn't change how a role approaches a core task)
- No prior version of this page was rejected by the human reviewer

**Write to `wiki/proposed/` (create GitHub PR, await approval) when ANY of these is true:**
- It reverses or contradicts an existing approved or auto page
- It is security-related (authentication, authorization, secret handling, injection)
- It describes an architectural decision (database choice, framework choice, pattern change)
- It is a GOTCHA derived from a single run (wait for ≥2 runs before auto-committing gotchas)
- You are uncertain whether it should be auto or proposed

When in doubt: propose. It is better to slow down than to inject a wrong learning.

---

## Health Check (run every session)

After every write session, before committing:

1. Scan all pages for `[[broken-links]]` — links to pages that do not exist
2. Scan all pages listed in `## Related` to verify the backlink exists on the target page
3. Check all pages have all 5 required sections (What, Why, How, Example, Related)
4. Check for contradictions: if page A says "do X" and page B says "do not X", flag both for human review
5. Check all role MOCs — verify every page with `roles: [ROLE]` frontmatter is linked from that role's MOC

Report health check findings in `raw/run-summaries/{runId}.md` before committing.

---

## Run Summary Format

After every pipeline, write `raw/run-summaries/{YYYY-MM-DD}-{runId}.md`:

```markdown
---
pipeline-run-id: run-{id}
spark-id: spark-{id}
playbook: FULL_PDLC|BUG_FIX|...
outcome: COMPLETED|FAILED
date: YYYY-MM-DD
---

# Run Summary — {spark title}

## What Was Built
One paragraph.

## What Went Well
Bullet list. Be specific.

## What Failed or Was Reworked
Bullet list. Include rework counts.

## Learnings This Run
For each: what was learned, where it was written (wiki/auto/ or wiki/proposed/), why that tier.

## Health Check Results
Pass/fail for each check. List any issues found and how they were resolved.
```

---

## Tone

You are writing for an AI agent, not a human. Be explicit. Be direct. Use imperative mood.

- DO: "Always use `tools.jackson.databind.json.JsonMapper`. Never use `ObjectMapper`."
- DO NOT: "You may want to consider using Jackson 3's JsonMapper instead of the legacy ObjectMapper."

Short sentences. Code examples. No ambiguity.
```

- [ ] **Step 6: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-knowledge
git add .
git commit -m "feat: initialize tacticl-knowledge Obsidian vault with schema"
```

---

## Chunk 2: Seed Conventions

### Task 2: Write all convention pages

**Files:** All in `wiki/auto/conventions/`

- [ ] **Step 1: Write jackson-3-imports.md**

```markdown
---
tags: [convention]
roles: [IMPLEMENTER, REVIEWER, TESTER, DEVOPS]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Jackson 3 Imports

## What
Tacticl uses Jackson 3 (`tools.jackson.*`). Jackson 2 (`com.fasterxml.jackson.*`) is NOT on the classpath.

## Why
Spring Boot 4.0.3 ships with Jackson 3. Jackson 2 imports will cause compile errors or ClassNotFoundExceptions at runtime.

## How
Always import from `tools.jackson.*`. Never import from `com.fasterxml.jackson.databind.*`.

Use `JsonMapper` instead of `ObjectMapper`. Use `JacksonException` instead of `JsonProcessingException`.

Annotation package is unchanged: `com.fasterxml.jackson.annotation.*` is still correct.

## Example
```java
// CORRECT — Jackson 3
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import com.fasterxml.jackson.annotation.JsonProperty; // annotation package unchanged

// WRONG — Jackson 2, will not compile
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
```

## Related
- [[decisions/jackson-3-migration]]
- [[conventions/naming-patterns]]
```

- [ ] **Step 2: Write gradle-module-structure.md**

```markdown
---
tags: [convention]
roles: [IMPLEMENTER, ARCHITECT, DEVOPS, REVIEWER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Gradle Module Structure

## What
Tacticl uses a nested multi-module Gradle layout with strict layering rules. Modules are grouped into: `service/`, `business/`, `data/`, `client/`, `application/`.

## Why
Violating layer boundaries creates circular dependencies, breaks the build, and couples layers that must stay independent.

## How
Follow these dependency rules. The rule is: lower layers cannot depend on higher layers.

```
service-*    → business-*, client-*, data-*, framework-*  (NEVER other service-*)
business-*   → other business-*, client-*, data-*, framework-*  (NEVER service-*)
client-*     → framework-* and client-base only
data-*       → framework-* only
```

Build commands:
- Full build: `./gradlew build`
- Skip tests: `./gradlew build -x test`
- Single module test: `./gradlew :service:service-agent:test`
- Show module tree: `./gradlew projects`

## Example
```kotlin
// In business-agent/build.gradle.kts — CORRECT
dependencies {
    implementation(project(":business:business-social"))
    implementation(project(":data:data-social"))
}

// WRONG — business depending on service
dependencies {
    implementation(project(":service:service-agent")) // circular!
}
```

## Related
- [[conventions/naming-patterns]]
- [[conventions/base-classes]]
```

- [ ] **Step 3: Write naming-patterns.md**

```markdown
---
tags: [convention]
roles: [IMPLEMENTER, REVIEWER, ARCHITECT]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Naming Patterns

## What
Tacticl follows the Cidadel naming convention: all classes extend base classes named with a `Base` prefix. Controllers, services, entities, and clients all have required base classes.

## Why
The base classes provide shared behavior (auth enforcement, soft delete, error handling). Skipping them bypasses security and consistency guarantees.

## How
Every class in the right layer MUST extend the correct base:

| Layer | Class type | Must extend |
|-------|-----------|------------|
| service-* | REST controller | `BaseController` |
| business-* | Service class | `BaseService` |
| data-* | Firestore entity | `BaseEntity` |
| client-* | HTTP client | `BaseHttpClient` |

## Example
```java
// CORRECT
@RestController
public class SparkController extends BaseController {

@Service
public class SparkService extends BaseService {

@Document
public class Spark extends BaseEntity {
```

## Related
- [[conventions/base-classes]]
- [[conventions/constructor-injection]]
- [[conventions/gradle-module-structure]]
```

- [ ] **Step 4: Write constructor-injection.md**

```markdown
---
tags: [convention]
roles: [IMPLEMENTER, REVIEWER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Constructor Injection

## What
All Spring dependencies are injected via constructor. Never use `@Autowired` on fields or setter methods.

## Why
Field injection hides dependencies, makes testing hard (cannot mock without reflection), and is not recommended by Spring since 5.x. Constructor injection makes dependencies explicit and allows `final` fields.

## How
Declare all dependencies as `private final` fields. Create one constructor with all dependencies. Spring autowires by type automatically if there is exactly one constructor.

Do NOT use `@Autowired` on the constructor — it is redundant in modern Spring and adds noise.

Do NOT use `@Autowired` on fields — ever.

## Example
```java
// CORRECT
@Service
public class SparkService extends BaseService {

    private final SparkRepository sparkRepository;
    private final SparkClassifierService classifier;

    public SparkService(SparkRepository sparkRepository,
                        SparkClassifierService classifier) {
        this.sparkRepository = sparkRepository;
        this.classifier = classifier;
    }
}

// WRONG — field injection
@Service
public class SparkService extends BaseService {
    @Autowired
    private SparkRepository sparkRepository; // never do this
}
```

## Related
- [[conventions/naming-patterns]]
- [[conventions/optional-return]]
```

- [ ] **Step 5: Write optional-return.md**

```markdown
---
tags: [convention]
roles: [IMPLEMENTER, REVIEWER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Optional Return for Queries

## What
All repository query methods that may return no result MUST return `Optional<T>`, never `null` or a bare entity type.

## Why
Returning null for missing records forces all callers to null-check. Forgetting a null check causes NullPointerExceptions in production. `Optional<T>` makes the absence explicit and forces callers to handle it.

## How
Repository methods: return `Optional<T>`.
Service methods that look up by ID: return `Optional<T>` and let the controller handle 404.
Never call `.get()` on an Optional without a `.isPresent()` check or `.orElseThrow()`.

## Example
```java
// CORRECT — repository
public Optional<Spark> findById(String userId, String sparkId) {
    // ...
}

// CORRECT — service
public Optional<Spark> getSpark(String userId, String sparkId) {
    return sparkRepository.findById(userId, sparkId);
}

// CORRECT — controller
Spark spark = sparkService.getSpark(userId, sparkId)
    .orElseThrow(() -> new NotFoundException("Spark not found: " + sparkId));

// WRONG — returning null
public Spark findById(String sparkId) {
    return null; // never do this
}
```

## Related
- [[conventions/constructor-injection]]
- [[conventions/base-classes]]
```

- [ ] **Step 6: Write base-classes.md**

```markdown
---
tags: [convention]
roles: [IMPLEMENTER, REVIEWER, ARCHITECT]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Required Base Classes

## What
Every controller, service, entity, and HTTP client MUST extend the appropriate Cidadel base class. These come from `cidadel-core` via GitHub Packages.

## Why
Base classes provide: auth enforcement (`@RequireAuth`), soft delete (`isActive` flag), error handling (`StandardErrorResponse`), and structured logging. Skipping them means manually re-implementing these — or missing them entirely.

## How
Import the base class from the correct Cidadel framework module. Add it as a dependency in the module's `build.gradle.kts` via the parent `build.gradle.kts` shared deps.

Soft delete: call `entity.delete()` which sets `isActive = false`. Never hard-delete Firestore documents.

Subcollection repositories: use `findById(userId, id)` — NOT `findById(id)`. The base class requires the user ID for subcollection path resolution.

## Example
```java
// Controller — from framework-authorization
import io.strategiz.framework.authorization.BaseController;

// Service — from framework-logging (or similar cidadel module)
import io.cidadel.framework.base.BaseService;

// Entity — from data-framework-base
import io.cidadel.data.base.BaseEntity;

// HTTP client — from client-base
import io.cidadel.client.base.BaseHttpClient;
```

## Related
- [[conventions/naming-patterns]]
- [[conventions/optional-return]]
- [[approved/gotchas/subcollection-userid-param]]
```

- [ ] **Step 7: Commit all convention pages**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-knowledge
git add wiki/auto/conventions/
git commit -m "feat: seed codebase convention knowledge pages (Jackson 3, modules, naming, injection, Optional, base classes)"
```

---

## Chunk 3: Seed Entities

### Task 3: Write entity knowledge pages

**Files:** All in `wiki/auto/entities/`

- [ ] **Step 1: Write spark-entity.md**

```markdown
---
tags: [entity]
roles: [PM, IMPLEMENTER, ARCHITECT, PLANNER, REVIEWER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Spark Entity

## What
A Spark is the top-level entity for every user request. Every chat message, voice command, and GitHub webhook creates exactly one Spark. There is no manual spark creation.

## Why
All downstream entities (tactics, execution logs, checkpoints, device commands) reference a sparkId. The spark is the unit of work tracking.

## How
Sparks live in the flat `sparks/` Firestore collection (not under a user subcollection — they are operational data).

State machine: `PENDING → ROUTING → QUEUED | EXECUTING → CHECKPOINT → COMPLETED | FAILED | CANCELLED`

Types (set by SparkClassifierService): `code | social | research | devops | creative | data`

`code` and `devops` types are routed through PdlcClassifierService for pipeline tier selection.

Creation: always via `SparkService.createSpark()` — never directly write to Firestore.

## Example
```java
// Creating a spark
Spark spark = sparkService.createSpark(userId, commandText, sessionId);

// State transitions
sparkService.markRunning(spark.getId());
sparkService.markCloudCompleted(spark.getId(), tokenCount, modelId);
```

Firestore path: `sparks/{sparkId}`

## Related
- [[entities/pipeline-run-entity]]
- [[entities/device-entity]]
- [[approved/decisions/firestore-hybrid-schema]]
```

- [ ] **Step 2: Write pipeline-run-entity.md**

```markdown
---
tags: [entity]
roles: [ARCHITECT, IMPLEMENTER, DEVOPS, RETRO_ANALYST]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# PipelineRun Entity

## What
A PipelineRun tracks the full lifecycle of one PDLC pipeline execution. One PipelineRun per spark that is classified as PLAYBOOK or FULL_PDLC tier. Lives in MongoDB (NOT Firestore — this is v2 architecture).

## Why
Pipeline state is complex (12 roles, parallel phases, rework loops, checkpoints). Firestore cannot efficiently query or aggregate this data. MongoDB's document model handles nested phase/role state naturally.

## How
MongoDB database: `tacticl_pdlc`, collection: `pipeline_runs`.

Top-level fields: `_id` (runId UUID), `sparkId`, `userId`, `playbook`, `status`, `sparkRequest`, `repoUrl`, `skipRoles[]`, `costCeilingUsd`, `totalCostUsd`, `createdAt`, `updatedAt`.

Nested `phases` map: keyed by phase ID (`PRODUCT`, `DESIGN`, `DEVELOPMENT`, `QUALITY`, `DEPLOY`). Each phase has: `status`, `startedAt`, `completedAt`, `roles` map, `checkpointId`, `checkpointStatus`.

State: `PENDING → RUNNING → PAUSED_AT_CHECKPOINT → RUNNING → COMPLETED | FAILED | CANCELLED`

Do NOT write to this collection from tacticl-core — it is owned by the arbiter shell. tacticl-core reads via gRPC `GetPipelineStatus`.

## Example
```json
{
  "_id": "run-abc123",
  "sparkId": "spark-xyz789",
  "playbook": "FULL_PDLC",
  "status": "RUNNING",
  "phases": {
    "PRODUCT": { "status": "COMPLETED", "roles": { "PM": { "status": "COMPLETED", "costUsd": 2.10 } } }
  }
}
```

## Related
- [[entities/spark-entity]]
- [[approved/decisions/firestore-hybrid-schema]]
```

- [ ] **Step 3: Write social-post-entity.md**

```markdown
---
tags: [entity]
roles: [IMPLEMENTER, REVIEWER, TECHNICAL_WRITER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# SocialPost Entity

## What
SocialPost tracks a piece of social media content through its publication lifecycle. Lives in flat `social_posts/` Firestore collection.

## Why
Posts go through async publishing — the scheduler picks up QUEUED posts and publishes them. The state machine prevents double-publishing and tracks failures.

## How
State machine: `DRAFT → QUEUED → PUBLISHING → PUBLISHED | FAILED | CANCELLED`

Supported platforms: `TWITTER`, `LINKEDIN`, `INSTAGRAM`, `GOOGLE_PHOTOS` (read-only source, not publish target).

Any publish action MUST require Tier 1 user confirmation before transitioning from DRAFT to QUEUED. Never auto-queue without user approval.

`PostPublisherJob` is `@Scheduled` — polls for QUEUED posts due for publishing. It uses `@Retryable(maxAttempts=3, backoff=@Backoff(delay=2000, multiplier=2))`.

## Example
```
DRAFT → (user confirms via Tier 1 confirmation) → QUEUED
QUEUED → (PostPublisherJob picks up) → PUBLISHING
PUBLISHING → (API call succeeds) → PUBLISHED
PUBLISHING → (API call fails after 3 retries) → FAILED
```

## Related
- [[entities/spark-entity]]
- [[approved/decisions/firestore-hybrid-schema]]
```

- [ ] **Step 4: Write device-entity.md**

```markdown
---
tags: [entity]
roles: [ARCHITECT, IMPLEMENTER, DEVOPS]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# DeviceRegistration Entity

## What
DeviceRegistration represents one of the user's connected devices (phone, laptop, desktop). Stored in the user's subcollection: `tacticl_users/{userId}/devices/{deviceId}`.

## Why
Device capabilities, battery state, and settings determine routing. The entity is the source of truth for which devices can accept sparks and what execution engine they use.

## How
Device types: `MACOS`, `WINDOWS`, `LINUX` (desktop, priority=0), `IOS`, `ANDROID` (mobile, priority=1).

Desktop devices (priority=0) default to Claude Code CLI engine. Mobile always uses LEGACY engine.

`DeviceSettings` is embedded in the document (not a separate collection). Contains: `executionEngine` (CLAUDE_CODE | LEGACY | AUTO), `maxDaemons`, `autoWake`, `sparkPreferences`.

`ClaudeCodeConfig` is embedded in `DeviceSettings`. Contains: model, maxTurns, maxBudgetUsd, allowedTools, permissionMode.

Subcollection access: always pass `userId` to the repository — `deviceRepository.findById(userId, deviceId)`.

## Example
```java
Optional<DeviceRegistration> device = deviceRepository.findById(userId, deviceId);

// Check if desktop
boolean isDesktop = device.map(d -> d.getDeviceType().getPriority() == 0).orElse(false);
```

## Related
- [[entities/spark-entity]]
- [[conventions/optional-return]]
- [[approved/gotchas/subcollection-userid-param]]
```

- [ ] **Step 5: Commit entity pages**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-knowledge
git add wiki/auto/entities/
git commit -m "feat: seed entity knowledge pages (Spark, PipelineRun, SocialPost, Device)"
```

---

## Chunk 4: Seed Approved Learnings

### Task 4: Write gotcha and decision pages

**Files:** `approved/gotchas/` and `approved/decisions/`

- [ ] **Step 1: Write vault-https-localhost.md**

```markdown
---
tags: [gotcha]
roles: [IMPLEMENTER, DEVOPS, TESTER]
auto-approved: false
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Vault Uses HTTPS on Localhost

## What
HashiCorp Vault on the local dev environment runs at `https://localhost:8200`, not `http://localhost:8200`. The `s` matters.

## Why
The dev Vault is configured with TLS. Using HTTP will result in connection refused or SSL handshake errors that are confusing to diagnose.

## How
In any config file, environment variable, or code that connects to Vault: always use `https://localhost:8200`.

Prod Vault URL: `https://strategiz-vault-43628135674.us-east1.run.app`

Vault context for Tacticl secrets: `tacticl`
Vault context for shared LLM API keys (Anthropic, OpenAI, Grok): `strategiz`

## Example
```yaml
# application-local.yml — CORRECT
vault:
  uri: https://localhost:8200
  token: ${VAULT_TOKEN}

# WRONG — will fail with connection error
vault:
  uri: http://localhost:8200
```

## Related
- [[approved/gotchas/anthropic-403-api-key]]
```

- [ ] **Step 2: Write subcollection-userid-param.md**

```markdown
---
tags: [gotcha]
roles: [IMPLEMENTER, REVIEWER]
auto-approved: false
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Subcollection Repos Require userId Parameter

## What
Repository classes for Firestore subcollections use `findById(userId, id)` — NOT `findById(id)`. The userId is required to construct the subcollection path.

## Why
Subcollections live at `tacticl_users/{userId}/{collection}/{docId}`. Without the userId, the Firestore path cannot be constructed. Calling `findById(id)` on a subcollection repo will either fail to compile or return wrong results.

## How
Always pass the authenticated user's ID as the first argument to subcollection repository calls. The authenticated userId comes from the `AuthenticatedUser` object in the controller, passed down through the service to the repo.

Affected subcollections: `devices/`, `social_integrations/`, `repo_grants/`, `agent_tokens/`, `agent_memory/`.

Flat collections (sparks, tactics, social_posts) use the standard `findById(id)`.

## Example
```java
// CORRECT — subcollection repo
Optional<DeviceRegistration> device = deviceRepository.findById(userId, deviceId);

// WRONG — missing userId
Optional<DeviceRegistration> device = deviceRepository.findById(deviceId); // compile error or wrong

// CORRECT — flat collection repo
Optional<Spark> spark = sparkRepository.findById(sparkId);
```

## Related
- [[conventions/optional-return]]
- [[conventions/base-classes]]
- [[entities/device-entity]]
```

- [ ] **Step 3: Write junit-bom-managed.md**

```markdown
---
tags: [gotcha]
roles: [IMPLEMENTER, TESTER, DEVOPS]
auto-approved: false
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Do Not Pin JUnit Versions

## What
JUnit versions are managed by the Spring Boot BOM (Bill of Materials). Do NOT add explicit JUnit version numbers to `gradle/libs.versions.toml` or any `build.gradle.kts`.

## Why
Spring Boot 4.0.3 provides JUnit 6.0.3 via its BOM. Pinning an explicit version causes version conflicts that result in cryptic test failures or build errors.

## How
Do not add `junit = "6.0.3"` or any JUnit version to `libs.versions.toml`.
Do not add `testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")` with an explicit version.
Spring Boot's test starter (`spring-boot-starter-test`) pulls the correct JUnit version automatically.

## Example
```toml
# libs.versions.toml — CORRECT (no JUnit version)
[versions]
spring-boot = "4.0.3"
jackson = "3.0.0"
# (no junit entry)

# WRONG — explicit JUnit pin causes conflicts
[versions]
junit = "6.0.3"  # remove this
```

## Related
- [[conventions/gradle-module-structure]]
```

- [ ] **Step 4: Write anthropic-403-api-key.md**

```markdown
---
tags: [gotcha]
roles: [IMPLEMENTER, DEVOPS, TESTER]
auto-approved: false
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Anthropic 403 = Missing API Key in Vault

## What
An HTTP 403 response from the Anthropic API during local dev or testing almost always means the API key is not loaded from Vault, not that the key is invalid.

## Why
Tacticl loads LLM API keys from Vault at startup (`secret/strategiz/anthropic`, key: `api-key`). If Vault is not running, the token is not exported, or the secret path is wrong, the key loads as null or empty — causing a 403 on the first LLM call.

## How
When you see a 403 from Anthropic:
1. Check Vault is running: `vault status` (should show `Sealed: false`)
2. Check `VAULT_TOKEN` is exported in your shell
3. Check the secret exists: `vault kv get secret/strategiz/anthropic` — look for `api-key` in the output
4. Restart tacticl-core after fixing Vault — keys are loaded at startup, not per-request

Vault context for Anthropic: `strategiz` (NOT `tacticl`). The LLM keys are shared with Strategiz.

## Example
```bash
# Check Vault status
vault status

# Verify the key exists
vault kv get secret/strategiz/anthropic
# Expected output includes: api-key = sk-ant-...

# If missing, set it
vault kv put secret/strategiz/anthropic api-key=sk-ant-YOUR-KEY-HERE
```

## Related
- [[approved/gotchas/vault-https-localhost]]
```

- [ ] **Step 5: Write auth-paseto.md**

```markdown
---
tags: [decision]
roles: [ARCHITECT, SECURITY_ANALYST, IMPLEMENTER]
auto-approved: false
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Auth Uses PASETO v4.local (Not JWT)

## What
Tacticl uses PASETO v4.local (symmetric encryption) for auth tokens. JWT is NOT used.

## Why
PASETO v4.local is simpler than JWT: no algorithm confusion attacks, no "none" algorithm vulnerability, no library fragmentation. The symmetric key is shared between Tacticl and Strategiz enabling cross-product SSO without token exchange.

## How
Token issuance: `cidadel-core` `framework-token-issuance` library. Do NOT implement your own token logic.

Token validation: use `@RequireAuth` annotation on controllers. The framework handles validation.

Claims: `userId`, `scopes[]`, `product` (`tacticl`), `deviceId` (optional), `issuedAt`, `expiresAt`.

Token lifetime: 15 minutes (access) + 30 days (refresh).

Vault path for symmetric key: managed by `framework-token-issuance` — do not read or use the raw key in application code.

## Example
```java
// CORRECT — use the annotation
@RequireAuth
@GetMapping("/v1/sparks")
public ResponseEntity<List<SparkResponse>> getSparks(AuthenticatedUser user) {
    return ok(sparkService.getSparks(user.getUserId()));
}

// WRONG — manual token parsing
String userId = jwtParser.parse(token).getClaim("userId"); // never do this
```

## Related
- [[approved/decisions/firestore-hybrid-schema]]
```

- [ ] **Step 6: Write firestore-hybrid-schema.md**

```markdown
---
tags: [decision]
roles: [ARCHITECT, IMPLEMENTER, REVIEWER]
auto-approved: false
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Firestore Hybrid Schema (Approach B)

## What
Tacticl uses a hybrid Firestore schema: user-owned data lives in subcollections under `tacticl_users/{userId}/`, operational data lives in flat top-level collections.

## Why
Subcollections for user data enforce ownership at the data layer — a user cannot accidentally access another user's devices. Flat collections for operational data (sparks, posts) allow efficient cross-user querying by the system.

## How
**Nested under user** (subcollections — require userId for path):
- `tacticl_users/{userId}/devices/`
- `tacticl_users/{userId}/social_integrations/`
- `tacticl_users/{userId}/repo_grants/`
- `tacticl_users/{userId}/agent_tokens/`
- `tacticl_users/{userId}/agent_memory/`

**Flat collections** (top-level — use standard findById):
- `sparks/`, `tactics/`, `execution_logs/`, `checkpoints/`
- `social_posts/`, `device_commands/`, `action_confirmations/`
- `agent_reminders/`, `agent_audit_log/`

Soft delete only: call `entity.delete()` which sets `isActive = false`. Never hard-delete Firestore documents.

PDLC pipeline state (`pipeline_runs`, `pipeline_events`) is in **MongoDB** not Firestore — do not create Firestore collections for pipeline data.

## Example
```java
// Subcollection — userId required
deviceRepository.findById(userId, deviceId);

// Flat collection — no userId
sparkRepository.findById(sparkId);
```

## Related
- [[approved/gotchas/subcollection-userid-param]]
- [[entities/spark-entity]]
- [[entities/pipeline-run-entity]]
```

- [ ] **Step 7: Write jackson-3-migration.md**

```markdown
---
tags: [decision]
roles: [IMPLEMENTER, REVIEWER]
auto-approved: false
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# Jackson 3 Migration Decision

## What
Tacticl uses Jackson 3 (`tools.jackson.*`) — both cidadel-core 0.4.2 and Spring Boot 4.0.3 ship with Jackson 3.

## Why
Jackson 3 is a complete rewrite with a new package namespace (`tools.jackson.*` instead of `com.fasterxml.jackson.databind.*`). Spring Boot 4 requires it. There is no compatibility mode — you must use one or the other.

## How
Key package changes from Jackson 2 → Jackson 3:
- `ObjectMapper` → `JsonMapper` (import from `tools.jackson.databind.json.JsonMapper`)
- `JsonProcessingException` → `JacksonException` (import from `tools.jackson.core.JacksonException`)
- Annotation package UNCHANGED: `com.fasterxml.jackson.annotation.*` still works

Do NOT mix Jackson 2 and Jackson 3 classes in the same codebase — it will not compile.

## Example
```java
// Jackson 3 — CORRECT
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

JsonMapper mapper = new JsonMapper();
JsonNode node = mapper.readTree(json);

// Jackson 2 — WRONG, will not compile with Spring Boot 4
import com.fasterxml.jackson.databind.ObjectMapper; // wrong
```

## Related
- [[conventions/jackson-3-imports]]
```

- [ ] **Step 8: Commit approved learnings**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-knowledge
git add approved/
git commit -m "feat: seed approved gotchas and decisions (Vault HTTPS, subcollection userId, JUnit BOM, Anthropic 403, PASETO, Firestore schema, Jackson 3)"
```

---

## Chunk 5: Role MOCs

### Task 5: Write all 12 role Maps of Content

**Files:** All in `wiki/auto/moc/`

Each MOC is the first file an agent reads when its workspace is assembled. It lists all relevant knowledge pages as `[[wikilinks]]`. MOCs are written by the engineering team initially, then maintained by RETRO_ANALYST.

- [ ] **Step 1: Write pm-guide.md**

```markdown
---
tags: [moc]
roles: [PM]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# PM — Map of Content

You are the Product Manager role in the Tacticl PDLC pipeline. You write product requirements documents based on user requests. Read this MOC first, then follow links to relevant knowledge.

## Product Knowledge
- [[entities/spark-entity]] — what a Spark is, the core unit of user work
- [[entities/social-post-entity]] — social automation state machine
- [[entities/device-entity]] — device types and capabilities

## Architecture Context
- [[approved/decisions/firestore-hybrid-schema]] — where data lives

## Your Output
Phase 1 Tier 1: `phase-1-prd.md` — product requirements document
Phase 1 Tier 2: `phase-1-product-requirements.md` — detailed requirements

Your output feeds into RESEARCHER (who validates your requirements against the codebase).
```

- [ ] **Step 2: Write researcher-guide.md**

```markdown
---
tags: [moc]
roles: [RESEARCHER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# RESEARCHER — Map of Content

You investigate existing code, external APIs, and technical constraints relevant to the PM's requirements. Use the live repo (Layer 1) as your primary source — grep, glob, and read the actual code.

## Codebase Navigation
- [[conventions/gradle-module-structure]] — where to look for different types of code
- [[entities/spark-entity]] — core entity you'll encounter frequently
- [[entities/pipeline-run-entity]] — PDLC pipeline entity (MongoDB-backed)

## Common Integration Points
- [[approved/gotchas/vault-https-localhost]] — if investigating secret loading
- [[approved/gotchas/anthropic-403-api-key]] — if investigating LLM integration

## Your Output
Phase 1 Tier 2: `phase-1-research-summary.md` — technical findings, risks, integration notes

Your output feeds into ARCHITECT (who designs the solution).
```

- [ ] **Step 3: Write architect-guide.md**

```markdown
---
tags: [moc]
roles: [ARCHITECT]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# ARCHITECT — Map of Content

You design the system architecture for the feature: API surface, data model, service boundaries, integration points. Your design must fit within Tacticl's existing architecture.

## Architecture Constraints
- [[conventions/gradle-module-structure]] — layer boundaries you must respect
- [[conventions/naming-patterns]] — base class requirements
- [[approved/decisions/auth-paseto]] — auth token system
- [[approved/decisions/firestore-hybrid-schema]] — where different data lives
- [[approved/decisions/jackson-3-migration]] — serialization library

## Key Entities
- [[entities/spark-entity]] — core work unit
- [[entities/pipeline-run-entity]] — PDLC pipeline state (MongoDB)
- [[entities/social-post-entity]] — social media state machine
- [[entities/device-entity]] — device routing

## Your Output
Phase 2 Tier 2: `phase-2-architecture.md` — system design, API endpoints, data model
Phase 2 Tier 2: `phase-2-erd.md` — entity relationship diagram

Your output feeds into DESIGNER and PLANNER simultaneously.
```

- [ ] **Step 4: Write designer-guide.md**

```markdown
---
tags: [moc]
roles: [DESIGNER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# DESIGNER — Map of Content

You design the user-facing screens and interaction flows for the feature. Your output is hi-fi HTML mockups using Tacticl's purple design system.

## Design System
Tacticl purple theme:
- `--bg: #0E0E11` (deepest background)
- `--bg-raised: #16161B` (surfaces)
- `--bg-surface: #1C1C23` (cards)
- `--primary: #6C63FF` (purple primary)
- `--primary-lt: #9D97FF` (light purple)
- `--text: #E8E8ED` (primary text)
- `--text-sec: #6B6B7B` (secondary text)
- `--warning: #FBBF24` (amber — HITL)
- `--error: #F87171` (red — critical)

## Your Output
Phase 2 Tier 2: `phase-2-screens-{name}.html` — self-contained hi-fi HTML mockups

Your output feeds into PLANNER (for story breakdown referencing the screens).
```

- [ ] **Step 5: Write planner-guide.md**

```markdown
---
tags: [moc]
roles: [PLANNER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# PLANNER — Map of Content

You break the ARCHITECT's design into implementation stories and tasks. Stories must be independently implementable by IMPLEMENTER.

## Codebase Structure (for story scoping)
- [[conventions/gradle-module-structure]] — module boundaries → natural story boundaries
- [[conventions/naming-patterns]] — what each story produces (controller, service, entity, etc.)

## Entities to Reference
- [[entities/spark-entity]]
- [[entities/pipeline-run-entity]]

## Your Output
Phase 2 Tier 2: `phase-2-stories.json` — structured story breakdown

Format: `{ stories: [{ id, title, description, tasks: [{ id, description, files: [] }], acceptanceCriteria: [] }] }`

Your output feeds into IMPLEMENTER (one story at a time, sequentially or in parallel per pipeline config).
```

- [ ] **Step 6: Write implementer-guide.md**

```markdown
---
tags: [moc]
roles: [IMPLEMENTER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# IMPLEMENTER — Map of Content

You write production code. Read every page linked here before writing a single line. These are non-negotiable conventions — REVIEWER will reject code that violates them.

## Codebase Conventions (read all of these)
- [[conventions/jackson-3-imports]] — always tools.jackson.*, never com.fasterxml.jackson.databind
- [[conventions/gradle-module-structure]] — which module your code goes in, layer dependency rules
- [[conventions/naming-patterns]] — BaseController, BaseService, BaseEntity, BaseHttpClient
- [[conventions/constructor-injection]] — no @Autowired on fields, ever
- [[conventions/optional-return]] — return Optional<T> for queries, never null
- [[conventions/base-classes]] — extend the right base class or it will not compile

## Architecture Decisions
- [[approved/decisions/auth-paseto]] — how auth works, use @RequireAuth
- [[approved/decisions/firestore-hybrid-schema]] — where to put new Firestore collections
- [[approved/decisions/jackson-3-migration]] — serialization classes to use

## Key Entities
- [[entities/spark-entity]] — core work unit
- [[entities/device-entity]] — device routing

## Gotchas (read before touching these areas)
- [[approved/gotchas/subcollection-userid-param]] — subcollection repos need userId
- [[approved/gotchas/junit-bom-managed]] — do not pin JUnit versions
- [[approved/gotchas/vault-https-localhost]] — Vault HTTPS on localhost
- [[approved/gotchas/anthropic-403-api-key]] — 403 = missing Vault key

## Your Output
Phase 3 Tier 2: code changes on `feature/{sparkId}/{storySlug}` branch
Phase 3: `results/metadata.json` with `{ shouldRework, reworkReason, confidence }`

You generate 3 candidate implementations. CRITIC selects the best. Your selected output feeds into TESTER, REVIEWER, SECURITY_ANALYST simultaneously.
```

- [ ] **Step 7: Write reviewer-guide.md**

```markdown
---
tags: [moc]
roles: [REVIEWER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# REVIEWER — Map of Content

You review IMPLEMENTER's code for correctness, convention compliance, and quality. You are one of three critics that must ALL approve before the pipeline moves to DEPLOY.

## What to Check
- [[conventions/jackson-3-imports]] — wrong imports = reject
- [[conventions/constructor-injection]] — field @Autowired = reject
- [[conventions/optional-return]] — returning null from query = reject
- [[conventions/naming-patterns]] — wrong base class = reject
- [[conventions/gradle-module-structure]] — wrong layer dependency = reject
- [[approved/decisions/auth-paseto]] — missing @RequireAuth on protected endpoint = reject

## Gotchas to Verify
- [[approved/gotchas/subcollection-userid-param]] — subcollection calls missing userId = reject

## Your Output
Phase 4 Tier 2: `phase-4-code-review.md`
`results/metadata.json`: `{ approved: true|false, shouldRework: boolean, reworkReason: string }`

If you reject, write specific, actionable feedback. IMPLEMENTER uses your feedback to fix the code.
```

- [ ] **Step 8: Write tester-guide.md**

```markdown
---
tags: [moc]
roles: [TESTER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# TESTER — Map of Content

You write tests for IMPLEMENTER's code. In FULL_PDLC, you write FAILING tests BEFORE IMPLEMENTER writes the implementation (TDD enforcement). You are one of three critics that must ALL approve before DEPLOY.

## Test Conventions
- [[conventions/junit-bom-managed]] — do NOT pin JUnit versions, Spring Boot BOM manages them
- [[conventions/gradle-module-structure]] — run tests with `./gradlew :module:module-name:test`

## Gotchas
- [[approved/gotchas/vault-https-localhost]] — tests that load Vault config need HTTPS
- [[approved/gotchas/anthropic-403-api-key]] — integration tests that call LLMs need Vault running

## Coverage Target
Target: ≥80% line coverage on generated code. Write coverage report to results.

## Your Output
Phase 4 Tier 2: `phase-4-test-results.md`, `phase-4-coverage-breakdown.md`
`results/metadata.json`: `{ allPass: boolean, coverage: number, shouldRework: boolean }`
```

- [ ] **Step 9: Write security-analyst-guide.md**

```markdown
---
tags: [moc]
roles: [SECURITY_ANALYST]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# SECURITY_ANALYST — Map of Content

You audit IMPLEMENTER's code for security vulnerabilities. Your veto is a HARD STOP — the pipeline cannot proceed to DEPLOY without your approval. You are the last line of defense before code ships.

## Auth Architecture
- [[approved/decisions/auth-paseto]] — PASETO tokens, @RequireAuth enforcement

## Common Vulnerability Areas (check all)
- Missing `@RequireAuth` on protected endpoints
- SQL/NoSQL injection (Firestore queries with unescaped user input)
- Secrets in logs or error messages (never log tokens, API keys, or PII)
- Missing input validation on user-provided data at REST endpoints
- Exposed internal endpoints (callback endpoints must be IP-restricted)
- OWASP Top 10 — check all categories relevant to the changed code

## Severity Ratings
- CRITICAL: exploitable by unauthenticated attacker, data breach risk → must fix, pipeline blocked
- HIGH: exploitable with auth, significant impact → must fix, pipeline blocked
- MEDIUM: limited scope or requires special conditions → should fix, flag for review
- LOW: defense-in-depth, theoretical risk → note in report, optional fix

## Your Output
Phase 4 Tier 2: `phase-4-security-audit.md`
`results/metadata.json`: `{ approved: boolean, criticalCount: number, highCount: number, shouldRework: boolean }`

CRITICAL or HIGH findings = `approved: false`. Pipeline re-dispatches IMPLEMENTER with your findings.
```

- [ ] **Step 10: Write technical-writer-guide.md**

```markdown
---
tags: [moc]
roles: [TECHNICAL_WRITER]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# TECHNICAL_WRITER — Map of Content

You write documentation for the feature that was implemented. This runs in the DEPLOY phase, after TESTER, REVIEWER, and SECURITY_ANALYST have all approved.

## What to Document
1. **README updates** — if the feature changes how to run or configure the service
2. **API docs** — new or changed endpoints in OpenAPI format (Spring annotations, not manual YAML)
3. **PR description** — used by DEVOPS to create the PR; must include: what was built, why, how to test it, breaking changes (if any)

## Tacticl API Conventions
- All endpoints use `/v1/` prefix
- Auth: `@RequireAuth` annotation
- Response format: `StandardErrorResponse` for errors (from framework-exception)

## Your Output
Phase 5 Tier 2: `phase-5-pr-descriptions.md` — PR title + body for each story branch

The PR description is what the user (Gabriel) sees when reviewing the PR.
```

- [ ] **Step 11: Write devops-guide.md**

```markdown
---
tags: [moc]
roles: [DEVOPS]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# DEVOPS — Map of Content

You handle deployment: creating PRs, writing migration scripts, updating environment config, and ensuring the feature can deploy cleanly.

## Deploy Targets
- **QA**: `gcloud builds submit --config deployment/cloudbuild/cloudbuild-qa.yaml .`
- **Prod**: `gcloud builds submit --config deployment/cloudbuild/cloudbuild-prod.yaml .`
- GCP project: `tacticl`, region: `us-east1`
- QA service: `tacticl-core-qa` (2Gi), Prod service: `tacticl-core` (4Gi)

## Secrets
- All secrets in Vault — never in code or config files
- Vault context: `tacticl` for Tacticl secrets, `strategiz` for LLM keys
- [[approved/gotchas/vault-https-localhost]]

## Firestore
- No migrations needed for Firestore schema changes (schemaless)
- If removing a collection: soft-delete first (mark docs `migrated: true`), hard-delete after 30-day window
- [[approved/decisions/firestore-hybrid-schema]]

## MongoDB (PDLC state only)
- Schema changes handled by arbiter shell — do not touch from tacticl-core
- [[entities/pipeline-run-entity]]

## Your Output
Phase 5 Tier 2: `phase-5-deployment-notes.md` — migration steps, env var changes, rollback plan

Create PRs via GitHub API using the TECHNICAL_WRITER's PR descriptions. Link PRs to the original spark (include sparkId in PR description).
```

- [ ] **Step 12: Write retro-analyst-guide.md**

```markdown
---
tags: [moc]
roles: [RETRO_ANALYST]
auto-approved: true
created: 2026-04-13
last-updated: 2026-04-13
pipeline-run: seed
---

# RETRO_ANALYST — Map of Content

You have two responsibilities:
1. **Retrospective** — analyze what went well and what failed in this pipeline run
2. **Knowledge Vault Maintenance** — update this vault with learnings from the run

**Read `schema.md` in the vault root before writing any pages.** It defines all rules for how to write pages, what AUTO vs PROPOSE means, and how to run a health check.

## Your Pipeline Process

### Step 1: Clone the vault
```bash
git clone https://github.com/[org]/tacticl-knowledge
cd tacticl-knowledge
```

### Step 2: Read all Tier 2 artifacts from this run
Read every `phase-{N}-*.md` file from the current pipeline run. Note: rework counts, rejection reasons, what failed, what patterns repeated.

### Step 3: Identify learnings
For each learning:
- Is it factual, convention-based, seen ≥3 times? → AUTO (`wiki/auto/`)
- Is it a pattern, decision, or gotcha? → PROPOSE (`wiki/proposed/`)

### Step 4: Write/update pages
Follow `schema.md` exactly. Atomic notes, required sections, mandatory backlinks.

### Step 5: Update MOCs
For each page you write, add it to the MOC of every role listed in `roles:` frontmatter.

### Step 6: Run health check
Per `schema.md` health check rules. Report results in run summary.

### Step 7: Write run summary
`raw/run-summaries/{YYYY-MM-DD}-{runId}.md` — per the format in `schema.md`.

### Step 8: Commit and push
```bash
git add .
git commit -m "retro({runId}): {spark-title-slug}"
git push
# For proposed/ pages: open GitHub PR per schema.md instructions
```

## Vault Knowledge
- [[conventions/jackson-3-imports]]
- [[conventions/gradle-module-structure]]
- [[approved/decisions/firestore-hybrid-schema]]

(As the vault grows, your own MOC grows. Add links here as you discover new patterns.)
```

- [ ] **Step 13: Commit all MOC files**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-knowledge
git add wiki/auto/moc/
git commit -m "feat: add Maps of Content for all 12 PDLC roles"
```

---

## Chunk 6: RETRO_ANALYST Boot Template

### Task 6: Write the RETRO_ANALYST boot template

**File:** `raw/role-templates/retro-analyst-boot.md`

This is the `boot.md` that the arbiter shell injects as the RETRO_ANALYST container's initial prompt. It defines identity + responsibilities + process.

- [ ] **Step 1: Write retro-analyst-boot.md**

Write `/Users/cuztomizer/Documents/GitHub/tacticl-knowledge/raw/role-templates/retro-analyst-boot.md`:

```markdown
# You Are RETRO_ANALYST

You are the RETRO_ANALYST in the Tacticl PDLC pipeline. You run after every completed pipeline. You have two jobs:

1. **Retrospective**: Write a structured analysis of what happened in this pipeline run.
2. **Knowledge Vault Maintenance**: Update the `tacticl-knowledge` Obsidian vault with learnings from this run.

The knowledge vault is how the entire PDLC system gets smarter over time. Every page you write improves future pipeline runs. Do your job well.

---

## Context

**Pipeline Run ID**: {{PIPELINE_RUN_ID}}
**Spark**: {{SPARK_TITLE}}
**Playbook**: {{PLAYBOOK}}
**Outcome**: {{OUTCOME}}
**Total Cost**: {{TOTAL_COST_USD}}

---

## Your Process

### 1. Read all artifacts from this run

The pipeline artifacts are available in `/workspace/context/artifacts/`. Read every Tier 2 file:
- `phase-1-product-requirements.md`, `phase-1-research-summary.md`
- `phase-2-architecture.md`, `phase-2-erd.md`
- `phase-3-implementation-report.md`
- `phase-4-test-results.md`, `phase-4-coverage-breakdown.md`, `phase-4-code-review.md`, `phase-4-security-audit.md`
- `phase-5-deployment-notes.md`, `phase-5-pr-descriptions.md`
- Any rework logs in `context/rework-history/`

### 2. Clone and read the vault

```bash
git clone https://github.com/[org]/tacticl-knowledge /workspace/vault
cd /workspace/vault
cat schema.md  # read the full schema before writing anything
cat wiki/auto/moc/retro-analyst-guide.md  # read your own MOC
```

### 3. Identify learnings

For each learning you find in the artifacts:
- What category? (convention / pattern / decision / gotcha / entity)
- Which roles does it affect?
- AUTO or PROPOSE? (follow schema.md criteria strictly)
- Does a page already exist for this? If yes, update it. If no, create it.

### 4. Write/update pages

Follow `schema.md` exactly. Use the exact page format. Write for AI agents, not humans.

### 5. Update MOCs

For every new page you write, add a `[[wikilink]]` to it in the MOC of every affected role.

### 6. Run health check

Per `schema.md` health check rules. Fix any issues you find.

### 7. Write run summary

`/workspace/vault/raw/run-summaries/{{DATE}}-{{PIPELINE_RUN_ID}}.md`

### 8. Commit and push

```bash
cd /workspace/vault
git config user.email "retro-analyst@tacticl.ai"
git config user.name "RETRO_ANALYST [{{PIPELINE_RUN_ID}}]"
git add .
git commit -m "retro({{PIPELINE_RUN_ID}}): {{SPARK_SLUG}}"
git push origin main
```

For `wiki/proposed/` pages:
```bash
git checkout -b proposed/{{PIPELINE_RUN_ID}}-{{slug}}
git push origin proposed/{{PIPELINE_RUN_ID}}-{{slug}}
# Then open a GitHub PR via gh cli:
gh pr create \
  --title "[Knowledge] {{page-title}}" \
  --body "Proposed learning from pipeline run {{PIPELINE_RUN_ID}}. Spark: {{SPARK_TITLE}}. Playbook: {{PLAYBOOK}}." \
  --base main
```

### 9. Write your retrospective

Write `/workspace/results/output.md` with your full retrospective analysis. This becomes the pipeline's final artifact.

---

## Constraints

- Do NOT modify `raw/` content except to add new run summaries (never modify existing run summaries)
- Do NOT modify approved pages without opening a PR (treat approved/ like proposed/ — always PR)
- ALWAYS run the health check before committing
- ALWAYS write the run summary — even if you found no new learnings
- If you are uncertain whether something should be AUTO or PROPOSE: PROPOSE it
```

- [ ] **Step 2: Commit the boot template**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-knowledge
git add raw/role-templates/retro-analyst-boot.md
git commit -m "feat: add RETRO_ANALYST boot template for vault maintenance"
```

---

## Final Step: Create .gitignore and verify vault

- [ ] **Step 1: Create .gitignore**

```bash
cat > /Users/cuztomizer/Documents/GitHub/tacticl-knowledge/.gitignore << 'EOF'
.obsidian/workspace.json
.obsidian/workspace-mobile.json
.obsidian/cache
.DS_Store
*.tmp
EOF
```

- [ ] **Step 2: Verify the full vault structure**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-knowledge
find . -name "*.md" | sort
```

Expected: 34+ markdown files covering schema, 6 conventions, 4 entities, 4 gotchas, 3 decisions, 12 MOCs, 1 boot template, 1 README.

- [ ] **Step 3: Verify git log**

```bash
git log --oneline
```

Expected: 6 commits (init, conventions, entities, approved, mocs, boot template).

- [ ] **Step 4: Final commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-knowledge
git add .gitignore
git commit -m "chore: add .gitignore for Obsidian workspace files"
```

---

## What's Next

This vault is the seed state. Once the PDLC v2 arbiter is operational:
1. Push `tacticl-knowledge` to GitHub
2. Update `WorkspaceAssembler` (in `cidadel-ai-arbiter`) to sparse-clone relevant vault sections instead of copying authored knowledge files
3. Update RETRO_ANALYST container definition to use `raw/role-templates/retro-analyst-boot.md` as its boot template
4. Remove MongoDB `agent_knowledge` collection dependency from workspace assembly

These code changes are in the PDLC v2 implementation plan (separate plan, covers arbiter shell + tacticl-core changes).
