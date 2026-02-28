# Auth & Infrastructure Migration to Cidadel-Core — Implementation Plan

**Date**: 2026-02-28
**Design**: [2026-02-28-auth-migration-to-cidadel-design.md](2026-02-28-auth-migration-to-cidadel-design.md)
**Estimated effort**: 6-8 hours with agent swarm

## Prerequisites

- cidadel-core repo cloned and buildable
- strategiz-core repo cloned and buildable
- GitHub Packages publish access for cidadel-core
- Both tacticl-core and strategiz-core locally buildable

## Phase 1: cidadel-core — Migrate Auth Business + Data Layers

### Task 1.1: Copy business-token-auth into cidadel business-auth
- Source: `strategiz-core/business/business-token-auth/src/`
- Target: `cidadel-core/business/business-auth/src/`
- Repackage from `io.strategiz.business.tokenauth` → `io.cidadel.identity.business.auth`
- Files (~11): SessionAuthBusiness, MfaEnforcementBusiness, DeviceTrustBusiness, PushAuthBusiness, AccountRecoveryBusiness, UserScopeResolver, ServiceAccountService, SessionValidationResult
- Update imports to reference cidadel data modules (`io.cidadel.identity.data.*`)
- Add `@ConditionalOnMissingBean` to all `@Bean` methods for safe override by products
- Update `build.gradle.kts` dependencies if needed

### Task 1.2: Copy data-device into cidadel
- Source: `strategiz-core/data/data-device/src/`
- Target: `cidadel-core/data/data-device/src/` (create module if not exists)
- Repackage from `io.strategiz.data.device` → `io.cidadel.identity.data.device`
- Add to `settings.gradle.kts` includes
- Add `build.gradle.kts` with data-framework-base dependency

### Task 1.3: Split data-preferences auth pieces into cidadel data-auth
- Move `SecurityPreferences` → `cidadel-core/data/data-auth/`
- Move `ServiceAccountEntity` + `ServiceAccountRepository` → `cidadel-core/data/data-auth/`
- Repackage to `io.cidadel.identity.data.auth`
- Remove from strategiz data-preferences (keep: PlatformSubscription, UserPreference, TokenUsage, tier configs)

### Task 1.4: Verify cidadel business-auth compiles
- Run `./gradlew :business:business-auth:compileJava` in cidadel-core
- Fix any import/dependency issues

## Phase 2: cidadel-core — Migrate Auth Service Layer + Clients

### Task 2.1: Copy service-auth controllers into cidadel service-auth
- Source: `strategiz-core/service/service-auth/src/`
- Target: `cidadel-core/service/service-auth/src/`
- Repackage from `io.strategiz.service.auth` → `io.cidadel.identity.service.auth`
- This is the largest module (~127 files: 22 controllers, 25+ services, 150+ DTOs, configs)
- Add `@ConditionalOnMissingBean` to config beans

### Task 2.2: Update all internal imports in service-auth
- Controllers → `io.cidadel.identity.business.auth`
- Data modules → `io.cidadel.identity.data.*`
- Framework modules → keep `io.strategiz.framework.*` (unchanged)
- Auth clients → `io.cidadel.client.*`

### Task 2.3: Add auth client modules to cidadel
- Copy from strategiz-core if not already in cidadel: `client-sendgrid`, `client-recaptcha`, `client-webpush`
- Repackage to `io.cidadel.client.*`
- Add to `settings.gradle.kts`
- Verify `client-google`, `client-facebook`, `client-sms` already in cidadel are complete

### Task 2.4: Verify cidadel service-auth compiles
- Run `./gradlew :service:service-auth:compileJava` in cidadel-core
- Fix any import/dependency issues

## Phase 3: cidadel-core — Migrate LLM Clients

### Task 3.1: Copy client-anthropic-direct
- Source: `strategiz-core/client/client-anthropic-direct/src/`
- Target: `cidadel-core/client/client-anthropic-direct/src/`
- Repackage from `io.strategiz.client.anthropic` → `io.cidadel.client.anthropic`
- Files (4): AnthropicDirectClient, AnthropicOAuthTokenManager, AnthropicDirectConfig, AnthropicVaultConfig
- Dependencies: client-framework-base, framework-secrets (both already in cidadel)
- Vault path in VaultConfig is a config string — no hardcoded product reference

### Task 3.2: Copy client-openai-direct
- Source: `strategiz-core/client/client-openai-direct/src/`
- Target: `cidadel-core/client/client-openai-direct/src/`
- Repackage from `io.strategiz.client.openai` → `io.cidadel.client.openai`
- Files (3): OpenAiDirectClient, OpenAiDirectConfig, OpenAiVaultConfig
- Uses OpenAiToolHelper from client-framework-base

### Task 3.3: Copy client-grok-direct
- Source: `strategiz-core/client/client-grok-direct/src/`
- Target: `cidadel-core/client/client-grok-direct/src/`
- Repackage from `io.strategiz.client.grok` → `io.cidadel.client.grok`
- Files (3): GrokDirectClient, GrokDirectConfig, GrokVaultConfig
- Uses OpenAiToolHelper (OpenAI-compatible format)

### Task 3.4: Verify all LLM clients compile
- Run `./gradlew :client:client-anthropic-direct:compileJava :client:client-openai-direct:compileJava :client:client-grok-direct:compileJava`
- Ensure LlmProvider interface, OpenAiToolHelper, SecretManager imports resolve

## Phase 4: cidadel-core — Publish

### Task 4.1: Update publishedModules
- Add to `publishedModules` set in root `build.gradle.kts`:
  - Auth: `service-auth`, `business-auth`, `data-auth`, `data-user`, `data-session`, `data-device`
  - Auth clients: `client-google`, `client-facebook`, `client-sms`, `client-sendgrid`, `client-recaptcha`, `client-webpush`
  - LLM: `client-anthropic-direct`, `client-openai-direct`, `client-grok-direct`

### Task 4.2: Build and publish
- Run `./gradlew build -x test` — verify full build passes
- Run `./gradlew test` — verify tests pass
- Push to main → GitHub Actions publishes to GitHub Packages
- Verify packages appear: `gh api '/users/cuztomizer/packages?package_type=maven' --jq '.[].name'`

## Phase 5: tacticl-core — Switch to cidadel

### Task 5.1: Update dependency declarations
- `gradle/libs.versions.toml`:
  - Remove all `strategiz-*` entries
  - Add cidadel equivalents: `cidadel-service-auth`, `cidadel-business-auth`, `cidadel-client-anthropic-direct`, `cidadel-client-openai-direct`, `cidadel-client-grok-direct`
- `application/build.gradle.kts`: Switch implementation lines
- `business-agent/build.gradle.kts`: Switch LLM client deps

### Task 5.2: Update component scans
- `TacticlApplication.java`: Replace ALL strategiz package scans with cidadel equivalents:
  - Auth: `io.strategiz.service.auth` → `io.cidadel.identity.service.auth` (+ business, data)
  - LLM: `io.strategiz.client.anthropic` → `io.cidadel.client.anthropic` (+ openai, grok)
  - Auth clients: `io.strategiz.client.sendgrid` → `io.cidadel.client.sendgrid` (+ all others)
  - Keep: `io.strategiz.framework.*` scans (framework packages unchanged)

### Task 5.3: Update business-agent imports
- `VoiceAgentService.java`: Update LLM client model imports (`LlmMessage`, `LlmResponse`, etc.)
- `TranscriptionService.java`: Update OpenAI config import
- `AgentSystemPrompt.java`: Update any client config references

### Task 5.4: Update Cloud Build
- `cloudbuild-prod.yaml`: Remove strategiz-core clone + Maven install entirely
- All deps now come from cidadel-core `publishToMavenLocal` (already in build)
- This simplifies Cloud Build significantly — one clone (cidadel) instead of two repos

### Task 5.5: Build and test tacticl-core
- `./gradlew build` — verify compilation
- `./gradlew test` — verify tests pass
- Local bootRun sanity check
- Verify auth endpoints respond correctly

### Task 5.6: Deploy tacticl-core
- `gcloud builds submit --config deployment/cloudbuild/cloudbuild-prod.yaml .`
- Verify auth endpoints work in prod
- Test SSO relay flow if applicable

## Phase 6: strategiz-core — Switch to cidadel

### Task 6.1: Add cidadel-core as Maven dependency
- Add cidadel GitHub Packages repo to root `pom.xml`
- Add cidadel auth + LLM module dependencies

### Task 6.2: Remove local modules
- Delete `service/service-auth/` module
- Delete `business/business-token-auth/` module
- Delete `client/client-anthropic-direct/`, `client-openai-direct/`, `client-grok-direct/`
- Delete auth pieces from `data/data-preferences/` (keep product pieces)
- Remove from parent pom module lists

### Task 6.3: Update component scans and imports
- Update all component scans to cidadel packages
- Update any direct imports in strategiz-specific code

### Task 6.4: Build and test strategiz-core
- `mvn clean install -DskipTests` — verify compilation
- `mvn test` — verify tests pass

### Task 6.5: Deploy strategiz-core
- Deploy and verify auth + LLM works

## Phase 7: Cleanup & Verification

### Task 7.1: Cross-product SSO test
- Login to tacticl → generate SSO relay token → exchange on strategiz
- Login to strategiz → generate SSO relay token → exchange on tacticl
- Verify token validation, session creation, cookie handling

### Task 7.2: LLM provider verification
- Test Claude API call through tacticl agent
- Test OpenAI Whisper transcription
- Test Grok fallback routing
- Verify LLM router selects correct provider

### Task 7.3: Update documentation
- Update CLAUDE.md in tacticl-core (remove strategiz dependency references)
- Update CLAUDE.md in strategiz-core
- Update CLAUDE.md in cidadel-core (add auth + LLM module docs)
- Update memory files

### Task 7.4: Verify Cloud Build is clean
- tacticl-core Cloud Build should only clone cidadel-core (no strategiz-core)
- Verify build time improved (one fewer repo to clone + install)

## Edge Case Mitigations

| Risk | Mitigation |
|------|------------|
| Vault paths change | All paths are config-driven; keep `anthropic.direct.oauth-vault-context=strategiz` |
| Bean conflicts | Add `@ConditionalOnMissingBean` to cidadel auth beans; `allow-bean-definition-overriding=true` as safety net |
| SSO breaks | Test relay flow in Phase 7; Firestore collection names stay the same |
| Firebase init race | Only cidadel's data-framework-base FirebaseConfig active; service-framework-base is `@Profile("disabled")` |
| Cookie name | Keep `STRATEGIZ_SESSION` for backward compat; cosmetic rename deferred |
| CORS failures | All CORS config is property-driven; verify domains in application-prod.properties |

## Rollback Plan

If anything breaks after switching:
1. Revert the consumer's dependency change (one commit revert)
2. Re-add strategiz-core clone + Maven install to Cloud Build
3. Redeploy

No data changes means rollback is purely a code/dependency revert.

## Agent Swarm Strategy

Parallelize within each phase:
- Phase 1: Tasks 1.1, 1.2, 1.3 in parallel (independent modules)
- Phase 2: Tasks 2.1, 2.3 in parallel (service-auth + client modules)
- Phase 3: Tasks 3.1, 3.2, 3.3 in parallel (independent clients)
- Phase 5: Tasks 5.1, 5.2, 5.3, 5.4 in parallel (independent config changes)
- Phase 6: Tasks 6.1, 6.2, 6.3 in parallel

Sequential gates: Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7
