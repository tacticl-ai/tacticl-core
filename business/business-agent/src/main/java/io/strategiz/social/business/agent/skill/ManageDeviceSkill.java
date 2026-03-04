package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.DevicePairingService;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceSettings;
import io.strategiz.social.data.repository.DeviceRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to pair, unpair, list, or update settings for user devices. Tier 1: mutations require confirmation. */
@Component
public class ManageDeviceSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(ManageDeviceSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final DevicePairingService devicePairingService;

	private final DeviceRepository deviceRepository;

	private final DeviceRegistryService deviceRegistryService;

	public ManageDeviceSkill(DevicePairingService devicePairingService, DeviceRepository deviceRepository,
			DeviceRegistryService deviceRegistryService) {
		this.devicePairingService = devicePairingService;
		this.deviceRepository = deviceRepository;
		this.deviceRegistryService = deviceRegistryService;
	}

	@Override
	public String getName() {
		return "manage_device";
	}

	@Override
	public String getDescription() {
		return "Pair a new device, unpair a device, or update device settings (max daemons, auto wake, priority)";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode action = properties.putObject("action");
		action.put("type", "string");
		action.put("description", "Action to perform: 'pair' to generate a pairing code, 'unpair' to deactivate a device, 'update_settings' to change device settings, 'list' to list devices");
		action.putArray("enum").add("pair").add("unpair").add("update_settings").add("list");

		ObjectNode deviceId = properties.putObject("device_id");
		deviceId.put("type", "string");
		deviceId.put("description", "Device ID (required for unpair and update_settings)");

		ObjectNode maxDaemons = properties.putObject("max_daemons");
		maxDaemons.put("type", "integer");
		maxDaemons.put("description", "Maximum number of concurrent daemons on the device (for update_settings)");

		ObjectNode autoWake = properties.putObject("auto_wake");
		autoWake.put("type", "boolean");
		autoWake.put("description", "Whether to auto-wake the device for incoming sparks (for update_settings)");

		ObjectNode priority = properties.putObject("priority");
		priority.put("type", "integer");
		priority.put("description", "Device routing priority — higher values receive sparks first (for update_settings)");

		schema.putArray("required").add("action");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String action = input.get("action").asText();

		return switch (action) {
			case "pair" -> handlePair(userId);
			case "unpair" -> handleUnpair(input, userId);
			case "update_settings" -> handleUpdateSettings(input, userId);
			case "list" -> handleList(userId);
			default -> "Unknown action: " + action + ". Use 'pair', 'unpair', 'update_settings', or 'list'.";
		};
	}

	private String handlePair(String userId) {
		try {
			var pairingCode = devicePairingService.generatePairingCode(userId);
			return "Pairing code: " + pairingCode.getCode()
					+ ". Enter this in the Tacticl app on your device within 5 minutes.";
		}
		catch (Exception e) {
			log.error("Failed to generate pairing code for user {}", userId, e);
			return "Failed to generate pairing code: " + e.getMessage();
		}
	}

	private String handleUnpair(JsonNode input, String userId) {
		if (!input.has("device_id") || input.get("device_id").asText().isBlank()) {
			return "device_id is required to unpair a device.";
		}

		String deviceId = input.get("device_id").asText();
		try {
			boolean revoked = deviceRegistryService.revokeDevice(deviceId, userId);
			if (!revoked) {
				return "Device not found or already unpaired: " + deviceId;
			}
			log.info("Device unpaired via agent skill: device={} user={}", deviceId, userId);
			return "Device " + deviceId + " has been unpaired and deactivated.";
		}
		catch (Exception e) {
			log.error("Failed to unpair device {} for user {}", deviceId, userId, e);
			return "Failed to unpair device: " + e.getMessage();
		}
	}

	private String handleUpdateSettings(JsonNode input, String userId) {
		if (!input.has("device_id") || input.get("device_id").asText().isBlank()) {
			return "device_id is required to update device settings.";
		}

		String deviceId = input.get("device_id").asText();
		try {
			Optional<DeviceRegistration> opt = deviceRepository.findByIdInSubcollection(userId, deviceId);
			if (opt.isEmpty()) {
				return "Device not found: " + deviceId;
			}

			DeviceRegistration device = opt.get();
			DeviceSettings settings = device.getSettings();
			if (settings == null) {
				settings = DeviceSettings.defaults();
			}

			boolean updated = false;
			if (input.has("max_daemons")) {
				settings.setMaxDaemons(input.get("max_daemons").asInt());
				updated = true;
			}
			if (input.has("auto_wake")) {
				settings.setAutoWake(input.get("auto_wake").asBoolean());
				updated = true;
			}
			if (input.has("priority")) {
				settings.setPriority(input.get("priority").asInt());
				updated = true;
			}

			if (!updated) {
				return "No settings fields provided. Specify at least one of: max_daemons, auto_wake, priority.";
			}

			device.setSettings(settings);
			deviceRepository.saveInSubcollection(userId, device, userId);

			log.info("Device settings updated via agent skill: device={} user={}", deviceId, userId);
			return "Device settings updated for " + device.getDeviceName() + ":\n"
					+ "- Max daemons: " + settings.getMaxDaemons() + "\n"
					+ "- Auto wake: " + settings.isAutoWake() + "\n"
					+ "- Priority: " + settings.getPriority();
		}
		catch (Exception e) {
			log.error("Failed to update device settings for device {} user {}", deviceId, userId, e);
			return "Failed to update device settings: " + e.getMessage();
		}
	}

	private String handleList(String userId) {
		try {
			List<DeviceRegistration> devices = deviceRegistryService.listDevices(userId);
			if (devices.isEmpty()) {
				return "You don't have any active devices. Use the 'pair' action to register a new device.";
			}

			StringBuilder sb = new StringBuilder("Your devices:\n");
			for (DeviceRegistration device : devices) {
				sb.append("- ").append(device.getDeviceName())
						.append(" (").append(device.getDeviceType()).append(")")
						.append(" [ID: ").append(device.getId()).append("]")
						.append(" — state: ").append(device.getState());
				if (device.getSettings() != null) {
					DeviceSettings s = device.getSettings();
					sb.append(", max_daemons=").append(s.getMaxDaemons())
							.append(", auto_wake=").append(s.isAutoWake())
							.append(", priority=").append(s.getPriority());
				}
				sb.append("\n");
			}
			return sb.toString().trim();
		}
		catch (Exception e) {
			log.error("Failed to list devices for user {}", userId, e);
			return "Failed to list devices: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
