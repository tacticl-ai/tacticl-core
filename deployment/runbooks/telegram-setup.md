# Telegram Integration — Environment Setup Runbook

One-time provisioning steps per environment (QA, prod). The application
refuses to register its webhook unless both Vault keys below are set.

## 1. Create a bot via BotFather

On Telegram, chat with [@BotFather](https://t.me/BotFather):

```
/newbot
Name:      Tacticl QA         (or Tacticl for prod)
Username:  tacticl_qa_bot     (must end in _bot, globally unique)
```

BotFather returns an HTTP API token. Keep it off disk — paste directly
into Vault in step 3.

Recommended follow-ups (optional, can be deferred):

```
/setdescription  → "Personal AI assistant — link your Tacticl account to receive updates and start sparks from chat."
/setcommands     → start - Link your account / status - Show linked chats
/setuserpic      → upload logo
```

## 2. Generate a webhook secret

Telegram posts each update with an `X-Telegram-Bot-Api-Secret-Token`
header. The app compares this header against the configured secret
using constant-time comparison.

```bash
openssl rand -hex 32
```

Capture the output for step 3.

## 3. Store both secrets in Vault

Tacticl QA uses Vault context `tacticl-qa`; prod uses `tacticl`.

```bash
# QA
vault kv put secret/tacticl-qa/telegram \
  bot-token="<token-from-botfather>" \
  webhook-secret="<hex-from-openssl>"

# Prod (later, after QA verification)
vault kv put secret/tacticl/telegram \
  bot-token="<prod-token>" \
  webhook-secret="<prod-hex>"
```

`TelegramVaultConfig` reads these at startup when
`tacticl.telegram.enabled=true`.

## 4. Configure bot username and public URL

These are non-secret config values (set via env or properties):

```
TELEGRAM_BOT_USERNAME=tacticl_qa_bot
```

`public-base-url` is set per environment in
`application-<env>.properties`:

- QA:   `https://api-qa.tacticl.ai`
- Prod: `https://api.tacticl.ai`

## 5. Deploy

```bash
# QA
gcloud builds submit --config deployment/cloudbuild/cloudbuild-qa.yaml .
```

On boot, `TelegramWebhookRegistrar` calls Telegram's `setWebhook`
with the public URL + secret. Look for:

```
INFO  io.tacticl.business.telegram.TelegramWebhookRegistrar — Telegram webhook registered at https://api-qa.tacticl.ai/v1/telegram/webhook
```

## 6. Verify registration from the outside

```bash
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

Expected JSON includes `"url": "https://api-qa.tacticl.ai/v1/telegram/webhook"`
and `"has_custom_certificate": false`.

## 7. Rotation

Rotate either secret with a single `vault kv put` and pod restart:

```bash
vault kv patch secret/tacticl-qa/telegram webhook-secret="<new-hex>"
# then restart Cloud Run revision — webhook is re-registered with the new secret
```

Bot-token rotation requires BotFather's `/revoke` + a fresh token.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| App logs `Telegram bot token missing — skipping webhook registration` | Vault keys absent or context wrong | Re-check `cidadel.vault.context` + key path |
| App logs `setWebhook returned ok=false` | Public URL not reachable from Telegram, or URL mismatch | Check Cloud Run ingress allows public traffic |
| Webhook posts return 401 | Secret mismatch between BotFather/Vault and `setWebhook` call | Re-run setWebhook or rotate secret via step 7 |
| `pending_update_count` grows | App not 2xx-ing webhook calls | Check Cloud Run logs for exceptions in `TelegramWebhookController` |
