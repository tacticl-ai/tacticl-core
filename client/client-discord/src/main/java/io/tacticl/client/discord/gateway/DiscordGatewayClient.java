package io.tacticl.client.discord.gateway;

import io.tacticl.client.discord.config.DiscordConfig;
import io.tacticl.client.discord.dto.DiscordGatewayMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Discord Gateway (WebSocket) client for receiving free-form {@code MESSAGE_CREATE} events,
 * built on the JDK {@link java.net.http.WebSocket} (same idiom as {@code DeepgramClient}). It
 * owns the full Gateway v10 protocol: HELLO → heartbeat, IDENTIFY/RESUME, sequence tracking,
 * heartbeat-ACK zombie detection, and reconnect with exponential backoff + jitter. Outbound
 * replies are NOT its job — those go through {@code DiscordRestClient}.
 *
 * <p><b>Connection generation.</b> Every connection attempt gets a monotonic generation id, carried
 * by its {@code GatewayListener} and its heartbeat task. {@link #triggerReconnect} CAS-bumps the
 * generation, which atomically (a) dedups the many concurrent reconnect triggers — close, error,
 * zombie, op7/op9 — down to one, and (b) makes every callback and the heartbeat of the now-superseded
 * connection a no-op. This closes the "late onClose from the old socket tears down the new
 * connection" hazard and the leaked-heartbeat / awaitingAck cross-connection races.
 *
 * <p>Lifecycle is driven by the business layer: {@link #start(DiscordGatewayListener)} on app ready,
 * {@link #shutdown()} on context close. The listener is invoked on the WS read thread and must not
 * block (see {@link DiscordGatewayListener}).
 *
 * <p>Privileged intent: this requests MESSAGE CONTENT, which must be enabled in the Discord dev
 * portal. Without it the gateway is closed 4014 (fatal) and the client stops with an actionable
 * error rather than reconnect-looping.
 */
public class DiscordGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(DiscordGatewayClient.class);

    private static final String GATEWAY_QS = "/?v=10&encoding=json";
    /** GUILD_MESSAGES (1<<9) | DIRECT_MESSAGES (1<<12) | MESSAGE_CONTENT (1<<15) = 37376. */
    private static final int INTENTS = (1 << 9) | (1 << 12) | (1 << 15);
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 15L;
    private static final long BACKOFF_BASE_MS = 1_000L;
    private static final long BACKOFF_MAX_MS = 60_000L;

    private final DiscordConfig config;
    private final HttpClient httpClient;
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Schedules heartbeats and reconnect delays. */
    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(1, namedDaemonFactory("discord-gw-sched"));
    /** Runs the blocking WS handshake off the scheduler / Spring threads. */
    private final ExecutorService connectExecutor =
        Executors.newSingleThreadExecutor(namedDaemonFactory("discord-gw-connect"));

    private volatile DiscordGatewayListener listener;
    private volatile WebSocket webSocket;
    private volatile ScheduledFuture<?> heartbeatTask;

    /** Identifies the live connection; bumped to supersede a connection + dedup reconnect triggers. */
    private final AtomicLong generation = new AtomicLong(0);

    private final AtomicLong lastSequence = new AtomicLong(-1); // -1 ⇒ serialize as JSON null
    private volatile String sessionId;
    private volatile String resumeGatewayUrl;
    private volatile String botUserId;
    private volatile boolean resumeIntent;

    private final AtomicBoolean awaitingAck = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean shuttingDown = false;
    private final AtomicInteger backoffAttempt = new AtomicInteger(0);

    public DiscordGatewayClient(DiscordConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    /** Start the gateway. Returns immediately; the handshake runs on a background thread. */
    public void start(DiscordGatewayListener listener) {
        if (config.getBotToken() == null || config.getBotToken().isBlank()) {
            log.warn("Discord gateway not started — no bot token configured");
            return;
        }
        this.listener = listener;
        if (!running.compareAndSet(false, true)) {
            return; // already running
        }
        shuttingDown = false;
        submitConnect(false);
    }

    /** Stop the gateway cleanly: a 1000 close drops the session (no reconnect), executors halt. */
    public void shutdown() {
        running.set(false);
        shuttingDown = true;
        generation.incrementAndGet(); // stale-out any in-flight callbacks/heartbeats
        cancelHeartbeat();
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try {
                ws.sendClose(GatewayOpcodes.CLOSE_NORMAL, "shutdown");
            } catch (Exception e) {
                try { ws.abort(); } catch (Exception ignore) { /* best effort */ }
            }
        }
        scheduler.shutdownNow();
        connectExecutor.shutdownNow();
        log.info("Discord gateway shut down");
    }

    public boolean isConnected() {
        return webSocket != null && !shuttingDown;
    }

    /** The bot's own user id (captured at READY); null until then. Used for self-loop defense. */
    public String getBotUserId() {
        return botUserId;
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private void submitConnect(boolean resume) {
        if (!running.get()) {
            return;
        }
        try {
            connectExecutor.submit(() -> connect(resume));
        } catch (RejectedExecutionException e) {
            // executor shut down between the running-check and submit — we're stopping; ignore.
            log.debug("Discord gateway connect not submitted (shutting down)");
        }
    }

    private void connect(boolean resume) {
        if (!running.get()) {
            return;
        }
        long gen = generation.incrementAndGet(); // this connection's identity
        this.resumeIntent = resume;
        URI uri = gatewayUri(resume);
        try {
            WebSocket ws = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new GatewayListener(gen))
                .toCompletableFuture()
                .get(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // webSocket is normally already assigned by onOpen (which fires before the first frame,
            // so sendIdentify() never races a null socket). Re-assert here, unless a reconnect
            // superseded this connection mid-handshake.
            if (gen != generation.get()) {
                try { ws.abort(); } catch (Exception ignore) { /* superseded */ }
                return;
            }
            this.webSocket = ws;
            log.info("Discord gateway connected ({}, resume={}, gen={})", uri.getHost(), resume, gen);
        } catch (Exception e) {
            log.warn("Discord gateway handshake failed ({}): {}", uri, e.toString());
            // A failed handshake can still resume if we hold a session; otherwise re-identify.
            triggerReconnect(resume && canResume(), 4000, gen);
        }
    }

    private URI gatewayUri(boolean resume) {
        if (resume && resumeGatewayUrl != null && !resumeGatewayUrl.isBlank()) {
            return URI.create(stripTrailingSlash(resumeGatewayUrl) + GATEWAY_QS);
        }
        return URI.create(stripTrailingSlash(config.getGatewayUrl()) + GATEWAY_QS);
    }

    // ── Frame handling ──────────────────────────────────────────────────────────

    private void handleFrame(String json, long gen) {
        try {
            JsonNode root = mapper.readTree(json);
            int op = root.path("op").intValue();
            switch (op) {
                case GatewayOpcodes.HELLO -> onHello(root.path("d"), gen);
                case GatewayOpcodes.HEARTBEAT_ACK -> awaitingAck.set(false);
                case GatewayOpcodes.HEARTBEAT -> sendHeartbeat(); // server-requested immediate beat
                case GatewayOpcodes.DISPATCH -> {
                    JsonNode s = root.path("s");
                    if (!s.isMissingNode() && !s.isNull()) {
                        lastSequence.set(s.longValue());
                    }
                    onDispatch(root.path("t").asString(""), root.path("d"));
                }
                case GatewayOpcodes.RECONNECT -> {
                    log.info("Discord gateway asked us to reconnect (op 7)");
                    triggerReconnect(canResume(), GatewayOpcodes.CLOSE_ZOMBIE, gen);
                }
                case GatewayOpcodes.INVALID_SESSION -> {
                    boolean resumable = root.path("d").asBoolean(false);
                    log.info("Discord gateway INVALID_SESSION (resumable={})", resumable);
                    if (!resumable) {
                        clearSession();
                    }
                    triggerReconnect(resumable && canResume(), resumable ? 4000 : 4007, gen);
                }
                default -> log.debug("Ignoring gateway op {}", op);
            }
        } catch (Exception e) {
            // A malformed frame must never kill the read pump.
            log.warn("Discord gateway frame parse failed: {}", e.toString());
        }
    }

    private void onHello(JsonNode d, long gen) {
        long interval = d.path("heartbeat_interval").longValue();
        if (interval <= 0) {
            interval = 41_250L; // Discord's typical value; defensive fallback
        }
        startHeartbeat(interval, gen);
        if (resumeIntent && canResume()) {
            sendResume();
        } else {
            sendIdentify();
        }
    }

    private void onDispatch(String type, JsonNode d) {
        switch (type) {
            case "READY" -> {
                sessionId = str(d.path("session_id"));
                resumeGatewayUrl = str(d.path("resume_gateway_url"));
                botUserId = str(d.path("user").path("id"));
                backoffAttempt.set(0);
                log.info("Discord gateway READY (session captured, bot={})", botUserId);
            }
            case "RESUMED" -> {
                backoffAttempt.set(0);
                log.info("Discord gateway RESUMED");
            }
            case "MESSAGE_CREATE" -> deliverMessage(d);
            default -> { /* ignore every other dispatch type, incl. MESSAGE_UPDATE */ }
        }
    }

    private void deliverMessage(JsonNode d) {
        DiscordGatewayMessage msg = new DiscordGatewayMessage(
            str(d.path("id")),
            str(d.path("channel_id")),
            str(d.path("guild_id")),
            str(d.path("author").path("id")),
            str(d.path("author").path("username")),
            d.path("author").path("bot").asBoolean(false),
            str(d.path("webhook_id")),
            d.path("content").asString(""));
        DiscordGatewayListener l = this.listener;
        if (l == null) {
            return;
        }
        try {
            l.onMessageCreate(msg);
        } catch (Exception e) {
            // A bridge failure must never kill the connection.
            log.warn("Discord gateway listener failed for message {}: {}", msg.id(), e.toString());
        }
    }

    // ── Heartbeat ───────────────────────────────────────────────────────────────

    private void startHeartbeat(long intervalMs, long gen) {
        cancelHeartbeat();
        awaitingAck.set(false);
        // Jitter the first beat across [0, interval) per Discord guidance.
        long first = (long) (intervalMs * ThreadLocalRandom.current().nextDouble());
        heartbeatTask = scheduler.scheduleAtFixedRate(
            () -> heartbeatTick(gen), first, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void heartbeatTick(long gen) {
        if (!running.get() || gen != generation.get()) {
            return; // superseded connection — this task is a no-op until it's cancelled
        }
        if (awaitingAck.get()) {
            // The previous heartbeat was never ACKed → zombie connection. Drop and resume.
            log.warn("Discord gateway heartbeat not ACKed — treating as zombie, reconnecting");
            triggerReconnect(true, GatewayOpcodes.CLOSE_ZOMBIE, gen);
            return;
        }
        awaitingAck.set(true);
        sendHeartbeat();
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        if (task != null) {
            task.cancel(false);
            heartbeatTask = null;
        }
    }

    // ── Sends ─────────────────────────────────────────────────────────────────

    private void sendHeartbeat() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("op", GatewayOpcodes.HEARTBEAT);
        long seq = lastSequence.get();
        payload.put("d", seq < 0 ? null : seq); // HashMap allows the null d before first dispatch
        send(payload);
    }

    private void sendIdentify() {
        Map<String, Object> identify = Map.of(
            "op", GatewayOpcodes.IDENTIFY,
            "d", Map.of(
                "token", config.getBotToken(),
                "intents", INTENTS,
                "properties", Map.of("os", "linux", "browser", "tacticl", "device", "tacticl")));
        send(identify);
        log.info("Discord gateway IDENTIFY sent (intents={})", INTENTS);
    }

    private void sendResume() {
        Map<String, Object> resume = Map.of(
            "op", GatewayOpcodes.RESUME,
            "d", Map.of(
                "token", config.getBotToken(),
                "session_id", sessionId,
                "seq", lastSequence.get()));
        send(resume);
        log.info("Discord gateway RESUME sent (seq={})", lastSequence.get());
    }

    private void send(Map<String, Object> payload) {
        WebSocket ws = webSocket;
        if (ws == null) {
            return;
        }
        try {
            ws.sendText(mapper.writeValueAsString(payload), true);
        } catch (Exception e) {
            log.warn("Discord gateway send failed (op={}): {}", payload.get("op"), e.toString());
        }
    }

    // ── Reconnect ───────────────────────────────────────────────────────────────

    private void triggerReconnect(boolean resume, int code, long gen) {
        if (!running.get()) {
            return;
        }
        // Only the CURRENT connection may trigger; the CAS both rejects stale callbacks (their gen no
        // longer matches) and dedups concurrent triggers from the same connection (only one wins the
        // bump). The bump also supersedes this connection's heartbeat task.
        if (!generation.compareAndSet(gen, gen + 1)) {
            return;
        }
        cancelHeartbeat();
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try { ws.abort(); } catch (Exception ignore) { /* best effort */ }
        }
        if (GatewayOpcodes.isFatalClose(code)) {
            running.set(false);
            if (GatewayOpcodes.isDisallowedIntents(code)) {
                log.error("Discord gateway closed 4014 (disallowed intents) — enable the MESSAGE "
                    + "CONTENT privileged intent for this bot in the Discord developer portal, then "
                    + "redeploy. The gateway will NOT reconnect until then.");
            } else if (GatewayOpcodes.isAuthFailed(code)) {
                log.error("Discord gateway closed 4004 (authentication failed) — the bot token is "
                    + "invalid. The gateway will NOT reconnect.");
            } else {
                log.error("Discord gateway closed with fatal code {} — not reconnecting", code);
            }
            return;
        }
        long delay = backoffDelayMs();
        log.warn("Discord gateway reconnecting in {}ms (resume={}, code={})", delay, resume, code);
        try {
            scheduler.schedule(() -> {
                if (running.get() && !shuttingDown) {
                    submitConnect(resume);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("Discord gateway reconnect not scheduled (shutting down)");
        }
    }

    private long backoffDelayMs() {
        int attempt = backoffAttempt.getAndIncrement();
        long base = Math.min(BACKOFF_BASE_MS * (1L << Math.min(attempt, 6)), BACKOFF_MAX_MS);
        // Full-ish jitter: half fixed, half random, to avoid a reconnect thundering herd.
        return base / 2 + ThreadLocalRandom.current().nextLong(base / 2 + 1);
    }

    private boolean canResume() {
        return sessionId != null && resumeGatewayUrl != null && lastSequence.get() >= 0;
    }

    private void clearSession() {
        sessionId = null;
        resumeGatewayUrl = null;
        lastSequence.set(-1);
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String str(JsonNode node) {
        return (node == null || node.isMissingNode() || node.isNull()) ? null : node.asString();
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** Bridges JDK {@link WebSocket.Listener} events into the protocol handler, tagged by generation. */
    private final class GatewayListener implements WebSocket.Listener {

        private final long gen;
        /** Confined to this connection's read thread — JDK delivers onText serially per socket. */
        private final StringBuilder buffer = new StringBuilder();

        GatewayListener(long gen) {
            this.gen = gen;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            // Publish the socket BEFORE any frame is delivered (onOpen precedes onText), so the
            // HELLO→sendIdentify path never writes to a null socket (the connect-thread assignment
            // can lose that race). Generation-guarded so a superseded connection can't clobber.
            if (gen == generation.get()) {
                DiscordGatewayClient.this.webSocket = webSocket;
            }
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                buffer.append(data);
                if (last) {
                    String frame = buffer.toString();
                    buffer.setLength(0);
                    handleFrame(frame, gen);
                }
            } catch (Exception e) {
                log.warn("Discord gateway onText failed: {}", e.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // With encoding=json (no zlib) Discord never sends binary frames; ignore.
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("Discord gateway closed: {} {} (gen={})", statusCode, reason, gen);
            if (!shuttingDown) {
                triggerReconnect(GatewayOpcodes.isResumableClose(statusCode) && canResume(), statusCode, gen);
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("Discord gateway socket error (gen={}): {}", gen, error.toString());
            if (!shuttingDown) {
                triggerReconnect(canResume(), 4000, gen);
            }
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
