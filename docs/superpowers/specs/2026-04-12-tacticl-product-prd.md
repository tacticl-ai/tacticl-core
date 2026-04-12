---
name: Tacticl Product Requirements Document
description: Level 1 product PRD for all of Tacticl — features, personas, use cases, roadmap
type: engineering-spec
status: draft
date: 2026-04-12
author: Gabriel Jimenez
related-docs:
  - 2026-04-12-tacticl-system-architecture-sad.md
  - 2026-04-11-tacticl-pdlc-v2-prd.md
---

# Tacticl — Product Requirements Document

**Date:** 2026-04-12
**Version:** 1.0
**Status:** Draft
**Author:** Gabriel Jimenez
**Related docs:**
- [System Architecture](2026-04-12-tacticl-system-architecture-sad.md)
- [PDLC v2 Component Spec](2026-04-11-tacticl-pdlc-v2-prd.md)

---

## 1. Executive Summary

Tacticl is a personal AI assistant that remotes into all your devices and utilizes them as workers. It can handle any task you can do on your devices — social automation, web browsing, content generation, video creation, software development, research, reminders, and more. You describe what you want in natural language. Tacticl routes it to the right execution plane, tracks progress, and delivers results.

For software tasks, Tacticl runs a full Product Development Lifecycle (PDLC) pipeline: 12 specialized AI agents that design, implement, test, review, document, and deploy production-ready code — with the user in control at every key decision point.

---

## 2. Problem Statement

### 2.1 The Core Problem

Modern knowledge workers manage an increasing number of digital workflows: social presence, content creation, research, code, communications, and business operations. AI models are powerful enough to execute all of these — but they lack the infrastructure to act on your behalf across your real devices, accounts, and repositories.

Existing AI assistants can answer questions and generate text. They cannot:
- Take action across your actual devices (phone, laptop, desktop)
- Maintain a persistent understanding of your work and preferences across sessions
- Run a production-grade software development pipeline from spec to merged PR
- Schedule, batch, and coordinate multi-step workflows asynchronously
- Learn from past runs to improve quality over time

### 2.2 The Tacticl Solution

Tacticl is the execution layer between user intent and real-world action. It operates two execution planes:

**Cloud Agent** — Runs 24/7 on Cloud Run. Handles tasks that don't require a user's physical device (research, social publishing, PDLC pipeline, reminders).

**Device Agent** — Runs as a daemon on user's devices (macOS, Windows, Linux, iOS, Android). Handles tasks that require local execution (terminal commands, browser automation, file manipulation, desktop app control).

The two planes coordinate under a single chat + voice interface. The user issues commands in natural language. The system routes to the right plane, executes, and reports back.

---

## 3. Vision & Goals

### 3.1 Vision

Every person has an intelligent AI workforce running behind them — across every device they own, every account they control, every repo they maintain. Tacticl makes that real: one interface, one set of permissions, one running history, infinite execution.

### 3.2 Primary Goals

| # | Goal | Measure |
|---|------|---------|
| G1 | **Universal task execution** | Any task doable on a connected device, Tacticl can do for you |
| G2 | **Production-ready software output** | ≥85% of PDLC pipelines produce PRs passing CI on first run |
| G3 | **True device integration** | Commands execute on real devices, not in simulated environments |
| G4 | **User control at every decision point** | No irreversible action without user confirmation |
| G5 | **Continuous improvement** | System gets better with every run via retro learning loop |
| G6 | **Cross-session memory** | Persistent understanding of user's context, preferences, history |

---

## 4. Users & Personas

### 4.1 Primary: Solo Developer / Founder

- Builds products alone or on a small team
- Uses Tacticl to offload implementation, content, and research work
- Needs trust: every significant action requires transparency and confirmation
- Cares about code quality, maintainability, and consistency

### 4.2 Secondary: Product Manager

- Describes features in natural language
- Files GitHub issues; Tacticl picks them up via webhook and implements them
- Reviews resulting PRs without writing code
- Uses PDLC checkpoints to steer direction

### 4.3 Tertiary: Engineering Lead

- Sets quality bars via configurable rework limits, required critics, checkpoint gates
- Reviews weekly retro outputs, approves learnings
- Monitors pipeline cost, quality metrics, and rework rates

---

## 5. Feature Areas

### 5.1 Conversational Interface (Chat + Voice)

Every interaction starts here. Users issue commands by typing or speaking. Voice input is transcribed by Whisper API (~500ms). The command enters the Spark pipeline.

**Key capabilities:**
- Text chat via mobile, web, or desktop
- Push-to-talk voice (expo-av) on mobile
- Persistent conversation history within a session
- Multi-turn clarification before spark creation
- Inline progress updates as tasks execute

### 5.2 Cloud Agent

The cloud-side execution engine. Handles tasks that run entirely in the cloud without needing a physical device.

**Key capabilities:**
- 20+ skill handlers (web search, social automation, reminders, PDLC, research, media)
- Multi-model routing: Haiku for classification/simple tasks, Sonnet for content generation, Opus for PDLC roles
- Tool use (Claude tool_use protocol) with `ToolRegistry`
- Brave Search integration for web research
- Jina Reader for web page extraction
- Persistent agent memory (`users/{id}/agent_memory/`)
- Two-tier action safety: Tier 0 (auto-execute), Tier 1 (confirm before executing)

### 5.3 Device Agent

The on-device execution engine. Handles tasks that require real device access.

**Key capabilities:**
- WebSocket-based spark dispatch from cloud to device
- Tactic decomposition: one spark → multiple executable sub-tasks
- 9 command types: TERMINAL_CMD, OPEN_URL, CLICK_ELEMENT, FILL_FORM, SCREENSHOT, READ_FILE, WRITE_FILE, WAIT, NOTIFY
- Checkpoint flow: device pauses on ambiguous steps, requests user decision
- Claude Code CLI engine (desktop only, macOS/Windows/Linux): default execution engine for desktop devices, spawned as subprocess with isolated workspace
- Device routing intelligence: selects best device based on battery, charging state, capabilities
- Multi-device support: one user → many devices; task routed to best-fit device

### 5.4 PDLC Pipeline (Software Factory)

The most powerful feature. A 12-role AI pipeline that takes a user's software request from description to merged PR. See [PDLC v2 PRD](2026-04-11-tacticl-pdlc-v2-prd.md) for full specification.

**Key capabilities:**
- 12 specialized roles: PM, RESEARCHER, ARCHITECT, DESIGNER, PLANNER, IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST, TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST
- 8 playbooks: FULL_PDLC, BUG_FIX, SMALL_FEATURE, REFACTOR, INFRA_CHANGE, DOCS_ONLY, UI_CHANGE, SECURITY_PATCH
- v2: each role runs in isolated Docker container on Hetzner with pre-assembled workspace
- Multi-candidate generation (IMPLEMENTER: 3 candidates by default, CRITIC selects best)
- TDD enforcement: TESTER writes failing tests before IMPLEMENTER writes code
- Multi-critic consensus: REVIEWER + TESTER + SECURITY_ANALYST must all approve before deploy
- User checkpoints at mandatory gates (after PM, after ARCHITECT, before deploy)
- Retro learning loop: weekly analysis of past runs → proposed learnings → approved learnings flow into future workspace assembly

**Three pipeline tiers:**
- `SIMPLE` — single agent loop (CloudOrchestratorService)
- `PLAYBOOK` — named workflow (subset of roles)
- `FULL_PDLC` — complete 12-role pipeline

### 5.5 Social Automation

**Key capabilities:**
- Publish to Twitter/X, LinkedIn, Instagram
- Google Photos as media source (read-only)
- AI video generation via SiliconFlow / Wan 2.2
- Post scheduling, batch publishing, content templates
- OAuth-based platform connections
- Agent skill for composing + scheduling posts
- Tier 1 confirmation before any publish action

### 5.6 Research & Browsing

**Key capabilities:**
- Web search via Brave Search API (independent index, not Google)
- Web page extraction via Jina Reader (URL → clean markdown)
- Both surfaced as agent skills (Tier 0, auto-execute)
- Results fed back to Claude for synthesis
- Domain allowlist/blocklist (user-configurable per device)

### 5.7 Notifications & Reminders

**Key capabilities:**
- FCM push notifications (iOS + Android)
- Scheduled reminders via `agent_reminders` collection
- Pipeline checkpoint notifications (pause → user decision → resume)
- Publish success/failure push notifications

### 5.8 Repository Management

**Key capabilities:**
- GitHub repo connection via `manage_repo` skill
- Grant/revoke repo access for PDLC execution
- GitHub webhook receiver for issue-triggered PDLC
- PR creation on user's repos as PDLC output
- Auto-merge option (opt-in per repo, requires CI green)

---

## 6. Spark Lifecycle

Every user command becomes a **Spark** — the single top-level entity for all user requests.

```
Chat/Voice → POST /v1/agent/command
    → SparkService.createSpark() [always]
    → SparkClassifierService auto-classifies: code | social | research | devops | creative | data
    → Route:
        a) Device online → SparkDispatchService → device → tactics
        b) No device    → CloudOrchestratorService
        c) PDLC spark   → PdlcClassifierService → tier selection → arbiter submission
```

**Spark states:** `PENDING → ROUTING → QUEUED | EXECUTING → CHECKPOINT → COMPLETED | FAILED | CANCELLED`

---

## 7. Functional Requirements

### 7.1 Input Channels
- **FR1.1:** System MUST accept text commands via `POST /v1/agent/command`
- **FR1.2:** System MUST accept voice input — Whisper API transcription, output feeds into FR1.1
- **FR1.3:** System MUST receive GitHub webhook events at `POST /v1/webhooks/github` and convert qualifying issues to sparks
- **FR1.4:** System MUST support scheduled sparks via cron-style expressions

### 7.2 Spark Execution
- **FR2.1:** Every command MUST create a Spark (even simple queries)
- **FR2.2:** `SparkClassifierService` MUST auto-classify spark type using Claude Haiku
- **FR2.3:** System MUST route to device if any device is online and capable
- **FR2.4:** System MUST fall back to cloud execution if no capable device is available
- **FR2.5:** `code` and `devops` sparks MUST go through `PdlcClassifierService` for tier selection

### 7.3 PDLC Pipeline
- **FR3.1–3.N:** See [PDLC v2 PRD](2026-04-11-tacticl-pdlc-v2-prd.md) Section 7 for complete PDLC functional requirements

### 7.4 Social Automation
- **FR4.1:** Posts MUST follow state machine: `DRAFT → QUEUED → PUBLISHING → PUBLISHED | FAILED`
- **FR4.2:** Any publish action MUST require Tier 1 confirmation (user approve before executing)
- **FR4.3:** System MUST support scheduling posts for future publish times
- **FR4.4:** OAuth tokens MUST be refreshed automatically before expiry

### 7.5 Device Management
- **FR5.1:** Users MUST be able to pair/unpair devices via the `manage_device` agent skill or REST API
- **FR5.2:** Device routing MUST consider battery level, charging state, and device capabilities
- **FR5.3:** Desktop devices (macOS, Windows, Linux) MUST default to Claude Code CLI execution engine
- **FR5.4:** All device command types MUST support checkpoint flow (pause on ambiguity, await user decision)

### 7.6 Memory & Context
- **FR6.1:** Agent memory MUST persist across sessions in `users/{id}/agent_memory/` subcollection
- **FR6.2:** Memory MUST be managed via `manage_settings` skill (read/update spending limits, domain lists)
- **FR6.3:** PDLC agents MUST have access to 4-layer knowledge system per run

### 7.7 User Control & Safety
- **FR7.1:** Tier 0 actions (read-only) execute automatically without confirmation
- **FR7.2:** Tier 1 actions (mutations: post, schedule, edit, delete) MUST request user confirmation before executing
- **FR7.3:** Tier 2 actions (financial: purchases, subscriptions) MUST require 2FA
- **FR7.4:** Spending limit defaults to $0 — user must explicitly enable before any billable execution
- **FR7.5:** Domain allowlist/blocklist MUST be enforced for all agent browsing

### 7.8 Notifications
- **FR8.1:** Push notification (FCM) MUST be sent when a spark completes, fails, or reaches a checkpoint
- **FR8.2:** Checkpoint notifications MUST include a deep link to the HITL approval surface (HTML)
- **FR8.3:** User MUST be able to approve/reject checkpoints from the notification-linked HTML surface without opening the app

---

## 8. Non-Functional Requirements

### 8.1 Availability
- Cloud agent: 99.9% uptime (Cloud Run auto-scaling)
- PDLC pipeline: best-effort — quality over speed, user tolerates latency

### 8.2 Latency
- Voice transcription: ≤500ms (Whisper)
- Spark classification: ≤1s (Haiku)
- Cloud agent response (simple query): ≤3s
- PDLC `SubmitPipeline` acknowledgment: ≤2s
- PDLC full run: no hard cap (quality first)
- Checkpoint resumption: ≤5s from user approval to next role dispatch

### 8.3 Security
- All secrets in Vault (never hardcoded)
- PASETO v4.local tokens for auth (shared keys with Strategiz for SSO)
- Per-pipeline workspace isolation (no shared filesystem)
- Agent containers run as non-root
- SECURITY_ANALYST veto is a hard stop (cannot be overridden without explicit checkpoint)

### 8.4 Cost Control
- Default monthly spend: $0 (blocked until user enables)
- Per-pipeline ceiling: $100 (configurable)
- All LLM token usage tracked per spark, per role, per pipeline

---

## 9. Roadmap

### v1 (Shipped)
- Cloud agent with 20+ skills
- Device agent with 9 command types + checkpoint flow
- PDLC v1 (in-JVM, Firestore-backed)
- Social automation (Twitter, LinkedIn, Instagram)
- Voice input (Whisper)
- Google Photos integration
- AI video generation (SiliconFlow / Wan 2.2)
- PASETO auth + CIDADEL SSO

### v2 (In Progress — PDLC Focus)
- **PDLC v2** (replaces v1): containerized roles on Hetzner, arbiter gRPC, multi-candidate generation, TDD enforcement, multi-critic consensus, vector search over past runs, retro learning loop
- Arbiter gRPC integration: centralized LLM routing replaces direct client deps
- MongoDB for PDLC state (replaces Firestore for pipeline data)
- Qdrant for semantic search over past runs (Voyage-code-3 embeddings)

### v3 (Planned)
- Multi-tenant SaaS isolation
- WebMCP integration (Chrome 146+ structured browsing)
- IDE integration (VSCode extension, JetBrains)
- Teams and org-level quality configuration
- BYOK (Bring Your Own Key) — user-supplied LLM API keys

---

## 10. Success Metrics

| Metric | Target | When |
|--------|--------|------|
| PDLC first-run CI pass rate | ≥85% | v2 GA |
| PR merge rate | ≥70% | v2 GA |
| Spark classification accuracy | ≥95% | Ongoing |
| Checkpoint rejection rate | ≤15% | Ongoing |
| Voice transcription accuracy | ≥98% | Ongoing |
| Social post success rate | ≥99% | Ongoing |
| Device command success rate | ≥95% | Ongoing |
| Monthly active sparks (per user) | ≥50 | 6 months post-launch |
