"""FastAPI entry point for the Tacticl voice sidecar.

Endpoints
---------
GET  /healthz   -> {"ok": true, "stt": "<model>", "tts": "<engine>"}
WS   /v1/stt    -> streaming speech-to-text (see protocol below)
WS   /v1/tts    -> one-utterance streaming text-to-speech

WS PROTOCOL (must match the Java client exactly)
------------------------------------------------
Transport: WebSocket. Audio is ALWAYS raw 16 kHz mono s16le PCM.

STT  (/v1/stt)
  client -> server: optional first JSON text frame
                    {"sample_rate":16000,"encoding":"pcm_s16le","channels":1};
                    then continuous BINARY frames = raw 16 kHz mono s16le PCM.
  server -> client: JSON text frames, one per event:
     {"type":"speech_started"}
     {"type":"partial","text":"<interim>"}
     {"type":"final","text":"<utterance>"}
     {"type":"error","message":"..."}

TTS  (/v1/tts)  — ONE utterance per connection
  client -> server: single JSON text frame {"text":"...","voice":"<optional>"}
  server -> client: BINARY frames = raw 16 kHz mono s16le PCM chunks; then a
                    JSON text frame {"type":"done"}; or {"type":"error","message":"..."}

This module is intentionally import-light at top level so the server boots and
/healthz responds even before any model is downloaded/loaded. Models load
lazily on first WS use (or via best-effort warmup inside the handler).
"""

from __future__ import annotations

import json
import logging

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from starlette.websockets import WebSocketState

from . import config, stt as stt_mod, tts as tts_mod

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [voice-sidecar] %(message)s",
)
log = logging.getLogger("voice-sidecar")

app = FastAPI(title="Tacticl Voice Sidecar", version="0.1.0")


@app.on_event("startup")
async def _startup() -> None:
    log.info("starting voice sidecar: %s", json.dumps(config.summary()))
    log.info(
        "models load lazily on first WS use "
        "(STT_ENGINE=%s, TTS_ENGINE=%s, MODEL_CACHE_DIR=%s)",
        config.STT_ENGINE,
        config.TTS_ENGINE,
        config.MODEL_CACHE_DIR,
    )


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------
@app.get("/healthz")
async def healthz() -> JSONResponse:
    return JSONResponse(
        {
            "ok": True,
            "stt": _stt_descriptor(),
            "tts": tts_mod.active_engine_name(),
        }
    )


def _stt_descriptor() -> str:
    """Human-readable STT identity for health (engine + concrete model name)."""
    engine = stt_mod.active_engine_name()
    if engine == "parakeet":
        return config.STT_PARAKEET_MODEL
    if engine == "faster_whisper":
        return f"faster_whisper:{config.STT_WHISPER_MODEL}"
    return engine


# ---------------------------------------------------------------------------
# STT WebSocket
# ---------------------------------------------------------------------------
@app.websocket("/v1/stt")
async def ws_stt(ws: WebSocket) -> None:
    await ws.accept()

    async def emit(event: dict) -> None:
        if ws.application_state == WebSocketState.CONNECTED:
            await ws.send_text(json.dumps(event))

    try:
        transcriber = stt_mod.make_transcriber()
    except Exception as exc:
        await emit({"type": "error", "message": f"stt init failed: {exc}"})
        await ws.close()
        return

    session = stt_mod.SttSession(transcriber, emit)

    try:
        while True:
            message = await ws.receive()
            mtype = message.get("type")

            if mtype == "websocket.disconnect":
                break

            # Binary audio frame — the common case.
            if message.get("bytes") is not None:
                await session.feed(message["bytes"])
                continue

            # Optional leading JSON config frame. We accept and (politely)
            # ignore it: the wire format is fixed at 16 kHz mono s16le.
            text = message.get("text")
            if text is not None:
                _handle_stt_text_frame(text)
                continue

    except WebSocketDisconnect:
        pass
    except Exception as exc:  # pragma: no cover - defensive
        log.exception("stt session error")
        await emit({"type": "error", "message": str(exc)})
    finally:
        # Finalize any in-flight utterance the client didn't get to end.
        try:
            await session.flush()
        except Exception:
            pass
        session.close()
        await _safe_close(ws)


def _handle_stt_text_frame(text: str) -> None:
    """Validate the optional STT config frame; log mismatches, never fail."""
    try:
        cfg = json.loads(text)
    except Exception:
        return
    sr = cfg.get("sample_rate")
    if sr is not None and int(sr) != config.SAMPLE_RATE:
        log.warning(
            "client announced sample_rate=%s but wire is fixed at %s — ignoring",
            sr,
            config.SAMPLE_RATE,
        )


# ---------------------------------------------------------------------------
# TTS WebSocket  (one utterance per connection)
# ---------------------------------------------------------------------------
@app.websocket("/v1/tts")
async def ws_tts(ws: WebSocket) -> None:
    await ws.accept()

    async def emit_json(event: dict) -> None:
        if ws.application_state == WebSocketState.CONNECTED:
            await ws.send_text(json.dumps(event))

    async def emit_bytes(data: bytes) -> None:
        if ws.application_state == WebSocketState.CONNECTED:
            await ws.send_bytes(data)

    try:
        # First (and only) inbound message must be the JSON request frame.
        raw = await ws.receive_text()
    except WebSocketDisconnect:
        return
    except Exception as exc:
        await emit_json({"type": "error", "message": f"expected JSON text frame: {exc}"})
        await _safe_close(ws)
        return

    try:
        req = json.loads(raw)
    except Exception as exc:
        await emit_json({"type": "error", "message": f"invalid JSON request: {exc}"})
        await _safe_close(ws)
        return

    text = (req.get("text") or "").strip()
    voice = req.get("voice")
    if not text:
        await emit_json({"type": "error", "message": "missing 'text'"})
        await _safe_close(ws)
        return

    try:
        synthesizer = tts_mod.make_synthesizer()
    except Exception as exc:
        await emit_json({"type": "error", "message": f"tts init failed: {exc}"})
        await _safe_close(ws)
        return

    session = tts_mod.TtsSession(synthesizer, emit_bytes)
    try:
        await session.speak(text, voice)
        await emit_json({"type": "done"})
    except WebSocketDisconnect:
        pass
    except Exception as exc:
        log.exception("tts synthesis error")
        await emit_json({"type": "error", "message": f"tts failed: {exc}"})
    finally:
        await _safe_close(ws)


async def _safe_close(ws: WebSocket) -> None:
    try:
        if ws.application_state == WebSocketState.CONNECTED:
            await ws.close()
    except Exception:
        pass


def run() -> None:
    """Console entry point: `python -m app.main` / module run."""
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=config.HOST,
        port=config.PORT,
        log_level="info",
    )


if __name__ == "__main__":
    run()
