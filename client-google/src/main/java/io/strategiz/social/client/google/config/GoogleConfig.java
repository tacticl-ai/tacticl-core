package io.strategiz.social.client.google.config;

/**
 * Configuration properties for Google OAuth 2.0 (YouTube, Gmail, Photos).
 *
 * <p>
 * Client ID and secret are loaded from Vault at startup by {@link GoogleVaultConfig}.
 */
public class GoogleConfig {

	private String clientId;

	private String clientSecret;

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

	/** Check if the client is properly configured with OAuth credentials. */
	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}

}
