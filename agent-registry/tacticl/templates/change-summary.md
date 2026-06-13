---
type: change-summary
title: Change Summary — <spark request short>
artifact_id: artifact_implementer_change_summary
agent: Implementer
run_id: {runId}
version: 1
---

<!--
IMPLEMENTER ARTIFACT TEMPLATE — "change-summary"
Reviewed at: Merge gate. THIS IS THE MERGE-GATE COVER SHEET — the first thing the human reads.

This is the human-readable face of your PR. The diff explains WHAT changed; this artifact
explains WHY, proves the plan was executed, and surfaces the automated verdicts (Reviewer /
Test / Security) up top so the human can decide in one read.

Keep it honest. A green build with no tests is an illusion of correctness — say so if that is
the situation. Disclose every deviation and every TODO. Never restate the diff; explain intent.
The full diff/PR and the review / test-report / security-report are DRILL-INS — reference them,
do not paste them. Replace all <…> placeholders and delete these comments before emitting.
-->

> **This is the Merge-gate cover sheet.** The full diff and the Reviewer / Test / Security
> reports are drill-ins. This summary is the WHY, the map back to the plan, and the automated
> verdicts. The authoritative WHAT lives in the PR: <pr_url> (also recorded in the manifest).

## Automated checks

<!-- COVER-SHEET HEADER — the human reads this first. Populated from the auto-run review,
     test-report, and security-report artifacts (Reviewer → Test → Security all run
     AUTOMATICALLY after the Implementer commits; none of them is a human checkpoint).
     Each row mirrors the verdict in its source artifact and links to it as the drill-in.
     If a check has not run yet, write "pending" — never fabricate a verdict. -->

| Check | Verdict | Detail | Drill-in |
|-------|---------|--------|----------|
| Reviewer | APPROVE / REQUEST CHANGES | <one line: e.g. "2 blocking (auth, NPE)" or "no blocking issues"> | `review.md` |
| Test | PASS / FAIL | <counts: e.g. "142/142 passed" or "3 failed / 142"> | `test-report.md` |
| Security | CLEAN / ISSUES | <one line: e.g. "no findings" or "1 HIGH (SSRF), 2 MEDIUM"> | `security-report.md` |

<!-- One line of net read for the human: is this mergeable as-is, or does a verdict above
     gate it? Be direct — this is the sentence that frames their single decision. -->

**Gate read:** <one line — e.g. "All green, ready to merge" or "REQUEST CHANGES blocks: see review.md finding 1">.

## What I built

<!-- One or two prose paragraphs. State the intent and outcome, not the file list.
     Lead with WHY: what problem this closes and how the change closes it.
     Reference the upstream contracts (PRD / solution-architecture / task-plan) the work
     satisfies. If the build is green but a behavior is untested, say so here — do not imply
     correctness you have not proven. -->

## Files changed

<!-- One row per file. `+/-` is added/removed lines from `git diff --stat`.
     Purpose is one line of INTENT, not a restatement of the change
     (good: "correlate role events in the web UI"; bad: "update DTO").
     Generated/vendored/lockfile churn can be collapsed into a single row.
     The full diff is the drill-in — this table is the map, not the change. -->

| File | +/- | Purpose |
|------|-----|---------|
| `path/to/File.java` | +42 / -3 | <why this file changed> |
| `path/to/FileTest.java` | +88 / -0 | <what behavior this test locks in> |

## Key decisions

<!-- Implementation-level decisions made WHILE building — not architecture (that is the
     Architect's solution-architecture). Trade-offs taken, libraries chosen, edge cases
     handled or deliberately deferred. If a decision contradicts or extends the task-plan,
     flag it here AND under Plan coverage. Use the ADR sub-form only for a non-trivial choice
     that future readers will need the reasoning for. -->

- <decision — one line, with the WHY>

### ADR-001: <title>   <!-- optional; include only for a load-bearing implementation choice -->

- **Status:** Accepted
- **Context:** <what forced a choice — name the rejected alternative and why it lost>
- **Decision:** <what was chosen>
- **Consequences:** <what this commits us to / rules out>

## Plan coverage

<!-- Map EVERY task from the task-plan (the contract) to its status. Every planned task must
     appear — done, in-progress, or todo. An unlisted task reads as a silent drop and breaks
     the chain of trust with the Planner and the reviewer. "Deviation" = built differently
     than planned; describe it and link the decision above. -->

| Plan task | Status | Notes |
|-----------|--------|-------|
| <task 1 from the plan> | ✅ Done | <commit / test that proves it> |
| <task 2 from the plan> | 🟡 In progress | <what remains> |
| <task 3 from the plan> | ⬜ Todo | <why deferred — and where it is tracked> |
| <task 4 from the plan> | ↪️ Deviated | <built differently — see Key decisions> |

## Tests

<!-- Each new behavior must have at least one test. List them and what they lock in.
     State the suite result you actually observed (re-run, do not trust memory). The full
     Test Report is the drill-in (`test-report.md`); this is the implementer's own account.
     If a behavior shipped WITHOUT a test, name it here as a known gap — do not hide it. -->

| Test | Locks in | Result |
|------|----------|--------|
| `FooTest.shouldDoX` | <the behavior / edge case> | PASS |

- Full suite: `<test command>` — <PASS / FAIL>
- Linter / checks: `<check command>` — <PASS / FAIL>
- **Untested behavior (if any):** <name the gap, or "none">

---

**HITL NOTE — Merge gate.** This Change Summary is the **cover sheet** the human reads first at
the **single Merge gate**. Reviewer, Test, and Security run AUTOMATICALLY after you commit — they
are not human checkpoints — and their verdicts are surfaced in **## Automated checks** above. The
human reads this summary (with those verdicts up top), drills into the diff / `review.md` /
`test-report.md` / `security-report.md` as needed, and makes ONE decision: **Approve & merge**,
**Request changes** (re-dispatches the Implementer with the findings; after 3 rework iterations a
human checkpoint is forced), or **Reject**. Coverage must be complete and every deviation / TODO
disclosed — anything hidden here surfaces as a broken merge later.

---

**HOW TO EMIT**
1. Write this file to `.tacticl/pdlc/{runId}/change-summary.md` on the `implementer-{runId}` branch (it rides inside the PR; git history is the version trail).
2. Commit it to the PR branch and push so the auto-run Reviewer / Test / Security roles populate **## Automated checks**.
3. Append/update the entry in `.tacticl/pdlc/{runId}/manifest.json`:
   ```json
   {
     "artifact_id": "artifact_implementer_change_summary",
     "type": "change-summary",
     "agent": "Implementer",
     "path": ".tacticl/pdlc/{runId}/change-summary.md",
     "title": "Change Summary — <spark request short>",
     "summary": "<one line: what shipped + PR link>",
     "sha": ""
   }
   ```
