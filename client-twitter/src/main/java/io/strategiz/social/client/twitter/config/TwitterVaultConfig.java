package io.strategiz.social.client.twitter.config;

import io.strategiz.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads Twitter/X API credentials from Vault and injects them into {@link TwitterConfig}.
 *
 * <p>
 * Vault paths:
 * <ul>
 *   <li>{@code twitter.api-key} - Twitter API key (consumer key)</li>
 *   <li>{@code twitter.api-secret} - Twitter API secret (consumer secret)</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.twitter.enabled", havingValue = "true")
public class TwitterVaultConfig {

	private static final Logger log = LoggerFactory.getLogger(TwitterVaultConfig.class);

	private final SecretManager secretManager;

	private final TwitterConfig twitterConfig;

	public TwitterVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
			TwitterConfig twitterConfig) {
		this.secretManager = secretManager;
		this.twitterConfig = twitterConfig;
	}

	@PostConstruct
	public void loadTwitterPropertiesFromVault() {
		try {
			log.info("Loading Twitter configuration from Vault...");

			String apiKey = secretManager.readSecret("twitter.api-key", null);
			if (apiKey != null && !apiKey.isEmpty()) {
				twitterConfig.setApiKey(apiKey);
				log.info("Loaded Twitter API key from Vault");
			}
			else {
				log.warn("Twitter API key not found in Vault - Twitter features will be disabled");
			}

			String apiSecret = secretManager.readSecret("twitter.api-secret", null);
			if (apiSecret != null && !apiSecret.isEmpty()) {
				twitterConfig.setApiSecret(apiSecret);
				log.info("Loaded Twitter API secret from Vault");
			}
			else {
				log.warn("Twitter API secret not found in Vault - Twitter features will be disabled");
			}

			if (twitterConfig.isConfigured()) {
				log.info("Twitter configuration loaded successfully");
			}
			else {
				log.warn("Twitter is not fully configured - some features may be unavailable");
			}
		}
		catch (Exception e) {
			log.error("Failed to load Twitter configuration from Vault", e);
		}
	}

}
