package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceSession;
import io.strategiz.social.data.entity.RepoGrant;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.DeviceRepository;
import io.strategiz.social.data.repository.DeviceSessionRepository;
import io.strategiz.social.data.repository.RepoGrantRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to show an overview of all connected resources (devices, social accounts, repos). Tier 0: auto-execute. */
@Component
public class ConnectionStatusSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(ConnectionStatusSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final DeviceRepository deviceRepository;

	private final DeviceSessionRepository sessionRepository;

	private final SocialIntegrationRepository integrationRepository;

	private final RepoGrantRepository repoGrantRepository;

	public ConnectionStatusSkill(DeviceRepository deviceRepository, DeviceSessionRepository sessionRepository,
			SocialIntegrationRepository integrationRepository, RepoGrantRepository repoGrantRepository) {
		this.deviceRepository = deviceRepository;
		this.sessionRepository = sessionRepository;
		this.integrationRepository = integrationRepository;
		this.repoGrantRepository = repoGrantRepository;
	}

	@Override
	public String getName() {
		return "connection_status";
	}

	@Override
	public String getDescription() {
		return "Show an overview of all connected resources: devices (with online status), social media integrations, and repositories";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		schema.putObject("properties");
		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		try {
			StringBuilder sb = new StringBuilder();

			appendDevices(sb, userId);
			appendIntegrations(sb, userId);
			appendRepos(sb, userId);

			return sb.toString();
		}
		catch (Exception e) {
			log.error("Failed to get connection status for user {}", userId, e);
			return "Failed to retrieve connection status: " + e.getMessage();
		}
	}

	private void appendDevices(StringBuilder sb, String userId) {
		List<DeviceRegistration> devices = deviceRepository.findActiveByUserId(userId);
		sb.append("Devices (").append(devices.size()).append("):\n");
		if (devices.isEmpty()) {
			sb.append("  (none)\n");
		}
		else {
			Set<String> onlineDeviceIds = sessionRepository.findActiveByUserId(userId).stream()
					.map(DeviceSession::getDeviceId)
					.collect(Collectors.toSet());
			for (DeviceRegistration device : devices) {
				boolean online = onlineDeviceIds.contains(device.getId());
				sb.append("- ").append(device.getDeviceName())
						.append(" (").append(device.getDeviceType()).append(")")
						.append(" — ").append(online ? "online" : "offline")
						.append("\n");
			}
		}
		sb.append("\n");
	}

	private void appendIntegrations(StringBuilder sb, String userId) {
		List<SocialIntegration> integrations = integrationRepository.findAllByUserId(userId);
		sb.append("Social Integrations (").append(integrations.size()).append("):\n");
		if (integrations.isEmpty()) {
			sb.append("  (none)\n");
		}
		else {
			for (SocialIntegration integ : integrations) {
				String status = integ.isDisabled() ? "disabled" : "active";
				sb.append("- ").append(integ.getPlatform().getDisplayName());
				if (integ.getPlatformUsername() != null) {
					sb.append(" (@").append(integ.getPlatformUsername()).append(")");
				}
				sb.append(" — ").append(status)
						.append("\n");
			}
		}
		sb.append("\n");
	}

	private void appendRepos(StringBuilder sb, String userId) {
		List<RepoGrant> repos = repoGrantRepository.findActiveByUserId(userId);
		sb.append("Repositories (").append(repos.size()).append("):\n");
		if (repos.isEmpty()) {
			sb.append("  (none)\n");
		}
		else {
			for (RepoGrant repo : repos) {
				sb.append("- ").append(repo.getRepoFullName())
						.append(" (").append(repo.getProvider()).append(")")
						.append(" [").append(repo.getAccessLevel()).append("]")
						.append("\n");
			}
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
