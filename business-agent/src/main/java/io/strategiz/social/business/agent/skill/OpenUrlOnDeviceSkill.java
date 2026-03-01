package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.client.base.llm.model.ToolDefinition;
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

/** Skill to open a URL on a user's device. Tier 0: auto-execute. */
@Component
public class OpenUrlOnDeviceSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(OpenUrlOnDeviceSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final DeviceCommandService commandService;

	private final DeviceRoutingService routingService;

	public OpenUrlOnDeviceSkill(DeviceCommandService commandService, DeviceRoutingService routingService) {
		this.commandService = commandService;
		this.routingService = routingService;
	}

	@Override
	public String getName() {
		return "open_url_on_device";
	}

	@Override
	public String getDescription() {
		return "Open a URL in the browser on one of the user's connected devices";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode url = properties.putObject("url");
		url.put("type", "string");
		url.put("description", "The URL to open");

		ObjectNode deviceId = properties.putObject("device_id");
		deviceId.put("type", "string");
		deviceId.put("description", "Optional: specific device ID. If omitted, auto-selects best device.");

		schema.putArray("required").add("url");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String url = input.get("url").asText();
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
				Optional<DeviceRegistration> opt = routingService.selectDevice(userId, "browser");
				if (opt.isEmpty()) {
					return "No online device available with browser capability. "
							+ "Make sure a device is connected and online.";
				}
				device = opt.get();
			}

			DeviceCommand cmd = commandService.createCommand(userId, device.getId(), null, CommandType.OPEN_URL,
					Map.of("url", url), getConfirmationTier());

			String result = commandService.awaitResult(cmd.getId());
			return String.format("Opening %s on \"%s\": %s", url, device.getDeviceName(), result);
		}
		catch (Exception e) {
			log.error("Failed to open URL on device for user {}: {}", userId, url, e);
			return "Failed to open URL: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
