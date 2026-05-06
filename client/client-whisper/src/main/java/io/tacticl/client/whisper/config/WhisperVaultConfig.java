package io.tacticl.client.whisper.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the OpenAI Whisper API key from Vault at startup.
 *
 * <p>Reads from the explicit cross-context path {@code secret/strategiz/openai}
 * (key {@code api-key}). The shared OpenAI key lives in the strategiz Vault
 * context — not the active tacticl context — because it is provisioned once
 * for the whole product family. This mirrors the convention in
 * {@code AnthropicVaultConfig.loadOAuthFromContext} which uses
 * {@code readSecretAsMap("secret/strategiz/anthropic")}.
 *
 * <p>Logs (does not throw) when the key is absent so deployments missing the
 * Vault provisioning step boot far enough to surface the misconfiguration in
 * health checks rather than crash the entire app.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.whisper.enabled", havingValue = "true")
public class WhisperVaultConfig {

    private static final Logger log = LoggerFactory.getLogger(WhisperVaultConfig.class);

    private static final String VAULT_PATH = "secret/strategiz/openai";

    private final SecretManager secretManager;

    private final WhisperConfig whisperConfig;

    public WhisperVaultConfig(@Qualifier("vaultSecretService") SecretManager secretManager,
                              WhisperConfig whisperConfig) {
        this.secretManager = secretManager;
        this.whisperConfig = whisperConfig;
    }

    @PostConstruct
    public void loadFromVault() {
        try {
            Map<String, Object> secrets = secretManager.readSecretAsMap(VAULT_PATH);
            if (secrets == null) {
                log.warn("Whisper enabled but no secrets found at {} — voice intake will fail.", VAULT_PATH);
                return;
            }
            Object apiKey = secrets.get("api-key");
            if (apiKey != null && !apiKey.toString().isEmpty()) {
                whisperConfig.setApiKey(apiKey.toString());
                log.info("Loaded Whisper API key from {}", VAULT_PATH);
            } else {
                log.warn("Whisper enabled but {} has no 'api-key' field — voice intake will fail.", VAULT_PATH);
            }
        } catch (Exception e) {
            log.error("Failed to load Whisper key from {}", VAULT_PATH, e);
        }
    }

}
