package io.tacticl.client.deepgram.config;

/**
 * Configuration for the Deepgram streaming STT client (SAD §5.2).
 *
 * <p>Bound from {@code tacticl.deepgram.*} via Spring
 * {@code @ConfigurationProperties}. The {@code apiKey} is not bound from
 * application properties — it is loaded from Vault at startup by
 * {@link DeepgramVaultConfig}.
 */
public class DeepgramConfig {

    private String apiKey;

    private String apiBaseUrl = "wss://api.deepgram.com";

    private String model = "nova-2";

    private int endpointingMs = 300;

    private int sampleRate = 16000;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getEndpointingMs() {
        return endpointingMs;
    }

    public void setEndpointingMs(int endpointingMs) {
        this.endpointingMs = endpointingMs;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

}
