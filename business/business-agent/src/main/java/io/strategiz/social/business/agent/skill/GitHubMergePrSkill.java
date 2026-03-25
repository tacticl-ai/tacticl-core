package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.GitHubTokenResolver;
import io.strategiz.social.client.github.GitHubClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to merge a GitHub pull request. Tier 1: requires confirmation. */
@Component
public class GitHubMergePrSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GitHubMergePrSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final Optional<GitHubClient> gitHubClient;

	private final GitHubTokenResolver tokenResolver;

	public GitHubMergePrSkill(Optional<GitHubClient> gitHubClient, GitHubTokenResolver tokenResolver) {
		this.gitHubClient = gitHubClient;
		this.tokenResolver = tokenResolver;
	}

	@Override
	public String getName() {
		return "github_merge_pr";
	}

	@Override
	public String getDescription() {
		return "Merge a pull request in a GitHub repository";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode repo = properties.putObject("repo");
		repo.put("type", "string");
		repo.put("description", "Full repository name (e.g. 'owner/repo-name')");

		ObjectNode prNumber = properties.putObject("prNumber");
		prNumber.put("type", "integer");
		prNumber.put("description", "Pull request number to merge");

		ObjectNode mergeMethod = properties.putObject("mergeMethod");
		mergeMethod.put("type", "string");
		mergeMethod.put("description", "Merge strategy: 'squash' (default), 'merge', or 'rebase'");
		mergeMethod.putArray("enum").add("squash").add("merge").add("rebase");

		schema.putArray("required").add("repo").add("prNumber");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		if (gitHubClient.isEmpty()) {
			return "GitHub integration is not configured. Enable it by setting tacticl.github.enabled=true.";
		}

		String repo = input.get("repo").asText();
		int prNumber = input.get("prNumber").asInt();
		String mergeMethod = input.has("mergeMethod") ? input.get("mergeMethod").asText() : "squash";

		Optional<String> token = tokenResolver.resolve(userId, repo);
		if (token.isEmpty()) {
			return "No GitHub access configured for this repository. Grant access with: manage_repo grant " + repo;
		}

		try {
			String accessToken = token.get();
			gitHubClient.get().mergePullRequest(repo, prNumber, mergeMethod, accessToken);
			log.info("Merged PR for user {} — repo: {}, PR #{}, method: {}", userId, repo, prNumber, mergeMethod);
			return String.format("Pull request #%d merged successfully in %s.\nMerge method: %s",
					prNumber, repo, mergeMethod);
		}
		catch (Exception e) {
			log.error("Failed to merge PR for user {} — repo: {}, PR #{}", userId, repo, prNumber, e);
			return "Failed to merge PR #" + prNumber + " in " + repo + ": " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
