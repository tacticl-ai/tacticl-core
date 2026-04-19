package io.tacticl.client.telegram.config;

public class TelegramConfig {

    private String botToken;
    private String webhookSecret;
    private String baseUrl = "https://api.telegram.org";
    private String webhookPath = "/v1/telegram/webhook";
    private String publicBaseUrl;
    private String botUsername;
    private int rateLimitPerMinute = 30;
    private int linkTokenTtlMinutes = 15;

    public boolean isConfigured() {
        return botToken != null && !botToken.isEmpty()
            && webhookSecret != null && !webhookSecret.isEmpty();
    }

    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getWebhookPath() { return webhookPath; }
    public void setWebhookPath(String webhookPath) { this.webhookPath = webhookPath; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    public String getBotUsername() { return botUsername; }
    public void setBotUsername(String botUsername) { this.botUsername = botUsername; }

    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public int getLinkTokenTtlMinutes() { return linkTokenTtlMinutes; }
    public void setLinkTokenTtlMinutes(int linkTokenTtlMinutes) { this.linkTokenTtlMinutes = linkTokenTtlMinutes; }
}
