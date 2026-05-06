package io.tacticl.client.whisper.config;

/** Configuration for the OpenAI Whisper transcription API. */
public class WhisperConfig {

    private String apiKey;

    private String baseUrl = "https://api.openai.com";

    private String model = "whisper-1";

    private int rateLimitPerMinute = 60;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

}
