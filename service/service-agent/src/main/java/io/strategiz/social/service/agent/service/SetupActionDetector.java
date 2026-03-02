package io.strategiz.social.service.agent.service;

import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.DeviceRepository;
import io.strategiz.social.data.repository.RepoGrantRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.service.agent.dto.AgentAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Post-processes agent response text against the user's current state to generate structured
 * AgentAction objects. When the agent's text mentions connecting an account, device, or repo, this
 * service checks what the user actually has connected and produces actionable setup cards.
 */
@Service
public class SetupActionDetector {

	/** Maps platform keywords in response text to platform keys used by OAuth. */
	private static final Map<String, String> PLATFORM_KEYWORDS = Map.ofEntries(
			Map.entry("twitter", "twitter"), Map.entry("x (twitter)", "twitter"), Map.entry("x account", "twitter"),
			Map.entry("youtube", "youtube"), Map.entry("instagram", "instagram"), Map.entry("linkedin", "linkedin"),
			Map.entry("facebook", "facebook"), Map.entry("tiktok", "tiktok"), Map.entry("gmail", "gmail"),
			Map.entry("github", "github"));

	private static final Set<String> DEVICE_KEYWORDS = Set.of("connect a device", "device is", "device needs",
			"no device", "devices are currently offline", "device connected", "device online",
			"no suitable device", "connect your device", "devices registered", "no devices");

	private final SocialIntegrationRepository integrationRepository;

	private final DeviceRepository deviceRepository;

	private final RepoGrantRepository repoGrantRepository;

	public SetupActionDetector(SocialIntegrationRepository integrationRepository, DeviceRepository deviceRepository,
			RepoGrantRepository repoGrantRepository) {
		this.integrationRepository = integrationRepository;
		this.deviceRepository = deviceRepository;
		this.repoGrantRepository = repoGrantRepository;
	}

	/**
	 * Detect setup actions needed based on the agent's response text and the user's current state.
	 * @param responseText the agent's response text
	 * @param userId the authenticated user's ID
	 * @return list of actions (empty if none needed)
	 */
	public List<AgentAction> detect(String responseText, String userId) {
		if (responseText == null || responseText.isBlank()) {
			return List.of();
		}

		String lower = responseText.toLowerCase(Locale.ROOT);
		List<AgentAction> actions = new ArrayList<>();

		// 1. Detect missing social platform connections
		Set<String> connectedPlatforms = getConnectedPlatforms(userId);
		for (Map.Entry<String, String> entry : PLATFORM_KEYWORDS.entrySet()) {
			if (lower.contains(entry.getKey()) && !connectedPlatforms.contains(entry.getValue())) {
				// Response mentions a platform that's not connected
				if (mentionsShouldConnect(lower, entry.getKey())) {
					String displayName = entry.getKey().substring(0, 1).toUpperCase() + entry.getKey().substring(1);
					actions.add(AgentAction.connectAccount(entry.getValue(),
							"Connect your " + displayName + " account to proceed"));
				}
			}
		}

		// 2. Detect missing device connection
		if (DEVICE_KEYWORDS.stream().anyMatch(lower::contains)) {
			List<DeviceRegistration> devices = deviceRepository.findActiveByUserId(userId);
			if (devices.isEmpty()) {
				actions.add(AgentAction.connectDevice("Connect a device to handle this task"));
			}
		}

		// 3. Detect missing repo access (look for "grant access" or "connect repo" patterns)
		if (lower.contains("grant access") || lower.contains("connect repo") || lower.contains("repo access")
				|| lower.contains("repository access")) {
			// Only add if user has no repos at all
			if (repoGrantRepository.findActiveByUserId(userId).isEmpty()) {
				actions.add(AgentAction.grantRepo("GITHUB", null, "READ", "Grant repository access to get started"));
			}
		}

		return actions;
	}

	/** Check if the response implies the user should connect the platform (not just mentioning it). */
	private boolean mentionsShouldConnect(String lower, String platformKeyword) {
		// Look for patterns like "connect <platform>", "link <platform>", "haven't connected",
		// "need to connect", etc.
		int idx = lower.indexOf(platformKeyword);
		if (idx < 0)
			return false;

		// Check surrounding context (100 chars before)
		int start = Math.max(0, idx - 100);
		String context = lower.substring(start, idx + platformKeyword.length());
		return context.contains("connect") || context.contains("link") || context.contains("need")
				|| context.contains("haven't") || context.contains("not connected") || context.contains("set up")
				|| context.contains("authorize");
	}

	private Set<String> getConnectedPlatforms(String userId) {
		List<SocialIntegration> integrations = integrationRepository.findAllByUserId(userId);
		return integrations.stream()
			.filter(i -> !i.isDisabled())
			.map(i -> i.getPlatform().name().toLowerCase(Locale.ROOT))
			.collect(Collectors.toSet());
	}

}
