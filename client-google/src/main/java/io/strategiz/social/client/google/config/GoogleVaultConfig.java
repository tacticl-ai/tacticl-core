package io.strategiz.social.client.google.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads Google OAuth credentials from Vault and injects them into {@link GoogleConfig}.
 *
 * <p>
 * Vault paths:
 * <ul>
 *   <li>{@code google.client-id} - Google OAuth client ID</li>
 *   <li>{@code google.client-secret} - Google OAuth client secret</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.google.enabled", havingValue = "true")
public class GoogleVaultConfig {

	private static final Logger log = LoggerFactory.getLogger(GoogleVaultConfig.class);

	private final SecretManager secretManager;

	private final GoogleConfig googleConfig;

	public GoogleVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
			GoogleConfig googleConfig) {
		this.secretManager = secretManager;
		this.googleConfig = googleConfig;
	}

	@PostConstruct
	public void loadGooglePropertiesFromVault() {
		try {
			log.info("Loading Google configuration from Vault...");

			String clientId = secretManager.readSecret("google.client-id", null);
			if (clientId != null && !clientId.isEmpty()) {
				googleConfig.setClientId(clientId);
				log.info("Loaded Google client ID from Vault");
			}
			else {
				log.warn("Google client ID not found in Vault - Google features will be disabled");
			}

			String clientSecret = secretManager.readSecret("google.client-secret", null);
			if (clientSecret != null && !clientSecret.isEmpty()) {
				googleConfig.setClientSecret(clientSecret);
				log.info("Loaded Google client secret from Vault");
			}
			else {
				log.warn("Google client secret not found in Vault - Google features will be disabled");
			}

			if (googleConfig.isConfigured()) {
				log.info("Google configuration loaded successfully");
			}
			else {
				log.warn("Google is not fully configured - YouTube/Gmail features will be unavailable");
			}
		}
		catch (Exception e) {
			log.error("Failed to load Google configuration from Vault", e);
		}
	}

}
