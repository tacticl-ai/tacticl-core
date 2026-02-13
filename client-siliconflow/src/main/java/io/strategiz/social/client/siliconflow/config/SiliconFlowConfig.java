package io.strategiz.social.client.siliconflow.config;

/** Configuration for SiliconFlow API (Wan 2.2 video generation). */
public class SiliconFlowConfig {

	private String apiKey;

	private String baseUrl = "https://api.siliconflow.cn";

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
