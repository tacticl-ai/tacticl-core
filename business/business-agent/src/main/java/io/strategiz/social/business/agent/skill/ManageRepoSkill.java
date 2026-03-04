package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.data.entity.AccessLevel;
import io.strategiz.social.data.entity.RepoGrant;
import io.strategiz.social.data.entity.RepoProvider;
import io.strategiz.social.data.repository.RepoGrantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to list, grant, or revoke repository access. Tier 1: mutations require confirmation. */
@Component
public class ManageRepoSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(ManageRepoSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final RepoGrantRepository repoGrantRepository;

	public ManageRepoSkill(RepoGrantRepository repoGrantRepository) {
		this.repoGrantRepository = repoGrantRepository;
	}

	@Override
	public String getName() {
		return "manage_repo";
	}

	@Override
	public String getDescription() {
		return "List, grant, or revoke repository access for Tacticl. Supported providers: GITHUB, GITLAB, BITBUCKET";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode action = properties.putObject("action");
		action.put("type", "string");
		action.put("description", "Action to perform: 'list' repos, 'grant' access to a new repo, or 'revoke' access to an existing repo");
		action.putArray("enum").add("list").add("grant").add("revoke");

		ObjectNode repoName = properties.putObject("repo_name");
		repoName.put("type", "string");
		repoName.put("description", "Full repository name (e.g. 'owner/repo-name'). Required for grant action");

		ObjectNode provider = properties.putObject("provider");
		provider.put("type", "string");
		provider.put("description", "Source control provider. Required for grant action");
		provider.putArray("enum").add("GITHUB").add("GITLAB").add("BITBUCKET");

		ObjectNode repoId = properties.putObject("repo_id");
		repoId.put("type", "string");
		repoId.put("description", "Repository grant ID. Required for revoke action");

		schema.putArray("required").add("action");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String action = input.get("action").asText();

		return switch (action) {
			case "list" -> handleList(userId);
			case "grant" -> handleGrant(input, userId);
			case "revoke" -> handleRevoke(input, userId);
			default -> "Unknown action: " + action + ". Use 'list', 'grant', or 'revoke'.";
		};
	}

	private String handleList(String userId) {
		try {
			List<RepoGrant> grants = repoGrantRepository.findActiveByUserId(userId);
			if (grants.isEmpty()) {
				return "You don't have any repositories connected. Use the 'grant' action to add one.";
			}
			StringBuilder sb = new StringBuilder("Your connected repositories:\n");
			for (RepoGrant grant : grants) {
				sb.append("- ").append(grant.getRepoFullName())
						.append(" (").append(grant.getProvider()).append(")")
						.append(" [").append(grant.getAccessLevel()).append("]")
						.append(" — ID: ").append(grant.getId())
						.append("\n");
			}
			return sb.toString();
		}
		catch (Exception e) {
			log.error("Failed to list repos for user {}", userId, e);
			return "Failed to list repositories: " + e.getMessage();
		}
	}

	private String handleGrant(JsonNode input, String userId) {
		if (!input.has("repo_name") || input.get("repo_name").asText().isBlank()) {
			return "Missing required field 'repo_name'. Provide the full repository name (e.g. 'owner/repo-name').";
		}
		if (!input.has("provider") || input.get("provider").asText().isBlank()) {
			return "Missing required field 'provider'. Specify GITHUB, GITLAB, or BITBUCKET.";
		}

		String repoName = input.get("repo_name").asText();
		String providerStr = input.get("provider").asText();

		RepoProvider provider;
		try {
			provider = RepoProvider.valueOf(providerStr);
		}
		catch (IllegalArgumentException e) {
			return "Unknown provider: " + providerStr + ". Supported: GITHUB, GITLAB, BITBUCKET.";
		}

		try {
			RepoGrant grant = new RepoGrant();
			String grantId = UUID.randomUUID().toString();
			grant.setId(grantId);
			grant.setUserId(userId);
			grant.setRepoFullName(repoName);
			grant.setProvider(provider);
			grant.setAccessLevel(AccessLevel.READ);
			grant.setGrantedAt(Instant.now());
			grant.setIsActive(true);

			repoGrantRepository.saveInSubcollection(userId, grant, userId);
			log.info("Granted repo access for user {}: {} ({})", userId, repoName, provider);
			return "Repository access granted: " + repoName + " (" + provider + ") with READ access. ID: " + grantId;
		}
		catch (Exception e) {
			log.error("Failed to grant repo access for user {}", userId, e);
			return "Failed to grant repository access: " + e.getMessage();
		}
	}

	private String handleRevoke(JsonNode input, String userId) {
		if (!input.has("repo_id") || input.get("repo_id").asText().isBlank()) {
			return "Missing required field 'repo_id'. Use the 'list' action to find the repo ID.";
		}

		String repoId = input.get("repo_id").asText();

		try {
			Optional<RepoGrant> existing = repoGrantRepository.findByIdInSubcollection(userId, repoId);
			if (existing.isEmpty() || !existing.get().getIsActive()) {
				return "Repository grant not found: " + repoId + ". Use the 'list' action to see your connected repos.";
			}

			RepoGrant grant = existing.get();
			grant.setIsActive(false);
			repoGrantRepository.saveInSubcollection(userId, grant, userId);
			log.info("Revoked repo access for user {}: {} ({})", userId, grant.getRepoFullName(), grant.getProvider());
			return "Repository access revoked: " + grant.getRepoFullName() + " (" + grant.getProvider() + ")";
		}
		catch (Exception e) {
			log.error("Failed to revoke repo access for user {}", userId, e);
			return "Failed to revoke repository access: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
