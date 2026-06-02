package io.tacticl.client.elevenlabs.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the ElevenLabs API key from Vault at startup.
 *
 * <p>Reads {@code elevenlabs.api-key} from the active tacticl Vault context
 * (context-aware: {@code secret/tacticl-qa/elevenlabs} in QA,
 * {@code secret/tacticl/elevenlabs} in prod), mirroring {@code DiscordVaultConfig}.
 * tacticl's own keys live under the tacticl context.
 *
 * <p>Logs (does not throw) when the key is absent so deployments missing the
 * Vault provisioning step boot far enough to surface the misconfiguration in
 * health checks rather than crash the entire app.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.elevenlabs.enabled", havingValue = "true")
public class ElevenLabsVaultConfig {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsVaultConfig.class);

    private final SecretManager secretManager;

    private final ElevenLabsConfig elevenLabsConfig;

    public ElevenLabsVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
                                 ElevenLabsConfig elevenLabsConfig) {
        this.secretManager = secretManager;
        this.elevenLabsConfig = elevenLabsConfig;
    }

    @PostConstruct
    public void loadFromVault() {
        try {
            // Context-aware: resolves to secret/<vault-context>/elevenlabs (e.g. secret/tacticl-qa/elevenlabs), key "api-key".
            String apiKey = secretManager.readSecret("elevenlabs.api-key", null);
            if (apiKey != null && !apiKey.isEmpty()) {
                elevenLabsConfig.setApiKey(apiKey);
                log.info("Loaded ElevenLabs API key from the tacticl Vault context (elevenlabs.api-key)");
            } else {
                log.warn("ElevenLabs enabled but elevenlabs.api-key not found in the tacticl Vault context — TTS will fail.");
            }
        } catch (Exception e) {
            log.error("Failed to load ElevenLabs key from Vault (elevenlabs.api-key)", e);
        }
    }

}
