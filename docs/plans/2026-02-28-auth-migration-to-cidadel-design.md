# Auth & Infrastructure Migration to Cidadel-Core — Design Document

**Date**: 2026-02-28
**Status**: Approved, pending implementation
**Scope**: Migrate all reusable auth modules AND LLM clients from strategiz-core into cidadel-core

## Problem

Tacticl-core pulls auth modules (`service-auth`, `business-token-auth`) and LLM clients (`client-anthropic-direct`, `client-openai-direct`, `client-grok-direct`) from strategiz-core via Maven install in Cloud Build. Framework modules have already moved to cidadel-core, but auth and LLM remain in strategiz-core. This creates:

- An unnecessary build dependency on strategiz-core for shared infrastructure
- Split ownership across two repos
- Friction when evolving auth or LLM infrastructure independently
- Cloud Build complexity (clone strategiz-core just for these modules)

## Decision

Move all reusable auth code AND LLM clients into cidadel-core, making it the single source for all shared platform infrastructure. Each product (tacticl, strategiz) consumes cidadel-core as a dependency. Auth data continues to write to each product's own Firestore (Option 2 architecture).

## Current Dependency Map

### What tacticl-core pulls from strategiz-core today

**Explicit Gradle dependencies (6):**

| Dependency | Provides | Used By |
|-----------|----------|---------|
| `strategiz-client-anthropic-direct` | Claude API client | business-agent, application |
| `strategiz-client-openai-direct` | OpenAI API + Whisper | business-agent, application |
| `strategiz-client-grok-direct` | Grok API client | business-agent, application |
| `strategiz-service-auth` | Auth REST endpoints `/v1/auth/*` | application (component scan) |
| `strategiz-service-framework-base` | BaseService, WebConfig, FirebaseConfig | application |
| `strategiz-business-token-auth` | Token validation, session management | application (component scan) |

**Transitive via component scan (~15 packages):**
- Auth clients: `client-sendgrid`, `client-recaptcha`, `client-webpush`, `client-google`, `client-facebook`, `client-firebasesms`
- Data: `data-auth`, `data-user`, `data-device`, `data-session`, `data-preferences`, `data-featureflags`
- Business: `business-risk`
- Config: `client-vault`

### What cidadel-core already provides

All 11 framework modules (authorization, token-issuance, exception, logging, secrets, resilience, api-docs, llm-router, client-framework-base, service-framework-base, data-framework-base) plus implemented data layers (data-auth 37 files, data-user 12 files, data-session 4 files).

## What Moves

### Auth modules (strategiz → cidadel)

| Module | Source | Approx Files | New Package |
|--------|--------|-------------|-------------|
| `service-auth` | strategiz `service/service-auth` | ~127 | `io.cidadel.identity.service.auth` |
| `business-auth` | strategiz `business/business-token-auth` | ~11 | `io.cidadel.identity.business.auth` |
| `data-device` | strategiz `data/data-device` | ~8 | `io.cidadel.identity.data.device` |

### LLM clients (strategiz → cidadel)

| Module | Files | New Package | Notes |
|--------|-------|-------------|-------|
| `client-anthropic-direct` | 4 | `io.cidadel.client.anthropic` | Claude API, streaming, tool_use, OAuth token management |
| `client-openai-direct` | 3 | `io.cidadel.client.openai` | GPT-4o, Whisper, tool_use via OpenAiToolHelper |
| `client-grok-direct` | 3 | `io.cidadel.client.grok` | xAI Grok, OpenAI-compatible format |

All three implement the `LlmProvider` interface from `client-framework-base`. Zero product-specific code. Vault paths are config-driven (not hardcoded).

### Auth clients (strategiz → cidadel)

| Client | Purpose |
|--------|---------|
| `client-google` | Google OAuth sign-in |
| `client-facebook` | Facebook OAuth sign-in |
| `client-sms` | Firebase SMS / Twilio OTP |
| `client-sendgrid` | Email OTP delivery |
| `client-recaptcha` | Fraud detection |
| `client-webpush` | Push authentication |

### data-preferences split

- **Moves to cidadel `data-auth`**: `SecurityPreferences` (MFA/ACR enforcement), `ServiceAccountEntity` (machine-to-machine auth), `ServiceAccountRepository`
- **Stays in strategiz**: `PlatformSubscription` (Stripe/STRAT billing), `UserPreferenceEntity` (theme/notifications), `TokenUsageRecord`, tier configs

### Already done in cidadel (no changes)

- `data-auth` (37 files) — entities, repos, domain models
- `data-user` (12 files) — UserEntity, UserProfile, email reservation, SsoRelayToken
- `data-session` (4 files) — SessionEntity, SessionRepository
- All 11 framework modules

## Package Naming

- Auth-specific modules: `io.cidadel.identity.*` packages
- LLM clients: `io.cidadel.client.*` packages
- Framework modules: keep `io.strategiz.framework.*` (already decided, minimize blast radius)

## Publishing

Update `publishedModules` in cidadel-core `build.gradle.kts`:

```kotlin
val publishedModules = setOf(
    // Existing framework (11)
    "framework-exception", "framework-secrets", "framework-logging",
    "framework-resilience", "framework-api-docs", "framework-authorization",
    "framework-token-issuance", "framework-llm-router",
    "client-framework-base", "service-framework-base", "data-framework-base",
    // Auth data + business + service (6)
    "service-auth", "business-auth",
    "data-auth", "data-user", "data-session", "data-device",
    // Auth clients (6)
    "client-google", "client-facebook", "client-sms",
    "client-sendgrid", "client-recaptcha", "client-webpush",
    // LLM clients (3)
    "client-anthropic-direct", "client-openai-direct", "client-grok-direct"
)
```

Total: ~26 published modules (up from 11).

## Consumer Changes

### tacticl-core

1. **libs.versions.toml**: Replace all 6 strategiz deps with cidadel equivalents
2. **application/build.gradle.kts**: Switch to cidadel dependencies
3. **TacticlApplication.java**: Update component scan packages:
   - `io.strategiz.service.auth` → `io.cidadel.identity.service.auth`
   - `io.strategiz.business.tokenauth` → `io.cidadel.identity.business.auth`
   - `io.strategiz.data.auth` → `io.cidadel.identity.data.auth`
   - `io.strategiz.data.user` → `io.cidadel.identity.data.user`
   - `io.strategiz.data.session` → `io.cidadel.identity.data.session`
   - `io.strategiz.data.device` → `io.cidadel.identity.data.device`
   - `io.strategiz.client.anthropic` → `io.cidadel.client.anthropic`
   - `io.strategiz.client.openai` → `io.cidadel.client.openai`
   - `io.strategiz.client.grok` → `io.cidadel.client.grok`
   - `io.strategiz.client.sendgrid` → `io.cidadel.client.sendgrid`
   - `io.strategiz.client.recaptcha` → `io.cidadel.client.recaptcha`
   - `io.strategiz.client.google` → `io.cidadel.client.google`
   - `io.strategiz.client.facebook` → `io.cidadel.client.facebook`
   - `io.strategiz.client.firebasesms` → `io.cidadel.client.sms`
   - `io.strategiz.client.webpush` → `io.cidadel.client.webpush`
4. **business-agent imports**: Update `VoiceAgentService.java` and `TranscriptionService.java` LLM client imports
5. **Cloud Build**: Remove strategiz-core Maven install entirely — all deps come from cidadel-core `publishToMavenLocal`

### strategiz-core

1. Add cidadel-core GitHub Packages as a Maven repository
2. Remove local auth modules (service-auth, business-token-auth, data-auth, data-user, data-session, data-device)
3. Remove local LLM client modules (client-anthropic-direct, client-openai-direct, client-grok-direct)
4. Add cidadel modules as dependencies
5. Update component scans to cidadel packages
6. Split `data-preferences` — move auth pieces to cidadel, keep product pieces

## Edge Cases & Risks

### 1. Vault Secret Paths (Medium Risk)

**Issue**: LLM clients load secrets from `secret/strategiz/anthropic`, `secret/strategiz/openai`, `secret/strategiz/grok`. Auth uses `secret/tacticl/*` for tacticl-specific secrets.

**Resolution**: Vault paths are config-driven via `application.properties`, not hardcoded in module code. Each product configures its own Vault context:
- Tacticl: `strategiz.vault.context=tacticl`
- Anthropic OAuth delegation: `anthropic.direct.oauth-vault-context=strategiz` (shared key management)

**Action**: Document that `anthropic.direct.oauth-vault-context` must stay `strategiz` — Anthropic key rotation is managed centrally.

### 2. Spring Bean Conflicts (Medium Risk)

**Issue**: tacticl-core has `spring.main.allow-bean-definition-overriding=true`. Duplicate CorsFilter and SecurityConfig beans exist with strategic naming to avoid conflicts.

**Resolution**:
- Cidadel auth beans should use `@ConditionalOnMissingBean` so product overrides take precedence
- Or use unique bean names (e.g., `@Bean("cidadelSessionAuthBusiness")`)
- Bean override flag already enabled as safety net

**Action**: Audit all `@Bean` definitions in migrated auth modules; add `@ConditionalOnMissingBean` where appropriate.

### 3. Cross-Product SSO Relay Tokens (Medium Risk)

**Issue**: `SsoRelayToken` in `data-user` enables cross-product SSO. If repository behavior changes, SSO breaks between products.

**Resolution**: SsoRelayToken entity already exists in cidadel-core's `data-user`. Firestore collection name stays the same. Both products must use the same cidadel version to ensure compatible serialization.

**Action**: Test SSO relay flow (tacticl → strategiz, strategiz → tacticl) before production deployment.

### 4. Firebase Singleton Initialization (High Risk)

**Issue**: Firebase SDK is a global singleton per JVM. Two FirebaseConfig classes exist:
- `io.cidadel.identity.data.base.config.FirebaseConfig` — `@Order(HIGHEST_PRECEDENCE + 2)`, `@Primary` (active)
- `io.strategiz.service.base.config.FirebaseConfig` — `@Profile("disabled")` (permanently off)

**Resolution**: Only cidadel's data-framework-base FirebaseConfig initializes. It checks `FirebaseApp.getApps().isEmpty()` before init. The service-framework-base version is disabled.

**Action**: Ensure tacticl-core never creates its own FirebaseConfig. Verify in integration tests that only one FirebaseApp initializes.

### 5. Session Cookie Name (Low Risk)

**Issue**: Session cookie is hardcoded as `STRATEGIZ_SESSION` in cidadel's SessionConfig. Renaming would log out all users.

**Resolution**: Keep `STRATEGIZ_SESSION` cookie name for backward compatibility. Rename is optional and would require coordinated rollout across all products.

**Action**: No change needed. Cosmetic rename can happen later with proper deprecation.

### 6. CORS Configuration (Low Risk)

**Issue**: Auth modules read `app.cookie.domain`, `app.cookie.same-site` from properties. CorsFilter patterns are product-specific.

**Resolution**: All CORS/cookie config is property-driven. Each product's `application-prod.properties` has the correct domains. No code change needed.

**Action**: Verify cross-origin requests from all expected domains after migration.

## Data Flow (unchanged)

```
User request → cidadel service-auth (REST controllers)
    → cidadel business-auth (SessionAuthBusiness, MFA, DeviceTrust)
    → cidadel data-* (Firestore repositories)
    → Host app's Firestore project (spring.cloud.gcp.project-id)
         tacticl → "tacticl" Firestore
         strategiz → "strategiz-io" Firestore
```

No data migration needed. Each product writes to its own Firestore via the host app's GCP project config.

## Architecture Summary (post-migration)

```
                    ┌──────────────────────────────────┐
                    │          cidadel-core             │
                    │    (single source of truth)       │
                    ├──────────────────────────────────┤
                    │  Framework (11 modules)           │
                    │  Auth: service + business + data  │
                    │  Auth clients: OAuth, SMS, email  │
                    │  LLM: Anthropic, OpenAI, Grok     │
                    └───────────────┬──────────────────┘
                                    │
                     Published to GitHub Packages (~26 modules)
                                    │
               ┌────────────────────┼────────────────────┐
               ▼                                         ▼
      ┌──────────────┐                         ┌──────────────┐
      │  tacticl-core │                         │strategiz-core│
      │  (Gradle)     │                         │  (Maven)     │
      │               │                         │              │
      │ + Sparks      │                         │ + Watchlists │
      │ + Social      │                         │ + Trading    │
      │ + Devices     │                         │ + Analytics  │
      │ + Video gen   │                         │ + Portfolios │
      │               │                         │              │
      │ GCP: tacticl  │                         │ GCP: strat.. │
      │ Firestore:own │                         │ Firestore:own│
      └──────────────┘                         └──────────────┘
```

**Post-migration, tacticl-core has ZERO dependency on strategiz-core.** All shared infrastructure comes from cidadel-core.

## Implementation Order

1. **cidadel-core**: Migrate business-auth + data-device + data-preferences split
2. **cidadel-core**: Migrate service-auth + auth clients (sendgrid, recaptcha, webpush)
3. **cidadel-core**: Migrate LLM clients (anthropic, openai, grok)
4. **cidadel-core**: Update publishedModules, build, publish
5. **tacticl-core**: Switch all deps to cidadel, update scans, remove strategiz-core from Cloud Build
6. **strategiz-core**: Remove local modules, add cidadel deps
7. **Both**: Deploy and verify (including SSO relay test)

## Pre-Migration Checklist

- [ ] Vault context mapping: Verify `anthropic.direct.oauth-vault-context` references
- [ ] Bean naming: Audit all `@Bean` definitions; add `@ConditionalOnMissingBean` where needed
- [ ] SSO flow test: Login tacticl → relay to strategiz → verify token exchange
- [ ] Firebase singleton: Verify only cidadel's data-framework-base FirebaseConfig initializes
- [ ] Cookie name: Confirm `STRATEGIZ_SESSION` cookie stays for backward compat
- [ ] CORS validation: Test cross-origin requests from all product domains
- [ ] Property overrides: Verify `spring.main.allow-bean-definition-overriding=true` doesn't mask conflicts

## Rollback Plan

If anything breaks after switching:
1. Revert the consumer's dependency change (one commit revert)
2. Re-add strategiz-core modules to Cloud Build
3. Redeploy

No data changes means rollback is purely a code/dependency revert.
