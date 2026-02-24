package io.strategiz.social.service.agent.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.CheckpointDecision;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handles WebSocket connections from user browser/mobile clients. Receives checkpoint decisions
 * and heartbeats. Spark events are pushed via {@link UserSessionManager}.
 */
@Component
public class UserWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(UserWebSocketHandler.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final UserSessionManager userSessionManager;

	private final SparkService sparkService;

	public UserWebSocketHandler(UserSessionManager userSessionManager, SparkService sparkService) {
		this.userSessionManager = userSessionManager;
		this.sparkService = sparkService;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		DevicePrincipal principal = (DevicePrincipal) session.getAttributes().get("principal");
		if (principal == null) {
			try {
				session.close(CloseStatus.POLICY_VIOLATION);
			}
			catch (Exception ignored) {
			}
			return;
		}
		userSessionManager.registerSession(principal.getUserId(), session);
		log.info("[WS-USER] User connected: {}", principal.getUserId());
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		DevicePrincipal principal = (DevicePrincipal) session.getAttributes().get("principal");
		if (principal == null) {
			return;
		}

		try {
			JsonNode node = objectMapper.readTree(message.getPayload());
			String type = node.has("type") ? node.get("type").asText() : "";

			switch (type) {
				case "checkpoint_decision" -> handleCheckpointDecision(node, principal);
				case "ping" -> handlePing(session);
				default -> log.warn("[WS-USER] Unknown message type '{}' from user {}", type, principal.getUserId());
			}
		}
		catch (Exception ex) {
			log.error("[WS-USER] Failed to parse message from user {}", principal.getUserId(), ex);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		DevicePrincipal principal = (DevicePrincipal) session.getAttributes().get("principal");
		if (principal != null) {
			userSessionManager.removeSession(principal.getUserId(), session);
		}
	}

	private void handleCheckpointDecision(JsonNode node, DevicePrincipal principal) {
		String checkpointId = node.has("checkpointId") ? node.get("checkpointId").asText() : null;
		String decisionStr = node.has("decision") ? node.get("decision").asText() : null;
		String feedback = node.has("feedback") ? node.get("feedback").asText() : null;

		if (checkpointId == null || decisionStr == null) {
			log.warn("[WS-USER] checkpoint_decision missing fields from user {}", principal.getUserId());
			return;
		}

		try {
			CheckpointDecision decision = CheckpointDecision.valueOf(decisionStr.toUpperCase());
			sparkService.decideCheckpoint(checkpointId, principal.getUserId(), decision, feedback);
			log.info("[WS-USER] Checkpoint decided: {} = {} by user {}", checkpointId, decision,
					principal.getUserId());
		}
		catch (IllegalArgumentException ex) {
			log.warn("[WS-USER] Invalid decision '{}' from user {}", decisionStr, principal.getUserId());
		}
	}

	private void handlePing(WebSocketSession session) {
		try {
			String pong = objectMapper
				.writeValueAsString(Map.of("type", "pong", "timestamp", System.currentTimeMillis()));
			session.sendMessage(new TextMessage(pong));
		}
		catch (Exception ex) {
			log.error("[WS-USER] Failed to send pong", ex);
		}
	}

}
