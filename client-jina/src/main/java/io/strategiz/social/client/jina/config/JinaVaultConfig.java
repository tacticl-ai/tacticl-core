package io.strategiz.social.client.jina.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** Loads Jina Reader API credentials from Vault at startup. */
@Configuration
@ConditionalOnProperty(name = "tacticl.jina.enabled", havingValue = "true")
public class JinaVaultConfig {

	private final SecretManager secretManager;

	private final JinaConfig jinaConfig;

	public JinaVaultConfig(SecretManager secretManager, JinaConfig jinaConfig) {
		this.secretManager = secretManager;
		this.jinaConfig = jinaConfig;
	}

	@PostConstruct
	public void loadFromVault() {
		String apiKey = secretManager.readSecret("jina.api-key", null);
		if (apiKey != null) {
			jinaConfig.setApiKey(apiKey);
		}
	}

}
