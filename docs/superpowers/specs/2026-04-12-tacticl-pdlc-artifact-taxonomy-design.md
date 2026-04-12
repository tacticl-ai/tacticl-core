# Tacticl PDLC v2 — Artifact Taxonomy & Naming Convention Design

**Status:** Approved
**Date:** 2026-04-12
**Author:** Gabriel Jimenez

---

## Problem

The PDLC pipeline produces many documents across 6 phases and 12 roles. Without a clear taxonomy, artifacts accumulate with inconsistent names, unclear purpose, and no obvious reading order. Users reviewing HITL checkpoints need to know exactly what to read and where to find it. Engineers building the system need consistent names they can rely on.

---

## Design

### Two Tiers of Artifacts

Every phase produces exactly two tiers of artifacts:

**Tier 1 — Critical Consolidated Report** (one per phase)
The single document a user sees at the HITL checkpoint. Summarizes all role outputs for that phase in one readable doc. The user approves or requests changes based on this document alone. They only read Tier 2 if they need depth on a specific finding.

**Tier 2 — Supporting Detail Artifacts** (one or more per phase)
The full output of each individual role. Linked from the Tier 1 report. Agents write to these directly. The pipeline assembles them into the Tier 1 report after all roles complete.

---

### File Format

**All artifacts: Markdown (`.md`) with YAML frontmatter.**

Not MDX — agents (Claude Code CLI) produce these files. Markdown is what they produce reliably. The look and feel in the Spark Dashboard comes from the renderer (`react-markdown` + plugins), not the file format. Syntax highlighting, styled tables, callout blocks, and severity badges are all handled by the dashboard rendering component.

**Mockups and diagrams: `.html`** — self-contained hi-fi screens, fully rendered in browser.

**Machine-readable data: `.json`** — story breakdowns, structured task plans.

---

### YAML Frontmatter Schema

Every artifact carries a YAML frontmatter block. The dashboard reads this for metadata display and status tracking.

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

`type` values:
- `phase-report` — Tier 1 consolidated report, the HITL approval surface
- `detail-artifact` — Tier 2 supporting artifact, linked from phase-report
- `engineering-spec` — tacticl-docs engineering reference (PRDs, SADs, design specs)

---

### Naming Convention

#### Pattern

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

**Viewer (tacticl-docs, living document — no date):**
```
tacticl-pdlc-design-docs.html
```

#### Document Title (H1 inside file)

```
Tacticl · {Phase Name} {Doc Role} · {Spark Title or Scope} — YYYY-MM-DD
```

Examples:
```
# Tacticl · Phase 4 Quality Report · Add Payment Flow — 2026-04-12
# Tacticl · Phase 1 Product Requirements · PDLC Overview — 2026-04-12
# Tacticl · Pipeline Architecture · System Design — 2026-04-12
```

---

### Per-Phase Artifact Map

#### Phase 1 — Product

| Tier | Artifact | Filename | Authored by |
|------|----------|----------|-------------|
| 1 | Product Report | `YYYY-MM-DD-tacticl-{slug}-phase-1-product-report.md` | PM + RESEARCHER (assembled) |
| 2 | Product Requirements Doc | `YYYY-MM-DD-tacticl-{slug}-phase-1-product-requirements.md` | PM |
| 2 | Research Summary | `YYYY-MM-DD-tacticl-{slug}-phase-1-research-summary.md` | RESEARCHER |
| 2 | Hi-fi Mockups | `YYYY-MM-DD-tacticl-{slug}-phase-1-mockup-{screen}.html` | PM / DESIGNER |

**Tier 1 structure:**
```markdown
## Executive Summary
## Product Requirements (Summary — 5 key decisions)
## Research Findings (Summary — risks, patterns, integrations)
## Mockups
[links to .html files]
## What's Next
Phase 2 will produce: SAD, ERD, Story Breakdown
```

---

#### Phase 2 — Design

| Tier | Artifact | Filename | Authored by |
|------|----------|----------|-------------|
| 1 | Design Report | `YYYY-MM-DD-tacticl-{slug}-phase-2-design-report.md` | ARCHITECT + DESIGNER + PLANNER (assembled) |
| 2 | System Architecture Doc | `YYYY-MM-DD-tacticl-{slug}-phase-2-architecture.md` | ARCHITECT |
| 2 | Entity Relationship Diagram | `YYYY-MM-DD-tacticl-{slug}-phase-2-erd.md` | ARCHITECT |
| 2 | Component Screens | `YYYY-MM-DD-tacticl-{slug}-phase-2-screens-{name}.html` | DESIGNER |
| 2 | Story Breakdown | `YYYY-MM-DD-tacticl-{slug}-phase-2-stories.json` | PLANNER |

**Tier 1 structure:**
```markdown
## Executive Summary
## Architecture Decisions (key tech choices, infra, API surface)
## Data Model (ERD summary, key entities)
## User Stories ({N} stories, {M} tasks)
## Supporting Artifacts
[links to architecture.md, erd.md, screens, stories.json]
```

---

#### Phase 3 — Development

| Tier | Artifact | Filename | Authored by |
|------|----------|----------|-------------|
| 1 | Implementation Report | `YYYY-MM-DD-tacticl-{slug}-phase-3-implementation-report.md` | IMPLEMENTER + internal REVIEWER |
| 2 | Per-story implementation logs | in `implementation-report.md` as sections | IMPLEMENTER |

**Tier 1 structure:**
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

---

#### Phase 4 — Quality

| Tier | Artifact | Filename | Authored by |
|------|----------|----------|-------------|
| 1 | Quality Report | `YYYY-MM-DD-tacticl-{slug}-phase-4-quality-report.md` | Assembled from all 3 |
| 2 | Test Results | `YYYY-MM-DD-tacticl-{slug}-phase-4-test-results.md` | TESTER |
| 2 | Coverage Breakdown | `YYYY-MM-DD-tacticl-{slug}-phase-4-coverage-breakdown.md` | TESTER |
| 2 | Code Review | `YYYY-MM-DD-tacticl-{slug}-phase-4-code-review.md` | REVIEWER |
| 2 | Security Audit | `YYYY-MM-DD-tacticl-{slug}-phase-4-security-audit.md` | SECURITY_ANALYST |

**Tier 1 structure:**
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

---

#### Phase 5 — Deploy

| Tier | Artifact | Filename | Authored by |
|------|----------|----------|-------------|
| 1 | Deployment Report | `YYYY-MM-DD-tacticl-{slug}-phase-5-deployment-report.md` | TECHNICAL_WRITER + DEVOPS |
| 2 | PR Descriptions | `YYYY-MM-DD-tacticl-{slug}-phase-5-pr-descriptions.md` | TECHNICAL_WRITER |
| 2 | Deployment Notes | `YYYY-MM-DD-tacticl-{slug}-phase-5-deployment-notes.md` | DEVOPS |

**Tier 1 structure:**
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

---

#### Phase 6 — Retro

| Tier | Artifact | Filename | Authored by |
|------|----------|----------|-------------|
| 1 | Retrospective Report | `YYYY-MM-DD-tacticl-{slug}-phase-6-retrospective-report.md` | RETRO_ANALYST |
| 2 | Proposed Learnings | `YYYY-MM-DD-tacticl-{slug}-phase-6-proposed-learnings.md` | RETRO_ANALYST |

**Tier 1 structure:**
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

---

### Engineering Spec Renames (tacticl-docs)

Current filename → New filename:

| Current | New |
|---------|-----|
| `review.html` | `tacticl-pdlc-design-docs.html` |
| `overview-prd.md` | `2026-04-12-tacticl-pdlc-overview-product-requirements.md` |
| `phase-1-product-prd.md` | `2026-04-12-tacticl-pdlc-phase-1-product-requirements.md` |
| `phase-2-design-prd.md` | `2026-04-12-tacticl-pdlc-phase-2-design-requirements.md` |
| `phase-3-development-prd.md` | `2026-04-12-tacticl-pdlc-phase-3-development-requirements.md` |
| `phase-4-test-prd.md` | `2026-04-12-tacticl-pdlc-phase-4-quality-requirements.md` |
| `phase-5-deploy-prd.md` | `2026-04-12-tacticl-pdlc-phase-5-deployment-requirements.md` |
| `phase-6-retro-prd.md` | `2026-04-12-tacticl-pdlc-phase-6-retrospective-requirements.md` |
| `dashboard-prd.md` | `2026-04-12-tacticl-pdlc-dashboard-requirements.md` |
| `pipeline-architecture-sad.md` | `2026-04-12-tacticl-pdlc-pipeline-architecture.md` |
| `mockups/pipeline-dashboard-user.html` | `mockups/2026-04-12-tacticl-pdlc-mockup-dashboard-user.html` |
| `mockups/pipeline-dashboard-admin.html` | `mockups/2026-04-12-tacticl-pdlc-mockup-dashboard-admin.html` |

H1 title format inside each engineering spec:
```
# Tacticl PDLC · {Document Role} · {Scope} — YYYY-MM-DD
```

---

### PRD Updates Required

The following PRD documents need new or updated sections:

**Overview PRD** (`2026-04-12-tacticl-pdlc-overview-product-requirements.md`):
- Add: **Artifact Taxonomy** section — two tiers, naming convention, YAML frontmatter schema
- Add: **Phase Output Summary** table — one row per phase showing Tier 1 doc name + Tier 2 docs

**Each per-phase PRD** (phases 1–6):
- Add: **Phase Output** section at the end — defines Tier 1 structure + Tier 2 artifacts

**Dashboard PRD** (`2026-04-12-tacticl-pdlc-dashboard-requirements.md`):
- Add: **Phase Report Rendering** section — how Spark Dashboard renders Tier 1 reports, HITL action bar, Tier 2 detail navigation

---

## What This Does Not Cover

- The Spark Dashboard rendering component implementation (tacticl-web)
- MongoDB `pipeline_artifacts` schema changes to store `type` and `status` frontmatter fields
- The `review.html` → `tacticl-pdlc-design-docs.html` SAD incorporation (separate task)

These are implementation concerns handled in the execution plan.
