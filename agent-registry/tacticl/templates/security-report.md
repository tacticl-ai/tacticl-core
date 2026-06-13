---
type: security-report
title: <Security Review — short subject, e.g. "Auth: /v1/health endpoint">
artifact_id: artifact_security_report
agent: Security Analyst
run_id: {runId}
version: 1
---

<!--
AUTHORING GUIDANCE (delete this comment block before committing).

You are the SECURITY_ANALYST. This artifact is your verdict, not your worklog.
Evidence over vibes: every finding carries category, file:line, exploit path,
severity, and fix direction — nothing more, nothing less. You FIND and HAND OFF;
you do NOT fix. Scanners (gitleaks, dependency, license) are INPUTS, not verdicts —
you read the code; the tools inform you.

Verdict rule (one line, non-negotiable): any HIGH or CRITICAL finding ⇒ BLOCK.
Only MEDIUM / LOW / INFO ⇒ PASS. If you cannot describe the exploit in one
sentence, drop the severity by one level. A clean diff with a documented walk is
a valid PASS — never invent findings to look thorough.

Fill every `##` section. Where a section does not apply, write "N/A — <reason>",
never leave it blank. The dashboard builds the left-nav from these headings, so
keep them exactly as ordered below.
-->

## Summary

> One-paragraph verdict up front. State **PASS** or **BLOCK**, the count of findings by
> severity (CRITICAL / HIGH / MEDIUM / LOW / INFO), and the headline reason. If BLOCK,
> name the issue numbers filed. This is the only section a merge approver may read — make
> it stand alone.

**Verdict:** `PASS` | `BLOCK`

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH     | 0 |
| MEDIUM   | 0 |
| LOW      | 0 |
| INFO     | 0 |

**Headline:** <e.g. "Clean diff — all 10 OWASP categories walked, no exploitable input paths added." or "BLOCK: SQL injection in UserController.findByEmail (A03), issue #124.">

**Scope reviewed:** PR #<n> diff (added/changed lines only), branch `implementer-{runId}`. Files: <list or count>.

> **Automated pre-pass.** You run automatically right after the Implementer (in parallel with
> Reviewer and Tester) — you are **not** a human checkpoint. This verdict (`PASS` / `BLOCK`) is
> surfaced at the top of the Implementer's Change Summary, the single cover sheet the one human
> reads at the Merge gate. Make the verdict + headline stand alone: it is read first, before any
> drill-in.

### OWASP Top 10 walk

> Required: an explicit outcome for ALL ten categories. `PASS` = checked, no finding.
> `FINDING` = links to a row below (cite the finding id). `N/A` = not exercised by this
> diff (give the one-line reason). No category may be omitted.

| Category | Outcome | Note |
|----------|---------|------|
| A01 Broken Access Control | PASS / FINDING / N/A | <auth + ownership checks on new endpoints, IDOR> |
| A02 Cryptographic Failures | PASS / FINDING / N/A | <hardcoded secrets, weak hashing, plaintext transit> |
| A03 Injection | PASS / FINDING / N/A | <SQL/NoSQL/command/template via user input> |
| A04 Insecure Design | PASS / FINDING / N/A | <missing rate limit / spending cap on expensive paths> |
| A05 Security Misconfiguration | PASS / FINDING / N/A | <debug flags, permissive CORS, verbose errors> |
| A06 Vulnerable Components | PASS / FINDING / N/A | <see Dependency advisories below> |
| A07 Identification/Auth Failures | PASS / FINDING / N/A | <session mgmt, credential exposure, MFA on sensitive ops> |
| A08 Software/Data Integrity | PASS / FINDING / N/A | <untrusted deserialization, unvalidated external data> |
| A09 Logging/Monitoring Failures | PASS / FINDING / N/A | <secrets in logs, missing audit on security events> |
| A10 Server-Side Request Forgery | PASS / FINDING / N/A | <server fetches user-controlled URLs, no allowlist> |

## Secret scan (gitleaks result)

> Run gitleaks (or equivalent) over the diff and the full changed tree — secrets and
> misconfig hide in test files, fixtures, and config, not just `src/main/`. Report the
> tool, version, and exit status. A clean scan is a sentence; any hit is a finding (and a
> HIGH/CRITICAL issue if a real secret is committed). Tool output is an input — confirm
> each hit is a true positive by reading the line.

| Field | Value |
|-------|-------|
| Tool / version | gitleaks <x.y.z> |
| Scope | PR diff + changed tree on `implementer-{runId}` |
| Result | `CLEAN` (0 leaks) / `<n> leak(s)` |

> If leaks: list each as `file:line — rule (e.g. "aws-access-token") — true/false positive — disposition`.
> A confirmed committed secret is CRITICAL: file an issue, BLOCK, and note that the secret must be
> rotated (the value is now in git history — removal from the working tree is not enough).

## Dependency advisories

> Diff the dependency manifests (`build.gradle.kts`, `gradle/libs.versions.toml`, `package.json`,
> `pom.xml`) and run the dep audit (`./gradlew dependencyCheckAnalyze`, `npm audit`, OSV). One row
> per advisory on a **newly added or version-bumped** dependency — do not audit the whole pre-existing
> tree. No new/changed deps ⇒ "N/A — no dependency manifest changes in this diff." HIGH/CRITICAL CVEs
> on a reachable path are findings; map each to a severity below.

| Package | Version | Severity | Advisory | Status |
|---------|---------|----------|----------|--------|
| <group:artifact> | <version> | CRITICAL/HIGH/MEDIUM/LOW | <CVE / GHSA id + one-line summary> | new / bumped — reachable? |

## License check

> Check the license of every newly added dependency against the project allowlist
> (permissive: MIT, Apache-2.0, BSD, ISC). Flag copyleft (GPL / AGPL / SSPL) or unknown/missing
> licenses — these are a legal/compliance finding, not a CVE. No new deps ⇒ "N/A — no new
> dependencies introduced." Note the tool used (e.g. license-maven-plugin, license-checker, manual).

| Package | License | Allowlisted? | Note |
|---------|---------|--------------|------|
| <group:artifact> | <SPDX id> | yes / no | <action if copyleft/unknown> |

### Data-handling notes

> Short, targeted check on how the diff treats sensitive data — this is where multi-tenant bugs and
> privacy leaks hide that the OWASP grep misses. Walk three questions and answer each in one line:
> (1) **PII** — does the diff log, persist, or return personal data (email, tokens, phone, location,
> message content)? Is it redacted/minimized? (2) **Cross-user leakage** — does every new query /
> read filter by the authenticated `userId` / `ownerId` (Firestore subcollection path, Mongo filter,
> WHERE clause)? An unscoped read across users is a HIGH finding (A01 IDOR) — cite it below.
> (3) **Egress** — does the change send user data to a new third party (LLM provider, webhook,
> analytics) or widen an existing payload? If none apply, write "N/A — no sensitive-data paths
> touched by this diff." Do not pad: anything actionable becomes a finding below.

| Aspect | Outcome | Note |
|--------|---------|------|
| PII handling (log / store / return) | PASS / FINDING / N/A | <what data, redaction status> |
| Cross-user scoping (tenant isolation) | PASS / FINDING / N/A | <queries filtered by authenticated owner?> |
| Data egress (new third-party / widened payload) | PASS / FINDING / N/A | <destination, what is sent> |

## Findings & recommendations

> The detailed ledger. One `###` sub-section per finding, ordered CRITICAL → HIGH → MEDIUM →
> LOW → INFO. Each finding states **Category**, **Severity**, **File:line**, **Evidence**,
> **Exploit** (one concrete sentence — who can do what), and **Fix direction** (you point;
> IMPLEMENTER fixes). Every HIGH/CRITICAL must also have a GitHub issue filed with the `security`
> label — record its number. LOW/INFO are recommendations only: they live here, NOT as issues.
> If the diff is clean, write "No findings. Defense-in-depth observations below (INFO)." and list any.

### FND-001: <short title> (<A0n category>)
- **Severity:** CRITICAL | HIGH | MEDIUM | LOW | INFO
- **File:** `path/to/File.java:42`
- **Evidence:** <the exact unsafe construct, quoted from the diff>
- **Exploit:** <one sentence: who can do what, and the blast radius>
- **Fix direction:** <where to fix — not the patch itself>
- **Issue:** #<n> (required for HIGH/CRITICAL; omit for MEDIUM and below)

<!-- Repeat FND-NNN per finding. Keep INFO items as recommendations, never as filed issues. -->

### Recommendations (non-blocking)

> Hardening ideas and defense-in-depth suggestions that did not rise to a finding. These never
> block merge and never become issues — they are guidance for the IMPLEMENTER and the record.

---

**HITL NOTE — Merge gate (automated pre-pass).** This report runs automatically (no human checkpoint here); its `PASS` / `BLOCK` verdict is surfaced on the Implementer's Change Summary, the one cover sheet the single human reads at the **Merge gate**.

> The verdict is a hard input to that gate: on **PASS** the approver may merge; on **BLOCK** merge is
> held, the filed `security` issues drive remediation, IMPLEMENTER is re-dispatched, and Security
> Analyst is re-invoked to re-verify after rework. The human's one decision is **approve & merge** vs
> **request changes / reject** — and this report (its verdict, OWASP walk, and issue links) is the
> evidence. A BLOCK is a terminal verdict for this role: finding delivered, the gate enforces the hold.

---

**HOW TO EMIT**
1. Write this file to `.tacticl/pdlc/{runId}/security-report.md` on the working branch.
2. Commit it to the PR branch (`git add .tacticl/pdlc/{runId}/security-report.md && git commit`) — the file rides inside the PR; git history is the versioning.
3. Append/update the entry in `.tacticl/pdlc/{runId}/manifest.json`:
   ```json
   {
     "artifact_id": "artifact_security_report",
     "type": "security-report",
     "agent": "Security Analyst",
     "path": ".tacticl/pdlc/{runId}/security-report.md",
     "title": "<your title>",
     "summary": "<one line: verdict + finding counts>",
     "sha": ""
   }
   ```
4. Post the report to the PR (`gh pr comment`) so reviewers see it inline, and file the `security`-labelled GitHub issue for each HIGH/CRITICAL finding before completing with your PASS / BLOCK summary.
