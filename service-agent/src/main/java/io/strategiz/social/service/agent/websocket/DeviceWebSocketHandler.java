package io.strategiz.social.service.agent.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strategiz.social.business.agent.service.DeviceCommandService;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handles raw WebSocket text messages from devices. Routes JSON messages by type to the appropriate
 * business service (command results, capability updates, status, heartbeat).
 */
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketHandler.class);

	private final ObjectMapper objectMapper;

	private final DeviceSessionManager sessionManager;

	private final DeviceCommandService commandService;

	private final DeviceRegistryService registryService;

	public DeviceWebSocketHandler(DeviceSessionManager sessionManager, DeviceCommandService commandService,
			DeviceRegistryService registryService) {
		this.sessionManager = sessionManager;
		this.commandService = commandService;
		this.registryService = registryService;
		this.objectMapper = new ObjectMapper();
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
		sessionManager.registerSession(session, principal);
		log.info("[WS] Device connected: {} (user: {})", principal.getDeviceId(), principal.getUserId());
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
				case "result" -> handleResult(node, principal);
				case "capabilities" -> handleCapabilities(node, principal);
				case "status" -> handleStatus(node, principal);
				case "ping" -> handlePing(session, principal);
				default -> log.warn("[WS] Unknown message type '{}' from device {}", type, principal.getDeviceId());
			}
		}
		catch (JsonProcessingException ex) {
			log.error("[WS] Failed to parse message from device {}", principal.getDeviceId(), ex);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessionManager.removeSession(session);
	}

	@SuppressWarnings("unchecked")
	private void handleResult(JsonNode node, DevicePrincipal principal) {
		String commandId = node.has("commandId") ? node.get("commandId").asText() : null;
		boolean success = node.has("success") && node.get("success").asBoolean();
		String msg = node.has("message") ? node.get("message").asText() : "";
		Map<String, Object> data = node.has("data") ? objectMapper.convertValue(node.get("data"), Map.class) : null;

		log.info("[WS] Command result from device {}: {} ({})", principal.getDeviceId(), commandId,
				success ? "success" : "failed");
		commandService.reportResult(commandId, success, msg, data);
	}

	@SuppressWarnings("unchecked")
	private void handleCapabilities(JsonNode node, DevicePrincipal principal) {
		Map<String, Object> capabilities = objectMapper.convertValue(node, Map.class);
		capabilities.remove("type");
		log.info("[WS] Capabilities from device {}", principal.getDeviceId());
		registryService.updateCapabilities(principal.getDeviceId(), capabilities);
	}

	@SuppressWarnings("unchecked")
	private void handleStatus(JsonNode node, DevicePrincipal principal) {
		Map<String, Object> status = objectMapper.convertValue(node, Map.class);
		status.remove("type");
		registryService.updateConnectivity(principal.getDeviceId(), status);
		sessionManager.updateHeartbeat(principal.getDeviceId());
	}

	private void handlePing(WebSocketSession session, DevicePrincipal principal) {
		sessionManager.updateHeartbeat(principal.getDeviceId());
		try {
			String pong = objectMapper
				.writeValueAsString(Map.of("type", "pong", "timestamp", System.currentTimeMillis()));
			session.sendMessage(new TextMessage(pong));
		}
		catch (Exception ex) {
			log.error("[WS] Failed to send pong to device {}", principal.getDeviceId(), ex);
		}
	}

}
