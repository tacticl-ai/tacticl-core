package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.bravesearch.client.BraveSearchClient;
import io.strategiz.social.client.bravesearch.dto.BraveSearchResult;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to search the web using Brave Search API. Tier 0: auto-execute. */
@Component
public class SearchWebSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(SearchWebSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final int MAX_RESULTS = 10;

	private static final int DEFAULT_RESULTS = 5;

	private final Optional<BraveSearchClient> braveSearchClient;

	public SearchWebSkill(Optional<BraveSearchClient> braveSearchClient) {
		this.braveSearchClient = braveSearchClient;
	}

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
		maxResults.put("description", "Maximum number of results to return (default 5, max 10)");

		schema.putArray("required").add("query");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String query = input.get("query").asText();
		int count = input.has("max_results") ? Math.min(input.get("max_results").asInt(DEFAULT_RESULTS), MAX_RESULTS)
				: DEFAULT_RESULTS;

		if (braveSearchClient.isEmpty()) {
			return "Web search is not available yet. This feature will be coming soon. "
					+ "For now, I can help you with other tasks like posting to social media, "
					+ "generating videos, or setting reminders. Your query was: " + query;
		}

		try {
			List<BraveSearchResult> results = braveSearchClient.get().search(query, count);

			if (results.isEmpty()) {
				return "No results found for: " + query;
			}

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("Found %d results for \"%s\":\n\n", results.size(), query));

			for (int i = 0; i < results.size(); i++) {
				BraveSearchResult result = results.get(i);
				sb.append(String.format("%d. **%s**\n", i + 1, result.getTitle()));
				sb.append(String.format("   %s\n", result.getUrl()));
				if (result.getDescription() != null && !result.getDescription().isBlank()) {
					sb.append(String.format("   %s\n", result.getDescription()));
				}
				if (result.getAge() != null && !result.getAge().isBlank()) {
					sb.append(String.format("   (%s)\n", result.getAge()));
				}
				sb.append("\n");
			}

			return sb.toString().trim();
		}
		catch (Exception e) {
			log.error("Web search failed for user {} with query: {}", userId, query, e);
			return "Web search failed: " + e.getMessage() + ". Please try again.";
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
