# Cloud Browser Automation — Design Document

**Date:** 2026-02-27
**Status:** APPROVED
**Approach:** A — Playwright-in-Process

## Problem Statement

Tacticl's cloud execution path is limited to read-only web capabilities (Brave Search + Jina Reader). When no user device is online, the agent cannot interact with websites — fill forms, click buttons, complete purchases, or automate multi-step web workflows. This is a significant capability gap compared to competitors like OpenClaw that provide full browser automation via headless Chromium + Playwright.

## Goals

1. **Expand automation capabilities** — Full interactive browser control in cloud execution (navigate, click, type, fill forms, download/upload files)
2. **Competitive parity and beyond** — Match OpenClaw's headless Chrome capabilities while integrating with Tacticl's existing device-remoting, checkpoint system, and tiered security model
3. **User-configurable execution** — Let users choose cloud-first, device-first, or cloud-only execution preferences
4. **Maintain security posture** — Extend existing Tier 0/1/2 confirmation system to browser actions with domain controls and resource quotas

## Non-Goals

- Replacing the device-remoting model (it stays, user chooses preference)
- Building a general-purpose browser product (this is agent-controlled automation)
- Supporting non-Chromium browsers

---

## Section 1: Execution Routing & User Preferences

### User Setting

New field on `users/` collection: `executionPreference`

| Value | Behavior |
|-------|----------|
| `DEVICE_FIRST` | Try device dispatch, fall back to cloud browser (default) |
| `CLOUD_FIRST` | Try cloud browser, fall back to device dispatch |
| `CLOUD_ONLY` | Never route to device, all cloud execution |

### Routing Logic

```
POST /api/agent/command
  → SparkService.createSpark()
  → Check user.executionPreference:
      CLOUD_ONLY  → VoiceAgentService (with browser skills)
      CLOUD_FIRST → VoiceAgentService (with browser skills)
                    └─ if cloud fails → try device dispatch
      DEVICE_FIRST → DeviceRoutingService.hasOnlineDevice()?
                    ├─ YES → WebSocket dispatch (existing)
                    └─ NO  → VoiceAgentService (with browser skills)
```

The LLM (Claude) decides when to use browser skills vs simpler tools based on task requirements. Search query → `search_web`. Fill out a form → `browser_navigate` + `browser_fill_form`.

---

## Section 2: Browser Session Management

### New Component: `BrowserSessionService`

Manages Playwright browser instances and user profiles. Two session types:

### Ephemeral Sessions

- Fresh browser context per skill execution, torn down after
- No cookies/state persisted
- Used for: quick scrapes, one-off form fills, public site interactions
- Tier 0 tasks default here

### Persistent Sessions

- Per-user Chromium profile directory (`/tmp/browser-profiles/{userId}/`)
- Cookies, localStorage, login sessions survive across commands
- Profile dirs backed up to GCS (`gs://tacticl-browser-profiles/{userId}/`)
- On session start: pull profile from GCS → local dir → launch with that profile
- On session end: sync profile dir back to GCS
- Used for: sites where user is logged in, multi-step workflows across commands
- Tier 1/2 tasks that need auth use these

### Lifecycle

```
Skill requests browser session:
  → BrowserSessionService.getSession(userId, ephemeral=true|false)
    ├─ Ephemeral: new BrowserContext(), return handle
    └─ Persistent:
        ├─ Check in-memory pool (already running for this user?)
        ├─ If not: pull profile from GCS → launch with --user-data-dir
        └─ Return handle
  → Skill uses browser
  → Skill done:
    ├─ Ephemeral: context.close()
    └─ Persistent: keep alive (idle timeout 5 min), sync to GCS on close
```

### Resource Limits

- Max concurrent browser contexts per instance: 3 (configurable)
- Max contexts per user: 1
- Ephemeral timeout: 60s
- Persistent idle timeout: 300s (5 min)
- Page load timeout: 30s
- Max pages per context: 5
- Single shared Chromium process, multiple lightweight contexts (~50MB each)

### Spring Integration

```java
@Component
public class BrowserSessionService {
    private final Map<String, BrowserContext> persistentSessions; // userId → context
    private final Playwright playwright;
    private final Browser browser; // single shared Chromium instance

    public BrowserContext getEphemeral();
    public BrowserContext getPersistent(String userId);
    public void releaseSession(String userId);

    @PreDestroy
    void cleanup();
}
```

---

## Section 3: Browser Skills

New `AgentSkill` implementations using `BrowserSessionService`. Registered in `ToolRegistry` via Spring component scanning.

### Skill Inventory

| Skill | Tier | Session | Description |
|-------|------|---------|-------------|
| `browser_navigate` | 0 | Ephemeral | Navigate to URL, return accessibility snapshot |
| `browser_click` | 1 | Either | Click element by role/text/ref |
| `browser_type` | 1 | Either | Type text into input field |
| `browser_fill_form` | 1 | Either | Fill multiple form fields at once |
| `browser_screenshot` | 0 | Either | Take screenshot, store to GCS as base64 |
| `browser_snapshot` | 0 | Either | Return accessibility tree for Claude to reason over |
| `browser_select` | 1 | Either | Select dropdown option |
| `browser_scroll` | 0 | Either | Scroll page or element |
| `browser_wait` | 0 | Either | Wait for element/text to appear |
| `browser_extract` | 0 | Ephemeral | Extract structured data from page |
| `browser_download` | 1 | Either | Download file, store to GCS user files |
| `browser_upload` | 1 | Either | Upload file from GCS to file input |
| `browser_session_login` | 1 | Persistent | Trigger checkpoint for user to log in via live view |

### Design Decision: Accessibility Snapshots over Screenshots

Claude reasons over **accessibility trees** (structured text: roles, names, values) rather than screenshots. Playwright's `page.accessibility().snapshot()` provides this. Screenshots are for the user to verify what's happening; accessibility trees are for Claude to decide what to do next.

### File Download Flow

```
Agent clicks download link → Playwright intercepts download event
  → Save to temp → Upload to GCS (gs://tacticl-user-files/{userId}/downloads/)
  → Return metadata (name, size, GCS path, content type)
  → Checkpoint: "Downloaded invoice.pdf (2.3MB). Want me to do anything with it?"
```

### File Upload Flow

```
Agent needs to upload → skill receives GCS path or URL
  → Pull file to temp → Playwright file chooser: page.setInputFiles(selector, path)
  → Return success/failure
```

### File Safety

- Max file size: 50MB (configurable)
- Blocked extensions: `.exe`, `.bat`, `.sh`, `.msi` (configurable)
- All downloads require checkpoint approval (Tier 1)
- Files scoped per-user in GCS

---

## Section 4: Checkpoint Integration & Live Browser View

### Checkpoint-Based (Default)

Agent works autonomously, pauses at key moments with screenshot + prompt via existing checkpoint system.

**Auto-checkpoint triggers:**
- Login required (URL patterns: `/login`, `/signin`, OAuth redirects)
- CAPTCHA detected (common CAPTCHA element selectors)
- Irreversible action (Tier 1/2: purchases, submissions, deletions)
- Agent uncertainty (unexpected page state, ambiguous choices)
- File downloaded

```
Agent hits login page:
  → browser_screenshot() → attach to checkpoint
  → SparkService.onSparkCheckpoint(sparkId, {
      type: "LOGIN_REQUIRED",
      screenshot: gcsUrl,
      message: "This site requires login. Want to sign in?",
      options: ["Open live view to log in", "Skip this step", "Cancel"]
    })
  → Spark state → CHECKPOINT
  → User sees screenshot + options in mobile app
  → User picks "Open live view" → launches live session
  → User logs in → closes live view → agent resumes
```

### Live Browser View (On-Demand)

User taps "Watch" or "Take over" in mobile app.

**Tech:** noVNC over WebSocket — renders browser viewport as video stream. Mobile app loads noVNC client in a WebView.

**Flow:**
```
User taps "Watch" on active spark:
  → WebSocket: { type: "live_view_request", sparkId }
  → BrowserSessionService.enableLiveView(userId)
      → Starts VNC server (Xvfb + x11vnc)
      → Returns noVNC WebSocket URL
  → Mobile app opens WebView with noVNC URL
  → User sees live browser
  → "Hand back" → agent resumes control
```

**Control modes:**
- **Watch only** — User observes, agent keeps working
- **Take over** — Agent pauses, user controls browser directly (login, CAPTCHA). Hands back when done.

### New Checkpoint Types

```java
public enum CheckpointType {
    ACTION_CONFIRMATION,     // existing
    LOGIN_REQUIRED,          // new
    CAPTCHA_DETECTED,        // new
    PURCHASE_CONFIRMATION,   // new
    DOWNLOAD_APPROVAL,       // new
    BROWSER_ERROR,           // new
    AMBIGUOUS_STATE          // new
}
```

### Mobile App Changes

- Spark detail: "Watch" button when browser session is active
- noVNC WebView component for live view
- Checkpoint UI: screenshot display + action buttons
- Take-over / hand-back controls

---

## Section 5: Security & Resource Management

### Tier Mapping

```
Tier 0 (Auto)     — browser_navigate, browser_snapshot, browser_scroll,
                     browser_wait, browser_extract, browser_screenshot
                     → Read-only browsing

Tier 1 (Confirm)  — browser_click, browser_type, browser_fill_form,
                     browser_select, browser_download, browser_upload,
                     browser_session_login
                     → Mutations, form submissions, file operations

Tier 2 (2FA)      — Purchase confirmation, financial form submission
                     → Detected via page content/URL patterns
                     → Requires 2FA push to mobile
```

### Domain Controls

User-configurable in Firestore `users/{id}/browserSettings`:

```json
{
  "domainAllowlist": ["opentable.com", "linkedin.com"],
  "domainBlocklist": ["casino.com"],
  "autoBlockCategories": ["gambling", "adult"],
  "allowFileDownloads": true,
  "allowFileUploads": true,
  "maxFileSize": 52428800,
  "maxSpendPerAction": 0
}
```

Every `browser_navigate` checks domain against user's settings before loading.

### Browser Isolation

- Each user gets own `BrowserContext` (isolated cookies, storage, cache)
- Own persistent profile dir (no cross-user leakage)
- Chromium runs sandboxed
- Internal GCP metadata server blocked (169.254.169.254)
- Filesystem: only `/tmp/browser-profiles/{userId}/` writable

### Cost Guardrails

Browser session time tracked and quota-limited per plan:

| Plan | Browser Minutes/Month |
|------|----------------------|
| Free | 10 |
| Creator | 120 (2 hours) |
| Business | 600 (10 hours) |
| Agency | Unlimited |

### Audit Trail

Every browser action logged to `browser_sessions/{id}/actions/`:

```json
{
  "sparkId": "...",
  "skillName": "browser_click",
  "url": "https://opentable.com/...",
  "elementRef": "button:Complete Reservation",
  "timestamp": "...",
  "screenshotUrl": "gs://...",
  "tier": 1,
  "userApproved": true,
  "durationMs": 450
}
```

---

## Section 6: Infrastructure Changes

### Docker

Switch from Alpine to Ubuntu base (Playwright requirement). Install Chromium + VNC tools at build time.

```
Image size: ~250MB (current) → ~650MB (with Chromium + VNC)
```

### Cloud Run

```yaml
prod:
  memory: 8Gi          # up from 4Gi
  cpu: 4               # up from 2
  min-instances: 1     # keep warm (browser startup is slow cold)
  max-instances: 10
  concurrency: 20
  timeout: 300s
```

### GCS Buckets

```
gs://tacticl-browser-profiles/{userId}/    # persistent Chromium profiles (no auto-delete)
gs://tacticl-user-files/{userId}/
  ├─ downloads/                            # auto-delete after 30 days
  └─ uploads/                              # auto-delete after 7 days
```

### Spring Configuration

```yaml
tacticl:
  browser:
    enabled: true
    chromium-path: ${PLAYWRIGHT_BROWSERS_PATH}/chromium
    max-concurrent-contexts: 3
    ephemeral-timeout: 60s
    persistent-idle-timeout: 300s
    profile-bucket: tacticl-browser-profiles
    files-bucket: tacticl-user-files
    vnc:
      enabled: true
      port-range: 5900-5910
```

---

## Section 7: Data Model

### Firestore: User Settings Extension

```
users/{userId}
  └─ executionPreference: "DEVICE_FIRST" | "CLOUD_FIRST" | "CLOUD_ONLY"
  └─ browserSettings: { domainAllowlist, domainBlocklist, autoBlockCategories,
                         allowFileDownloads, allowFileUploads, maxFileSize, maxSpendPerAction }
  └─ browserQuota: { planTier, minutesUsed, minutesLimit, resetDate }
```

### Firestore: Browser Sessions

```
browser_sessions/{sessionId}
  userId, sparkId, type (EPHEMERAL|PERSISTENT), status (ACTIVE|IDLE|CLOSED),
  createdAt, lastActiveAt, closedAt, profilePath, currentUrl, pagesOpen,
  memoryUsageMb, liveViewEnabled, durationSeconds
```

### Firestore: Browser Action Logs (subcollection)

```
browser_sessions/{sessionId}/actions/{actionId}
  sparkId, skillName, timestamp, url, elementRef, inputData, result,
  screenshotUrl, tier, userApproved, durationMs
```

### Firestore: User Files

```
user_files/{fileId}
  userId, sparkId, sessionId, type (DOWNLOAD|UPLOAD), fileName,
  contentType, sizeBytes, gcsPath, sourceUrl, createdAt, expiresAt
```

### Spark Entity Extension

```
sparks/{sparkId}
  └─ executionMode: "DEVICE" | "CLOUD" | "CLOUD_BROWSER"
  └─ browserSessionId: string | null
  └─ browserMinutesUsed: number
```

---

## Section 8: Module Structure

### New Modules

```
data-browser/         → BrowserSession, BrowserActionLog, UserFile entities + repos
business-browser/     → BrowserSessionService, ProfileStorage, LiveView, Security,
                        all browser AgentSkill implementations
```

### Dependency Graph

```
service-agent
  ├─ business-agent
  │   ├─ business-browser (NEW)
  │   │   ├─ data-browser (NEW)
  │   │   │   └─ framework-*
  │   │   ├─ client-base (GCS operations)
  │   │   └─ framework-*
  │   ├─ business-social
  │   ├─ client-* (twitter, linkedin, etc.)
  │   ├─ data-social
  │   └─ framework-*
  ├─ service-spark
  └─ framework-*
```

### Package Structure

```
business-browser/
  └─ src/main/java/io/tacticl/browser/
      ├─ service/
      │   ├─ BrowserSessionService.java
      │   ├─ BrowserProfileStorageService.java
      │   ├─ BrowserLiveViewService.java
      │   └─ BrowserSecurityService.java
      ├─ skill/
      │   ├─ BrowserNavigateSkill.java
      │   ├─ BrowserClickSkill.java
      │   ├─ BrowserTypeSkill.java
      │   ├─ BrowserFillFormSkill.java
      │   ├─ BrowserScreenshotSkill.java
      │   ├─ BrowserSnapshotSkill.java
      │   ├─ BrowserSelectSkill.java
      │   ├─ BrowserScrollSkill.java
      │   ├─ BrowserWaitSkill.java
      │   ├─ BrowserExtractSkill.java
      │   ├─ BrowserDownloadSkill.java
      │   ├─ BrowserUploadSkill.java
      │   └─ BrowserSessionLoginSkill.java
      └─ config/
          └─ BrowserConfig.java

data-browser/
  └─ src/main/java/io/tacticl/browser/data/
      ├─ entity/
      │   ├─ BrowserSession.java
      │   ├─ BrowserActionLog.java
      │   └─ UserFile.java
      └─ repository/
          ├─ BrowserSessionRepository.java
          ├─ BrowserActionLogRepository.java
          └─ UserFileRepository.java
```

### Conditional Loading

Entire browser capability behind feature flag:

```java
@Configuration
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserConfig { ... }
```

If disabled: no Playwright init, no browser skills registered, zero overhead.

---

## Implementation Strategy

Full vision, team-of-agents execution. Design the complete architecture, implement with parallel agent swarm across independent modules and concerns.
