package io.strategiz.social.client.jina.config;

/** Configuration for Jina Reader API. */
public class JinaConfig {

	private String apiKey;

	private String baseUrl = "https://r.jina.ai";

	private int rateLimitPerMinute = 200;

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
