package io.tacticl.business.voice;

import java.util.function.Consumer;

/**
 * Provider-neutral streaming speech-to-text leg for one voice session. Owns the
 * mic → transcript half of a turn: {@link #open()} the upstream session, forward
 * raw 16 kHz mono s16le PCM mic chunks straight through with {@link #sendAudio},
 * and surface partial/final transcript text (and VAD start) as plain callbacks the
 * {@link VoiceSessionService} consumes — callers never touch any provider DTOs.
 *
 * <p>Implementations: {@link DeepgramSttBridge} (managed STT) and
 * {@link LocalSttBridge} (local sidecar over WebSocket). The active implementation
 * is chosen by {@code tacticl.voice.stt-provider} via the factory selected in
 * {@link BusinessVoiceConfig}; {@link VoiceSessionService} injects this interface,
 * never a concrete bridge.
 *
 * <p>Not thread-safe for concurrent {@link #open}/{@link #close}; audio pushes are
 * forwarded as-is and the underlying WebSocket serializes them. The callback
 * registrars return {@code this} for fluent chaining.
 */
public interface SttBridge {

    /**
     * Open the STT session (idempotent — a no-op if already open). Wires the
     * upstream callbacks to the registered handlers. Any open failure is routed to
     * {@code onError} rather than thrown, so a failed turn degrades to silence.
     */
    void open();

    /**
     * Forward a 16 kHz mono s16le PCM chunk upstream. Silently ignored if the
     * session is not open (e.g. a stray binary frame after barge-in).
     */
    void sendAudio(byte[] pcm);

    /** True when the underlying STT session is open and accepting frames. */
    boolean isOpen();

    /** Graceful close; safe to call repeatedly. */
    void close();

    /** Register the interim-transcript handler (partial hypotheses). */
    SttBridge onPartial(Consumer<String> handler);

    /**
     * Register the final-transcript handler — the trigger for ingress dispatch.
     * Fired once per finalized utterance.
     */
    SttBridge onFinal(Consumer<String> handler);

    /** Register the VAD start-of-speech handler (drives the listening animation). */
    SttBridge onSpeechStarted(Runnable handler);

    /** Register the terminal-error handler. */
    SttBridge onError(Consumer<Throwable> handler);
}
