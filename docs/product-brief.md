# Product Brief

A personal AI assistant that remotes into the user's devices and executes work across them — social automation, research, content/video generation, reminders, and full software development lifecycles.

---

## 1. Problem

Modern "AI assistants" fall into two camps, and both fail the user:

1. **Chatbots with tools** — Clever at conversation, weak at execution. They can draft a post but can't actually publish it across six accounts on a schedule. They can suggest code but can't ship it end-to-end. They live inside one app and never touch the user's real environment.
2. **Rigid automation platforms** (Zapier, n8n, Make) — Can execute, but require the user to become a workflow engineer. Every new capability is a configuration project. No reasoning, no adaptation, no memory of what worked last time.

The gap is a single agent that can:

- **Understand intent** from a voice or chat message,
- **Classify the depth** of work required (a tweet vs. a new product feature),
- **Orchestrate multiple specialized agents** when the task is complex,
- **Execute on the user's own devices** (phone, desktop) when the work needs local context, credentials, or browser state — and execute in the cloud otherwise,
- **Learn from every run** so the next one is cheaper, faster, and better.

No single product covers all five. Users stitch together ChatGPT, Zapier, Claude Code, Buffer, and three browser extensions — and still can't leave the loop.

---

## 2. Solution

A unified agent platform structured as **four collaborating layers**:

1. **Conversation layer** — Chat and push-to-talk. The *only* input surface. Every command becomes a first-class "spark" (a tracked unit of intent).
2. **Classification + orchestration layer** — A two-stage classifier decides the *type* (social, research, code, devops, creative, data) and the *depth* (single-agent loop, named playbook, or full multi-role pipeline). An orchestrator then manages the lifecycle of the spark through to completion.
3. **Execution layer** — A pool of specialized **role agents** (Product Manager, Architect, Implementer, Reviewer, Tester, DevOps, Security, Technical Writer, etc.) for complex work, or a single-agent tool loop for simple work. Execution can happen in the cloud *or* be dispatched to one of the user's own devices over WebSocket.
4. **Learning layer** — A git-managed knowledge vault and a dedicated retrospective agent that captures lessons from every run. Knowledge is both machine-readable (for future agent runs) and human-readable (for user oversight).

Underneath these layers sits a **centralized LLM arbiter** (a separate platform service) that routes every model call, isolates workspaces, handles credentials, and tracks cost. No agent in the product talks to a model provider directly.

The result is one assistant that can handle *"post this video to Instagram and X at 6pm"* and *"build and ship a new billing feature"* — and treats them as the same shape of problem at different depths.

---

## 3. Vision and Measures of Success

### Vision

**"One assistant, every device, every task."** The user speaks or types once. The system figures out what kind of work it is, where it should run, which specialists to involve, and how to verify the result — and it gets measurably better at the user's specific style every week.

### Measures of Success

**Product health**

| Metric | Target |
|---|---|
| Time from intent to first useful action | < 3 seconds (sync path), < 30 seconds (async pipelines) |
| % of commands resolved without user re-clarification | ≥ 85% |
| % of complex tasks completed without human-checkpoint escalation | ≥ 70% at steady state |
| Device execution success rate (when a device is online) | ≥ 95% |

**Learning**

| Metric | Target |
|---|---|
| Cost per completed pipeline, month over month | Monotonically decreasing for steady-state task types |
| Rework rate per role | < 15% with 3-iteration hard cap |
| Knowledge-vault entries auto-written per pipeline | ≥ 1 on average |
| User-approved vs. user-rejected auto-learnings | ≥ 80% approved |

**Trust**

| Metric | Target |
|---|---|
| Tier-1 (mutating) actions executed without user confirmation | **0** |
| Tier-2 (financial) actions executed without 2FA | **0** |
| Unauthorized domain access | **0** |

**Business**

| Metric | Target |
|---|---|
| Weekly active users running ≥ 1 multi-role pipeline | Leading indicator of power-user depth |
| Devices paired per user | Indicator of platform lock-in and utility |
| Monthly spend ceiling adoption | Indicator that users trust the assistant with money |

---

## 4. Architecture

### 4.1 System Shape

```
 ┌──────────────────────────────────────────────────────────────┐
 │                      CONVERSATION LAYER                      │
 │       Mobile app  ·  Web dashboard  ·  Push-to-talk          │
 └─────────────────────────┬────────────────────────────────────┘
                           │  POST /v1/agent/command
                           ▼
 ┌──────────────────────────────────────────────────────────────┐
 │                CLASSIFICATION + ORCHESTRATION                │
 │  Spark Service  →  Type Classifier  →  Depth Classifier      │
 │                                                              │
 │  Depth = SIMPLE  ─────────────►  Single-agent loop           │
 │  Depth = PLAYBOOK ────────────►  Named workflow (subset)     │
 │  Depth = FULL_PIPELINE ───────►  12-role multi-agent         │
 └─────────────────────────┬────────────────────────────────────┘
                           │
         ┌─────────────────┴─────────────────┐
         ▼                                   ▼
 ┌───────────────────┐              ┌────────────────────┐
 │  CLOUD EXECUTION  │              │  DEVICE EXECUTION  │
 │  Role agents run  │              │  WebSocket dispatch│
 │  on managed infra │              │  to user's device  │
 └─────────┬─────────┘              └──────────┬─────────┘
           │                                   │
           └────────────────┬──────────────────┘
                            ▼
 ┌──────────────────────────────────────────────────────────────┐
 │          SHARED PLATFORM  (separate repo — cidadel)          │
 │   LLM Arbiter (gRPC)  ·  Auth  ·  Token issuance  ·  Vault   │
 │   — Routes ALL model calls, isolates workspaces, tracks cost │
 └──────────────────────────────────────────────────────────────┘
                            ▲
                            │ reads / writes
 ┌──────────────────────────┴───────────────────────────────────┐
 │                       LEARNING LAYER                         │
 │  Retrospective agent  ·  Git-managed knowledge vault         │
 │  Per-role Maps of Content  ·  Rework + cost telemetry        │
 └──────────────────────────────────────────────────────────────┘
```

### 4.2 Spark Lifecycle

Every chat message is a **spark**. There is no other way to create one — no manual form, no API shortcut. This gives the system a single audit log and a single unit of cost accounting.

```
chat / voice input
      │
      ▼
 [ Spark created ]   ── state: PENDING
      │
      ▼
 [ Type classifier ]   → social | research | code | devops | creative | data
      │
      ▼
 [ Depth classifier ]  → SIMPLE | PLAYBOOK | FULL_PIPELINE
      │                   (six-dimension rubric: scope, risk, domain breadth,
      │                    integration surface, test complexity, reversibility)
      ▼
 [ Routing decision ]  → cloud or user's device?
      │                   (based on user's execution preference + device online state)
      ▼
 [ Execution ]         → single-agent loop  OR  multi-role pipeline
      │
      ▼
 [ Checkpoints ]       → Tier 1 mutations / Tier 2 financial → user approval
      │
      ▼
 [ Retrospective ]     → lessons written to knowledge vault (always runs)
      │
      ▼
 [ Completed ]         → result returned to chat, receipt stored
```

### 4.3 The Three Execution Shapes

| Shape | When | How |
|---|---|---|
| **SIMPLE** (single-agent loop) | One-off tasks that fit in a single LLM turn with tools — post a tweet, summarize a page, set a reminder. | One orchestrator, one LLM, a tool registry (~20+ skills), at most a few tool-use rounds. Sync-with-timeout: if it runs long, it flips to async and streams back over WebSocket. |
| **PLAYBOOK** | Recurring structured flows — bug fix, small feature, UI change, docs-only, infra change, security patch. | A subset of role agents configured as a named workflow. Data-driven — playbook definitions are config, not code. |
| **FULL_PIPELINE** | Net-new features or systems with real risk and cross-cutting impact. | All 12 role agents — Product Manager, Researcher, Architect, Designer, Planner, Implementer, Reviewer, Tester, Security Analyst, Technical Writer, DevOps, Retrospective Analyst — with rework loops, quality gates, and mandatory human checkpoints for mutating or financial actions. |

The depth classifier **cannot** route code or devops work to SIMPLE — that class of work always gets at least a PLAYBOOK, as a safety floor.

### 4.4 The Orchestrator / Role Agents Relationship

The **orchestrator** is a lifecycle engine, not an agent. It:

- Reads the playbook config,
- Runs roles in dependency order (with parallel groups where declared),
- Persists every role's output as an artifact,
- Enforces a 3-iteration rework cap per role,
- Surfaces checkpoints to the user,
- Emits events over WebSocket and push for live UI updates,
- Never makes product decisions — those belong to roles.

Each **role agent** is a specialized LLM instance with a dedicated system prompt, a filtered tool set, and a pre-loaded "Map of Content" (its personal index into the knowledge vault). A role sees only what it needs to see, which keeps cost and context low and makes the pipeline genuinely composable.

### 4.5 Cloud vs. Device Execution

The product is explicit that **cloud and device are peers** — neither is a fallback:

- **Cloud execution** runs on managed infrastructure with browser automation, web search, web-page readers, social-platform clients, and a video-generation pipeline. Used when no device is online, or when the user prefers cloud.
- **Device execution** dispatches the spark over WebSocket to one of the user's own desktops or phones. The device can decompose it into sub-tasks ("tactics"), execute locally (terminal commands, browser clicks, file ops), request credentials, and report progress. Desktops can additionally run a CLI-driven coding agent in-place with per-task workspace isolation.

Routing is controlled by a user preference (`CLOUD_ONLY`, `CLOUD_FIRST`, `DEVICE_FIRST`) and a device-scoring function (charging state, battery, device type, capability match).

### 4.6 The Arbiter (lives in the shared platform repo, not the product)

The **LLM Arbiter** is a **platform-level service in the cidadel shared platform** — not part of the product codebase. Every model call from every agent, every role, every classifier goes through it over gRPC. It provides:

- **Unified routing** across 25+ models / 7 providers (Anthropic, OpenAI, Grok, Gemini, Llama, Mistral, Cohere),
- **Three engine types** — `api` (single-turn), `agentic` (multi-turn tool loop), `cli` (full CLI-driven coding agent),
- **Per-request workspace isolation** for CLI engines (`mkdtemp` + shallow clone + execute + teardown), so concurrent pipelines never leak context into each other,
- **Credential management** (Vault-backed),
- **Cost tracking and budget enforcement** (per-request `--max-cost`, per-pipeline ceiling, per-user monthly spend limit),
- **Rate limiting** and circuit breaking.

Because the arbiter is shared infrastructure, *every* product built on the platform benefits from the same routing, cost model, and credential hygiene. The product does not ship its own LLM clients.

### 4.7 The Learning Layer

Learning is not a "nice to have" bolt-on — it is a first-class role in the pipeline.

**Retrospective agent (always runs).** After every pipeline, a dedicated role reviews the run and writes two kinds of output:

1. **Auto-committed facts** — safe, factual observations (conventions observed, entities touched, tool-call outcomes) go directly into an `auto/` folder of the knowledge vault.
2. **Proposed learnings** — judgment-laden lessons ("this kind of task benefits from skipping the Designer role") go into a `proposed/` folder for human review.

Human-approved learnings graduate to an `approved/` folder and become load-bearing context for future runs.

**Knowledge vault.** A git-managed wiki (Obsidian-style markdown with wikilinks). Each role has its own **Map of Content** — a hand-maintained index telling the role *what to care about* before it starts work. The vault grows incrementally; bad entries are reverted via git, which keeps accountability cheap.

**Telemetry-driven learning.** Separate from the vault, the system stores:

- **Rework events** — which role asked for another pass, why, how many iterations,
- **Checkpoint outcomes** — what the user approved, rejected, modified,
- **Cost per role per task type** — feeds the classifier's future routing decisions.

Over time the classifier itself is expected to consume this telemetry to improve depth and routing decisions.

### 4.8 Safety Tiers

Every action the agent can take is labeled:

| Tier | Examples | Policy |
|---|---|---|
| **Tier 0** — read-only | Search, browse, check schedule, read photos | Auto-execute |
| **Tier 1** — mutations | Publish post, send message, edit, delete, git push | Requires user confirmation |
| **Tier 2** — financial | Purchases, subscriptions, spend over threshold | Requires 2FA, gated by monthly spend limit (default $0) |

Safety tiers are enforced at the tool-registry level — not as a prompt instruction to the agent. An agent that *wants* to skip a checkpoint cannot.

---

## 5. Comparison with Market

| Capability | This product | ChatGPT / Claude (chat) | Claude Code / Cursor | Zapier / n8n / Make | Buffer / Hootsuite | Rabbit / Humane |
|---|---|---|---|---|---|---|
| Natural-language entry | Yes | Yes | Partial (code focus) | No — flow-builder UI | No | Yes |
| Executes on user's own devices | **Yes** (paired desktop + mobile) | No | Local only, single machine | No | No | Device-local only |
| Cloud execution fallback | **Yes** | N/A | No | Yes | Yes | No |
| Multi-role pipelines (PM → Arch → Impl → Review → Test) | **Yes** (12 roles, 8 playbooks) | No | No | No — no notion of role | No | No |
| Depth classifier (routes simple vs. complex) | **Yes** | No | No | No | No | No |
| Centralized LLM arbiter (multi-provider, cost-tracked) | **Yes** (via shared platform) | Single provider | Single provider | N/A | N/A | Single provider |
| Per-request workspace isolation for coding | **Yes** | No | Partial | N/A | N/A | No |
| Social publishing across platforms | Yes | Via plugin | No | Yes | Yes | No |
| AI video generation | Yes | No | No | No | No | No |
| Git-managed learning vault with human review | **Yes** | No (opaque memory) | No | No | No | No |
| Per-role Maps of Content (targeted context) | **Yes** | No | No | No | No | No |
| Safety tiers enforced at tool layer (not prompt) | **Yes** | Prompt-level only | Prompt-level only | N/A — no agency | N/A | Prompt-level |
| Per-pipeline + monthly spend ceilings | **Yes** | No | Partial | No | No | No |
| Device routing intelligence (battery, charging, capability) | **Yes** | No | No | No | No | No |

### Positioning

- **Against chat assistants** — They reason; we reason *and* execute across the user's devices, with real cost controls and real safety tiers.
- **Against coding agents** — They ship code from one machine; we ship outcomes across the user's life (code, content, social, research) through a consistent pipeline architecture, with coding as one vertical.
- **Against workflow automation** — They require the user to design the workflow; we classify intent and select the workflow automatically, while still letting power users define custom playbooks.
- **Against social-media schedulers** — They queue posts; we generate, schedule, publish, *and* retrospect on performance — all as one spark.
- **Against AI hardware (Rabbit, Humane)** — They tie the assistant to one device; we treat every device the user already owns as a worker in a pool.

### Moats

1. **The arbiter + workspace isolation** — infra most competitors don't have the shape to build, because their product is the model rather than the routing layer.
2. **The multi-role pipeline** — requires a library of role-specific prompts, tools, and knowledge that compounds with usage.
3. **The learning vault** — a per-user, per-team knowledge asset that gets more valuable every week and is hard to replicate after the fact.
4. **Device pairing** — two-sided: once users pair three devices, switching cost is substantial.

---
