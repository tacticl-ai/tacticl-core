package io.tacticl.client.deepgram.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the Deepgram API key from Vault at startup.
 *
 * <p>Reads from the explicit cross-context path
 * {@code secret/strategiz/deepgram} (key {@code api-key}). Like Whisper /
 * OpenAI / Anthropic, the Deepgram key is provisioned once for the whole
 * product family under the {@code strategiz} Vault context — not the active
 * {@code tacticl} context. This mirrors {@code WhisperVaultConfig} and
 * {@code AnthropicVaultConfig.loadOAuthFromContext}.
 *
 * <p>Logs (does not throw) when the key is absent so deployments missing the
 * Vault provisioning step boot far enough for the misconfiguration to surface
 * in health checks rather than crash the entire app.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.deepgram.enabled", havingValue = "true")
public class DeepgramVaultConfig {

    private static final Logger log = LoggerFactory.getLogger(DeepgramVaultConfig.class);

    private static final String VAULT_PATH = "secret/strategiz/deepgram";

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
            Map<String, Object> secrets = secretManager.readSecretAsMap(VAULT_PATH);
            if (secrets == null) {
                log.warn("Deepgram enabled but no secrets found at {} — streaming STT will fail.",
                    VAULT_PATH);
                return;
            }
            Object apiKey = secrets.get("api-key");
            if (apiKey != null && !apiKey.toString().isEmpty()) {
                deepgramConfig.setApiKey(apiKey.toString());
                log.info("Loaded Deepgram API key from {}", VAULT_PATH);
            } else {
                log.warn("Deepgram enabled but {} has no 'api-key' field — streaming STT will fail.",
                    VAULT_PATH);
            }
        } catch (Exception e) {
            log.error("Failed to load Deepgram key from {}", VAULT_PATH, e);
        }
    }

}
