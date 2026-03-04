# Design: Migrate tacticl-core to cidadel's data-framework-base

**Date**: 2026-03-03
**Status**: Approved
**Approach**: Full Adoption (Approach 1)

## Context

Tacticl-core uses 13 of 14 cidadel-core shared modules. The missing one is `data-framework-base`, which provides `BaseEntity`, `BaseRepository`, `SubcollectionRepository`, auditing, transactions, and structured error handling. Instead, tacticl has two lightweight local base classes (`FirestoreRepository<T>`, `FirestoreSubcollectionRepository<T>`) that lack audit fields, soft delete, transactions, and structured errors.

## Goal

Replace tacticl's local Firestore base classes with cidadel's `data-framework-base`. All entities extend `BaseEntity`, all repos extend `BaseRepository`/`SubcollectionRepository`. Delete local base classes.

## Design

### 1. Dependency & Spring Config

Add `data-framework-base` to `libs.versions.toml`:
```
cidadel-data-framework-base = { module = "io.cidadel:data-framework-base", version.ref = "cidadel" }
```

Add to `data/build.gradle.kts` as shared dependency for all data submodules.

**Firestore initialization**: Exclude cidadel's `FirebaseConfig` + `FirebaseVaultConfig` via `@ComponentScan` filter (they hardcode `secret/strategiz/firebase` vault path). Keep tacticl's own `FirestoreConfig`. Add `@ComponentScan` for `io.cidadel.identity.data.base.audit` to pick up `FirestoreAuditingHandler` and `FirestoreAuditorAware` beans.

### 2. Entity Migration

All standalone entities extend `BaseEntity`. Gains:
- `createdBy`, `modifiedBy` (String) — audit user tracking
- `createdDate`, `modifiedDate` (Timestamp) — audit timestamps
- `version` (Long) — optimistic locking
- `isActive` (Boolean) — soft delete flag

Per entity:
- Add `extends BaseEntity`
- Add `@Collection("collection_name")` annotation
- Implement abstract `getId()`/`setId()` (already have these as concrete methods — just add `@Override`)
- Remove redundant fields: `createdAt`, `updatedAt`, `isActive` + their getters/setters
- Keep `@IgnoreExtraProperties` for backward compat with existing Firestore data
- Keep domain-specific timestamp fields (`completedAt`, `lastSeenAt`, `closedAt`, etc.)

Embedded POJOs (UserConfig, DeviceSettings, BrowserSettings) and enums — no change.

### 3. Repository Migration

**Flat repos** — extend `BaseRepository<T>`:
- Constructor: `(Firestore, Class<T>, FirestoreAuditingHandler)` — collection name moves to `@Collection` on entity
- Implement `getModuleName()` abstract method
- Custom query methods: update to use `findByField()` which now auto-filters `isActive=true`

**Subcollection repos** — extend `SubcollectionRepository<T>`:
- Constructor: `(Firestore, Class<T>, FirestoreAuditingHandler)`
- Implement `getModuleName()`, `getParentCollectionName()` (returns `"tacticl_users"`), `getSubcollectionName()`
- Methods rename: `save()` → `saveInSubcollection()`, `findById()` → `findByIdInSubcollection()`, `findAll()` → `findAllInSubcollection()`

### 4. Caller Migration

Both save signatures from cidadel's BaseRepository are available:
- `save(T entity)` — uses SecurityContext for audit user
- `save(T entity, String userId)` — explicit audit user

**Flat repo callers**:
```java
// Old: sparkRepository.save(spark, spark.getId())
// New: sparkRepository.save(spark, userId)  // entity already has ID set

// Old: sparkRepository.save(spark, sparkId)  // ID passed separately
// New: spark.setId(sparkId); sparkRepository.save(spark, userId)
```

**Subcollection repo callers**:
```java
// Old: integrationRepository.save(userId, integration, integration.getId())
// New: integrationRepository.saveInSubcollection(userId, integration, userId)
```

### 5. Cleanup

Delete after migration:
- `data/data-social/.../repository/FirestoreRepository.java`
- `data/data-social/.../repository/FirestoreSubcollectionRepository.java`
- `data/data-social/.../repository/FirestoreSubcollectionRepositoryTest.java`
- Remove tacticl's `FirestoreConfig` if cidadel's `FirebaseConfig` is used instead (TBD based on vault path configurability)

### 6. Backward Compatibility

- Old docs with `createdAt` field → `@IgnoreExtraProperties` ignores unknown fields on read
- Old docs without audit fields → null on read → BaseRepository treats as "create" on next save → audit fields populated
- No Firestore data migration required — fields populate naturally as documents are saved

## Scope

- ~25 standalone entity classes (extend BaseEntity)
- ~20 repository classes (extend BaseRepository/SubcollectionRepository)
- ~50+ caller sites in business/service layers (update save signatures)
- 2 local base classes deleted
- 1 dependency added

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Parent collection for subcollections | Keep `tacticl_users` | No data migration, product isolation |
| Audit field naming | Use BaseEntity's `createdDate`/`modifiedDate` | Standard cidadel convention |
| Old `createdAt` data | Orphaned, ignored via `@IgnoreExtraProperties` | Pre-launch, acceptable |
| FirestoreConfig | Exclude cidadel's, keep tacticl's enhanced | Vault path hardcoded to wrong context |
| Save signatures | Both available: `save(entity)` and `save(entity, userId)` | Flexibility for different contexts |
