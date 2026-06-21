# Local Voice (STT/TTS) — Spec & Runbook

Status: design + runbook. Owner: tacticl-core voice plane.
Related code: `business/business-voice` (the bridge seam), `voice-sidecar/` (Python service).

## 1. Motivation

The voice plane today bills per use: Deepgram for streaming STT and ElevenLabs for
streaming TTS. Two problems:

- **Cost.** Every spoken turn hits two metered cloud APIs. At any real session
  volume this is a recurring per-minute charge with no fixed ceiling.
- **Key gating.** Voice is hard-blocked without valid Deepgram + ElevenLabs keys.
  When cloud credits lapse (see the repeated Anthropic-credit incidents in MEMORY),
  voice dies entirely.

**Local voice** runs both legs on open-weight models in a CPU-only Python sidecar
on the existing platform host. It drops the per-use STT/TTS bill to zero and lets
voice run with **no cloud voice keys at all**. It plugs in behind the *same* bridge
seam tacticl-core already uses, so the browser path and the WS transport are
untouched — it is a provider swap selected by config.

## 2. Architecture

```
browser mic ──16kHz PCM──┐                          ┌── STT provider = deepgram ── Deepgram cloud WS
                         │                          │   (DeepgramSttBridge)
   tacticl-web  ◄──WS───►│  tacticl-core            │
   (/v1/voice, unchanged)│  business-voice          ├── STT provider = local ──┐
                         │   VoiceSessionService    │   (LocalSttBridge)       │   ws://voice-sidecar:8700
                         │     ├─ Stt*BridgeFactory  │                          ├──►  Python sidecar
   browser playback ◄────┘     └─ Tts*BridgeFactory  ├── TTS provider = local ──┘   Parakeet STT
       16kHz PCM                                     │   (LocalTtsBridge)            Kokoro/XTTS TTS
                                                     └── TTS provider = elevenlabs ─ ElevenLabs cloud WS
                                                         (ElevenLabsTtsBridge)
```

**The seam.** `VoiceSessionService` never talks to a vendor directly. Per voice
session it asks a factory for a bridge:

- STT bridge — `open()`, `sendAudio(byte[] pcm)`, and callbacks `onSpeechStarted`,
  `onPartial(text)`, `onFinal(text)` (the **final** callback is the dispatch
  trigger), `onError`, `close()`.
- TTS bridge — `speak(text)`, callbacks `onAudioChunk(byte[] pcm)`, `onDone`,
  `onError`, plus `stop()` for barge-in.

`DeepgramSttBridge` and `ElevenLabsTtsBridge` are the existing cloud
implementations. Local voice adds two siblings — `LocalSttBridge` /
`LocalTtsBridge` — that implement the identical surface but speak the WS protocol
in §3 to the Python sidecar instead of to a vendor. The factories
(`*SttBridgeFactory` / `*TtsBridgeFactory`, today gated only by
`tacticl.voice.enabled=true`) choose which implementation to build from the new
provider config in §4.

**What does NOT change:**

- The browser ↔ tacticl-core WebSocket (`tacticl.voice.ws-path=/v1/voice`),
  including its frame format and the voice sphere UI.
- Audio is **always raw 16 kHz mono s16le PCM** end to end — the same format the
  browser mic produces and the browser playback consumes. The cloud TTS bridge
  already pins this (`ElevenLabsTtsBridge.PCM_16K = "pcm_16000"`); the sidecar
  produces the same, so there is no codec negotiation anywhere in the path.
- `VoiceSessionService`, the session registry, the conversation engine, and the
  turn handler — all provider-agnostic, all untouched.

**The sidecar** (`voice-sidecar/`, Python) is a single WebSocket service:

- **STT: NVIDIA Parakeet (0.6B)** — streaming ASR, emits VAD start + interim +
  final transcripts. CPU-real-time at this size.
- **TTS: Kokoro (82M)** by default — fast, light, streams PCM. XTTS-v2 is a
  swappable alternative engine (see licensing, §7 — non-commercial, dev only).

## 3. WS Protocol (normative)

> LOCAL VOICE SIDECAR — WS PROTOCOL (both the Java client and the Python server MUST implement this EXACTLY).
> Transport: WebSocket. Audio is ALWAYS raw 16 kHz mono s16le PCM (matches the browser mic + playback — NO codec negotiation).
>
> STT  —  path  /v1/stt
>   client → server: optionally a first JSON text frame {"sample_rate":16000,"encoding":"pcm_s16le","channels":1};
>                    then continuous BINARY frames = raw 16 kHz mono s16le PCM mic chunks.
>   server → client: JSON text frames, one per event:
>      {"type":"speech_started"}                  // VAD detected start of speech
>      {"type":"partial","text":"<interim>"}      // interim hypothesis (may repeat/grow)
>      {"type":"final","text":"<utterance>"}      // finalized utterance — this is the dispatch trigger
>      {"type":"error","message":"..."}
>   Session ends when either side closes the socket.
>
> TTS  —  path  /v1/tts   (ONE utterance per connection, mirroring ElevenLabs' per-utterance session)
>   client → server: a single JSON text frame {"text":"<text to speak>","voice":"<optional voice id>"}
>   server → client: BINARY frames = raw 16 kHz mono s16le PCM16 audio chunks, streamed as synthesized;
>                    then a JSON text frame {"type":"done"} once the final chunk is emitted;
>                    {"type":"error","message":"..."} on failure.
>   (If the TTS model natively outputs 24 kHz, the SERVER must resample to 16 kHz mono s16le before sending.)
>
> Health: GET /healthz → 200 {"ok":true,"stt":"<model>","tts":"<engine>"}.

**Mapping to the bridge callbacks** (so the parity is unambiguous):

| Sidecar frame | STT bridge effect |
|---|---|
| `{"type":"speech_started"}` | `onSpeechStarted` |
| `{"type":"partial","text":…}` | `onPartial(text)` |
| `{"type":"final","text":…}` | `onFinal(text)` → ingress dispatch |
| `{"type":"error",…}` | `onError` |

| TTS bridge call | Sidecar exchange |
|---|---|
| `speak(text)` | open `/v1/tts`, send `{"text":…,"voice":…}` |
| `onAudioChunk(pcm)` | each BINARY frame |
| `onDone` | `{"type":"done"}` |
| `stop()` (barge-in) | close the socket |

Kokoro and Parakeet operate at native sample rates internally; the sidecar
resamples to 16 kHz mono s16le before sending so the Java side and browser never
deal with anything else. Parakeet's native 16 kHz input means mic PCM is forwarded
as-is.

## 4. Config switch

New keys under the existing `tacticl.voice` prefix (`VoiceProperties`):

```properties
tacticl.voice.enabled=true

# Provider selection — independent per leg.
tacticl.voice.stt-provider=local         # deepgram | local   (default: deepgram)
tacticl.voice.tts-provider=local         # elevenlabs | local (default: elevenlabs)

# Sidecar base URL (ws://…); STT uses {base}/v1/stt, TTS uses {base}/v1/tts.
tacticl.voice.local-base-url=ws://voice-sidecar:8700
```

- The two providers are chosen **independently** — e.g. local STT + cloud TTS is
  valid. The `*BridgeFactory` for each leg reads its own provider key and builds
  the matching bridge.
- Defaults keep the current cloud behaviour (`deepgram` / `elevenlabs`), so an
  upgrade with no config change is a no-op.
- `local-base-url` defaults to `ws://voice-sidecar:8700` (the compose service name,
  §5). When either provider is `local`, the cloud client for that leg is no longer
  required, so its Vault key (Deepgram / ElevenLabs) is not needed.
- `tacticl.voice.voice-id` still applies: for local TTS it is passed through as the
  optional `voice` field on the `/v1/tts` open frame (Kokoro voice id); blank ⇒
  sidecar default voice.

## 5. Deploy

The sidecar runs as one Docker service, `voice-sidecar`, on the **existing
platform host** alongside the other product containers. **CPU is fine — no GPU box
is required.** Parakeet 0.6B and Kokoro 82M are small enough to run real-time on
CPU; XTTS-v2 is heavier and benefits from a GPU but is dev-only anyway (§7).

`voice-sidecar/Dockerfile` (sketch):

```dockerfile
FROM python:3.12-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
      libsndfile1 ffmpeg && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
EXPOSE 8700
CMD ["python", "-m", "app"]   # serves /v1/stt, /v1/tts, /healthz on :8700
```

`docker-compose` wiring (same network as `tacticl-api`):

```yaml
services:
  voice-sidecar:
    build: ./voice-sidecar
    container_name: voice-sidecar
    restart: unless-stopped
    expose:
      - "8700"                 # internal-only; reached over the compose network
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8700/healthz"]
      interval: 30s
      timeout: 5s
      retries: 3
    # Optional: persist model weights so they download once.
    volumes:
      - voice-models:/root/.cache

  tacticl-api:
    # ...existing config...
    environment:
      TACTICL_VOICE_ENABLED: "true"
      TACTICL_VOICE_STT_PROVIDER: "local"
      TACTICL_VOICE_TTS_PROVIDER: "local"
      TACTICL_VOICE_LOCAL_BASE_URL: "ws://voice-sidecar:8700"
    depends_on:
      voice-sidecar:
        condition: service_healthy

volumes:
  voice-models:
```

Notes:
- `voice-sidecar` is **not** published through Caddy — it is reached only over the
  internal compose network by `tacticl-api`. The browser never talks to it.
- Spring relaxed binding maps `TACTICL_VOICE_STT_PROVIDER` →
  `tacticl.voice.stt-provider`, etc., so env vars are enough; no properties file
  edit needed on the host.
- First container start downloads the model weights; pin them into the image or the
  `voice-models` volume to avoid a cold download on every redeploy.
- Smoke after deploy: `curl http://voice-sidecar:8700/healthz` from inside the
  `tacticl-api` container → `{"ok":true,"stt":"parakeet-0.6b","tts":"kokoro-82m"}`.

## 6. Rollout posture

Two valid postures; both are config-only flips:

- **Local-primary (cost-first).** Set `stt-provider=local`, `tts-provider=local`.
  Zero per-use voice spend. This is the recommended default once the sidecar is
  verified on the host.
- **Cloud-primary, local fallback.** Keep `deepgram` / `elevenlabs` while cloud
  credits hold quality/latency you want, and flip the affected leg to `local` when
  credits run out or a vendor degrades. Because the legs are independent, you can
  fall back STT and TTS separately.

There is no automatic runtime failover between providers — selection is the config
value at startup. "Fallback" here means an operator flips the env var and restarts
`tacticl-api`. The seam makes that a one-line change with no code deploy.

## 7. Licensing

Model choice is constrained by license — this gates what can ship to production:

- **Kokoro — Apache-2.0. Safe for commercial/production use.** This is the default
  TTS engine for exactly this reason.
- **Parakeet (NVIDIA) — permissive** open model weights; usable for the STT leg.
- **XTTS-v2 — Coqui Public Model License, NON-COMMERCIAL.** Do **not** ship XTTS-v2
  in production. It is allowed only as a local/dev experimentation engine (e.g.
  evaluating voice quality). Any production TTS path must use Kokoro (or another
  Apache/MIT/CC-BY-licensed engine).

When adding a new local engine, record its license here before wiring it as a
selectable `tts-provider`/`stt-provider` value.
