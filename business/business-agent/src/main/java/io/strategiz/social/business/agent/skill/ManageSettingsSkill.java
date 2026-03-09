package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.UserConfigService;
import io.strategiz.social.data.entity.UserConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to read or update user configuration settings. Tier 1: mutations require confirmation. */
@Component
public class ManageSettingsSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(ManageSettingsSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final UserConfigService userConfigService;

	public ManageSettingsSkill(UserConfigService userConfigService) {
		this.userConfigService = userConfigService;
	}

	@Override
	public String getName() {
		return "manage_settings";
	}

	@Override
	public String getDescription() {
		return "View or update user settings: max concurrent sparks, spending limit, domain allowlist/blocklist, and confirmation overrides";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode action = properties.putObject("action");
		action.put("type", "string");
		action.put("description", "Action to perform: 'get' to view current settings, 'update' to change settings");
		action.putArray("enum").add("get").add("update");

		ObjectNode maxConcurrentSparks = properties.putObject("max_concurrent_sparks");
		maxConcurrentSparks.put("type", "integer");
		maxConcurrentSparks.put("description", "Maximum number of sparks that can run simultaneously (for update action)");

		ObjectNode spendingLimit = properties.putObject("spending_limit");
		spendingLimit.put("type", "string");
		spendingLimit.put("description", "Maximum spending amount in dollars (for update action, e.g. '50.00')");

		ObjectNode domainAllowlist = properties.putObject("domain_allowlist");
		domainAllowlist.put("type", "array");
		domainAllowlist.put("description", "List of allowed domains the agent can access (for update action)");
		domainAllowlist.putObject("items").put("type", "string");

		ObjectNode domainBlocklist = properties.putObject("domain_blocklist");
		domainBlocklist.put("type", "array");
		domainBlocklist.put("description", "List of blocked domains the agent cannot access (for update action)");
		domainBlocklist.putObject("items").put("type", "string");

		schema.putArray("required").add("action");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String action = input.get("action").asText();

		return switch (action) {
			case "get" -> handleGet(userId);
			case "update" -> handleUpdate(input, userId);
			default -> "Unknown action: " + action + ". Use 'get' or 'update'.";
		};
	}

	private String handleGet(String userId) {
		try {
			UserConfig config = userConfigService.getConfig(userId);
			StringBuilder sb = new StringBuilder("Your current settings:\n");
			sb.append("- Max concurrent sparks: ").append(config.getMaxConcurrentSparks()).append("\n");
			sb.append("- Spending limit: $").append(config.getSpendingLimit()).append("\n");
			sb.append("- Domain allowlist: ").append(formatList(config.getDomainAllowlist())).append("\n");
			sb.append("- Domain blocklist: ").append(formatList(config.getDomainBlocklist())).append("\n");
			sb.append("- Confirmation overrides: ").append(formatMap(config.getConfirmationOverrides()));
			return sb.toString();
		}
		catch (Exception e) {
			log.error("Failed to get settings for user {}", userId, e);
			return "Failed to retrieve settings: " + e.getMessage();
		}
	}

	private String handleUpdate(JsonNode input, String userId) {
		try {
			Map<String, Object> updates = new HashMap<>();

			if (input.has("max_concurrent_sparks")) {
				updates.put("maxConcurrentSparks", input.get("max_concurrent_sparks").asInt());
			}
			if (input.has("spending_limit")) {
				updates.put("spendingLimit", input.get("spending_limit").asText());
			}
			if (input.has("domain_allowlist")) {
				updates.put("domainAllowlist", jsonArrayToList(input.get("domain_allowlist")));
			}
			if (input.has("domain_blocklist")) {
				updates.put("domainBlocklist", jsonArrayToList(input.get("domain_blocklist")));
			}

			if (updates.isEmpty()) {
				return "No settings fields provided to update. Specify at least one of: max_concurrent_sparks, spending_limit, domain_allowlist, domain_blocklist.";
			}

			userConfigService.updateConfig(userId, updates);
			log.info("Updated settings for user {}: {}", userId, updates.keySet());
			return "Settings updated successfully: " + String.join(", ", updates.keySet());
		}
		catch (Exception e) {
			log.error("Failed to update settings for user {}", userId, e);
			return "Failed to update settings: " + e.getMessage();
		}
	}

	private List<String> jsonArrayToList(JsonNode arrayNode) {
		List<String> list = new ArrayList<>();
		for (JsonNode element : arrayNode) {
			list.add(element.asText());
		}
		return list;
	}

	private String formatList(List<String> list) {
		return list == null || list.isEmpty() ? "(none)" : String.join(", ", list);
	}

	private String formatMap(Map<String, Integer> map) {
		return map == null || map.isEmpty() ? "(none)" : map.toString();
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
