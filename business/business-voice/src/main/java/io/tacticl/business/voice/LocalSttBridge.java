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

/**
 * Per-session STT bridge over the LOCAL voice sidecar's {@code /v1/stt} WebSocket.
 * The local mirror of {@link DeepgramSttBridge}: same {@link SttBridge} contract,
 * same callback semantics, but instead of Deepgram it streams raw 16 kHz mono
 * s16le PCM mic chunks to a self-hosted sidecar and parses the sidecar's JSON
 * events back into partial/final/speech-started/error callbacks.
 *
 * <p>Wire protocol ({@code {base}/v1/stt}):
 * <ul>
 *   <li>client → server: an init JSON text frame
 *       {@code {"sample_rate":16000,"encoding":"pcm_s16le","channels":1}}, then
 *       continuous BINARY frames = raw PCM mic chunks.</li>
 *   <li>server → client: JSON text frames, one per event —
 *       {@code {"type":"speech_started"}}, {@code {"type":"partial","text":...}},
 *       {@code {"type":"final","text":...}}, {@code {"type":"error","message":...}}.</li>
 * </ul>
 *
 * <p>Robust to fragmented WS text frames: fragments are accumulated until
 * {@code last == true} before parsing (the sidecar may split a JSON event across
 * callbacks). Threading mirrors {@link DeepgramSttBridge} — callbacks run on the
 * JDK HttpClient's WS executor; the consuming {@link VoiceSessionService} owns any
 * hand-off. Not thread-safe for concurrent {@link #open}/{@link #close}.
 */
public class LocalSttBridge implements SttBridge {

    private static final Logger log = LoggerFactory.getLogger(LocalSttBridge.class);

    private static final String STT_PATH = "/v1/stt";

    private static final String INIT_FRAME =
        "{\"sample_rate\":16000,\"encoding\":\"pcm_s16le\",\"channels\":1}";

    /** How long to wait for the WS handshake before treating open() as failed. */
    private static final long CONNECT_TIMEOUT_SECONDS = 10L;

    private final HttpClient httpClient;

    private final JsonMapper mapper;

    private final URI sttUri;

    private volatile WebSocket webSocket;

    private final AtomicBoolean open = new AtomicBoolean(false);

    private volatile Consumer<String> onPartial = t -> { };

    private volatile Consumer<String> onFinal = t -> { };

    private volatile Runnable onSpeechStarted = () -> { };

    private volatile Consumer<Throwable> onError = e -> { };

    /**
     * @param baseUrl the sidecar base WS URL (e.g. {@code ws://voice-sidecar:8700});
     *                {@code /v1/stt} is appended.
     */
    public LocalSttBridge(HttpClient httpClient, JsonMapper mapper, String baseUrl) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.sttUri = URI.create(trimTrailingSlash(baseUrl) + STT_PATH);
    }

    @Override
    public SttBridge onPartial(Consumer<String> handler) {
        this.onPartial = handler != null ? handler : t -> { };
        return this;
    }

    @Override
    public SttBridge onFinal(Consumer<String> handler) {
        this.onFinal = handler != null ? handler : t -> { };
        return this;
    }

    @Override
    public SttBridge onSpeechStarted(Runnable handler) {
        this.onSpeechStarted = handler != null ? handler : () -> { };
        return this;
    }

    @Override
    public SttBridge onError(Consumer<Throwable> handler) {
        this.onError = handler != null ? handler : e -> { };
        return this;
    }

    /**
     * Open the sidecar STT socket (idempotent — a no-op if already open). Sends the
     * init JSON frame on connect. Any handshake failure routes to {@code onError}
     * rather than throwing, so a failed turn degrades to silence.
     */
    @Override
    public synchronized void open() {
        if (webSocket != null && open.get()) {
            return;
        }
        try {
            WebSocket ws = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .buildAsync(sttUri, new SttListener())
                .get();
            this.webSocket = ws;
            this.open.set(true);
            ws.sendText(INIT_FRAME, true);
            log.info("Local STT bridge opened ({})", sttUri);
        } catch (Exception e) {
            log.warn("Local STT bridge open failed ({}): {}", sttUri, e.toString());
            this.open.set(false);
            dispatchError(e);
        }
    }

    /**
     * Forward a 16 kHz mono s16le PCM chunk as a BINARY frame. Silently ignored if
     * the socket is not open (e.g. a stray frame after barge-in).
     */
    @Override
    public void sendAudio(byte[] pcmChunk) {
        WebSocket ws = this.webSocket;
        if (ws == null || !open.get() || pcmChunk == null || pcmChunk.length == 0) {
            return;
        }
        try {
            ws.sendBinary(ByteBuffer.wrap(pcmChunk), true);
        } catch (Exception e) {
            dispatchError(e);
        }
    }

    @Override
    public boolean isOpen() {
        WebSocket ws = this.webSocket;
        return ws != null && open.get() && !ws.isOutputClosed();
    }

    @Override
    public synchronized void close() {
        WebSocket ws = this.webSocket;
        this.webSocket = null;
        this.open.set(false);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "client-close");
            } catch (Exception e) {
                log.debug("Local STT close raced: {}", e.toString());
                try {
                    ws.abort();
                } catch (Exception ignored) {
                    // already gone
                }
            }
        }
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
                case "speech_started" -> onSpeechStarted.run();
                case "partial" -> {
                    String text = textOf(node);
                    if (text != null && !text.isBlank()) {
                        onPartial.accept(text);
                    }
                }
                case "final" -> {
                    String text = textOf(node);
                    if (text != null && !text.isBlank()) {
                        log.info("Local STT FINAL transcript: '{}'", text);
                        onFinal.accept(text);
                    }
                }
                case "error" -> {
                    JsonNode msg = node.get("message");
                    String message = msg != null && !msg.isNull() ? msg.asString() : "local stt error";
                    dispatchError(new IllegalStateException(message));
                }
                default -> log.debug("Local STT ignored event type '{}'", type);
            }
        } catch (RuntimeException e) {
            log.debug("Local STT malformed event ignored: {}", e.toString());
        }
    }

    private static String textOf(JsonNode node) {
        JsonNode text = node.get("text");
        return text != null && !text.isNull() ? text.asString() : null;
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
     * WS listener that accumulates fragmented text frames and surfaces parsed
     * events / transport errors onto the bridge callbacks.
     */
    private final class SttListener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
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
            open.set(false);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            open.set(false);
            dispatchError(error);
        }
    }
}
