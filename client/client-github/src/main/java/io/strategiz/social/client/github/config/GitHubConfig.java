package io.strategiz.social.client.github.config;

/**
 * Configuration properties for the GitHub API client.
 *
 * <p>
 * OAuth credentials (client ID and secret) and the repository owner are loaded from
 * Vault at startup by {@link GitHubVaultConfig}.
 *
 * <p>
 * Vault paths:
 * <ul>
 *   <li>{@code github.client-id} — GitHub OAuth App client ID</li>
 *   <li>{@code github.client-secret} — GitHub OAuth App client secret</li>
 *   <li>{@code github.owner} — GitHub user or organization that owns the repositories</li>
 *   <li>{@code github.app-token} — Tacticl-controlled GitHub PAT (scopes: {@code repo},
 *       {@code admin:org}) used for agent-driven repo provisioning via
 *       {@code POST /user/repos} or {@code POST /orgs/{owner}/repos}</li>
 * </ul>
 */
public class GitHubConfig {

	private String clientId;

	private String clientSecret;

	/** GitHub user or organization name that owns the target repositories. */
	private String owner;

	/**
	 * Tacticl-controlled GitHub personal access token used for agent-driven repo
	 * provisioning (e.g. {@code createRepo}). Distinct from per-user OAuth tokens.
	 */
	private String appToken;

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

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getAppToken() {
		return appToken;
	}

	public void setAppToken(String appToken) {
		this.appToken = appToken;
	}

	/** Check if the client is properly configured with OAuth credentials. */
	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null
				&& !clientSecret.isBlank();
	}

}
