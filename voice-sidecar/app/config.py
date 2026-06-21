"""Environment-driven configuration for the voice sidecar.

Everything is read once at import time. Keep this module dependency-free
(stdlib only) so it can be imported before any heavy model package is present.
"""

from __future__ import annotations

import os


def _env(name: str, default: str) -> str:
    val = os.environ.get(name)
    return val if val is not None and val != "" else default


# --- Server ---------------------------------------------------------------
PORT: int = int(_env("PORT", "8700"))
HOST: str = _env("HOST", "0.0.0.0")

# --- Audio (fixed wire format — see WS protocol) --------------------------
# The wire is ALWAYS raw 16 kHz mono s16le PCM. Do not make this negotiable.
SAMPLE_RATE: int = 16000
CHANNELS: int = 1
SAMPLE_WIDTH_BYTES: int = 2  # s16le

# --- STT ------------------------------------------------------------------
# parakeet | faster_whisper
STT_ENGINE: str = _env("STT_ENGINE", "parakeet").lower()
STT_PARAKEET_MODEL: str = _env("STT_PARAKEET_MODEL", "nvidia/parakeet-tdt-0.6b-v2")
# faster-whisper fallback model size: tiny|base|small|medium|large-v3
STT_WHISPER_MODEL: str = _env("STT_WHISPER_MODEL", "base.en")
STT_WHISPER_DEVICE: str = _env("STT_WHISPER_DEVICE", "cpu")
STT_WHISPER_COMPUTE: str = _env("STT_WHISPER_COMPUTE", "int8")

# VAD: silero | webrtc
VAD_ENGINE: str = _env("VAD_ENGINE", "silero").lower()
# Speech is considered ended after this much trailing silence.
VAD_SILENCE_MS: int = int(_env("VAD_SILENCE_MS", "700"))
# How often to attempt an interim ("partial") decode while speech is ongoing.
PARTIAL_INTERVAL_MS: int = int(_env("PARTIAL_INTERVAL_MS", "600"))
# webrtcvad aggressiveness 0..3 (only used when VAD_ENGINE=webrtc)
WEBRTC_VAD_AGGRESSIVENESS: int = int(_env("WEBRTC_VAD_AGGRESSIVENESS", "2"))
# silero speech probability threshold 0..1
SILERO_THRESHOLD: float = float(_env("SILERO_THRESHOLD", "0.5"))

# --- TTS ------------------------------------------------------------------
# kokoro | xtts
TTS_ENGINE: str = _env("TTS_ENGINE", "kokoro").lower()
# Default voice id. Kokoro voices look like "af_heart", "am_michael", etc.
TTS_VOICE: str = _env("TTS_VOICE", "af_heart")
TTS_LANG: str = _env("TTS_LANG", "a")  # kokoro lang code: 'a' = American English
# XTTS speaker wav (path inside the container) — optional, non-commercial weights.
XTTS_SPEAKER_WAV: str = _env("XTTS_SPEAKER_WAV", "")
XTTS_LANGUAGE: str = _env("XTTS_LANGUAGE", "en")
# Size of each outbound PCM chunk in samples (~50ms at 16 kHz).
TTS_CHUNK_SAMPLES: int = int(_env("TTS_CHUNK_SAMPLES", "800"))

# --- Models ---------------------------------------------------------------
MODEL_CACHE_DIR: str = _env("MODEL_CACHE_DIR", "/models")


def summary() -> dict:
    """A small, log/health-friendly snapshot of the active config."""
    return {
        "port": PORT,
        "sample_rate": SAMPLE_RATE,
        "stt_engine": STT_ENGINE,
        "tts_engine": TTS_ENGINE,
        "tts_voice": TTS_VOICE,
        "vad_engine": VAD_ENGINE,
        "model_cache_dir": MODEL_CACHE_DIR,
    }
