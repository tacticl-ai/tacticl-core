package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.jina.client.JinaClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to browse a web page and extract content using Jina Reader. Tier 0: auto-execute. */
@Component
public class BrowseWebSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowseWebSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final int MAX_CONTENT_LENGTH = 4000;

	private final Optional<JinaClient> jinaClient;

	public BrowseWebSkill(Optional<JinaClient> jinaClient) {
		this.jinaClient = jinaClient;
	}

	@Override
	public String getName() {
		return "browse_web";
	}

	@Override
	public String getDescription() {
		return "Browse a web page and extract its content as readable text";
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

		if (jinaClient.isEmpty()) {
			return "Web browsing is not available yet. This feature will be coming soon. "
					+ "For now, I can help you with other tasks like posting to social media, "
					+ "generating videos, or setting reminders. The URL you requested was: " + url;
		}

		try {
			String content = jinaClient.get().readPage(url);
			content = truncateContent(content);
			return "Content from " + url + ":\n\n" + content;
		}
		catch (Exception e) {
			log.error("Web browsing failed for user {} with URL: {}", userId, url, e);
			return "Failed to browse " + url + ": " + e.getMessage() + ". Please try again.";
		}
	}

	private String truncateContent(String content) {
		if (content.length() <= MAX_CONTENT_LENGTH) {
			return content;
		}

		// Truncate at the nearest paragraph boundary before the limit
		int cutoff = content.lastIndexOf("\n\n", MAX_CONTENT_LENGTH);
		if (cutoff <= 0) {
			cutoff = MAX_CONTENT_LENGTH;
		}

		return content.substring(0, cutoff)
				+ "\n\n[Content truncated at " + cutoff + " characters. The full page has more content.]";
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
