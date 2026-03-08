package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.DeviceCommandService;
import io.strategiz.social.business.agent.service.DeviceRoutingService;
import io.strategiz.social.data.entity.CommandType;
import io.strategiz.social.data.entity.DeviceCommand;
import io.strategiz.social.data.entity.DeviceRegistration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to launch an app on a user's device via URL scheme or intent. Tier 1: requires confirmation. */
@Component
public class LaunchAppSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(LaunchAppSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final DeviceCommandService commandService;

	private final DeviceRoutingService routingService;

	public LaunchAppSkill(DeviceCommandService commandService, DeviceRoutingService routingService) {
		this.commandService = commandService;
		this.routingService = routingService;
	}

	@Override
	public String getName() {
		return "launch_app";
	}

	@Override
	public String getDescription() {
		return "Launch an app on the user's device (e.g., Settings, Calendar, Camera). "
				+ "Uses URL schemes on iOS and intents on Android.";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode appName = properties.putObject("app_name");
		appName.put("type", "string");
		appName.put("description",
				"The name of the app to launch (e.g., 'Settings', 'Calendar', 'Camera', 'Maps')");

		ObjectNode deviceId = properties.putObject("device_id");
		deviceId.put("type", "string");
		deviceId.put("description", "Optional: specific device ID. If omitted, auto-selects best device.");

		schema.putArray("required").add("app_name");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String appName = input.get("app_name").asText();
		String targetDeviceId = input.has("device_id") ? input.get("device_id").asText() : null;

		try {
			DeviceRegistration device;
			if (targetDeviceId != null) {
				Optional<DeviceRegistration> opt = routingService.getOnlineDevice(targetDeviceId, userId);
				if (opt.isEmpty()) {
					return "Device not found or offline. Use list_devices to see available devices.";
				}
				device = opt.get();
			}
			else {
				Optional<DeviceRegistration> opt = routingService.selectDevice(userId, null);
				if (opt.isEmpty()) {
					return "No online device available. Make sure a device is connected.";
				}
				device = opt.get();
			}

			Map<String, Object> payload = new HashMap<>();
			payload.put("appName", appName);

			DeviceCommand cmd = commandService.createCommand(userId, device.getId(), null, CommandType.LAUNCH_APP,
					payload, getConfirmationTier());

			String result = commandService.awaitResult(cmd.getId());
			return String.format("Launching %s on \"%s\": %s", appName, device.getDeviceName(), result);
		}
		catch (Exception e) {
			log.error("Failed to launch app on device for user {}: {}", userId, appName, e);
			return "Failed to launch app: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
