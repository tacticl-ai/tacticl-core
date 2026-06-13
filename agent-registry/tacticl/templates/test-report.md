---
type: test-report
title: Test Report — <short title of the change under test>
artifact_id: artifact_tester_test_report
agent: Tester
run_id: {runId}
version: 1
---

<!--
CANONICAL TEMPLATE — Tester deliverable, reviewed at the MERGE GATE.
You are TESTER. You do NOT approve or reject the PR (that is REVIEWER's job). Your
deliverable is COVERAGE, not a verdict. Fill every section below, then emit per the
HOW TO EMIT footer. Delete these comments before committing.

VOICE: test names are behavior statements, not implementation labels
(`rejectsExpiredTokens`, not `testValidate3`). Assertions are precise — never tautological.
Every test you list was proven to fail once when its assertion was wrong (mutation check).
If a test exposes a real bug, you LEAVE it failing — do not weaken it; the rework loop
routes it back to IMPLEMENTER with your failing test as the evidence.
-->

<!-- HITL NOTE: Reviewed at the MERGE GATE as an AUTOMATED PRE-PASS. You run right after the
Implementer, in parallel with Reviewer and Security Analyst — you are NOT a human checkpoint.
Your headline counts (PASS/FAIL/coverage) are surfaced at the top of the Implementer's CHANGE
SUMMARY, which is the single merge-gate cover sheet one human reads before making one decision
(Approve & merge / Request changes / Reject). You do not vote on the merge — your deliverable is
COVERAGE, not a verdict. -->

## Summary

<!-- One-paragraph verdict-free summary of the run, then the headline counts. State the
baseline result (suite must have been green before you added anything), the branch you
ran on (the implementer branch, never main), and the final result. Fill the table. -->

| Metric | Baseline (before) | Final (after) |
| --- | --- | --- |
| Passed | `<n>` | `<n>` |
| Failed | `0` | `<n — should be 0 unless a test exposes a real bug>` |
| Skipped | `<n>` | `<n>` |
| Total | `<n>` | `<n>` |

- **Branch tested:** `implementer-{runId}` (commit `<sha>`)
- **Test command:** `<e.g. ./gradlew test>`
- **Baseline status:** `<GREEN — required before adding tests | BLOCKED — stopped here, do not build on a red baseline>`
- **Net new tests added:** `<n>`
- **Acceptance criteria from PO/PRD spec:** `<m>` total — `<covered>` covered, `<gaps>` not covered (see Gaps)
- **Pre-pass verdict (for the Change Summary cover sheet):** `<PASS — full suite green | FAIL — n test(s) red, see Failures>`

## Coverage

<!-- Coverage of the TOUCHED files only — chase coverage of behavior that matters, not a
global percentage. One row per file the implementer changed (from `git diff --stat`).
Note whether the line/branch number reflects behavior worth caring about, not just a metric. -->

| Touched file | Lines | Branches | Notes |
| --- | --- | --- | --- |
| `src/main/.../FooService.java` | `<%>` | `<%>` | `<what matters here / why a gap is acceptable>` |
| `src/main/.../BarController.java` | `<%>` | `<%>` | `<...>` |

<!-- Map each PO/PRD acceptance criterion to the test that asserts it. This is the heart
of the report — a criterion with no test is a Gap, not a pass. -->

### Acceptance-criteria coverage matrix

| Criterion (from spec) | Test (file::name) | Status |
| --- | --- | --- |
| Returns 404 when user not found | `UserServiceTest::shouldReturnNotFoundWhenUserMissing` | COVERED (pre-existing) |
| Rate limit blocks after 5 requests | `RateLimitTest::blocksSixthRequestWithinWindow` | COVERED (added) |
| Password hash uses bcrypt cost 12 | `(none)` | NOT COVERED — see Gaps |

## New tests added

<!-- List every test YOU added (test code only — you never touch src/main/). For each:
the behavior it locks down and the mutation you used to prove it can fail. If you added
none because coverage was already complete, say so explicitly and explain how you verified that. -->

| Test (file::name) | Behavior asserted | Mutation proof (what made it fail) |
| --- | --- | --- |
| `RateLimitTest::blocksSixthRequestWithinWindow` | 6th request inside the window returns 429 | Bumped limit to 6 → test went red as expected |
| `TokenTest::rejectsExpiredTokens` | Expired PASETO is rejected | Removed expiry check → test went red as expected |

- **Commit:** `<sha>` — pushed to `implementer-{runId}` with a `test:` prefix
- **Production code touched:** `none` (`git diff --stat src/main/` is empty — required)

## Failures

<!-- If the final suite is fully green, write: "No failures — full suite green (n/n)."
Otherwise, ONE `###` per failing test. A failure here is intentional evidence: it means a
test correctly caught a real bug. Do NOT delete or weaken it — capture it so REWORK can
route back to IMPLEMENTER. Include the failing assertion and the trimmed stack. -->

### `<TestClass::testName>` — <one-line what behavior broke>

- **Expected:** `<expected value/behavior per spec>`
- **Actual:** `<observed value/behavior>`
- **Likely cause:** `<best read of the production bug — for IMPLEMENTER, not a fix>`

```
<trimmed assertion message + stack trace — enough to locate the bug, not the whole log>
```

### Flaky / quarantined

<!-- OMIT this sub-section entirely if nothing here. Only list a test if it passed on rerun but
not deterministically (timing, ordering, external dependency). A flake is NOT a real-bug failure —
do not route it to rework — but the human at the merge gate must know the green you reported is not
fully stable. Quarantine (don't delete) and file a follow-up; a flake you hid is a flake you own. -->

| Test (file::name) | Symptom | Pass rate (n reruns) | Action |
| --- | --- | --- | --- |
| `<TestClass::testName>` | `<e.g. intermittent timeout on WS connect>` | `<8/10>` | `<quarantined — follow-up issue #...>` |

## Gaps / not-covered

<!-- Honest list of what is NOT tested and why. Each gap is a decision input for the human
at the Merge gate. Categorize and justify — "deferred" is fine if stated; "forgot" is not.
If a gap blocks merge in your judgment, say so plainly (the human still decides). -->

| Area not covered | Reason | Risk if shipped | Recommendation |
| --- | --- | --- | --- |
| `<e.g. bcrypt cost assertion>` | `<spec ambiguous on cost factor — used report.sh ask, awaiting answer>` | `<low/med/high>` | `<defer | block | follow-up issue>` |
| `<e.g. concurrency under load>` | `<out of scope for unit suite; needs load test>` | `<...>` | `<follow-up issue #...>` |

---

**HITL NOTE:** Reviewed at the **Merge gate (automated pre-pass)** — this report runs automatically after the Implementer (alongside Reviewer and Security Analyst), and its verdict is surfaced on the Implementer's Change Summary cover sheet for the one human merge decision. The Tester does not vote on the merge.

**HOW TO EMIT**

1. **Write** this file to `.tacticl/pdlc/{runId}/test-report.md` (test code + this report only — never `src/main/`), with the frontmatter from the top of this template and every `##` section filled (replace all `<…>` placeholders; omit `### Flaky / quarantined` if empty).
2. **Append/update** your entry in `.tacticl/pdlc/{runId}/manifest.json` (read it, create `[]` if missing; replace the entry with the same `artifact_id` in place on a rework, else append); leave `sha` empty — git fills it:

```json
{
  "artifact_id": "artifact_tester_test_report",
  "type": "test-report",
  "agent": "Tester",
  "path": ".tacticl/pdlc/{runId}/test-report.md",
  "title": "Test Report — <short title of the change under test>",
  "summary": "<one line: counts + coverage + PASS/FAIL>",
  "sha": ""
}
```

3. **Commit** the artifact and the manifest together with your `test:`-prefixed tests on the `implementer-{runId}` branch and push so CI runs — they ride inside the PR; git history is the version trail. Do not open a separate branch.
