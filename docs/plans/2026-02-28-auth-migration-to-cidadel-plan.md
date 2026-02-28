# Auth Migration to Cidadel-Core — Implementation Plan

**Date**: 2026-02-28
**Design**: [2026-02-28-auth-migration-to-cidadel-design.md](2026-02-28-auth-migration-to-cidadel-design.md)
**Estimated effort**: 4-6 hours with agent swarm

## Prerequisites

- cidadel-core repo cloned and buildable
- GitHub Packages publish access for cidadel-core
- Both tacticl-core and strategiz-core locally buildable

## Phase 1: cidadel-core — Migrate Business Layer

### Task 1.1: Copy business-token-auth into cidadel business-auth
- Source: `strategiz-core/business/business-token-auth/src/`
- Target: `cidadel-core/business/business-auth/src/`
- Repackage from `io.strategiz.business.tokenauth` → `io.cidadel.identity.business.auth`
- Files (~11): SessionAuthBusiness, MfaEnforcementBusiness, DeviceTrustBusiness, PushAuthBusiness, AccountRecoveryBusiness, UserScopeResolver, ServiceAccountService, SessionValidationResult
- Update imports to reference cidadel data modules (`io.cidadel.identity.data.*`)
- Update `build.gradle.kts` dependencies if needed

### Task 1.2: Copy data-device into cidadel
- Source: `strategiz-core/data/data-device/src/`
- Target: `cidadel-core/data/data-device/src/` (create module if not exists)
- Repackage from `io.strategiz.data.device` → `io.cidadel.identity.data.device`
- Add to `settings.gradle.kts` includes
- Add `build.gradle.kts` with data-framework-base dependency

### Task 1.3: Split data-preferences auth pieces into cidadel data-auth
- Move `SecurityPreferences` → `cidadel-core/data/data-auth/` (may already exist)
- Move `ServiceAccountEntity` + `ServiceAccountRepository` → `cidadel-core/data/data-auth/`
- Repackage to `io.cidadel.identity.data.auth`
- Remove from strategiz data-preferences (keep product-specific: PlatformSubscription, UserPreference, TokenUsage)

### Task 1.4: Verify cidadel business-auth compiles
- Run `./gradlew :business:business-auth:compileJava` in cidadel-core
- Fix any import/dependency issues

## Phase 2: cidadel-core — Migrate Service Layer

### Task 2.1: Copy service-auth controllers into cidadel service-auth
- Source: `strategiz-core/service/service-auth/src/`
- Target: `cidadel-core/service/service-auth/src/`
- Repackage from `io.strategiz.service.auth` → `io.cidadel.identity.service.auth`
- This is the largest module (~127 files: 22 controllers, 25+ services, 150+ DTOs, configs)

### Task 2.2: Update all internal imports
- Controllers reference business-auth classes → update to `io.cidadel.identity.business.auth`
- Controllers reference data modules → update to `io.cidadel.identity.data.*`
- Controllers reference framework modules → keep `io.strategiz.framework.*` (unchanged)
- Controllers reference auth clients → update to cidadel client packages

### Task 2.3: Add auth client modules to cidadel
- Check if `client-sendgrid`, `client-recaptcha`, `client-webpush` already exist in cidadel-core
- If not, create them from strategiz-core sources
- Repackage to `io.cidadel.identity.client.*`
- Add to `settings.gradle.kts`

### Task 2.4: Verify cidadel service-auth compiles
- Run `./gradlew :service:service-auth:compileJava` in cidadel-core
- Fix any import/dependency issues

## Phase 3: cidadel-core — Publish

### Task 3.1: Update publishedModules
- Add to `publishedModules` set in root `build.gradle.kts`:
  - `service-auth`, `business-auth`
  - `data-auth`, `data-user`, `data-session`, `data-device`
  - Auth client modules as needed

### Task 3.2: Build and publish
- Run `./gradlew build -x test` — verify full build passes
- Run `./gradlew test` — verify tests pass
- Push to main → GitHub Actions publishes to GitHub Packages
- Verify packages appear: `gh api '/users/cuztomizer/packages?package_type=maven' --jq '.[].name'`

## Phase 4: tacticl-core — Switch to cidadel auth

### Task 4.1: Update dependency declarations
- `gradle/libs.versions.toml`: Replace `strategiz-service-auth` and `strategiz-business-token-auth` with cidadel versions
- Add cidadel auth module entries: `cidadel-service-auth`, `cidadel-business-auth`, etc.
- `application/build.gradle.kts`: Switch implementation lines

### Task 4.2: Update component scans
- `TacticlApplication.java`: Replace strategiz package scans with cidadel equivalents
- Update any `@Import` or `@ComponentScan` annotations elsewhere

### Task 4.3: Update Cloud Build
- `cloudbuild-prod.yaml`: Remove strategiz-core Maven install of `service-auth`, `service-framework-base`, `business-token-auth`
- cidadel modules come from `publishToMavenLocal` (already in build) or GitHub Packages
- Keep strategiz-core install only for LLM clients (`client-anthropic-direct`, etc.) if still needed

### Task 4.4: Build and test tacticl-core
- `./gradlew build` — verify compilation
- `./gradlew test` — verify tests pass
- Local bootRun sanity check

### Task 4.5: Deploy tacticl-core
- `gcloud builds submit --config deployment/cloudbuild/cloudbuild-prod.yaml .`
- Verify auth endpoints work in prod

## Phase 5: strategiz-core — Switch to cidadel auth

### Task 5.1: Add cidadel-core as Maven dependency
- Add cidadel GitHub Packages repo to root `pom.xml`
- Add cidadel auth module dependencies

### Task 5.2: Remove local auth modules
- Delete `service/service-auth/` module
- Delete `business/business-token-auth/` module
- Delete auth pieces from `data/data-preferences/` (keep product pieces)
- Remove from parent pom module lists

### Task 5.3: Update component scans and imports
- Update all component scans to cidadel packages
- Update any direct imports in strategiz-specific code

### Task 5.4: Build and test strategiz-core
- `mvn clean install -DskipTests` — verify compilation
- `mvn test` — verify tests pass

### Task 5.5: Deploy strategiz-core
- Deploy and verify auth works

## Phase 6: Cleanup

### Task 6.1: Remove strategiz auth module install from tacticl Cloud Build
- If Phase 4.3 kept any strategiz auth installs as safety net, remove them now

### Task 6.2: Update documentation
- Update CLAUDE.md in both repos
- Update memory files

## Rollback Plan

If anything breaks after switching:
1. Revert the consumer's dependency change (one commit revert)
2. Re-add strategiz-core auth modules to Cloud Build
3. Redeploy

No data changes means rollback is purely a code/dependency revert.

## Agent Swarm Strategy

Parallelize within each phase:
- Phase 1: Tasks 1.1, 1.2, 1.3 can run in parallel (independent modules)
- Phase 2: Tasks 2.1, 2.3 can run in parallel (service-auth + client modules)
- Phase 4: Tasks 4.1, 4.2, 4.3 can run in parallel (independent config changes)
- Phase 5: Tasks 5.1, 5.2, 5.3 can run in parallel

Sequential gates: Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
