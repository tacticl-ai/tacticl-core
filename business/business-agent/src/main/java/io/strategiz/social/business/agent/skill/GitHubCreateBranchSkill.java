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

/** Skill to create a new branch in a GitHub repository. Tier 1: requires confirmation. */
@Component
public class GitHubCreateBranchSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GitHubCreateBranchSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final Optional<GitHubClient> gitHubClient;

	private final GitHubTokenResolver tokenResolver;

	public GitHubCreateBranchSkill(Optional<GitHubClient> gitHubClient, GitHubTokenResolver tokenResolver) {
		this.gitHubClient = gitHubClient;
		this.tokenResolver = tokenResolver;
	}

	@Override
	public String getName() {
		return "github_create_branch";
	}

	@Override
	public String getDescription() {
		return "Create a new branch in a GitHub repository from a base branch";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode repo = properties.putObject("repo");
		repo.put("type", "string");
		repo.put("description", "Full repository name (e.g. 'owner/repo-name')");

		ObjectNode branchName = properties.putObject("branchName");
		branchName.put("type", "string");
		branchName.put("description", "Name for the new branch (e.g. 'feature/my-feature')");

		ObjectNode baseBranch = properties.putObject("baseBranch");
		baseBranch.put("type", "string");
		baseBranch.put("description", "Branch to create from (default: 'main')");

		schema.putArray("required").add("repo").add("branchName");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		if (gitHubClient.isEmpty()) {
			return "GitHub integration is not configured. Enable it by setting tacticl.github.enabled=true.";
		}

		String repo = input.get("repo").asText();
		String branchName = input.get("branchName").asText();
		String baseBranch = input.has("baseBranch") ? input.get("baseBranch").asText() : "main";

		Optional<String> token = tokenResolver.resolve(userId, repo);
		if (token.isEmpty()) {
			return "No GitHub access configured for this repository. Grant access with: manage_repo grant " + repo;
		}

		try {
			String accessToken = token.get();
			String latestSha = gitHubClient.get().getLatestCommitSha(repo, baseBranch, accessToken);
			gitHubClient.get().createBranch(repo, branchName, latestSha, accessToken);
			log.info("Created branch for user {} — repo: {}, branch: {}, base: {}", userId, repo, branchName, baseBranch);
			return String.format("Branch created: '%s' in %s (based on '%s', SHA: %s)",
					branchName, repo, baseBranch, latestSha.substring(0, Math.min(7, latestSha.length())));
		}
		catch (Exception e) {
			log.error("Failed to create branch for user {} — repo: {}, branch: {}", userId, repo, branchName, e);
			return "Failed to create branch '" + branchName + "' in " + repo + ": " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
