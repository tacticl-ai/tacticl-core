# Auth Migration to Cidadel-Core — Design Document

**Date**: 2026-02-28
**Status**: Approved, pending implementation
**Scope**: Migrate all reusable auth modules from strategiz-core into cidadel-core

## Problem

Tacticl-core currently pulls `service-auth` and `business-token-auth` from strategiz-core via Maven install in Cloud Build. All framework and infrastructure modules have already moved to cidadel-core, but the auth endpoints and business logic remain in strategiz-core. This creates:

- An unnecessary build dependency on strategiz-core for auth
- Split ownership of the auth stack across two repos
- Friction when evolving auth independently for each product

## Decision

Move all reusable auth code into cidadel-core, making it the single source for auth and core frameworks. Each product (tacticl, strategiz) consumes cidadel-core as a dependency. Auth data continues to write to each product's own Firestore (Option 2 architecture).

## What Moves

### New cidadel-core published modules

| Module | Source | Approx Files | New Package |
|--------|--------|-------------|-------------|
| `service-auth` | strategiz `service/service-auth` | ~127 | `io.cidadel.identity.service.auth` |
| `business-auth` | strategiz `business/business-token-auth` | ~11 | `io.cidadel.identity.business.auth` |
| `data-device` | strategiz `data/data-device` | ~8 | `io.cidadel.identity.data.device` |

### Auth clients (cidadel owns)

| Client | Purpose |
|--------|---------|
| `client-google` | Google OAuth sign-in |
| `client-facebook` | Facebook OAuth sign-in |
| `client-sms` | Firebase SMS / Twilio OTP |
| `client-sendgrid` | Email OTP delivery (new to cidadel) |
| `client-recaptcha` | Fraud detection (new to cidadel) |
| `client-webpush` | Push authentication (new to cidadel) |

### data-preferences split

- **Moves to cidadel `data-auth`**: `SecurityPreferences`, `ServiceAccountEntity`, `ServiceAccountRepository`
- **Stays in strategiz**: `PlatformSubscription`, `UserPreferenceEntity`, `TokenUsageRecord`, STRAT tracking, tier configs

### Already done in cidadel (no changes)

- `data-auth` (37 files) — entities, repos, domain models
- `data-user` (12 files) — UserEntity, UserProfile, email reservation
- `data-session` (4 files) — SessionEntity, SessionRepository
- All 11 framework modules (authorization, token-issuance, exception, logging, secrets, resilience, api-docs, llm-router, client-framework-base, service-framework-base, data-framework-base)

## Package Naming

- Auth-specific modules: `io.cidadel.identity.*` packages
- Framework modules: keep `io.strategiz.framework.*` (already decided, minimize blast radius)

## Publishing

Update `publishedModules` in cidadel-core `build.gradle.kts`:

```kotlin
val publishedModules = setOf(
    // Existing (11)
    "framework-exception", "framework-secrets", "framework-logging",
    "framework-resilience", "framework-api-docs", "framework-authorization",
    "framework-token-issuance", "framework-llm-router",
    "client-framework-base", "service-framework-base", "data-framework-base",
    // New (6+ data modules)
    "service-auth", "business-auth",
    "data-auth", "data-user", "data-session", "data-device",
    // New (auth clients)
    "client-google", "client-facebook", "client-sms",
    "client-sendgrid", "client-recaptcha", "client-webpush"
)
```

## Consumer Changes

### tacticl-core

1. **libs.versions.toml**: Replace `strategiz-service-auth` and `strategiz-business-token-auth` with cidadel equivalents
2. **application/build.gradle.kts**: Switch to cidadel auth dependencies
3. **TacticlApplication.java**: Update component scan packages:
   - `io.strategiz.service.auth` → `io.cidadel.identity.service.auth`
   - `io.strategiz.business.tokenauth` → `io.cidadel.identity.business.auth`
   - `io.strategiz.data.auth` → `io.cidadel.identity.data.auth`
   - `io.strategiz.data.user` → `io.cidadel.identity.data.user`
   - `io.strategiz.data.session` → `io.cidadel.identity.data.session`
   - `io.strategiz.data.device` → `io.cidadel.identity.data.device`
4. **Cloud Build**: Remove Maven install of `service-auth`, `service-framework-base`, `business-token-auth` from strategiz-core step (keep only LLM client installs if still needed)

### strategiz-core

1. Add cidadel-core GitHub Packages as a Maven repository
2. Remove local `service-auth`, `business-token-auth`, `data-auth`, `data-user`, `data-session`, `data-device` modules
3. Add cidadel auth modules as dependencies
4. Update component scans to cidadel packages
5. Split `data-preferences` — move auth pieces to cidadel, keep product pieces

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

## Auth Architecture Summary (post-migration)

```
                    ┌─────────────────────────────┐
                    │       cidadel-core           │
                    │  (single source of truth)    │
                    ├─────────────────────────────┤
                    │  service-auth (controllers)  │
                    │  business-auth (logic)        │
                    │  data-auth/user/session/device│
                    │  framework-* (11 modules)     │
                    │  client-* (OAuth, SMS, etc.)  │
                    └──────────┬──────────────────┘
                               │
                    Published to GitHub Packages
                               │
              ┌────────────────┼────────────────┐
              ▼                                 ▼
     ┌──────────────┐                  ┌──────────────┐
     │  tacticl-core │                  │strategiz-core│
     │  (Gradle)     │                  │  (Maven)     │
     │               │                  │              │
     │ GCP: tacticl  │                  │ GCP: strat.. │
     │ Firestore:own │                  │ Firestore:own│
     └──────────────┘                  └──────────────┘
```

## Implementation Order

1. **cidadel-core**: Copy service-auth + business-token-auth code, repackage, add to publishedModules
2. **cidadel-core**: Add auth client modules (sendgrid, recaptcha, webpush)
3. **cidadel-core**: Split data-preferences (SecurityPreferences → data-auth)
4. **cidadel-core**: Publish all new modules to GitHub Packages
5. **tacticl-core**: Switch from strategiz to cidadel auth deps, update scans, test
6. **strategiz-core**: Remove local auth modules, add cidadel deps, test
7. **Both**: Deploy and verify

## Risk Mitigation

- No behavior change — pure code reorganization
- No data migration — each product keeps its own Firestore
- Deploy strategiz-core first (source repo), then tacticl-core
- Run full test suites for both products after migration
- Keep strategiz-core's auth modules temporarily until both products are verified on cidadel
