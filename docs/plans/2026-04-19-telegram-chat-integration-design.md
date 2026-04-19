# Telegram Chat Integration Design

**Date**: 2026-04-19
**Status**: Draft
**Author**: Claude + Gabriel (brainstorm)
**Target executor**: Fritz (migration branch)

## Goal

Add Telegram as an additional chat surface for Tacticl so users can create sparks, approve PDLC checkpoints, and receive pipeline progress from anywhere — without installing the mobile app or opening the web dashboard. Treat Telegram as an **input/output adapter** on top of the existing `SparkService` + `PipelineEventEmitter`, not a parallel chat stack.

## Motivation

The PDLC pipeline needs a reliable chat surface for:
1. **Spark creation on the go** — "build me a landing page" from a phone lock screen.
2. **Checkpoint approvals** — pipelines frequently pause on `REWORK`, `SECURITY_ANALYST`, and `DEVOPS` gates. Today these push through FCM → mobile app, which requires opening the app. Telegram inline buttons (`✅ Approve / ❌ Reject / ✏️ Modify`) collapse that to one tap.
3. **Async progress updates** — pipeline events currently fan out to Firestore + WebSocket + FCM. Telegram is a 4th channel requiring no new plumbing.
4. **Universal reach** — no app install, works on any device, push notifications native, zero cost.

We considered building our own chat UI end-to-end (and have partial work in `tacticl-web` / `tacticl-mobile`). That still makes sense for rich artifact review and pipeline visualization, but for **text-in / approve-out** flows, Telegram is a better native experience.

## Alternatives Considered

| Platform | Verdict | Reason |
|---|---|---|
| **Telegram** | ✅ **Chosen first** | Free Bot API, no quotas, inline buttons, webhook or long-poll, zero friction for users |
| **Slack** | 🟡 Phase 2 | Best fit for team/work sparks, threading for pipelines; requires workspace install so not universal |
| **Discord** | 🟡 Maybe | Good for dev-community sparks; less "work" vibe than Slack |
| **WhatsApp Business** | ❌ Skip for now | Meta Cloud API requires template approval, strict 24h session window kills async PDLC checkpoints |
| **SMS (Twilio)** | 🟡 Fallback | Per-message cost, no rich interactivity; useful only for urgent checkpoint escalation |
| **Email** | 🟡 Phase 2 | Natural for async approvals (`reply APPROVE <token>`); reuses the same checkpoint token; low-engagement channel |
| **MS Teams** | ❌ Defer | Enterprise-only, heavy Bot Framework SDK, no current user demand |
| **iMessage / Signal** | ❌ Blocked | No public bot API |
| **Matrix** | ❌ Defer | Open protocol but tiny user base, self-host overhead |

**Phasing**: Telegram now → Slack + Email once Telegram flows prove the adapter pattern → reassess WhatsApp if a user specifically asks.

## Design

### 1. Module Structure

Follows existing `client-google` / `client-twitter` patterns (see `2026-03-01-google-photos-integration-design.md`).

```
client/client-telegram/
  TelegramConfig.java              @ConfigurationProperties("tacticl.telegram")
                                    baseUrl, rateLimitPerMinute (default 30),
                                    webhookPath, enabled flag
  TelegramVaultConfig.java         loads bot-token, webhook-secret from
                                    secret/tacticl/telegram
  ClientTelegramConfig.java        @Bean: config, Bucket4j rate limiter,
                                    RestClient, TelegramBotClient
  TelegramBotClient.java           extends BaseHttpClient
                                    sendMessage, sendPhoto, editMessageText,
                                    editMessageReplyMarkup, answerCallbackQuery,
                                    setWebhook, deleteWebhook, getMe
  dto/
    Update.java                    top-level Telegram update envelope
    Message.java                   incoming message
    CallbackQuery.java             inline button press payload
    InlineKeyboardMarkup.java      outbound reply markup
    InlineKeyboardButton.java      individual button
    SendMessageRequest.java
    EditMessageRequest.java

service/service-telegram/
  TelegramWebhookController.java   POST /v1/telegram/webhook
                                    Tier 0 (public, HMAC-validated via
                                    X-Telegram-Bot-Api-Secret-Token header)
                                    dispatches Update → TelegramDispatchService
  TelegramLinkController.java      POST /v1/telegram/link   (creates one-time link token)
                                    GET  /v1/telegram/status (user's link status)
                                    DELETE /v1/telegram/link (unlink)

business/business-telegram/
  TelegramDispatchService.java     routes incoming Update:
                                    - command /start <token>  → TelegramUserLinker
                                    - command /sparks         → lists active sparks
                                    - command /cancel <id>    → cancels spark
                                    - plain text              → SparkService.createSpark()
                                    - callback_query          → CheckpointService.resolve()
  TelegramUserLinker.java          generates + validates link tokens (15min TTL),
                                    binds telegram chat_id to userId,
                                    persists TelegramLink entity
  TelegramEventSubscriber.java     @EventListener on PipelineEventEmitter
                                    filters events by user, formats for Telegram,
                                    sends via TelegramBotClient
  TelegramMessageFormatter.java    pipeline events → Markdown/HTML messages,
                                    checkpoint events → inline keyboards
  TelegramSparkCallbackHandler.java callback_query → Approve/Reject/Modify
                                    updates checkpoint state, edits original
                                    message to show decision

data/data-telegram/
  TelegramLink.java                entity under tacticl_users/{userId}/telegram_link/{chatId}
                                    fields: chatId, username, linkedAt,
                                    notificationPrefs (checkpoints, progress, completion)
  TelegramLinkRepository.java      extends FirestoreSubcollectionRepository<TelegramLink>
  TelegramLinkTokenRepository.java short-lived link tokens (Firestore TTL on expiresAt)
```

### 2. User Linking Flow

One-time token bridge between authenticated Tacticl user and anonymous Telegram chat.

```
Web dashboard / mobile:
  User taps "Connect Telegram"
    → POST /v1/telegram/link
    → returns { token: "abc123", botUrl: "https://t.me/tacticl_bot?start=abc123" }
    → opens link (deep link on mobile)

Telegram:
  User sees bot, taps Start
    → bot receives /start abc123
    → TelegramUserLinker.redeemToken("abc123", chatId, username)
    → validates token (not expired, not used)
    → creates TelegramLink under tacticl_users/{userId}/telegram_link/{chatId}
    → deletes token
    → bot replies "✅ Linked as <user>. Send me a message to create a spark."

Unlink:
  /unlink command OR DELETE /v1/telegram/link from dashboard
```

### 3. Incoming Message Flow (Spark Creation)

```
User texts bot:
  "Build a landing page for my podcast"

Webhook receives Update → TelegramWebhookController
  → HMAC validates secret header
  → TelegramDispatchService.handle(update)
  → resolveUserId(chatId) via TelegramLinkRepository
  → if unlinked: reply "Please link your account first: <dashboardUrl>"
  → else: SparkService.createSpark(userId, text, "telegram")
  → reply with sparkId + status link
  → TelegramEventSubscriber takes over for progress updates
```

### 4. Checkpoint Approval Flow

```
Pipeline hits checkpoint (e.g., REVIEWER rework or DEVOPS deploy gate)
  → PipelineEventEmitter.emit(CheckpointCreated)
  → TelegramEventSubscriber receives event
  → loads TelegramLink for user
  → formats message:
      "🔔 Checkpoint: Review required for spark X
       REVIEWER found 2 issues in IMPLEMENTER output.
       [View artifact]"
      with InlineKeyboardMarkup:
        ✅ Approve    ❌ Reject    ✏️ Request changes
  → bot.sendMessage(chatId, ...)
  → message persisted with messageId for later edit

User taps ✅ Approve:
  → Telegram sends callback_query to webhook
  → TelegramSparkCallbackHandler
      .handle(callbackQuery)
      .parseCallbackData("checkpoint:<id>:APPROVE")
  → CheckpointService.resolve(checkpointId, APPROVE, userId)
  → bot.editMessageReplyMarkup(clear buttons)
  → bot.editMessageText("✅ Approved at 14:32 — pipeline resumed")
  → bot.answerCallbackQuery(callbackQueryId, "Approved")
```

Callback data format: `action:entityType:entityId:decision` (≤ 64 bytes, Telegram limit).

### 5. Outbound Event Channel

Add Telegram as a 4th fan-out target alongside Firestore/WebSocket/FCM. `PipelineEventEmitter` already supports multiple subscribers — no change to existing contract.

```java
@Component
@ConditionalOnProperty("tacticl.telegram.enabled")
public class TelegramEventSubscriber {
  @EventListener
  public void onPipelineEvent(PipelineEvent event) {
    telegramLinkRepo.findByUserId(event.userId())
      .filter(link -> link.notificationPrefs().shouldNotify(event.type()))
      .ifPresent(link -> dispatchToTelegram(link, event));
  }
}
```

Notification prefs per link (respects user choice):
- `checkpoints` (default on)
- `progress` (default off — every role transition is noisy)
- `completion` (default on)
- `failures` (default on)

### 6. Webhook Security

- **Endpoint**: `POST /v1/telegram/webhook` — public, Tier 0 (no auth filter).
- **HMAC validation**: Telegram sends `X-Telegram-Bot-Api-Secret-Token` header on every request. Set via `bot.setWebhook(secret_token=...)`. Reject requests where header != stored secret.
- **Rate limit**: Bucket4j per-chat-id (e.g., 20 messages/minute) to prevent abuse if webhook-secret leaks.
- **Webhook URL**: `https://tacticl-core-<env>.run.app/v1/telegram/webhook` — registered once per environment on startup (idempotent via `bot.setWebhook`).

### 7. Configuration

**Vault secrets** (`secret/tacticl/telegram`):
- `bot-token` — from BotFather
- `webhook-secret` — random 32-byte string, rotatable

**Feature flag**: `tacticl.telegram.enabled` (default false; enable per environment)

**Application properties**:
```yaml
tacticl:
  telegram:
    enabled: true
    base-url: https://api.telegram.org
    webhook-path: /v1/telegram/webhook
    rate-limit-per-minute: 30
    link-token-ttl-minutes: 15
```

### 8. Firestore Schema Additions

Under hybrid schema (Approach B):

```
tacticl_users/{userId}/
  telegram_link/{chatId}         (new subcollection)
    chatId, username, firstName,
    linkedAt, notificationPrefs, isActive

telegram_link_tokens/             (flat, TTL 15min)
  {token}: userId, createdAt, expiresAt, consumedAt
```

No new top-level collections beyond the tokens (short-lived, could live in Redis if we add caching later).

## Risks & Open Questions

1. **Webhook cold starts** — Cloud Run cold starts can be 3–5s. Telegram retries on timeout, so idempotency on `update.update_id` is required (dedupe window of 24h).
2. **Callback data size** — 64-byte limit means long checkpoint IDs need shortening. Use first 8 chars of UUID + lookup, or store full mapping in a `telegram_callback_map` collection.
3. **Multi-device linking** — should one Tacticl user be able to link multiple Telegram chats (personal + work)? Design allows it (chatId in doc path), but need UX decision on which chat receives notifications.
4. **Media upload** — should users be able to send images/files to create sparks with attachments? Scope v2.
5. **Bot personality** — should Tacticl bot have its own handle per environment (`tacticl_qa_bot`, `tacticl_bot`) or one bot routing by env header? Recommend per-env bots for clear separation.
6. **Privacy** — Telegram messages pass through Telegram servers. Document this for users; don't use for sensitive outputs (e.g., secrets in artifacts).

## Out of Scope (this iteration)

- Telegram Premium features (stickers, custom emoji)
- Group chat support (would need different permission model)
- Voice message transcription via Telegram (use existing Whisper path — user records on-device)
- Payments via Telegram Stars
- Mini Apps (Telegram WebApp) — possible future for artifact review

## Migration / Rollout

1. **Phase 1 — Infrastructure (Fritz's branch)**
   - Create `client-telegram`, `service-telegram`, `business-telegram`, `data-telegram` modules
   - Bot token provisioning (BotFather → Vault)
   - Webhook registration on startup
   - Basic `/start <token>` linking
2. **Phase 2 — Spark creation**
   - Text-to-spark flow, reply with status
   - `/sparks` and `/cancel` commands
3. **Phase 3 — Checkpoint approvals**
   - `TelegramEventSubscriber` + inline keyboards
   - Callback handling + checkpoint resolution
4. **Phase 4 — Progress updates & prefs**
   - Notification preferences UI on web dashboard
   - Progress/completion/failure messages
5. **Phase 5 (future)** — Slack adapter reusing the same `TelegramDispatchService` shape (abstract `ChatAdapter` interface if patterns align).

## Client Repos Affected

Per `feedback_cross_repo_context.md` — **verify with Gabriel before assuming**:

- **tacticl-core** (this repo): all backend work
- **tacticl-web**: add "Connect Telegram" flow, notification prefs UI
- **tacticl-mobile**: same (deep link into Telegram app)
- **tacticl-device**: no change (device agent runs locally, unaffected)
- **tacticl-docs**: add Telegram to ecosystem diagram + integrations doc

## References

- Telegram Bot API: https://core.telegram.org/bots/api
- Webhook security: https://core.telegram.org/bots/webhooks#the-short-version
- Existing pattern: `docs/plans/2026-03-01-google-photos-integration-design.md`
- PDLC checkpoints: `docs/architecture/pdlc-pipeline-architecture.md`
- Event fan-out: `PipelineEventEmitter` in `business-agent`
