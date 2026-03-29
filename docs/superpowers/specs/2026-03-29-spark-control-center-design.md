# Spark Control Center — Design Spec

**Date:** 2026-03-29
**Status:** Approved
**Repos:** tacticl-web (primary), tacticl-mobile (secondary), tacticl-core (no changes needed)

## Overview

User-facing Spark Control Center that lets users monitor spark execution, track PDLC pipeline progress role-by-role, view artifacts, approve checkpoints, and steer pipelines (skip roles, rework, cancel). Built on tacticl-web first, then adapted to tacticl-mobile.

All backend endpoints and WebSocket message types already exist in tacticl-core — this is purely a frontend build.

## Scope

Three additive views that build on existing infrastructure:

| View | Description | Route |
|------|-------------|-------|
| Enhanced Spark List | Upgrade existing SparkListPage with inline role progress bars, playbook labels, execution summaries | `/sparks` (existing) |
| PDLC Pipeline Detail | New page with PDLC role pipeline, live progress, event timeline, artifacts, steering controls | `/sparks/:id` (new route) |
| Simple Spark Detail | Same route, lighter layout with execution log for SIMPLE tier sparks | `/sparks/:id` (tier-aware) |

## View 1: Enhanced Spark List

Evolves the existing `SparkListPage` component.

### Changes to SparkRow

**Pipeline sparks (PLAYBOOK / FULL_PDLC tier):**
- Inline `RoleProgressBar` — mini colored segments per role (green=complete, purple glow=active, gray=pending)
- Playbook label badge (e.g., "SMALL_FEATURE", "BUG_FIX")
- Current role indicator text (e.g., "IMPLEMENTER")
- Left border color matches status

**Simple sparks (SIMPLE tier):**
- `SparkExecutionSummary` — single line: "4 tools called · 3.2K tokens"
- No role progress bar

**Both:**
- Entire row is clickable → navigates to `/sparks/:id`
- Expand arrow still works for quick-glance inline detail (existing behavior preserved)

## View 2: PDLC Pipeline Detail Page

New layout for `SparkDetailPage` when spark tier is PLAYBOOK or FULL_PDLC.

### Header

- Breadcrumb: Sparks / spark_id
- Spark title, type badge, playbook badge, priority badge
- Execution target (Cloud / device name)
- Elapsed time
- Cancel button (top right)

### KPI Strip

Horizontal row of 5 counters:

| Metric | Source |
|--------|--------|
| Roles Done (e.g., "4/6") | PipelineRun.activatedRoles vs completed count |
| Cost | PipelineRun.totalCost |
| Tokens | PipelineRun.totalTokens |
| Elapsed | now - PipelineRun.createdAt |
| Reworks | count of REWORK_TRIGGERED events |

### PDLC Role Pipeline

Horizontal row of role nodes. Each node shows:
- Role abbreviation (PM, RSCH, ARCH, PLAN, IMPL, REV, TEST, SEC, DOCS, OPS, RETRO)
- State: completed (green fill), active (purple glow + pulse animation), pending (dashed border), failed (red fill), skipped (strikethrough)
- Duration (completed roles)
- Model used (e.g., "Sonnet 4.5", "Opus 4")
- Token count
- Rework badge (if iterations > 1)

Connector lines between nodes: green for completed transitions, gray for pending.

Clicking a completed role node scrolls to its artifact tab.

### Checkpoint Banner (conditional)

Renders between role pipeline and two-column section when spark status is CHECKPOINT:
- Amber left border
- Checkpoint title and description
- Findings list (type, severity, description, suggested action)
- Feedback textarea
- Three buttons: Approve (green), Rework (amber outline), Reject (red outline)
- Calls `POST /v1/sparks/{id}/pipeline/checkpoint/{checkpointId}`

### Two-Column Layout

**Left column — Active Role Panel (flex: 1.2):**
- Active role name + model badge
- Live progress log (monospace terminal style, auto-scrolling)
  - Timestamps + progress messages from WebSocket `spark_progress` events
  - Blinking cursor on latest line
- File changes summary: chips showing created (+), modified (~), in-progress (...) files

**Right column — Event Timeline (flex: 0.8):**
- Reverse-chronological event list
- Each event: colored dot + event description + metadata (duration, tokens, model, iteration)
- Event types with colors:
  - Pipeline started/resumed: gray
  - Pipeline completed: green
  - Pipeline failed/cancelled: red
  - Role started: purple
  - Role completed: green
  - Role rejected/skipped: amber
  - Rework triggered/completed/escalated: amber
  - Checkpoint requested/resolved/timeout: amber
  - Artifact produced: teal
  - Cost warning/ceiling: red
  - Parallel roles started: purple
  - Unknown event types: gray (fallback)
- Source: `GET /v1/sparks/{id}/pipeline/events`

### PDLC Pipeline Controls (collapsible)

Collapsible section below the two-column layout:
- **Role skip toggles:** Chips for each upcoming (not-yet-started) role. Click to toggle skip. Skipped roles show strikethrough + dimmed.
- **Cost ceiling bar:** Progress bar showing current cost vs ceiling ($50 default). Color shifts: teal (<50%), amber (50-80%), red (>80%).

### Role Artifacts (tabbed)

Bottom section with tabs for each activated role:
- Completed roles: clickable tab, renders artifact content as markdown
- Active role: tab visible but shows "In progress..."
- Pending roles: greyed-out tab, not clickable
- Artifact metadata: model used, token count, iteration number
- Source: `GET /v1/sparks/{id}/pipeline/artifacts/{role}` (fetched on-demand when tab clicked)

## View 3: Simple Spark Detail Page

Same route (`/sparks/:id`), lighter layout when spark tier is SIMPLE.

### Header

Same as pipeline detail (title, type, priority, elapsed, cancel).

### KPI Strip

3 counters instead of 5: Cost, Tokens, Elapsed (no roles or reworks).

### Execution Log

Replaces role pipeline + two-column layout:
- Chronological list of tool calls
- Each entry: tool name, input summary (collapsed), output summary (collapsed), token usage, duration
- Expandable to see full input/output
- Chat-transcript style: alternating agent reasoning + tool calls
- Source: `GET /v1/sparks/{id}/logs`

### Result Card

When spark is completed: summary, findings, PRs, issues (same as existing SparkRow expanded result).

## Data Flow

### API Calls (new in tacticl-web)

All endpoints exist in tacticl-core — only need new hooks in web:

| Hook | Endpoint | Polling |
|------|----------|---------|
| `usePipelineRun(sparkId)` | `GET /v1/sparks/{id}/pipeline` | 5s when active |
| `usePipelineEvents(sparkId)` | `GET /v1/sparks/{id}/pipeline/events` | 5s when active |
| `useRoleArtifact(sparkId, role)` | `GET /v1/sparks/{id}/pipeline/artifacts/{role}` | On-demand (no poll) |
| `useResolveCheckpoint()` | `POST /v1/sparks/{id}/pipeline/checkpoint/{checkpointId}` | Mutation |

Existing hooks used as-is: `useSpark(id)`, `useSparkTactics(sparkId)`, `useSparkLogs(sparkId)`, `useCheckpoints()`.

### WebSocket Events (already handled, extend consumer)

| Message Type | Handler |
|--------------|---------|
| `pipeline_event` | New — update pipeline run cache, push to event timeline, invalidate queries on terminal events |
| `spark_progress` | Existing — feeds `useSparkProgressStore` (live progress terminal) |
| `spark_status` | Existing — updates spark status |
| `spark_completed` / `spark_failed` | Existing — invalidate queries, stop polling |
| `spark_checkpoint` | Existing — trigger checkpoint banner render |

### Polling + WebSocket Hybrid

- React Query polls at 5s intervals for active sparks (fallback)
- WebSocket pushes update Zustand stores immediately (primary)
- Terminal WebSocket events (`spark_completed`, `spark_failed`) invalidate React Query cache
- Completed sparks: no polling, data is static

## Component Architecture

### New Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `PipelineRoleStrip` | `src/components/sparks/` | Horizontal role nodes with state, duration, model, tokens |
| `KpiStrip` | `src/components/sparks/` | Counter bar (5 metrics for pipeline, 3 for simple) |
| `ActiveRolePanel` | `src/components/sparks/` | Live progress terminal + file change chips |
| `EventTimeline` | `src/components/sparks/` | Reverse-chronological event dots |
| `ArtifactTabs` | `src/components/sparks/` | Tabbed role output viewer with markdown rendering |
| `CheckpointBanner` | `src/components/sparks/` | Approval card with findings, feedback, action buttons |
| `PipelineControls` | `src/components/sparks/` | Role skip toggles + cost ceiling bar |
| `ExecutionLog` | `src/components/sparks/` | Tool call list for simple sparks |
| `RoleProgressBar` | `src/components/sparks/` | Mini inline bar for SparkRow |
| `SparkExecutionSummary` | `src/components/sparks/` | Mini inline text for SparkRow |

### Modified Components

| Component | Change |
|-----------|--------|
| `SparkRow` | Add `RoleProgressBar` / `SparkExecutionSummary` based on tier. Make clickable → navigate to `/sparks/:id` |
| `SparkListPage` | Add playbook label display |
| `SparkDetailPage` | Rebuild as tier-aware container (pipeline layout vs execution log layout) |
| `App.tsx` | Wire `/sparks/:id` route |
| `websocket.ts` | Add `pipeline_event` message handler |

### New Hooks

| Hook | File |
|------|------|
| `usePipelineRun` | `src/hooks/usePipeline.ts` |
| `usePipelineEvents` | `src/hooks/usePipeline.ts` |
| `useRoleArtifact` | `src/hooks/usePipeline.ts` |
| `useResolveCheckpoint` | `src/hooks/usePipeline.ts` |

### New API Functions

| Function | File |
|----------|------|
| `pipelineApi.getRun(sparkId)` | `src/api/pipeline.ts` |
| `pipelineApi.getEvents(sparkId, params?)` | `src/api/pipeline.ts` |
| `pipelineApi.getArtifact(sparkId, role)` | `src/api/pipeline.ts` |
| `pipelineApi.resolveCheckpoint(sparkId, checkpointId, data)` | `src/api/pipeline.ts` |

### New Types

```typescript
interface PipelineRun {
  id: string;
  sparkId: string;
  playbook: string;
  pipelineTier: 'SIMPLE' | 'PLAYBOOK' | 'FULL_PDLC';
  status: PipelineStatus;
  activatedRoles: PdlcRole[];
  currentRole: PdlcRole | null;
  roleResults: Record<string, RoleResultSummary>;  // keyed by role name string
  skippedRoles: string[];                           // requires backend DTO addition
  reworkCount: number;
  totalTokens: number;
  totalCost: number;
  startedAt: string | null;
  completedAt: string | null;
}

// Pipeline status lifecycle: CREATED → CLASSIFYING → AWAITING_CONFIRMATION → EXECUTING → CHECKPOINT → COMPLETED/FAILED/CANCELLED
type PipelineStatus =
  | 'CREATED' | 'CLASSIFYING' | 'AWAITING_CONFIRMATION'
  | 'EXECUTING' | 'CHECKPOINT'
  | 'COMPLETED' | 'FAILED' | 'CANCELLED';

interface RoleResultSummary {
  status: 'PENDING' | 'EXECUTING' | 'COMPLETED' | 'REJECTED' | 'REWORKING' | 'FAILED' | 'ESCALATED' | 'SKIPPED' | 'AWAITING_APPROVAL';
  model: string;
  tokens: number;
  cost: number;
  durationMs: number;
  iteration: number;
}

interface PipelineEvent {
  id: string;
  pipelineRunId: string;  // requires backend DTO addition
  eventType: PipelineEventType;
  role: PdlcRole | null;
  roleIteration: number;  // int, defaults to 0 (not nullable)
  metadata: Record<string, unknown>;
  timestamp: string;
}

type PipelineEventType =
  | 'PIPELINE_STARTED' | 'PIPELINE_COMPLETED' | 'PIPELINE_FAILED' | 'PIPELINE_CANCELLED' | 'PIPELINE_RESUMED'
  | 'ROLE_STARTED' | 'ROLE_COMPLETED' | 'ROLE_REJECTED' | 'ROLE_SKIPPED'
  | 'REWORK_TRIGGERED' | 'REWORK_COMPLETED' | 'REWORK_ESCALATED'
  | 'ARTIFACT_PRODUCED'
  | 'CHECKPOINT_REQUESTED' | 'CHECKPOINT_RESOLVED' | 'CHECKPOINT_TIMEOUT_REMINDER'
  | 'PARALLEL_ROLES_STARTED'
  | 'COST_THRESHOLD_WARNING' | 'COST_CEILING_REACHED';

type PdlcRole =
  | 'PM' | 'RESEARCHER' | 'ARCHITECT' | 'DESIGNER' | 'PLANNER'
  | 'IMPLEMENTER' | 'REVIEWER' | 'TESTER' | 'SECURITY_ANALYST'
  | 'TECHNICAL_WRITER' | 'DEVOPS' | 'RETRO_ANALYST';

// NOTE: Backend currently returns Map<String, Object> for artifacts.
// Needs new RoleArtifactResponse DTO with these fields.
interface RoleArtifact {
  role: PdlcRole;
  content: Record<string, unknown>;  // structured content map, not raw string
  model: string;
  tokens: number;
  iteration: number;
  createdAt: string;
}

interface CheckpointResolution {
  decision: 'APPROVED' | 'REJECTED' | 'MODIFIED';
  feedback: string | null;
}
```

## Mobile Adaptation (tacticl-mobile)

Same three views, adapted for React Native and small screens.

### Enhanced Spark List (Dashboard tab)

- Migrate "asks" terminology → "sparks" in stores, API calls, types
- Add `RoleProgressBar` to active spark cards
- `SparkExecutionSummary` for simple sparks
- Tap card → navigate to new spark detail screen

### Spark Detail Screen

New route: `app/spark/[id].tsx`

Layout adaptations:
- KPI strip: **2x3 grid** instead of horizontal row
- Role pipeline: **horizontally scrollable** (same nodes, swipe to see all roles)
- Two-column layout **stacks vertically**: active role panel on top, event timeline below (collapsible accordion)
- Artifact tabs: **bottom sheet** triggered by tapping a completed role node
- Checkpoint banner: **full-width card** pushed to top of screen

### Steering Controls

- Role skip toggles: bottom sheet triggered by "Controls" FAB
- Cost ceiling bar: bottom of KPI strip grid

### API Changes

- New API calls: `pipelineApi.getRun`, `pipelineApi.getEvents`, `pipelineApi.getArtifact`, `pipelineApi.resolveCheckpoint`
- New WebSocket handler for `pipeline_event` message type
- Migrate activity store from asks → sparks (rename types, update API paths)

### Navigation

- New file: `app/spark/[id].tsx`
- Dashboard spark cards: `onPress → router.push(\`/spark/${spark.id}\`)`

## Backend Changes

**Minor DTO changes required.** All endpoints exist, but some response DTOs need fields added:

- `PipelineRunResponse` — add `roleResults` map, `skippedRoles` list, `createdAt`/`updatedAt` timestamps
- `PipelineArtifact` endpoint — return structured `RoleArtifactResponse` DTO instead of raw `Map<String, Object>` (include `role`, `content`, `model`, `tokens`, `iteration`, `createdAt`)
- `PipelineEventResponse` — add `pipelineRunId` field
- Role skip mid-pipeline — new `PUT /v1/sparks/{id}/pipeline/skip-roles` endpoint needed for live skip toggles

Existing endpoints:
- `GET /v1/sparks/{id}/pipeline` — pipeline run status
- `GET /v1/sparks/{id}/pipeline/events` — event timeline
- `GET /v1/sparks/{id}/pipeline/artifacts/{role}` — role artifact
- `POST /v1/sparks/{id}/pipeline/checkpoint/{checkpointId}` — resolve checkpoint
- `GET /v1/sparks/{id}/logs` — execution logs

All WebSocket message types exist: `pipeline_event`, `spark_progress`, `spark_status`, `spark_completed`, `spark_failed`, `spark_checkpoint`.

## Visual Reference

Mockups saved in `.superpowers/brainstorm/75796-1774741041/`:
- `spark-control-center-overview.html` — Three-view overview (list, detail, controls)
- `pipeline-detail-v2.html` — Full pipeline detail page mockup
