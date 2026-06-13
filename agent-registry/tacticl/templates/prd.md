---
type: prd
title: <feature name> — Product Requirements
artifact_id: artifact_po_prd
agent: Product Owner
run_id: {runId}
version: 1
---

<!--
PO AUTHORING GUIDANCE — you are the guardian of "what" and "why."
This PRD is the single source of truth for the run; downstream personas translate
this contract into architecture, design, code, and tests.

Voice: concrete, declarative, behavior-focused. Write "the system sends a
confirmation within 5 seconds," never "the system should feel responsive."
Zero "how" — no frameworks, datastores, API shapes, schemas, or code. Those
belong to ARCHITECT and IMPLEMENTER; naming them here forecloses their design space.
No "should / could / might" in acceptance criteria — every AC is declarative and
yields a single binary pass/fail. If TESTER cannot write a binary check from an AC,
it is not done — rewrite it. Refine the raw request; never paste it verbatim.
Don't leave open questions dangling — either decide, or escalate via `ask`.
-->

## Summary

<!-- 2-4 sentences. The problem in user-facing terms (what is broken or missing),
who is hurting, and the outcome we are buying. State the underlying problem, not the
stated feature — return to first principles. No solutioning. -->

## Goals

<!-- Bulleted list of the observable outcomes this run must deliver. Each goal is a
measurable signal we can check next week, not an adjective. Optionally call out explicit
non-goals here (distinct from Out of scope below) to pin the boundary early. -->

- <observable outcome this run must deliver>

### Success metrics

<!-- The post-launch signals that confirm the goals were actually achieved. Each is
observable and measurable AFTER ship (not a build-time check) with a named source and a
target — never an adjective. If you cannot name where the number comes from, it is not a
metric. Keep this short; 2-4 metrics that matter, not a dashboard. -->

| Metric | Baseline | Target | Source |
| ------ | -------- | ------ | ------ |
| <observable post-launch signal> | <current value or "n/a — new"> | <threshold that confirms success> | <where it is measured> |

## User stories

<!-- One or more entries in strict "As a <role>, I want <capability>, so that
<outcome>" form. At least one is required. Each story maps to one or more acceptance
criteria below. Keep them user-facing — no implementation verbs. -->

- As a <role>, I want <capability>, so that <outcome>.

## Requirements

<!-- The contract. Functional requirements are numbered so downstream personas and
ADRs can cite them (FR-1, FR-2, ...). Priority is MUST / SHOULD / COULD (MoSCoW).
Keep each requirement single-behavior — no compound "and." -->

### Functional

| #    | Requirement                                           | Priority |
| ---- | ----------------------------------------------------- | -------- |
| FR-1 | <single observable behavior the system must exhibit>  | MUST     |
| FR-2 | <single observable behavior>                          | SHOULD   |
| FR-3 | <single observable behavior>                          | COULD    |

### Non-functional

<!-- Performance, reliability, security, accessibility, and operational constraints
stated as measurable thresholds (e.g. "p95 latency under 300ms," "tokens encrypted
at rest"). Bullets or a table — keep each one verifiable, never an adjective. -->

- <NFR — measurable threshold or constraint>

## Acceptance criteria

<!-- The heart of the PRD. Numbered Given/When/Then, each binary pass/fail, each
verifying exactly one behavior (no compound "and"). Every criterion must trace to a
functional requirement above and be writable as a TESTER check with no follow-up
question. This is what the TESTER persona and the Plan gate reviewer read first. -->

1. **Given** <initial state>, **When** <action>, **Then** <single observable result>.
2. **Given** <initial state>, **When** <action>, **Then** <single observable result>.
3. **Given** <error/edge state>, **When** <action>, **Then** <single observable result>.

## Out of scope

<!-- Explicit exclusions. Silence on scope invites scope creep — say no in writing.
Pre-empt the three most likely "while we're in there" additions a downstream persona
might attempt. Each line is a concrete thing this run will NOT do. -->

- <explicitly excluded work>

---

**HITL NOTE — Plan gate.** This PRD is reviewed by the human at the **Plan gate**
(alongside the Architect's solution-architecture and the Planner's task-plan). The human
decides: are the acceptance criteria binary and testable, is scope tight and correct, and
is the plan approved to proceed into implementation? Approve → downstream personas build
against this contract. Request changes → revise this PRD and re-emit (bump `version`)
before the run advances. Unresolved decisions must be raised via `ask` before requesting
the gate — never ship the PRD with dangling open questions.

**HOW TO EMIT**
1. Write this file to `.tacticl/pdlc/{runId}/prd.md` on the working branch (frontmatter first; replace every `<…>` and delete the HTML-comment guidance).
2. Commit it to the working branch (it rides inside the PR; git history is the version trail — edit in place and bump `version` on rework, never write `-v2` files).
3. Append/update the entry in `.tacticl/pdlc/{runId}/manifest.json` (replace the entry with the same `artifact_id` if it already exists; leave `sha` empty for git to fill):
   ```json
   {
     "artifact_id": "artifact_po_prd",
     "type": "prd",
     "agent": "Product Owner",
     "path": ".tacticl/pdlc/{runId}/prd.md",
     "title": "<feature name> — Product Requirements",
     "summary": "<one-line summary of the contract>",
     "sha": ""
   }
   ```
