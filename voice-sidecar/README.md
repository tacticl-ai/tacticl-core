# Tacticl Voice Sidecar

A self-contained Python microservice that provides **local streaming speech-to-text**
(NVIDIA Parakeet) and **streaming text-to-speech** (Kokoro by default, XTTS-v2
optional), exposed over WebSocket. It is the local/self-hosted equivalent of the
Deepgram (STT) + ElevenLabs (TTS) cloud plane — same wire shape, no cloud.

- **STT**: `nvidia/parakeet-tdt-0.6b-v2` via NeMo (default), or `faster-whisper` fallback.
- **TTS**: Kokoro (Apache-2.0, **commercial-safe**, default), or XTTS-v2 (Coqui,
  **non-commercial** weights, optional).
- **Wire format**: always raw **16 kHz mono s16le PCM** — no codec negotiation.
  Matches the browser mic capture and playback path on the Java client.

> ⚠️ **This service cannot be exercised in the build/CI environment** (no models,
> no GPU here). It must be run on a machine that has the model weights available
> (and ideally a GPU for Parakeet). The code is structured to be **import-lazy**:
> `uvicorn app.main:app` boots and `/healthz` responds **before** any model loads.
> Models download/load on first WebSocket use.

---

## WS protocol

Both the Java client and this Python server implement this **exactly**.

Transport: WebSocket. Audio is **ALWAYS** raw 16 kHz mono s16le PCM.

### STT — `WS /v1/stt`

```
client → server:
  (optional) one JSON text frame: {"sample_rate":16000,"encoding":"pcm_s16le","channels":1}
  then continuous BINARY frames = raw 16 kHz mono s16le PCM mic chunks.

server → client: JSON text frames, one per event:
  {"type":"speech_started"}              // VAD detected start of speech
  {"type":"partial","text":"<interim>"}  // interim hypothesis (may repeat/grow)
  {"type":"final","text":"<utterance>"}  // finalized utterance — dispatch trigger
  {"type":"error","message":"..."}
```

The session ends when either side closes the socket. The optional leading JSON
config frame is accepted but **ignored** — the wire format is fixed.

### TTS — `WS /v1/tts` (one utterance per connection)

Mirrors ElevenLabs' per-utterance session: open socket → send one request → receive
audio → `done` → close.

```
client → server: a single JSON text frame: {"text":"<text to speak>","voice":"<optional voice id>"}

server → client:
  BINARY frames = raw 16 kHz mono s16le PCM16 audio chunks, streamed as synthesized;
  then a JSON text frame {"type":"done"} once the final chunk is emitted;
  {"type":"error","message":"..."} on failure.
```

Kokoro and XTTS-v2 both natively synthesize at **24 kHz**; the **server resamples
to 16 kHz mono s16le** before sending, so the client always receives 16 kHz.

### Health — `GET /healthz`

```
200 {"ok":true,"stt":"<model>","tts":"<engine>"}
```

e.g. `{"ok":true,"stt":"nvidia/parakeet-tdt-0.6b-v2","tts":"kokoro"}`.

---

## Run locally

```bash
cd voice-sidecar
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# Default stack (Parakeet + Kokoro). To enable Parakeet, also install NeMo:
#   pip install "nemo_toolkit[asr]"
# Or run the lighter fallback without NeMo:
#   export STT_ENGINE=faster_whisper

uvicorn app.main:app --host 0.0.0.0 --port 8700
```

Then:

```bash
curl localhost:8700/healthz
# {"ok":true,"stt":"nvidia/parakeet-tdt-0.6b-v2","tts":"kokoro"}
```

`/healthz` responds immediately — models load lazily on first WS connection.

### Engine selection at a glance

| Want | Set |
|------|-----|
| Parakeet STT (default, best quality, GPU-friendly) | `STT_ENGINE=parakeet` + `pip install "nemo_toolkit[asr]"` |
| Lighter CPU STT | `STT_ENGINE=faster_whisper` (faster-whisper is in requirements.txt) |
| Kokoro TTS (default, commercial-safe) | `TTS_ENGINE=kokoro` |
| XTTS-v2 TTS (non-commercial weights) | `TTS_ENGINE=xtts` + `pip install coqui-tts` + set `XTTS_SPEAKER_WAV` |

---

## Environment variables

| Var | Default | Notes |
|-----|---------|-------|
| `PORT` | `8700` | HTTP/WS listen port |
| `HOST` | `0.0.0.0` | bind address |
| `STT_ENGINE` | `parakeet` | `parakeet` \| `faster_whisper` |
| `STT_PARAKEET_MODEL` | `nvidia/parakeet-tdt-0.6b-v2` | NeMo model id |
| `STT_WHISPER_MODEL` | `base.en` | faster-whisper size (`tiny`…`large-v3`) |
| `STT_WHISPER_DEVICE` | `cpu` | `cpu` \| `cuda` |
| `STT_WHISPER_COMPUTE` | `int8` | faster-whisper compute type |
| `VAD_ENGINE` | `silero` | `silero` \| `webrtc` |
| `VAD_SILENCE_MS` | `700` | trailing silence that ends an utterance |
| `PARTIAL_INTERVAL_MS` | `600` | cadence of interim `partial` decodes |
| `SILERO_THRESHOLD` | `0.5` | silero speech probability threshold |
| `WEBRTC_VAD_AGGRESSIVENESS` | `2` | webrtcvad 0..3 |
| `TTS_ENGINE` | `kokoro` | `kokoro` \| `xtts` |
| `TTS_VOICE` | `af_heart` | default voice id (Kokoro voice or XTTS speaker wav) |
| `TTS_LANG` | `a` | Kokoro lang code (`a` = American English) |
| `XTTS_SPEAKER_WAV` | _(empty)_ | reference speaker wav for XTTS |
| `XTTS_LANGUAGE` | `en` | XTTS language |
| `TTS_CHUNK_SAMPLES` | `800` | outbound PCM chunk size (~50 ms @ 16 kHz) |
| `MODEL_CACHE_DIR` | `/models` | where weights download/cache |

---

## Docker

```bash
# Build (CPU image; models download lazily into the /models volume on first use)
docker build -t voice-sidecar:latest ./voice-sidecar

# Run
docker run --rm -p 8700:8700 \
  -e STT_ENGINE=faster_whisper \
  -e TTS_ENGINE=kokoro \
  -v voice-models:/models \
  voice-sidecar:latest
```

The image is CPU-based (`python:3.11-slim`) with a CPU torch wheel. **Parakeet/NeMo
strongly prefer a GPU** — for a GPU host, switch the base image to an NVIDIA CUDA
runtime, install a CUDA torch wheel, and grant the container a GPU (see the GPU
block in `docker-compose.snippet.yml`).

### Compose

See `docker-compose.snippet.yml` — drop the `voice-sidecar` service into
`deployment/docker-compose.yml`. It joins the existing external `platform-net`
network, so `tacticl-api` reaches it at:

- `ws://voice-sidecar:8700/v1/stt`
- `ws://voice-sidecar:8700/v1/tts`

---

## Model licensing

| Model | License | Commercial use |
|-------|---------|----------------|
| Kokoro (default TTS) | **Apache-2.0** | ✅ **Yes** — commercial-safe default |
| XTTS-v2 (optional TTS) | **Coqui Public Model License (CPML)** | ❌ **No** — **non-commercial only** |
| Parakeet (default STT) | NVIDIA model license (see model card) | Check the model card for your use |
| faster-whisper (Whisper) | MIT (code) / Whisper weights MIT | ✅ Generally permissive |

**Default = Kokoro (Apache-2.0) precisely because it is commercial-safe.** Only
switch `TTS_ENGINE=xtts` if your usage complies with the non-commercial CPML on
the XTTS-v2 weights.

---

## Architecture

```
app/
  main.py    FastAPI app: /healthz, WS /v1/stt, WS /v1/tts (protocol wiring)
  config.py  env-driven config (stdlib only — safe to import anywhere)
  audio.py   PCM<->float, mono, resample (soxr → scipy → linear fallback)
  vad.py     VAD backends: silero (default) / webrtc — frame-level is_speech()
  stt.py     Parakeet / faster-whisper transcribers + streaming SttSession
  tts.py     Kokoro / XTTS synthesizers + streaming TtsSession (resample → PCM16)
```

Key design choices:

- **Import-lazy models.** Heavy packages (NeMo, kokoro, coqui-tts, torch) are
  imported only on first model use. The server boots and `/healthz` answers with
  no models present.
- **Fixed wire format.** 16 kHz mono s16le PCM everywhere. The server resamples
  TTS output from the model's native 24 kHz down to 16 kHz before sending.
- **Decode off the event loop.** STT/TTS inference runs in a thread executor so
  the WebSocket stays responsive while a model works.
- **VAD-driven segmentation.** `speech_started` on VAD onset, periodic `partial`
  re-decodes during speech, `final` after `VAD_SILENCE_MS` of trailing silence.
