package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.GitHubTokenResolver;
import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.model.GitHubPullRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to create a pull request in a GitHub repository. Tier 1: requires confirmation. */
@Component
public class GitHubCreatePrSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GitHubCreatePrSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final Optional<GitHubClient> gitHubClient;

	private final GitHubTokenResolver tokenResolver;

	public GitHubCreatePrSkill(Optional<GitHubClient> gitHubClient, GitHubTokenResolver tokenResolver) {
		this.gitHubClient = gitHubClient;
		this.tokenResolver = tokenResolver;
	}

	@Override
	public String getName() {
		return "github_create_pr";
	}

	@Override
	public String getDescription() {
		return "Create a pull request in a GitHub repository";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode repo = properties.putObject("repo");
		repo.put("type", "string");
		repo.put("description", "Full repository name (e.g. 'owner/repo-name')");

		ObjectNode title = properties.putObject("title");
		title.put("type", "string");
		title.put("description", "Pull request title");

		ObjectNode body = properties.putObject("body");
		body.put("type", "string");
		body.put("description", "Pull request description / body");

		ObjectNode head = properties.putObject("head");
		head.put("type", "string");
		head.put("description", "The branch that contains the changes (head branch)");

		ObjectNode base = properties.putObject("base");
		base.put("type", "string");
		base.put("description", "The branch to merge into (default: 'main')");

		schema.putArray("required").add("repo").add("title").add("body").add("head");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		if (gitHubClient.isEmpty()) {
			return "GitHub integration is not configured. Enable it by setting tacticl.github.enabled=true.";
		}

		String repo = input.get("repo").asText();
		String title = input.get("title").asText();
		String body = input.get("body").asText();
		String head = input.get("head").asText();
		String base = input.has("base") ? input.get("base").asText() : "main";

		Optional<String> token = tokenResolver.resolve(userId, repo);
		if (token.isEmpty()) {
			return "No GitHub access configured for this repository. Grant access with: manage_repo grant " + repo;
		}

		try {
			String accessToken = token.get();
			GitHubPullRequest pr = gitHubClient.get().createPullRequest(repo, title, body, head, base, accessToken);
			log.info("Created PR for user {} — repo: {}, PR #{}", userId, repo, pr.getNumber());
			return String.format("Pull request created successfully.\nRepo: %s\nPR #%d: %s\nURL: %s\n%s → %s",
					repo, pr.getNumber(), title, pr.getHtmlUrl(), head, base);
		}
		catch (Exception e) {
			log.error("Failed to create PR for user {} — repo: {}, head: {}", userId, repo, head, e);
			return "Failed to create pull request in " + repo + ": " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
