package io.tacticl.business.voice;

import io.tacticl.client.elevenlabs.client.ElevenLabsClient;
import io.tacticl.client.elevenlabs.client.ElevenLabsSession;
import io.tacticl.client.elevenlabs.client.ElevenLabsSessionConfig;
import io.tacticl.client.elevenlabs.dto.AudioChunk;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-session bridge over short-lived {@link ElevenLabsSession}s. Each call to
 * {@link #speak(String)} opens a fresh streaming-TTS session (the Turbo
 * handshake is sub-100 ms, so one session per utterance is the documented
 * pattern), pumps the narration text in, and forwards every audio chunk's raw
 * bytes to {@link #onAudioChunk}. {@link #onDone} fires once the utterance's
 * final chunk has been emitted.
 *
 * <p>{@link #stop()} is the barge-in primitive: it closes the in-flight session
 * non-blocking (fire-and-forget end frame + socket abort) and returns
 * immediately so the WS handler can flip straight back to listening.
 *
 * <p>The DOWN audio codec is fixed to raw 16 kHz mono PCM16 to match the mic
 * format the browser already plays (the protocol's canonical assumption), which
 * lets the transport skip the {@code audio_format} negotiation frame entirely.
 */
public class ElevenLabsTtsBridge {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsTtsBridge.class);

    /**
     * Canonical DOWN format — matches the mic's 16 kHz mono PCM16 so the browser
     * streams it raw (see {@code tacticl-web/src/voice/protocol.ts}). Overriding
     * the client's mp3 default avoids a codec-negotiation frame.
     */
    public static final String PCM_16K = "pcm_16000";

    private final ElevenLabsClient client;

    private final String voiceId;

    private volatile ElevenLabsSession session;

    private volatile Consumer<byte[]> onAudioChunk = b -> {
    };

    private volatile Runnable onDone = () -> {
    };

    private volatile Consumer<Throwable> onError = e -> {
    };

    public ElevenLabsTtsBridge(ElevenLabsClient client) {
        this(client, null);
    }

    /**
     * @param voiceId optional explicit ElevenLabs voice id; {@code null} uses the
     *                client's configured default voice.
     */
    public ElevenLabsTtsBridge(ElevenLabsClient client, String voiceId) {
        this.client = client;
        this.voiceId = voiceId;
    }

    /** Register the per-chunk audio handler (raw PCM16 bytes → BINARY DOWN frame). */
    public ElevenLabsTtsBridge onAudioChunk(Consumer<byte[]> handler) {
        this.onAudioChunk = handler != null ? handler : b -> {
        };
        return this;
    }

    /** Register the end-of-utterance handler. */
    public ElevenLabsTtsBridge onDone(Runnable handler) {
        this.onDone = handler != null ? handler : () -> {
        };
        return this;
    }

    /** Register the error handler. */
    public ElevenLabsTtsBridge onError(Consumer<Throwable> handler) {
        this.onError = handler != null ? handler : e -> {
        };
        return this;
    }

    /**
     * Synthesize one utterance. Stops any in-flight utterance first (a new turn
     * always supersedes the previous narration), opens a fresh session, sends the
     * text, and flushes so the sentence finishes audibly. Open failures route to
     * {@code onError} rather than throwing.
     */
    public synchronized void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        stop();
        try {
            ElevenLabsSession s = client.open(sessionConfig());
            s.onAudioChunk(this::dispatchChunk);
            s.onDone(() -> dispatchDone(s));
            s.onError(this::dispatchError);
            this.session = s;
            s.sendTextChunk(text);
            s.flush();
            log.debug("ElevenLabs TTS utterance started ({} chars)", text.length());
        } catch (Exception e) {
            log.warn("ElevenLabs TTS speak failed: {}", e.toString());
            dispatchError(e);
        }
    }

    /** True when an utterance is currently streaming. */
    public boolean isSpeaking() {
        ElevenLabsSession s = this.session;
        return s != null && s.isOpen();
    }

    /**
     * Barge-in / stop. Non-blocking: aborts the current session and returns
     * immediately. Safe to call when nothing is playing.
     */
    public synchronized void stop() {
        ElevenLabsSession s = this.session;
        this.session = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception e) {
                log.debug("ElevenLabs TTS stop raced: {}", e.toString());
            }
        }
    }

    private ElevenLabsSessionConfig sessionConfig() {
        ElevenLabsSessionConfig defaults = ElevenLabsSessionConfig.defaults();
        return new ElevenLabsSessionConfig(
            voiceId,
            defaults.stability(),
            defaults.similarityBoost(),
            defaults.style(),
            PCM_16K);
    }

    private void dispatchChunk(AudioChunk chunk) {
        if (chunk != null && chunk.data() != null && chunk.data().length > 0) {
            onAudioChunk.accept(chunk.data());
        }
    }

    /**
     * The utterance's audio is fully streamed. Close the streaming-input socket now
     * rather than leaving it open idle — ElevenLabs closes an idle input socket after
     * ~20s with close code 1008 {@code input_timeout_exceeded}, which the client
     * surfaces as a spurious {@code UPSTREAM_ERROR} badge even though the reply played
     * fine. Closing after the final chunk races no in-flight send (all audio is in),
     * so it does not reintroduce the frame-interleave bug that {@link #stop()} avoids.
     */
    private synchronized void dispatchDone(ElevenLabsSession finished) {
        if (finished != null) {
            if (this.session == finished) {
                this.session = null;
            }
            try {
                finished.close();
            } catch (Exception e) {
                log.debug("ElevenLabs TTS close-on-done raced: {}", e.toString());
            }
        }
        onDone.run();
    }

    private void dispatchError(Throwable t) {
        onError.accept(t);
    }
}
