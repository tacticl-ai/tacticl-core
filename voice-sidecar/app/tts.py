"""Streaming text-to-speech.

Two synthesizer backends behind a common `Synthesizer` interface:
  - KokoroSynthesizer  (default; Apache-2.0 — COMMERCIAL-SAFE)
  - XttsSynthesizer     (Coqui XTTS-v2; weights are CPML / NON-COMMERCIAL)

Each yields float32 audio at the model's native sample rate together with that
rate; the WS layer (main.py) resamples to 16 kHz mono s16le before sending, so
the wire format stays fixed regardless of engine.

Both backends are import-lazy so the service boots with only one installed.

LICENSING NOTE
--------------
Kokoro is Apache-2.0 and safe for commercial use — it is the default.
XTTS-v2 weights are released under the Coqui Public Model License (CPML),
which is NON-COMMERCIAL. Only enable TTS_ENGINE=xtts if your usage complies.
"""

from __future__ import annotations

import asyncio
from typing import Awaitable, Callable, Iterable, Optional, Tuple

import numpy as np

from . import audio, config

# Kokoro and XTTS both natively synthesize at 24 kHz.
KOKORO_NATIVE_RATE = 24000
XTTS_NATIVE_RATE = 24000

EmitBytesFn = Callable[[bytes], Awaitable[None]]


class Synthesizer:
    """Synthesizes text into (float32 audio, native_sample_rate) chunks."""

    name: str = "base"
    native_rate: int = 24000

    def load(self) -> None:  # pragma: no cover - iface
        raise NotImplementedError

    def synthesize(self, text: str, voice: Optional[str]) -> Iterable[Tuple[np.ndarray, int]]:
        """Yield (mono float32 in [-1,1], sample_rate) chunks for `text`."""
        raise NotImplementedError  # pragma: no cover - iface


class KokoroSynthesizer(Synthesizer):
    name = "kokoro"
    native_rate = KOKORO_NATIVE_RATE

    def __init__(self, default_voice: str = config.TTS_VOICE, lang: str = config.TTS_LANG):
        self.default_voice = default_voice
        self.lang = lang
        self._pipeline = None

    def load(self) -> None:
        if self._pipeline is not None:
            return
        # `kokoro` (PyTorch) is the reference package. KPipeline streams per
        # sentence/segment, which is exactly what we want for low-latency TTS.
        from kokoro import KPipeline  # type: ignore

        self._pipeline = KPipeline(lang_code=self.lang)

    def synthesize(self, text: str, voice: Optional[str]):
        self.load()
        v = voice or self.default_voice
        # KPipeline yields (graphemes, phonemes, audio) per segment; audio is a
        # torch tensor / numpy array of float32 at 24 kHz.
        for result in self._pipeline(text, voice=v):
            wav = result[2] if isinstance(result, (tuple, list)) else getattr(result, "audio", result)
            arr = _to_numpy_f32(wav)
            if arr.size:
                yield arr, self.native_rate


class XttsSynthesizer(Synthesizer):
    name = "xtts"
    native_rate = XTTS_NATIVE_RATE

    def __init__(
        self,
        speaker_wav: str = config.XTTS_SPEAKER_WAV,
        language: str = config.XTTS_LANGUAGE,
    ):
        self.speaker_wav = speaker_wav
        self.language = language
        self._tts = None

    def load(self) -> None:
        if self._tts is not None:
            return
        # Coqui TTS. NOTE: XTTS-v2 weights are CPML (non-commercial).
        from TTS.api import TTS  # type: ignore

        self._tts = TTS("tts_models/multilingual/multi-dataset/xtts_v2")

    def synthesize(self, text: str, voice: Optional[str]):
        self.load()
        # `voice` may override the reference speaker wav path per-utterance.
        speaker_wav = voice or self.speaker_wav or None
        kwargs = {"text": text, "language": self.language}
        if speaker_wav:
            kwargs["speaker_wav"] = speaker_wav
        wav = self._tts.tts(**kwargs)
        arr = _to_numpy_f32(wav)
        if arr.size:
            yield arr, self.native_rate


def _to_numpy_f32(wav) -> np.ndarray:
    """Coerce a torch tensor / list / ndarray of audio to mono float32."""
    if wav is None:
        return np.zeros(0, dtype=np.float32)
    # torch tensor?
    detach = getattr(wav, "detach", None)
    if callable(detach):
        wav = detach().cpu().numpy()
    arr = np.asarray(wav, dtype=np.float32)
    if arr.ndim == 2:
        arr = arr.mean(axis=1)
    return arr.reshape(-1)


def make_synthesizer(engine: Optional[str] = None) -> Synthesizer:
    name = (engine or config.TTS_ENGINE or "kokoro").lower()
    if name == "xtts":
        return XttsSynthesizer()
    if name == "kokoro":
        return KokoroSynthesizer()
    raise ValueError(f"unknown TTS_ENGINE: {name!r} (expected kokoro|xtts)")


def active_engine_name() -> str:
    return (config.TTS_ENGINE or "kokoro").lower()


# ---------------------------------------------------------------------------
# Streaming session
# ---------------------------------------------------------------------------
class TtsSession:
    """Synthesizes one utterance and streams 16 kHz s16le PCM chunks out.

    Synthesis runs in a thread executor (it's CPU-bound and blocking). Each
    native-rate float32 chunk is resampled to 16 kHz and re-chunked into small
    PCM frames so playback can begin before the whole utterance is done.
    """

    def __init__(self, synthesizer: Synthesizer, emit_bytes: EmitBytesFn):
        self._tts = synthesizer
        self._emit_bytes = emit_bytes

    async def speak(self, text: str, voice: Optional[str]) -> None:
        text = (text or "").strip()
        if not text:
            return
        loop = asyncio.get_running_loop()

        # Run the (blocking) generator in a worker thread, marshalling each
        # produced chunk back to the loop as it's ready.
        queue: "asyncio.Queue[Optional[Tuple[np.ndarray, int]]]" = asyncio.Queue(maxsize=8)

        def producer() -> None:
            try:
                for arr, rate in self._tts.synthesize(text, voice):
                    fut = asyncio.run_coroutine_threadsafe(queue.put((arr, rate)), loop)
                    fut.result()  # backpressure
            finally:
                asyncio.run_coroutine_threadsafe(queue.put(None), loop).result()

        producer_task = loop.run_in_executor(None, producer)

        while True:
            item = await queue.get()
            if item is None:
                break
            arr, rate = item
            await self._emit_chunked_pcm16(arr, rate)

        # Surface any exception raised inside the producer thread.
        await asyncio.wrap_future(producer_task)

    async def _emit_chunked_pcm16(self, arr: np.ndarray, src_rate: int) -> None:
        # Resample native -> 16 kHz mono, then slice into small PCM frames.
        resampled = audio.resample(audio.to_mono(arr), src_rate, config.SAMPLE_RATE)
        chunk = config.TTS_CHUNK_SAMPLES
        total = resampled.shape[0]
        for start in range(0, total, chunk):
            piece = resampled[start : start + chunk]
            pcm = audio.float32_to_pcm16_bytes(piece)
            if pcm:
                await self._emit_bytes(pcm)

    async def warmup(self) -> None:
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(None, self._tts.load)
