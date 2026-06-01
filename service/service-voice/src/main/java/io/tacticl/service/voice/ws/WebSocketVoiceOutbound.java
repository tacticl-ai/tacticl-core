package io.tacticl.service.voice.ws;

import io.tacticl.business.voice.VoiceOutbound;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@link VoiceOutbound} implementation that writes back over a live
 * {@link WebSocketSession}. This is the transport adapter the business layer's
 * transport-neutral {@code VoiceSessionService} pushes through:
 *
 * <ul>
 *   <li>{@link #sendAudio(byte[])} → a {@link BinaryMessage} (TTS audio chunk).</li>
 *   <li>{@link #sendControl(Map)} → a {@link TextMessage} of the JSON-serialized
 *       control frame produced by {@code VoiceFrames}.</li>
 * </ul>
 *
 * <p>{@code WebSocketSession.sendMessage} is NOT thread-safe and the business
 * layer calls this from arbitrary executor threads (STT/TTS client callbacks and
 * the pipeline-event emitter), so every send is synchronized on this adapter and
 * guarded by an {@code isOpen()} check. A failed send is logged and swallowed —
 * a dropped audio chunk or control frame must never propagate back into the turn
 * loop and tear down the session.
 */
public class WebSocketVoiceOutbound implements VoiceOutbound {

    private static final Logger log = LoggerFactory.getLogger(WebSocketVoiceOutbound.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final WebSocketSession session;

    public WebSocketVoiceOutbound(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void sendAudio(byte[] audio) {
        if (audio == null || audio.length == 0) {
            return;
        }
        send(new BinaryMessage(ByteBuffer.wrap(audio)));
    }

    @Override
    public void sendControl(Map<String, Object> frame) {
        if (frame == null) {
            return;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(frame);
        } catch (RuntimeException e) {
            log.warn("Failed to serialize voice control frame {}: {}", frame.get("type"), e.toString());
            return;
        }
        send(new TextMessage(json));
    }

    private synchronized void send(org.springframework.web.socket.WebSocketMessage<?> message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            log.debug("Voice WS send failed (session likely closing) id={}: {}",
                session.getId(), e.toString());
        }
    }
}
