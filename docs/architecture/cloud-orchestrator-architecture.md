# Cloud Orchestrator Architecture

**System**: tacticl-core (Java · Spring Boot · Cloud Run)

## Overview

The cloud orchestrator is a full-power SDLC agent pipeline. It handles everything: code tasks, social media, web research, content generation, video creation, browser automation, device coordination, and more. It is NOT a fallback or lightweight path — it is a complete agent.

## Request Flow

```
Mobile/Web Client
  │
  │ POST /api/agent/command { text, sessionId, timezone, model }
  ▼
AgentController
  │
  ├── UserProvisioningService.ensureUserExists()
  ├── SparkService.createSpark()              ← Every command = one spark
  ├── SparkClassifierService.classify()       ← Auto-classify type via Haiku
  ├── Get ExecutionPreference from UserConfig
  │
  ├── Route Decision:
  │   ├── CLOUD_ONLY  ──────────────► executeInCloud()
  │   ├── CLOUD_FIRST ──────────────► executeInCloud()
  │   └── DEVICE_FIRST ─┬── device online? ──► delegateToDevice()
  │                      └── no device     ──► executeInCloud()
  │
  ▼ (Cloud Path)
VoiceAgentService.execute(sparkId, text, userId, ...)
  │
  ├── SparkContext.set(sparkId)               ← ThreadLocal per request
  ├── SparkService.markRunning(sparkId)
  ├── AgentSystemPrompt.buildSystemPrompt()   ← Personality + user context + devices + config
  │
  ├── Agent Loop (max 5 rounds):
  │   │
  │   ├── LlmRouter.generateWithTools(messages, model, tools, systemPrompt)
  │   │     │
  │   │     └── Routes to: AnthropicDirectClient / OpenAIDirectClient / GrokDirectClient
  │   │
  │   ├── If stop_reason == "tool_use":
  │   │     ├── For each ToolUseBlock:
  │   │     │     └── ToolRegistry.getSkill(name).execute(input, userId)
  │   │     ├── Collect ToolResultMessage list
  │   │     ├── Add to conversation messages
  │   │     └── Continue loop
  │   │
  │   └── If stop_reason == "end_turn":
  │         └── Extract finalResponse, break loop
  │
  ├── AgentAuditLogRepository.save()          ← Audit trail
  ├── SparkService.markCloudCompleted()       ← Cost estimation
  └── Return AgentResult → AgentCommandResponse → Client
```

## LLM Routing (cidadel-core)

```
LlmRouter (framework-llm-router)
  │
  ├── Routes by model ID → provider
  ├── 26+ models across providers
  ├── Model enabler (feature flags)
  ├── Usage recorder (billing)
  │
  ├── Anthropic (primary)
  │   ├── claude-opus-4-5, claude-sonnet-4-5, claude-haiku-4-5
  │   ├── claude-3-5-sonnet, claude-3-5-haiku
  │   ├── claude-3-opus, claude-3-sonnet, claude-3-haiku
  │   └── Dual auth: API key (primary) + OAuth (fallback)
  │
  ├── OpenAI
  │   └── gpt-4o, gpt-4o-mini, gpt-4-turbo, o1, o1-mini
  │
  ├── Grok (xAI)
  │   └── grok-4.1-fast, grok-4, grok-3, grok-3-mini
  │
  ├── Gemini (Google) — catalog listed, provider registration varies
  ├── Llama (Meta via SambaNova)
  ├── Mistral
  └── Cohere
```

**Two-Model Strategy**:
- `claude-haiku-4-5` — Routing, classification, simple queries (fast, cheap)
- `claude-sonnet-4-5` — Content generation, complex reasoning
- User can override per-request via `modelOverride` parameter

## Agent Skills (20+)

### Tier 0 — Auto-Execute (Read-Only)

| Skill | Description |
|-------|-------------|
| `search_web` | Brave Search API (5-10 results) |
| `browse_web` | Jina Reader (page → markdown, 4000 char) |
| `google_photos` | Search/list/get user photos |
| `set_reminder` | Create reminders (ISO-8601) |
| `list_devices` | List registered devices |
| `list_scheduled` | Show scheduled posts/tasks |
| `take_screenshot` | Capture device screen |
| `connection_status` | Overview of devices, integrations, repos |
| `generate_content` | Generate social media drafts |
| `check_video_status` | Poll video generation status |

### Tier 1 — Confirmation Required (Mutations)

| Skill | Description |
|-------|-------------|
| `post_to_social` | Post to Twitter/LinkedIn/Instagram |
| `schedule_post` | Schedule future publication |
| `generate_video` | AI video via SiliconFlow Wan 2.2 |
| `manage_settings` | Get/update user config |
| `manage_device` | Pair/unpair/update device settings |
| `manage_repo` | Grant/revoke/list repos |
| `open_url_on_device` | Open URL on connected device |
| `launch_app` | Launch app on device |
| `run_shortcut` | Execute device shortcut |

### Tier 2 — 2FA (Financial)
Reserved for future financial actions.

## Browser Automation (Playwright)

- Headless Chromium via business-browser module
- Per-user BrowserContext (viewport 1280x720)
- Max 3 concurrent contexts
- Profiles persisted in GCS
- Domain allowlist/blocklist enforcement
- 12 browser skills: navigate, click, type, select, screenshot, scroll, extract, download, upload, fill_form, session_login, snapshot

## System Prompt Construction

`AgentSystemPrompt` dynamically builds context:
- Base personality (action-oriented, concise)
- User ID, current time, timezone
- Connected platforms (Twitter, LinkedIn, Instagram, etc.)
- Device context (online status, battery, capabilities)
- Cloud browser availability
- User config (spending limits, domain lists, overrides)
- Action confirmation tier rules

## Spark Lifecycle

```
PENDING → ROUTING → QUEUED (offline device) | EXECUTING
PENDING → EXECUTING (cloud path)
PENDING → SCHEDULED (cron)
EXECUTING → CHECKPOINT → EXECUTING (after approval)
EXECUTING → COMPLETED | FAILED
Any → CANCELLED
```

**Cost tracking**: SparkService estimates cost per model (Opus $75/M, Sonnet $15/M, Haiku $1.25/M tokens).

## External Integrations

| Service | Client Module | Auth | Purpose |
|---------|--------------|------|---------|
| Anthropic API | client-anthropic-direct | API key + OAuth | Primary LLM |
| OpenAI API | client-openai-direct | API key | GPT models |
| xAI Grok API | client-grok-direct | API key | Grok models |
| Twitter API v2 | client-twitter | Per-user OAuth | Social posting |
| LinkedIn Marketing API | client-linkedin | Per-user OAuth | Social posting |
| Instagram Graph API | client-instagram | Per-user OAuth | Social posting |
| GitHub API v3 | client-github | Per-user OAuth | Repo access |
| Google APIs | client-google | Per-user OAuth | Photos, YouTube, Gmail |
| Brave Search | client-brave-search | API key | Web search |
| Jina Reader | client-jina | API key (optional) | Web → markdown |
| SiliconFlow | client-siliconflow | API key | Video generation |
| Google Cloud Storage | client-gcs | GCP default auth | File/profile storage |
| Playwright | business-browser | N/A (local) | Browser automation |

## Real-Time Communication

- **WebSocket `/ws/device`** — Device connections (spark dispatch, commands, progress)
- **WebSocket `/ws/user`** — User browser/mobile (progress updates, checkpoints)
- **PASETO authentication** on both endpoints
- Broadcasting: UserBroadcaster (to user sessions), ActivityBroadcaster (to devices)
