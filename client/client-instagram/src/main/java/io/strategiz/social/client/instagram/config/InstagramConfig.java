package io.strategiz.social.client.instagram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration properties for Instagram Graph API. */
@Configuration
@ConfigurationProperties(prefix = "instagram")
public class InstagramConfig {

	private String clientId;

	private String clientSecret;

	private String baseUrl = "https://graph.facebook.com/v21.0";

	private int rateLimitPerMinute = 200;

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
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

	/** Check if the client is properly configured with required credentials. */
	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}

}
