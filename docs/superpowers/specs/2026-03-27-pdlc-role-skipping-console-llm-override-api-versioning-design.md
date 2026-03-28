# PDLC Role Skipping, Console LLM Override & API Versioning

**Date**: 2026-03-27
**Status**: Approved

## Overview

Three changes to the tacticl-core backend:

1. **PDLC Role Skipping** — Users can skip pipeline roles via natural language keywords or explicit API fields. Soft guardrails confirm before skipping required roles.
2. **Console LLM Override** — Admin users can override which engine/model handles each SDLC step and PDLC role via REST endpoints on the Tacticl Console.
3. **API Versioning** — Migrate all REST endpoints from `/api/*` to `/v1/*`.

## Feature 1: PDLC Role Skipping

### Input Channels

**A. Natural Language Parsing**

A stateless `RoleSkipParser` utility extracts skip intent from command text using regex patterns:

| User phrase | Skipped role(s) |
|---|---|
| "skip review", "no review" | REVIEWER |
| "skip tests", "no testing", "don't test" | TESTER |
| "skip security", "no security check" | SECURITY_ANALYST |
| "skip docs", "no documentation" | TECHNICAL_WRITER |
| "just implement", "implement only" | all except IMPLEMENTER |
| "skip planning" | PM, PLANNER |
| "no retro" | RETRO_ANALYST |
| "skip design" | DESIGNER |
| "skip research" | RESEARCHER |
| "skip devops", "no deploy" | DEVOPS |

**B. Explicit API Field**

New `skipRoles` field on `AgentCommandRequest`:

```json
POST /v1/agent/command
{
  "text": "Clean up dead code in strategiz-core",
  "skipRoles": ["REVIEWER", "TESTER"]
}
```

### Merge Logic

In `AgentController.executeInCloudOrPipeline()`:

1. Parse NL keywords from `request.getText()` → `nlSkipRoles`
2. Read `request.getSkipRoles()` → `apiSkipRoles`
3. Union: `effectiveSkipRoles = nlSkipRoles ∪ apiSkipRoles` (API field takes precedence in case of conflict)
4. Remove `effectiveSkipRoles` from `classification.activatedRoles()`

### Soft Guardrails

Each `PlaybookStage` has a `required` flag. If a user tries to skip a required role:

- A confirmation checkpoint is created before pipeline dispatch
- The checkpoint message explains which required role(s) would be skipped
- User can approve (skip anyway) or deny (keep the role)
- Pipeline does not start until the checkpoint is resolved

### New Components

| Component | Module | Description |
|---|---|---|
| `RoleSkipParser` | `business-agent` | Stateless utility, regex-based, returns `Set<PdlcRole>` |
| `skipRoles` field | `service-agent` DTO | New `List<String>` field on `AgentCommandRequest` |

### Modified Components

| Component | Change |
|---|---|
| `AgentController.executeInCloudOrPipeline()` | Merge skip sources, remove from activatedRoles, create checkpoint if required roles skipped |
| `PdlcClassification` | No change — activatedRoles is modified after classification returns |

## Feature 2: Console LLM Override

### Naming Convention

- "Console" is the product (Tacticl Console)
- "Admin" is the user role that accesses the console
- Endpoints live under `/v1/console/*`
- Auth: `@RequireScope("admin")`

### Override Resolution Chain

```
Role override → Step override → Product defaults (AiSdlcStepDefaults) → Fallback chain
```

Highest priority wins. Role overrides only apply during PDLC pipeline execution. Step overrides apply to both pipeline and simple cloud execution.

### Step-Level Overrides

Uses the existing cidadel `ai_engine_overrides` Firestore collection.

**Endpoints:**

```
GET    /v1/console/ai-engine-routing/steps              — list all step configs (defaults + overrides)
GET    /v1/console/ai-engine-routing/steps/{stepName}    — get one step config
PUT    /v1/console/ai-engine-routing/steps/{stepName}    — set override (engine + model)
DELETE /v1/console/ai-engine-routing/steps/{stepName}    — remove override (revert to default)
```

**PUT request body:**
```json
{
  "engineId": "anthropic-agentic",
  "model": "claude-opus-4-6"
}
```

**GET response** (includes both default and override info):
```json
{
  "stepName": "CODE_GENERATION",
  "default": { "engineId": "claude-code-cli", "model": "claude-opus-4-6", "fallbacks": ["codex-cli", "anthropic-agentic"] },
  "override": { "engineId": "anthropic-agentic", "model": "claude-sonnet-4-6" },
  "effective": { "engineId": "anthropic-agentic", "model": "claude-sonnet-4-6" }
}
```

### Role-Level Overrides

New Firestore collection: `ai_role_overrides`.

**Endpoints:**

```
GET    /v1/console/ai-engine-routing/roles               — list all role overrides
GET    /v1/console/ai-engine-routing/roles/{roleName}     — get one role override
PUT    /v1/console/ai-engine-routing/roles/{roleName}     — set override (engine + model)
DELETE /v1/console/ai-engine-routing/roles/{roleName}     — remove override (revert to step default)
```

**Firestore document** (`ai_role_overrides/{roleName}`):
```json
{
  "role": "IMPLEMENTER",
  "engineId": "anthropic-agentic",
  "model": "claude-opus-4-6",
  "updatedBy": "admin-user-id",
  "updatedAt": "2026-03-27T01:30:00Z"
}
```

### Integration Point

In `RealPdlcRoleExecutor.execute()`, before resolving the engine via the SDLC step:

1. Query `ai_role_overrides` for the current `PdlcRole`
2. If found → use that engine + model (skip step-level resolution)
3. If not found → fall back to existing step-level resolution chain

### New Components

| Component | Module | Description |
|---|---|---|
| `ConsoleAiEngineRoutingController` | `service-agent` | REST controller for step + role override CRUD |
| `AiRoleOverride` entity | `data-social` | Firestore entity for role-level overrides |
| `AiRoleOverrideRepository` | `data-social` | Firestore repository for `ai_role_overrides` collection |
| `AiRoleOverrideService` | `business-agent` | Business logic: read/write role overrides, resolve effective config |

### Modified Components

| Component | Change |
|---|---|
| `RealPdlcRoleExecutor` | Check role override before step-level resolution |
| `AiEngineAdapterConfig` | No change — engines already registered |

## Feature 3: API Versioning

### Migration

All existing `@RequestMapping("/api/...")` prefixes change to `/v1/...`:

| Current | New |
|---|---|
| `/api/agent/*` | `/v1/agent/*` |
| `/api/sparks/*` | `/v1/sparks/*` |
| `/api/social/*` | `/v1/social/*` |
| `/api/settings/*` | `/v1/settings/*` |
| `/api/devices/*` | `/v1/devices/*` |
| `/api/repos/*` | `/v1/repos/*` |
| `/api/tokens/*` | `/v1/tokens/*` |
| `/api/checkpoints/*` | `/v1/checkpoints/*` |
| `/api/admin/*` | `/v1/console/*` |

### Affected Controllers

All controllers in `service-agent`, `service-spark`, `service-social`, `service-checkpoint`, `service-repo`, `service-token`.

### Mobile App Impact

The React Native app (`tacticl-mobile`) will need its base URL path updated. This should be coordinated with a mobile app release — either:
- Deploy backend with both `/api/*` and `/v1/*` active temporarily (backward compat)
- Or ship mobile update first with configurable base path

**Recommendation**: Add a temporary `@RequestMapping` alias so both `/api/*` and `/v1/*` work during transition. Remove `/api/*` aliases in a follow-up once all mobile clients are updated.

## Out of Scope

- Custom playbook creation via console (future)
- Per-user LLM override (future — currently admin-global only)
- Mobile app base URL migration (separate PR, coordinate with mobile team)
