package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.github.GitHubClient;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to list directory contents in a GitHub repository. Tier 0: auto-execute (read-only). */
@Component
public class GitHubListFilesSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GitHubListFilesSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final Optional<GitHubClient> gitHubClient;

	public GitHubListFilesSkill(Optional<GitHubClient> gitHubClient) {
		this.gitHubClient = gitHubClient;
	}

	@Override
	public String getName() {
		return "github_list_files";
	}

	@Override
	public String getDescription() {
		return "List the contents of a directory in a GitHub repository";
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
		path.put("description", "Path to the directory to list (default: '/' for root)");

		ObjectNode branch = properties.putObject("branch");
		branch.put("type", "string");
		branch.put("description", "Branch name to list from (default: 'main')");

		schema.putArray("required").add("repo");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		if (gitHubClient.isEmpty()) {
			return "GitHub integration is not configured. Enable it by setting tacticl.github.enabled=true.";
		}

		String repo = input.get("repo").asText();
		String path = input.has("path") ? input.get("path").asText() : "/";
		String branch = input.has("branch") ? input.get("branch").asText() : "main";

		try {
			List<GitHubClient.FileEntry> entries = gitHubClient.get().listFiles(repo, path, branch);

			if (entries.isEmpty()) {
				return "Directory is empty: " + path + " in " + repo + " (branch: " + branch + ")";
			}

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("Contents of %s in %s (branch: %s):\n\n", path, repo, branch));

			for (GitHubClient.FileEntry entry : entries) {
				String typeMarker = "dir".equals(entry.getType()) ? "[DIR] " : "      ";
				sb.append(typeMarker).append(entry.getName()).append("\n");
			}

			return sb.toString().trim();
		}
		catch (Exception e) {
			log.error("Failed to list files for user {} — repo: {}, path: {}", userId, repo, path, e);
			return "Failed to list files at " + path + " in " + repo + ": " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
