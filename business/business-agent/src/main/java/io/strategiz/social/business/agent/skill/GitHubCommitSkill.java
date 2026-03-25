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

/** Skill to commit a file to a GitHub repository. Tier 1: requires confirmation. */
@Component
public class GitHubCommitSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GitHubCommitSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final Optional<GitHubClient> gitHubClient;

	public GitHubCommitSkill(Optional<GitHubClient> gitHubClient) {
		this.gitHubClient = gitHubClient;
	}

	@Override
	public String getName() {
		return "github_commit";
	}

	@Override
	public String getDescription() {
		return "Commit a file (create or update) to a GitHub repository branch";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode repo = properties.putObject("repo");
		repo.put("type", "string");
		repo.put("description", "Full repository name (e.g. 'owner/repo-name')");

		ObjectNode path = properties.putObject("path");
		path.put("type", "string");
		path.put("description", "Path of the file to create or update (e.g. 'src/Main.java')");

		ObjectNode content = properties.putObject("content");
		content.put("type", "string");
		content.put("description", "Full file content to write");

		ObjectNode message = properties.putObject("message");
		message.put("type", "string");
		message.put("description", "Commit message describing the change");

		ObjectNode branch = properties.putObject("branch");
		branch.put("type", "string");
		branch.put("description", "Branch to commit to");

		schema.putArray("required").add("repo").add("path").add("content").add("message").add("branch");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		if (gitHubClient.isEmpty()) {
			return "GitHub integration is not configured. Enable it by setting tacticl.github.enabled=true.";
		}

		String repo = input.get("repo").asText();
		String path = input.get("path").asText();
		String content = input.get("content").asText();
		String message = input.get("message").asText();
		String branch = input.get("branch").asText();

		try {
			String commitSha = gitHubClient.get().commitFile(repo, path, content, message, branch);
			log.info("Committed file for user {} — repo: {}, path: {}, branch: {}", userId, repo, path, branch);
			return String.format("File committed successfully.\nRepo: %s\nFile: %s\nBranch: %s\nCommit SHA: %s\nMessage: %s",
					repo, path, branch, commitSha, message);
		}
		catch (Exception e) {
			log.error("Failed to commit file for user {} — repo: {}, path: {}, branch: {}", userId, repo, path, branch, e);
			return "Failed to commit " + path + " to " + repo + " on branch " + branch + ": " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
