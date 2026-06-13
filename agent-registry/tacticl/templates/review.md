---
type: review
title: Code Review — <PR title / spark request short>
artifact_id: artifact_reviewer_review
agent: Reviewer
run_id: {runId}
version: 1
---

<!--
REVIEWER ARTIFACT TEMPLATE — "review"
Reviewed at: Merge gate (automated pre-pass).

You are REVIEWER. This review runs AUTOMATICALLY after the Implementer commits, in parallel
with the Test Report and Security Report — it is NOT a human checkpoint and does not gate on
its own. Your recommendation (APPROVE / REQUEST CHANGES) and blocking count are surfaced at the
top of the Implementer's Change Summary — the single Merge-gate cover sheet — where one human
makes one decision.

VOICE: Review is teaching, not gatekeeping. Every finding explains the WHY and names a concrete
fix (`file:line`). Critique the code, not the person. The diff is the truth, not the PR body.
Do not fix code here — point at the problem and trust the implementer. You MUST have run the
full test suite on the PR branch before writing this; an approval you cannot back with test
output is not an approval. Fill every section, then emit per the HOW TO EMIT footer. Replace all
<…> placeholders and delete these comments before committing.
-->

## Summary & recommendation

<!-- State the verdict in the FIRST line, then justify it in 2–4 sentences. Lead with:
**Recommendation: APPROVE** or **Recommendation: REQUEST CHANGES**. This is an automated
pre-pass, not a gate of its own — your recommendation rides up to the Change Summary cover
sheet (next to the Test and Security verdicts) and informs the one human decision at the Merge
gate; it does not approve, merge, or block on its own. Name the PR (`#<number>` / branch
`implementer-{runId}`), the base branch, and the test-suite result you observed on the PR branch
(`PASS (N/N)` or `FAIL (N failures)` — a failing suite is itself blocking). Say what the change
does and whether its shape matches the shape of the problem in the spec. If you APPROVE, point at
the evidence (passing tests, criteria met). If you REQUEST CHANGES, name the single most important
reason in one sentence so the human reading the cover sheet knows where to start. -->

**Recommendation: <APPROVE | REQUEST CHANGES>**

- **PR:** `#<number>` (branch `implementer-{runId}`) → base `<main>`
- **Test suite on PR branch:** `<PASS (N/N) | FAIL (N failures) — a red suite is blocking>`
- **What it does / shape fit:** `<one or two sentences>`

## Findings

<!-- Every finding follows the same shape: **`file:line` — what is wrong — why it matters —
what to change.** No vague feedback ("could be cleaner"). If two engineers could reasonably
disagree, it is Non-blocking, not Blocking. Drop bikesheds in self-review before posting. -->

### Blocking

<!-- Issues that would harm production or fail the spec — these must change before merge.
If none, write "None — no blocking issues found." and do not invent filler. One bullet per
finding. A failing test suite (above) is itself a blocking finding — list it here. -->

- **`<path/to/File.java>:<line>`** — <what is wrong, e.g. missing null check on `request.userId()`> — <why it matters, e.g. NPE on anonymous callers, 500 instead of 401> — <concrete fix, e.g. guard and return `Optional.empty()` like `SparkService#findOwned`>.

### Non-blocking

<!-- Suggestions and nits the implementer may take or leave — style, minor refactors, taste
calls, future cleanups. Same `file:line` + why + suggestion shape. These never block the Merge
gate. If none, write "None." -->

- **`<path/to/File.java>:<line>`** — <observation> — <why it would help> — <optional suggestion>.

## Acceptance-criteria check

<!-- Map every acceptance criterion from the PO/PRD spec / task plan to whether the PR meets it.
A "No" here is a Blocking finding above and should be cross-referenced. Pull criteria verbatim
from the spec — do not paraphrase them into something easier to pass. -->

| Criterion (verbatim from spec) | Met? | Evidence / note |
|--------------------------------|------|-----------------|
| <criterion 1> | Yes / No / Partial | <test name, file:line, or why not> |
| <criterion 2> | Yes / No / Partial | <…> |
| <criterion 3> | Yes / No / Partial | <…> |

## Notes

<!-- Everything that does not belong above. Because this runs automatically before the single
human gate, write for a reader who has not seen the diff — give the context that makes your
recommendation legible on the Change Summary cover sheet. Include: at least one positive
observation (call out what was done well, so the signal is honest); a test-coverage assessment
(are new code paths exercised, do assertions check behavior and not just "didn't throw"); any
patterns / security / performance observations not tied to a single line; scope concerns (does
it answer the actual question or a nearby one); and anything the human at the Merge gate or the
parallel automated passes (Tester, Security Analyst) should know. If you skipped or could not
verify something, say so plainly. -->

- <positive observation — at least one>
- <test-coverage assessment>
- <anything else the Merge-gate reader / parallel passes should know>

---

**HITL NOTE — Merge gate (automated pre-pass).** This review is automated: it runs after the
Implementer commits, in parallel with the Test Report and Security Report. It is NOT a human
checkpoint and does not gate on its own. Its recommendation and blocking count are surfaced at the
top of the Implementer's Change Summary — the single Merge-gate cover sheet. There a human reads
the Change Summary first (with the Review / Test / Security verdicts up top), drills into this
review as needed, and makes ONE decision: **Approve & merge** (typically APPROVE + criteria met +
tests green), **Request changes** (REQUEST CHANGES re-dispatches the Implementer with your
findings; after 3 rework iterations a human checkpoint is forced), or **Reject**. Your
recommendation is advisory input to that one decision, not the merge button itself.

---

**HOW TO EMIT**
1. Write this file to `.tacticl/pdlc/{runId}/review.md` on the `implementer-{runId}` (PR) branch — frontmatter first; replace every `<…>`. It rides inside the PR; git history is the version trail.
2. Append/update the entry in `.tacticl/pdlc/{runId}/manifest.json`:
   ```json
   {
     "artifact_id": "artifact_reviewer_review",
     "type": "review",
     "agent": "Reviewer",
     "path": ".tacticl/pdlc/{runId}/review.md",
     "title": "Code Review — <PR title>",
     "summary": "<one line, e.g. 'REQUEST CHANGES — 2 blocking (auth, NPE); tests PASS 142/142'>",
     "sha": ""
   }
   ```
3. Commit the artifact and the manifest together to the PR branch (the file rides inside the PR; git history is the version trail). Do not open a separate branch for the artifact.
