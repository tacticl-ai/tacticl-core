package io.tacticl.client.discord.config;

/**
 * Configuration holder for the Discord transport. Populated from {@code tacticl.discord.*}
 * properties; the secret fields ({@code publicKey}, {@code botToken}, {@code applicationId})
 * are overlaid from Vault by {@link DiscordVaultConfig} at startup.
 */
public class DiscordConfig {

    /** Hex-encoded Ed25519 application public key used to verify inbound interaction signatures. */
    private String publicKey;

    /** Bot token (used as {@code Authorization: Bot <token>}) for REST calls — channel messages, command registration. */
    private String botToken;

    /** Discord application (client) id — used for the guild-scoped command registration endpoint. */
    private String applicationId;

    private String baseUrl = "https://discord.com/api/v10";

    private String interactionsPath = "/v1/discord/interactions";

    /** Guild the bot registers slash/context-menu commands against on boot. */
    private String commandGuildId;

    private int rateLimitPerMinute = 50;

    /** How long an interaction id is retained for dedup. Discord never replays beyond a few minutes. */
    private int interactionDedupTtlSeconds = 600;

    /** TTL for one-time account-link tokens. */
    private int linkTokenTtlMinutes = 15;

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isEmpty()
            && botToken != null && !botToken.isEmpty()
            && applicationId != null && !applicationId.isEmpty();
    }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getInteractionsPath() { return interactionsPath; }
    public void setInteractionsPath(String interactionsPath) { this.interactionsPath = interactionsPath; }

    public String getCommandGuildId() { return commandGuildId; }
    public void setCommandGuildId(String commandGuildId) { this.commandGuildId = commandGuildId; }

    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public int getInteractionDedupTtlSeconds() { return interactionDedupTtlSeconds; }
    public void setInteractionDedupTtlSeconds(int interactionDedupTtlSeconds) {
        this.interactionDedupTtlSeconds = interactionDedupTtlSeconds;
    }

    public int getLinkTokenTtlMinutes() { return linkTokenTtlMinutes; }
    public void setLinkTokenTtlMinutes(int linkTokenTtlMinutes) { this.linkTokenTtlMinutes = linkTokenTtlMinutes; }
}
