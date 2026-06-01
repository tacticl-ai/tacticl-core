package io.tacticl.client.deepgram.client;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.client.deepgram.config.DeepgramConfig;
import io.tacticl.client.deepgram.dto.DeepgramSessionConfig;
import io.tacticl.client.deepgram.exception.DeepgramErrorDetails;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens streaming WebSocket sessions to Deepgram per SAD §5.2.
 *
 * <p>This client is stateless across sessions — each {@link #open(DeepgramSessionConfig)}
 * call returns a fresh {@link DeepgramSession}. Reconnect / backoff lives in
 * {@code DeepgramStreamBridge} (business-voice, Wave 3), not here.
 *
 * <p>Endpoint URL is built from {@link DeepgramConfig}:
 * <pre>
 * wss://api.deepgram.com/v1/listen
 *   ?model={model}&encoding=linear16&sample_rate={sampleRate}
 *   &channels=1&interim_results=true&endpointing={endpointingMs}
 *   &vad_events=true&language={lang}[&utterance_end_ms={endpointing}]
 * </pre>
 *
 * <p>Authorization header: {@code Token {apiKey}}.
 */
public class DeepgramClient {

    private static final Logger log = LoggerFactory.getLogger(DeepgramClient.class);

    private static final String MODULE_NAME = "client-deepgram";

    private final DeepgramConfig config;

    private final HttpClient httpClient;

    private final ScheduledExecutorService keepaliveScheduler;

    public DeepgramClient(DeepgramConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.keepaliveScheduler = Executors.newScheduledThreadPool(1, namedDaemonFactory("deepgram-keepalive"));
    }

    /**
     * Open a new Deepgram streaming session.
     *
     * @throws CidadelException with {@link DeepgramErrorDetails#NOT_CONFIGURED} when the
     *     API key has not been provisioned, or {@link DeepgramErrorDetails#CONNECT_FAILED}
     *     when the WS handshake fails.
     */
    public DeepgramSession open(DeepgramSessionConfig sessionConfig) {
        if (!config.isConfigured()) {
            throw new CidadelException(DeepgramErrorDetails.NOT_CONFIGURED, MODULE_NAME);
        }
        DeepgramSessionConfig effective = sessionConfig != null
            ? sessionConfig : DeepgramSessionConfig.defaults();

        URI uri = buildEndpoint(effective);
        DeepgramSessionImpl session = createSession();

        try {
            WebSocket ws = httpClient.newWebSocketBuilder()
                .header("Authorization", "Token " + config.getApiKey())
                // utterance_end_ms requires UtteranceEnd to be enabled — Deepgram sends
                // these when both vad_events=true and utterance_end_ms is set.
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .buildAsync(uri, new SessionListener(session))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
            session.attachWebSocket(ws);
            if (effective.keepalive()) {
                session.scheduleKeepalive(keepaliveScheduler);
            }
            log.info("Deepgram session opened (model={}, sampleRate={}, lang={})",
                config.getModel(), config.getSampleRate(), effective.language());
            return session;
        } catch (CidadelException e) {
            throw e;
        } catch (Exception e) {
            log.error("Deepgram WS handshake failed: {}", e.getMessage());
            throw new CidadelException(DeepgramErrorDetails.CONNECT_FAILED, MODULE_NAME, e.getMessage());
        }
    }

    /** Visible for tests — overridden to inject a pre-baked impl with a stub WebSocket. */
    DeepgramSessionImpl createSession() {
        return new DeepgramSessionImpl();
    }

    URI buildEndpoint(DeepgramSessionConfig sessionConfig) {
        // TODO: spec gap — SAD lists Deepgram base URL but not the exact query-string
        // contract for utterance_end_ms vs endpointing. Defaulting to utterance_end_ms = endpointing
        // (Deepgram's documented behaviour: UtteranceEnd events fire after N ms of silence).
        String base = trimTrailingSlash(config.getApiBaseUrl());
        String query = String.format(
            "model=%s&encoding=linear16&sample_rate=%d&channels=1&interim_results=true"
                + "&endpointing=%d&vad_events=true&utterance_end_ms=%d&language=%s",
            urlEncode(config.getModel()),
            config.getSampleRate(),
            config.getEndpointingMs(),
            config.getEndpointingMs(),
            urlEncode(sessionConfig.language()));
        return URI.create(base + "/v1/listen?" + query);
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String urlEncode(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** Bridges JDK {@link WebSocket.Listener} events to the {@link DeepgramSessionImpl}. */
    private static final class SessionListener implements WebSocket.Listener {

        private final DeepgramSessionImpl session;

        SessionListener(DeepgramSessionImpl session) {
            this.session = session;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                session.onTextFragment(data, last);
            } catch (Exception e) {
                session.fireError(e);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // Deepgram does not send binary frames in this protocol; ignore.
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            session.markClosed();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            session.fireError(error);
            session.markClosed();
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

    }

}
