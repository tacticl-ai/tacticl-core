package io.strategiz.social.service.agent.websocket;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.strategiz.social.business.agent.service.CredentialService;
import io.strategiz.social.business.agent.service.DeviceCommandService;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.SparkState;
import io.strategiz.social.data.entity.TacticState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handles raw WebSocket text messages from devices. Routes JSON messages by type to the appropriate
 * business service (command results, capability updates, status, heartbeat, credential requests).
 */
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketHandler.class);

	private final JsonMapper objectMapper;

	private final DeviceSessionManager sessionManager;

	private final DeviceCommandService commandService;

	private final DeviceRegistryService registryService;

	private final SparkService sparkService;

	private final CredentialService credentialService;

	public DeviceWebSocketHandler(DeviceSessionManager sessionManager, DeviceCommandService commandService,
			DeviceRegistryService registryService, SparkService sparkService,
			CredentialService credentialService) {
		this.sessionManager = sessionManager;
		this.commandService = commandService;
		this.registryService = registryService;
		this.sparkService = sparkService;
		this.credentialService = credentialService;
		this.objectMapper = JsonMapper.builder()
			.disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		WebSocketPrincipal principal = (WebSocketPrincipal) session.getAttributes().get("principal");
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
		WebSocketPrincipal principal = (WebSocketPrincipal) session.getAttributes().get("principal");
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
				case "spark_accepted" -> handleSparkAccepted(node, principal);
				case "spark_progress" -> handleSparkProgress(node, principal);
				case "spark_checkpoint" -> handleSparkCheckpoint(node, principal);
				case "spark_completed" -> handleSparkCompleted(node, principal);
				case "spark_failed" -> handleSparkFailed(node, principal);
				case "credentials_request" -> handleCredentialsRequest(node, session, principal);
				default -> log.warn("[WS] Unknown message type '{}' from device {}", type, principal.getDeviceId());
			}
		}
		catch (JacksonException ex) {
			log.error("[WS] Failed to parse message from device {}", principal.getDeviceId(), ex);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessionManager.removeSession(session);
	}

	@SuppressWarnings("unchecked")
	private void handleResult(JsonNode node, WebSocketPrincipal principal) {
		String commandId = node.has("commandId") ? node.get("commandId").asText() : null;
		boolean success = node.has("success") && node.get("success").asBoolean();
		String msg = node.has("message") ? node.get("message").asText() : "";
		Map<String, Object> data = node.has("data") ? objectMapper.convertValue(node.get("data"), Map.class) : null;

		log.info("[WS] Command result from device {}: {} ({})", principal.getDeviceId(), commandId,
				success ? "success" : "failed");
		commandService.reportResult(commandId, success, msg, data);
	}

	@SuppressWarnings("unchecked")
	private void handleCapabilities(JsonNode node, WebSocketPrincipal principal) {
		Map<String, Object> capabilities = objectMapper.convertValue(node, Map.class);
		capabilities.remove("type");
		log.info("[WS] Capabilities from device {}", principal.getDeviceId());
		registryService.updateCapabilities(principal.getDeviceId(), principal.getUserId(), capabilities);
	}

	@SuppressWarnings("unchecked")
	private void handleStatus(JsonNode node, WebSocketPrincipal principal) {
		Map<String, Object> status = objectMapper.convertValue(node, Map.class);
		status.remove("type");
		registryService.updateConnectivity(principal.getDeviceId(), principal.getUserId(), status);
		sessionManager.updateHeartbeat(principal.getDeviceId());
	}

	private void handlePing(WebSocketSession session, WebSocketPrincipal principal) {
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

	private void handleSparkAccepted(JsonNode node, WebSocketPrincipal principal) {
		String sparkId = node.has("sparkId") ? node.get("sparkId").asText() : null;
		if (sparkId == null) {
			log.warn("[WS] spark_accepted missing sparkId from device {}", principal.getDeviceId());
			return;
		}
		log.info("[WS] Spark accepted: spark={} device={}", sparkId, principal.getDeviceId());
		sparkService.onSparkProgress(sparkId, SparkState.EXECUTING, 0);
	}

	@SuppressWarnings("unchecked")
	private void handleSparkProgress(JsonNode node, WebSocketPrincipal principal) {
		String sparkId = node.has("sparkId") ? node.get("sparkId").asText() : null;
		if (sparkId == null) {
			log.warn("[WS] spark_progress missing sparkId from device {}", principal.getDeviceId());
			return;
		}

		long tokensDelta = node.has("tokensDelta") ? node.get("tokensDelta").asLong() : 0;
		String statusStr = node.has("status") ? node.get("status").asText() : null;
		SparkState status = parseSparkState(statusStr, SparkState.EXECUTING);

		sparkService.onSparkProgress(sparkId, status, tokensDelta);

		if (node.has("tactics") && node.get("tactics").isArray()) {
			for (JsonNode tacticNode : node.get("tactics")) {
				String tacticId = tacticNode.has("tacticId") ? tacticNode.get("tacticId").asText() : null;
				if (tacticId == null) {
					continue;
				}
				String description = tacticNode.has("description") ? tacticNode.get("description").asText() : null;
				String tacticStatusStr = tacticNode.has("status") ? tacticNode.get("status").asText() : null;
				TacticState tacticState = parseTacticState(tacticStatusStr, TacticState.EXECUTING);
				long tacticTokens = tacticNode.has("tokenUsage") ? tacticNode.get("tokenUsage").asLong() : 0;

				sparkService.syncTactic(sparkId, tacticId, principal.getDeviceId(), description, tacticState,
						tacticTokens);
			}
		}

		log.debug("[WS] Spark progress: spark={} status={} tokens={}", sparkId, status, tokensDelta);
	}

	@SuppressWarnings("unchecked")
	private void handleSparkCheckpoint(JsonNode node, WebSocketPrincipal principal) {
		String sparkId = node.has("sparkId") ? node.get("sparkId").asText() : null;
		if (sparkId == null) {
			log.warn("[WS] spark_checkpoint missing sparkId from device {}", principal.getDeviceId());
			return;
		}

		String tacticId = node.has("tacticId") ? node.get("tacticId").asText() : null;
		String title = node.has("title") ? node.get("title").asText() : "Checkpoint";
		String description = node.has("description") ? node.get("description").asText() : "";

		List<Map<String, Object>> findings = null;
		if (node.has("findings") && node.get("findings").isArray()) {
			findings = objectMapper.convertValue(node.get("findings"),
					objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
		}

		List<String> options = null;
		if (node.has("options") && node.get("options").isArray()) {
			options = objectMapper.convertValue(node.get("options"),
					objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
		}

		sparkService.onSparkCheckpoint(sparkId, tacticId, title, description, findings, options);
		log.info("[WS] Spark checkpoint: spark={} title={}", sparkId, title);
	}

	@SuppressWarnings("unchecked")
	private void handleSparkCompleted(JsonNode node, WebSocketPrincipal principal) {
		String sparkId = node.has("sparkId") ? node.get("sparkId").asText() : null;
		if (sparkId == null) {
			log.warn("[WS] spark_completed missing sparkId from device {}", principal.getDeviceId());
			return;
		}

		Map<String, Object> result = null;
		if (node.has("result")) {
			result = objectMapper.convertValue(node.get("result"), Map.class);
		}
		long totalTokens = node.has("totalTokens") ? node.get("totalTokens").asLong() : 0;

		sparkService.onSparkCompleted(sparkId, result, totalTokens);
		log.info("[WS] Spark completed: spark={} tokens={}", sparkId, totalTokens);
	}

	private void handleSparkFailed(JsonNode node, WebSocketPrincipal principal) {
		String sparkId = node.has("sparkId") ? node.get("sparkId").asText() : null;
		if (sparkId == null) {
			log.warn("[WS] spark_failed missing sparkId from device {}", principal.getDeviceId());
			return;
		}

		String error = node.has("error") ? node.get("error").asText() : "Unknown error";
		sparkService.onSparkFailed(sparkId, error);
		log.info("[WS] Spark failed: spark={} error={}", sparkId, error);
	}

	private void handleCredentialsRequest(JsonNode node, WebSocketSession session, WebSocketPrincipal principal) {
		String platform = node.has("platform") ? node.get("platform").asText() : null;
		String sparkId = node.has("sparkId") ? node.get("sparkId").asText() : null;
		String requestId = node.has("requestId") ? node.get("requestId").asText() : null;

		if (platform == null || sparkId == null) {
			log.warn("[WS] credentials_request missing fields from device {}", principal.getDeviceId());
			return;
		}

		// Verify device owns the spark and is assigned to it
		Optional<Spark> sparkOpt = sparkService.getSparkInternal(sparkId);
		if (sparkOpt.isEmpty() || !sparkOpt.get().getUserId().equals(principal.getUserId())) {
			log.warn("[WS] credentials_request denied: device {} not authorized for spark {}", principal.getDeviceId(),
					sparkId);
			sendCredentialsError(session, requestId, "Not authorized for this spark");
			return;
		}

		// Verify that the requesting device is actually assigned to this spark
		Spark spark = sparkOpt.get();
		if (spark.getDeviceId() != null && !spark.getDeviceId().equals(principal.getDeviceId())) {
			log.warn("[WS] credentials_request denied: device {} is not assigned to spark {} (assigned to {})",
					principal.getDeviceId(), sparkId, spark.getDeviceId());
			sendCredentialsError(session, requestId, "Device not assigned to this spark");
			return;
		}

		Optional<Map<String, Object>> credentials = credentialService.getCredentials(principal.getUserId(), platform);
		try {
			Map<String, Object> response = new HashMap<>();
			response.put("type", "credentials_response");
			response.put("requestId", requestId);
			if (credentials.isPresent()) {
				response.put("credentials", credentials.get());
				response.put("success", true);
			}
			else {
				response.put("success", false);
				response.put("error", "No credentials found for platform: " + platform);
			}
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
			log.info("[WS] Credentials served for platform={} device={}", platform, principal.getDeviceId());
		}
		catch (Exception ex) {
			log.error("[WS] Failed to send credentials response to device {}", principal.getDeviceId(), ex);
		}
	}

	private void sendCredentialsError(WebSocketSession session, String requestId, String error) {
		try {
			Map<String, Object> response = Map.of("type", "credentials_response", "requestId",
					requestId != null ? requestId : "", "success", false, "error", error);
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
		}
		catch (Exception ex) {
			log.error("[WS] Failed to send credentials error", ex);
		}
	}

	private SparkState parseSparkState(String value, SparkState defaultState) {
		if (value == null) {
			return defaultState;
		}
		try {
			return SparkState.valueOf(value.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return defaultState;
		}
	}

	private TacticState parseTacticState(String value, TacticState defaultState) {
		if (value == null) {
			return defaultState;
		}
		try {
			return TacticState.valueOf(value.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return defaultState;
		}
	}

}
