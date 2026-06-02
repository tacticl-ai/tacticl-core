package io.tacticl.client.deepgram.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the Deepgram API key from Vault at startup.
 *
 * <p>Reads {@code deepgram.api-key} from the active tacticl Vault context
 * (context-aware: {@code secret/tacticl-qa/deepgram} in QA,
 * {@code secret/tacticl/deepgram} in prod), mirroring {@code DiscordVaultConfig}.
 * tacticl's own keys live under the tacticl context.
 *
 * <p>Logs (does not throw) when the key is absent so deployments missing the
 * Vault provisioning step boot far enough for the misconfiguration to surface
 * in health checks rather than crash the entire app.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.deepgram.enabled", havingValue = "true")
public class DeepgramVaultConfig {

    private static final Logger log = LoggerFactory.getLogger(DeepgramVaultConfig.class);

    private final SecretManager secretManager;

    private final DeepgramConfig deepgramConfig;

    public DeepgramVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
                               DeepgramConfig deepgramConfig) {
        this.secretManager = secretManager;
        this.deepgramConfig = deepgramConfig;
    }

    @PostConstruct
    public void loadFromVault() {
        try {
            // Context-aware: resolves to secret/<vault-context>/deepgram (e.g. secret/tacticl-qa/deepgram), key "api-key".
            String apiKey = secretManager.readSecret("deepgram.api-key", null);
            if (apiKey != null && !apiKey.isEmpty()) {
                deepgramConfig.setApiKey(apiKey);
                log.info("Loaded Deepgram API key from the tacticl Vault context (deepgram.api-key)");
            } else {
                log.warn("Deepgram enabled but deepgram.api-key not found in the tacticl Vault context — streaming STT will fail.");
            }
        } catch (Exception e) {
            log.error("Failed to load Deepgram key from Vault (deepgram.api-key)", e);
        }
    }

}
