package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.client.base.llm.model.ToolDefinition;
import org.springframework.stereotype.Component;

/** Skill to browse a web page. Tier 0: auto-execute. Stub for MVP. */
@Component
public class BrowseWebSkill implements AgentSkill {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public String getName() {
		return "browse_web";
	}

	@Override
	public String getDescription() {
		return "Browse a web page and extract its content";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode url = properties.putObject("url");
		url.put("type", "string");
		url.put("description", "The URL of the web page to browse");

		schema.putArray("required").add("url");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String url = input.get("url").asText();
		return "Web browsing is not available yet. This feature will be coming soon. "
				+ "For now, I can help you with other tasks like posting to social media, "
				+ "generating videos, or setting reminders. The URL you requested was: " + url;
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
