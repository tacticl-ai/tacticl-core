# CLAUDE.md — Tacticl

## ⭐ Active architectural overhaul (2026-05-25)

**The orchestration/conversation/pipeline layer is being rebuilt.** Existing `ConversationService` state machine and `PdlcV2Service` direct path are being deleted (they don't reliably complete end-to-end). Replaced by the **Cloud Agent Orchestrator** — Temporal-backed durable workflow with persona/skill registries and a voice plane (Deepgram + ElevenLabs) on a pulsating voice sphere UI.

**Canonical docs (read these before changing anything in conversation/pipeline/agent code):**
- PRD: `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-prd.md`
- SAD: `docs/superpowers/specs/2026-05-25-cloud-agent-orchestrator-sad.md`
- Plan: `docs/superpowers/plans/2026-05-25-cloud-agent-orchestrator.md`

**Sections below that describe the OLD architecture** (PDLC Pipeline Engine, Cloud Agent Flow, Dual Agent Architecture as-described) are **being superseded by the new docs.** They're kept here only because some still-correct bits are mixed in. If anything below contradicts the PRD/SAD, the PRD/SAD wins.

## Ecosystem Context (AUTO-LOAD)

At the start of every session, read `../tacticl-docs/CLAUDE.md` (sibling repo in the VS Code multi-root workspace) for complete cross-repo ecosystem context (API contracts, deploy targets, architecture, conventions). This is the source of truth for all cross-cutting concerns.

### Local sibling repos — TRAVERSE THESE (they ARE part of your codebase)

All of these are checked out side-by-side under `/Users/cuztomizer/Documents/GitHub/` and are wired into `.claude/settings.local.json` → `permissions.additionalDirectories`. Read/Grep/Edit across them directly. **Never tell the user you "can't find" or "can't reach" cidadel — it's right here.**

| Repo | What it is |
|------|-----------|
| `../cidadel-core` | Shared platform (Java/Gradle) — auth, tokens, AI engine framework, published to GitHub Packages. **Part of the codebase, not an external dependency.** |
| `../cidadel-ai-arbiter` | TS Temporal engine — Cloud Agent Orchestrator + PDLC pipeline engine (post-2026-05-30 pivot). Deployed from here. ⚠️ Load-bearing invariant: every OAuth Anthropic request must lead with the Claude Code identity system block (`withClaudeCodeIdentity`) or Anthropic 429s and all OAuth LLM (incl. Strategiz mobile AI Signal) dies — never ship an arbiter build that drops it. See `../cidadel-ai-arbiter/CLAUDE.md`. |
| `../strategiz-core` | Sibling product backend (Java/Maven) — shares the framework + Vault |
| `../tacticl-docs` | Shared ecosystem/architecture/convention docs |

### Infrastructure — NOT Cloud Run (decommissioned long ago)

Everything (backends, frontends, **Vault**) runs in Docker on the **Hetzner platform host** behind Caddy. Cloud Run is dead.
- **Vault**: `http://vault:8200` inside the compose network (`http://10.0.1.10:8200` on-host). Anthropic creds are **OAuth-only** at `secret/shared/anthropic` + `secret/shared/anthropic-codegen` (no metered api-key, per the 2026-06 cost incident).
- **Any `*-vault-*.us-east1.run.app` URL is DEAD** — do not use it, and delete it on sight. The canonical default in every deployment is `http://vault:8200`.

## Ecosystem (MUST READ before cross-cutting changes)

Tacticl has 4 repos in a VS Code multi-root workspace. Changes to API paths, auth, WebSocket messages, or Firestore schema affect multiple repos.

| Repo | Role | Consumes tacticl-core via |
|------|------|--------------------------|
| **tacticl-core** (this repo) | Java backend, REST API, PDLC pipeline | — |
| **tacticl-web** | React web dashboard (Spark Control, Chat) | REST (`src/api/*.ts`) + WebSocket |
| **tacticl-mobile** | React Native mobile app (Chat, Push-to-talk) | REST (`src/api/*.ts`) + WebSocket |
| **tacticl-device** | Electron desktop agent (executes Sparks locally) | WebSocket only |

**Shared platform:** `cidadel-core` (Java, GitHub Packages) — auth, tokens, AI engine framework.

**When changing API contracts, update ALL clients.** REST endpoints use `/v1/` prefix.

## Overview

Personal AI assistant that remotes into all your devices and utilizes them as workers. It can handle social automation, web browsing, content generation, video creation, reminders, and much more — any task you can do on your devices, Tacticl can do for you. Claude (tool_use) routes commands to skill handlers.

This repo is the **Java backend** (Gradle, Spring Boot, Cloud Run). Mobile app lives in `tacticl-mobile` (React Native Expo, separate repo). Shares auth/framework infrastructure with strategiz-core via GitHub Packages.

## Tech Stack

- Java 25, Spring Boot 4.0.3, Gradle 9.4.0 (Kotlin DSL)
- Firestore (own project: `tacticl`, us-east1)
- Cloud Run deployment, Cloud Build CI/CD
- PASETO auth (shared keys with Strategiz for cross-product SSO)
- React Native (Expo) mobile app
- Whisper API (speech-to-text), Claude tool_use (command routing)
- SiliconFlow / Wan 2.2 (AI video generation)
- Google Photos Library API (media source for social posts)

## Shared Libraries (from strategiz-core via GitHub Packages)

```
framework-authorization    — @RequireAuth, @RequireScope, @Authorize, AuthenticatedUser
framework-token-issuance   — PASETO v4.local token creation/validation
framework-exception        — BaseException, StandardErrorResponse, ErrorDetails pattern
framework-logging          — Structured logging, request tracing
framework-secrets          — Vault integration, secret loading
framework-resilience       — Circuit breakers, rate limiting (bucket4j)
framework-api-docs         — Swagger/OpenAPI config
client-base                — BaseHttpClient, RestTemplate patterns
```

**Version**: `1.0-SNAPSHOT` in `gradle/libs.versions.toml`. Requires GitHub Packages auth (GITHUB_ACTOR + GITHUB_TOKEN env vars or gradle.properties).

## Module Structure

Nested multi-module layout matching Cidadel pattern. Each layer has a parent `build.gradle.kts` with shared dependencies.

```
application/                     → Spring Boot entry point (@EnableScheduling)
service/                         → Parent: shared service deps (auth, web, validation, openapi)
  service-agent/                 → Cloud agent controller (POST /v1/agent/command), Console admin
  service-spark/                 → Spark CRUD, activity, tactics, logs (GET /v1/sparks/*)
  service-checkpoint/            → Checkpoint approval endpoints
  service-social/                → Social media REST controllers, DTOs
  service-repo/                  → Repository access endpoints
  service-token/                 → API token management endpoints
business/                        → Parent: shared business deps (exception, logging, jackson)
  business-agent/                → Agent orchestration (CloudOrchestratorService, SparkService, ToolRegistry, skills)
  business-browser/              → Playwright browser automation for agents
  business-social/               → Social logic (compose, schedule, publish, analytics, oauth)
data/                            → Parent: shared data deps (firestore, jackson)
  data-social/                   → Firestore entities (Spark, Tactic, SocialPost, SocialIntegration, DeviceCommand)
  data-browser/                  → Browser session persistence
client/                          → Parent: shared client deps (exception, secrets, client-base, web)
  client-twitter/                → Twitter/X API v2 client
  client-linkedin/               → LinkedIn Marketing API client
  client-instagram/              → Instagram Graph API client
  client-google/                 → Google OAuth + Photos Library API client
  client-github/                 → GitHub API client
  client-siliconflow/            → SiliconFlow API (Wan 2.2 video generation)
  client-brave-search/           → Brave Search API (web search for agents)
  client-jina/                   → Jina Reader API (web page → markdown extraction)
  client-gcs/                    → Google Cloud Storage client
deployment/                      → Cloud Build YAML configs
```

## Build Commands

```bash
./gradlew build                              # Full build
./gradlew build -x test                      # Skip tests
./gradlew :application:bootRun               # Run locally (Vault required)
./gradlew test                               # Run all tests
./gradlew :client:client-twitter:test        # Single module tests
./gradlew :service:service-agent:test        # Agent module tests
./gradlew projects                           # Show nested module tree
```

## Module Dependency Rules

Same layered architecture as strategiz-core:

```
service-*    → business-*, client-*, data-*, framework-*  (NEVER other service-*)
business-*   → other business-*, client-*, data-*, framework-*  (NEVER service-*)
client-*     → framework-* and client-base only
data-*       → framework-* only
```

- **Constructor injection only** (no `@Autowired` on fields)
- **Base classes required**: Controllers extend `BaseController`, services extend `BaseService`, entities extend `BaseEntity`, clients extend `BaseHttpClient`
- **Return `Optional<T>`** for queries, never null
- **Soft delete**: `delete()` sets `isActive=false`

## Key Patterns

### Provider Pattern (Social Media)
Each platform implements `SocialMediaProvider` interface:
- `getPlatformType()`, `validate()`, `publish()`, `generateAuthUrl()`, `authenticate()`, `refreshToken()`
- `SocialMediaProviderFactory` resolves by `PlatformType` enum
- Follow strategiz-core's `client-fmp` pattern for new clients (Config → VaultConfig → @Bean → Client)

### Skill-Based Architecture (Agent)

**Status:** Being replaced (2026-05-25). The legacy in-JVM `ToolRegistry` model lives on for `CloudOrchestratorService` (social/research/video skills) and will be wrapped as a Temporal activity (`StartCloudSkillActivity`) under the new orchestrator. New skills should be modelled per the Cloud Agent Orchestrator SAD §4 (`Skill` Mongo entity + Temporal activity), not added to `ToolRegistry`.

Legacy `ToolRegistry` description (still applies to existing `business-agent` skills until they migrate):
- Skill = Claude tool definition + handler lambda
- `ToolRegistry.register(toolName, toolDefinition, handler)`
- Handler receives `JsonNode` input, returns `String` result
- Adding a legacy capability = one registry entry + one handler class

New model (canonical going forward):
- `Skill` Mongo entity: id, name, description, inputSchema, activityName
- Each skill backed by a Temporal activity bean
- `Persona.skillIds` allowlists which skills a persona may invoke (Anthropic tool-use)
- `PersonaRegistry.toolsFor(personaId)` resolves persona's skills → Anthropic tool defs at invocation

### Post State Machine
`DRAFT → QUEUED → PUBLISHING → PUBLISHED | FAILED | CANCELLED`
- `PostPublisherJob` (@Scheduled) polls for QUEUED posts due for publishing
- `@Retryable(maxAttempts=3, backoff=@Backoff(delay=2000, multiplier=2))`
- FCM push notification on publish/failure

### Action Confirmation Tiers (Agent Security)
```
Tier 0 (Auto)     — Read-only: search, browse, check schedule, Google Photos
Tier 1 (Confirm)  — Mutations: post, schedule, edit, delete
Tier 2 (2FA)      — Financial: purchases, subscriptions, spending > $X
```
- Domain allowlist/blocklist controls which sites the agent can access
- Spending limit ($0 default, user must explicitly enable)

## Dual Agent Architecture

**Status note:** "Cloud Agent" now has two distinct things stacked on top of each other after the 2026-05-25 overhaul:
- **Cloud Agent Orchestrator** (NEW, Temporal-backed) — the conversation brain. Personas, voice plane, persona routing. PRD/SAD canonical.
- **`CloudOrchestratorService`** (existing) — the *skill executor* for social/research/video/browser. Now wrapped as a `StartCloudSkillActivity` invoked by the orchestrator. Still owns the agent loop for non-PDLC sparks.

A **Device Agent Orchestrator** is anticipated (PRD §1.5.2) as a future sibling that runs *on* the user's device. Out of scope today.

**Cloud Agent Orchestrator** (this repo — tacticl-core, new):
- `CloudAgentSessionWorkflow` (Temporal) — one per conversation, durable
- `PersonaRouter` (pure function) — picks persona per turn from hard rules + intent regex
- `PersonaRegistry` + `SkillRegistry` (Mongo + Caffeine cache)
- Voice plane: Deepgram streaming STT + ElevenLabs streaming TTS
- Voice sphere UI on tacticl-web at `/chat`

**CloudOrchestratorService (skill executor)** (this repo — tacticl-core, existing):
- LlmRouter + 20+ AgentSkills for social/research/video/browser sparks
- Multi-LLM via Arbiter (Anthropic, OpenAI, Grok)
- Playwright browser, Brave Search, Jina Reader
- Now wrapped as a Temporal activity called by the orchestrator when a persona invokes `start_cloud_skill`

**Device Agent** (tacticl-mobile / tacticl-device daemon):
- WebSocket-based spark dispatch + tactic decomposition
- 9 command types (TERMINAL_CMD, OPEN_URL, CLICK_ELEMENT, etc.)
- Checkpoint flow, credential requests, progress reporting
- Device routing intelligence (battery, charging, capabilities)
- Invoked from the orchestrator via `dispatch_to_device` skill → `DispatchToDeviceActivity`

**Claude Code Engine** (NEW — desktop devices only):
- Claude Code CLI subprocess as additional execution engine on desktop
- Built-in: File/Bash/Web/MCP/Subagents — complements existing device capabilities
- Default engine on desktop (macOS, Windows, Linux), configurable per device
- Desktop detection: `DeviceType.priority == 0` → desktop, `1` → mobile
- Config: `DeviceSettings.executionEngine` = CLAUDE_CODE | LEGACY | AUTO
- See `docs/architecture/device-agent-architecture.md` for full design

**Architecture docs**: `docs/architecture/cloud-orchestrator-architecture.md`, `docs/architecture/device-agent-architecture.md`

## PDLC Pipeline Engine

**Status (2026-05-25):** orchestration sections being replaced by Temporal `PipelineWorkflow` (child workflow of the Cloud Agent Orchestrator). Arbiter execution plane (ephemeral containers, workspace assembly, Claude Code CLI) is unchanged. `PdlcRole.PM` is being renamed to `PdlcRole.PO` (Product Owner — see Personas section below).

**12 Roles** (`PdlcRole` enum, post-rename): **PO** (was PM), RESEARCHER, ARCHITECT, DESIGNER, PLANNER, IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST, TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST

**8 Playbooks** (moved from `PlaybookSpecResolver` hardcode → `playbooks` Mongo collection): FULL_PDLC, BUG_FIX, SMALL_FEATURE, REFACTOR, INFRA_CHANGE, DOCS_ONLY, UI_CHANGE, SECURITY_PATCH

**Active orchestration:**
- `PipelineWorkflow` (Temporal child workflow) — replaces `PdlcPipelineOrchestrator` + `PipelineStateManager` + `ReworkTracker` + `PipelineWatchdog` + `PipelineRecoveryJob`
- Activities: `SubmitToArbiterActivity`, `PersistPipelineEventActivity`, `FanOutPipelineEventActivity`, `InvokeArbiterRoleActivity`
- State is the workflow history (durable). Mongo `pipeline_runs` / `pipeline_events` / `pipeline_checkpoints` are write-through projections for UI/queries.

**Personas + Skills registries (new):**
- `Persona` Mongo entity — job role, system prompt, defaultModel, skillIds, voicePreset
- `Skill` Mongo entity — id, name, description, inputSchema, activityName
- 14 total personas: 2 CONVERSATIONAL (Product Manager, Market Researcher) + 12 PDLC
- ~15 skills shared across personas
- `RoleIdentityLoader` + `business-pipeline/src/main/resources/role-identities/*.md` are deleted (content lives in Mongo)
- `PlaybookSpecResolver` hardcoded map deleted

**MongoDB Collections (active):** `conversation_sessions/`, `personas/`, `skills/`, `playbooks/`, `pipeline_runs/`, `pipeline_events/`, `pipeline_checkpoints/`, `sparks/`

**Key REST Endpoints**:
- `GET /v1/sparks/{sparkId}/pipeline` — pipeline run status
- `GET /v1/sparks/{sparkId}/pipeline/events` — event timeline (paginated)
- `GET /v1/sparks/{sparkId}/pipeline/artifacts/{role}` — role artifact
- `POST /v1/sparks/{sparkId}/pipeline/checkpoint/{checkpointId}` — resolve checkpoint
- `GET /v1/playbooks` — list available playbooks
- `GET/PUT/DELETE /v1/console/ai-engine-routing/roles/{role}` — role-level LLM override
- `GET /v1/console/ai-engine-routing/steps` — list SDLC step engine configs

**Deployment note**: Claude Code CLI must be in the Cloud Run image (required by IMPLEMENTER, TESTER, DEVOPS roles). GitHub repo access must be granted via `manage_repo` skill.

**Full architecture doc**: `docs/architecture/pdlc-pipeline-architecture.md`

## Spark Lifecycle

**Status (2026-05-25):** the "every chat command is a Spark" model is being replaced. Under the new orchestrator, a `ConversationSession` has **0..N Sparks** — sparks are created only when actual execution work begins (pipeline run, cloud skill, device dispatch). Conversation-only turns (clarification, market research, status questions) don't create sparks. See PRD §5.7.1.

```
User opens chat → ConversationSession created → CloudAgentSessionWorkflow starts
                                              → templated greeting plays

User: "let's build a /health endpoint"
  → onUserText signal → Product Manager (clarifier mode)
  → no spark yet

User: "yes, go ahead"
  → onUserText signal → Product Manager → propose_implementation → start_pipeline skill
  → StartPipelineWorkflowActivity → SparkService.createSpark(type=CODE, conversationSessionId=...)
  → PipelineWorkflow child workflow starts → Arbiter → containers

User (during pipeline): "how's it going?"
  → Product Manager → summarize_pipeline_progress skill
  → no new spark

User (after pipeline): "great, now add monitoring too"
  → second spark created
```

### Spark State Machine
```
PENDING → ROUTING → QUEUED (no device) | EXECUTING (device found)
PENDING → EXECUTING (cloud fallback, no device available)
PENDING → SCHEDULED (if cron schedule set)
EXECUTING → CHECKPOINT → EXECUTING (after user decision)
EXECUTING → COMPLETED | FAILED
Any → CANCELLED
```

### Telegram entry path (conversational)

**Status (2026-05-25):** Telegram path migrates to the new orchestrator. `TelegramConversationAdapter` becomes a thin signaler — it translates inbound Telegram events into workflow signals. The marker protocol (`<<<CREATE_REPO>>>`, `<<<PROPOSE>>>`, `<<<START>>>`) is replaced by persona tool calls (`propose_implementation`, `start_pipeline`). No application-level changes required for Telegram clients.

Legacy description (pre-overhaul, kept for reference):

Telegram inbound (plain-text bot mention, `/spark`, voice transcript) does **not** create a Spark immediately. It flows through `TelegramConversationAdapter` → `ConversationService` (gather → propose → align state machine), and only when the agent emits the `<<<START>>>` marker does the conversation hand off to `SparkService.create` + `PdlcRouter.route(...)` for the pipeline. Pipeline events fan out to `TelegramEventChannel` (live Telegram render) and `ConversationEventChannel` (durable session history). The HTTP `POST /v1/agent/command` path keeps the legacy direct-spark behaviour.

For CODE/DEVOPS work the conversation needs a repo URL before alignment. Two paths:
- **Agent-driven**: the LLM proposes a name/owner/visibility, gets user confirmation in chat, and emits `<<<CREATE_REPO:{json}>>>`. `ConversationService` invokes `GitHubClient.createRepo(...)` (authorized by the Tacticl PAT in `secret/tacticl/github` → `app-token`) and persists the resulting URL on the session.
- **User-driven**: `/repo <github-url>` slash command sets `session.repoUrl` directly, skipping creation.

Three markers in total: `<<<CREATE_REPO:{...}>>>` (optional, code/devops only), `<<<PROPOSE>>>` (alignment summary), `<<<START>>>` (handoff to PDLC). One marker per LLM turn. See `docs/runbooks/telegram-bot.md` for operator details and `deployment/runbooks/github-app-token.md` for the PAT setup.

### Spark → Tactic Relationship
- **Spark**: User's raw input request (created from chat)
- **Tactic**: Device-side decomposition of a spark into executable sub-tasks (created on-device only, not for cloud execution)
- One spark can produce multiple tactics when a device breaks down the work

### Key Services
- `SparkService` — Full lifecycle: creation, routing, progress tracking, checkpoints, completion
- `SparkContext` — ThreadLocal holding current sparkId during execution
- `SparkClassifierService` — Auto-classifies spark type via Claude Haiku
- `SparkDispatchService` — WebSocket dispatch to devices
- `CloudOrchestratorService` — Cloud execution fallback (accepts sparkId, manages LLM agent loop)

## Cloud Agent Flow (Cloud Execution)

**Status (2026-05-25):** rewritten. The legacy "POST /v1/agent/command → CloudOrchestratorService.execute" flow is being replaced by the Cloud Agent Orchestrator. See PRD/SAD.

**New canonical flow (web sphere)**:
```
Page mount → WS connect to /ws/cloud-agent/{sessionId}
          → CloudAgentSessionWorkflow starts (Temporal)
          → templated greeting plays (pre-cached audio, <500ms)

User speaks → mic AudioWorklet → 16kHz PCM chunks → WS binary frames
           → DeepgramStreamBridge → Deepgram WS → final transcript
           → onUserTranscript signal to workflow

Workflow:
  → PersonaRouter.route() — pure function, no LLM
  → InvokePersonaActivity (Anthropic streaming) for the chosen persona
  → text_delta blocks → ElevenLabsStreamBridge → audio → client (sphere speaks)
  → tool_use blocks → workflow executes skill activities (start_pipeline / web_search / etc.)
  → continuation text_delta after tool results → speaks
  → message_stop → idle
```

**Legacy flow (still active for mobile push-to-talk and Telegram, will migrate):**
```
Push-to-talk → expo-av → Whisper API (~500ms) → text
    → POST /v1/agent/command { text, sessionId }
    → AgentController → AgentCommandService → workflow signal (under new orchestrator)
    or → CloudOrchestratorService.execute(sparkId, ...) (legacy in-JVM, being retired)
```

**Model strategy**: Haiku 4.5 for routing/simple queries (will be redirected to PersonaRouter function), Sonnet 4.6 for substantive personas (Product Manager, Market Researcher) and PDLC roles.

## Web Search & Browsing

- **Search**: Brave Search API (`client-brave-search`) — independent index, $3/1K queries, 2K free/month
- **Browse**: Jina Reader API (`client-jina`) — `GET r.jina.ai/{url}` returns clean markdown, 10M free tokens
- Both use `Optional<Client>` injection in skills — graceful degradation if disabled
- Feature flags: `tacticl.brave-search.enabled`, `tacticl.jina.enabled`
- Vault secrets: `brave-search.api-key`, `jina.api-key`

## Google Photos (Media Source)

- **API**: Google Photos Library API v1 (`client-google` module, shared OAuth config with YouTube/Gmail)
- **Scope**: `photoslibrary.readonly` (read-only media source, no uploads)
- **Feature flag**: `tacticl.google.enabled` (shared with YouTube)
- **Vault secrets**: `google.client-id`, `google.client-secret` (shared Google OAuth credentials)
- **Agent skill**: `google_photos` (Tier 0) — search by date/type, list albums, get album photos, get photo by ID
- **PlatformType**: `GOOGLE_PHOTOS` with `ConnectionCategory.MEDIA_SOURCE` (shown separately from social accounts in app)
- **OAuth**: Same flow as other platforms (`SocialOAuthController`), reuses `exchangeGoogleToken()` from YouTube
- **Use case**: Agent searches user's photos → presents matches → user confirms → media URL attached to social post

### Future: Google WebMCP (Chrome 146+)

Google's Web Model Context Protocol (`navigator.modelContext`) lets websites expose structured tools directly to AI agents — no scraping needed. Currently in Chrome 146 Canary behind a flag, expected stable mid-to-late 2026.

**How it complements Brave/Jina**: WebMCP handles *interaction* (fill forms, click buttons, complete workflows via structured JSON schemas), while Brave/Jina handle *reading/searching*. When WebMCP reaches stable Chrome and websites adopt it, integrate as a third browsing layer — especially powerful for Tier 1 actions (posting to platforms, form submission).

**Integration approach**: Would require a headless Chrome instance (or mobile browser bridge) since WebMCP is a client-side browser API, not a server-side REST call. Could run as a sidecar service or leverage Chrome DevTools Protocol.

## AI Video Generation

- **Provider**: SiliconFlow API → Wan 2.2 model
- **Cost**: ~$0.21/video (480p, 5sec)
- **Quota by tier**: Creator 20/mo, Business 50/mo, Agency 100/mo
- **Flow**: User prompt → Sonnet enhances prompt → SiliconFlow generates → Cloud Storage → attach to post

## Deploy — $0 self-hosted GitHub Actions (CURRENT)

**The old `gcloud builds submit` / Cloud Build / Cloud Run path is DEAD** (Cloud Run decommissioned), and
`scripts/deploy.sh` (ssh-to-host) is also DEAD (SSH to platform-apps is proxy-blocked). Every deploy now
runs **through GitHub Actions on a self-hosted runner ON the platform-apps host** — it dials *out* to
GitHub, so no inbound SSH, and it costs **$0** (never use paid GitHub-hosted `ubuntu-latest` runners).

```bash
gh workflow run deploy-tacticl-api.yml -f environment=prod   # or: Actions tab → deploy-tacticl-api → Run workflow
gh run watch
```
- `environment=none` = build-only validation · `prod` / `qa` / `both` = build + deploy. Service: `tacticl-api`.
- Workflow: `.github/workflows/deploy-tacticl-api.yml` (`runs-on: [self-hosted, platform-apps]`).
- Full platform deploy context (all brands): `../cidadel-core/docs/runbooks/platform-github-actions-deploys.md`.

## Firestore Collections

Key collections (see PRD-005 for full schemas):

```
sparks/                    — Every chat command (top-level entity, replaces asks)
tactics/                   — Device-side decomposition of sparks into sub-tasks
execution_logs/            — Spark execution logs (tool calls, outputs, tokens)
checkpoints/               — Spark checkpoints requiring user approval
social_posts/              — Posts with state machine
social_integrations/       — Connected platform accounts (OAuth tokens)
generated_videos/          — AI video generation tracking
device_commands/            — Commands dispatched to devices (references sparkId)
agent_audit_log/           — All agent commands logged
users/{id}/agent_memory/   — Persistent memory (subcollection)
action_confirmations/      — Pending action confirmations
agent_reminders/           — User reminders
content_templates/         — Reusable post templates
scheduled_batches/         — Batch scheduling groups
```

**Removed collections** (unified into sparks): `asks/`, `agent_tasks/`, `agent_instances/`

## Troubleshooting

- **Build failures**: Ensure Java 25, check GitHub Packages auth (`GITHUB_ACTOR`/`GITHUB_TOKEN`)
- **Shared lib version mismatch**: Run `mvn install` in strategiz-core first, then `./gradlew build --refresh-dependencies`
- **Vault issues**: Ensure `vault server -dev` running, `VAULT_TOKEN` exported
- **OAuth token errors**: Check token expiry in `SocialIntegration`, verify refresh flow
- **Agent tool_use errors**: Check `stop_reason` in Claude response — `tool_use` means execute tools, `end_turn` means done
- **Video gen failures**: Check SiliconFlow API key in Vault, verify quota not exceeded
