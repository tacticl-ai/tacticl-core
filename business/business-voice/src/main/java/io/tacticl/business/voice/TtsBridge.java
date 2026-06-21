package io.tacticl.business.voice;

import java.util.function.Consumer;

/**
 * Provider-neutral streaming text-to-speech leg for one voice session. Owns the
 * narration → audio half of a turn: {@link #speak(String)} synthesizes one
 * utterance and forwards every audio chunk's raw 16 kHz mono s16le PCM16 bytes to
 * {@link #onAudioChunk}; {@link #onDone} fires once the utterance's final chunk has
 * been emitted. {@link #stop()} is the barge-in primitive.
 *
 * <p>Implementations: {@link ElevenLabsTtsBridge} (managed TTS) and
 * {@link LocalTtsBridge} (local sidecar over WebSocket). The active implementation
 * is chosen by {@code tacticl.voice.tts-provider} via the factory selected in
 * {@link BusinessVoiceConfig}; {@link VoiceSessionService} injects this interface,
 * never a concrete bridge.
 *
 * <p>The DOWN audio codec is fixed to raw 16 kHz mono PCM16 to match the mic format
 * the browser already plays (the protocol's canonical assumption). The callback
 * registrars return {@code this} for fluent chaining.
 */
public interface TtsBridge {

    /**
     * Synthesize one utterance. Stops any in-flight utterance first (a new turn
     * always supersedes the previous narration). Open failures route to
     * {@code onError} rather than throwing. No-op on blank text.
     */
    void speak(String text);

    /**
     * Barge-in / stop. Non-blocking: aborts the current utterance and returns
     * immediately. Safe to call when nothing is playing.
     */
    void stop();

    /** True when an utterance is currently streaming. */
    boolean isSpeaking();

    /** Register the per-chunk audio handler (raw PCM16 bytes → BINARY DOWN frame). */
    TtsBridge onAudioChunk(Consumer<byte[]> handler);

    /** Register the end-of-utterance handler. */
    TtsBridge onDone(Runnable handler);

    /** Register the error handler. */
    TtsBridge onError(Consumer<Throwable> handler);
}
