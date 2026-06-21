"""Audio format helpers: PCM <-> float conversion and resampling.

The wire format for this service is fixed: raw 16 kHz mono s16le PCM.
Models internally may use float32 in [-1, 1] and/or a different native rate
(e.g. Kokoro/XTTS emit 24 kHz). These helpers bridge the two.

Only numpy is imported at module load. The resampler prefers `soxr` (fast,
high quality) and falls back to `scipy.signal.resample_poly`, then to a plain
linear interpolation if neither is installed — so the module imports cleanly
in a minimal environment.
"""

from __future__ import annotations

import numpy as np

# Resampler backends are resolved lazily and cached.
_SOXR = None
_SCIPY_RESAMPLE_POLY = None
_RESAMPLER_RESOLVED = False


def _resolve_resampler() -> None:
    global _SOXR, _SCIPY_RESAMPLE_POLY, _RESAMPLER_RESOLVED
    if _RESAMPLER_RESOLVED:
        return
    try:  # preferred
        import soxr  # type: ignore

        _SOXR = soxr
    except Exception:
        _SOXR = None
    if _SOXR is None:
        try:
            from scipy.signal import resample_poly  # type: ignore

            _SCIPY_RESAMPLE_POLY = resample_poly
        except Exception:
            _SCIPY_RESAMPLE_POLY = None
    _RESAMPLER_RESOLVED = True


def pcm16_bytes_to_float32(data: bytes) -> np.ndarray:
    """Decode little-endian s16 PCM bytes into a float32 array in [-1, 1].

    Tolerates a trailing odd byte (drops it) rather than raising.
    """
    if not data:
        return np.zeros(0, dtype=np.float32)
    if len(data) & 1:
        data = data[:-1]
    pcm = np.frombuffer(data, dtype="<i2").astype(np.float32)
    return pcm / 32768.0


def float32_to_pcm16_bytes(samples: np.ndarray) -> bytes:
    """Encode a float32 array in [-1, 1] to little-endian s16 PCM bytes."""
    if samples is None or len(samples) == 0:
        return b""
    arr = np.asarray(samples, dtype=np.float32)
    arr = np.clip(arr, -1.0, 1.0)
    pcm = (arr * 32767.0).astype("<i2")
    return pcm.tobytes()


def pcm16_bytes_to_int16(data: bytes) -> np.ndarray:
    """Decode s16 PCM bytes into a raw int16 array (no scaling)."""
    if not data:
        return np.zeros(0, dtype=np.int16)
    if len(data) & 1:
        data = data[:-1]
    return np.frombuffer(data, dtype="<i2").copy()


def resample(samples: np.ndarray, src_rate: int, dst_rate: int) -> np.ndarray:
    """Resample a mono float32 signal from src_rate to dst_rate.

    Picks the best available backend (soxr > scipy > linear). Returns float32.
    """
    arr = np.asarray(samples, dtype=np.float32)
    if src_rate == dst_rate or arr.size == 0:
        return arr

    _resolve_resampler()

    if _SOXR is not None:
        return _SOXR.resample(arr, src_rate, dst_rate).astype(np.float32)

    if _SCIPY_RESAMPLE_POLY is not None:
        from math import gcd

        g = gcd(int(src_rate), int(dst_rate))
        up = int(dst_rate) // g
        down = int(src_rate) // g
        return _SCIPY_RESAMPLE_POLY(arr, up, down).astype(np.float32)

    # Last-resort linear interpolation. Lower quality but dependency-free.
    n_out = int(round(arr.size * dst_rate / float(src_rate)))
    if n_out <= 0:
        return np.zeros(0, dtype=np.float32)
    x_old = np.linspace(0.0, 1.0, num=arr.size, endpoint=False)
    x_new = np.linspace(0.0, 1.0, num=n_out, endpoint=False)
    return np.interp(x_new, x_old, arr).astype(np.float32)


def to_mono(samples: np.ndarray) -> np.ndarray:
    """Collapse a (n, channels) or interleaved-flat array to mono float32."""
    arr = np.asarray(samples, dtype=np.float32)
    if arr.ndim == 2:
        return arr.mean(axis=1).astype(np.float32)
    return arr


def resample_to_pcm16(samples: np.ndarray, src_rate: int, dst_rate: int) -> bytes:
    """Convenience: mono float32 -> resampled -> s16le PCM bytes."""
    mono = to_mono(samples)
    out = resample(mono, src_rate, dst_rate)
    return float32_to_pcm16_bytes(out)


def resampler_backend() -> str:
    """Name of the resampler that will actually be used (for diagnostics)."""
    _resolve_resampler()
    if _SOXR is not None:
        return "soxr"
    if _SCIPY_RESAMPLE_POLY is not None:
        return "scipy"
    return "linear"
