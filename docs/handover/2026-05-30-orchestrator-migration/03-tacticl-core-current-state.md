# 03 — tacticl-core Current State (the SLIMMING side)

> Companion to `00-session-decisions.md` (architectural decisions, same dir) — this section is the **verified inventory of uncommitted changes** in `/Users/cuztomizer/Documents/GitHub/tacticl-core` so a fresh session can resume the orchestrator → arbiter migration with zero loss. Do not re-derive the decisions; see `00-session-decisions.md`. Other sibling sections present in `docs/handover/2026-05-30-orchestrator-migration/`: `01-migration-plan-summary.md`, `04-prd-sad-canonical-decisions.md`, `05-learning-layer-preservation.md`, `06-ecosystem-context.md`, `07-architecture-prose-and-ascii.md`, `08-memory-and-docs-index.md`, `09-risks-and-open-questions.md`.

**Everything below was verified by reading the actual files / diffs in this session.** Two items remain unverified and are explicitly marked `UNVERIFIED` (the `./gradlew test` result, and the exact location of the runtime PM→PO data-migration runner). A prior draft speculated a standalone top-level `application/` module — that is **wrong**; there is none (see "Modules that STAY").

---

## Repository position (VERIFIED)

- **Branch:** `main`, up to date with `origin/main`.
- **HEAD = `fb20a72d1f38af419b723a48062e2016a6538865`** (`fb20a72`) `ops(deploy): prune dangling images + old build cache after deploy`.
- Last 8 commits (`git log -8 --oneline`):
  ```
  fb20a72 ops(deploy): prune dangling images + old build cache after deploy
  d39d7fc fix(deploy): also tag built image as :latest so docker compose finds it on restart
  214d225 fix(llm-callers): correct generateContent arg order at 3 call sites — system prompt folded into history head
  f893865 docs(architecture): six architect critiques + synthesis for conversation-plane decision
  088259b fix(conversation): temporarily switch CONVERSATION_MODEL to Haiku while Sonnet 429s persist
  a69898e fix(service-telegram): standardize TelegramLinkController to @RequireAuth + @AuthUser (was X-User-Id header)
  a488465 fix(startup): add io.strategiz.social.client.github to scanBasePackages so GitHubClient bean is registered
  9fd32ae merge: Telegram conversational spark → PDLC handoff with agent-driven repo creation
  ```
- **None of the Wave 1+2 work is committed.** It is all in the working tree: exactly **one staged add** (`po.md`), many unstaged modifications, and several untracked directories.

---

## (a) Wave 1+2 NEW modules slated for DELETION — ALL VERIFIED PRESENT

Temporal/voice/persona-orchestrator modules being **removed** (orchestration moves into `cidadel-ai-arbiter`; see `00-session-decisions.md`). All appear under **Untracked files** in `git status` and were confirmed on disk. Untracked = never committed → deletion is `rm -rf` of the dir + removing its wiring; no git-history surgery.

| Module / path (under repo root) | How verified | Status |
|---|---|---|
| `business/business-cloud-orchestrator/` | `ls business/`, `find` for assets | VERIFIED present |
| `business/business-voice/` | `ls business/` | VERIFIED present |
| `client/client-deepgram/` | `ls -d` | VERIFIED present |
| `client/client-elevenlabs/` | `ls -d` | VERIFIED present |
| `data/data-cloud-orchestrator/` | `ls -d` | VERIFIED present |
| `service/service-cloud-orchestrator/` | `ls -d` | VERIFIED present |
| `application-api/src/main/java/io/tacticl/application/temporal/` + sub-pkg `.../temporal/smoke/` + test `application-api/src/test/java/io/tacticl/application/temporal/smoke/` | `find application-api/src` | VERIFIED present |

**Exact Temporal files inside `application-api` to delete** (VERIFIED via `find ... -name '*.java'`):
```
application-api/src/main/java/io/tacticl/application/temporal/TemporalProperties.java
application-api/src/main/java/io/tacticl/application/temporal/TemporalWorkerConfig.java
application-api/src/main/java/io/tacticl/application/temporal/smoke/SmokeWorkflow.java
application-api/src/main/java/io/tacticl/application/temporal/smoke/SmokeWorkflowImpl.java
application-api/src/test/java/io/tacticl/application/temporal/smoke/SmokeWorkflowTest.java
```
**Do NOT delete** the kept Spring wiring that also lives in `application-api`: `application-api/src/main/java/io/strategiz/social/application/**` (TacticlApplication.java, config/{CorsFilter,GlobalMessageSourceConfig,MongoConfig,SecurityConfig}.java, controller/HealthCheckController.java). VERIFIED these are the real entry point — they stay.

### `settings.gradle.kts` — VERIFIED exact lines to remove

`settings.gradle.kts` modified (unstaged). The diff adds exactly this block (lines 52–57); remove it on slimming:
```kotlin
include(":data:data-cloud-orchestrator")
include(":client:client-deepgram")
include(":client:client-elevenlabs")
include(":business:business-cloud-orchestrator")
include(":business:business-voice")
include(":service:service-cloud-orchestrator")
```
Note `:client:client-ai-arbiter` is already included (line 35) and STAYS — the arbiter client is wired in.

### `gradle/libs.versions.toml` — VERIFIED additions to strip

Diff adds (all orchestrator-only; remove on slimming **unless** the arbiter pipeline still needs Temporal/caffeine — confirm against the migration plan):
```toml
# [versions]
temporal-sdk = "1.27.0"
# [libraries]
temporal-sdk = { module = "io.temporal:temporal-sdk", version.ref = "temporal-sdk" }
temporal-spring-boot-starter = { module = "io.temporal:temporal-spring-boot-starter-alpha", version.ref = "temporal-sdk" }
temporal-testing = { module = "io.temporal:temporal-testing", version.ref = "temporal-sdk" }
caffeine = { module = "com.github.ben-manes.caffeine:caffeine", version = "3.1.8" }
```

### `application-api/build.gradle.kts` — VERIFIED additions to strip
```kotlin
implementation(libs.temporal.sdk)        // line added under dependencies
testImplementation(libs.temporal.testing)
```

### `application*.properties` — VERIFIED Temporal keys to remove (all three files modified)
- `application.properties` (base) adds: `tacticl.temporal.host` (default `localhost`), `.port` (7233), `.namespace` (`tacticl-qa`), `.task-queues.cloud-agent-session=cloud-agent-session-tq`, `.task-queues.pipeline=pipeline-tq`, `.task-queues.voice-activity=voice-activity-tq`.
- `application-prod.properties` adds: `tacticl.temporal.host` (default `temporal.hetzner.internal`), `.port` (7233), `.namespace` (`tacticl-prod`).
- `application-qa.properties` adds: `tacticl.temporal.host` (default `temporal.hetzner.internal`), `.port` (7233), `.namespace` (`tacticl-qa`).
- **Note:** the `pdlc.v2.*` arbiter keys (`pdlc.v2.enabled`, `pdlc.v2.arbiter.host`, etc.) already exist in prod/qa **and are NOT part of this diff** — they stay (the arbiter integration is pre-existing and is the migration target).

### Superseded / new design docs (VERIFIED status)
- Modified (Temporal-era specs being superseded): `docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-prd.md`, `docs/superpowers/specs/2026-04-11-tacticl-pdlc-v2-sad.md`.
- Untracked (Temporal Cloud Agent Orchestrator design — superseded by arbiter move): `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-prd.md`, `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md`, `docs/superpowers/plans/2026-05-25-cloud-agent-orchestrator.md`.
- **Untracked — authoritative NEW migration plan:** `docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md`. Drives the SLIMMING.

---

## (b) Wave 1+2 fix-ups that are PRESERVED — ALL DIFFS VERIFIED

Architecture-independent fixes, kept regardless of the orchestrator move. Each diff body below was read this session.

### PdlcRole.PM → PO rename — VERIFIED
`data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PdlcRole.java` (modified; +14/-1). New test `data/data-pipeline/src/test/java/io/tacticl/data/pipeline/entity/PdlcRoleTest.java` (untracked). The change is the single enum-constant swap `PM` → `PO` plus a large javadoc block. The javadoc states: *"The migration runner bulk-updates `pipeline_runs.role` and `pipeline_events.role` Mongo records from `"PM"` to `"PO"` in-place, so no `@JsonAlias` is needed (single-cut deploy)."*
```java
-    PM, RESEARCHER, ARCHITECT, DESIGNER, PLANNER,
+    PO, RESEARCHER, ARCHITECT, DESIGNER, PLANNER,
     IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST,
     TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST
```

### pipeline_runs role migration (PM→PO backfill) — UNVERIFIED location
The PM→PO enum rename is verified, and `PdlcRole.java`'s javadoc *references* a "migration runner" that bulk-updates `pipeline_runs.role` / `pipeline_events.role`, **but no such runtime migration class appears in `git status` and a grep for it was not completed** (the `--include` grep failed under zsh and was not retried). **The resuming session must locate or author the actual backfill runner** (`grep -rn "PM" --include=*.java data/data-pipeline business/business-pipeline service` and search for a Mongo migration/`@ChangeUnit`/startup runner). This is the only PRESERVED item whose *runtime code* is not pinned down.

### PipelineRun field adds — VERIFIED
`data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PipelineRun.java` (modified; +67). Adds: `@CompoundIndexes` (`userId_status_updatedAt`, `status_updatedAt`); `sparkId` made `@Indexed(unique=true)`; new fields **`workflowId`** (`@Indexed(unique=true, sparse=true)`), **`name`** (human-readable pipeline name), **`creatingSessionId`**, **`blockedCheckpointId`**, **`personaVersions`** (`Map<PdlcRole,Integer>`), with getters/setters; `create(...)` inits `personaVersions`; `resumeFromCheckpoint()` clears `blockedCheckpointId`. NOTE: `workflowId` / Temporal references in javadoc — but these are entity fields (data layer), kept; only the *Temporal worker wiring* is deleted. The resuming session should decide whether `workflowId`/`personaVersions` remain meaningful under the arbiter model (likely repurposed, not deleted).

### PipelineCheckpoint.userId + CheckpointStatus integration — VERIFIED
`data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/PipelineCheckpoint.java` (modified; +70). Test `.../PipelineCheckpointTest.java` (modified). Adds: `@CompoundIndexes` (`userId_status_raisedAt`); new fields **`userId`** (`@Indexed`), **`resolvedBy`**; overloaded `create(..., userId, ...)` and `resolve(..., resolvedBy)`; `getStatusEnum()`/`setStatusEnum(CheckpointStatus)` bridging the String `status`; `getRaisedAt()`/`setRaisedAt()` aliases for `createdAt`; `getVersion()`/`setVersion()`. **`create(...)` now writes `CheckpointStatus.OPEN.name()`** instead of the old `"PENDING"` string; `resolve(...)` writes `CheckpointStatus.RESOLVED.name()`.

### CheckpointStatus enum (NEW) — VERIFIED (full content)
`data/data-pipeline/src/main/java/io/tacticl/data/pipeline/entity/CheckpointStatus.java` (untracked) + test `.../CheckpointStatusTest.java` (untracked). Full enum:
```java
public enum CheckpointStatus {
    OPEN,
    RESOLVED,
    CANCELLED,
    /** @deprecated Use OPEN. Kept for backward compat with pre-v2 data. */
    @Deprecated PENDING
}
```
Brand-new (untracked) → PRESERVED, not deletion set.

### Spark.conversationSessionId — VERIFIED
`data/data-sparks/src/main/java/io/tacticl/data/sparks/entity/Spark.java` (modified; +10). Adds `@Indexed private String conversationSessionId;` + getter/setter. Test `.../SparkTest.java` (modified).

### ConversationSession bridge methods — VERIFIED (large change, +262)
`data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/ConversationSession.java` (modified; +262/-~37). This is the biggest preserved diff. Key facts:
- **Imports from the deletion-set module:** `io.tacticl.data.cloudorchestrator.entity.{CostBreakdown, SessionMode, Turn, SessionStatus}`. ⚠️ **These types live in `data/data-cloud-orchestrator/` which is slated for DELETION.** Deleting `data-cloud-orchestrator` will break `ConversationSession` compile. **The resuming session must relocate `CostBreakdown`, `SessionMode`, `Turn`, and the new `SessionStatus` enum into a kept module (e.g. `data-conversation`) before/with the deletion, OR keep that subset of `data-cloud-orchestrator`.** This is the single most important cross-cutting compile risk of the slimming.
- New fields: `workflowId`, new `status` (typed to `cloudorchestrator.entity.SessionStatus`), `mode` (`SessionMode`), `sessionStartedSparkIds` (List), `activePersonaId`, `focusedPipelineId`, `costAccumulator` (`CostBreakdown`), `costCeilingUsd` (default 5.0), `turns` (List<Turn>).
- Legacy fields kept `@Deprecated`: `messages` (List<ConversationMessage>), `sparkId`, `detectedSparkType`, `proposalText`.
- New methods: `appendTurn`, `recordStartedSpark`, `changeStatus`, `changeMode`, `focusOn`, `clearFocus`, `setActivePersona`.
- **Legacy bridge methods** (explicit comment: "DELETE these when ConversationService is removed in the Phase-4 cutover"): `addMessage(...)` (writes to BOTH `messages` and `turns`), `revertToGathering()`, `markActive(sparkId)` (now → `PIPELINE_ACTIVE`), `markProposing(sparkType, proposalSummary)`, `markCompleted()`. These exist so legacy `ConversationService` + Telegram callsites + tests keep compiling during the single-cut migration.

### SessionStatus (legacy enum, deprecated) — VERIFIED
`data/data-conversation/src/main/java/io/tacticl/data/conversation/entity/SessionStatus.java` (modified). The **legacy** enum (values `GATHERING, PROPOSING, ...`) is now annotated `@Deprecated` with javadoc pointing to `io.tacticl.data.cloudorchestrator.entity.SessionStatus` as the replacement. So there are **two SessionStatus enums** right now: the legacy one in `data-conversation` and the new one in `data-cloud-orchestrator` (the latter adds `PIPELINE_ACTIVE` etc.). The migration must pick one home.

### ConversationSessionRepository — VERIFIED
`data/data-conversation/src/main/java/io/tacticl/data/conversation/repository/ConversationSessionRepository.java` (modified). Switches import to `cloudorchestrator.entity.SessionStatus`. Adds `findByUserId`, `findByUserIdAndStatus`, keeps `findByUserIdOrderByUpdatedAtDesc` and `findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc`; marks `findBySparkId` `@Deprecated`. (Also `data-conversation/build.gradle.kts` modified — adds the `data-cloud-orchestrator` dependency that supplies those entity types; this dependency must be retargeted when the module is deleted.)

### PdlcV2Service fix — VERIFIED
`business/business-pipeline/src/main/java/io/tacticl/business/pipeline/service/PdlcV2Service.java` (modified) + test (modified). One-line behavioral fix: the duplicate-decision guard now compares against `CheckpointStatus.OPEN.name()` instead of the string `"PENDING"`:
```java
-        if (!"PENDING".equals(checkpoint.getStatus())) {
+        if (!CheckpointStatus.OPEN.name().equals(checkpoint.getStatus())) {
```

### ConversationEventChannel API migration — VERIFIED
`business/business-pipeline/src/main/java/io/tacticl/business/pipeline/channel/ConversationEventChannel.java` (modified) + test (modified). Switches imports from `data.conversation.entity.{ConversationMessage, SessionStatus}` to `data.cloudorchestrator.entity.{SessionStatus, Turn}`. Behavioral change: `session.addMessage(ConversationMessage.assistant(summary))` → `session.appendTurn(Turn.assistant("product-manager", summary, "text"))`; and `session.markCompleted()` → `session.changeStatus(SessionStatus.COMPLETED)`. ⚠️ Also depends on the `cloudorchestrator` `Turn`/`SessionStatus` types (same relocation risk as ConversationSession).

### ConversationService update — VERIFIED
`business/business-conversation/.../service/ConversationService.java` (modified) + test (modified). Switches `SessionStatus` import to `cloudorchestrator.entity.SessionStatus`; replaces `SessionStatus.ACTIVE` → `SessionStatus.PIPELINE_ACTIVE` (in the `MessageResponse` and in `buildSystemPrompt` guard).

### RoleIdentityLoaderTest update — VERIFIED
`business/business-pipeline/src/test/java/io/tacticl/business/pipeline/service/RoleIdentityLoaderTest.java` (modified; +2/-1). Updated for the pm→po rename. **Significant:** the test was *updated*, not deleted — confirming `RoleIdentityLoader` + the role-identity markdowns are being **KEPT** (contradicting CLAUDE.md's stale Temporal-era "delete role markdowns" note).

### Telegram adapter + RepoCommand + tests — VERIFIED
- `business/business-telegram/.../conversation/TelegramConversationAdapter.java` (modified): import → `cloudorchestrator.entity.SessionStatus`; `RESUMABLE` list now uses `SessionStatus.PIPELINE_ACTIVE` (was `ACTIVE`).
- `business/business-telegram/.../command/RepoCommand.java` (modified): same import switch; `RESUMABLE` uses `PIPELINE_ACTIVE`.
- Tests `.../command/InitCommandTest.java` (+4/-2) and `.../command/RepoCommandTest.java` (+1/-1) updated accordingly.
- Telegram is in the PRESERVED set (stays).

---

## (c) Persona prompt ASSETS to copy verbatim

### Conversational persona prompts (inside the module being DELETED) — VERIFIED
Exact paths (`find`):
```
business/business-cloud-orchestrator/src/main/resources/conversational-personas/product-manager.md
business/business-cloud-orchestrator/src/main/resources/conversational-personas/market-researcher.md
```
(Compiled copies under `.../build/resources/main/conversational-personas/` — ignore; build output.) Exactly **2** files = the 2 CONVERSATIONAL personas (Product Manager + Market Researcher). **ACTION (critical): copy both `.md` files byte-for-byte into `cidadel-ai-arbiter` BEFORE deleting `business-cloud-orchestrator`.** New home is a cross-repo concern — see the arbiter-side handover / `00-session-decisions.md`.

### 12 PDLC role markdowns (in the module that STAYS) — VERIFIED
Path `business/business-pipeline/src/main/resources/role-identities/`. `ls` returned **exactly 12** files:
```
architect.md  designer.md  devops.md  implementer.md  planner.md  po.md
researcher.md  retro_analyst.md  reviewer.md  security_analyst.md
technical_writer.md  tester.md
```
VERIFIED: `po.md` present, `pm.md` absent. git status confirms `pm.md` deleted (unstaged) and `po.md` added (the lone staged change). **Keep these** — `RoleIdentityLoaderTest` was updated not deleted, so they remain in use. Only delete if `docs/superpowers/plans/2026-05-30-orchestrator-migrate-to-arbiter.md` explicitly says so.

---

## `./gradlew test` — green or not?

**UNVERIFIED — could not confirm.** A scoped run was attempted but the sandbox shell does not have `timeout` (`command not found: timeout`), so the wrapped invocation never executed gradle; no pass/fail was observed (the trailing `GRADLE_EXIT=0` was the exit of the failed `timeout` lookup, NOT a gradle result). **Do not interpret it as green.**

The resuming session MUST verify with (no `timeout` wrapper — use `&` + background or just run directly):
```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew test
```
Caveat: the six deletion-set modules are wired into `settings.gradle.kts` (lines 52–57) + `libs.versions.toml` + `application-api/build.gradle.kts`, so a full `./gradlew test` currently compiles/tests them too. **Recommended:** (1) baseline test run now; (2) slim (delete modules + strip wiring + relocate the `cloudorchestrator` entity types `Turn`/`SessionMode`/`CostBreakdown`/`SessionStatus`); (3) re-run to prove the PRESERVED fix-ups still pass.

---

## Modules that STAY (source of truth = `settings.gradle.kts`, VERIFIED full content)

**There is NO standalone top-level `application/` module** — `ls application` → "No such file or directory". The assembler is `application-api` only.

Included modules (from `settings.gradle.kts`) that STAY:
- **Application:** `application-api` (entry point `io.strategiz.social.application.TacticlApplication`).
- **Service / REST surfaces:** `service:service-agent`, `service:service-spark`, `service:service-checkpoint`, `service:service-repo`, `service:service-token`, `service:service-connections`, `service:service-sparks`, `service:service-pipeline`, `service:service-conversation`, `service:service-profile`, `service:service-telegram`.
- **Business / skill executors + domain:** `business:business-agent`, `business:business-connections`, `business:business-sparks`, `business:business-pipeline`, `business:business-conversation`, `business:business-profile`, `business:business-telegram`.
- **Data:** `data:data-connections`, `data:data-sparks`, `data:data-pipeline`, `data:data-conversation`, `data:data-profile`, `data:data-telegram`.
- **Clients:** `client:client-brave-search`, `client:client-github`, `client:client-google`, `client:client-jina`, `client:client-whisper`, `client:client-ai-arbiter` (the arbiter client — the migration target), `client:client-telegram`.

**DELETE (the six, lines 52–57):** `data:data-cloud-orchestrator`, `client:client-deepgram`, `client:client-elevenlabs`, `business:business-cloud-orchestrator`, `business:business-voice`, `service:service-cloud-orchestrator` — plus the `application-api/.../io/tacticl/application/temporal/` files listed in (a).

> **Reconciliation note (important):** `ls business/` shows `business-browser` and `business-social` on disk, but they are **NOT in `settings.gradle.kts`** — so they are not part of the current Gradle build. Likewise CLAUDE.md's module map lists `client-twitter`/`client-linkedin`/`client-instagram`/`client-siliconflow`/`client-gcs`/`data-social`/`data-browser`, none of which are in `settings.gradle.kts`. **CLAUDE.md's module map is partly stale; `settings.gradle.kts` is the source of truth for what compiles.** Trust the settings file.

---

## Resume checklist

1. Re-run `git status` + `git log -8 --oneline`; confirm HEAD `fb20a72`.
2. **Relocate the `data.cloudorchestrator.entity` types that PRESERVED code depends on** (`Turn`, `SessionMode`, `CostBreakdown`, the new `SessionStatus`) out of `data-cloud-orchestrator` into a kept module (likely `data-conversation`) — `ConversationSession`, `ConversationEventChannel`, `ConversationSessionRepository`, `ConversationService`, `TelegramConversationAdapter`, `RepoCommand` all import them. This must happen with/before the deletion or the build breaks.
3. Locate/author the runtime **PM→PO `pipeline_runs`/`pipeline_events` backfill runner** (only the enum rename is in place; the runner is UNVERIFIED).
4. Copy `business/business-cloud-orchestrator/src/main/resources/conversational-personas/{product-manager,market-researcher}.md` verbatim into `cidadel-ai-arbiter` BEFORE deleting the module.
5. Establish `./gradlew test` baseline (run WITHOUT a `timeout` wrapper — not installed).
6. Slim: `rm -rf` the six modules + the `application-api/.../temporal/` files; delete `settings.gradle.kts` lines 52–57; strip Temporal/caffeine from `gradle/libs.versions.toml` + `application-api/build.gradle.kts`; remove `tacticl.temporal.*` keys from all three `application*.properties` (leave `pdlc.v2.*` alone).
7. Re-run `./gradlew test`; PRESERVED fix-ups must still pass.
