package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.client.base.llm.model.ToolDefinition;
import org.springframework.stereotype.Component;

/** Skill to search the web. Tier 0: auto-execute. Stub for MVP. */
@Component
public class SearchWebSkill implements AgentSkill {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public String getName() {
		return "search_web";
	}

	@Override
	public String getDescription() {
		return "Search the web for information on a given topic or query";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode query = properties.putObject("query");
		query.put("type", "string");
		query.put("description", "The search query");

		ObjectNode maxResults = properties.putObject("max_results");
		maxResults.put("type", "integer");
		maxResults.put("description", "Maximum number of results to return (default 5)");

		schema.putArray("required").add("query");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String query = input.get("query").asText();
		return "Web search is not available yet. This feature will be coming soon. "
				+ "For now, I can help you with other tasks like posting to social media, "
				+ "generating videos, or setting reminders. Your query was: " + query;
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
