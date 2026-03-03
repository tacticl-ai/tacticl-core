# CLAUDE.md — Tacticl

## Overview

Personal AI assistant that remotes into all your devices and utilizes them as workers. It can handle social automation, web browsing, content generation, video creation, reminders, and much more — any task you can do on your devices, Tacticl can do for you. Claude (tool_use) routes commands to skill handlers.

This repo is the **Java backend** (Gradle, Spring Boot, Cloud Run). Mobile app lives in `tacticl-mobile` (React Native Expo, separate repo). Shares auth/framework infrastructure with strategiz-core via GitHub Packages.

## Tech Stack

- Java 21, Spring Boot 3.5.7, Gradle 8.12 (Kotlin DSL)
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
  service-agent/                 → Voice agent controller (POST /api/agent/command)
  service-spark/                 → Spark CRUD, activity, tactics, logs (GET /api/sparks/*)
  service-checkpoint/            → Checkpoint approval endpoints
  service-social/                → Social media REST controllers, DTOs
  service-repo/                  → Repository access endpoints
  service-token/                 → API token management endpoints
business/                        → Parent: shared business deps (exception, logging, jackson)
  business-agent/                → Agent orchestration (VoiceAgentService, SparkService, ToolRegistry, skills)
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
Each capability is a "skill" registered in `ToolRegistry`:
- Skill = Claude tool definition + handler lambda
- `ToolRegistry.register(toolName, toolDefinition, handler)`
- Handler receives `JsonNode` input, returns `String` result
- Adding a new capability = one registry entry + one handler class

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

## Spark Lifecycle

Every chat command is a **Spark** — the single top-level entity for all user requests. There is no manual spark creation; sparks are created exclusively via the chat/voice agent flow.

```
Chat message → POST /api/agent/command { text, sessionId }
    → SparkService.createSpark() [ALWAYS — every command is a spark]
    → SparkClassifierService auto-classifies type (code, social, research, devops, creative, data)
    → Route decision:
        a) Device online → SparkDispatchService → device decomposes into Tactics
        b) No device    → VoiceAgentService cloud execution (no tactics)
    → Spark tracked through completion (PENDING → EXECUTING → COMPLETED/FAILED)
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

### Spark → Tactic Relationship
- **Spark**: User's raw input request (created from chat)
- **Tactic**: Device-side decomposition of a spark into executable sub-tasks (created on-device only, not for cloud execution)
- One spark can produce multiple tactics when a device breaks down the work

### Key Services
- `SparkService` — Full lifecycle: creation, routing, progress tracking, checkpoints, completion
- `SparkContext` — ThreadLocal holding current sparkId during execution
- `SparkClassifierService` — Auto-classifies spark type via Claude Haiku
- `SparkDispatchService` — WebSocket dispatch to devices
- `VoiceAgentService` — Cloud execution fallback (accepts sparkId, manages LLM agent loop)

## Voice Agent Flow (Cloud Execution)

```
Push-to-talk → expo-av → Whisper API (~500ms) → text
    → POST /api/agent/command { text, sessionId }
    → AgentController creates Spark, then:
        → VoiceAgentService.execute(sparkId, ...):
            1. SparkService.markRunning(sparkId)
            2. Build system prompt (personality + user context + memory)
            3. Get tools filtered by user's scopes/tier
            4. Claude API with tool_use
            5. Execute tool calls via ToolRegistry
            6. Send tool_result back to Claude
            7. SparkService.markCloudCompleted(sparkId, tokens, model)
            8. Return final text response
    → Mobile app renders response + actions
```

**Two-model strategy**: Haiku 4.5 for routing/simple queries, Sonnet 4.5 for content generation and complex tasks.

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

## Deploy

```bash
# QA
gcloud builds submit --config deployment/cloudbuild/cloudbuild-qa.yaml .

# Production
gcloud builds submit --config deployment/cloudbuild/cloudbuild-prod.yaml .
```

- GCP project: `tacticl`, Artifact Registry: `tacticl-core`
- QA: `tacticl-core-qa` (2Gi), prod: `tacticl-core` (4Gi)
- Both on Cloud Run, us-east1, public access
- Spring profiles: `qa` / `prod`

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

- **Build failures**: Ensure Java 21, check GitHub Packages auth (`GITHUB_ACTOR`/`GITHUB_TOKEN`)
- **Shared lib version mismatch**: Run `mvn install` in strategiz-core first, then `./gradlew build --refresh-dependencies`
- **Vault issues**: Ensure `vault server -dev` running, `VAULT_TOKEN` exported
- **OAuth token errors**: Check token expiry in `SocialIntegration`, verify refresh flow
- **Agent tool_use errors**: Check `stop_reason` in Claude response — `tool_use` means execute tools, `end_turn` means done
- **Video gen failures**: Check SiliconFlow API key in Vault, verify quota not exceeded
