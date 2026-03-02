package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.DeviceCommandService;
import io.strategiz.social.business.agent.service.DeviceRoutingService;
import io.strategiz.social.data.entity.CommandType;
import io.strategiz.social.data.entity.DeviceCommand;
import io.strategiz.social.data.entity.DeviceRegistration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to capture a screenshot from a user's device. Tier 0: auto-execute. */
@Component
public class TakeScreenshotSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(TakeScreenshotSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final DeviceCommandService commandService;

	private final DeviceRoutingService routingService;

	public TakeScreenshotSkill(DeviceCommandService commandService, DeviceRoutingService routingService) {
		this.commandService = commandService;
		this.routingService = routingService;
	}

	@Override
	public String getName() {
		return "take_screenshot";
	}

	@Override
	public String getDescription() {
		return "Capture a screenshot of the current screen on a user's device";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode deviceId = properties.putObject("device_id");
		deviceId.put("type", "string");
		deviceId.put("description", "Optional: specific device ID. If omitted, auto-selects best device.");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
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
				Optional<DeviceRegistration> opt = routingService.selectDevice(userId, "screen");
				if (opt.isEmpty()) {
					return "No online device available with screenshot capability.";
				}
				device = opt.get();
			}

			DeviceCommand cmd = commandService.createCommand(userId, device.getId(), null, CommandType.TAKE_SCREENSHOT,
					Map.of(), getConfirmationTier());

			String result = commandService.awaitResult(cmd.getId());
			return String.format("Screenshot from \"%s\": %s", device.getDeviceName(), result);
		}
		catch (Exception e) {
			log.error("Failed to take screenshot on device for user {}", userId, e);
			return "Failed to take screenshot: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
