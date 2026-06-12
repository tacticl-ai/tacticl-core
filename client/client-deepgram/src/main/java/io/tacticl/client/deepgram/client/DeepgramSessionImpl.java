package io.tacticl.client.deepgram.client;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.client.deepgram.dto.DeepgramFinalTranscript;
import io.tacticl.client.deepgram.dto.DeepgramPartialTranscript;
import io.tacticl.client.deepgram.dto.WordTiming;
import io.tacticl.client.deepgram.exception.DeepgramErrorDetails;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Package-private {@link DeepgramSession} implementation backed by a JDK
 * {@link WebSocket}. Parsing logic in {@link #handleTextFrame(String)} is
 * exposed package-private so {@link DeepgramClientTest} can drive it without
 * standing up a real WS server.
 *
 * <p>Threading: handler callbacks are invoked on whatever thread Deepgram's
 * WebSocket {@link java.net.http.WebSocket.Listener#onText} runs on (typically
 * an internal HttpClient executor). The bridge in business-voice is responsible
 * for handing off to its own dispatch executor if it needs serial ordering
 * relative to the outbound client WS.
 */
class DeepgramSessionImpl implements DeepgramSession {

    private static final Logger log = LoggerFactory.getLogger(DeepgramSessionImpl.class);

    private static final String MODULE_NAME = "client-deepgram";

    private static final String KEEPALIVE_PAYLOAD = "{\"type\":\"KeepAlive\"}";

    private static final String CLOSE_STREAM_PAYLOAD = "{\"type\":\"CloseStream\"}";

    private static final long KEEPALIVE_INTERVAL_SECONDS = 8L;

    /** How long close() waits for Deepgram to flush finals + close before aborting. */
    private static final long DRAIN_TIMEOUT_MS = 2000L;

    private final JsonMapper objectMapper;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Set once close() starts draining — rejects new audio while late finals stream in. */
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private final StringBuilder textBuffer = new StringBuilder();

    /**
     * Finalized-but-not-speech-final segments of the current utterance
     * ({@code is_final: true, speech_final: false}). Deepgram splits a long or
     * noisy utterance into several such segments and the closing
     * {@code speech_final} frame (or {@code UtteranceEnd}, or the post-CloseStream
     * flush) may carry only the tail — often blank. The full utterance is the
     * concatenation, so segments are buffered here and merged at flush time.
     * Guarded by itself.
     */
    private final List<DeepgramFinalTranscript> pendingSegments = new ArrayList<>();

    private volatile WebSocket webSocket;

    private volatile ScheduledExecutorService scheduler;

    private volatile ScheduledFuture<?> keepaliveTask;

    private volatile Consumer<DeepgramPartialTranscript> partialHandler = t -> { };

    private volatile Consumer<DeepgramFinalTranscript> finalHandler = t -> { };

    private volatile Runnable speechStartedHandler = () -> { };

    private volatile Runnable speechFinalHandler = () -> { };

    private volatile Consumer<Throwable> errorHandler = t -> log.warn(
        "Deepgram error (no handler registered)", t);

    private volatile Runnable closeHandler = () -> { };

    DeepgramSessionImpl() {
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    /** Called by {@link DeepgramClient} after the WS handshake completes. */
    void attachWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    /** Schedule the periodic KeepAlive frame (one task per session). */
    void scheduleKeepalive(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            return;
        }
        this.scheduler = scheduler;
        this.keepaliveTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                WebSocket ws = this.webSocket;
                if (ws != null && !closed.get()) {
                    ws.sendText(KEEPALIVE_PAYLOAD, true);
                }
            } catch (Exception e) {
                log.debug("Deepgram KeepAlive send failed: {}", e.getMessage());
            }
        }, KEEPALIVE_INTERVAL_SECONDS, KEEPALIVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void sendAudio(byte[] pcmChunk) {
        if (pcmChunk == null || pcmChunk.length == 0) {
            throw new CidadelException(DeepgramErrorDetails.INVALID_FRAME, MODULE_NAME);
        }
        WebSocket ws = this.webSocket;
        if (ws == null || closed.get() || closing.get()) {
            throw new CidadelException(DeepgramErrorDetails.SESSION_CLOSED, MODULE_NAME);
        }
        try {
            ws.sendBinary(ByteBuffer.wrap(pcmChunk), true);
        } catch (Exception e) {
            throw new CidadelException(DeepgramErrorDetails.SEND_FAILED, MODULE_NAME, e.getMessage());
        }
    }

    @Override
    public void onPartialTranscript(Consumer<DeepgramPartialTranscript> handler) {
        if (handler != null) {
            this.partialHandler = handler;
        }
    }

    @Override
    public void onFinalTranscript(Consumer<DeepgramFinalTranscript> handler) {
        if (handler != null) {
            this.finalHandler = handler;
        }
    }

    @Override
    public void onSpeechStarted(Runnable handler) {
        if (handler != null) {
            this.speechStartedHandler = handler;
        }
    }

    @Override
    public void onSpeechFinal(Runnable handler) {
        if (handler != null) {
            this.speechFinalHandler = handler;
        }
    }

    @Override
    public void onError(Consumer<Throwable> handler) {
        if (handler != null) {
            this.errorHandler = handler;
        }
    }

    @Override
    public void onClose(Runnable handler) {
        if (handler != null) {
            this.closeHandler = handler;
        }
    }

    @Override
    public boolean isOpen() {
        WebSocket ws = this.webSocket;
        return ws != null && !closed.get() && !closing.get() && !ws.isOutputClosed();
    }

    /**
     * Graceful close: sends Deepgram's {@code CloseStream} message so the server
     * transcribes any buffered audio and flushes the remaining finals before
     * closing the socket itself (a bare WS close frame would discard them — the
     * operator's last words would vanish and the turn would never dispatch).
     * The server's close lands in {@link #markClosed()}, which flushes any
     * accumulated utterance; a watchdog aborts the socket if Deepgram doesn't
     * close within {@link #DRAIN_TIMEOUT_MS}.
     */
    @Override
    public void close() {
        if (closed.get() || !closing.compareAndSet(false, true)) {
            return;
        }
        if (keepaliveTask != null) {
            keepaliveTask.cancel(false);
        }
        WebSocket ws = this.webSocket;
        if (ws == null) {
            markClosed();
            return;
        }
        try {
            ws.sendText(CLOSE_STREAM_PAYLOAD, true);
        } catch (Exception e) {
            log.debug("Deepgram CloseStream send failed: {}", e.getMessage());
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "client-close");
            } catch (Exception e2) {
                log.debug("Deepgram sendClose failed: {}", e2.getMessage());
            }
            markClosed();
            return;
        }
        ScheduledExecutorService sch = this.scheduler;
        if (sch == null) {
            // No scheduler to watchdog the drain (tests / degenerate wiring):
            // fall back to an immediate close after the flush request.
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "client-close");
            } catch (Exception e) {
                log.debug("Deepgram sendClose failed: {}", e.getMessage());
            }
            markClosed();
            return;
        }
        sch.schedule(() -> {
            if (!closed.get()) {
                log.debug("Deepgram drain timed out — aborting socket");
                try {
                    ws.abort();
                } catch (Exception ignored) {
                    // already gone
                }
                // markClosed flushes the pending utterance into the final handler,
                // which may run a long dispatch — keep it off the shared scheduler.
                java.util.concurrent.CompletableFuture.runAsync(this::markClosed);
            }
        }, DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /* ---------------- Inbound frame handling (called by listener) ---------------- */

    /**
     * Aggregate WS text fragments. Deepgram messages can arrive split across
     * multiple {@code onText} callbacks; we buffer until {@code last == true}.
     */
    void onTextFragment(CharSequence data, boolean last) {
        textBuffer.append(data);
        if (last) {
            String msg = textBuffer.toString();
            textBuffer.setLength(0);
            handleTextFrame(msg);
        }
    }

    /** Visible for tests — parses a complete JSON message and fires the matching handler. */
    void handleTextFrame(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode typeNode = node.get("type");
            String type = typeNode != null && !typeNode.isNull() ? typeNode.asString() : "Results";
            switch (type) {
                case "Results" -> handleResults(node);
                case "SpeechStarted" -> safe(speechStartedHandler);
                case "UtteranceEnd" -> {
                    safe(speechFinalHandler);
                    // VAD said the utterance is over but no speech_final frame came
                    // (typical under background noise) — the buffered is_final
                    // segments ARE the utterance; flush them as the final.
                    flushPendingSegments(null);
                }
                case "Error" -> fireError(node);
                case "Metadata" -> { /* ignore — diagnostics only */ }
                default -> log.debug("Deepgram unknown message type: {}", type);
            }
        } catch (Exception e) {
            fireError(e);
        }
    }

    private void handleResults(JsonNode node) {
        JsonNode channel = node.get("channel");
        if (channel == null || channel.isNull()) {
            return;
        }
        JsonNode alternatives = channel.get("alternatives");
        if (alternatives == null || !alternatives.isArray() || alternatives.isEmpty()) {
            return;
        }
        JsonNode alt = alternatives.get(0);
        String text = textValue(alt.get("transcript"));
        double confidence = doubleValue(alt.get("confidence"));
        List<WordTiming> words = parseWords(alt.get("words"));
        String requestId = textValue(node.get("request_id"));

        boolean isFinal = boolValue(node.get("is_final"));
        boolean speechFinal = boolValue(node.get("speech_final"));

        if (isFinal && speechFinal) {
            // End of utterance — merge any earlier finalized segments with this
            // tail (which Deepgram may send with only the tail text, or blank).
            flushPendingSegments(new DeepgramFinalTranscript(text, confidence, words, requestId));
        } else if (isFinal) {
            // Segment finalized mid-utterance: buffer it for the eventual flush,
            // and surface it as a partial so live captions keep up.
            synchronized (pendingSegments) {
                pendingSegments.add(new DeepgramFinalTranscript(text, confidence, words, requestId));
            }
            DeepgramPartialTranscript pt = new DeepgramPartialTranscript(
                text, confidence, words, requestId);
            try {
                partialHandler.accept(pt);
            } catch (Exception e) {
                fireError(e);
            }
        } else {
            DeepgramPartialTranscript pt = new DeepgramPartialTranscript(
                text, confidence, words, requestId);
            try {
                partialHandler.accept(pt);
            } catch (Exception e) {
                fireError(e);
            }
        }
    }

    /**
     * Merge the buffered {@code is_final} segments (plus an optional closing
     * tail) into one utterance and fire the final handler. No-op when the merged
     * transcript is blank — a silence-only flush must not dispatch a turn.
     */
    private void flushPendingSegments(DeepgramFinalTranscript tail) {
        List<DeepgramFinalTranscript> segments;
        synchronized (pendingSegments) {
            segments = new ArrayList<>(pendingSegments);
            pendingSegments.clear();
        }
        if (tail != null) {
            segments.add(tail);
        }
        StringBuilder text = new StringBuilder();
        List<WordTiming> words = new ArrayList<>();
        double confidence = 0.0d;
        String requestId = null;
        for (DeepgramFinalTranscript segment : segments) {
            if (segment.text() != null && !segment.text().isBlank()) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(segment.text().trim());
                confidence = segment.confidence();
            }
            if (segment.wordTimings() != null) {
                words.addAll(segment.wordTimings());
            }
            if (segment.requestId() != null) {
                requestId = segment.requestId();
            }
        }
        if (text.isEmpty()) {
            return;
        }
        DeepgramFinalTranscript merged = new DeepgramFinalTranscript(
            text.toString(), confidence, words.isEmpty() ? null : words, requestId);
        try {
            finalHandler.accept(merged);
        } catch (Exception e) {
            fireError(e);
        }
    }

    private List<WordTiming> parseWords(JsonNode wordsNode) {
        if (wordsNode == null || !wordsNode.isArray() || wordsNode.isEmpty()) {
            return null;
        }
        List<WordTiming> out = new ArrayList<>(wordsNode.size());
        for (JsonNode w : wordsNode) {
            out.add(new WordTiming(
                textValue(w.get("word")),
                doubleValue(w.get("start")),
                doubleValue(w.get("end")),
                doubleValue(w.get("confidence"))));
        }
        return out;
    }

    private static String textValue(JsonNode n) {
        return n == null || n.isNull() ? null : n.asString();
    }

    private static double doubleValue(JsonNode n) {
        return n == null || n.isNull() ? 0.0d : n.asDouble();
    }

    private static boolean boolValue(JsonNode n) {
        return n != null && !n.isNull() && n.asBoolean(false);
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            fireError(e);
        }
    }

    private void fireError(JsonNode errorNode) {
        String description = errorNode != null && errorNode.get("description") != null
            ? errorNode.get("description").asString() : "Deepgram error frame";
        fireError(new CidadelException(DeepgramErrorDetails.CONNECT_FAILED, MODULE_NAME, description));
    }

    void fireError(Throwable t) {
        try {
            errorHandler.accept(t);
        } catch (Exception e) {
            log.warn("Deepgram error handler threw", e);
        }
    }

    void fireClose() {
        try {
            closeHandler.run();
        } catch (Exception e) {
            log.warn("Deepgram close handler threw", e);
        }
    }

    /** Marks the session as closed without sending another close frame (used from WS listener). */
    void markClosed() {
        if (closed.compareAndSet(false, true)) {
            if (keepaliveTask != null) {
                keepaliveTask.cancel(false);
            }
            // The socket is gone — whatever segments accumulated are the last
            // chance at this utterance (covers a drain where the post-CloseStream
            // finals never carried speech_final).
            flushPendingSegments(null);
            fireClose();
        }
    }

}
