package io.strategiz.social.client.github.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads GitHub OAuth credentials from Vault and injects them into {@link GitHubConfig}.
 *
 * <p>
 * Vault paths:
 * <ul>
 *   <li>{@code github.client-id} - GitHub OAuth App client ID</li>
 *   <li>{@code github.client-secret} - GitHub OAuth App client secret</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.github.enabled", havingValue = "true")
public class GitHubVaultConfig {

	private static final Logger log = LoggerFactory.getLogger(GitHubVaultConfig.class);

	private final SecretManager secretManager;

	private final GitHubConfig gitHubConfig;

	public GitHubVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
			GitHubConfig gitHubConfig) {
		this.secretManager = secretManager;
		this.gitHubConfig = gitHubConfig;
	}

	@PostConstruct
	public void loadGitHubPropertiesFromVault() {
		try {
			log.info("Loading GitHub configuration from Vault...");

			String clientId = secretManager.readSecret("github.client-id", null);
			if (clientId != null && !clientId.isEmpty()) {
				gitHubConfig.setClientId(clientId);
				log.info("Loaded GitHub client ID from Vault");
			}
			else {
				log.warn("GitHub client ID not found in Vault - GitHub features will be disabled");
			}

			String clientSecret = secretManager.readSecret("github.client-secret", null);
			if (clientSecret != null && !clientSecret.isEmpty()) {
				gitHubConfig.setClientSecret(clientSecret);
				log.info("Loaded GitHub client secret from Vault");
			}
			else {
				log.warn("GitHub client secret not found in Vault - GitHub features will be disabled");
			}

			if (gitHubConfig.isConfigured()) {
				log.info("GitHub configuration loaded successfully");
			}
			else {
				log.warn("GitHub is not fully configured - features will be unavailable");
			}
		}
		catch (Exception e) {
			log.error("Failed to load GitHub configuration from Vault", e);
		}
	}

}
