---
name: Tacticl System Architecture Document
description: Level 1 SAD for all of Tacticl — full deployment topology, service interactions, auth, data architecture
type: engineering-spec
status: draft
date: 2026-04-12
author: Gabriel Jimenez
related-docs:
  - 2026-04-12-tacticl-product-prd.md
  - 2026-04-11-tacticl-pdlc-v2-sad.md
---

# Tacticl — System Architecture Document

**Date:** 2026-04-12
**Version:** 1.0
**Status:** Draft
**Author:** Gabriel Jimenez
**Related docs:**
- [Product PRD](2026-04-12-tacticl-product-prd.md)
- [PDLC v2 SAD](2026-04-11-tacticl-pdlc-v2-sad.md)

---

## 1. Overview

This document describes the full system architecture of Tacticl: all repositories, services, infrastructure components, and the interactions between them. It covers the Cloud Run control plane, the Hetzner execution plane, all client applications, and the shared CIDADEL platform infrastructure.

For PDLC pipeline internals (container lifecycle, workspace assembly, arbiter shell), see the [PDLC v2 SAD](2026-04-11-tacticl-pdlc-v2-sad.md).

---

## 2. Repository Map

| Repo | Language | Role | Deploy target |
|------|----------|------|---------------|
| `tacticl-core` | Java 25 / Spring Boot 4 | Backend API, cloud agent, PDLC orchestration | Cloud Run (GCP `tacticl`) |
| `tacticl-web` | React / TypeScript | Web dashboard (Spark Control, Chat) | CDN / Firebase Hosting |
| `tacticl-mobile` | React Native / Expo | Mobile app (Chat, Push-to-talk, Device agent) | App Store / Google Play |
| `tacticl-device` | Electron | Desktop agent daemon | macOS / Windows / Linux local |
| `cidadel-core` | Java 25 / Gradle | Shared infrastructure library | GitHub Packages (Maven) |
| `cidadel-ai-arbiter` | Node.js | gRPC LLM routing + PDLC container orchestrator | Hetzner (CPX31+) |
| `tacticl-docs` | Markdown / HTML | Architecture docs, PDLC templates, design system | GitHub Pages / local viewer |

---

## 3. System Context Diagram

Full system boundary showing Tacticl and all external dependencies.

```mermaid
C4Context
  title Tacticl — System Context

  Person(user, "Tacticl User", "Solo dev, PM, or eng lead")

  System_Boundary(tacticl, "Tacticl Platform") {
    System(core, "tacticl-core", "Java backend: REST API, cloud agent, PDLC orchestration")
    System(arbiter, "cidadel-ai-arbiter", "Node.js: LLM routing, PDLC container orchestration")
    System(web, "tacticl-web", "React web dashboard")
    System(mobile, "tacticl-mobile", "React Native mobile app")
    System(device, "tacticl-device", "Electron desktop agent")
  }

  System_Ext(anthropic, "Anthropic API", "Claude models")
  System_Ext(openai, "OpenAI API", "GPT models")
  System_Ext(grok, "Grok API", "Grok models")
  System_Ext(github, "GitHub API", "Repos, PRs, webhooks")
  System_Ext(firebase, "Firebase / GCP", "Firestore, FCM, Cloud Run, GCS")
  System_Ext(hetzner, "Hetzner Cloud", "VMs for container execution")
  System_Ext(vault, "HashiCorp Vault", "Secrets management")
  System_Ext(brave, "Brave Search", "Web search index")
  System_Ext(jina, "Jina Reader", "Web page extraction")
  System_Ext(siliconflow, "SiliconFlow", "Wan 2.2 video generation")
  System_Ext(twitter, "Twitter/X API", "Social publishing")
  System_Ext(linkedin, "LinkedIn API", "Social publishing")
  System_Ext(instagram, "Instagram Graph API", "Social publishing")
  System_Ext(gphotos, "Google Photos API", "Media source")
  System_Ext(whisper, "Whisper API", "Voice transcription")

  Rel(user, mobile, "Chat, voice, approvals")
  Rel(user, web, "Dashboard, pipeline review")
  Rel(user, device, "Device agent pairing")
  Rel(mobile, core, "REST + WebSocket")
  Rel(web, core, "REST + WebSocket")
  Rel(device, core, "WebSocket")
  Rel(core, arbiter, "gRPC")
  Rel(arbiter, anthropic, "LLM calls")
  Rel(arbiter, openai, "LLM calls")
  Rel(arbiter, hetzner, "Docker container lifecycle")
  Rel(core, firebase, "Firestore reads/writes, FCM push")
  Rel(core, vault, "Secret loading at startup")
  Rel(core, github, "Webhook receive, PR creation")
  Rel(core, brave, "Web search")
  Rel(core, jina, "Page extraction")
  Rel(core, siliconflow, "Video generation")
  Rel(core, twitter, "Social publish")
  Rel(core, linkedin, "Social publish")
  Rel(core, instagram, "Social publish")
  Rel(core, gphotos, "Media fetch")
  Rel(core, whisper, "Voice transcription")
```

---

## 4. Deployment Topology

### 4.1 GCP / Cloud Run (Control Plane)

All user-facing API traffic runs here.

| Service | Image | Memory | Instances | Region |
|---------|-------|--------|-----------|--------|
| `tacticl-core` (prod) | `tacticl-core:prod` | 4Gi | 1–10 (auto) | us-east1 |
| `tacticl-core` (qa) | `tacticl-core:qa` | 2Gi | 1–3 (auto) | us-east1 |
| `strategiz-vault` | `vault:latest` | 512Mi | 1 (always-on) | us-east1 |

**Firestore** (project `tacticl`, us-east1): all operational data — sparks, tactics, social posts, device commands, social integrations, checkpoints, user settings, agent memory.

**GCS** (project `tacticl`): pipeline workspace archives, generated videos, uploaded media.

**Firebase Cloud Messaging (FCM)**: push notifications to mobile (iOS + Android).

### 4.2 Hetzner (Execution Plane)

PDLC pipeline container execution runs here.

| Host | Spec | Role |
|------|------|------|
| `hetzner-arbiter-01` | CPX31 (4 vCPU, 8GB RAM) | arbiter shell + Docker daemon + MongoDB + Qdrant |
| `hetzner-arbiter-02` (future) | CPX51 (20 vCPU, 32GB RAM) | overflow container execution |

**MongoDB** (on hetzner-arbiter-01, `port 27017`): PDLC pipeline state — `pipeline_runs`, `pipeline_events`, `pipeline_artifacts`, `agent_knowledge`, `checkpoints`.

**Qdrant** (on hetzner-arbiter-01, `port 6333`): vector search over past pipeline runs — collection `past_pipeline_runs`, Voyage-code-3 embeddings.

**Workspace storage** (`/opt/cidadel/agent-workspaces/`): live workspace bind mounts (per pipeline run) + archive directory (30-day retention).

### 4.3 Deployment Topology Diagram

> **Note for HTML rendering:** The below is described as a Mermaid diagram for the `.md` source. The HTML HITL surface for this document uses a draw.io SVG that renders the full topology with color-coded zones (GCP = blue, Hetzner = orange, External = grey).

```mermaid
graph TB
  subgraph GCP["GCP / Cloud Run (us-east1)"]
    CORE["tacticl-core\n(Spring Boot 4, Java 25)\n4Gi, auto-scale"]
    VAULT["Vault\n(512Mi, always-on)"]
    FS[("Firestore\n(operational data)")]
    GCS[("GCS\n(artifacts, videos)")]
    FCM["FCM\n(push notifications)"]
  end

  subgraph HETZNER["Hetzner Cloud (hetzner-arbiter-01)"]
    ARBITER["cidadel-ai-arbiter\n(Node.js gRPC server)"]
    DOCKER["Docker Daemon"]
    MONGO[("MongoDB\n(PDLC state)")]
    QDRANT[("Qdrant\n(vector search)")]
    WORKSPACES[("Workspace Storage\n/opt/cidadel/agent-workspaces/")]
    subgraph CONTAINERS["Agent Containers (ephemeral)"]
      C1["cidadel-agent\n(PM role)"]
      C2["cidadel-agent\n(ARCHITECT role)"]
      C3["cidadel-agent\n(IMPLEMENTER x3)"]
      CN["..."]
    end
  end

  subgraph CLIENTS["Client Apps"]
    MOBILE["tacticl-mobile\n(React Native / Expo)"]
    WEB["tacticl-web\n(React)"]
    DESKTOP["tacticl-device\n(Electron daemon)"]
  end

  subgraph EXTERNAL["External Services"]
    ANTHROPIC["Anthropic API"]
    GITHUB["GitHub API"]
    SOCIAL["Twitter / LinkedIn\n/ Instagram"]
    GPHOTOS["Google Photos"]
    BRAVE["Brave Search"]
    JINA["Jina Reader"]
    SILICON["SiliconFlow"]
    WHISPER["Whisper API"]
  end

  MOBILE -->|"REST + WebSocket"| CORE
  WEB -->|"REST + WebSocket"| CORE
  DESKTOP -->|"WebSocket"| CORE
  CORE -->|"gRPC (port 50051)"| ARBITER
  CORE <-->|"read/write"| FS
  CORE -->|"upload/download"| GCS
  CORE -->|"push"| FCM
  CORE -->|"secrets"| VAULT
  CORE -->|"social"| SOCIAL
  CORE -->|"media"| GPHOTOS
  CORE -->|"search"| BRAVE
  CORE -->|"extract"| JINA
  CORE -->|"video"| SILICON
  CORE -->|"STT"| WHISPER
  CORE -->|"webhooks, PRs"| GITHUB
  ARBITER -->|"spawn/kill"| DOCKER
  DOCKER -->|"create"| CONTAINERS
  ARBITER <-->|"pipeline state"| MONGO
  ARBITER <-->|"vector search"| QDRANT
  CONTAINERS -->|"workspace bind mount"| WORKSPACES
  CONTAINERS -->|"LLM calls via arbiter"| ARBITER
  ARBITER -->|"LLM routing"| ANTHROPIC
  CONTAINERS -->|"git clone / PR"| GITHUB
```

---

## 5. Service Interactions

### 5.1 tacticl-core ↔ cidadel-ai-arbiter (gRPC)

All PDLC pipeline execution is coordinated via gRPC. tacticl-core is the control plane; arbiter is the execution plane.

**Protocol:** gRPC over mTLS (internal Hetzner network). Port 50051.

**Key RPCs:**

| RPC | Direction | Purpose |
|-----|-----------|---------|
| `SubmitPipeline` | core → arbiter | Start a new PDLC pipeline run |
| `ResolveCheckpoint` | core → arbiter | Relay user's approve/reject/feedback |
| `GetPipelineStatus` | core → arbiter | Poll current pipeline state (for recovery) |
| `StreamPipelineEvents` | arbiter → core | Push events as pipeline progresses |

tacticl-core also receives callbacks from arbiter via HTTP POST to `/v1/internal/pipeline/callback` (for non-streaming event delivery).

### 5.2 tacticl-core ↔ Clients (REST + WebSocket)

**REST:** `https://api.tacticl.ai/v1/` — all endpoints use `/v1/` prefix.

**WebSocket:** `/ws/sparks/{userId}` — real-time spark progress events, tactic updates, checkpoint notifications, device status changes.

**Auth:** PASETO v4.local token in `Authorization: Bearer` header (REST) or initial handshake (WebSocket).

### 5.3 tacticl-core ↔ Devices (WebSocket)

Devices maintain a persistent WebSocket connection to tacticl-core. Commands are dispatched as JSON messages. Device sends back progress events, checkpoint requests, and completion signals.

```mermaid
sequenceDiagram
  participant C as tacticl-core
  participant D as Device Agent

  D->>C: DEVICE_CONNECT {deviceId, capabilities, battery}
  C->>D: ACK

  Note over C,D: User sends spark

  C->>D: SPARK_DISPATCH {sparkId, tactics[]}
  D->>C: TACTIC_STARTED {tacticId}
  D->>C: TACTIC_PROGRESS {tacticId, progress}

  Note over C,D: Ambiguous step

  D->>C: CHECKPOINT_REQUESTED {checkpointId, question}
  C->>D: CHECKPOINT_RESOLVED {checkpointId, decision}
  D->>C: TACTIC_COMPLETED {tacticId, result}
  D->>C: SPARK_COMPLETED {sparkId}
```

---

## 6. Auth Architecture

### 6.1 PASETO Tokens

Tacticl uses PASETO v4.local (symmetric encryption) for all auth. Tokens are issued by `cidadel-core`'s `framework-token-issuance` library.

**Token lifetime:** 15 minutes (access) + 30 days (refresh)

**Claims:** `userId`, `scopes[]`, `product` (`tacticl`), `deviceId` (optional), `issuedAt`, `expiresAt`

**Cross-product SSO:** Shared symmetric key between Tacticl and Strategiz — a Strategiz token is valid in Tacticl (same cidadel infrastructure).

### 6.2 Auth Flow

```mermaid
sequenceDiagram
  participant App as Mobile App
  participant Core as tacticl-core
  participant Cidadel as cidadel-core

  App->>Core: POST /v1/auth/login {email, password}
  Core->>Cidadel: validateCredentials(email, password)
  Cidadel-->>Core: userId, scopes
  Core->>Cidadel: issueToken(userId, scopes, product=tacticl)
  Cidadel-->>Core: PASETO access + refresh tokens
  Core-->>App: {accessToken, refreshToken}

  Note over App,Core: Subsequent requests

  App->>Core: GET /v1/sparks (Authorization: Bearer {token})
  Core->>Cidadel: validateToken(token)
  Cidadel-->>Core: claims {userId, scopes}
  Core-->>App: 200 OK {sparks[]}
```

### 6.3 Scope System

| Scope | Controls |
|-------|---------|
| `sparks:read` | Read spark history |
| `sparks:write` | Create sparks (chat commands) |
| `social:read` | Read social posts and connections |
| `social:write` | Create / schedule posts |
| `devices:manage` | Pair / unpair devices |
| `pipeline:read` | Read pipeline status |
| `pipeline:write` | Submit pipelines, resolve checkpoints |
| `console:admin` | Admin endpoints (role overrides, migrations) |

---

## 7. Data Architecture

### 7.1 Firestore (Operational Data)

Project: `tacticl`, region: `us-east1`

**Hybrid schema (Approach B):** User-owned data nested under `tacticl_users/{userId}/`, operational data stays flat.

**Nested under user** (subcollections):
- `tacticl_users/{userId}/devices/` — registered devices + settings
- `tacticl_users/{userId}/social_integrations/` — OAuth tokens per platform
- `tacticl_users/{userId}/repo_grants/` — connected GitHub repos
- `tacticl_users/{userId}/agent_tokens/` — API tokens for agent access
- `tacticl_users/{userId}/agent_memory/` — persistent cross-session memory

**Flat collections** (operational):
- `sparks/` — all user sparks (lifetime entity)
- `tactics/` — device-side decomposition of sparks
- `execution_logs/` — LLM calls, tool invocations, token usage
- `checkpoints/` — user decision gates (v1 pipeline)
- `social_posts/` — post state machine
- `device_commands/` — dispatched commands with sparkId ref
- `action_confirmations/` — pending Tier 1/2 action approvals
- `agent_reminders/` — scheduled reminders
- `agent_audit_log/` — all agent commands (immutable)

### 7.2 MongoDB (PDLC Pipeline State)

Host: `hetzner-arbiter-01:27017`, database: `tacticl_pdlc`

| Collection | Purpose |
|-----------|---------|
| `pipeline_runs` | Full pipeline lifecycle — one doc per run, all state transitions |
| `pipeline_events` | Append-only event log — role start/complete/rework/checkpoint events |
| `pipeline_artifacts` | Artifact metadata + content refs (GitHub path) |
| `agent_knowledge` | Learned patterns with status lifecycle (proposed → approved → active) |
| `checkpoints` | v2 checkpoint records (replaces Firestore `checkpoints/` for PDLC) |

### 7.3 Qdrant (Vector Search)

Host: `hetzner-arbiter-01:6333`

**Collection:** `past_pipeline_runs`
**Embedding model:** Voyage-code-3 (1536 dimensions)
**Indexed content:** role prompt + role output + outcome metadata (one vector per role per run)
**Query:** agents call `find_similar_runs(query, top_k=5)` via Qdrant MCP server inside containers
**Population:** RETRO_ANALYST indexes successful runs weekly

### 7.4 Data Flow by Feature

```mermaid
flowchart LR
  subgraph Chat["Chat / Spark Flow"]
    SPARK_INPUT["Spark Input"] -->|"creates"| FIRESTORE_SPARKS["Firestore: sparks/"]
    FIRESTORE_SPARKS -->|"if PDLC"| MONGO_RUNS["MongoDB: pipeline_runs"]
  end

  subgraph Social["Social Flow"]
    COMPOSE["Compose"] -->|"draft"| FIRESTORE_POSTS["Firestore: social_posts/"]
    FIRESTORE_POSTS -->|"publish job"| TWITTER["Twitter API"]
  end

  subgraph PDLC["PDLC Learning Loop"]
    MONGO_RUNS -->|"weekly retro"| QDRANT["Qdrant: past_pipeline_runs"]
    QDRANT -->|"future runs: context"| AGENT_WS["Agent Workspace"]
  end
```

---

## 8. Key Flows

### 8.1 Full Spark Lifecycle

```mermaid
sequenceDiagram
  participant U as User
  participant M as Mobile App
  participant C as tacticl-core
  participant D as Device Agent
  participant A as Arbiter (PDLC)

  U->>M: Voice input
  M->>C: POST /v1/agent/command {text, sessionId}
  C->>C: SparkClassifierService (Haiku)
  C->>C: SparkService.createSpark()

  alt Device online + non-PDLC spark
    C->>D: SPARK_DISPATCH {sparkId, tactics[]}
    D->>C: Progress events
    D->>C: SPARK_COMPLETED
  else PDLC spark
    C->>C: PdlcClassifierService (tier + playbook)
    C->>A: SubmitPipeline gRPC
    A->>C: StreamPipelineEvents
    A->>C: CHECKPOINT_REQUESTED
    C->>M: Push notification
    U->>M: Approve checkpoint
    M->>C: POST /v1/sparks/{id}/pipeline/checkpoint/{cid}
    C->>A: ResolveCheckpoint gRPC
    A->>C: PIPELINE_COMPLETED
    C->>M: Push notification "PR ready"
  else No device, non-PDLC
    C->>C: CloudOrchestratorService
    C->>M: Response via WebSocket
  end
```

### 8.2 PDLC Checkpoint Flow

```mermaid
sequenceDiagram
  participant SHELL as Arbiter Shell
  participant CORE as tacticl-core
  participant MOBILE as Mobile App
  participant USER as User

  SHELL->>CORE: HTTP POST /v1/internal/pipeline/callback {event: CHECKPOINT_REQUESTED, checkpointId, phaseSummary}
  CORE->>CORE: Create Checkpoint record (MongoDB)
  CORE->>MOBILE: FCM push "Review required: {phase} complete"
  MOBILE->>USER: Notification with deep link to HITL HTML
  USER->>MOBILE: Opens HITL HTML (Tacticl purple theme)
  USER->>CORE: POST /v1/sparks/{id}/pipeline/checkpoint/{cid} {decision: APPROVED | REWORK, feedback?: "..."}
  CORE->>SHELL: gRPC ResolveCheckpoint {decision, feedback}
  SHELL->>SHELL: Resume pipeline (next role) or re-dispatch with feedback
```

---

## 9. Infrastructure

### 9.1 Hetzner Node Setup

```
hetzner-arbiter-01 (CPX31: 4 vCPU, 8GB RAM, 160GB NVMe)
├── cidadel-ai-arbiter (Node.js, port 50051 gRPC + 3000 HTTP)
├── Docker daemon (container execution)
├── MongoDB 7.x (port 27017, auth enabled)
├── Qdrant 1.x (port 6333)
└── /opt/cidadel/agent-workspaces/
    ├── live/         <- active pipeline workspaces (bind-mounted into containers)
    └── archive/      <- completed pipeline workspaces (30-day retention)
```

### 9.2 Cloud Run Services

Both services deploy to `us-east1` with public access.

**tacticl-core (prod):** `gcr.io/tacticl/tacticl-core:prod`, 4Gi RAM, min 1 / max 10 instances
**tacticl-core (qa):** `gcr.io/tacticl/tacticl-core:qa`, 2Gi RAM, min 1 / max 3 instances

**Build:** `gcloud builds submit --config deployment/cloudbuild/cloudbuild-prod.yaml .`

### 9.3 Vault

Deployed on Cloud Run (`strategiz-vault-*`). Both Tacticl and Strategiz share one Vault cluster. Contexts:
- `tacticl` — tacticl-specific secrets (brave-search, jina, google, siliconflow, github-webhook)
- `strategiz` — shared LLM API keys (anthropic, openai, grok)

---

## 10. External Dependencies

| Service | Purpose | Auth | Cost model |
|---------|---------|------|-----------|
| Anthropic API | LLM (Claude Haiku/Sonnet/Opus) | API key (Vault: `strategiz/anthropic`) | Per token |
| OpenAI API | LLM (GPT-4o) | API key (Vault: `strategiz/openai`) | Per token |
| Grok API | LLM (Grok models) | API key (Vault: `strategiz/grok`) | Per token |
| Whisper API | Voice transcription | OpenAI key | Per minute |
| GitHub API | Webhooks, repo access, PR creation | OAuth per user | Free tier sufficient |
| Firebase / GCP | Firestore, FCM, Cloud Run, GCS | Service account | Pay-per-use |
| Hetzner Cloud | VM execution (PDLC containers) | API key | ~€15/mo per CPX31 |
| Vault (Cloud Run) | Secrets management | VAULT_TOKEN | Included with Hetzner plan |
| Brave Search | Web search | API key (Vault: `tacticl/brave-search`) | $3/1K queries, 2K free/mo |
| Jina Reader | Web extraction | API key (Vault: `tacticl/jina`) | 10M free tokens/mo |
| SiliconFlow | Wan 2.2 video generation | API key (Vault: `tacticl/siliconflow`) | ~$0.21/video |
| Twitter/X API | Social publish | OAuth per user | Paid tier required |
| LinkedIn API | Social publish | OAuth per user | Free (rate limited) |
| Instagram Graph API | Social publish | OAuth per user | Free (rate limited) |
| Google Photos API | Media source | OAuth per user | Free (read-only) |
