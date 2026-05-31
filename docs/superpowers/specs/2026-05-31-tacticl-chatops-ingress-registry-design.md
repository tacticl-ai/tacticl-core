# Tacticl ChatOps Ingress + Entry-Point Registry — Design

Status: **approved-in-principle (brainstorm 2026-05-31), ready to write the build plan.**

## 1. Summary & the converged decision

Tacticl becomes the platform's **conversation layer + entry-point registry/management plane** for development/feedback chat-ops across *all* products. A human reports a problem or feature request in a chat channel → tacticl resolves which product/repo/pipeline it belongs to → calls the **arbiter** (the universal, product-agnostic agent engine) → the arbiter writes a fix/feature **PR directly into that product's repo** → feedback returns through tacticl to the originating channel.

**Layering (the whole platform, settled):**
```
CONVERSATION LAYER  →  tacticl: channel adapters + ENTRY-POINT REGISTRY + identity + human triage/interview
   Telegram→tacticl   Discord→strategiz   (future: voice/Jarvis, web-chat, email, pointstax…)
        │ resolve entry point → {product, repo, pipeline, admins}; SubmitPipeline
        ▼
AGENT ENGINE        →  ARBITER (product-agnostic). Durable Temporal workflow + agents + LLM gateway.
        │ GitHub App: per-run least-privilege token for the TARGET repo
        ▼
TARGET REPO         →  PR opened directly in strategiz-core / tacticl-core / … (the product's running app is NOT in the loop)
        │
   callbacks ──▶ tacticl ──▶ originating channel (card / voice) ; human approve/answer ──▶ tacticl ──▶ arbiter signal
AUTH / IDENTITY     →  cidadel (who can trigger/approve, per product)
```

**Load-bearing decisions (locked in the brainstorm):**
1. **tacticl = the conversation layer**, not the engine. Its agentic "primary piece" already moved to the arbiter; its remaining identity *is* the multi-channel conversational front-end + the entry-point registry.
2. **arbiter = universal engine.** Product-agnostic. Can improve *any* product because it holds a **GitHub App installed on every product repo**; each run mints a per-run, single-repo, least-privilege installation token for the target repo.
3. **The product's running app is never in the loop.** strategiz-api does nothing here; `strategiz-core` is only the *repo* the PR lands in.
4. **Entry points are registered + managed in tacticl.** A product can have many (Discord channels, Telegram, voice, web-chat, email…); each is a registry row mapping it to `{product, repo, pipeline, admin allowlist, adapter}`. New entry point = a row (+ adapter if a new channel type); zero arbiter change, zero product-app change.
5. **Consequence — revert the misplaced strategiz-core PDLC ingress** (committed `f4eaa0b2a`). It was built as a strategiz-side ingress before we resolved that ingress lives in tacticl. The arbiter engine + arbiter-side intake **stay**; the strategiz-core `service/business/data-pdlc-intake` + `client-discord` + the rich `client-arbiter-pipeline` overload get reverted (per "remove legacy, don't deprecate").

## 2. Entry-point registry (the product-aware config authority)

A new tacticl module owns the registry — the single source of truth mapping an inbound chat context to its routing.

| field | meaning |
|---|---|
| `entryPointId` | stable id, e.g. `discord:guild/channel` or `telegram:chatId` |
| `adapter` | `discord` \| `telegram` \| `voice` \| `web` \| `email` … |
| `product` | `strategiz` \| `tacticl` \| `pointstax` … |
| `repo` | target repo for PRs (`strategiz-core`, `tacticl-core`, `strategiz-ui`…) |
| `pipeline` | `pdlc-fix` \| `pdlc-feature` \| `tacticl-dev` … (default routing hint) |
| `adminAllowlist` | who may trigger/approve from this entry point |
| `enabled` | dormant by default; flip on per entry point |

tacticl resolves every inbound message → its registry row → builds the `SubmitPipeline` request. Source-channel is a routing **hint** (#35): a fix-channel defaults `pdlc-fix`, a feature-channel `pdlc-feature`; the triage step confirms. Alert-channel intake is **admin-triggered**, never auto-fire.

Example seed rows: `discord:#sev*→strategiz/strategiz-core/pdlc-fix`, `discord:#new-functionality→strategiz/strategiz-core/pdlc-feature`, `telegram:@tacticl→tacticl/tacticl-core/tacticl-dev`.

## 3. Discord adapter (transport)

**Primary: Interactions-webhook** — mirrors tacticl's existing `service-telegram` webhook pattern almost exactly (HTTP POST → Java controller → dispatch). The admin triggers intake explicitly:
- a **message context-menu command** ("Apps → Send to PDLC") right-clicked on a message that carries the screenshot — the interaction payload includes the message + attachments; or
- a slash command `/pdlc <text>`; and
- **buttons** the bot posts (approve/reject/answer) come back as interactions too.

Discord POSTs these to `POST /v1/discord/interactions` (signature-verified with the app's Ed25519 public key) → `DiscordDispatchService` → registry resolve → arbiter. **No persistent gateway, no Node sidecar, all-Java**, and it enforces admin-triggered intake (consistent with #35 — won't auto-ingest every alert/false-positive).

**Future option: gateway (JDA WebSocket)** — only if passive "drop an image, no command" auto-read is wanted. Documented, not built in v1 (it adds a stateful WebSocket + reads every message). Decision recorded; revisit if the explicit-trigger UX proves insufficient.

Mirror tacticl's Telegram module shape: `service-discord` (interactions controller) + `business-discord` (dispatch, command/interaction handlers, dedup, outbound) + `client-discord` (Discord REST API: post cards, register commands) + `data-discord` (if state needed) — paralleling `service/business/client/data-telegram`.

## 4. Dispatch → arbiter

Reuse tacticl's existing `client-ai-arbiter` (`ArbiterClient`/gRPC). On a triggered intake:
1. Upload any attachment bytes → MinIO → `minioKey` (never bytes in the proto).
2. Resolve the registry row → `{product, repo, pipeline, knowledgeNamespace, admins}`.
3. Run the human triage/interview if needed (tacticl's `business-conversation` — its strength) to confirm the requirement.
4. `SubmitPipeline(idempotency_key, product, pipeline_name, repo_url, request_context_json{printed-version + minioKey}, …)` → the arbiter's `pdlcRunWorkflow`.
5. Persist an intake record (tacticl `data`) for audit + correlation (the threaded `intakeId`).

Vision-extract stays an **arbiter** concern (its LLM gateway / `Generate`); tacticl supplies the image ref + the product-aware routing. (Reconcile with the arbiter Phase-3 intake activities during the plan: extract = arbiter; product-aware route = tacticl registry; classify/interview = tacticl conversation — settle exact split in writing-plans.)

## 5. Feedback loop

Arbiter callbacks (`pr_ready`, `merged`, `failed`, `needs-answer`) → a tacticl callback endpoint (HMAC, reusing the established `/internal` signer) → tacticl posts a card (or voice) back to the **originating** entry point. Human approve/reject/answer (a button click or reply) → tacticl → arbiter `SignalPipelineDecision`. Every human touchpoint (interview, plan approval, merge approval) is the same park-on-signal pattern.

## 6. Arbiter multi-repo access (GitHub App)

The arbiter needs write access to **every** product repo it may improve. One GitHub App installed on `strategiz-core`, `strategiz-ui`, `tacticl-core`, `pointstax`, … ; each run mints a **per-run, single-repo, least-privilege installation token** for the target repo, clones, writes, opens the PR. Branch protection on each repo's `main` requires human approval before merge.

## 7. Jarvis / voice (future entry point — architecture already accommodates)

A `voice` adapter is just another registry entry point: Deepgram STT in → tacticl intake → arbiter → ElevenLabs TTS out, rendered on tacticl's pulsating voice-sphere UI. *"There's a bug in strategiz sign-in"* → PR → *"Opened PR #142, want me to merge?"* tacticl already has `business-voice`. Scoped as a **separate follow-up spec** so it doesn't bloat the ingress build; this design is forward-compatible with it (no rework needed to add voice later).

## 8. Phased build plan

0. **Revert** the strategiz-core PDLC ingress (`f4eaa0b2a`): remove `service/business/data-pdlc-intake`, `client-discord`, `PdlcAttachmentStore`, the rich `client-arbiter-pipeline` overload + its settings/`@Import` wiring. Keep the proto sync (Phase 1) — it's harmless/shared. Arbiter side untouched.
1. **Entry-point registry** in tacticl (`data` + `service`/`business`): the schema above + management endpoints + seed rows (disabled).
2. **Discord Interactions adapter** in tacticl: `service-discord` controller (Ed25519-verified `/v1/discord/interactions`) + `business-discord` dispatch + `client-discord` (post cards, register the `/pdlc` slash + "Send to PDLC" context-menu command). Mirror `*-telegram`.
3. **Dispatch → arbiter**: MinIO upload + registry resolve + `SubmitPipeline` via `client-ai-arbiter`; intake record.
4. **Feedback**: arbiter-callback endpoint (HMAC) → channel card; button/reply → `SignalPipelineDecision`.
5. **GitHub App**: install on the target repos; per-run installation token in the arbiter host-git activities.
6. **Canary**: strategiz `#sev*`/`#new-functionality` registered + enabled, triage-mandatory, on strategiz-ui first.
7. **(Separate spec)** Voice/Jarvis adapter.

Each phase additive + dormant until its registry rows are enabled and the arbiter flag is on.
