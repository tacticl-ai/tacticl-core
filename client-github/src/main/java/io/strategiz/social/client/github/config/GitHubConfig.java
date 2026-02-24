package io.strategiz.social.client.github.config;

/**
 * Configuration properties for GitHub OAuth.
 *
 * <p>
 * Client ID and secret are loaded from Vault at startup by {@link GitHubVaultConfig}.
 */
public class GitHubConfig {

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
