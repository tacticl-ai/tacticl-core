package io.tacticl.client.elevenlabs.client;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.client.elevenlabs.config.ElevenLabsConfig;
import io.tacticl.client.elevenlabs.dto.AudioChunk;
import io.tacticl.client.elevenlabs.exception.ElevenLabsErrorDetails;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Streaming TTS client for ElevenLabs (SAD §5.3).
 *
 * <p>Each call to {@link #open(ElevenLabsSessionConfig)} establishes a new
 * WebSocket against {@code /v1/text-to-speech/{voiceId}/stream-input}, sends
 * the documented init frame, and exposes an {@link ElevenLabsSession} that
 * pumps text in and audio chunks out. Sessions are designed to be short-lived
 * (one per utterance, sometimes multiple per assistant turn at tool-use
 * barriers — SAD §5.10.5).
 *
 * <p>This client owns the wire protocol only. Sentence chunking, buffer-flush
 * coordination on Anthropic {@code tool_use} blocks, and the
 * {@code OutboundAudioQueue} are owned by {@code business-voice}'s
 * {@code ElevenLabsStreamBridge}.
 *
 * <p><b>Auth choice:</b> ElevenLabs streaming-input historically accepts the
 * API key via either the {@code xi-api-key} HTTP header on the WS handshake or
 * the {@code xi_api_key} field inside the init JSON frame. We send <i>both</i>
 * — header for environments that respect handshake headers and init-frame for
 * environments where the handshake strips custom headers. This is a safe
 * superset; sending the key twice is documented to be tolerated.
 * TODO: verify ElevenLabs WS auth header vs init-message once the QA env is
 * provisioned and drop whichever path is redundant.
 */
public class ElevenLabsClient {

    private static final Logger logger = LoggerFactory.getLogger(ElevenLabsClient.class);

    private static final String MODULE_NAME = "client-elevenlabs";

    private static final String STREAM_INPUT_PATH_TEMPLATE =
        "/v1/text-to-speech/%s/stream-input?model_id=%s&output_format=%s";

    private final ElevenLabsConfig config;

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    public ElevenLabsClient(ElevenLabsConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    /**
     * Open a new streaming TTS session.
     *
     * @param sessionConfig per-utterance voice/format overrides;
     *                      {@link ElevenLabsSessionConfig#defaults()} for app-wide defaults
     */
    public ElevenLabsSession open(ElevenLabsSessionConfig sessionConfig) {
        if (!config.isConfigured()) {
            throw new CidadelException(ElevenLabsErrorDetails.SESSION_NOT_CONFIGURED, MODULE_NAME);
        }

        String voiceId = sessionConfig.voiceId() != null ? sessionConfig.voiceId() : config.getDefaultVoiceId();
        String outputFormat = sessionConfig.outputFormat() != null
            ? sessionConfig.outputFormat()
            : config.getDefaultOutputFormat();

        URI uri = URI.create(config.getApiBaseUrl()
            + String.format(Locale.ROOT, STREAM_INPUT_PATH_TEMPLATE, voiceId, config.getModel(), outputFormat));

        StreamingSession session = new StreamingSession(outputFormat, objectMapper);
        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
            .header("xi-api-key", config.getApiKey())
            .connectTimeout(Duration.ofSeconds(5));

        try {
            WebSocket ws = builder.buildAsync(uri, session)
                .toCompletableFuture()
                .join();
            session.attach(ws);
            session.sendInit(buildInitFrame(sessionConfig));
            return session;
        } catch (Exception e) {
            logger.error("Failed to open ElevenLabs WS to {}", uri, e);
            throw new CidadelException(ElevenLabsErrorDetails.SESSION_OPEN_FAILED, MODULE_NAME, e.getMessage());
        }
    }

    /**
     * Build the documented init frame. ElevenLabs requires a first message with
     * a single-space text so the model warms up before user text arrives.
     */
    public String buildInitFrame(ElevenLabsSessionConfig sessionConfig) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", " ");

        ObjectNode voiceSettings = root.putObject("voice_settings");
        voiceSettings.put("stability", sessionConfig.stability());
        voiceSettings.put("similarity_boost", sessionConfig.similarityBoost());
        if (sessionConfig.style() != null) {
            voiceSettings.put("style", sessionConfig.style());
        }

        ObjectNode generationConfig = root.putObject("generation_config");
        // chunk_length_schedule keeps ElevenLabs from waiting for long buffers;
        // these are conservative low-latency defaults aligned with eleven_turbo_v2.
        generationConfig.putArray("chunk_length_schedule").add(50);

        // See class-level TODO — sending xi_api_key in init too for safety.
        root.put("xi_api_key", config.getApiKey());

        return objectMapper.writeValueAsString(root);
    }

    /**
     * WebSocket listener that buffers partial text frames, dispatches inbound
     * JSON to handlers, and tracks send ordering.
     */
    public static final class StreamingSession implements WebSocket.Listener, ElevenLabsSession {

        private final String outputFormat;

        private final ObjectMapper mapper;

        private final StringBuilder inboundBuffer = new StringBuilder();

        private final AtomicReference<WebSocket> wsRef = new AtomicReference<>();

        private final AtomicBoolean open = new AtomicBoolean(false);

        private final AtomicBoolean doneFired = new AtomicBoolean(false);

        private final AtomicInteger sequence = new AtomicInteger(0);

        // Serialize send() calls — java.net.http.WebSocket requires that the
        // previous send CompletableFuture resolves before another send is invoked.
        private final ConcurrentLinkedQueue<CompletableFuture<WebSocket>> sendChain =
            new ConcurrentLinkedQueue<>();

        private volatile Consumer<AudioChunk> audioHandler = chunk -> {};

        private volatile Runnable doneHandler = () -> {};

        private volatile Consumer<Throwable> errorHandler = err -> {};

        public StreamingSession(String outputFormat, ObjectMapper mapper) {
            this.outputFormat = outputFormat;
            this.mapper = mapper;
        }

        public void attach(WebSocket ws) {
            wsRef.set(ws);
            open.set(true);
        }

        public void sendInit(String initJson) {
            enqueueSend(initJson);
        }

        @Override
        public void sendTextChunk(String text) {
            if (!open.get()) {
                throw new CidadelException(ElevenLabsErrorDetails.SESSION_CLOSED, MODULE_NAME);
            }
            ObjectNode frame = mapper.createObjectNode();
            frame.put("text", text);
            enqueueSend(mapper.writeValueAsString(frame));
        }

        @Override
        public void flush() {
            if (!open.get()) {
                return;
            }
            ObjectNode frame = mapper.createObjectNode();
            frame.put("text", "");
            frame.put("flush", true);
            enqueueSend(mapper.writeValueAsString(frame));
        }

        @Override
        public void onAudioChunk(Consumer<AudioChunk> handler) {
            this.audioHandler = handler != null ? handler : c -> {};
        }

        @Override
        public void onDone(Runnable handler) {
            this.doneHandler = handler != null ? handler : () -> {};
        }

        @Override
        public void onError(Consumer<Throwable> handler) {
            this.errorHandler = handler != null ? handler : e -> {};
        }

        @Override
        public boolean isOpen() {
            WebSocket ws = wsRef.get();
            return open.get() && ws != null && !ws.isOutputClosed() && !ws.isInputClosed();
        }

        /**
         * Close the WS immediately. SAD §5.10.5 demands this be non-blocking even
         * when audio is still in flight — we send the empty-text terminator best
         * effort, abort the socket, and return without joining anything.
         */
        @Override
        public void close() {
            if (!open.compareAndSet(true, false)) {
                return;
            }
            WebSocket ws = wsRef.get();
            if (ws == null) {
                return;
            }
            // Do NOT send a graceful EOS terminator here. java.net.http.WebSocket forbids a
            // sendText while a previous send's CompletableFuture is still pending, and close()
            // (a new turn's stop()/barge-in) races the in-flight init/text/flush chain from
            // speak(). A direct ws.sendText() that bypasses the send-serialization chain
            // interleaves frames on the wire → ElevenLabs rejects the stream with an error
            // frame (surfaces as UPSTREAM_ERROR, silencing the reply). On supersede/barge-in we
            // are discarding the remaining audio anyway, so abort() is the correct teardown.
            try {
                ws.abort();
            } catch (Exception e) {
                logger.debug("ElevenLabs ws.abort() raised: {}", e.toString());
            }
        }

        private void enqueueSend(String json) {
            WebSocket ws = wsRef.get();
            if (ws == null) {
                throw new CidadelException(ElevenLabsErrorDetails.SEND_FAILED, MODULE_NAME, "ws not attached");
            }
            CompletableFuture<WebSocket> prev = sendChain.poll();
            CompletableFuture<WebSocket> next;
            if (prev == null || prev.isDone()) {
                next = ws.sendText(json, true).toCompletableFuture();
            } else {
                next = prev.thenCompose(socket -> socket.sendText(json, true));
            }
            next.exceptionally(err -> {
                errorHandler.accept(err);
                return null;
            });
            sendChain.offer(next);
        }

        // -------- WebSocket.Listener --------

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            inboundBuffer.append(data);
            if (last) {
                String full = inboundBuffer.toString();
                inboundBuffer.setLength(0);
                handleInbound(full);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // ElevenLabs streaming-input historically only emits text frames
            // (audio is base64-embedded in JSON). Keep the binary path inert
            // but request more so the socket does not stall if that changes.
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            open.set(false);
            if (doneFired.compareAndSet(false, true)) {
                doneHandler.run();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            open.set(false);
            errorHandler.accept(error);
        }

        public void handleInbound(String json) {
            JsonNode node;
            try {
                node = mapper.readTree(json);
            } catch (Exception e) {
                errorHandler.accept(e);
                return;
            }

            JsonNode errorNode = node.get("error");
            JsonNode messageNode = node.get("message");
            if (errorNode != null && !errorNode.isNull()) {
                String msg = messageNode != null && !messageNode.isNull()
                    ? messageNode.asString()
                    : errorNode.asString();
                // Surface the raw ElevenLabs error frame — CidadelException#getMessage only
                // yields the code (UPSTREAM_ERROR), hiding what ElevenLabs actually rejected.
                logger.warn("ElevenLabs upstream error frame: {} (raw={})", msg,
                    json.length() > 400 ? json.substring(0, 400) : json);
                errorHandler.accept(new CidadelException(ElevenLabsErrorDetails.UPSTREAM_ERROR, MODULE_NAME, msg));
                return;
            }

            JsonNode audioNode = node.get("audio");
            JsonNode isFinalNode = node.get("isFinal");
            boolean isFinal = isFinalNode != null && isFinalNode.asBoolean(false);

            if (audioNode != null && !audioNode.isNull() && !audioNode.asString().isEmpty()) {
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(audioNode.asString());
                } catch (IllegalArgumentException badBase64) {
                    errorHandler.accept(badBase64);
                    return;
                }
                AudioChunk chunk = new AudioChunk(
                    bytes,
                    deriveFormatTag(outputFormat),
                    sequence.getAndIncrement(),
                    isFinal);
                audioHandler.accept(chunk);
            }

            if (isFinal && doneFired.compareAndSet(false, true)) {
                doneHandler.run();
            }
        }

        private static String deriveFormatTag(String outputFormat) {
            if (outputFormat == null) {
                return "mp3";
            }
            String lower = outputFormat.toLowerCase(Locale.ROOT);
            if (lower.startsWith("pcm")) {
                return "pcm";
            }
            if (lower.startsWith("mp3")) {
                return "mp3";
            }
            // Pass through codec prefix for future formats (e.g. opus_…).
            int underscore = lower.indexOf('_');
            return underscore > 0 ? lower.substring(0, underscore) : lower;
        }

    }

}
