package io.tacticl.business.voice;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Per-session TTS bridge over the LOCAL voice sidecar's {@code /v1/tts} WebSocket.
 * The local mirror of {@link ElevenLabsTtsBridge}: same {@link TtsBridge} contract,
 * one short-lived WS per utterance. {@link #speak(String)} opens a fresh socket,
 * sends a single JSON {@code {"text":...,"voice":...}} frame, forwards every inbound
 * BINARY chunk (raw 16 kHz mono s16le PCM16) to {@link #onAudioChunk}, and fires
 * {@link #onDone} on the terminal {@code {"type":"done"}} text frame.
 *
 * <p>Wire protocol ({@code {base}/v1/tts}, one utterance per connection):
 * <ul>
 *   <li>client → server: a single JSON text frame
 *       {@code {"text":"<text>","voice":"<optional voice id>"}}.</li>
 *   <li>server → client: BINARY frames = raw 16 kHz PCM16 chunks, then a JSON text
 *       frame {@code {"type":"done"}}; {@code {"type":"error","message":...}} on
 *       failure. (If the model emits 24 kHz natively the SIDECAR resamples to 16 kHz
 *       before sending — the client assumes 16 kHz, matching the mic + browser.)</li>
 * </ul>
 *
 * <p>{@link #stop()} is the barge-in primitive: aborts the in-flight socket
 * non-blocking so the WS handler can flip straight back to listening. Robust to
 * fragmented WS text frames (accumulated until {@code last}).
 */
public class LocalTtsBridge implements TtsBridge {

    private static final Logger log = LoggerFactory.getLogger(LocalTtsBridge.class);

    private static final String TTS_PATH = "/v1/tts";

    /** How long to wait for the per-utterance WS handshake before failing the turn. */
    private static final long CONNECT_TIMEOUT_SECONDS = 10L;

    private final HttpClient httpClient;

    private final JsonMapper mapper;

    private final URI ttsUri;

    private final String voiceId;

    private volatile WebSocket webSocket;

    private final AtomicBoolean speaking = new AtomicBoolean(false);

    private volatile Consumer<byte[]> onAudioChunk = b -> { };

    private volatile Runnable onDone = () -> { };

    private volatile Consumer<Throwable> onError = e -> { };

    /**
     * @param baseUrl the sidecar base WS URL (e.g. {@code ws://voice-sidecar:8700});
     *                {@code /v1/tts} is appended.
     * @param voiceId optional explicit voice id; {@code null}/blank omits the field so
     *                the sidecar uses its default voice.
     */
    public LocalTtsBridge(HttpClient httpClient, JsonMapper mapper, String baseUrl, String voiceId) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.ttsUri = URI.create(trimTrailingSlash(baseUrl) + TTS_PATH);
        this.voiceId = (voiceId == null || voiceId.isBlank()) ? null : voiceId;
    }

    @Override
    public TtsBridge onAudioChunk(Consumer<byte[]> handler) {
        this.onAudioChunk = handler != null ? handler : b -> { };
        return this;
    }

    @Override
    public TtsBridge onDone(Runnable handler) {
        this.onDone = handler != null ? handler : () -> { };
        return this;
    }

    @Override
    public TtsBridge onError(Consumer<Throwable> handler) {
        this.onError = handler != null ? handler : e -> { };
        return this;
    }

    /**
     * Synthesize one utterance. Stops any in-flight utterance first (a new turn
     * supersedes the previous narration), opens a fresh socket, and sends the text
     * frame. Open failures route to {@code onError} rather than throwing. No-op on
     * blank text.
     */
    @Override
    public synchronized void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        stop();
        try {
            WebSocket ws = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .buildAsync(ttsUri, new TtsListener())
                .get();
            this.webSocket = ws;
            this.speaking.set(true);
            ws.sendText(requestFrame(text), true);
            log.debug("Local TTS utterance started ({} chars, {})", text.length(), ttsUri);
        } catch (Exception e) {
            log.warn("Local TTS speak failed ({}): {}", ttsUri, e.toString());
            this.speaking.set(false);
            dispatchError(e);
        }
    }

    @Override
    public boolean isSpeaking() {
        WebSocket ws = this.webSocket;
        return ws != null && speaking.get() && !ws.isOutputClosed();
    }

    /**
     * Barge-in / stop. Non-blocking: aborts the current socket and returns
     * immediately. Safe to call when nothing is playing.
     */
    @Override
    public synchronized void stop() {
        WebSocket ws = this.webSocket;
        this.webSocket = null;
        this.speaking.set(false);
        if (ws != null) {
            try {
                ws.abort();
            } catch (Exception e) {
                log.debug("Local TTS stop raced: {}", e.toString());
            }
        }
    }

    private String requestFrame(String text) {
        ObjectNode node = mapper.createObjectNode();
        node.put("text", text);
        if (voiceId != null) {
            node.put("voice", voiceId);
        }
        return node.toString();
    }

    private void handleEvent(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode typeNode = node.get("type");
            String type = typeNode != null && !typeNode.isNull() ? typeNode.asString() : null;
            if (type == null) {
                return;
            }
            switch (type) {
                case "done" -> dispatchDone();
                case "error" -> {
                    JsonNode msg = node.get("message");
                    String message = msg != null && !msg.isNull() ? msg.asString() : "local tts error";
                    dispatchError(new IllegalStateException(message));
                }
                default -> log.debug("Local TTS ignored event type '{}'", type);
            }
        } catch (RuntimeException e) {
            log.debug("Local TTS malformed event ignored: {}", e.toString());
        }
    }

    /**
     * The utterance's audio is fully streamed. Close the socket and fire onDone.
     * Idempotent against a racing {@link #stop()} / transport close.
     */
    private synchronized void dispatchDone() {
        WebSocket ws = this.webSocket;
        this.webSocket = null;
        this.speaking.set(false);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            } catch (Exception e) {
                log.debug("Local TTS close-on-done raced: {}", e.toString());
            }
        }
        onDone.run();
    }

    private void dispatchError(Throwable t) {
        onError.accept(t);
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isBlank()) {
            return "ws://voice-sidecar:8700";
        }
        String trimmed = base.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /**
     * WS listener for one utterance: BINARY frames are PCM16 audio chunks forwarded
     * to {@code onAudioChunk}; the terminal {@code done}/{@code error} text frames
     * settle the utterance. Binary fragments are reassembled before forwarding.
     */
    private final class TtsListener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();

        private ByteBuffer binaryBuffer;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            binaryBuffer = append(binaryBuffer, data);
            if (last) {
                ByteBuffer complete = binaryBuffer;
                binaryBuffer = null;
                byte[] bytes = new byte[complete.remaining()];
                complete.get(bytes);
                if (bytes.length > 0) {
                    onAudioChunk.accept(bytes);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String msg = textBuffer.toString();
                textBuffer.setLength(0);
                handleEvent(msg);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            speaking.set(false);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            speaking.set(false);
            dispatchError(error);
        }

        private ByteBuffer append(ByteBuffer existing, ByteBuffer next) {
            if (existing == null) {
                ByteBuffer copy = ByteBuffer.allocate(next.remaining());
                copy.put(next);
                copy.flip();
                return copy;
            }
            ByteBuffer combined = ByteBuffer.allocate(existing.remaining() + next.remaining());
            combined.put(existing);
            combined.put(next);
            combined.flip();
            return combined;
        }
    }
}
