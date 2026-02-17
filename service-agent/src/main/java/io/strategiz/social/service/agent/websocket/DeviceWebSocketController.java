package io.strategiz.social.service.agent.websocket;

import io.strategiz.social.business.agent.service.DeviceCommandService;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * STOMP message handler for device-to-server communication. Devices send command results,
 * capability updates, and status reports here.
 */
@Controller
public class DeviceWebSocketController {

	private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketController.class);

	private final DeviceCommandService commandService;

	private final DeviceRegistryService registryService;

	private final DeviceSessionManager sessionManager;

	public DeviceWebSocketController(DeviceCommandService commandService, DeviceRegistryService registryService,
			DeviceSessionManager sessionManager) {
		this.commandService = commandService;
		this.registryService = registryService;
		this.sessionManager = sessionManager;
	}

	/** Device reports a command execution result. */
	@MessageMapping("/device/result")
	public void handleCommandResult(@Payload Map<String, Object> payload, DevicePrincipal principal) {
		String commandId = (String) payload.get("commandId");
		boolean success = Boolean.TRUE.equals(payload.get("success"));
		String message = (String) payload.get("message");

		@SuppressWarnings("unchecked")
		Map<String, Object> resultData = (Map<String, Object>) payload.get("data");

		log.info("Command result from device {}: {} ({})", principal.getDeviceId(), commandId,
				success ? "success" : "failed");

		commandService.reportResult(commandId, success, message, resultData);
	}

	/** Device reports its capability manifest (on connect or capability change). */
	@MessageMapping("/device/capabilities")
	public void handleCapabilities(@Payload Map<String, Object> capabilities, DevicePrincipal principal) {
		log.info("Capabilities update from device {}: {} capabilities", principal.getDeviceId(), capabilities.size());
		registryService.updateCapabilities(principal.getDeviceId(), capabilities);
	}

	/** Device reports connectivity status (battery, network). */
	@MessageMapping("/device/status")
	public void handleStatus(@Payload Map<String, Object> status, DevicePrincipal principal) {
		log.debug("Status update from device {}", principal.getDeviceId());
		registryService.updateConnectivity(principal.getDeviceId(), status);
		sessionManager.updateHeartbeat(principal.getDeviceId());
	}

	/** Device sends a heartbeat ping. */
	@MessageMapping("/device/ping")
	@SendToUser("/queue/heartbeat")
	public Map<String, Object> handlePing(DevicePrincipal principal) {
		sessionManager.updateHeartbeat(principal.getDeviceId());
		return Map.of("type", "pong", "timestamp", System.currentTimeMillis());
	}

}
