package io.strategiz.social.client.github.config;

/**
 * Configuration for the Tacticl GitHub App, used to mint short-lived installation access tokens
 * (least-privilege, per-org) for repos-in-scope discovery and host-git operations.
 *
 * <p>
 * Credentials are loaded from Vault at startup by {@link GitHubAppVaultConfig}.
 *
 * <p>
 * Vault path {@code secret/tacticl/github-app}, keys:
 * <ul>
 *   <li>{@code app-id} — numeric GitHub App id (as string)</li>
 *   <li>{@code private-key} — App private key, PEM ({@code -----BEGIN ... PRIVATE KEY-----})</li>
 *   <li>{@code app-slug} — App "slug" used to build the install URL
 *       {@code https://github.com/apps/{slug}/installations/new} (optional)</li>
 * </ul>
 *
 * <p>
 * The bean is always present (so REST endpoints degrade gracefully). {@link #isConfigured()}
 * reports whether the App credentials are actually available; when {@code false} the org-scoped
 * features return empty rather than failing.
 */
public class GitHubAppConfig {

	private String appId;

	/** App private key in PEM form. */
	private String privateKey;

	/** App slug used to construct the public install URL. May be {@code null}. */
	private String appSlug;

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public String getAppSlug() {
		return appSlug;
	}

	public void setAppSlug(String appSlug) {
		this.appSlug = appSlug;
	}

	/** True only when both the App id and private key are present. */
	public boolean isConfigured() {
		return appId != null && !appId.isBlank() && privateKey != null && !privateKey.isBlank();
	}

}
