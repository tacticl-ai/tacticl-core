package io.strategiz.social.client.twitter.config;

/**
 * Configuration properties for Twitter/X API v2.
 *
 * <p>
 * API key and secret are loaded from Vault at startup by {@link TwitterVaultConfig}.
 * The base URL and rate limit have sensible defaults for production use.
 */
public class TwitterConfig {

	private String apiKey;

	private String apiSecret;

	private String baseUrl = "https://api.twitter.com";

	private int rateLimitPerMinute = 300;

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getApiSecret() {
		return apiSecret;
	}

	public void setApiSecret(String apiSecret) {
		this.apiSecret = apiSecret;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public int getRateLimitPerMinute() {
		return rateLimitPerMinute;
	}

	public void setRateLimitPerMinute(int rateLimitPerMinute) {
		this.rateLimitPerMinute = rateLimitPerMinute;
	}

	/** Check if the client is properly configured with API credentials. */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
	}

}
