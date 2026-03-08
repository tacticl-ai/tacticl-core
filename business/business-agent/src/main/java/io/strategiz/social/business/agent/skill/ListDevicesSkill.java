package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.DeviceRoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to list user's registered devices and their status. Tier 0: auto-execute. */
@Component
public class ListDevicesSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(ListDevicesSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final DeviceRoutingService deviceRoutingService;

	public ListDevicesSkill(DeviceRoutingService deviceRoutingService) {
		this.deviceRoutingService = deviceRoutingService;
	}

	@Override
	public String getName() {
		return "list_devices";
	}

	@Override
	public String getDescription() {
		return "List the user's registered devices and their online status, battery level, and capabilities";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		schema.putObject("properties");
		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		try {
			String context = deviceRoutingService.buildDeviceContext(userId);
			if (context.contains("No devices registered")) {
				return "You don't have any devices registered yet. "
						+ "Open the Tacticl mobile app or desktop agent to register a device.";
			}
			return "Your devices:\n" + context;
		}
		catch (Exception e) {
			log.error("Failed to list devices for user {}", userId, e);
			return "Failed to retrieve devices: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
