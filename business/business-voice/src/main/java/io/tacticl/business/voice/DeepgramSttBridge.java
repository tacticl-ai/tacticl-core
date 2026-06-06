package io.tacticl.business.voice;

import io.tacticl.client.deepgram.client.DeepgramClient;
import io.tacticl.client.deepgram.client.DeepgramSession;
import io.tacticl.client.deepgram.dto.DeepgramSessionConfig;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin per-session bridge over a {@link DeepgramSession}. Owns the streaming STT
 * leg for one voice session: open on first use, forward raw 16 kHz mono s16le PCM
 * mic chunks straight through, and surface partial/final transcript text (and VAD
 * start) as plain callbacks the {@link VoiceSessionService} consumes — callers
 * never touch the client DTOs.
 *
 * <p>Not thread-safe for concurrent {@link #open}/{@link #close}; audio pushes
 * ({@link #sendAudio}) are forwarded as-is and the underlying JDK WebSocket
 * serializes them. Reconnect/backoff is intentionally out of scope (a turn that
 * drops surfaces via {@code onError} and the session simply re-opens on the next
 * {@code start}).
 */
public class DeepgramSttBridge {

    private static final Logger log = LoggerFactory.getLogger(DeepgramSttBridge.class);

    private final DeepgramClient client;

    private volatile DeepgramSession session;

    /** Diagnostic counters — first chunk + periodic so we can see audio actually flowing. */
    private final java.util.concurrent.atomic.AtomicLong audioSent = new java.util.concurrent.atomic.AtomicLong();

    private final java.util.concurrent.atomic.AtomicLong audioDropped = new java.util.concurrent.atomic.AtomicLong();

    private volatile Consumer<String> onPartial = t -> {
    };

    private volatile Consumer<String> onFinal = t -> {
    };

    private volatile Runnable onSpeechStarted = () -> {
    };

    private volatile Consumer<Throwable> onError = e -> {
    };

    public DeepgramSttBridge(DeepgramClient client) {
        this.client = client;
    }

    /** Register the interim-transcript handler (partial:true frames). */
    public DeepgramSttBridge onPartial(Consumer<String> handler) {
        this.onPartial = handler != null ? handler : t -> {
        };
        return this;
    }

    /**
     * Register the final-transcript handler — the trigger for ingress dispatch.
     * Fired once per finalized utterance.
     */
    public DeepgramSttBridge onFinal(Consumer<String> handler) {
        this.onFinal = handler != null ? handler : t -> {
        };
        return this;
    }

    /** Register the VAD start-of-speech handler (drives the listening animation). */
    public DeepgramSttBridge onSpeechStarted(Runnable handler) {
        this.onSpeechStarted = handler != null ? handler : () -> {
        };
        return this;
    }

    /** Register the terminal-error handler. */
    public DeepgramSttBridge onError(Consumer<Throwable> handler) {
        this.onError = handler != null ? handler : e -> {
        };
        return this;
    }

    /**
     * Open the STT session (idempotent — a no-op if already open). Wires the
     * client callbacks to the registered handlers. Any open failure is routed to
     * {@code onError} rather than thrown, so a failed turn degrades to silence.
     */
    public synchronized void open() {
        if (session != null && session.isOpen()) {
            return;
        }
        try {
            DeepgramSession s = client.open(DeepgramSessionConfig.defaults());
            s.onPartialTranscript(t -> dispatchPartial(t.text()));
            s.onFinalTranscript(t -> dispatchFinal(t.text()));
            s.onSpeechStarted(this::dispatchSpeechStarted);
            s.onError(this::dispatchError);
            this.session = s;
            audioSent.set(0);
            audioDropped.set(0);
            log.info("Deepgram STT bridge opened (session open={})", s.isOpen());
        } catch (Exception e) {
            log.warn("Deepgram STT bridge open failed: {}", e.toString());
            dispatchError(e);
        }
    }

    /**
     * Forward a 16 kHz mono s16le PCM chunk to Deepgram. Silently ignored if the
     * session is not open (e.g. a stray binary frame after barge-in).
     */
    public void sendAudio(byte[] pcmChunk) {
        DeepgramSession s = this.session;
        if (s == null || !s.isOpen() || pcmChunk == null || pcmChunk.length == 0) {
            if (pcmChunk != null && pcmChunk.length > 0) {
                long d = audioDropped.incrementAndGet();
                if (d == 1 || d % 200 == 0) {
                    log.warn("Dropping mic audio — STT session {} (dropped={})",
                        s == null ? "null" : (s.isOpen() ? "open" : "not-open"), d);
                }
            }
            return;
        }
        try {
            long n = audioSent.incrementAndGet();
            if (n == 1 || n % 200 == 0) {
                log.info("Mic audio → Deepgram chunk #{} ({} bytes)", n, pcmChunk.length);
            }
            s.sendAudio(pcmChunk);
        } catch (Exception e) {
            dispatchError(e);
        }
    }

    /** True when the underlying STT session is open and accepting frames. */
    public boolean isOpen() {
        DeepgramSession s = this.session;
        return s != null && s.isOpen();
    }

    /** Graceful close; safe to call repeatedly. */
    public synchronized void close() {
        DeepgramSession s = this.session;
        this.session = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception e) {
                log.debug("Deepgram STT bridge close raced: {}", e.toString());
            }
        }
    }

    private void dispatchPartial(String text) {
        if (text != null && !text.isBlank()) {
            log.info("Deepgram partial: '{}'", text);
            onPartial.accept(text);
        }
    }

    private void dispatchFinal(String text) {
        if (text != null && !text.isBlank()) {
            log.info("Deepgram FINAL transcript: '{}'", text);
            onFinal.accept(text);
        }
    }

    private void dispatchSpeechStarted() {
        onSpeechStarted.run();
    }

    private void dispatchError(Throwable t) {
        onError.accept(t);
    }
}
