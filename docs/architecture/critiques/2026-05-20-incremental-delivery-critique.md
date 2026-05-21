# Incremental Delivery / Pragmatist Critique

**Date:** 2026-05-20
**Persona:** Senior architect specializing in shipping software (the person who talks teams out of Kubernetes and into Heroku)
**Topic:** The simplest version of Tacticl that could possibly work, and how soon real users could be on it

I've read the plan. I've read the prior critiques. They're all technically correct. They're also, collectively, the reason you don't have a single user after 11 months.

Let me be the asshole in the room.

## 1. The first-user-first-week reality check

You have **zero users**. You've had zero users for 11 months. The marginal cost of waiting two more weeks for Temporal + auto-unseal + Docker socket lockdown isn't "two weeks of delay" — it's **two weeks of not learning what your product actually is**. Every day without a real conversation is a day you're optimizing for an imaginary user.

The lightest version that gets you typing into Telegram tonight:

1. Fix the Anthropic arg-order bug at the three call sites. That was the actual blocker. That fix was 20 minutes from done.
2. Confirm the Vault OAuth account is correct (you already did this — keep the fix).
3. Make sure cidadel-arbiter is actually running and the gRPC call lands. Health check.
4. Hard-code a daily $5 cost ceiling per conversation in the simplest possible place — one counter, one check, one log line. Not a service. Not a manager. A counter.
5. Type into Telegram. Have a conversation. See what breaks.

Defer: Temporal, Docker socket lockdown, Vault auto-unseal, Mongo collection migration, conversation message splitting, multi-persona orchestration, typed tool calls (use whatever you have — markers, JSON, whatever ships), connectors beyond what's already wired, the 12-role pipeline integration with the conversation, persistence redesign, Postgres backups, all of it.

**The pivot away from the arg-order fix was premature.** You were 20 minutes from a working smoke test. Instead you spent hours debating Temporal. That's the trap. Fix the bug, get the conversation working, *then* learn what's actually broken — because I guarantee you, what's broken in production won't be what the architects predicted.

## 2. What can be deferred without regret

- **Temporal**: bucket D. Defer until pain forces it. Cursor didn't use it. v0 didn't use it. Replit Agent didn't until they had real scale problems. You can hand-roll a state machine in Mongo for 1000 users. You don't have 1.
- **12-role pipeline integration with conversation**: bucket C. The pipeline exists. Leave it. Ship the conversation hooked to a **3-role MVP** (PM gathers requirements, IMPLEMENTER writes the code, TESTER runs it) and see if anyone wants the output. The other 9 roles are theater until proven otherwise.
- **Docker socket lockdown**: bucket B. You're the only user. You're not going to pwn yourself. Lock it before you onboard user #2.
- **Vault auto-unseal**: bucket B. At 1 user, manual unseal at 3AM is fine because there's no 3AM traffic. Fix before user 10.
- **Mongo 16MB doc cliff**: bucket C. You'd need ~50,000 messages in one conversation to hit it. You don't have 50.
- **Per-conversation cost cap**: bucket A. But the version you need is `if (todaySpend > 5) reject()`. Not a manager.
- **Hetzner two-host topology**: works fine to ~1000 users. Move when you feel pain, not before.

## 3. The sunk cost trap

Yes. You are doubling down. The 12-role pipeline, the cidadel-arbiter abstraction, the cross-product PASETO SSO, the ai-engine framework with 19 SDLC steps — these are beautiful systems that have **never shipped a PR to a real user's repo**. Every architecture decision is being made to protect investments in abstractions that haven't proven their value.

"Add Temporal" is **not incremental**. It touches the workflow boundary, the conversation handler, the pipeline orchestration, the worker model, and the deploy topology. That's a rewrite wearing an incremental costume. A real incremental change is "fix the bug, ship, observe." Temporal is "rebuild the spine before we know if the body works."

The "throw away if it doesn't work with Claude Code CLI" sentiment is gone. The architecture is now load-bearing on the assumption that all of this stays. That's the moment to be suspicious.

## 4. Industry comparisons

- **Cursor**: shipped a VS Code fork with a simple LLM proxy. No Temporal. No durable workflows. They added durability when they had paying customers complaining about lost sessions.
- **v0**: Vercel functions + Postgres. That's it. Workflow orchestration came **way** later.
- **Replit Agent**: shipped on their existing container infra. Added durable execution after PMF, not before.
- **Devin**: opaque, but everyone who's looked at it says the magic is in the loop and the eval harness, not the infra.
- **Sweep**: literally a GitHub App + Python script + Modal. They got users, then added complexity.

None of them used Temporal on day 1. Inkeep, the one that's closest to "durable agentic backend," added their workflow engine in year 2 after they had revenue.

"First prototype working in chat" to "first paying user" at these companies was typically **4-12 weeks**. You're at month 11 with no prototype talking to anyone.

## 5. The solo founder constraints

A team of 5 builds the planned architecture in 3 months. A solo founder builds it in 9 months, ships nothing, and runs out of motivation. The plan is **structurally infeasible for one person** and the architects giving advice aren't accounting for that.

What goes wrong: operational toil compounds. Every new piece of infra (Temporal cluster, MinIO, Postgres backups, ClickHouse, Qdrant, Neo4j, full Grafana stack) is something *you* have to keep running at 2AM, *you* have to upgrade, *you* have to debug when Hetzner has a network blip. You already have too much infrastructure for one person, and the plan adds more.

You're in **analysis paralysis with architect-assist**. Architects optimize for correctness; founders optimize for learning. Right now you need to learn, not be correct.

Weekend scope: working Telegram conversation, hardcoded cost cap, conversation persisted as a flat doc.
3-day scope: + 3-role pipeline hooked to a real GitHub repo (yours), producing a real PR (probably broken, that's fine).
1-week scope: + you've had 20 real conversations with it, written down the top 5 ways it disappoints you, and you know what to build next.

## 6. The Telegram-only constraint

The plan over-designs for multi-channel. Connectors before conversation works = premature. Multi-user before single-user works = premature. You don't need Twitter, Gmail, or anything beyond "Telegram in, GitHub PR out" to validate the core thesis.

## 7. The "what if Temporal is wrong" question

Failure modes without Temporal at 1-10 users: a conversation crashes mid-flow and you lose it. **You're the user. You'll just retype it.** That's it. That's the failure mode. Adding Temporal in 3 months: ~1 week of work once you actually need it, with the benefit of knowing what state actually needs to be durable. Adding Temporal now: 2-3 weeks, designed for an imagined workload.

"Temporal saves us from hand-rolling state machines" is true at 1000 concurrent workflows. At 1, it's solving an imaginary problem with real operational overhead.

## 8. If I were the founder

- **Weekend**: Telegram conversation working end-to-end, persistence in a flat Mongo doc, hardcoded cost cap, deployed.
- **Two weeks**: Conversation reliably triggers the 3-role pipeline against your own GitHub repo. You've shipped at least one PR Tacticl wrote.
- **Two months**: 10 friendly users on it. You've thrown away at least one major abstraction that turned out to be wrong. *Now* you consider Temporal.
- **Would not build**: Temporal, ClickHouse, Neo4j, Qdrant (until RAG is a proven need), 12-role expansion, multi-channel, auto-unseal automation, cross-product SSO, the cidadel-arbiter LLM abstraction in its current form. Several of these are already built — fine, leave them, but don't add anything in this category.

## 9. Existential pushback

"AI builds my software autonomously" is a real market — but it's brutally competitive (Devin, Cursor Composer, Claude Code itself, Cline, Aider) and the winners are the ones who ship loops that actually produce working code. Nobody wins on architecture. They win on **eval quality and iteration speed**.

If you're building Tacticl because *you* want a Telegram-accessible AI dev — fine, build the version *you'd use*. That's a clear product. If you're building it because you think others will want it, you need their feedback in week 1, not month 12.

## 10. The hard recommendation

Stop the architecture conversation. Today. Fix the arg-order bug. Get a conversation working in Telegram tonight. Hardcode a $5/day cap. Hook it to a 3-role pipeline pointing at your own GitHub. Ship a PR — even a bad one — by Sunday night. Then, and only then, look at the plan again with real data.

---

**If I were the founder, here's what I would do this weekend, in this exact order:** (1) Revert the architectural pivot and finish the arg-order fix at the three call sites — 30 minutes. (2) Add a dumb in-memory counter that rejects new LLM calls when today's conversation has spent >$5 — 20 minutes. (3) Verify cidadel-arbiter is healthy with a curl/grpcurl check, fix if not — 30 minutes. (4) Have a real conversation in Telegram, screenshot every failure, do not fix anything yet — 1 hour. (5) Triage the screenshots into "blocks every conversation" vs "annoying" — 15 minutes. (6) Fix only the blockers, ignoring everything the architects flagged — 2-4 hours. (7) Wire the conversation's output to the existing 12-role pipeline but configure it to only run PM + IMPLEMENTER + TESTER, pointed at a throwaway GitHub repo you own — 3-4 hours. (8) Trigger it from Telegram, watch it produce a (probably broken) PR, write down exactly how it disappointed you — 1 hour. (9) Go to sleep without touching Temporal, Docker lockdown, Vault auto-unseal, or any of the planned foundation work. Monday, decide what to build next based on what you learned this weekend, not what architects told you in advance.
