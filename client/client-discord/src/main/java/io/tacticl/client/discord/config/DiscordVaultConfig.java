package io.tacticl.client.discord.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Overlays the Discord secrets from Vault onto {@link DiscordConfig} at startup. Vault refs:
 * {@code discord-public-key}, {@code discord-bot-token}, {@code discord-application-id}.
 * Mirrors {@code TelegramVaultConfig}.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordVaultConfig {

    private final SecretManager secretManager;
    private final DiscordConfig discordConfig;

    public DiscordVaultConfig(SecretManager secretManager, DiscordConfig discordConfig) {
        this.secretManager = secretManager;
        this.discordConfig = discordConfig;
    }

    @PostConstruct
    public void loadFromVault() {
        String publicKey = secretManager.readSecret("discord.public-key", null);
        String botToken = secretManager.readSecret("discord.bot-token", null);
        String applicationId = secretManager.readSecret("discord.application-id", null);
        if (publicKey != null) {
            discordConfig.setPublicKey(publicKey);
        }
        if (botToken != null) {
            discordConfig.setBotToken(botToken);
        }
        if (applicationId != null) {
            discordConfig.setApplicationId(applicationId);
        }
    }
}
