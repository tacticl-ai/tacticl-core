# Spark Control Center Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the user-facing Spark Control Center in tacticl-web (then tacticl-mobile) — enhanced spark list, PDLC pipeline detail page, simple spark detail, and inline steering controls.

**Architecture:** Pipeline-agnostic detail page shell (header, KPI strip, event timeline, controls) with pluggable pipeline visualizations. PDLC is the first pipeline type. The shell renders based on a `pipelineType` discriminator so future pipeline types can provide their own stage visualization without rewriting the container.

**Tech Stack:** React 19, MUI 7, TanStack React Query 5, Zustand 5, React Router 7, TypeScript 5.9, Vite 7.3

**Spec:** `docs/superpowers/specs/2026-03-29-spark-control-center-design.md`

---

## Chunk 1: Backend DTO Fixes (tacticl-core)

Small changes to expose fields the frontend needs. All in existing files.

### Task 1: Add roleResults and skippedRoles to PipelineRunResponse

**Files:**
- Modify: `tacticl-core/service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/PipelineRunResponse.java`

- [ ] **Step 1: Add roleResults field to PipelineRunResponse**

Add field and map it in the `from()` factory method:

```java
private Map<String, RoleResultSummary> roleResults;
private List<String> skippedRequiredRoles;
```

In `from()`:
```java
response.setRoleResults(run.getRoleResults());
response.setSkippedRequiredRoles(run.getSkippedRequiredRoles());
```

- [ ] **Step 2: Add createdAt field**

The entity inherits `createdDate` from BaseEntity. Add to DTO:

```java
private Instant createdAt;
```

In `from()`:
```java
response.setCreatedAt(run.getCreatedDate());
```

- [ ] **Step 3: Run tests**

Run: `cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew :service:service-agent:test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/PipelineRunResponse.java
git commit -m "feat(pipeline): expose roleResults, skippedRequiredRoles, createdAt in PipelineRunResponse"
```

### Task 2: Add pipelineRunId to PipelineEventResponse

**Files:**
- Modify: `tacticl-core/service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/PipelineEventResponse.java`

- [ ] **Step 1: Add pipelineRunId field**

```java
private String pipelineRunId;
```

In `from()`:
```java
response.setPipelineRunId(event.getPipelineRunId());
```

- [ ] **Step 2: Run tests**

Run: `cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew :service:service-agent:test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/PipelineEventResponse.java
git commit -m "feat(pipeline): expose pipelineRunId in PipelineEventResponse"
```

### Task 3: Create RoleArtifactResponse DTO

**Files:**
- Create: `tacticl-core/service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/RoleArtifactResponse.java`
- Modify: `tacticl-core/service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/PipelineController.java`

- [ ] **Step 1: Create RoleArtifactResponse**

```java
package io.strategiz.social.service.agent.dto;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import java.time.Instant;
import java.util.Map;

public class RoleArtifactResponse {
    private String id;
    private PdlcRole role;
    private String artifactType;
    private Map<String, Object> content;
    private int artifactVersion;
    private String model;       // joined from RoleResultSummary
    private long tokens;        // joined from RoleResultSummary
    private int iteration;      // joined from RoleResultSummary
    private Instant createdAt;

    // Factory: takes artifact + roleResult from PipelineRun.roleResults for that role
    public static RoleArtifactResponse from(PipelineArtifact artifact, RoleResultSummary roleResult) {
        var response = new RoleArtifactResponse();
        response.setId(artifact.getId());
        response.setRole(artifact.getRole());
        response.setArtifactType(artifact.getArtifactType());
        response.setContent(artifact.getContent());
        response.setArtifactVersion(artifact.getArtifactVersion());
        response.setCreatedAt(artifact.getCreatedAt());
        if (roleResult != null) {
            response.setModel(roleResult.getModel());
            response.setTokens(roleResult.getTokens());
            response.setIteration(roleResult.getIteration());
        }
        return response;
    }

    // Manual getters/setters (no Lombok — matches existing DTO pattern in codebase)
    // Generate for all fields: id, role, artifactType, content, artifactVersion, model, tokens, iteration, createdAt
}
```

- [ ] **Step 2: Update PipelineController.getArtifact to return RoleArtifactResponse**

Change return type from `ResponseEntity<Map<String, Object>>` to `ResponseEntity<RoleArtifactResponse>`. Use `RoleArtifactResponse.from(artifact)`.

- [ ] **Step 3: Run tests**

Run: `cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew :service:service-agent:test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/RoleArtifactResponse.java
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/PipelineController.java
git commit -m "feat(pipeline): add RoleArtifactResponse DTO, replace raw Map return"
```

### Task 4: Add skip-roles endpoint for live pipeline steering

**Files:**
- Modify: `tacticl-core/service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/PipelineController.java`
- Modify: `tacticl-core/business/business-agent/` — PdlcPipelineOrchestrator or PipelineStateManager (add skipRoles method)

- [ ] **Step 1: Add PUT endpoint**

```java
@PutMapping("/v1/sparks/{sparkId}/pipeline/skip-roles")
@RequireAuth
public ResponseEntity<PipelineRunResponse> updateSkippedRoles(
        @PathVariable String sparkId,
        @RequestBody List<PdlcRole> skipRoles,
        AuthenticatedUser user) {
    // Validate ownership, update skipped roles on running pipeline
    // Only roles not yet started can be skipped
    var run = pipelineService.updateSkippedRoles(sparkId, skipRoles, user.getUserId());
    return ResponseEntity.ok(PipelineRunResponse.from(run));
}
```

- [ ] **Step 2: Implement business logic**

In the appropriate service (PipelineStateManager or PdlcPipelineOrchestrator), add method that:
- Validates pipeline is still EXECUTING
- Filters skipRoles to only include roles not yet started (status PENDING)
- Updates `skippedRequiredRoles` on the PipelineRun entity
- Emits ROLE_SKIPPED event for each newly skipped role
- Returns updated PipelineRun

- [ ] **Step 3: Write test**

Test that skipping a PENDING role succeeds, skipping a COMPLETED role is rejected, and skipping on a non-EXECUTING pipeline returns 409.

- [ ] **Step 4: Run tests**

Run: `cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew :service:service-agent:test :business:business-agent:test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(pipeline): add PUT /v1/sparks/{sparkId}/pipeline/skip-roles endpoint"
```

---

## Chunk 2: TypeScript Types + API Layer (tacticl-web)

Foundation for all UI work. Types, API functions, and hooks.

### Task 5: Add pipeline types to types.ts

**Files:**
- Modify: `tacticl-web/src/api/types.ts`

- [ ] **Step 1: Add pipeline types at end of file**

```typescript
// --- PDLC Pipeline Types ---

export type PipelineTier = 'SIMPLE' | 'PLAYBOOK' | 'FULL_PDLC';

export type PipelineStatus =
  | 'CREATED' | 'CLASSIFYING' | 'AWAITING_CONFIRMATION'
  | 'EXECUTING' | 'CHECKPOINT'
  | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type PdlcRole =
  | 'PM' | 'RESEARCHER' | 'ARCHITECT' | 'DESIGNER' | 'PLANNER'
  | 'IMPLEMENTER' | 'REVIEWER' | 'TESTER' | 'SECURITY_ANALYST'
  | 'TECHNICAL_WRITER' | 'DEVOPS' | 'RETRO_ANALYST';

export type RoleStatus =
  | 'PENDING' | 'EXECUTING' | 'COMPLETED' | 'REJECTED' | 'REWORKING'
  | 'FAILED' | 'ESCALATED' | 'SKIPPED' | 'AWAITING_APPROVAL';

export type PipelineEventType =
  | 'PIPELINE_STARTED' | 'PIPELINE_COMPLETED' | 'PIPELINE_FAILED'
  | 'PIPELINE_CANCELLED' | 'PIPELINE_RESUMED'
  | 'ROLE_STARTED' | 'ROLE_COMPLETED' | 'ROLE_REJECTED' | 'ROLE_SKIPPED'
  | 'REWORK_TRIGGERED' | 'REWORK_COMPLETED' | 'REWORK_ESCALATED'
  | 'ARTIFACT_PRODUCED'
  | 'CHECKPOINT_REQUESTED' | 'CHECKPOINT_RESOLVED' | 'CHECKPOINT_TIMEOUT_REMINDER'
  | 'PARALLEL_ROLES_STARTED'
  | 'COST_THRESHOLD_WARNING' | 'COST_CEILING_REACHED';

export interface RoleResultSummary {
  childSparkId: string | null;
  status: RoleStatus;
  artifactId: string | null;
  iteration: number;
  tokens: number;
  cost: number;
  durationMs: number;
  model: string;
  engine: string | null;
}

export interface PipelineRun {
  id: string;
  sparkId: string;
  playbook: string;
  pipelineTier: PipelineTier;
  status: PipelineStatus;
  activatedRoles: PdlcRole[];
  currentRole: PdlcRole | null;
  roleResults: Record<string, RoleResultSummary>;
  skippedRequiredRoles: string[];
  reworkCount: number;
  totalTokens: number;
  totalCost: number;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface PipelineEvent {
  id: string;
  pipelineRunId: string;
  eventType: PipelineEventType;
  role: PdlcRole | null;
  roleIteration: number;
  metadata: Record<string, unknown>;
  timestamp: string;
}

export interface RoleArtifact {
  id: string;
  role: PdlcRole;
  artifactType: string;
  content: Record<string, unknown>;
  artifactVersion: number;
  createdAt: string;
}

export interface CheckpointResolution {
  decision: 'APPROVED' | 'REJECTED' | 'MODIFIED';
  feedback: string | null;
}

export interface Playbook {
  name: string;
  displayName: string;
  description: string;
  tier: PipelineTier;
  stages: string[];
  isSystemPlaybook: boolean;
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/api/types.ts
git commit -m "feat: add PDLC pipeline TypeScript types"
```

### Task 6: Create pipeline API functions

**Files:**
- Create: `tacticl-web/src/api/pipeline.ts`

- [ ] **Step 1: Create pipeline API module**

**Important:** The web API client exports `api` (not `apiClient`), and `api.get()` takes only a path string — query params must be appended manually to the URL.

```typescript
import { api } from './client';
import type {
  PipelineRun,
  PipelineEvent,
  RoleArtifact,
  CheckpointResolution,
  Playbook,
  PdlcRole,
} from './types';

export const pipelineApi = {
  getRun: (sparkId: string) =>
    api.get<PipelineRun>(`/v1/sparks/${sparkId}/pipeline`),

  getEvents: (sparkId: string, params?: { limit?: number; offset?: number }) => {
    const qs = new URLSearchParams();
    if (params?.limit) qs.set('limit', String(params.limit));
    if (params?.offset) qs.set('offset', String(params.offset));
    const query = qs.toString();
    return api.get<PipelineEvent[]>(`/v1/sparks/${sparkId}/pipeline/events${query ? `?${query}` : ''}`);
  },

  getArtifact: (sparkId: string, role: PdlcRole) =>
    api.get<RoleArtifact>(`/v1/sparks/${sparkId}/pipeline/artifacts/${role}`),

  resolveCheckpoint: (sparkId: string, checkpointId: string, data: CheckpointResolution) =>
    api.post(`/v1/sparks/${sparkId}/pipeline/checkpoint/${checkpointId}`, data),

  updateSkippedRoles: (sparkId: string, skipRoles: PdlcRole[]) =>
    api.put<PipelineRun>(`/v1/sparks/${sparkId}/pipeline/skip-roles`, skipRoles),

  getPlaybooks: () =>
    api.get<Playbook[]>('/v1/playbooks'),
};
```

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/api/pipeline.ts
git commit -m "feat: add pipeline API functions"
```

### Task 7: Create pipeline React Query hooks

**Files:**
- Create: `tacticl-web/src/hooks/usePipeline.ts`

- [ ] **Step 1: Create hooks**

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { pipelineApi } from '../api/pipeline';
import type { PdlcRole, CheckpointResolution, PipelineRun } from '../api/types';

const ACTIVE_STATUSES = ['CREATED', 'CLASSIFYING', 'AWAITING_CONFIRMATION', 'EXECUTING', 'CHECKPOINT'];

function isActive(run: PipelineRun | undefined): boolean {
  return run != null && ACTIVE_STATUSES.includes(run.status);
}

export function usePipelineRun(sparkId: string | undefined) {
  return useQuery({
    queryKey: ['pipeline-run', sparkId],
    queryFn: () => pipelineApi.getRun(sparkId!),
    enabled: !!sparkId,
    refetchInterval: (query) => isActive(query.state.data) ? 5_000 : false,
  });
}

export function usePipelineEvents(sparkId: string | undefined, isActive: boolean = false) {
  return useQuery({
    queryKey: ['pipeline-events', sparkId],
    queryFn: () => pipelineApi.getEvents(sparkId!, { limit: 100 }),
    enabled: !!sparkId,
    refetchInterval: isActive ? 5_000 : false,
  });
}

export function useRoleArtifact(sparkId: string | undefined, role: PdlcRole | null) {
  return useQuery({
    queryKey: ['role-artifact', sparkId, role],
    queryFn: () => pipelineApi.getArtifact(sparkId!, role!),
    enabled: !!sparkId && !!role,
  });
}

export function usePlaybooks() {
  return useQuery({
    queryKey: ['playbooks'],
    queryFn: () => pipelineApi.getPlaybooks(),
    staleTime: 60_000,
  });
}

export function useResolveCheckpoint(sparkId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ checkpointId, data }: { checkpointId: string; data: CheckpointResolution }) =>
      pipelineApi.resolveCheckpoint(sparkId, checkpointId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pipeline-run', sparkId] });
      queryClient.invalidateQueries({ queryKey: ['pipeline-events', sparkId] });
      queryClient.invalidateQueries({ queryKey: ['checkpoints'] });
    },
  });
}

export function useUpdateSkippedRoles(sparkId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (skipRoles: PdlcRole[]) =>
      pipelineApi.updateSkippedRoles(sparkId, skipRoles),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pipeline-run', sparkId] });
    },
  });
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/hooks/usePipeline.ts
git commit -m "feat: add pipeline React Query hooks with smart polling"
```

### Task 8: Extend WebSocket handler for pipeline events

**Files:**
- Modify: `tacticl-web/src/lib/websocket.ts` — add `pipeline_event` to `SparkWebSocketMessage` union type
- Modify: `tacticl-web/src/hooks/useWebSocket.ts` — add handler in `handleMessage` switch (this is where message switching lives, NOT `websocket.ts`)

- [ ] **Step 1: Add pipeline_event to SparkWebSocketMessage union type in websocket.ts**

In `src/lib/websocket.ts`, add to the `SparkWebSocketMessage` discriminated union:

```typescript
| { type: 'pipeline_event'; sparkId: string; eventType: string; role?: string; status?: string; pipelineRunId?: string; metadata?: Record<string, unknown> }
```

- [ ] **Step 2: Add handler in useWebSocket.ts handleMessage switch**

In `src/hooks/useWebSocket.ts`, add a new case in the `handleMessage` switch:

```typescript
case 'pipeline_event': {
  const { sparkId, eventType, role } = msg;
  // Push to progress store — addProgress takes (sparkId, msgData) as two args
  useSparkProgressStore.getState().addProgress(sparkId, {
    message: `[${role ?? 'pipeline'}] ${eventType}`,
    type: 'progress',
  });
  // Invalidate React Query caches on terminal events
  const terminalEvents = ['PIPELINE_COMPLETED', 'PIPELINE_FAILED', 'PIPELINE_CANCELLED'];
  if (terminalEvents.includes(eventType)) {
    queryClient.invalidateQueries({ queryKey: ['pipeline-run', sparkId] });
    queryClient.invalidateQueries({ queryKey: ['pipeline-events', sparkId] });
    queryClient.invalidateQueries({ queryKey: ['sparks'] });
  }
  break;
}
```

Note: Check how `useWebSocket.ts` accesses `queryClient` — it likely receives it as a parameter or uses `useQueryClient()`. Follow the existing pattern for other message types.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/lib/websocket.ts src/hooks/useSparkProgress.ts
git commit -m "feat: handle pipeline_event WebSocket messages"
```

---

## Chunk 3: Enhanced Spark List (tacticl-web)

Upgrade SparkListPage with inline pipeline indicators.

### Task 9: Create RoleProgressBar component

**Files:**
- Create: `tacticl-web/src/components/sparks/RoleProgressBar.tsx`

- [ ] **Step 1: Create the component**

Small horizontal bar of colored segments, one per activated role. Props:

```typescript
interface RoleProgressBarProps {
  activatedRoles: PdlcRole[];
  roleResults: Record<string, RoleResultSummary>;
  currentRole: PdlcRole | null;
}
```

Each segment:
- Completed (`status === 'COMPLETED'`): `#4CAF50` (green)
- Active (matches `currentRole`): `#6C63FF` (purple) with subtle glow
- Failed/Rejected: `#CF6679` (red)
- Skipped: `#555` with strikethrough pattern
- Pending: `#333` (dark gray)

Use MUI `Box` with `display: flex`, each segment a small `Box` with `height: 4px`, `flex: 1`, `borderRadius: 1px`.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/RoleProgressBar.tsx
git commit -m "feat: add RoleProgressBar component for inline pipeline progress"
```

### Task 10: Create SparkExecutionSummary component

**Files:**
- Create: `tacticl-web/src/components/sparks/SparkExecutionSummary.tsx`

- [ ] **Step 1: Create the component**

Simple text line for SIMPLE tier sparks. Props:

```typescript
interface SparkExecutionSummaryProps {
  totalTokens: number;
  estimatedCost: number;
}
```

Renders: `"3.2K tokens · $0.04"` — format tokens with K/M suffix, cost with 2 decimal places.

Use MUI `Typography` variant `caption`, color `text.secondary`.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/SparkExecutionSummary.tsx
git commit -m "feat: add SparkExecutionSummary for simple spark rows"
```

### Task 11: Integrate into SparkRow and SparkListPage

**Files:**
- Modify: `tacticl-web/src/components/sparks/SparkRow.tsx`
- Modify: `tacticl-web/src/pages/SparkListPage.tsx`

- [ ] **Step 1: Add pipeline data fetch to SparkRow**

For each spark, conditionally call `usePipelineRun(spark.id)` when `spark.type === 'code' || spark.type === 'devops'` (types that can trigger PDLC). Show `RoleProgressBar` if pipeline data exists (tier is PLAYBOOK or FULL_PDLC), otherwise show `SparkExecutionSummary`.

**Performance note:** Only call `usePipelineRun` for sparks with ACTIVE status (EXECUTING, CHECKPOINT, ROUTING). For completed/failed sparks, fetch once without polling (pass `enabled` conditionally). This prevents N concurrent polling queries when the list has many historical sparks.

Replace the existing "Tactics progress bar" column content with the appropriate component.

- [ ] **Step 2: Add playbook label**

When pipeline data exists, show playbook name as a small chip next to the spark type badge. Example: `code · SMALL_FEATURE`.

- [ ] **Step 3: Add current role indicator**

When pipeline is active, show current role name in the status area (e.g., "IMPLEMENTER" in purple text).

- [ ] **Step 4: Make row clickable for navigation**

Add `onClick` to the row that navigates to `/sparks/${spark.id}` using React Router's `useNavigate`. Keep the expand toggle as a separate click target (stopPropagation on the expand arrow).

- [ ] **Step 5: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/SparkRow.tsx src/pages/SparkListPage.tsx
git commit -m "feat: integrate pipeline progress, playbook labels, and navigation into SparkRow"
```

---

## Chunk 4: Pipeline-Agnostic Detail Page Shell (tacticl-web)

The container that renders header, KPI strip, and delegates to pipeline-specific visualizations.

### Task 12: Wire /sparks/:id route

**Files:**
- Modify: `tacticl-web/src/App.tsx`

- [ ] **Step 1: Add route**

Add inside the protected routes:

```tsx
<Route path="/sparks/:id" element={<SparkDetailPage />} />
```

Import `SparkDetailPage` from `../pages/SparkDetailPage`.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/App.tsx
git commit -m "feat: wire /sparks/:id route"
```

### Task 13: Create KpiStrip component

**Files:**
- Create: `tacticl-web/src/components/sparks/KpiStrip.tsx`

- [ ] **Step 1: Create the component**

Horizontal row of metric cards. Props:

```typescript
interface KpiStripProps {
  metrics: Array<{
    label: string;
    value: string;
    color?: string;
  }>;
}
```

Pipeline sparks pass 5 metrics (Roles Done, Cost, Tokens, Elapsed, Reworks). Simple sparks pass 3 (Cost, Tokens, Elapsed).

Use MUI `Box` with `display: flex`, each metric in a `Box` with `flex: 1`, `textAlign: center`, `padding: '12px 16px'`. Value in large text, label in small uppercase.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/KpiStrip.tsx
git commit -m "feat: add KpiStrip component for spark detail metrics"
```

### Task 14: Rebuild SparkDetailPage as tier-aware container

**Files:**
- Modify: `tacticl-web/src/pages/SparkDetailPage.tsx`

- [ ] **Step 1: Restructure as tier-aware container**

The page fetches `useSpark(id)` and `usePipelineRun(id)`. Based on whether pipeline data exists and its tier:

```tsx
function SparkDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data: spark } = useSpark(id!);       // id is string|undefined from useParams; early return below
  const { data: pipelineRun } = usePipelineRun(id);

  if (!spark) return <LoadingState />;

  // Pipeline-agnostic: check if a pipeline exists, then delegate to type-specific view.
  // Future pipeline types add a new branch here (e.g., 'cicd', 'etl').
  // The shell only knows about header + KPI strip — views provide their own metrics.
  const hasPipeline = !!pipelineRun;

  return (
    <Box>
      <SparkDetailHeader spark={spark} pipelineRun={pipelineRun} />
      {hasPipeline ? (
        <PipelineDetailView sparkId={spark.id} pipelineRun={pipelineRun!} />
      ) : (
        <SimpleSparkView sparkId={spark.id} />
      )}
    </Box>
  );
}

// PipelineDetailView delegates to the right pipeline-type view.
// Currently only PDLC exists. When a second pipeline type is added,
// add a discriminator field to PipelineRun and branch here.
function PipelineDetailView({ sparkId, pipelineRun }: { sparkId: string; pipelineRun: PipelineRun }) {
  return (
    <>
      <KpiStrip metrics={buildPdlcMetrics(pipelineRun)} />
      <PdlcPipelineView sparkId={sparkId} pipelineRun={pipelineRun} />
    </>
  );
}
```

This is the pipeline-agnostic shell. Each pipeline view provides its own KPI metrics via a dedicated helper. `SimpleSparkView` renders its own 3-metric KPI strip internally. Future pipeline types add a new branch in `PipelineDetailView`.

- [ ] **Step 2: Create SparkDetailHeader sub-component**

Inline component (or extract to file if large). Shows breadcrumb, title, type/playbook/priority badges, elapsed time, cancel button.

- [ ] **Step 3: Create buildMetrics helper**

```typescript
function buildMetrics(spark: Spark, run?: PipelineRun) {
  const base = [
    { label: 'Cost', value: `$${(run?.totalCost ?? spark.estimatedCost).toFixed(2)}`, color: '#03DAC6' },
    { label: 'Tokens', value: formatTokens(run?.totalTokens ?? spark.totalTokens) },
    { label: 'Elapsed', value: formatElapsed(spark.createdAt) },
  ];
  if (run && run.pipelineTier !== 'SIMPLE') {
    const completed = Object.values(run.roleResults).filter(r => r.status === 'COMPLETED').length;
    return [
      { label: 'Roles Done', value: `${completed}/${run.activatedRoles.length}` },
      ...base,
      { label: 'Reworks', value: String(run.reworkCount), color: run.reworkCount > 0 ? '#FF9800' : undefined },
    ];
  }
  return base;
}
```

- [ ] **Step 4: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/pages/SparkDetailPage.tsx src/components/sparks/KpiStrip.tsx
git commit -m "feat: rebuild SparkDetailPage as pipeline-agnostic container"
```

---

## Chunk 5: PDLC Pipeline Visualization (tacticl-web)

The PDLC-specific components that plug into the detail page shell.

### Task 15: Create PdlcRoleStrip component

**Files:**
- Create: `tacticl-web/src/components/sparks/pdlc/PdlcRoleStrip.tsx`

- [ ] **Step 1: Create the role pipeline visualization**

Horizontal row of role nodes with connectors. Props:

```typescript
interface PdlcRoleStripProps {
  activatedRoles: PdlcRole[];
  roleResults: Record<string, RoleResultSummary>;
  currentRole: PdlcRole | null;
  skippedRequiredRoles: string[];  // matches PipelineRun field name
  onRoleClick: (role: PdlcRole) => void;
}
```

Each node is a circle (40x40px) with:
- Completed: green gradient fill, checkmark or role abbreviation, duration + model + tokens below
- Active (matches currentRole): purple fill, pulse animation (`@keyframes`), elapsed timer
- Pending: dashed border, gray, no metadata
- Failed: red fill
- Skipped: gray with strikethrough text
- Rework badge: small amber pill showing iteration count if > 1

Connectors: 20px wide `Box` with 2px height, green for completed transitions, gray for pending.

Role abbreviations: `{ PM: 'PM', RESEARCHER: 'RSCH', ARCHITECT: 'ARCH', DESIGNER: 'DSGN', PLANNER: 'PLAN', IMPLEMENTER: 'IMPL', REVIEWER: 'REV', TESTER: 'TEST', SECURITY_ANALYST: 'SEC', TECHNICAL_WRITER: 'DOCS', DEVOPS: 'OPS', RETRO_ANALYST: 'RETRO' }`

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/pdlc/PdlcRoleStrip.tsx
git commit -m "feat: add PdlcRoleStrip component with role state visualization"
```

### Task 16: Create ActiveRolePanel component

**Files:**
- Create: `tacticl-web/src/components/sparks/pdlc/ActiveRolePanel.tsx`

- [ ] **Step 1: Create live progress panel**

Left column of the two-column layout. Props:

```typescript
interface ActiveRolePanelProps {
  sparkId: string;
  currentRole: PdlcRole;
  roleResult: RoleResultSummary | undefined;
}
```

Shows:
- Role name + model badge (from roleResult.model)
- Live progress log from `useSparkProgressStore` — monospace font, dark background (`#1a1a2e`), auto-scroll to bottom
- Blinking cursor on latest line
- File changes summary (parse from progress messages or metadata)

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/pdlc/ActiveRolePanel.tsx
git commit -m "feat: add ActiveRolePanel with live progress terminal"
```

### Task 17: Create EventTimeline component

**Files:**
- Create: `tacticl-web/src/components/sparks/EventTimeline.tsx`

- [ ] **Step 1: Create timeline component**

Right column. Props:

```typescript
interface EventTimelineProps {
  sparkId: string;
}
```

Fetches events via `usePipelineEvents(sparkId)`. Renders reverse-chronological list with:
- Colored dot per event type (use a color map constant)
- Event description (human-readable from eventType + role)
- Metadata line (duration, tokens, model, iteration)

Color map:
```typescript
const EVENT_COLORS: Record<string, string> = {
  PIPELINE_STARTED: '#888',
  PIPELINE_COMPLETED: '#4CAF50',
  PIPELINE_FAILED: '#CF6679',
  PIPELINE_CANCELLED: '#CF6679',
  PIPELINE_RESUMED: '#888',
  ROLE_STARTED: '#6C63FF',
  ROLE_COMPLETED: '#4CAF50',
  ROLE_REJECTED: '#FF9800',
  ROLE_SKIPPED: '#FF9800',
  REWORK_TRIGGERED: '#FF9800',
  REWORK_COMPLETED: '#FF9800',
  REWORK_ESCALATED: '#FF9800',
  CHECKPOINT_REQUESTED: '#FF9800',
  CHECKPOINT_RESOLVED: '#FF9800',
  CHECKPOINT_TIMEOUT_REMINDER: '#FF9800',
  ARTIFACT_PRODUCED: '#03DAC6',
  PARALLEL_ROLES_STARTED: '#6C63FF',
  COST_THRESHOLD_WARNING: '#CF6679',
  COST_CEILING_REACHED: '#CF6679',
};
```

Fallback color for unrecognized events: `#888`.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/EventTimeline.tsx
git commit -m "feat: add EventTimeline with color-coded pipeline events"
```

### Task 18: Create ArtifactTabs component

**Files:**
- Create: `tacticl-web/src/components/sparks/pdlc/ArtifactTabs.tsx`

- [ ] **Step 1: Create tabbed artifact viewer**

Props:

```typescript
interface ArtifactTabsProps {
  sparkId: string;
  activatedRoles: PdlcRole[];
  roleResults: Record<string, RoleResultSummary>;
}
```

MUI `Tabs` with one tab per activated role. Completed roles are clickable, active shows "In progress...", pending roles are disabled (grayed out).

On tab click, fetch artifact via `useRoleArtifact(sparkId, selectedRole)`. Render content in a monospace container (`#1a1a2e` background). Show metadata: model, tokens, iteration, artifactType.

Content rendering: if `artifactType` is CODE, show file changes and PR link from content map. Otherwise, render values as formatted text/JSON.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/pdlc/ArtifactTabs.tsx
git commit -m "feat: add ArtifactTabs for per-role artifact viewing"
```

### Task 19: Create PdlcPipelineView container

**Files:**
- Create: `tacticl-web/src/components/sparks/pdlc/PdlcPipelineView.tsx`

- [ ] **Step 1: Compose all PDLC components**

```typescript
interface PdlcPipelineViewProps {
  sparkId: string;
  pipelineRun: PipelineRun;
}
```

Layout:
1. `PdlcRoleStrip` — full width
2. Checkpoint slot — renders `CheckpointBanner` if status === 'CHECKPOINT' (use lazy import or stub placeholder until Task 20 creates it; the component is optional at render time since most pipeline views won't be in CHECKPOINT state during dev)
3. Two-column `Box` (flex): `ActiveRolePanel` (flex: 1.2) | `EventTimeline` (flex: 0.8)
4. Controls slot — renders `PdlcPipelineControls` when Task 21 creates it (collapsible, optional)
5. `ArtifactTabs` — full width

State: `selectedRole` for artifact tabs (clicking a role node in strip selects it).

**Note:** This task creates the layout with placeholder slots for CheckpointBanner (Task 20) and PdlcPipelineControls (Task 21). Use conditional imports or simple `{CheckpointBanner && <CheckpointBanner ... />}` patterns so the component compiles without those dependencies existing yet.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/pdlc/PdlcPipelineView.tsx
git commit -m "feat: add PdlcPipelineView container composing all PDLC components"
```

---

## Chunk 6: Steering Controls (tacticl-web)

Checkpoint, role skipping, cost bar.

### Task 20: Create CheckpointBanner component

**Files:**
- Create: `tacticl-web/src/components/sparks/CheckpointBanner.tsx`

- [ ] **Step 1: Create checkpoint approval banner**

Props:

```typescript
interface CheckpointBannerProps {
  sparkId: string;
  checkpoint: Checkpoint;
}
```

Amber left border card. Shows:
- Title + description
- Findings list (severity color-coded)
- Feedback `TextField` (multiline)
- Three `Button`s: Approve (green), Rework (amber outlined), Reject (red outlined)

**Button → decision mapping:** Approve sends `decision: 'APPROVED'`, Rework sends `decision: 'MODIFIED'` (triggers rework loop back to a prior role), Reject sends `decision: 'REJECTED'` (fails the pipeline).

Uses `useResolveCheckpoint(sparkId)` mutation. On success, banner disappears (pipeline status changes from CHECKPOINT).

Handle errors: 409 (already resolved) shows snackbar "Checkpoint already resolved", 403 shows "Not authorized".

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/CheckpointBanner.tsx
git commit -m "feat: add CheckpointBanner with approve/rework/reject actions"
```

### Task 21: Create PdlcPipelineControls component

**Files:**
- Create: `tacticl-web/src/components/sparks/pdlc/PdlcPipelineControls.tsx`

- [ ] **Step 1: Create collapsible controls section**

Props:

```typescript
interface PdlcPipelineControlsProps {
  sparkId: string;
  pipelineRun: PipelineRun;
}
```

Collapsible section (MUI `Accordion` or `Collapse` with toggle):

**Role skip toggles:**
- Chip for each role in `activatedRoles` that hasn't started yet (status PENDING in roleResults)
- Clicking a chip toggles it as skipped (strikethrough + dimmed)
- "Apply" button calls `useUpdateSkippedRoles` mutation with updated list
- Already-completed/active roles are not toggleable

**Cost ceiling bar:**
- Fetch user's cost ceiling from settings (or show default $50)
- `LinearProgress` (MUI) showing `totalCost / ceiling` percentage
- Color: teal (<50%), amber (50-80%), red (>80%)
- Label: `$2.14 / $50.00`

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/pdlc/PdlcPipelineControls.tsx
git commit -m "feat: add PdlcPipelineControls with role skip toggles and cost bar"
```

---

## Chunk 7: Simple Spark Detail View (tacticl-web)

### Task 22: Create ExecutionLog component

**Files:**
- Create: `tacticl-web/src/components/sparks/ExecutionLog.tsx`

- [ ] **Step 1: Create execution log viewer**

Props:

```typescript
interface ExecutionLogProps {
  sparkId: string;
}
```

Fetches logs via `useSparkLogs(sparkId)`. Renders chronological list of tool calls:
- Each entry: tool name (bold), duration, token count
- Expandable accordion for input/output JSON (collapsed by default)
- Chat-transcript style: alternating background for agent reasoning vs tool calls

Uses MUI `Accordion`/`AccordionSummary`/`AccordionDetails` for each log entry. Monospace font for JSON content.

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/ExecutionLog.tsx
git commit -m "feat: add ExecutionLog component for simple spark detail"
```

### Task 23: Create SimpleSparkView container

**Files:**
- Create: `tacticl-web/src/components/sparks/SimpleSparkView.tsx`

- [ ] **Step 1: Compose simple spark view**

```typescript
interface SimpleSparkViewProps {
  sparkId: string;
}
```

Layout:
1. `ExecutionLog` — main content
2. Spark Result card (existing component) — when spark is completed
3. Live Activity panel (reuse from existing SparkRow expanded view) — when spark is active

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add src/components/sparks/SimpleSparkView.tsx
git commit -m "feat: add SimpleSparkView container for non-pipeline sparks"
```

---

## Chunk 8: Mobile Adaptation (tacticl-mobile)

### Task 24: Add pipeline types and API to tacticl-mobile

**Files:**
- Modify: `tacticl-mobile/src/api/types.ts`
- Create: `tacticl-mobile/src/api/pipeline.ts`
- Create: `tacticl-mobile/src/hooks/usePipeline.ts`

- [ ] **Step 1: Copy pipeline types from tacticl-web**

Add the same `PipelineRun`, `PipelineEvent`, `RoleArtifact`, `CheckpointResolution`, etc. types to mobile's `types.ts`.

- [ ] **Step 2: Create pipeline API module**

Same structure as web's `pipeline.ts`, adapted to mobile's API client pattern.

- [ ] **Step 3: Create pipeline hooks**

Same hooks as web: `usePipelineRun`, `usePipelineEvents`, `useRoleArtifact`, `useResolveCheckpoint`, `useUpdateSkippedRoles`.

- [ ] **Step 4: Add pipeline_event to WebSocket handler**

Extend `tacticl-mobile/src/lib/websocket.ts` to handle `pipeline_event` message type, push to activity store.

- [ ] **Step 5: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-mobile
git add src/api/types.ts src/api/pipeline.ts src/hooks/usePipeline.ts src/lib/websocket.ts
git commit -m "feat: add pipeline types, API, hooks, and WebSocket handler"
```

### Task 25: Migrate asks → sparks terminology in mobile

**Files:**
- Modify: `tacticl-mobile/src/stores/activity-store.ts`
- Modify: `tacticl-mobile/src/api/activity.ts`
- Modify: `tacticl-mobile/src/hooks/useActivity.ts`
- Modify: `tacticl-mobile/app/(tabs)/index.tsx`

- [ ] **Step 1: Rename types and store**

Rename `ActivityAsk` → `ActivitySpark`, `askState` → `sparkStatus`, `askId` → `sparkId` throughout. Update API endpoint from `/v1/agent/activity` to `/v1/sparks` (or whichever endpoint the backend now uses for the activity dashboard — check `SparkController.getActivity()`).

- [ ] **Step 2: Update dashboard screen**

Update `app/(tabs)/index.tsx` to use new type names. Add `RoleProgressBar` to active spark cards (React Native version using `View` with `flexDirection: 'row'` instead of MUI `Box`).

- [ ] **Step 3: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-mobile
git add -A
git commit -m "feat: migrate asks to sparks terminology, add pipeline progress to dashboard"
```

### Task 26: Create Spark Detail Screen for mobile

**Files:**
- Create: `tacticl-mobile/app/spark/[id].tsx`
- Create: `tacticl-mobile/src/components/PdlcRoleStrip.tsx`
- Create: `tacticl-mobile/src/components/ActiveRolePanel.tsx`
- Create: `tacticl-mobile/src/components/EventTimeline.tsx`
- Create: `tacticl-mobile/src/components/CheckpointBanner.tsx`
- Create: `tacticl-mobile/src/components/ArtifactSheet.tsx`

- [ ] **Step 1: Create spark detail screen**

`app/spark/[id].tsx` — Expo Router file-based route. Fetches spark + pipeline data.

Layout (ScrollView):
1. Header: title, type/playbook badges, back button
2. KPI grid: 2x3 grid of metric boxes (React Native `View` with `flexDirection: 'row', flexWrap: 'wrap'`)
3. Role strip: horizontal `ScrollView` with role nodes (same visual as web, React Native `View` circles)
4. Checkpoint banner: full-width card at top when status is CHECKPOINT
5. Active role panel: live progress (monospace `Text`, auto-scroll `ScrollView`)
6. Event timeline: collapsible accordion (starts collapsed to save screen space)

- [ ] **Step 2: Create PdlcRoleStrip (React Native)**

Horizontal `ScrollView` with role circle `View`s. Same state logic as web (green/purple/gray/red). Use `Animated` for pulse effect on active role.

- [ ] **Step 3: Create ArtifactSheet**

React Native `BottomSheet` (or modal). Triggered by tapping a completed role node. Shows artifact content.

- [ ] **Step 4: Create remaining components**

Port `ActiveRolePanel`, `EventTimeline`, `CheckpointBanner` from web to React Native equivalents. Replace MUI components with RN primitives (`View`, `Text`, `Pressable`, `TextInput`).

- [ ] **Step 5: Wire navigation**

In dashboard, add `onPress={() => router.push(`/spark/${spark.id}`)}` to spark cards.

- [ ] **Step 6: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-mobile
git add -A
git commit -m "feat: add spark detail screen with PDLC pipeline visualization"
```

---

## Task Dependency Summary

```
Chunk 1 (backend DTOs) → Chunk 2 (types + API) → Chunk 3 (enhanced list)
                                                 → Chunk 4 (detail shell)
                                                 → Chunk 5 (PDLC components)  → Chunk 6 (steering)
                                                 → Chunk 7 (simple spark view)
Chunks 2-7 complete → Chunk 8 (mobile)
```

Chunks 3, 4, 5, 7 can be parallelized after Chunk 2 completes. Chunk 6 depends on Chunk 5. Chunk 8 depends on all web chunks.

## Parallelization Strategy

For subagent-driven execution:

**Wave 1:** Tasks 1-4 (backend DTO fixes) — 1 agent, sequential in tacticl-core
**Wave 2:** Tasks 5-8 (types + API + hooks + WebSocket) — 1 agent, sequential in tacticl-web
**Wave 3:** Tasks 9-11, 12-13, 15-19, 22-23 (4 parallel agents):
  - Agent A: Enhanced Spark List (Tasks 9-11)
  - Agent B: Route + KpiStrip (Tasks 12-13 only — Task 14 depends on SimpleSparkView)
  - Agent C: PDLC Pipeline Components (Tasks 15-19)
  - Agent D: Simple Spark View (Tasks 22-23)
**Wave 3b:** Task 14 (1 agent) — after Agent B + D complete:
  - Rebuild SparkDetailPage as tier-aware container (needs both PdlcPipelineView and SimpleSparkView)
**Wave 4:** Tasks 20-21 (Steering Controls) — 1 agent, after Wave 3
**Wave 5:** Tasks 24-26 (Mobile) — 1-2 agents, after all web work
