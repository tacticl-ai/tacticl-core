# AI Agent Architect Critique — Conversation Plane Design

**Date:** 2026-05-20
**Persona:** Senior AI agent architect (Cursor / Devin / Cognition / Sweep lineage)
**Topic:** Conversation plane for Tacticl — Options A/B/C and proposed Option D

## The frame is wrong before you pick an option

You're conflating three things that have different lifecycles and should be designed separately:

1. **The conversation state** (messages, slots, the evolving spec) — this is *data*, lives in Mongo, survives everything.
2. **The reasoning loop** (LLM call that reads state + last user message + emits next assistant turn) — this is *stateless compute*, should be re-creatable on every turn.
3. **The execution sandbox** (filesystem, CLI tools, OAuth-scoped credentials, MCP servers) — this is *infrastructure*, expensive to spawn, cheap to keep warm.

Option A fuses (2) and (3) into one persistent SDK subprocess and uses the SDK's in-memory context as the source of truth for (1). That's the trap. **Any architecture where the canonical conversation state lives inside a subprocess is broken by week three.** Subprocesses die. Containers get evicted. The Claude Code SDK has a context window. Your founder said the conversation "spans hours" — that's *exactly* when persistent-subprocess designs fail, because the subprocess outlives nothing useful and dies right when you need it.

Devin had this exact bug for months in 2024. They went from "persistent session" to "rehydrate on every turn from durable state" and never looked back.

## Option-by-option

### Option A — Persistent OrchestratorSession per conversation

**Fatal flaws:**
- Idle subprocesses pinned to a node = a memory leak with extra steps. 10K active conversations = 10K subprocesses each holding ~200MB resident + a Claude Code session. Your arbiter node count is now driven by *conversation count*, not *active reasoning*. That's the wrong axis to scale.
- Multi-channel (Telegram + web) appending to the same Spark is now a distributed-locking problem. Which node owns the subprocess? If the user types in web while Telegram's subprocess is mid-stream, you either drop the message or you need a session router with sticky routing. Either is bad.
- Crash recovery is unsolved. The SDK context isn't durable. You have to either (a) replay all messages on restart (slow, breaks idempotency of tool calls already executed), or (b) accept the conversation forgets — which is the worst possible failure mode for a "build me a project over hours" UX.
- The Claude Code SDK in particular is *not* designed for hours-of-idle then resume. You'll fight permission prompts, hook lifecycle, and MCP server reconnects.

**When it's right:** never for chat. It's right for the *PDLC role containers* — short-lived (minutes), single-purpose, hot working directory. That's `ContainerManager`'s actual job and you already have it.

### Option B — Two personas (Researcher + Product Owner)

**Fatal flaws:**
- You're spawning Docker containers to do what is, 90% of the time, a single LLM call against the conversation history. Container cold start dominates first-reply latency. Telegram users will see a 3–6s delay before "are you thinking of CLI flags or a config file?" — that's not the cost-quality-latency triangle, that's just losing.
- "Researcher" and "Product Owner" as separate containers is fragmentation theater. There is zero evidence from production systems (Cursor, Sweep, Cognition, v0, Replit Agent) that splitting requirement-gathering into two LLM personas improves output quality. It mostly degrades it because each persona has half the context.
- You'd be reusing PDLC infrastructure for a fundamentally different workload. PDLC roles are *workers given a sealed spec*. Conversation is *open-ended state evolution*. Same tool, wrong job.

**When it's right:** if the "research" step were *actually* using web search, GitHub API, repo scanning — i.e., heavy tool-use with isolation requirements — then yes, you'd want a sandboxed researcher. But that's Option C territory.

### Option C — Hybrid

Closer to right, but still over-engineered. You don't need a *persistent* orchestrator. You need *no* orchestrator process at all between turns. Spawning ephemeral researcher containers on demand is fine and correct — that's just tool use.

## What actually wins — Option D

**Stateless turn handler + durable conversation state + ephemeral tool sandboxes.**

```
Telegram message
  → tacticl-core: append to Mongo conversation doc, push onto Spark
  → tacticl-core: arbiter.Generate(systemPrompt, fullHistory, tools=[user-scoped MCP tools])
  → arbiter: routes to claude-code-cli or anthropic-direct (your choice per turn)
        - if tools needed → spawn ephemeral container with workspace + MCP servers,
          run one Claude Code turn, capture output, kill container
        - if just reasoning → anthropic-direct, no container, ~400ms
  → response streams back via gRPC, tacticl-core writes to Mongo, sends to Telegram
```

**Why this wins concretely:**

1. **Cost-latency:** Pure-reasoning turns (the common case — "got it, what about auth?") hit `anthropic-direct` in 400–800ms. No container cold start. Cost is one LLM call. Tool-use turns pay the container cost only when needed.
2. **State:** Mongo is the source of truth. Crash recovery is trivial — next turn just reads the doc. Multi-channel is trivial — both Telegram and web write to the same Spark conversation array, and `Generate` always sees the full thread.
3. **Idle for hours:** zero cost. No subprocess pinned anywhere.
4. **Parallel users:** scales with arbiter node count for active reasoning, not active conversations.
5. **Pipeline handoff:** when the PM-style reasoning decides it's ready, it emits a *structured tool call* (`finalize_spec({title, repo_name, requirements, acceptance_criteria, tech_stack, risks})`). Tacticl-core validates the schema and calls `SubmitPipeline`. **Never let the handoff be a text marker like `START`** — that's a parse-error waiting to happen and IMPLEMENTER will hate you for the ambiguous spec. Make the spec a typed artifact, validated against a JSON schema in tacticl-core, before pipeline submission.

## On the persona question

"One persona doing gathering + synthesis + handoff" is **not** a quality problem in modern frontier models. Sonnet 4.5 and GPT-5 do this fine with a good system prompt and structured tool outputs. Cursor's chat is one persona. v0 is one persona. Replit Agent's planning phase is one persona. The multi-persona-conversation pattern is mostly residue from GPT-3.5-era systems where each call was too dumb to hold multiple roles. Don't cargo-cult it.

Where multi-persona earns its keep: *execution* (your 12-role PDLC). Not conversation.

## Connectors / skills future

This is the actual important question and your options barely address it. With 30+ user-scoped tools (Twitter, Gmail, GitHub, Drive…), you want:

- **One MCP server per connector, hosted by arbiter** (not tacticl-core, not the conversation subprocess). Arbiter owns OAuth refresh, rate limiting, credential scope per user.
- **The `Generate` call passes a user identity**; arbiter injects user-scoped MCP servers into the container or anthropic-direct toolset for that turn.
- Inline tool defs for the 5–10 universal tools (finalize_spec, ask_clarification, etc.). MCP for everything user-installable. Subagents are the wrong abstraction here — they're for *delegating sub-tasks*, not for *connecting to external APIs*.

Option A breaks this badly: the persistent subprocess holds OAuth tokens that rotate. You'll have mid-conversation auth failures and no clean way to refresh.

## On "just use a framework"

- **LangGraph**: tempting because conversation-as-graph maps cleanly. Real cost: you're now Python+Java, your durable state lives in LangGraph's checkpointer instead of your Mongo, and your team has to debug LangGraph's quirks at 2am. Pass.
- **OpenAI Assistants**: vendor lock-in on a product OpenAI has signaled they're deprecating in favor of Responses API. Hard pass.
- **Mastra / Pydantic AI / Inkeep**: all fine, all add a layer between you and the model that you'll fight when you need control. You already have arbiter — *arbiter is your framework*. Make it good.

The honest answer: you have arbiter, you have `Generate`, you have container infra. Adding a framework duplicates 60% of arbiter's job. Build on what you have.

## Founder's blind spots

1. "Tacticl should have nothing" is correct as a principle but is being used to justify a persistent-subprocess design that puts the *most stateful thing in the system* into the *least durable place*. Inversion.
2. Reusing PDLC `ContainerManager` for conversation feels like leverage; it's actually misapplication. Different workload shape.
3. The "START marker" handoff is technical debt being designed in on day one. Make it typed.

## What I would build, in order, starting Monday

Day 1–2: define the `ConversationSpec` JSON schema in tacticl-core (the artifact PDLC consumes). Day 3–5: ship the stateless turn handler — Telegram message → Mongo append → `arbiter.Generate` with full history and a small inline toolset (`ask_clarification`, `propose_repo_name`, `finalize_spec`) → reply. No containers in the conversation path yet. Week 2: wire `finalize_spec` to call `SubmitPipeline` with the validated spec, and add web-chat as a second writer to the same Spark conversation doc. Week 3: introduce ephemeral researcher container *as a tool* the turn handler can invoke (GitHub repo-name check, web search) — reuse `ContainerManager` but call it from the turn handler, not as a persona. Week 4: stand up the first three user-scoped MCP servers (GitHub, Google, Twitter) in arbiter with per-user OAuth, and pass them into `Generate` based on the user's connected skills. Leave `OrchestratorSession` exactly where it is — a PDLC escalation brain, `ENABLE_ORCHESTRATOR=false` in prod for conversation, forever.
