"""Voice Activity Detection (VAD) backends.

Two implementations behind a tiny common interface:
  - SileroVad  (default; torch.hub 'snakers4/silero-vad')
  - WebrtcVad  (lightweight; `webrtcvad` package)

Both operate on fixed-size frames of 16 kHz mono int16 PCM and answer a single
question: "is there speech in this frame?". The session loop in stt.py layers
start/silence hysteresis on top of that.

Models are imported and loaded lazily (on first `is_speech` call) so the
service boots even when no VAD package is installed; a clear RuntimeError is
raised only if VAD is actually exercised without its dependency.
"""

from __future__ import annotations

from typing import Optional

import numpy as np

from . import config

# Silero consumes 512-sample windows at 16 kHz (32 ms). webrtcvad accepts
# 10/20/30 ms frames; we use 20 ms (320 samples) so both share one cadence
# closely enough for the session buffer.
SILERO_FRAME_SAMPLES = 512
WEBRTC_FRAME_SAMPLES = 320  # 20 ms @ 16 kHz


class BaseVad:
    frame_samples: int = SILERO_FRAME_SAMPLES

    def is_speech(self, frame_int16: np.ndarray) -> bool:  # pragma: no cover - iface
        raise NotImplementedError

    def reset(self) -> None:
        """Reset any internal streaming state between utterances."""


class SileroVad(BaseVad):
    frame_samples = SILERO_FRAME_SAMPLES

    def __init__(self, threshold: float = config.SILERO_THRESHOLD):
        self.threshold = threshold
        self._model = None
        self._torch = None

    def _ensure(self) -> None:
        if self._model is not None:
            return
        try:
            import torch  # type: ignore
        except Exception as exc:  # pragma: no cover - env dependent
            raise RuntimeError(
                "silero VAD requires torch — install torch or set VAD_ENGINE=webrtc"
            ) from exc
        model, _ = torch.hub.load(
            repo_or_dir="snakers4/silero-vad",
            model="silero_vad",
            trust_repo=True,
            onnx=False,
        )
        self._torch = torch
        self._model = model

    def reset(self) -> None:
        # silero_vad keeps internal LSTM state; reset it between utterances.
        if self._model is not None and hasattr(self._model, "reset_states"):
            self._model.reset_states()

    def is_speech(self, frame_int16: np.ndarray) -> bool:
        self._ensure()
        torch = self._torch
        # Pad/trim to the exact window silero expects.
        f = frame_int16
        if f.shape[0] < self.frame_samples:
            f = np.pad(f, (0, self.frame_samples - f.shape[0]))
        elif f.shape[0] > self.frame_samples:
            f = f[: self.frame_samples]
        audio = torch.from_numpy((f.astype(np.float32)) / 32768.0)
        with torch.no_grad():
            prob = float(self._model(audio, config.SAMPLE_RATE).item())
        return prob >= self.threshold


class WebrtcVad(BaseVad):
    frame_samples = WEBRTC_FRAME_SAMPLES

    def __init__(self, aggressiveness: int = config.WEBRTC_VAD_AGGRESSIVENESS):
        self.aggressiveness = aggressiveness
        self._vad = None

    def _ensure(self) -> None:
        if self._vad is not None:
            return
        try:
            import webrtcvad  # type: ignore
        except Exception as exc:  # pragma: no cover - env dependent
            raise RuntimeError(
                "webrtc VAD requires the 'webrtcvad' package — install it or "
                "set VAD_ENGINE=silero"
            ) from exc
        self._vad = webrtcvad.Vad(self.aggressiveness)

    def is_speech(self, frame_int16: np.ndarray) -> bool:
        self._ensure()
        f = frame_int16
        if f.shape[0] < self.frame_samples:
            f = np.pad(f, (0, self.frame_samples - f.shape[0]))
        elif f.shape[0] > self.frame_samples:
            f = f[: self.frame_samples]
        return self._vad.is_speech(f.astype("<i2").tobytes(), config.SAMPLE_RATE)


def make_vad(engine: Optional[str] = None) -> BaseVad:
    name = (engine or config.VAD_ENGINE or "silero").lower()
    if name == "webrtc":
        return WebrtcVad()
    return SileroVad()
