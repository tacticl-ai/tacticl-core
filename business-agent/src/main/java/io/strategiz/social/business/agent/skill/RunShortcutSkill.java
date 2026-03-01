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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to execute an iOS Shortcut on a user's device. Tier 1: requires confirmation. */
@Component
public class RunShortcutSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(RunShortcutSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final DeviceCommandService commandService;

	private final DeviceRoutingService routingService;

	public RunShortcutSkill(DeviceCommandService commandService, DeviceRoutingService routingService) {
		this.commandService = commandService;
		this.routingService = routingService;
	}

	@Override
	public String getName() {
		return "run_shortcut";
	}

	@Override
	public String getDescription() {
		return "Execute an iOS Shortcut by name on the user's iPhone or iPad";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode shortcutName = properties.putObject("shortcut_name");
		shortcutName.put("type", "string");
		shortcutName.put("description", "The name of the iOS Shortcut to run");

		ObjectNode inputText = properties.putObject("input_text");
		inputText.put("type", "string");
		inputText.put("description", "Optional: text input to pass to the Shortcut");

		ObjectNode deviceId = properties.putObject("device_id");
		deviceId.put("type", "string");
		deviceId.put("description", "Optional: specific device ID. If omitted, auto-selects best iOS device.");

		schema.putArray("required").add("shortcut_name");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String shortcutName = input.get("shortcut_name").asText();
		String inputText = input.has("input_text") ? input.get("input_text").asText() : null;
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
				Optional<DeviceRegistration> opt = routingService.selectDevice(userId, "shortcuts");
				if (opt.isEmpty()) {
					return "No online device available with Shortcuts capability. "
							+ "Make sure an iOS device is connected and online.";
				}
				device = opt.get();
			}

			Map<String, Object> payload = new HashMap<>();
			payload.put("shortcutName", shortcutName);
			if (inputText != null) {
				payload.put("inputText", inputText);
			}

			DeviceCommand cmd = commandService.createCommand(userId, device.getId(), null, CommandType.RUN_SHORTCUT,
					payload, getConfirmationTier());

			String result = commandService.awaitResult(cmd.getId());
			return String.format("Running Shortcut \"%s\" on \"%s\": %s", shortcutName, device.getDeviceName(), result);
		}
		catch (Exception e) {
			log.error("Failed to run shortcut on device for user {}: {}", userId, shortcutName, e);
			return "Failed to run shortcut: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
