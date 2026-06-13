package io.tacticl.service.voice.ws;

import io.tacticl.business.voice.VoiceSession;
import io.tacticl.business.voice.VoiceSessionService;
import io.tacticl.service.voice.token.VoiceSessionTokenService;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The voice command-center WebSocket transport at {@code /v1/voice}.
 *
 * <p>One socket multiplexes two channels (mirroring
 * {@code tacticl-web/src/voice/protocol.ts}):
 * <ul>
 *   <li>UP BINARY — 16 kHz mono PCM16 mic chunks; forwarded straight into the
 *       Deepgram STT leg via {@code VoiceSessionService.pushAudio}.</li>
 *   <li>UP TEXT — JSON control frames {@code start | stop | barge_in | decision},
 *       dispatched onto the corresponding {@code VoiceSessionService} method.</li>
 *   <li>DOWN BINARY/TEXT — TTS audio + state/transcript/hud/checkpoint/error
 *       frames, written by {@link WebSocketVoiceOutbound}.</li>
 * </ul>
 *
 * <p>Auth: the browser can't set an {@code Authorization} header on a WebSocket,
 * so the access PASETO is exchanged for a short-lived voice session token at
 * {@code POST /v1/voice/token} first. This handler validates only that token
 * from the {@code ?token=} query param at {@link #afterConnectionEstablished}; an
 * absent/invalid/expired token closes the socket with {@code 1008 NOT_ACCEPTABLE}
 * before any session is created.
 *
 * <p>Lifecycle: handshake validated → {@code openSession} builds the STT/TTS
 * bridges + binds a {@link WebSocketVoiceOutbound} → the turn loop runs on the
 * business side → {@code afterConnectionClosed} tears the session down and
 * invalidates the token so it can't be replayed within its TTL.
 *
 * <p>Gated by {@code tacticl.voice.enabled=true}; with the flag off the handler
 * bean is never created and {@code /v1/voice} is unregistered.
 */
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceWebSocketHandler.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /** Attribute keys stashed on the WS session for teardown. */
    private static final String ATTR_VOICE_SESSION = "voiceSession";

    private static final String ATTR_TOKEN = "voiceToken";

    private final VoiceSessionService sessionService;

    private final VoiceSessionTokenService tokenService;

    /** Live WS-session-id → bound business VoiceSession, so handlers route by socket. */
    private final Map<String, VoiceSession> sessions = new ConcurrentHashMap<>();

    public VoiceWebSocketHandler(VoiceSessionService sessionService,
                                 VoiceSessionTokenService tokenService) {
        this.sessionService = sessionService;
        this.tokenService = tokenService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        Optional<String> userId = tokenService.resolveUserId(token);
        if (userId.isEmpty()) {
            log.info("Voice WS rejected (invalid/expired token) id={}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid voice token"));
            return;
        }

        // Optional ?cid=<conversationId> resumes a prior conversation (ownership is
        // re-checked server-side); absent ⇒ a fresh conversation is created.
        String conversationId = extractQueryParam(session, "cid");
        VoiceSession voiceSession = sessionService.openSession(
            userId.get(), conversationId, new WebSocketVoiceOutbound(session));
        session.getAttributes().put(ATTR_VOICE_SESSION, voiceSession);
        session.getAttributes().put(ATTR_TOKEN, token);
        sessions.put(session.getId(), voiceSession);
        log.info("Voice WS established id={} user={}", session.getId(), userId.get());
    }

    /** UP BINARY — raw mic PCM. Forward straight through to the STT leg. */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        VoiceSession voiceSession = sessions.get(session.getId());
        if (voiceSession == null) {
            return;
        }
        ByteBuffer payload = message.getPayload();
        byte[] pcm = new byte[payload.remaining()];
        payload.get(pcm);
        sessionService.pushAudio(voiceSession, pcm);
    }

    /** UP TEXT — a JSON control frame: start | stop | barge_in | decision. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        VoiceSession voiceSession = sessions.get(session.getId());
        if (voiceSession == null) {
            return;
        }
        Map<String, Object> frame = parse(message.getPayload());
        if (frame == null) {
            log.debug("Voice WS ignored malformed control frame id={}", session.getId());
            return;
        }
        String type = asString(frame.get("type"));
        if (type == null) {
            return;
        }
        switch (type) {
            case "start" -> sessionService.startTurn(voiceSession);
            case "stop" -> sessionService.stopTurn(voiceSession);
            case "text" -> sessionService.handleTypedText(voiceSession, asString(frame.get("text")));
            case "barge_in" -> sessionService.bargeIn(voiceSession);
            case "decision" -> sessionService.submitDecision(
                voiceSession,
                asString(frame.get("checkpointId")),
                asString(frame.get("decision")),
                asString(frame.get("feedback")));
            default -> log.debug("Voice WS ignored unknown control type '{}' id={}", type, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        VoiceSession voiceSession = sessions.remove(session.getId());
        if (voiceSession != null) {
            sessionService.closeSession(voiceSession);
        }
        Object token = session.getAttributes().get(ATTR_TOKEN);
        if (token instanceof String t) {
            tokenService.invalidate(t);
        }
        log.info("Voice WS closed id={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Voice WS transport error id={}: {}", session.getId(), exception.toString());
    }

    private static String extractToken(WebSocketSession session) {
        return extractQueryParam(session, "token");
    }

    private static String extractQueryParam(WebSocketSession session, String key) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && key.equals(pair.substring(0, eq))) {
                return java.net.URLDecoder.decode(
                    pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static Map<String, Object> parse(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
