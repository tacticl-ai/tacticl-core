package io.strategiz.social.client.linkedin.config;

import io.strategiz.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that loads LinkedIn OAuth credentials from Vault and injects them
 * into {@link LinkedInConfig}. This ensures all LinkedIn secrets are loaded from Vault at
 * startup.
 *
 * <p>
 * Vault keys:
 * <ul>
 * <li>linkedin.client-id: LinkedIn OAuth application client ID</li>
 * <li>linkedin.client-secret: LinkedIn OAuth application client secret</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.linkedin.enabled", havingValue = "true")
public class LinkedInVaultConfig {

	private static final Logger log = LoggerFactory.getLogger(LinkedInVaultConfig.class);

	private final SecretManager secretManager;

	private final LinkedInConfig linkedInConfig;

	@Autowired
	public LinkedInVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
			LinkedInConfig linkedInConfig) {
		this.secretManager = secretManager;
		this.linkedInConfig = linkedInConfig;
	}

	@PostConstruct
	public void loadLinkedInPropertiesFromVault() {
		try {
			log.info("Loading LinkedIn configuration from Vault...");

			String clientId = secretManager.readSecret("linkedin.client-id", null);
			if (clientId != null && !clientId.isEmpty()) {
				linkedInConfig.setClientId(clientId);
				log.info("Loaded LinkedIn client ID from Vault");
			}
			else {
				log.warn("LinkedIn client ID not found in Vault - LinkedIn features will be disabled");
			}

			String clientSecret = secretManager.readSecret("linkedin.client-secret", null);
			if (clientSecret != null && !clientSecret.isEmpty()) {
				linkedInConfig.setClientSecret(clientSecret);
				log.info("Loaded LinkedIn client secret from Vault");
			}
			else {
				log.warn("LinkedIn client secret not found in Vault - LinkedIn features will be disabled");
			}

			if (linkedInConfig.isConfigured()) {
				log.info("LinkedIn configuration loaded successfully");
			}
			else {
				log.warn("LinkedIn is not fully configured - some features may be unavailable");
			}

		}
		catch (Exception e) {
			log.error("Failed to load LinkedIn configuration from Vault", e);
		}
	}

}
