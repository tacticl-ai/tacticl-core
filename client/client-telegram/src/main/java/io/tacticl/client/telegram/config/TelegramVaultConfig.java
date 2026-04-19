package io.tacticl.client.telegram.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramVaultConfig {

    private final SecretManager secretManager;
    private final TelegramConfig telegramConfig;

    public TelegramVaultConfig(SecretManager secretManager, TelegramConfig telegramConfig) {
        this.secretManager = secretManager;
        this.telegramConfig = telegramConfig;
    }

    @PostConstruct
    public void loadFromVault() {
        String botToken = secretManager.readSecret("telegram.bot-token", null);
        String webhookSecret = secretManager.readSecret("telegram.webhook-secret", null);
        if (botToken != null) {
            telegramConfig.setBotToken(botToken);
        }
        if (webhookSecret != null) {
            telegramConfig.setWebhookSecret(webhookSecret);
        }
    }
}
