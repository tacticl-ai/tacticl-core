# Firestore Schema Redesign + Agent Configuration Feature

**Date**: 2026-02-28
**Status**: Approved

## Problem

1. Tacticl's agent doesn't recognize its own capabilities (responds "I can't access your local environment" for tasks devices can handle) — **fixed in AgentSystemPrompt.java**
2. No way for users to configure agent behavior (daemon limits, domain lists, confirmation tiers) via chat or settings page
3. No agent skills for registration flows (device pairing, repo management, social account connection)
4. Database schema needs restructuring: user-owned data should live under the user document

## Architecture Decision: Hybrid Schema (Approach B)

**Principle**: User-owned configuration data nests under the user. Operational execution data stays flat.

This reflects a real domain boundary:
- **What you own** (devices, integrations, repos, tokens, config) → subcollections under `tacticl_users/{userId}/`
- **What you do** (sparks, tactics, logs, posts, commands, confirmations) → flat top-level collections

Cross-user query patterns (scheduled job polling, device command lookup, admin dashboards) make flat collections architecturally necessary for operational data. Collection group queries are fragile (require manual index creation, can't filter by parent doc fields, namespace collision risk) and offer zero performance benefit over flat queries.

Firestore security rules are irrelevant — the Java backend uses Admin SDK which bypasses all rules. Security boundary is `@RequireAuth` at the controller layer.

---

## Firestore Schema

### Nested Under User (user-owned data)

```
tacticl_users/{userId}                              <- user profile document
|   onboardingComplete, createdAt
|   config: {                                        <- EMBEDDED (always co-read, 1:1, <1KB)
|     maxConcurrentSparks: 3
|     spendingLimit: 0.00
|     domainAllowlist: []
|     domainBlocklist: []
|     confirmationOverrides: {}
|   }
|   preferences: {                                   <- EMBEDDED
|     timezone: "America/New_York"
|     notificationsEnabled: true
|   }
|
+-- devices/{deviceId}                               <- subcollection
|       deviceName, deviceType, state, capabilities
|       batteryLevel, isCharging, pushToken, specs
|       clonedRepos, activeDaemons, daemonVersion, lastSeenAt
|       settings: {                                  <- EMBEDDED (per-device config)
|         maxDaemons: 2
|         autoWake: false
|         priority: 1
|       }
|       sparkPreferences: {                          <- EMBEDDED (replaces device_preferences)
|         code: { fallbackPolicy: "QUEUE" }
|         social: { fallbackPolicy: "CLOUD" }
|       }
|
+-- social_integrations/{integrationId}              <- subcollection
|       platform, platformUserId, platformUsername
|       profileImageUrl, accessToken, refreshToken
|       tokenExpiresAt, tokenRefreshNeeded, platformMetadata
|
+-- repo_grants/{grantId}                            <- subcollection
|       provider, repoFullName, accessLevel
|       oauthTokenRef, grantedAt
|
+-- agent_tokens/{tokenId}                           <- subcollection
|       provider, label, tokenRef
|       usageLimits, currentUsage
|
+-- reminders/{reminderId}                           <- subcollection
|       message, remindAt, delivered
|
+-- spark_templates/{templateId}                     <- subcollection
        name, description, defaultRepos, tags
```

### Flat Top-Level (operational/execution data)

```
sparks/{sparkId}                                     <- cross-user job polling
    userId, title, description, type, status
    deviceId, deviceName (denormalized), schedule
    parentSparkId, totalTokens, estimatedCost, model
    userConfigSnapshot: { spendingLimit, maxConcurrentSparks }
    result, createdAt, completedAt

tactics/{tacticId}                                   <- cross-user device queries
    sparkId, deviceId, description, status
    sparkTitle (denormalized), sparkType (denormalized)
    repos, tokenUsage, createdAt, completedAt

execution_logs/{logId}                               <- write-heavy, spark-keyed
    sparkId, tacticId, userId (ADD THIS)
    toolName, toolInput, toolOutput, durationMs, timestamp

checkpoints/{checkpointId}                           <- spark-keyed
    sparkId, tacticId, title, description
    findings, options, userDecision, userFeedback, decidedAt

social_posts/{postId}                                <- cross-user publisher job
    userId, sparkId, content, mediaUrls
    state, targetIntegrationIds
    targets: [{ integrationId, platform, username }]  <- denormalized
    publishDate, publishedAt, failureReason

device_sessions/{sessionId}                          <- cross-user device lookup
    deviceId, userId, connectedAt, lastPingAt

device_commands/{commandId}                          <- cross-user device polling
    userId, deviceId, sparkId
    commandType, state, payload
    createdAt, sentAt, completedAt, expiresAt

action_confirmations/{confirmId}                     <- cross-user expiration job
    userId, sparkId, toolName
    actionDescription, state, tier
    createdAt, expiresAt, resolvedAt

agent_audit_log/{logId}                              <- cross-user admin queries
    userId, sparkId, sessionId, commandText
    toolsInvoked, responseText, success
    executionTimeMs, createdAt

pairing_codes/{codeId}                               <- ephemeral, pre-auth lookup
    userId, code, expiresAt, consumed

pairing_sessions/{sessionId}                         <- ephemeral, pre-auth lookup
    secret, userId, deviceId, status, expiresAt
```

### Collections Eliminated

- `device_preferences` -> merged into device document as `sparkPreferences` embedded map
- `device_settings` (was proposed) -> embedded in device document as `settings` map
- `user_config` (was proposed as subcollection) -> embedded in user document as `config` map

### Denormalization

| Source | Denormalized To | Field |
|--------|-----------------|-------|
| DeviceRegistration.deviceName | Spark | deviceName (display) |
| DeviceRegistration.deviceType | Spark | deviceType (display) |
| Spark.title | Tactic | sparkTitle (device context) |
| Spark.type | Tactic | sparkType (device context) |
| SocialIntegration.platform + username | SocialPost.targets | platform, username (display) |
| UserConfig (snapshot) | Spark.userConfigSnapshot | spendingLimit, maxConcurrentSparks |

### New Field

Add `userId` to `ExecutionLog` entity — currently missing, needed for GDPR user data deletion.

---

## New Entities

### UserConfig (embedded in TacticlUser document)

```java
public class UserConfig {
    private int maxConcurrentSparks = 3;
    private BigDecimal spendingLimit = BigDecimal.ZERO;
    private List<String> domainAllowlist = List.of();
    private List<String> domainBlocklist = List.of();
    private Map<String, Integer> confirmationOverrides = Map.of();
    // e.g., {"post_to_social": 0} lowers post confirmation from Tier 1 to Tier 0
}
```

### DeviceSettings (embedded in DeviceRegistration document)

```java
public class DeviceSettings {
    private int maxDaemons = 1;       // max parallel tactics on this device
    private boolean autoWake = false;  // wake device for incoming sparks
    private int priority = 0;          // higher = prefer for routing
}
```

---

## Agent Skills to Add

### Registration/Management Skills

| Skill Name | Tier | Purpose |
|------------|------|---------|
| `manage_device` | 1 | Pair, unpair, update device settings (maxDaemons, autoWake, priority, sparkPreferences) |
| `manage_repo` | 1 | Add/remove repos from Tacticl's purview |
| `manage_settings` | 1 | Read/update UserConfig (spending limits, domain lists, confirmation overrides, concurrent sparks) |
| `connection_status` | 0 | Overview of all connected resources (devices + repos + social accounts + their status) |

### Existing Skills (unchanged)

list_devices (Tier 0), search_web (Tier 0), browse_web (Tier 0), content_gen (Tier 0),
list_scheduled (Tier 0), check_video_status (Tier 0), social_post (Tier 1),
schedule_post (Tier 1), set_reminder (Tier 1), video_gen (Tier 1),
open_url_on_device (Tier 1), launch_app (Tier 1), run_shortcut (Tier 1),
take_screenshot (Tier 1)

### Example Chat Interactions

```
User: "Pair my MacBook as a new device"
Agent: [uses manage_device] "I've generated a pairing code: 847291.
       Enter this in the Tacticl desktop app within 5 minutes."

User: "Add strategiz-core to my repos"
Agent: [uses manage_repo] "Done. I've granted access to cuztomizer/strategiz-core.
       Your MacBook Pro can now work with this repo."

User: "Set my MacBook to handle 3 tasks at once"
Agent: [uses manage_device] "Updated. Your MacBook Pro can now run up to 3
       parallel tactics."

User: "What's connected?"
Agent: [uses connection_status] "You have 2 devices (MacBook Pro online,
       iPhone offline), Twitter and LinkedIn connected, and 3 repos
       under purview."

User: "Block reddit.com from my browsing"
Agent: [uses manage_settings] "Added reddit.com to your domain blocklist."
```

---

## Settings Page REST Endpoints

### User Config
- `GET /api/settings` — get user's full config
- `PUT /api/settings` — update user config fields

### Device Settings
- `GET /api/settings/devices/{deviceId}` — get device settings
- `PUT /api/settings/devices/{deviceId}` — update device settings

### Repository Management
- `GET /api/repos` — list granted repos (exists)
- `POST /api/repos/grant` — grant repo access (exists)
- `DELETE /api/repos/{id}` — revoke repo access (exists)

### Connection Status
- `GET /api/settings/connections` — aggregated view of all devices, integrations, repos

---

## GDPR: UserDataPurgeService

Since operational data is flat, create an explicit service for user data deletion:

```java
@Service
public class UserDataPurgeService {
    private static final List<String> USER_COLLECTIONS = List.of(
        "devices", "device_sessions", "social_integrations", "repo_grants",
        "agent_tokens", "reminders", "spark_templates",
        "sparks", "tactics", "execution_logs", "checkpoints", "social_posts",
        "device_commands", "action_confirmations", "agent_audit_log"
    );

    public void purgeAllUserData(String userId) {
        // 1. Delete tacticl_users/{userId} and subcollections
        // 2. For each flat collection: query userId == X, batch delete
        // 3. Delete pairing_codes/sessions for userId
    }
}
```

---

## Index Recommendations

### Composite Indexes (flat collections)

```
sparks: (userId, createdAt DESC)
sparks: (status, nextRunAt ASC)           <- scheduler job
social_posts: (state, publishDate ASC)    <- publisher job
social_posts: (userId, createdAt DESC)
device_commands: (deviceId, state, createdAt)
device_sessions: (deviceId, isActive)
device_sessions: (userId, isActive)
action_confirmations: (userId, state)
action_confirmations: (state, expiresAt)
agent_audit_log: (userId, createdAt DESC)
execution_logs: (sparkId, timestamp ASC)
tactics: (sparkId, createdAt ASC)
checkpoints: (sparkId)
```

### Collection Group Indexes (for nested subcollections — admin queries only)

```
COLLECTION GROUP "reminders": (delivered, remindAt ASC)   <- delivery job
```

### Single-Field Index Exemptions (reduce write costs)

```
execution_logs: toolInput, toolOutput     <- large, never queried
agent_audit_log: responseText, commandText
sparks: result
social_integrations: accessToken, refreshToken
```

### TTL Policies

```
pairing_codes: expiresAt          <- auto-delete after 5 min
pairing_sessions: expiresAt       <- auto-delete after 5 min
execution_logs: timestamp         <- auto-delete after 90 days
agent_audit_log: createdAt        <- auto-delete after 365 days
```
