package io.strategiz.social.client.bravesearch.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** Loads Brave Search API credentials from Vault at startup. */
@Configuration
@ConditionalOnProperty(name = "tacticl.brave-search.enabled", havingValue = "true")
public class BraveSearchVaultConfig {

	private final SecretManager secretManager;

	private final BraveSearchConfig braveSearchConfig;

	public BraveSearchVaultConfig(SecretManager secretManager, BraveSearchConfig braveSearchConfig) {
		this.secretManager = secretManager;
		this.braveSearchConfig = braveSearchConfig;
	}

	@PostConstruct
	public void loadFromVault() {
		String apiKey = secretManager.readSecret("brave-search.api-key", null);
		if (apiKey != null) {
			braveSearchConfig.setApiKey(apiKey);
		}
	}

}
