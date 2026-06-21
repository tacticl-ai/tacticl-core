# ---------------------------------------------------------------------------
# Tacticl voice sidecar — CPU image
# ---------------------------------------------------------------------------
# Default engines: Parakeet (STT) + Kokoro (TTS). Models are NOT baked in;
# they download lazily to MODEL_CACHE_DIR on first WS use. Mount a volume at
# /models in production so downloads persist across restarts.
#
# This is a CPU base. Parakeet/NeMo run far better on GPU — for a GPU host,
# switch the base image to an NVIDIA CUDA runtime and install a CUDA torch wheel.
# ---------------------------------------------------------------------------
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8700 \
    STT_ENGINE=parakeet \
    TTS_ENGINE=kokoro \
    TTS_VOICE=af_heart \
    MODEL_CACHE_DIR=/models \
    HF_HOME=/models/hf \
    TORCH_HOME=/models/torch \
    NEMO_CACHE_DIR=/models/nemo

# System deps:
#   - libsndfile1 : soundfile (TTS audio I/O)
#   - ffmpeg      : faster-whisper / audio decoding
#   - espeak-ng   : phonemizer backend used by Kokoro/Coqui
#   - git         : some model hubs fetch via git
RUN apt-get update && apt-get install -y --no-install-recommends \
        libsndfile1 \
        ffmpeg \
        espeak-ng \
        git \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install the CPU torch wheel first (smaller, avoids pulling CUDA), then deps.
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu \
        "torch>=2.2,<3.0"

COPY requirements.txt /app/requirements.txt
# torch is already installed above; let the rest resolve against it.
RUN pip install --no-cache-dir -r /app/requirements.txt

# To bake Parakeet/XTTS in, uncomment the engine lines in requirements.txt
# (or add an explicit `pip install nemo_toolkit[asr]` / `coqui-tts` here).

COPY app /app/app

RUN mkdir -p /models
VOLUME ["/models"]

EXPOSE 8700

# Healthcheck hits /healthz — responds even before models load.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/healthz" || exit 1

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT}"]
