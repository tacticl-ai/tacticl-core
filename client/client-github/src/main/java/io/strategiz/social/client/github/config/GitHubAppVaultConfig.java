package io.strategiz.social.client.github.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads GitHub App credentials from Vault into {@link GitHubAppConfig}.
 *
 * <p>
 * Vault path {@code secret/tacticl/github-app}, keys:
 * <ul>
 *   <li>{@code app-id} — numeric GitHub App id</li>
 *   <li>{@code private-key} — App private key, PEM</li>
 *   <li>{@code app-slug} — App slug for the install URL (optional)</li>
 * </ul>
 *
 * <p>
 * Gated by {@code tacticl.github.app.enabled}. DEGRADES GRACEFULLY: if any secret is absent the
 * {@link GitHubAppConfig} bean simply stays unconfigured (it is wired separately in
 * {@link ClientGitHubConfig}), and org-scoped features return empty rather than failing. Never
 * throws at startup.
 *
 * <p>
 * Mirrors the arbiter's {@code github-app-auth.ts} credential source
 * ({@code secret/{context}/github-app → app-id, private-key}).
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.github.app.enabled", havingValue = "true")
public class GitHubAppVaultConfig {

	private static final Logger log = LoggerFactory.getLogger(GitHubAppVaultConfig.class);

	private final SecretManager secretManager;

	private final GitHubAppConfig gitHubAppConfig;

	public GitHubAppVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
			GitHubAppConfig gitHubAppConfig) {
		this.secretManager = secretManager;
		this.gitHubAppConfig = gitHubAppConfig;
	}

	@PostConstruct
	public void loadGitHubAppPropertiesFromVault() {
		try {
			log.info("Loading GitHub App configuration from Vault (secret/tacticl/github-app)...");

			String appId = secretManager.readSecret("app-id", null);
			if (appId != null && !appId.isEmpty()) {
				gitHubAppConfig.setAppId(appId);
				log.info("Loaded GitHub App id from Vault");
			}
			else {
				log.warn("GitHub App id not found in Vault - GitHub App features will be disabled");
			}

			String privateKey = secretManager.readSecret("private-key", null);
			if (privateKey != null && !privateKey.isEmpty()) {
				// Tolerate a `\n`-escaped single-line PEM value (same as the arbiter).
				gitHubAppConfig.setPrivateKey(privateKey.replace("\\n", "\n"));
				log.info("Loaded GitHub App private key from Vault");
			}
			else {
				log.warn(
						"GitHub App private key not found in Vault - GitHub App features will be disabled");
			}

			String appSlug = secretManager.readSecret("app-slug", null);
			if (appSlug != null && !appSlug.isEmpty()) {
				gitHubAppConfig.setAppSlug(appSlug);
				log.info("Loaded GitHub App slug '{}' from Vault", appSlug);
			}
			else {
				log.warn("GitHub App slug not found in Vault - install URL will be unavailable");
			}

			if (gitHubAppConfig.isConfigured()) {
				log.info("GitHub App configuration loaded successfully");
			}
			else {
				log.warn("GitHub App is not fully configured - org-scoped features will return empty");
			}
		}
		catch (Exception e) {
			log.error("Failed to load GitHub App configuration from Vault - App features disabled", e);
		}
	}

}
