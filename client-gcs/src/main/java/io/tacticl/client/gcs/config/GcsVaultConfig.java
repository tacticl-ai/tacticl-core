package io.tacticl.client.gcs.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** Loads GCS credentials from Vault at startup. */
@Configuration
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class GcsVaultConfig {

	private final SecretManager secretManager;

	private final GcsConfig gcsConfig;

	public GcsVaultConfig(SecretManager secretManager, GcsConfig gcsConfig) {
		this.secretManager = secretManager;
		this.gcsConfig = gcsConfig;
	}

	@PostConstruct
	public void loadFromVault() {
		String key = secretManager.readSecret("gcs.service-account-key", null);
		if (key != null) {
			gcsConfig.setServiceAccountKey(key);
		}
	}

}
