package io.strategiz.social.business.agent.service;

import io.strategiz.service.base.BaseService;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages credentials for device agents. Reads OAuth tokens from SocialIntegration Firestore
 * records. Devices request credentials on-demand during spark execution.
 */
@Service
public class CredentialService extends BaseService {

	private static final String MODULE_NAME = "business-agent";

	private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

	private final SocialIntegrationRepository integrationRepository;

	public CredentialService(SocialIntegrationRepository integrationRepository) {
		this.integrationRepository = integrationRepository;
	}

	@Override
	protected String getModuleName() {
		return MODULE_NAME;
	}

	/** Get credentials for a specific platform for a user. */
	public Optional<Map<String, Object>> getCredentials(String userId, String platform) {
		log.warn("[CREDENTIALS-AUDIT] Credential access requested: userId={}, platform={}, module={}",
				userId, platform, MODULE_NAME);

		PlatformType platformType = parsePlatformType(platform);
		if (platformType == null) {
			log.warn("[CREDENTIALS] Unknown platform: {}", platform);
			return Optional.empty();
		}

		Optional<SocialIntegration> integration = integrationRepository.findByUserIdAndPlatform(userId, platformType);
		if (integration.isEmpty()) {
			log.warn("[CREDENTIALS] No integration found for user={} platform={}", userId, platform);
			return Optional.empty();
		}

		SocialIntegration si = integration.get();
		if (si.isDisabled()) {
			log.warn("[CREDENTIALS] Integration disabled for user={} platform={}", userId, platform);
			return Optional.empty();
		}

		// Check if token refresh is needed and log a warning — the device will get a 401
		// from the platform and can request a refresh via OAuthTokenExchangeService
		if (si.isTokenRefreshNeeded()) {
			log.warn("[CREDENTIALS] Token refresh needed for user={} platform={} — serving stale access token",
					userId, platform);
		}

		Map<String, Object> credentials = new HashMap<>();
		credentials.put("platform", platform);
		if (si.getAccessToken() != null) {
			credentials.put("oauthAccessToken", si.getAccessToken());
		}
		// SECURITY: Never send refresh tokens to clients — they are server-side only
		if (si.getPlatformUsername() != null) {
			credentials.put("username", si.getPlatformUsername());
		}
		if (si.getPlatformUserId() != null) {
			credentials.put("platformUserId", si.getPlatformUserId());
		}
		credentials.put("tokenRefreshNeeded", si.isTokenRefreshNeeded());

		log.warn("[CREDENTIALS-AUDIT] Credentials served: userId={}, platform={}, tokenRefreshNeeded={}",
				userId, platform, si.isTokenRefreshNeeded());
		return Optional.of(credentials);
	}

	/** Register new credentials (e.g., from agent-created accounts). */
	public SocialIntegration registerCredentials(String userId, String platform, Map<String, String> credentials) {
		PlatformType platformType = parsePlatformType(platform);
		if (platformType == null) {
			throw new IllegalArgumentException("Unknown platform: " + platform);
		}

		// Check if integration already exists
		Optional<SocialIntegration> existing = integrationRepository.findByUserIdAndPlatform(userId, platformType);
		SocialIntegration si;
		if (existing.isPresent()) {
			si = existing.get();
		}
		else {
			si = new SocialIntegration();
			si.setId(UUID.randomUUID().toString());
			si.setUserId(userId);
			si.setPlatform(platformType);
			si.setCreatedAt(Instant.now());
		}

		if (credentials.containsKey("accessToken")) {
			si.setAccessToken(credentials.get("accessToken"));
		}
		if (credentials.containsKey("refreshToken")) {
			si.setRefreshToken(credentials.get("refreshToken"));
		}
		if (credentials.containsKey("username")) {
			si.setPlatformUsername(credentials.get("username"));
		}
		if (credentials.containsKey("platformUserId")) {
			si.setPlatformUserId(credentials.get("platformUserId"));
		}

		si.setDisabled(false);
		si.setTokenRefreshNeeded(false);
		si.setUpdatedAt(Instant.now());
		integrationRepository.save(userId, si, si.getId());

		log.info("[CREDENTIALS] Registered credentials for user={} platform={}", userId, platform);
		return si;
	}

	/** List all connected accounts for a user. */
	public java.util.List<SocialIntegration> listAccounts(String userId) {
		return integrationRepository.findAllByUserId(userId);
	}

	/** Disconnect an account. */
	public boolean disconnectAccount(String userId, String integrationId) {
		Optional<SocialIntegration> opt = integrationRepository.findById(userId, integrationId);
		if (opt.isEmpty()) {
			return false;
		}
		SocialIntegration si = opt.get();
		si.setActive(false);
		si.setDisabled(true);
		si.setUpdatedAt(Instant.now());
		integrationRepository.save(userId, si, si.getId());
		log.info("[CREDENTIALS] Disconnected account id={} platform={}", integrationId, si.getPlatform());
		return true;
	}

	private PlatformType parsePlatformType(String platform) {
		if (platform == null) {
			return null;
		}
		try {
			return PlatformType.valueOf(platform.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			// Try common aliases
			return switch (platform.toLowerCase()) {
				case "x" -> PlatformType.TWITTER;
				case "youtube" -> PlatformType.YOUTUBE;
				case "gmail" -> PlatformType.GMAIL;
				case "github" -> PlatformType.GITHUB;
				case "facebook", "fb" -> PlatformType.FACEBOOK;
				default -> null;
			};
		}
	}

}
