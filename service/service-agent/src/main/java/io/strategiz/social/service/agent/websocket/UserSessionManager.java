package io.strategiz.social.service.agent.websocket;

import tools.jackson.databind.json.JsonMapper;
import io.strategiz.social.business.agent.service.UserBroadcaster;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Manages user WebSocket sessions. A single user may have multiple browser tabs/devices connected.
 * Broadcasts spark events to all sessions for a given user.
 */
@Component
public class UserSessionManager implements UserBroadcaster {

	private static final Logger log = LoggerFactory.getLogger(UserSessionManager.class);

	private static final int SEND_TIME_LIMIT = 20_000;

	private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

	private final JsonMapper objectMapper = new JsonMapper();

	/** Maps userId to a set of concurrent-safe WebSocket sessions. */
	private final ConcurrentHashMap<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

	public void registerSession(String userId, WebSocketSession rawSession) {
		WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(rawSession, SEND_TIME_LIMIT,
				BUFFER_SIZE_LIMIT);
		userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(safeSession);
		log.info("[WS-USER] Session registered: user={}, sessions={}", userId,
				userSessions.getOrDefault(userId, Collections.emptySet()).size());
	}

	public void removeSession(String userId, WebSocketSession session) {
		Set<WebSocketSession> sessions = userSessions.get(userId);
		if (sessions != null) {
			sessions.removeIf(s -> s.getId().equals(session.getId()));
			if (sessions.isEmpty()) {
				userSessions.remove(userId);
			}
		}
		log.info("[WS-USER] Session removed: user={}", userId);
	}

	@Override
	public void broadcastToUser(String userId, Map<String, Object> payload) {
		Set<WebSocketSession> sessions = userSessions.get(userId);
		if (sessions == null || sessions.isEmpty()) {
			return;
		}
		try {
			String json = objectMapper.writeValueAsString(payload);
			TextMessage message = new TextMessage(json);
			for (WebSocketSession session : sessions) {
				if (session.isOpen()) {
					try {
						session.sendMessage(message);
					}
					catch (Exception ex) {
						log.error("[WS-USER] Failed to send to user {} session {}", userId, session.getId(), ex);
					}
				}
			}
		}
		catch (Exception ex) {
			log.error("[WS-USER] Failed to serialize payload for user {}", userId, ex);
		}
	}

}
