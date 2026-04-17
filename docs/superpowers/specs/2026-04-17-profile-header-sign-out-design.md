# Profile Header + Sign Out — Design Spec

**Date:** 2026-04-17  
**Status:** Approved

## Problem

The Tacticl web dashboard has no profile visibility or sign-out capability in the header. Users cannot see who is logged in and have no way to sign out from the UI.

## Goals

- Show the authenticated user's name/avatar in the header
- Provide a sign-out action accessible from the header
- Establish `UserProfile` as the first MongoDB-backed entity in tacticl (FK to cidadel identity)
- Wire up the MongoDB infrastructure foundation all future MongoDB entities will use

## Out of Scope

- Profile editing / `PUT /v1/users/me` (deferred to UX revamp)
- Avatar upload (deferred to UX revamp)
- Server-side token revocation
- Full Firestore → MongoDB migration (separate effort)
- `tacticl-mobile` client wiring (out of scope for now)

---

## Architecture

### MongoDB Infrastructure Foundation

This is the first MongoDB feature. The following infrastructure must be established:

**`data/build.gradle.kts`** — add `spring-boot-starter-data-mongodb` to shared data deps.

**`application/` module:**
- `@EnableMongoRepositories(basePackages = "io.tacticl")` + `@EnableMongoAuditing` on the main application class (auditing required for `@CreatedDate` / `@LastModifiedDate` to populate)
- `MongoVaultConfig` — follows the `VaultConfig → @Bean` pattern (same as `AnthropicVaultConfig`): loads `secret/tacticl/mongodb` → key `uri` from Vault (context: `tacticl`), creates `MongoClient` bean
- Spring profile gating: `mongodb.uri` in `application-qa.yml` and `application-prod.yml` (URI injected from Vault via `framework-secrets`)
- Local dev: `application-local.yml` sets `spring.data.mongodb.uri=mongodb://localhost:27017/tacticl` (bypasses Vault for `bootRun`)
- Set `spring.data.mongodb.auto-index-creation=true` in config so `@Indexed(unique = true)` on `cidadelUserId` is enforced at startup (required for the `DuplicateKeyException` retry path to fire)

**`BaseMongoEntity` (in `data-profile`):**  
MongoDB entities do **not** extend the Firestore `BaseEntity`. A new `BaseMongoEntity` abstract class provides:
- `id` (`@Id String`)
- `isActive` (boolean, default `true`) — soft-delete convention
- `createdAt` (Instant, `@CreatedDate`)
- `updatedAt` (Instant, `@LastModifiedDate`)

This becomes the base for all future MongoDB entities in tacticl.

---

### Data Layer — `data-profile` module

**`UserProfile extends BaseMongoEntity`:**

| Field | Type | Notes |
|---|---|---|
| `cidadelUserId` | `String` | FK to cidadel identity; `@Indexed(unique = true)` |
| `displayName` | `String` | Synced from PASETO `name` claim |
| `email` | `String` | Synced from PASETO `email` claim |
| `avatarUrl` | `String` | Nullable; reserved for future upload |

Collection: `user_profiles`. Indexes: unique on `cidadelUserId`.

**`UserProfileRepository extends MongoRepository<UserProfile, String>`:**
- `Optional<UserProfile> findByCidadelUserIdAndIsActiveTrue(String cidadelUserId)`

**Module wiring:** Add `data/data-profile` to `settings.gradle.kts` and `data/build.gradle.kts` includes.

---

### Business Layer — `business-profile` module

**`UserProfileService extends BaseService`:**

```
UserProfile getOrCreate(AuthenticatedUser user)
```

Logic:
1. `findByCidadelUserIdAndIsActiveTrue(user.getUserId())`
2. If present → return as-is (no token-drift sync needed on read)
3. If absent → build new `UserProfile` from `user.getName()`, `user.getEmail()`, `user.getUserId()`, insert
4. On `DuplicateKeyException` (race: two simultaneous first-time requests) → re-read and return existing

If `user.getName()` or `user.getEmail()` is null → throw `BaseException` with `ErrorCode.INVALID_TOKEN_CLAIMS` (400).

**Constructor injection only.** No `@Autowired` fields.

**Module wiring:** Add `business/business-profile` to `settings.gradle.kts` and `business/build.gradle.kts` includes.

---

### Service Layer — `service-profile` module

**`ProfileController extends BaseController`:**

```
GET /v1/users/me
  Auth:     @RequireAuth (no additional scope — profile is a baseline entitlement)
  Response: UserProfileResponse { displayName, email, avatarUrl }
  Errors:   400 INVALID_TOKEN_CLAIMS, 500 via StandardErrorResponse / ErrorDetails
```

Calls `UserProfileService.getOrCreate(authUser)`, maps to `UserProfileResponse`.

**Module wiring:** Add `service/service-profile` to `settings.gradle.kts` and `service/build.gradle.kts` includes.

---

### Sign Out

Client-side only. PASETO tokens are stateless; clearing the token from local storage and redirecting to login is sufficient. No server endpoint required.

---

## Frontend (tacticl-web)

- Fetch `GET /v1/users/me` on app load; store `{ displayName, email, avatarUrl }` in auth context
- Header top-right: avatar circle with gradient initials (fallback if no `avatarUrl`)
- Click avatar → dropdown: `displayName`, `email`, divider, red "Sign out" button
- Sign out handler: clear token from storage → redirect to login
- UI style: Option A (avatar dropdown), existing dark theme

---

## Token Claims

`AuthenticatedUser` from `framework-authorization-1.0.21` (cidadel):

| Getter | PASETO claim | Used for |
|---|---|---|
| `getUserId()` | `sub` | `cidadelUserId` FK |
| `getName()` | `name` | `displayName` |
| `getEmail()` | `email` | `email` |

---

## Module Dependency Graph

```
service-profile  →  business-profile  →  data-profile
                 →  framework-authorization (AuthenticatedUser)
                 →  framework-exception (BaseException, StandardErrorResponse)
```

Follows strict layering: service → business → data, never reverse. No service-* → service-* deps.

---

## Testing

| Layer | Approach |
|---|---|
| `UserProfileRepository` | Flapdoodle embedded MongoDB (`de.flapdoodle.embed:de.flapdoodle.embed.mongo`) or Testcontainers MongoDB |
| `UserProfileService` | Unit test, mock repository; test `getOrCreate` happy path, absent-user creation, `DuplicateKeyException` retry, null-claim exception |
| `ProfileController` | `@WebMvcTest` + `AuthenticatedUser` stub via builder; test 200, 400 (null claims), 401 (no token) |

---

## Vault Secret

Path: `secret/tacticl/mongodb`, key: `uri`  
Context: `tacticl` (consistent with all other tacticl Vault secrets)
