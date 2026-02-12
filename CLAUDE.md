# CLAUDE.md — Tacticl

## Overview

Voice-first personal AI assistant platform. Users speak commands to post to social media, browse websites, generate AI videos, set reminders, and more. Social media management is the flagship skill, but the agent handles any task. Claude (tool_use) routes commands to skill handlers.

This repo is the **Java backend** (Gradle, Spring Boot, Cloud Run). Mobile app lives in `tacticl-mobile` (React Native Expo, separate repo). Shares auth/framework infrastructure with strategiz-core via GitHub Packages.

## Tech Stack

- Java 21, Spring Boot 3.5.7, Gradle 8.12 (Kotlin DSL)
- Firestore (own project: `tacticl`, us-east1)
- Cloud Run deployment, Cloud Build CI/CD
- PASETO auth (shared keys with Strategiz for cross-product SSO)
- React Native (Expo) mobile app
- Whisper API (speech-to-text), Claude tool_use (command routing)
- SiliconFlow / Wan 2.2 (AI video generation)

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

```
application/          → Spring Boot entry point (@EnableScheduling)
service-social/       → Social media REST controllers, DTOs
service-agent/        → Voice agent controller (POST /api/agent/command)
business-social/      → Social logic (compose, schedule, publish, analytics, oauth)
business-agent/       → Agent orchestration (VoiceAgentService, ToolRegistry, skills)
data-social/          → Firestore entities (SocialPost, SocialIntegration, PlatformType)
client-twitter/       → Twitter/X API v2 client
client-linkedin/      → LinkedIn Marketing API client
client-instagram/     → Instagram Graph API client
client-siliconflow/   → SiliconFlow API (Wan 2.2 video generation)
deployment/           → Cloud Build YAML configs
```

**Currently scaffolded**: application, service-social, business-social, data-social, client-twitter, client-linkedin, client-instagram. The rest are planned.

## Build Commands

```bash
./gradlew build                        # Full build
./gradlew build -x test                # Skip tests
./gradlew :application:bootRun         # Run locally (Vault required)
./gradlew test                         # Run all tests
./gradlew :client-twitter:test         # Single module tests
./gradlew :service-agent:test          # Agent module tests
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
Tier 0 (Auto)     — Read-only: search, browse, check schedule
Tier 1 (Confirm)  — Mutations: post, schedule, edit, delete
Tier 2 (2FA)      — Financial: purchases, subscriptions, spending > $X
```
- Domain allowlist/blocklist controls which sites the agent can access
- Spending limit ($0 default, user must explicitly enable)

## Voice Agent Flow

```
Push-to-talk → expo-av → Whisper API (~500ms) → text
    → POST /api/agent/command { text, sessionId }
    → VoiceAgentService:
        1. Build system prompt (personality + user context + memory)
        2. Get tools filtered by user's scopes/tier
        3. Claude API with tool_use
        4. Execute tool calls via ToolRegistry
        5. Send tool_result back to Claude
        6. Return final text response
    → Mobile app renders response + actions
```

**Two-model strategy**: Haiku 4.5 for routing/simple queries, Sonnet 4.5 for content generation and complex tasks.

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
social_posts/              — Posts with state machine
social_integrations/       — Connected platform accounts (OAuth tokens)
generated_videos/          — AI video generation tracking
browser_sessions/          — General browser automation sessions
agent_audit_log/           — All agent commands logged
users/{id}/agent_memory/   — Persistent memory (subcollection)
action_confirmations/      — Pending action confirmations
agent_reminders/           — User reminders
content_templates/         — Reusable post templates
scheduled_batches/         — Batch scheduling groups
```

## Troubleshooting

- **Build failures**: Ensure Java 21, check GitHub Packages auth (`GITHUB_ACTOR`/`GITHUB_TOKEN`)
- **Shared lib version mismatch**: Run `mvn install` in strategiz-core first, then `./gradlew build --refresh-dependencies`
- **Vault issues**: Ensure `vault server -dev` running, `VAULT_TOKEN` exported
- **OAuth token errors**: Check token expiry in `SocialIntegration`, verify refresh flow
- **Agent tool_use errors**: Check `stop_reason` in Claude response — `tool_use` means execute tools, `end_turn` means done
- **Video gen failures**: Check SiliconFlow API key in Vault, verify quota not exceeded
