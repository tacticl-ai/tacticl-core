package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.model.GitHubFileContent;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to read a file from a GitHub repository. Tier 0: auto-execute (read-only). */
@Component
public class GitHubReadFileSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GitHubReadFileSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final Optional<GitHubClient> gitHubClient;

	public GitHubReadFileSkill(Optional<GitHubClient> gitHubClient) {
		this.gitHubClient = gitHubClient;
	}

	@Override
	public String getName() {
		return "github_read_file";
	}

	@Override
	public String getDescription() {
		return "Read the content of a file from a GitHub repository";
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
		path.put("description", "Path to the file within the repository (e.g. 'src/Main.java')");

		ObjectNode branch = properties.putObject("branch");
		branch.put("type", "string");
		branch.put("description", "Branch name to read from (default: 'main')");

		schema.putArray("required").add("repo").add("path");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		if (gitHubClient.isEmpty()) {
			return "GitHub integration is not configured. Enable it by setting tacticl.github.enabled=true.";
		}

		String repo = input.get("repo").asText();
		String path = input.get("path").asText();
		String branch = input.has("branch") ? input.get("branch").asText() : "main";

		try {
			// TODO: resolve the user's GitHub access token from their repo grant
			String accessToken = null;
			GitHubFileContent fileContent = gitHubClient.get().readFile(repo, path, branch, accessToken);
			String content = fileContent.getDecodedContent();
			return String.format("File: %s (branch: %s)\nRepo: %s\n\n%s", path, branch, repo, content);
		}
		catch (Exception e) {
			log.error("Failed to read file for user {} — repo: {}, path: {}", userId, repo, path, e);
			return "Failed to read file " + path + " from " + repo + ": " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
