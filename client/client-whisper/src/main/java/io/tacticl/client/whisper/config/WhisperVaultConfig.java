package io.tacticl.client.whisper.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the OpenAI Whisper API key from Vault at startup.
 *
 * <p>Reuses the shared OpenAI key managed by Strategiz: the same key
 * referenced by {@code AnthropicVaultConfig}-style consumers in the
 * cidadel/strategiz ecosystem under {@code secret/strategiz/openai}.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.whisper.enabled", havingValue = "true")
public class WhisperVaultConfig {

    private final SecretManager secretManager;

    private final WhisperConfig whisperConfig;

    public WhisperVaultConfig(SecretManager secretManager, WhisperConfig whisperConfig) {
        this.secretManager = secretManager;
        this.whisperConfig = whisperConfig;
    }

    @PostConstruct
    public void loadFromVault() {
        String apiKey = secretManager.readSecret("openai.api-key", null);
        if (apiKey != null) {
            whisperConfig.setApiKey(apiKey);
        }
    }

}
