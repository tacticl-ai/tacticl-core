# Architecture Synthesis — 2026-05-20

Six architects pressure-tested the conversation-plane + pipeline architecture for Tacticl. This document is the synthesized take and the action plan that came out of it.

## The architects

| Critique | One-line take |
|---|---|
| [ai-agent](2026-05-20-ai-agent-critique.md) | Stateless turn handler + Mongo state + typed tool calls (Option D). Persistent subprocess is the trap. |
| [ops](2026-05-20-ops-critique.md) | Lock down Docker socket, cost cap, Vault auto-unseal, off-host backups. |
| [workflow-temporal](2026-05-20-workflow-temporal-critique.md) | Temporal is right, but split `IntakeWorkflow` from `PipelineWorkflow`. Run Temporalite not full cluster. |
| [security](2026-05-20-security-critique.md) | You're building RCE-as-a-service. Egress allowlist + per-user MCP isolation are NOT deferable. |
| [platform-sre](2026-05-20-platform-sre-critique.md) | Two-host topology is fine, but off-site backups of Vault are the non-negotiable. |
| [incremental-delivery](2026-05-20-incremental-delivery-critique.md) | Zero users at month 11. Fix the bug. Type into Telegram tonight. Stop architecting. |

## The genuine consensus across all six

Strip out the framing and they all converge on a surprisingly small list:

**Things they all agree are real:**

1. **Typed tool calls instead of regex markers** — `<<<START>>>` / `<<<PROPOSE>>>` / `<<<CREATE_REPO>>>` markers are debt being designed in.
2. **Cost ceiling enforced somewhere obvious** — Even the incremental architect says hardcoded $5/day counter.
3. **Backups off-host** — Platform/SRE makes it the non-negotiable. Security implies it (Vault loss = product death).
4. **Docker socket lockdown via allowlist** — Ops + Security flag this. Platform/SRE nuance: the socket isn't the worst boundary; the spec interpolation is.
5. **Egress allowlist on agent containers** — Security explicit. Implicit in everyone else's "you're running arbitrary code" framing.

## The disagreement (and the disagreement IS the signal)

- Workflow architect says Temporal-yes-split-into-two.
- Platform architect says Temporalite-not-full-cluster.
- Incremental architect says Temporal-not-now-just-fix-the-bug.

The disagreement maps perfectly to time horizon. **At month 12 with zero users, the incremental architect wins. At 50 concurrent Sparks, the workflow architect's split-workflows answer is what you need. Platform architect is the migration path between them.**

## The brutal thing all six touched

Each one independently said the same thing in different words: **"You haven't proven the product. You're optimizing the substrate."**

That's a 6-for-6 signal. Not architect groupthink — they came from different angles and landed on the same thing.

## What we're actually doing

### Tonight (~3 hours)

1. **Revert the architectural pivot.** Go back to the `(prompt, history, model)` arg-order fix at three call sites.
2. **Add a dumb $5/day cost counter** — Spring `@Component`, in-memory `ConcurrentMap` keyed by `userId+date`, rejects when over. 20 lines.
3. **Type into Telegram. Have a conversation. Screenshot failures.**

(Note: typed tool calls replace markers — recognized as foundational by 4 of 6 — but deferred because they require non-trivial system-prompt + LLM-output-handling changes. Markers stay tonight; tool calls are the next refactor when we know what's actually broken.)

### This weekend (~6 hours)

4. **Wire to the existing 12-role pipeline configured to PM + IMPLEMENTER + TESTER only**, throwaway GitHub repo. Watch it produce a (probably broken) PR.
5. **Write down what disappointed you.** Triage into "blocks the product thesis" vs "is annoying."

### Before user #2 (not user #1 — you're user #1)

6. **Off-site backups to Backblaze B2 or Cloudflare R2.** ~$5-10/mo. Includes Vault snapshots.
7. **Docker socket allowlist** — hardcoded role→spec table in arbiter's `ContainerManager`.
8. **Egress allowlist on agent containers** — Docker network with filtering proxy.
9. **Typed tool calls replace markers** — `ask_clarification`, `propose_repo`, `create_repo`, `finalize_spec` as Anthropic tool_use blocks instead of regex-matched text.

### When you hit ~10 active Sparks OR when state corruption costs you a real conversation

10. **Temporalite, not full Temporal.** Single-container, SQLite-backed, same Java SDK.
11. **Then split into `IntakeWorkflow` + `PipelineWorkflow`** as the workflow architect described.

### Defer indefinitely

- Auto Vault unseal (manual is fine for solo)
- ContinueAsNew patterns (not needed until many turns/Sparks)
- MCP per-user OAuth servers (not needed until connectors and users)
- Multi-instance arbiter (not needed until 100+ concurrent)
- Real staging environment (after the first 5 users, not before)

## Open follow-up items (tracked, not blocking)

- Vault Shamir shard recoverability — share with a designated trusted person (security architect non-negotiable for "what if founder is hit by a bus")
- PASETO key rotation runbook — write the 5-sentence procedure before it's needed
- Loki log redaction filter for secrets — synthetic-token test before next user onboards
- Group-chat confused-deputy fix in `MemberPermissionService` — gate tool invocations on spark owner's permissions, not message sender's

## The intellectual honesty note

The pivot away from "fix the arg-order bug and smoke test" toward "design the perfect architecture with Temporal" was premature. We were 20 minutes from a working smoke test and spent hours on architecture instead. The architects' critiques are valuable — and saved here for when they're actually needed — but the immediate action is the smallest possible patch to unblock a real conversation.

Build → measure → learn. Then revisit this synthesis with real data.
