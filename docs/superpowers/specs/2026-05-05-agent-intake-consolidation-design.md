# Agent Intake Consolidation — Design

**Date:** 2026-05-05
**Status:** Approved (verbal)
**Driver:** Telegram chat operations final-mile (voice handler + DM commands), surfacing pre-existing duplication between `AgentController` and `TelegramSparkInitiator`.

## Problem

`AgentController.executeCommand` and `TelegramSparkInitiator.initiate` both create sparks and route them, but the implementations have drifted:

| Capability | `AgentController` | `TelegramSparkInitiator` |
|---|---|---|
| Spark create | ✅ | ✅ |
| `SparkClassifierService` | ✅ | ❌ hardcoded `CODE` |
| `PdlcRouter` | ✅ | ✅ |
| Anthropic fallback for non-PDLC | ✅ | ❌ TODO |
| Cost ceiling | from request | hardcoded `50.0` |
| Provenance (initiator/projectId) | ❌ | ✅ |
| Cloud completion tracking | ✅ | ❌ |

Telegram voice messages are received and silently dropped (`TelegramDispatchService` line 158). There is no server-side transcription anywhere in tacticl-core; mobile transcribes client-side via Whisper.

## Goals

1. **One orchestration owner.** Spark create + classify + route + fallback + completion lives in one service. HTTP and in-process channels are thin adapters.
2. **One transcription owner.** Audio→text lives in one service. Channels (telegram now, mobile/web later) call it instead of re-implementing.
3. **Telegram voice operational.** `/spark`-equivalent flow when a user sends a voice message in a linked group.

## Non-Goals

- Mobile dropping client-side Whisper. Mobile keeps its current path; the server-side endpoint is added but not required.
- Multimodal richer than audio (images, video) — out of scope.
- `PipelineArtifact` data model rework (Task 35 deferred).

## Architecture

```
HTTP (mobile, web, future)        In-process (telegram)
       │                                 │
       ▼                                 ▼
AgentController                  TelegramSparkInitiator     VoiceMessageHandler
  (auth, DTO map)                  (permissions, provenance)  (download, transcribe)
       │                                 │                         │
       └─────────────┬───────────────────┴──────────┬──────────────┘
                     ▼                              ▼
            AgentCommandService            TranscriptionService
            (spark + classify              (Whisper-backed,
             + route + fallback             single bean)
             + completion)
                     │
                     ▼
              SparkService / PdlcRouter / Anthropic
```

### Components

**`AgentCommandService`** — new bean in `business-agent`.
- `AgentCommandResult execute(AgentCommand cmd)` where `AgentCommand` carries `userId`, `text`, optional `model`, optional `costCeilingUsd`, optional `initiatorSource`, optional `projectId`, optional `repoUrl`.
- Owns the body of today's `AgentController.executeCommand`: create spark, classify, PdlcRouter for CODE/DEVOPS, Anthropic fallback for the rest, completion + failure tracking.
- Returns enough for both adapters: `sparkId`, `responseText`, `pipelineRunId` (nullable), `pipelineTier`, `model`, `tokens`, `succeeded`.

**`AgentController`** — slimmed to a thin HTTP adapter.
- `@PostMapping("/command")` builds `AgentCommand` from request DTO + `AuthenticatedUser`, calls service, maps `AgentCommandResult` → `AgentCommandResponse`.
- New `@PostMapping("/voice")` accepts `multipart/form-data` (audio file + optional model), calls `TranscriptionService` then `AgentCommandService`, returns same response DTO.

**`TelegramSparkInitiator`** — slimmed to a thin in-process adapter.
- Permission check + `AgentCommand` build (with `SparkInitiatorSource.TELEGRAM_GROUP` and `projectId`) + outbound reply using returned `sparkId`.
- Loses hardcoded `CODE` — classifier now decides, which means social/research sparks no longer mis-route into PDLC.
- Loses hardcoded `50.0` cost ceiling — service applies the documented default; future `UserConfig` lookup belongs in the service.

**`TranscriptionService`** — new interface in `business-agent`.
- `String transcribe(byte[] audio, String mimeType)`.
- `WhisperTranscriptionService` impl in tacticl-core (new `client-whisper` module — small, ~80 lines: RestClient → multipart → `audio/transcriptions` endpoint, Vault-backed key at `secret/strategiz/openai`).
- Reasoning for in-tacticl-core: cidadel `client-openai-direct` is chat-only; arbiter has no STT. When cidadel grows STT, swap impl by changing one bean.

**`VoiceMessageHandler`** — new in `business-telegram/event/`.
- `bot.getFile(file_id)` (NEW on `TelegramBotClient`) → download bytes from `https://api.telegram.org/file/bot<token>/<file_path>` (NEW helper on client) → `TranscriptionService.transcribe(...)` → resolve identity + active project (same as `handlePlainText`) → call `TelegramSparkInitiator.initiate(...)` with the transcript.
- Wired into `TelegramDispatchService` voice branch (replacing the silent drop).

**`TelegramBotClient`** — two new methods.
- `Optional<File> getFile(String fileId)` — `getFile` Bot API.
- `byte[] downloadFile(String filePath)` — fetch from Telegram CDN URL.

### Auth model preserved

- HTTP path: `@RequireAuth` PASETO unchanged.
- Telegram path: `TelegramIdentityResolver` already maps Telegram chat → `tacticlUserId`; that resolved id is passed straight into `AgentCommand`. No internal token minting.

## Trade-offs considered

- **Telegram → HTTP self-call.** Rejected: same-process HTTP, internal auth gymnastics, no value over a direct service call.
- **Whisper in cidadel.** Right long-term home, but cross-repo change blocks this work and adds a release cycle. Local module + clean interface is a one-line swap later.
- **Extend `/v1/agent/command` to multipart instead of new `/voice`.** Multipart-or-JSON branching on one endpoint is messier than two endpoints; clients send what they have.

## Risk

- `AgentCommandService` extraction touches `AgentController` (used by mobile + web) — covered by full TDD on the service plus updated controller test.
- Telegram `/spark` behavior changes: classified type instead of forced `CODE`. This is a behavioral upgrade, but mention in commit message and runbook.
- No back-pressure on Whisper rate limits — initial cut treats failures as "couldn't transcribe, try text" reply; rate-limit handling is YAGNI until needed.

## Out of scope (this spec)

- Task 35 (artifact `sendDocument`) — deferred until artifact data model decided.
- Mobile audio upload — server endpoint added, mobile migration is a separate spec.
- Multimodal image/video.
