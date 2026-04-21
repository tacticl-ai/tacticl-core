# Telegram Group-Project Model — Design Spec

**Date:** 2026-04-20
**Status:** Draft
**Owner:** Gabriel Jimenez

## Problem

Phase 1 Telegram integration (bot + DM link flow) is live in prod. Each Tacticl user can link one Telegram chat to their account. This is fine for personal push-to-talk style interaction but misses the collaboration model Tacticl is built around: a single user initiates a project, multiple humans collaborate across its PDLC, and the bot drives the roles + checkpoints.

A single DM between one user and `@tacticl_bot` cannot host multi-person collaboration, cannot separate contexts across projects, and forces all work into one interleaved thread.

## Goals

1. Each Tacticl project maps to a Telegram group.
2. The group is the primary surface for PDLC work: role outputs, checkpoints, and human collaboration happen inline.
3. Any linked Tacticl user can bootstrap a new project by adding the bot to a fresh Telegram group — no prior setup in the web/mobile app required.
4. Project owner controls who can run tasks; other members can observe or be granted execute rights.
5. Every action posted by a human is attributable to a Tacticl user, so spend and audit are traceable.

## Non-goals

- Per-user dedicated bots (Telegram caps bot creation; doesn't scale).
- Supporting multiple Tacticl projects in a single Telegram group.
- Bridging to external tools (GitHub, Linear, Slack) — listed as a follow-up.
- Telegram Mini Apps / webviews — future consideration.
- Rebuilding the full Tacticl UI inside Telegram; the app remains the canonical surface.

## Design Overview

**Group-first model.** A Tacticl project is born when a linked user creates a Telegram group, adds `@tacticl_bot`, and runs `/init`. The group's `chat_id` becomes the project's primary identity in Telegram. All PDLC lifecycle events, role outputs, and checkpoints flow through that group.

**Owner + grants.** The `/init` initiator becomes the project owner. Owner explicitly grants permission levels to other linked members. Unlinked members are silently no-op'd on commands, with a one-time DM nudge to link.

**Topics for clean threading.** When a group supports Forum Topics (supergroups with topics enabled), the bot auto-creates a topic per PDLC role (RESEARCHER, ARCHITECT, IMPLEMENTER, REVIEWER, …). Role outputs post to their topic; human chat flows in the general topic.

**DM identity flow reused.** The existing `TelegramLink` + token flow from Phase 1 keeps working for per-user identity proof. This spec adds a separate `TelegramProjectLink` for group↔project binding.

## User Flows

### 1. Identity linking (prereq, reuse from Phase 1)

Each person who wants to act in a Telegram group first must link their Telegram identity to a Tacticl account. Unchanged from Phase 1:

1. User logs into Tacticl web/mobile.
2. App calls `POST /v1/telegram/link` → deeplink `https://t.me/tacticl_bot?start=<token>`.
3. User taps → `/start <token>` in DM → bot redeems → `TelegramLink { telegramUserId, tacticlUserId }` created.

### 2. Project creation (new)

1. Alice creates a Telegram group, adds her teammates, adds `@tacticl_bot` with admin rights (required for topics, pins, kick-on-abuse).
2. Bot detects `my_chat_member` event, posts a welcome message in the group:
   > 👋 Hi! I'm ready to run a Tacticl project in this group. A linked Tacticl user can claim this group by sending `/init`.
3. Alice sends `/init`. Bot checks:
    - Alice is linked to a Tacticl account → ✅
    - Group isn't already claimed → ✅
    - Alice has quota to create another project → ✅
4. Bot creates a Tacticl project owned by Alice, stores `TelegramProjectLink { projectId, chatId, ownerUserId }`, posts:
   > ✅ Project `<auto-name or prompt>` is now active in this group. Owner: Alice. Type `/help` to see what I can do.
5. Bot pins the welcome status message (see "Status catch-up" below).
6. (Optional) If the group has Topics enabled, bot auto-creates one topic per PDLC role and stores `forumTopics: Map<PdlcRole, ThreadId>`.

### 3. Permission grants

After `/init`, the owner manages access with slash commands:

| Command | Who | Effect |
|---|---|---|
| `/grant @user <role>` | owner, admin | Sets member's permission to the specified role. |
| `/revoke @user` | owner, admin | Removes member's grant (defaults back to observer). |
| `/transfer @user` | owner | Transfers ownership. Previous owner becomes admin. |
| `/members` | anyone | Lists all members + their permission level. |

Permission roles:

| Role | Permissions |
|---|---|
| `observer` (default) | Read-only. Can't trigger sparks, approve checkpoints, or comment on PDLC threads. |
| `contributor` | Can run Tier 0 (read-only) sparks. |
| `runner` | Tier 0 + Tier 1 (mutations). Tier 2 (financial) still requires owner approval. |
| `admin` | All `runner` powers + grant/revoke members. Cannot delete project or transfer ownership. |
| `owner` | All `admin` powers + transfer/delete. Exactly one per project. |

### 4. Running a spark in the group

1. Member with sufficient role sends a natural-language message or `/spark <text>` in the group.
2. Bot checks:
    - Sender linked? If no → DM them "Please link your Tacticl account to participate. <link>", stop.
    - Sender has required permission? If no → reply ephemerally, stop.
    - Project within cost ceiling? If no → reply "Spending limit reached. Owner must raise `pipelineCostCeiling` in Tacticl settings.", stop.
3. Bot creates a Spark tied to the project, with `initiatorUserId = sender.tacticlUserId`.
4. PDLC orchestrator runs; role outputs post to their topic (or general if topics disabled).
5. Human checkpoints render as inline keyboards:
    > 🔎 **REVIEWER** has flagged 2 issues. Approve changes?
    > [Approve ✅]  [Request changes ✏️]  [Reject ❌]
6. Only members with `runner` or higher can tap the approval buttons. Bot validates on callback before acting.

## Command Surface

### Group commands

| Command | Scope | Who | Purpose |
|---|---|---|---|
| `/init` | group | any linked member | Claim group as Tacticl project. |
| `/help` | group | anyone | Show commands relevant to sender's role. |
| `/status` | group | anyone | Show live project state (active roles, pending checkpoints, cost-to-date). |
| `/members` | group | anyone | List members + permissions. |
| `/grant @user <role>` | group | owner, admin | Set member permission. |
| `/revoke @user` | group | owner, admin | Clear grant. |
| `/transfer @user` | group | owner | Transfer ownership. |
| `/spark <text>` | group | contributor+ | Run a spark. Also supported via plain-text mention or reply to bot. |
| `/cancel <sparkId>` | group | initiator, admin, owner | Abort in-flight spark. |
| `/approve` / `/reject` | group | runner+ | Resolve the most-recent pending checkpoint (for users who prefer typing over tapping). |
| `/archive` | group | owner | Archive project (soft-delete; group stays, bot goes silent). |
| `/leave` | group | owner, admin | Bot leaves the group. Archives project. |

### DM commands (reuse Phase 1)

| Command | Purpose |
|---|---|
| `/start <token>` | Redeem link token. |
| `/start` | Show "tap link in your dashboard" message. |
| `/whoami` | Show linked Tacticl identity. |
| `/projects` | List groups where sender is a member + their permission level. |
| `/unlink` | Disconnect Telegram from Tacticl account. |

All commands should be registered via BotFather's `/setcommands` so Telegram autocompletes them.

## Data Model

### New entity: `TelegramProjectLink`

```java
@Document(collection = "telegram_project_links")
public class TelegramProjectLink extends BaseMongoEntity {
    private String projectId;             // Tacticl project id
    private long chatId;                  // Telegram group chat_id
    private String ownerUserId;           // Tacticl userId of owner
    private String groupTitle;            // denormalized, useful for UX
    private ProjectStatus status;         // ACTIVE | ARCHIVED | ORPHANED
    private Map<PdlcRole, Long> forumTopics; // nullable; role → thread_id
    private Instant initializedAt;
    private String initClaimCode;         // used if we add two-phase `/claim` later
}
```

Index: unique on `chatId` (one project per group), index on `projectId` and `ownerUserId`.

### New entity: `TelegramMemberGrant`

```java
@Document(collection = "telegram_member_grants")
public class TelegramMemberGrant extends BaseMongoEntity {
    private String projectId;
    private long chatId;
    private String tacticlUserId;
    private long telegramUserId;
    private MemberRole role;              // OBSERVER | CONTRIBUTOR | RUNNER | ADMIN | OWNER
    private String grantedByUserId;       // who issued the grant
    private Instant grantedAt;
}
```

Compound unique index: `(projectId, tacticlUserId)`.

### Existing `TelegramLink` — unchanged

Still maps `telegramUserId ↔ tacticlUserId`. Group-level grants join from `telegramUserId` via this lookup.

## Lifecycle & Edge Cases

### Supergroup migration

When Telegram migrates a basic group to a supergroup, it sends `message.migrate_to_chat_id` in the old chat and `message.migrate_from_chat_id` in the new one. Bot must:

1. Detect the event.
2. Update `TelegramProjectLink.chatId` to the new id.
3. Re-pin the status message in the new supergroup if possible.
4. Re-create forum topics (old topic ids don't survive migration).

### Bot removed from group

`my_chat_member` event with status `left` or `kicked`:

1. Mark `TelegramProjectLink.status = ORPHANED`.
2. Cancel any in-flight PDLC runs for that project.
3. Notify owner via DM.
4. Keep data for 30 days for audit, then soft-delete.

### Owner leaves Telegram group

Telegram doesn't distinguish "left" from "was removed." If the owner is no longer a member:

1. Next action in the group triggers owner-check.
2. If owner is gone, bot posts "Owner @alice has left. An admin must run `/claim-owner` to take over, or the project will be archived in 7 days."
3. After grace period with no claim, archive the project.

### Project deleted in Tacticl

1. Bot posts farewell in group.
2. Cancels PDLC runs.
3. Bot leaves the group.
4. `TelegramProjectLink.status = ARCHIVED`.

### User unlinks Tacticl account

1. Their `TelegramMemberGrant`s are revoked.
2. If they were owner of any project, transfer to next-admin or archive (same as "owner leaves" path).

## Privacy & Data Handling

**Privacy Mode must be OFF** on `@tacticl_bot` (set via BotFather `/setprivacy → Disable`) so the bot can read natural-language messages in groups, not just `/commands`. Implications:

- The bot receives every message in every group it's in.
- **Retention policy (proposed):**
    - **Bot-directed messages** (commands, mentions, replies to bot): stored as part of the spark audit log, retained for the project's lifetime.
    - **Non-bot-directed human chat**: not persisted at all. Processed in-memory for contextual replies only. No database write.
    - **Role outputs** (bot → group): retained as PDLC artifacts.
- **GDPR right-to-erasure**: `/unlink` removes `TelegramLink` + all `TelegramMemberGrant`s. A separate endpoint deletes any stored bot-directed message content associated with that user.

**Encryption**: bot-token and webhook-secret already in Vault (Phase 1). No additional secrets.

## Rate Limits & Throttling

Telegram limits:

- 30 messages/second globally per bot.
- 1 message/second per chat (groups: up to 20/min).

PDLC roles can post rapidly. Need a per-chat outbound queue with 1-msg/sec pacing. If a role wants to post 4 messages fast (thinking → diff → explanation → buttons), queue and drain.

**Implementation sketch:** `TelegramOutboundQueue` service with a bounded per-chat Queue<Message>, drained by a `@Scheduled` task every 50ms. Existing `TelegramBotClient.sendMessage` becomes `enqueueMessage` for anything bound for a project group.

## Status Catch-Up

PDLC runs take minutes to hours. Members joining mid-run or returning after hours shouldn't have to scroll.

**Pinned status message.** At `/init`, bot pins a status message that it edits continuously:

```
📊 Tacticl Project — Status

Current phase: IMPLEMENTER (2/5)
Last activity: 3 min ago
Checkpoints pending: 1 (awaiting REVIEWER approval)
Spend: $2.14 / $50 ceiling
Active sparks: 1

Type /status for details.
```

Edits are rate-limited (Telegram allows ~1 edit/sec per message), so we debounce updates to every 10s.

## Observability & Audit

- Every command, spark trigger, and permission change gets a `TelegramAuditEvent` record with `{ chatId, telegramUserId, tacticlUserId, action, timestamp, payload }`.
- Existing Tacticl billing already tracks cost per Spark; we annotate `Spark.initiatorSource = TELEGRAM_GROUP` for filtering.
- Owner can query `/spending` in group or see the per-project breakdown in web.
- Metrics to expose: webhook latency, dispatch failure rate, outbound queue depth, per-project cost.

## Integration Points

### PDLC

- New spark initiator source: `TELEGRAM_GROUP`.
- `PipelineEventEmitter` fans out to a new `TelegramEventChannel` alongside Firestore/WebSocket/FCM. The channel formats role events as Telegram messages + inline keyboards and enqueues them.
- Checkpoint approval paths: web UI unchanged; Telegram adds inline-keyboard callbacks routed through a new `TelegramCheckpointResolver`.

### Voice

- Group members can send voice notes. Bot detects `message.voice`, runs Whisper, treats transcript as natural-language input (same path as `/spark <text>`).
- Aligns with the Tacticl push-to-talk mobile UX.

### Media artifacts

- PDLC outputs (diffs, screenshots, generated videos) attach as Telegram files when possible, with captions summarizing the artifact. Large files (>50 MB Telegram cap) get a link to the Tacticl web view.

## Out of Scope / Follow-ups

- **External integrations** (GitHub PR events, Linear issues, Slack bridging). Natural extensions once the group-project loop is solid.
- **Telegram Mini Apps** for a richer UI inside the chat.
- **Analytics & insights** on group usage.
- **Multi-language bot responses.** Use Telegram's `language_code` when we localize.

## Open Questions

1. **Multi-project quota per user.** Do we cap # of active Telegram-project groups per Tacticl user? If so, tier-based?
2. **Billing attribution when multiple runners contribute.** Owner pays, but should a report break down cost by initiator? I think yes, but confirming.
3. **Orphan handling when owner account is deleted.** Transfer to longest-tenured admin, or archive?
4. **Claim semantics if `/init` is abused.** Can anyone in the group `/init`? I propose yes, first-come-first-serve, reversible via `/transfer`. Alternative: require approval from a second admin.
5. **Is `TelegramAuditEvent` a net-new collection, or folded into existing `agent_audit_log`?** Prefer the latter for cross-surface queries.

## Implementation Chunks (rough sizing)

Not a plan yet — just scope sanity check:

1. **Data model + entities** (~1 day). `TelegramProjectLink`, `TelegramMemberGrant` + repos.
2. **Group event handling** (~2 days). `my_chat_member`, `message.migrate_*`, group command routing.
3. **Identity + permission resolver** (~2 days). Joins Telegram user → Tacticl user → grant → permission check.
4. **Command handlers** (~3 days). `/init`, `/grant`, `/revoke`, `/members`, `/transfer`, `/archive`, `/status`.
5. **Forum topics** (~1 day). Auto-create per-role topic; route role events to topics.
6. **Outbound queue + rate limiting** (~1 day).
7. **Pinned status message** (~1 day). Compose + debounced edits.
8. **Inline keyboards for checkpoints** (~2 days). Render + callback handling + permission re-check.
9. **Voice & media plumbing** (~2 days). Whisper hook + file attachment handling.
10. **End-to-end testing + runbook updates** (~2 days).

**Rough total:** 2.5–3 weeks of focused work. Could be parallelized via subagents; many chunks are independent.

## Next Step

Convert this spec into an implementation plan in `docs/superpowers/plans/` when we're ready to execute.
