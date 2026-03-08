package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import org.springframework.stereotype.Component;

/** Skill to generate social media content using Claude. Tier 0: auto-execute. */
@Component
public class ContentGenSkill implements AgentSkill {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public String getName() {
		return "generate_content";
	}

	@Override
	public String getDescription() {
		return "Generate platform-optimized social media content based on a topic or prompt. "
				+ "Returns draft text that the user can review before posting.";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode topic = properties.putObject("topic");
		topic.put("type", "string");
		topic.put("description", "The topic or prompt to generate content about");

		ObjectNode platform = properties.putObject("platform");
		platform.put("type", "string");
		platform.put("description", "Target platform for content optimization");
		platform.putArray("enum").add("TWITTER").add("LINKEDIN").add("INSTAGRAM");

		ObjectNode tone = properties.putObject("tone");
		tone.put("type", "string");
		tone.put("description", "Desired tone: professional, casual, humorous, inspirational");

		ObjectNode count = properties.putObject("count");
		count.put("type", "integer");
		count.put("description", "Number of variations to generate (1-5)");
		count.put("default", 3);

		schema.putArray("required").add("topic").add("platform");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		// Content generation is handled by Claude itself in the outer agent loop.
		// This tool call signals to the agent that it should generate content
		// and return it as text. The agent's system prompt instructs it to do this.
		String topic = input.get("topic").asText();
		String platform = input.get("platform").asText();
		String tone = input.has("tone") ? input.get("tone").asText() : "professional";
		int count = input.has("count") ? input.get("count").asInt() : 3;

		return String.format(
				"Generate %d %s social media post(s) about: %s. "
						+ "Optimize for %s platform character limits and best practices. "
						+ "Return numbered drafts the user can choose from.",
				count, tone, topic, platform);
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
