# Telegram Bot Runbook

**Audience:** on-call operator. **Scope:** the Tacticl Telegram bot integration —
group lifecycle, command surface, outbound rate-limit diagnostics. Developer
docs live in the design plan under `docs/superpowers/plans/`; this file is
deliberately operator-shaped.

## At a glance

| Property                                  | Default | Consumed by |
|-------------------------------------------|---------|-------------|
| `tacticl.telegram.enabled`                | `false` | `@ConditionalOnProperty` on every Telegram bean |
| `tacticl.telegram.base-url`               | `https://api.telegram.org` | `TelegramBotClient` |
| `tacticl.telegram.webhook-path`           | `/v1/telegram/webhook` | webhook controller, registrar |
| `tacticl.telegram.public-base-url`        | _(env-specific)_ | `TelegramWebhookRegistrar` |
| `tacticl.telegram.bot-username`           | _(env-specific)_ | `GroupMembershipHandler` |
| `tacticl.telegram.rate-limit-per-minute`  | `30` | per-user inbound throttle |
| `tacticl.telegram.link-token-ttl-minutes` | `15` | DM `/start` link token expiry |
| `tacticl.telegram.outbound.capacity`      | `200` | `TelegramOutboundQueue` (per-chat ring) |
| `tacticl.telegram.outbound.drain-ms`      | `50` | `OutboundDrainer` `@Scheduled` cadence |
| `tacticl.telegram.status-debounce-ms`     | `10000` | **not yet wired** — see "Status debounce" below |
| `tacticl.whisper.enabled`                 | `false` | Server-side voice transcription (gates `WhisperTranscriptionService` + `VoiceMessageHandler`) |
| `tacticl.whisper.base-url`                | `https://api.openai.com` | `WhisperClient` |
| `tacticl.whisper.model`                   | `whisper-1` | `WhisperClient` |
| `tacticl.whisper.rate-limit-per-minute`   | `60` | per-pod outbound throttle to OpenAI |

> **Heads-up:** `tacticl.telegram.status-debounce-ms` is exposed as a
> configuration key but is **not currently consumed** by `PinnedStatusService`.
> The service hard-codes `DEBOUNCE_WINDOW = 10s` and `QUIET_WINDOW = 2s` as
> private constants. Setting the property today has no runtime effect.
> Tracked as a follow-up — wire `@Value` injection and remove the constants.

## 1. Privacy Mode must be OFF (BotFather)

In BotFather, run `/mybots` → pick the bot → **Bot Settings → Group Privacy →
Turn off**.

**Why:** Telegram's default ("privacy mode ON") only delivers messages that
start with `/` to the bot. The Tacticl bot needs to see the **full text** of
group messages because:

- `/spark` is the primary entry point and frequently appears with free-form
  trailing prose ("`/spark fix the OAuth bug, it's been broken since…`"). The
  router parses the entire message body, not just the slash-token.
- Plain-text replies (no slash) are how members confirm pipeline checkpoints
  and provide credentials. If privacy mode is ON, those replies never reach
  the webhook and the pipeline stalls waiting for input.

**Symptom of misconfiguration:** the bot responds to `/help` but appears deaf
to anything without a leading slash. Fix by toggling Privacy Mode OFF, then
**remove and re-add the bot to each existing group** — Telegram caches the
privacy setting per-membership.

## 2. Group join lifecycle and `/init`

The bot follows this lifecycle when added to a group:

```
operator adds bot to group
  └─> Telegram delivers `my_chat_member` update (status: left → member)
        └─> GroupMembershipHandler detects "added" transition
              └─> enqueues welcome message via TelegramOutboundQueue
                    "👋 Hi! I'm ready to run a Tacticl project in this group.
                     A linked Tacticl user can claim this group by sending /init."

linked user sends `/init` in the group
  └─> InitCommand creates a TelegramProjectLink (chatId → projectId)
        └─> bot confirms the group is now a Tacticl project

operator removes bot from group
  └─> Telegram delivers `my_chat_member` update (status: member → left)
        └─> GroupMembershipHandler.orphan() — see "Orphan vs Archive" below
```

A group **must** be claimed via `/init` from a linked Tacticl user before any
project commands work. Any user can `/init` if they've completed the DM link
flow (`/start` in DM with the bot, exchange a one-time link token, TTL
controlled by `link-token-ttl-minutes`).

## 3. Orphan vs Archive semantics

`TelegramProjectLink.status` has two terminal states. They look similar but
have different recovery paths.

| Status     | Set by                                        | Cause                                            | Recovery                                      |
|------------|-----------------------------------------------|--------------------------------------------------|-----------------------------------------------|
| `ARCHIVED` | `/archive` or `/leave` command (intentional) | An owner explicitly retired the project          | Recreate via `/init` in a fresh group         |
| `ORPHANED` | `GroupMembershipHandler` on bot removal       | Bot was kicked / removed from the group          | Re-add bot to the same group → `/init` reuses chat |

**Rule of thumb:** `ARCHIVED = a human said stop`. `ORPHANED = the bot was
ejected and the link is dangling`. Both set `isActive=false`; both stop the
project from accepting new commands. Logs distinguish them:

- archive → `ArchiveCommand`/`LeaveCommand` audit log entry
- orphan  → `Project {id} orphaned by bot removal from chat {chatId}`
  (`GroupMembershipHandler` at INFO)

If you see an unexpected `ORPHANED`, check whether someone removed the bot or
whether the bot itself crashed mid-restart and missed the `my_chat_member`
add-back. Re-adding the bot does **not** auto-recover; the user must `/init`
again.

## 4. Command surface

Registered at startup by `TelegramCommandRegistrar` (publishes to BotFather so
they appear in Telegram's `/` autocomplete). All commands except `/help` are
**group-scoped**.

| Command     | Description                                |
|-------------|--------------------------------------------|
| `/init`     | Claim this group as a Tacticl project      |
| `/spark`    | Start a project spark                      |
| `/status`   | Show project status                        |
| `/members`  | List project members                       |
| `/grant`    | Grant a role to a member                   |
| `/revoke`   | Revoke a member's role                     |
| `/transfer` | Transfer project ownership                 |
| `/archive`  | Archive this project                       |
| `/leave`    | Archive project and leave group            |
| `/help`     | Show available commands                    |

DM-scoped commands (private chat with the bot only):

| Command     | Description                                |
|-------------|--------------------------------------------|
| `/whoami`   | Show your linked Tacticl identity          |
| `/projects` | List Tacticl projects you belong to        |
| `/unlink`   | Unlink your Tacticl account from Telegram  |

`/unlink` is blocked if you are the sole owner of any active project — transfer
ownership first via `/transfer` in the affected group.

Source of truth: each command's `description()` override in
`business-telegram/.../command/*.java`. If you add or rename a command, the
registrar republishes on next deploy — no manual BotFather edit required.

### Voice messages

When `tacticl.whisper.enabled=true`, voice notes posted in a linked group are
treated as `/spark`-equivalents:

```
user posts voice note in linked group
  └─> TelegramDispatchService routes to VoiceMessageHandler
        └─> bot.getFile + downloadFile → audio bytes
              └─> TranscriptionService (Whisper) → text
                    └─> TelegramSparkInitiator (same path text mentions take)
                          └─> AgentCommandService → spark + classify + route
```

Prerequisites:
- `tacticl.whisper.enabled=true` (this gates the bean — when false, voice notes
  are silently dropped, preserving prior behaviour).
- Vault key at `secret/strategiz/openai` with field `api-key`.

Symptoms of misconfiguration:
- "⚠️ Couldn't download voice message." — Telegram `getFile` failed; check bot
  token, network egress to `api.telegram.org`.
- "⚠️ Couldn't transcribe voice. Try sending text." — Whisper API call failed;
  check OpenAI key, quota, and network egress to `api.openai.com`.

## 5. Rate-limit diagnostics

Telegram's hard ceiling is **1 message per second per chat**. Tacticl enforces
this client-side via `TelegramRateLimiter` (shared bean, AtomicLongMap of
last-send timestamps). Both `OutboundDrainer` and `PinnedStatusService` go
through the same limiter, so two unrelated send paths never collectively
breach the cap.

### Architecture

```
producer (handler / formatter)
  └─> TelegramOutboundQueue.enqueue(chatId, msg)   ← per-chat ArrayBlockingQueue, capacity = 200
        └─> @Scheduled(50ms) OutboundDrainer.drain()
              ├─> for each active chatId:
              │     ├─> rateLimiter.tryAcquire(chatId)   ← false = skip this tick
              │     └─> queue.poll(chatId) → bot.sendMessage(...)
```

### What to look for in logs

- **Send failures:** `OutboundDrainer` logs at ERROR — `Outbound send failed
  for chat {chatId}` with the `RuntimeException`. This is the only outbound
  log line on the hot path; if you see a burst of these for one `chatId`,
  that chat is likely 429'd by Telegram or has lost network.
- **Queue saturation:** `TelegramOutboundQueue.enqueue` returns `false` when
  the per-chat queue is full (capacity 200). **Today this is silent at the
  queue level** — there is no log line when an enqueue is dropped. If
  outbound messages appear to "vanish" but no errors fire, suspect
  saturation. Mitigations: increase `tacticl.telegram.outbound.capacity` for
  bursty deploys, or shorten `tacticl.telegram.outbound.drain-ms` to drain
  more frequently.
- **Rate-limit hits:** `TelegramRateLimiter.tryAcquire` returning `false` is
  also silent — the drainer just `continue`s past that chat. To confirm a
  chat is being throttled rather than offline, check that the message
  eventually goes through on a subsequent tick (look for it in the
  destination chat) and that there are no `Outbound send failed` errors for
  that chatId.

### Quick triage

1. User reports "the bot isn't responding."
2. Check `OutboundDrainer` ERROR logs filtered by their `chatId` — any 4xx
   from Telegram?
3. If clean: confirm `tacticl.telegram.enabled=true` for the running pod and
   that the inbound webhook is reaching the service (look for the controller
   handling `/v1/telegram/webhook`).
4. If both clean: check whether their group's `TelegramProjectLink` is
   `ORPHANED` or `ARCHIVED` — commands silently no-op against an inactive
   project.
5. If the project is active and inbound is firing: suspect outbound queue
   saturation. See "Queue saturation" above. Bouncing the pod drops the queue
   (it's in-memory only) — only do this if you've confirmed messages are
   stuck, not in-flight.

## 6. Status debounce (placeholder, not yet wired)

`PinnedStatusService` updates the pinned project status message in each
project's group. Today it uses two hard-coded windows:

- `DEBOUNCE_WINDOW = 10s` — hard cap; even a noisy producer is flushed at
  least every 10s
- `QUIET_WINDOW = 2s` — soft window; if no new updates arrive for 2s, flush
  immediately

The new `tacticl.telegram.status-debounce-ms` property is intended to make
the 10s hard cap configurable. **It is not yet consumed** — see "Heads-up"
in the table above. The follow-up issue should:

1. Inject `@Value("${tacticl.telegram.status-debounce-ms:10000}")` into
   `PinnedStatusService`.
2. Replace the `DEBOUNCE_WINDOW` constant with the injected value.
3. Decide whether `QUIET_WINDOW` gets its own key or stays a derived
   constant (currently 1/5 of the debounce window).

Until that lands, changing the property does nothing at runtime — don't tune
it expecting an effect.
