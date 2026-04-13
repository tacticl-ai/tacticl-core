# PDLC Artifact Taxonomy Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename all tacticl-docs PDLC files to the approved naming convention, add YAML frontmatter + standardized H1 titles to all .md files, update all PRDs with artifact taxonomy content, and overhaul the viewer HTML.

**Architecture:** All changes are in the `tacticl-docs` repo at `/Users/cuztomizer/Documents/GitHub/tacticl-docs/architecture/pdlc/`. File renames use `git mv` to preserve history. PRD updates are additive sections. Viewer HTML gets a full structural overhaul to reflect the new naming and embed the SAD.

**Tech Stack:** Git (file renames), Markdown (YAML frontmatter + PRD sections), HTML/JS/CSS (viewer overhaul)

---

## Chunk 1: File Renames

### Task 1: Rename all PDLC files with git mv

**Files:**
- Rename: `tacticl-docs/architecture/pdlc/review.html` → `tacticl-pdlc-design-docs.html`
- Rename: `tacticl-docs/architecture/pdlc/overview-prd.md` → `2026-04-12-tacticl-pdlc-overview-product-requirements.md`
- Rename: `tacticl-docs/architecture/pdlc/phase-1-product-prd.md` → `2026-04-12-tacticl-pdlc-phase-1-product-requirements.md`
- Rename: `tacticl-docs/architecture/pdlc/phase-2-design-prd.md` → `2026-04-12-tacticl-pdlc-phase-2-design-requirements.md`
- Rename: `tacticl-docs/architecture/pdlc/phase-3-development-prd.md` → `2026-04-12-tacticl-pdlc-phase-3-development-requirements.md`
- Rename: `tacticl-docs/architecture/pdlc/phase-4-test-prd.md` → `2026-04-12-tacticl-pdlc-phase-4-quality-requirements.md`
- Rename: `tacticl-docs/architecture/pdlc/phase-5-deploy-prd.md` → `2026-04-12-tacticl-pdlc-phase-5-deployment-requirements.md`
- Rename: `tacticl-docs/architecture/pdlc/phase-6-retro-prd.md` → `2026-04-12-tacticl-pdlc-phase-6-retrospective-requirements.md`
- Rename: `tacticl-docs/architecture/pdlc/dashboard-prd.md` → `2026-04-12-tacticl-pdlc-dashboard-requirements.md`
- Rename: `tacticl-docs/architecture/pdlc/pipeline-architecture-sad.md` → `2026-04-12-tacticl-pdlc-pipeline-architecture.md`
- Rename: `tacticl-docs/architecture/pdlc/mockups/pipeline-dashboard-user.html` → `mockups/2026-04-12-tacticl-pdlc-mockup-dashboard-user.html`
- Rename: `tacticl-docs/architecture/pdlc/mockups/pipeline-dashboard-admin.html` → `mockups/2026-04-12-tacticl-pdlc-mockup-dashboard-admin.html`

- [ ] **Step 1: Run all git mv commands**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-docs/architecture/pdlc

git mv review.html tacticl-pdlc-design-docs.html
git mv overview-prd.md 2026-04-12-tacticl-pdlc-overview-product-requirements.md
git mv phase-1-product-prd.md 2026-04-12-tacticl-pdlc-phase-1-product-requirements.md
git mv phase-2-design-prd.md 2026-04-12-tacticl-pdlc-phase-2-design-requirements.md
git mv phase-3-development-prd.md 2026-04-12-tacticl-pdlc-phase-3-development-requirements.md
git mv phase-4-test-prd.md 2026-04-12-tacticl-pdlc-phase-4-quality-requirements.md
git mv phase-5-deploy-prd.md 2026-04-12-tacticl-pdlc-phase-5-deployment-requirements.md
git mv phase-6-retro-prd.md 2026-04-12-tacticl-pdlc-phase-6-retrospective-requirements.md
git mv dashboard-prd.md 2026-04-12-tacticl-pdlc-dashboard-requirements.md
git mv pipeline-architecture-sad.md 2026-04-12-tacticl-pdlc-pipeline-architecture.md
git mv mockups/pipeline-dashboard-user.html mockups/2026-04-12-tacticl-pdlc-mockup-dashboard-user.html
git mv mockups/pipeline-dashboard-admin.html mockups/2026-04-12-tacticl-pdlc-mockup-dashboard-admin.html
```

Expected: 12 renames, all staged.

- [ ] **Step 2: Verify renames**

```bash
git status
```

Expected: 12 `renamed:` entries in staged changes.

- [ ] **Step 3: Commit renames**

```bash
git commit -m "rename: apply artifact taxonomy naming convention to all PDLC docs"
```

---

## Chunk 2: Frontmatter + H1 Titles

### Task 2: Add YAML frontmatter and standardized H1 to all 9 .md files

Each file gets a YAML frontmatter block inserted at the top and its existing H1 replaced with the standard pattern:
`# Tacticl PDLC · {Document Role} · {Scope} — YYYY-MM-DD`

**Files to update (after rename):**
- `2026-04-12-tacticl-pdlc-overview-product-requirements.md`
- `2026-04-12-tacticl-pdlc-phase-1-product-requirements.md`
- `2026-04-12-tacticl-pdlc-phase-2-design-requirements.md`
- `2026-04-12-tacticl-pdlc-phase-3-development-requirements.md`
- `2026-04-12-tacticl-pdlc-phase-4-quality-requirements.md`
- `2026-04-12-tacticl-pdlc-phase-5-deployment-requirements.md`
- `2026-04-12-tacticl-pdlc-phase-6-retrospective-requirements.md`
- `2026-04-12-tacticl-pdlc-dashboard-requirements.md`
- `2026-04-12-tacticl-pdlc-pipeline-architecture.md`

- [ ] **Step 4: Add frontmatter + update H1 in overview-product-requirements.md**

Replace the existing H1 and prepend frontmatter:

Old H1: `# Tacticl PDLC v2 — Pipeline Overview`

New content at top of file:
```
---
product: tacticl
type: engineering-spec
scope: pdlc-overview
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Product Requirements · Pipeline Overview — 2026-04-12
```

- [ ] **Step 5: Add frontmatter + update H1 in phase-1-product-requirements.md**

Old H1: `# Tacticl PDLC v2 — Phase 1: Product`

New:
```
---
product: tacticl
type: engineering-spec
scope: phase-1-product
phase: 1
phase-name: Product
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Product Requirements · Phase 1 Product — 2026-04-12
```

- [ ] **Step 6: Add frontmatter + update H1 in phase-2-design-requirements.md**

Old H1: `# Tacticl PDLC v2 — Phase 2: Design`

New:
```
---
product: tacticl
type: engineering-spec
scope: phase-2-design
phase: 2
phase-name: Design
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Product Requirements · Phase 2 Design — 2026-04-12
```

- [ ] **Step 7: Add frontmatter + update H1 in phase-3-development-requirements.md**

Old H1: `# Tacticl PDLC v2 — Phase 3: Development`

New:
```
---
product: tacticl
type: engineering-spec
scope: phase-3-development
phase: 3
phase-name: Development
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Product Requirements · Phase 3 Development — 2026-04-12
```

- [ ] **Step 8: Add frontmatter + update H1 in phase-4-quality-requirements.md**

Old H1: `# Tacticl PDLC v2 — Phase 4: Test`

New:
```
---
product: tacticl
type: engineering-spec
scope: phase-4-quality
phase: 4
phase-name: Quality
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Product Requirements · Phase 4 Quality — 2026-04-12
```

- [ ] **Step 9: Add frontmatter + update H1 in phase-5-deployment-requirements.md**

Old H1: `# Tacticl PDLC v2 — Phase 5: Deploy`

New:
```
---
product: tacticl
type: engineering-spec
scope: phase-5-deployment
phase: 5
phase-name: Deployment
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Product Requirements · Phase 5 Deployment — 2026-04-12
```

- [ ] **Step 10: Add frontmatter + update H1 in phase-6-retrospective-requirements.md**

Old H1: `# Tacticl PDLC v2 — Phase 6: Retro`

New:
```
---
product: tacticl
type: engineering-spec
scope: phase-6-retrospective
phase: 6
phase-name: Retrospective
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Product Requirements · Phase 6 Retrospective — 2026-04-12
```

- [ ] **Step 11: Add frontmatter + update H1 in dashboard-requirements.md**

Old H1: `# Tacticl PDLC v2 — Dashboard PRD`

New:
```
---
product: tacticl
type: engineering-spec
scope: pdlc-dashboard
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Product Requirements · Dashboard — 2026-04-12
```

- [ ] **Step 12: Add frontmatter + update H1 in pipeline-architecture.md**

Old H1: `# Tacticl PDLC v2 — Pipeline Architecture`

New:
```
---
product: tacticl
type: engineering-spec
scope: pdlc-pipeline-architecture
status: draft
authored-by: [TEAM]
date: 2026-04-12T00:00:00Z
---

# Tacticl PDLC · Pipeline Architecture · System Design — 2026-04-12
```

- [ ] **Step 13: Commit frontmatter updates**

```bash
git add -A
git commit -m "feat: add YAML frontmatter and standardized H1 titles to all PDLC engineering specs"
```

---

## Chunk 3: PRD Content Updates

### Task 3: Add Artifact Taxonomy section to Overview PRD

**File:** `2026-04-12-tacticl-pdlc-overview-product-requirements.md`

- [ ] **Step 14: Add Artifact Taxonomy section**

Insert a new top-level section after the "Cost Visibility" section and before "Related Docs":

```markdown
---

## Artifact Taxonomy

### Two Tiers

Every phase produces exactly two tiers of artifacts:

**Tier 1 — Critical Consolidated Report** (one per phase)
The single document a user sees at the HITL checkpoint. Summarizes all role outputs for that phase in one readable doc. The user approves or requests changes based on this document alone.

**Tier 2 — Supporting Detail Artifacts** (one or more per phase)
The full output of each individual role. Linked from the Tier 1 report. Users read these only when they need depth on a specific finding.

### Naming Convention

**Tier 1 (runtime, per pipeline run):**
```
YYYY-MM-DD-tacticl-{spark-slug}-phase-{N}-{phase-name}-report.md
```

**Tier 2 (runtime, per pipeline run):**
```
YYYY-MM-DD-tacticl-{spark-slug}-phase-{N}-{artifact-name}.md
```

**Engineering specs (tacticl-docs, written by team):**
```
YYYY-MM-DD-tacticl-pdlc-{scope}-{document-role}.md
```

### YAML Frontmatter

Every artifact carries a YAML frontmatter block:

```yaml
---
product: tacticl
phase: 4
phase-name: Quality Report
spark-id: spark-abc123
spark-title: Add payment flow to checkout
pipeline-run-id: run-xyz789
type: phase-report          # phase-report | detail-artifact | engineering-spec
status: pending-review       # pending-review | approved | changes-requested | cancelled
authored-by: [TESTER, REVIEWER, SECURITY_ANALYST]
date: 2026-04-12T14:23:00Z
approved-by: null
approved-at: null
---
```

### Phase Output Summary

| Phase | Tier 1 Report | Tier 2 Artifacts |
|-------|--------------|-----------------|
| 1 — Product | `phase-1-product-report.md` | product-requirements.md, research-summary.md, mockup-{screen}.html |
| 2 — Design | `phase-2-design-report.md` | architecture.md, erd.md, screens-{name}.html, stories.json |
| 3 — Development | `phase-3-implementation-report.md` | (per-story logs embedded in report) |
| 4 — Quality | `phase-4-quality-report.md` | test-results.md, coverage-breakdown.md, code-review.md, security-audit.md |
| 5 — Deployment | `phase-5-deployment-report.md` | pr-descriptions.md, deployment-notes.md |
| 6 — Retrospective | `phase-6-retrospective-report.md` | proposed-learnings.md |
```

- [ ] **Step 15: Update Related Docs links to use new filenames**

Update the Related Docs section to use all renamed filenames:

```markdown
## Related Docs

### PRD (Behavioral Specs)
- [Phase 1: Product](2026-04-12-tacticl-pdlc-phase-1-product-requirements.md) — PM + RESEARCHER
- [Phase 2: Design](2026-04-12-tacticl-pdlc-phase-2-design-requirements.md) — ARCHITECT + DESIGNER + PLANNER
- [Phase 3: Development](2026-04-12-tacticl-pdlc-phase-3-development-requirements.md) — IMPLEMENTER + internal REVIEWER
- [Phase 4: Quality](2026-04-12-tacticl-pdlc-phase-4-quality-requirements.md) — TESTER + REVIEWER + SECURITY_ANALYST
- [Phase 5: Deployment](2026-04-12-tacticl-pdlc-phase-5-deployment-requirements.md) — TECHNICAL_WRITER + DEVOPS
- [Phase 6: Retrospective](2026-04-12-tacticl-pdlc-phase-6-retrospective-requirements.md) — RETRO_ANALYST
- [Dashboard](2026-04-12-tacticl-pdlc-dashboard-requirements.md) — user pipeline tracking + admin console

### SAD (System Architecture)
- [Pipeline Architecture](2026-04-12-tacticl-pdlc-pipeline-architecture.md) — infrastructure, orchestration, workspace protocol, knowledge system, MongoDB schemas
- Per-phase SADs (coming next)
```

### Task 4: Add Phase Output section to each per-phase PRD

- [ ] **Step 16: Add Phase Output to phase-1-product-requirements.md**

Append at end of file:

```markdown
---

## Phase Output

### Tier 1 — Product Report

**Filename:** `YYYY-MM-DD-tacticl-{spark-slug}-phase-1-product-report.md`
**Authored by:** PM + RESEARCHER (assembled by coordinator after both roles complete)

Structure:
```markdown
## Executive Summary
## Product Requirements (Summary — 5 key decisions)
## Research Findings (Summary — risks, patterns, integrations)
## Mockups
[links to .html files]
## What's Next
Phase 2 will produce: SAD, ERD, Story Breakdown
```

### Tier 2 — Supporting Artifacts

| Artifact | Filename | Authored by |
|----------|----------|-------------|
| Product Requirements Doc | `phase-1-product-requirements.md` | PM |
| Research Summary | `phase-1-research-summary.md` | RESEARCHER |
| Hi-fi Mockups | `phase-1-mockup-{screen}.html` | PM / DESIGNER |
```

- [ ] **Step 17: Add Phase Output to phase-2-design-requirements.md**

Append at end of file:

```markdown
---

## Phase Output

### Tier 1 — Design Report

**Filename:** `YYYY-MM-DD-tacticl-{spark-slug}-phase-2-design-report.md`
**Authored by:** ARCHITECT + DESIGNER + PLANNER (assembled)

Structure:
```markdown
## Executive Summary
## Architecture Decisions (key tech choices, infra, API surface)
## Data Model (ERD summary, key entities)
## User Stories ({N} stories, {M} tasks)
## Supporting Artifacts
[links to architecture.md, erd.md, screens, stories.json]
```

### Tier 2 — Supporting Artifacts

| Artifact | Filename | Authored by |
|----------|----------|-------------|
| System Architecture Doc | `phase-2-architecture.md` | ARCHITECT |
| Entity Relationship Diagram | `phase-2-erd.md` | ARCHITECT |
| Component Screens | `phase-2-screens-{name}.html` | DESIGNER |
| Story Breakdown | `phase-2-stories.json` | PLANNER |
```

- [ ] **Step 18: Add Phase Output to phase-3-development-requirements.md**

Append at end of file:

```markdown
---

## Phase Output

### Tier 1 — Implementation Report

**Filename:** `YYYY-MM-DD-tacticl-{spark-slug}-phase-3-implementation-report.md`
**Authored by:** IMPLEMENTER + internal REVIEWER

Structure:
```markdown
## Summary
{N} stories implemented across {N} branches. {N} rework iterations.
## Story-by-Story
### Story 1: {title}
Branch: feature/{spark-id}/{story-slug}
Internal review: {N} iterations. Key issues caught: ...
Acceptance criteria: all met ✓ / {N} not met ✗
### Story 2: ...
## Code Changes
[links to GitHub diff per branch]
## What's Next
Phase 4 will run: TESTER, REVIEWER, SECURITY_ANALYST in parallel
```

### Tier 2 — Supporting Artifacts

Per-story implementation logs are embedded as sections within the Tier 1 implementation report. No separate Tier 2 files for Phase 3.
```

- [ ] **Step 19: Add Phase Output to phase-4-quality-requirements.md**

Append at end of file:

```markdown
---

## Phase Output

### Tier 1 — Quality Report

**Filename:** `YYYY-MM-DD-tacticl-{spark-slug}-phase-4-quality-report.md`
**Authored by:** Assembled from TESTER + REVIEWER + SECURITY_ANALYST outputs

Structure:
```markdown
## Verdict
PASS ✓ / FAIL ✗ (with which roles blocked)
## Tests — TESTER
{N} passing, {N} failing. Coverage: {N}% (threshold: 80%)
[link to full test results + coverage breakdown]
## Code Review — REVIEWER
{verdict}. Key findings: ...
[link to full code review]
## Security — SECURITY_ANALYST
{N} CRITICAL, {N} HIGH, {N} MEDIUM, {N} LOW
[CRITICAL/HIGH findings listed inline — user must see these]
[link to full security audit]
## Rework History
Story X went through {N} rework iterations before passing.
```

### Tier 2 — Supporting Artifacts

| Artifact | Filename | Authored by |
|----------|----------|-------------|
| Test Results | `phase-4-test-results.md` | TESTER |
| Coverage Breakdown | `phase-4-coverage-breakdown.md` | TESTER |
| Code Review | `phase-4-code-review.md` | REVIEWER |
| Security Audit | `phase-4-security-audit.md` | SECURITY_ANALYST |
```

- [ ] **Step 20: Add Phase Output to phase-5-deployment-requirements.md**

Append at end of file:

```markdown
---

## Phase Output

### Tier 1 — Deployment Report

**Filename:** `YYYY-MM-DD-tacticl-{spark-slug}-phase-5-deployment-report.md`
**Authored by:** TECHNICAL_WRITER + DEVOPS

Structure:
```markdown
## Summary
{N} PRs opened. CI: PASSING ✓. Auto-merge: enabled.
## Pull Requests
[per-story PR links with CI status]
## Documentation
README updated ✓ / API docs generated ✓
## Infrastructure Changes
Migrations: {N}. Env vars added: {N}. Rollback plan: [link]
## Supporting Artifacts
[links to pr-descriptions.md, deployment-notes.md]
```

### Tier 2 — Supporting Artifacts

| Artifact | Filename | Authored by |
|----------|----------|-------------|
| PR Descriptions | `phase-5-pr-descriptions.md` | TECHNICAL_WRITER |
| Deployment Notes | `phase-5-deployment-notes.md` | DEVOPS |
```

- [ ] **Step 21: Add Phase Output to phase-6-retrospective-requirements.md**

Append at end of file:

```markdown
---

## Phase Output

### Tier 1 — Retrospective Report

**Filename:** `YYYY-MM-DD-tacticl-{spark-slug}-phase-6-retrospective-report.md`
**Authored by:** RETRO_ANALYST

Structure:
```markdown
## Pipeline Summary
Quality score: {N}/10. Total cost: ${N}. Duration: {N}h.
## What Worked Well
## What Caused Rework
## Proposed Learnings ({N} total)
[each learning with approve/reject action]
## Cost Breakdown
[per-role cost table]
```

### Tier 2 — Supporting Artifacts

| Artifact | Filename | Authored by |
|----------|----------|-------------|
| Proposed Learnings | `phase-6-proposed-learnings.md` | RETRO_ANALYST |
```

### Task 5: Add Phase Report Rendering section to Dashboard PRD

- [ ] **Step 22: Add Phase Report Rendering section to dashboard-requirements.md**

Insert a new top-level section at the end of the file, before "Related Docs":

```markdown
---

## 4. Phase Report Rendering

The Spark Dashboard renders Tier 1 phase reports in a dedicated full-page view. Users reach this view by tapping the phase entry in the HITL checkpoint banner or the phase artifact tab.

### 4.1 Report Renderer

Phase reports are Markdown files with YAML frontmatter. The dashboard uses `react-markdown` with plugins for rich rendering:

- Syntax highlighting: `react-syntax-highlighter`
- Tables: `remark-gfm`
- Callout blocks: custom `rehype` plugin mapping `> **NOTE:**` / `> **WARNING:**` patterns to styled callout components
- Severity badges: custom renderer for `CRITICAL` / `HIGH` / `MEDIUM` / `LOW` tokens in security audit reports

The YAML frontmatter block is stripped from display and used only for metadata (status chip, date, authored-by attribution).

### 4.2 Report Header

Each rendered phase report displays a metadata bar above the document body:

| Element | Source |
|---------|--------|
| Phase badge | `phase` + `phase-name` from frontmatter |
| Status chip | `status` from frontmatter (color: pending-review=amber, approved=green, changes-requested=red) |
| Authored by | `authored-by` role list from frontmatter |
| Date | `date` from frontmatter, formatted as `Apr 12, 2026` |
| Spark title | `spark-title` from frontmatter |

### 4.3 HITL Action Bar

When `status = pending-review`, a sticky action bar renders at the bottom of the report:

```
[Request Changes]    [Approve →]
```

Actions are phase-specific (see Section 3 HITL Checkpoint Screens for full action matrix per phase).

The action bar is hidden when `status` is `approved`, `changes-requested`, or `cancelled`.

### 4.4 Tier 2 Detail Navigation

Each Tier 1 report links to its Tier 2 artifacts. The dashboard renders these as a "Supporting Artifacts" panel below the report body. Each entry shows:

- Artifact name
- File type badge (`.md` / `.html` / `.json`)
- Author role badge
- "View" link that opens the artifact in a slide-over panel

`.html` artifacts (mockups, screens) open in a full-screen iframe overlay.
`.json` artifacts (stories) open in a formatted JSON viewer panel.
`.md` artifacts open in the same `react-markdown` renderer.

### 4.5 MongoDB Fields

The `pipeline_artifacts` MongoDB collection stores the `type` and `status` frontmatter fields as indexed top-level fields to support fast dashboard queries:

```
pipeline_artifacts.type    — "phase-report" | "detail-artifact" | "engineering-spec"
pipeline_artifacts.status  — "pending-review" | "approved" | "changes-requested" | "cancelled"
```

These fields are synced from frontmatter after each artifact write. Dashboard queries for "all pending HITL reports" use `{ type: "phase-report", status: "pending-review" }`.
```

- [ ] **Step 23: Commit PRD content updates**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-docs
git add architecture/pdlc/
git commit -m "feat: add artifact taxonomy sections, phase output maps, and phase report rendering to all PRDs"
```

---

## Chunk 4: Viewer HTML Overhaul

### Task 6: Fix internal links in viewer HTML

After the file renames, the viewer HTML (`tacticl-pdlc-design-docs.html`) references the old filenames in the DOCS array and in anchor tags. These need updating.

- [ ] **Step 24: Update DOCS array entries in tacticl-pdlc-design-docs.html**

The DOCS array currently references old filenames in the `src` fields and label text. Update each entry to reflect new filenames and labels. The viewer HTML embeds doc content inline via `<script type="text/plain">` blocks — these do not need file path updates. Only the metadata object fields need updating.

Update the `DOCS` array object entries to use these values:

```javascript
var DOCS = [
  // SECTION: PRODUCT REQUIREMENTS
  { key:'overview', label:'Pipeline Overview', sub:'Product Requirements · Overview', type:'overview', hitl:false, content: OVERVIEW_MD },
  { key:'phase1', label:'Phase 1 · Product', sub:'Product Requirements · Phase 1', type:'phase', phase:1, hitl:true, content: PHASE1_MD },
  { key:'phase2', label:'Phase 2 · Design', sub:'Product Requirements · Phase 2', type:'phase', phase:2, hitl:true, content: PHASE2_MD },
  { key:'phase3', label:'Phase 3 · Development', sub:'Product Requirements · Phase 3', type:'phase', phase:3, hitl:true, content: PHASE3_MD },
  { key:'phase4', label:'Phase 4 · Quality', sub:'Product Requirements · Phase 4', type:'phase', phase:4, hitl:true, content: PHASE4_MD },
  { key:'phase5', label:'Phase 5 · Deployment', sub:'Product Requirements · Phase 5', type:'phase', phase:5, hitl:true, content: PHASE5_MD },
  { key:'phase6', label:'Phase 6 · Retrospective', sub:'Product Requirements · Phase 6', type:'phase', phase:6, hitl:false, content: PHASE6_MD },
  { key:'dashboard', label:'Dashboard', sub:'Product Requirements · Dashboard', type:'dashboard', hitl:false, content: DASHBOARD_MD },
  // SECTION: SYSTEM ARCHITECTURE
  { key:'sad', label:'Pipeline Architecture', sub:'System Architecture · SAD', type:'sad', hitl:false, content: SAD_MD },
  // SECTION: HI-FI MOCKUPS (external links — open in new tab)
  { key:'mockup-user', label:'User Dashboard', sub:'Hi-Fi Mockup · User View', type:'mockup-link', href:'mockups/2026-04-12-tacticl-pdlc-mockup-dashboard-user.html' },
  { key:'mockup-admin', label:'Admin Console', sub:'Hi-Fi Mockup · Admin View', type:'mockup-link', href:'mockups/2026-04-12-tacticl-pdlc-mockup-dashboard-admin.html' },
];
```

- [ ] **Step 25: Add SAD_MD variable declaration**

After the existing `var DASHBOARD_MD = ...` line, add:

```javascript
var SAD_MD = document.getElementById('sad-md').textContent.trim();
```

- [ ] **Step 26: Embed SAD content as a text/plain script block**

Read `2026-04-12-tacticl-pdlc-pipeline-architecture.md` and embed its full content in the HTML as a `<script type="text/plain" id="sad-md">` block, placed alongside the other embedded doc blocks (after `dashboard-md` block).

- [ ] **Step 27: Add section headers and mockup-link handling to buildNav**

Update the `buildNav()` function to:
1. Render section header labels at the three breakpoints: before `overview` (PRODUCT REQUIREMENTS), before `sad` (SYSTEM ARCHITECTURE), before `mockup-user` (HI-FI MOCKUPS)
2. Handle `type === 'mockup-link'` entries: render as nav item that opens `doc.href` in a new tab (no active state, no content panel swap)

Section header HTML pattern (insert as non-clickable nav item):
```html
<div class="nav-section-header">{SECTION LABEL}</div>
```

CSS for section header:
```css
.nav-section-header {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #6b7280;
  padding: 18px 20px 6px;
}
```

Mockup link nav item click handler: `window.open(doc.href, '_blank')` instead of `showDoc(doc.key)`.

- [ ] **Step 28: Add SAD banner rendering in renderContent**

In the `renderContent()` function, for entries where `doc.type === 'sad'`, prepend a banner above the rendered markdown:

```html
<div class="sad-banner">
  <div class="banner-icon">&#9881;</div>
  <div class="banner-info">
    <div class="banner-title">Pipeline Architecture SAD</div>
    <div class="banner-sub">v2.0 · Draft · 2026-04-12 · 17 sections</div>
  </div>
  <span class="sad-type-badge">SAD</span>
</div>
```

CSS for SAD banner:
```css
.sad-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #0f2942;
  border: 1px solid #1e4a7a;
  border-radius: 8px;
  padding: 14px 18px;
  margin-bottom: 24px;
}
.banner-icon { font-size: 22px; color: #60a5fa; }
.banner-title { font-weight: 700; color: #e2e8f0; font-size: 15px; }
.banner-sub { font-size: 12px; color: #94a3b8; margin-top: 2px; }
.sad-type-badge {
  margin-left: auto;
  background: #1e3a5f;
  color: #60a5fa;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  padding: 3px 10px;
  border-radius: 4px;
}
```

- [ ] **Step 29: Add anchor link interception for SAD TOC**

The SAD has a large table of contents with anchor links (e.g., `#section-3-workspace-protocol`). Without interception these cause the browser to navigate away from the viewer.

Add a click event listener after `marked.parse()` renders the SAD content that intercepts `<a>` clicks where `href` starts with `#` and instead scrolls within the content panel:

The event listener should be attached to the content container div, check if the clicked target is an anchor with a hash href, and use `document.querySelector(href)` within the content panel to `scrollIntoView`.

- [ ] **Step 30: Update page title and header**

In the `<title>` tag: `Tacticl PDLC Design Docs`

In the visible page header H1 (if present in the HTML): `Tacticl PDLC Design Docs`

### Task 7: Commit and push all changes

- [ ] **Step 31: Verify all changes**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-docs
git status
git diff --stat HEAD
```

Expected: All 12 renames committed, all .md files modified, tacticl-pdlc-design-docs.html modified.

- [ ] **Step 32: Final commit and push**

```bash
git add -A
git commit -m "feat: complete artifact taxonomy implementation — viewer HTML overhaul, SAD embed, section nav"
git push origin main
```

Expected: All changes pushed to tacticl-docs remote.

---

## Verification Checklist

After all tasks complete:

- [ ] `git log --oneline -5` in tacticl-docs shows 3 commits (renames, frontmatter, PRD content + viewer)
- [ ] `ls /Users/cuztomizer/Documents/GitHub/tacticl-docs/architecture/pdlc/` — old filenames gone, new filenames present
- [ ] `head -10 2026-04-12-tacticl-pdlc-overview-product-requirements.md` — YAML frontmatter at top
- [ ] `tacticl-pdlc-design-docs.html` opens in browser — sidebar shows 3 sections (PRODUCT REQUIREMENTS / SYSTEM ARCHITECTURE / HI-FI MOCKUPS)
- [ ] Clicking "Pipeline Architecture" in sidebar — SAD banner displays above document body
- [ ] Clicking "User Dashboard" / "Admin Console" — opens mockup HTML in new browser tab
- [ ] Clicking a TOC anchor in SAD — scrolls within content panel, does not navigate away
