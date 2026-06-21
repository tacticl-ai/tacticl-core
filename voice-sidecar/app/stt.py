"""Streaming speech-to-text.

Two transcriber backends behind a common `Transcriber` interface:
  - ParakeetTranscriber     (NVIDIA NeMo 'nvidia/parakeet-tdt-0.6b-v2')
  - FasterWhisperTranscriber (faster-whisper, CPU-friendly fallback)

Both are import-lazy: the heavy package is only imported the first time the
model is actually needed, so `uvicorn app.main:app` boots even if only one
engine's dependencies are installed (or neither).

`SttSession` wires a transcriber to a VAD and runs the chunked-streaming loop
that emits the protocol events: speech_started / partial / final.
"""

from __future__ import annotations

import asyncio
from typing import Awaitable, Callable, Optional

import numpy as np

from . import audio, config
from .vad import BaseVad, make_vad

EmitFn = Callable[[dict], Awaitable[None]]


def _set_inference_mode(model) -> None:
    """Put a torch.nn.Module into inference (eval) mode if it supports it."""
    fn = getattr(model, "eval", None)
    if callable(fn):
        fn()


# ---------------------------------------------------------------------------
# Transcriber backends
# ---------------------------------------------------------------------------
class Transcriber:
    """Decodes a whole buffer of 16 kHz mono float32 audio into text."""

    name: str = "base"

    def load(self) -> None:  # pragma: no cover - iface
        raise NotImplementedError

    def transcribe(self, audio_f32: np.ndarray) -> str:  # pragma: no cover - iface
        raise NotImplementedError


class ParakeetTranscriber(Transcriber):
    name = "parakeet"

    def __init__(self, model_name: str = config.STT_PARAKEET_MODEL):
        self.model_name = model_name
        self._model = None

    def load(self) -> None:
        if self._model is not None:
            return
        # Heavy import — only happens on first use.
        import nemo.collections.asr as nemo_asr  # type: ignore

        model = nemo_asr.models.ASRModel.from_pretrained(model_name=self.model_name)
        _set_inference_mode(model)
        self._model = model

    def transcribe(self, audio_f32: np.ndarray) -> str:
        self.load()
        if audio_f32.size == 0:
            return ""
        # NeMo's transcribe accepts a list of numpy float32 arrays at 16 kHz.
        try:
            results = self._model.transcribe(
                [audio_f32.astype(np.float32)],
                batch_size=1,
                verbose=False,
            )
        except TypeError:
            # Older/newer NeMo signatures may not accept `verbose`.
            results = self._model.transcribe([audio_f32.astype(np.float32)])
        return _coerce_nemo_result(results)


def _coerce_nemo_result(results) -> str:
    """NeMo returns either list[str] or list[Hypothesis] depending on version."""
    if not results:
        return ""
    first = results[0]
    if isinstance(first, str):
        return first.strip()
    text = getattr(first, "text", None)
    if isinstance(text, str):
        return text.strip()
    # Some versions return a tuple (best_hyps, all_hyps).
    if isinstance(results, tuple) and results and results[0]:
        return _coerce_nemo_result(results[0])
    return str(first).strip()


class FasterWhisperTranscriber(Transcriber):
    name = "faster_whisper"

    def __init__(
        self,
        model_size: str = config.STT_WHISPER_MODEL,
        device: str = config.STT_WHISPER_DEVICE,
        compute_type: str = config.STT_WHISPER_COMPUTE,
    ):
        self.model_size = model_size
        self.device = device
        self.compute_type = compute_type
        self._model = None

    def load(self) -> None:
        if self._model is not None:
            return
        from faster_whisper import WhisperModel  # type: ignore

        self._model = WhisperModel(
            self.model_size,
            device=self.device,
            compute_type=self.compute_type,
            download_root=config.MODEL_CACHE_DIR,
        )

    def transcribe(self, audio_f32: np.ndarray) -> str:
        self.load()
        if audio_f32.size == 0:
            return ""
        segments, _info = self._model.transcribe(
            audio_f32.astype(np.float32),
            language="en",
            beam_size=1,
            vad_filter=False,
        )
        return "".join(seg.text for seg in segments).strip()


def make_transcriber(engine: Optional[str] = None) -> Transcriber:
    name = (engine or config.STT_ENGINE or "parakeet").lower()
    if name == "faster_whisper":
        return FasterWhisperTranscriber()
    if name == "parakeet":
        return ParakeetTranscriber()
    raise ValueError(f"unknown STT_ENGINE: {name!r} (expected parakeet|faster_whisper)")


def active_engine_name() -> str:
    return (config.STT_ENGINE or "parakeet").lower()


# ---------------------------------------------------------------------------
# Streaming session
# ---------------------------------------------------------------------------
class SttSession:
    """Drives one STT WebSocket connection.

    Feed raw PCM16 bytes via `feed()`; protocol events are pushed through the
    async `emit` callback. Decoding runs in a thread executor so the event loop
    stays responsive while the model works.
    """

    def __init__(self, transcriber: Transcriber, emit: EmitFn, vad: Optional[BaseVad] = None):
        self._tx = transcriber
        self._emit = emit
        self._vad = vad or make_vad()

        self._frame_samples = self._vad.frame_samples
        self._pcm_tail = np.zeros(0, dtype=np.int16)  # leftover < one VAD frame
        self._utterance = np.zeros(0, dtype=np.float32)  # current speech buffer

        self._in_speech = False
        self._silence_ms = 0.0
        self._frame_ms = 1000.0 * self._frame_samples / config.SAMPLE_RATE
        self._ms_since_partial = 0.0

        self._lock = asyncio.Lock()
        self._closed = False

    async def feed(self, pcm_bytes: bytes) -> None:
        """Process an inbound binary PCM frame."""
        if self._closed or not pcm_bytes:
            return
        async with self._lock:
            incoming = audio.pcm16_bytes_to_int16(pcm_bytes)
            buf = np.concatenate([self._pcm_tail, incoming])
            n_frames = buf.shape[0] // self._frame_samples
            consumed = n_frames * self._frame_samples
            self._pcm_tail = buf[consumed:].copy()

            for i in range(n_frames):
                frame = buf[i * self._frame_samples : (i + 1) * self._frame_samples]
                await self._process_frame(frame)

    async def _process_frame(self, frame_int16: np.ndarray) -> None:
        try:
            speech = self._vad.is_speech(frame_int16)
        except RuntimeError as exc:
            await self._emit({"type": "error", "message": str(exc)})
            self._closed = True
            return

        frame_f32 = frame_int16.astype(np.float32) / 32768.0

        if speech:
            if not self._in_speech:
                self._in_speech = True
                self._silence_ms = 0.0
                self._ms_since_partial = 0.0
                await self._emit({"type": "speech_started"})
            self._utterance = np.concatenate([self._utterance, frame_f32])
            self._silence_ms = 0.0
            self._ms_since_partial += self._frame_ms
            if self._ms_since_partial >= config.PARTIAL_INTERVAL_MS:
                self._ms_since_partial = 0.0
                await self._emit_partial()
        else:
            if self._in_speech:
                # Keep trailing silence in the buffer — it helps the decoder.
                self._utterance = np.concatenate([self._utterance, frame_f32])
                self._silence_ms += self._frame_ms
                if self._silence_ms >= config.VAD_SILENCE_MS:
                    await self._finalize()

    async def _emit_partial(self) -> None:
        text = await self._decode(self._utterance)
        if text:
            await self._emit({"type": "partial", "text": text})

    async def _finalize(self) -> None:
        utt = self._utterance
        # Reset state for the next utterance before the (slow) final decode so
        # incoming audio for the next phrase isn't mixed into this one.
        self._utterance = np.zeros(0, dtype=np.float32)
        self._in_speech = False
        self._silence_ms = 0.0
        self._ms_since_partial = 0.0
        self._vad.reset()

        text = await self._decode(utt)
        # Always emit a final on end-of-utterance — even empty — so the client
        # state machine advances. Callers treat empty final as "nothing heard".
        await self._emit({"type": "final", "text": text})

    async def _decode(self, audio_f32: np.ndarray) -> str:
        if audio_f32.size == 0:
            return ""
        loop = asyncio.get_running_loop()
        try:
            return await loop.run_in_executor(None, self._tx.transcribe, audio_f32)
        except Exception as exc:  # decode failures shouldn't kill the socket
            await self._emit({"type": "error", "message": f"stt decode failed: {exc}"})
            return ""

    async def flush(self) -> None:
        """Finalize any in-flight utterance when the client stops sending."""
        async with self._lock:
            if self._in_speech and self._utterance.size > 0:
                await self._finalize()

    async def warmup(self) -> None:
        """Best-effort model preload so the first utterance isn't cold."""
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(None, self._tx.load)

    def close(self) -> None:
        self._closed = True
