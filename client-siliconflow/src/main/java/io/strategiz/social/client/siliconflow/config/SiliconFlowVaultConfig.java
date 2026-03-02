package io.strategiz.social.client.siliconflow.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** Loads SiliconFlow API credentials from Vault at startup. */
@Configuration
@ConditionalOnProperty(name = "tacticl.siliconflow.enabled", havingValue = "true")
public class SiliconFlowVaultConfig {

	private final SecretManager secretManager;

	private final SiliconFlowConfig siliconFlowConfig;

	public SiliconFlowVaultConfig(SecretManager secretManager, SiliconFlowConfig siliconFlowConfig) {
		this.secretManager = secretManager;
		this.siliconFlowConfig = siliconFlowConfig;
	}

	@PostConstruct
	public void loadFromVault() {
		String apiKey = secretManager.readSecret("siliconflow.api-key", null);
		if (apiKey != null) {
			siliconFlowConfig.setApiKey(apiKey);
		}
	}

}
