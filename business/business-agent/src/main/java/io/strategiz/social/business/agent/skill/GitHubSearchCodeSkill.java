package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.GitHubTokenResolver;
import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.model.GitHubFileContent;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to search code within a GitHub repository. Tier 0: auto-execute (read-only). */
@Component
public class GitHubSearchCodeSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GitHubSearchCodeSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final Optional<GitHubClient> gitHubClient;

	private final GitHubTokenResolver tokenResolver;

	public GitHubSearchCodeSkill(Optional<GitHubClient> gitHubClient, GitHubTokenResolver tokenResolver) {
		this.gitHubClient = gitHubClient;
		this.tokenResolver = tokenResolver;
	}

	@Override
	public String getName() {
		return "github_search_code";
	}

	@Override
	public String getDescription() {
		return "Search for code within a GitHub repository by keyword or pattern";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode repo = properties.putObject("repo");
		repo.put("type", "string");
		repo.put("description", "Full repository name (e.g. 'owner/repo-name')");

		ObjectNode query = properties.putObject("query");
		query.put("type", "string");
		query.put("description", "Search query to find matching code (e.g. a class name, method, or string literal)");

		schema.putArray("required").add("repo").add("query");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		if (gitHubClient.isEmpty()) {
			return "GitHub integration is not configured. Enable it by setting tacticl.github.enabled=true.";
		}

		String repo = input.get("repo").asText();
		String query = input.get("query").asText();

		Optional<String> token = tokenResolver.resolve(userId, repo);
		if (token.isEmpty()) {
			return "No GitHub access configured for this repository. Grant access with: manage_repo grant " + repo;
		}

		try {
			String accessToken = token.get();
			List<GitHubFileContent> results = gitHubClient.get().searchCode(repo, query, accessToken);

			if (results.isEmpty()) {
				return "No code matches found for \"" + query + "\" in " + repo;
			}

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("Found %d result(s) for \"%s\" in %s:\n\n", results.size(), query, repo));

			for (int i = 0; i < results.size(); i++) {
				GitHubFileContent result = results.get(i);
				sb.append(String.format("%d. %s\n", i + 1, result.getPath()));
				sb.append("\n");
			}

			return sb.toString().trim();
		}
		catch (Exception e) {
			log.error("Failed to search code for user {} — repo: {}, query: {}", userId, repo, query, e);
			return "Failed to search code in " + repo + ": " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
