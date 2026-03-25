package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.github.GitHubClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to submit a review on a GitHub pull request. Tier 1: requires confirmation. */
@Component
public class GitHubReviewPrSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GitHubReviewPrSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final Optional<GitHubClient> gitHubClient;

	public GitHubReviewPrSkill(Optional<GitHubClient> gitHubClient) {
		this.gitHubClient = gitHubClient;
	}

	@Override
	public String getName() {
		return "github_review_pr";
	}

	@Override
	public String getDescription() {
		return "Submit a review on a GitHub pull request (approve or request changes)";
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
		prNumber.put("description", "Pull request number to review");

		ObjectNode action = properties.putObject("action");
		action.put("type", "string");
		action.put("description", "Review action: 'APPROVE' to approve the PR, 'REQUEST_CHANGES' to request changes");
		action.putArray("enum").add("APPROVE").add("REQUEST_CHANGES");

		ObjectNode body = properties.putObject("body");
		body.put("type", "string");
		body.put("description", "Review comment body explaining the review decision");

		schema.putArray("required").add("repo").add("prNumber").add("action").add("body");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		if (gitHubClient.isEmpty()) {
			return "GitHub integration is not configured. Enable it by setting tacticl.github.enabled=true.";
		}

		String repo = input.get("repo").asText();
		int prNumber = input.get("prNumber").asInt();
		String action = input.get("action").asText();
		String body = input.get("body").asText();

		try {
			gitHubClient.get().reviewPullRequest(repo, prNumber, action, body);
			log.info("Submitted PR review for user {} — repo: {}, PR #{}, action: {}", userId, repo, prNumber, action);
			return String.format("Review submitted for PR #%d in %s.\nAction: %s\nComment: %s",
					prNumber, repo, action, body);
		}
		catch (Exception e) {
			log.error("Failed to review PR for user {} — repo: {}, PR #{}", userId, repo, prNumber, e);
			return "Failed to review PR #" + prNumber + " in " + repo + ": " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
