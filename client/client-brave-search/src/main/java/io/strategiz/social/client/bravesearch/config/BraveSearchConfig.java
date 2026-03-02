package io.strategiz.social.client.bravesearch.config;

/** Configuration for Brave Search API. */
public class BraveSearchConfig {

	private String apiKey;

	private String baseUrl = "https://api.search.brave.com";

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

	public int getRateLimitPerMinute() {
		return rateLimitPerMinute;
	}

	public void setRateLimitPerMinute(int rateLimitPerMinute) {
		this.rateLimitPerMinute = rateLimitPerMinute;
	}

}
