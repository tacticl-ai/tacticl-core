package io.tacticl.client.elevenlabs.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the ElevenLabs API key from Vault at startup.
 *
 * <p>Reads from the explicit cross-context path
 * {@code secret/strategiz/elevenlabs} (key {@code api-key}). The shared
 * ElevenLabs key lives in the strategiz Vault context — not the active tacticl
 * context — because the credential is provisioned once for the whole product
 * family. This mirrors the convention in {@code WhisperVaultConfig} and
 * {@code AnthropicVaultConfig.loadOAuthFromContext}.
 *
 * <p>Logs (does not throw) when the key is absent so deployments missing the
 * Vault provisioning step boot far enough to surface the misconfiguration in
 * health checks rather than crash the entire app.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.elevenlabs.enabled", havingValue = "true")
public class ElevenLabsVaultConfig {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsVaultConfig.class);

    private static final String VAULT_PATH = "secret/strategiz/elevenlabs";

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
            Map<String, Object> secrets = secretManager.readSecretAsMap(VAULT_PATH);
            if (secrets == null) {
                log.warn("ElevenLabs enabled but no secrets found at {} — TTS will fail.", VAULT_PATH);
                return;
            }
            Object apiKey = secrets.get("api-key");
            if (apiKey != null && !apiKey.toString().isEmpty()) {
                elevenLabsConfig.setApiKey(apiKey.toString());
                log.info("Loaded ElevenLabs API key from {}", VAULT_PATH);
            } else {
                log.warn("ElevenLabs enabled but {} has no 'api-key' field — TTS will fail.", VAULT_PATH);
            }
        } catch (Exception e) {
            log.error("Failed to load ElevenLabs key from {}", VAULT_PATH, e);
        }
    }

}
