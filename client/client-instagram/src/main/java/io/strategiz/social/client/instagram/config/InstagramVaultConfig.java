package io.strategiz.social.client.instagram.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that loads Instagram credentials from Vault and injects them into
 * InstagramConfig. This ensures all Instagram secrets are loaded from Vault at startup.
 *
 * <p>
 * Vault paths: - instagram.client-id: Instagram app client ID - instagram.client-secret:
 * Instagram app client secret
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.instagram.enabled", havingValue = "true")
public class InstagramVaultConfig {

	private static final Logger log = LoggerFactory.getLogger(InstagramVaultConfig.class);

	private final SecretManager secretManager;

	private final InstagramConfig instagramConfig;

	@Autowired
	public InstagramVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
			InstagramConfig instagramConfig) {
		this.secretManager = secretManager;
		this.instagramConfig = instagramConfig;
	}

	@PostConstruct
	public void loadInstagramPropertiesFromVault() {
		try {
			log.info("Loading Instagram configuration from Vault...");

			String clientId = secretManager.readSecret("instagram.client-id", null);
			if (clientId != null && !clientId.isEmpty()) {
				instagramConfig.setClientId(clientId);
				log.info("Loaded Instagram client ID from Vault");
			}
			else {
				log.warn("Instagram client ID not found in Vault - Instagram features will be disabled");
			}

			String clientSecret = secretManager.readSecret("instagram.client-secret", null);
			if (clientSecret != null && !clientSecret.isEmpty()) {
				instagramConfig.setClientSecret(clientSecret);
				log.info("Loaded Instagram client secret from Vault");
			}
			else {
				log.warn("Instagram client secret not found in Vault - Instagram features will be disabled");
			}

			if (instagramConfig.isConfigured()) {
				log.info("Instagram configuration loaded successfully");
			}
			else {
				log.warn("Instagram is not fully configured - some features may be unavailable");
			}

		}
		catch (Exception e) {
			log.error("Failed to load Instagram configuration from Vault", e);
		}
	}

}
