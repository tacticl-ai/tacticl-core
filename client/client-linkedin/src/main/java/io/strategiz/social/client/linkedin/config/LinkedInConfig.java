package io.strategiz.social.client.linkedin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for LinkedIn Marketing API.
 *
 * <p>
 * Properties are loaded from Vault via {@link LinkedInVaultConfig} at startup. The
 * clientId and clientSecret are required for OAuth2 flows; the baseUrl and
 * rateLimitPerMinute have sensible defaults.
 */
@Configuration
@ConfigurationProperties(prefix = "linkedin")
public class LinkedInConfig {

	private String clientId;

	private String clientSecret;

	private String baseUrl = "https://api.linkedin.com";

	private int rateLimitPerMinute = 100;

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

	/**
	 * Check if the client is properly configured with OAuth credentials.
	 * @return true if both clientId and clientSecret are present
	 */
	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}

}
