package io.strategiz.social.client.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.cidadel.framework.exception.CidadelException;
import io.strategiz.social.client.github.config.GitHubAppConfig;
import io.strategiz.social.client.github.exception.GitHubErrorDetails;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * GitHub App authentication — mints short-lived (≤1h), per-installation access tokens for
 * least-privilege org/repo access.
 *
 * <p>
 * Java port of the arbiter's {@code github-app-auth.ts} chain:
 * <ol>
 *   <li>App JWT — RS256-signed with the App private key ({@code iss = appId}, {@code iat} backdated
 *       60s for clock skew, {@code exp} +9m within GitHub's 10-minute cap).</li>
 *   <li>Installation id — {@code GET /orgs/{org}/installation}, authenticated as the App.</li>
 *   <li>Installation token — {@code POST /app/installations/{id}/access_tokens}, authenticated as
 *       the App. Returns {@code { token, expires_at }} valid ≤1h; cached ~50min.</li>
 * </ol>
 *
 * <p>
 * Construction does not validate credentials — when the App is unconfigured the bean is still
 * present but {@link #isEnabled()} reports {@code false}, so callers degrade gracefully and never
 * fail at startup.
 */
public class GitHubAppAuth {

	private static final Logger log = LoggerFactory.getLogger(GitHubAppAuth.class);

	private static final String MODULE_NAME = "client-github";

	/** Reuse a cached installation token while it still has comfortably more than this much life. */
	private static final long CACHE_SAFETY_WINDOW_SECONDS = 600; // 10 minutes

	private final RestClient restClient;

	/** App credentials holder; populated from Vault after construction (lazy-resolved on first use). */
	private final GitHubAppConfig appConfig;

	/** Lazily-parsed private key, cached after the first successful resolve. */
	private final AtomicReference<PrivateKey> parsedKey = new AtomicReference<>();

	/** org login → installation id (stable for the App's lifetime). */
	private final Map<String, Long> installationCache = new ConcurrentHashMap<>();

	/** installation id → cached token while still fresh. */
	private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

	/**
	 * @param restClient RestClient pointed at the GitHub API base URL
	 * @param appConfig App credentials holder (may be unconfigured at construction; populated from
	 * Vault before first use)
	 */
	public GitHubAppAuth(RestClient restClient, GitHubAppConfig appConfig) {
		this.restClient = restClient;
		this.appConfig = appConfig;
	}

	/** Whether usable App credentials are configured (id + parseable private key). */
	public boolean isEnabled() {
		return appConfig != null && appConfig.isConfigured() && resolveKey() != null;
	}

	/** Resolve + cache the parsed private key, or {@code null} if unconfigured/unparseable. */
	private PrivateKey resolveKey() {
		PrivateKey existing = parsedKey.get();
		if (existing != null) {
			return existing;
		}
		if (appConfig == null || appConfig.getPrivateKey() == null
				|| appConfig.getPrivateKey().isBlank()) {
			return null;
		}
		try {
			PrivateKey parsed = parsePrivateKey(appConfig.getPrivateKey());
			parsedKey.compareAndSet(null, parsed);
			return parsedKey.get();
		}
		catch (Exception e) {
			log.warn("GitHub App private key could not be parsed - App auth disabled: {}",
					e.getMessage());
			return null;
		}
	}

	/**
	 * Resolve the installation id for an org via {@code GET /orgs/{org}/installation}, authenticated
	 * as the App. Cached for the App's lifetime.
	 * @param org GitHub org login
	 * @return the installation id
	 * @throws CidadelException if the App is unconfigured or the App is not installed on the org
	 */
	public long installationIdForOrg(String org) {
		ensureEnabled();
		Long cached = installationCache.get(org);
		if (cached != null) {
			return cached;
		}
		try {
			InstallationResponse res = restClient.get()
				.uri("/orgs/{org}/installation", org)
				.header("Authorization", "Bearer " + mintAppJwt())
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.onStatus(this::isUnauthorized, (req, resp) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"App JWT rejected resolving installation for org " + org);
				})
				.onStatus(this::isNotFound, (req, resp) -> {
					throw new CidadelException(GitHubErrorDetails.NOT_FOUND, MODULE_NAME,
							"GitHub App is not installed on org " + org);
				})
				.onStatus(HttpStatusCode::isError, (req, resp) -> {
					throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
							"GitHub API returned status " + resp.getStatusCode().value()
									+ " resolving installation for org " + org);
				})
				.body(InstallationResponse.class);

			if (res == null || res.id == null) {
				throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
						"No installation id returned for org " + org);
			}
			installationCache.put(org, res.id);
			return res.id;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to resolve installation id for org {}: {}", org, e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	/**
	 * Mint an installation access token via {@code POST /app/installations/{id}/access_tokens}.
	 * A token still in cache with comfortable life left is reused (no fresh mint).
	 * @param installationId the installation id
	 * @return a short-lived installation access token
	 * @throws CidadelException if the App is unconfigured or the mint fails
	 */
	public String mintInstallationToken(long installationId) {
		ensureEnabled();
		CachedToken cached = tokenCache.get(installationId);
		if (cached != null
				&& cached.expiresAt.isAfter(Instant.now().plusSeconds(CACHE_SAFETY_WINDOW_SECONDS))) {
			return cached.token;
		}
		try {
			AccessTokenResponse res = restClient.post()
				.uri("/app/installations/{id}/access_tokens", installationId)
				.header("Authorization", "Bearer " + mintAppJwt())
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.onStatus(this::isUnauthorized, (req, resp) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"App JWT rejected minting installation token for " + installationId);
				})
				.onStatus(HttpStatusCode::isError, (req, resp) -> {
					throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
							"GitHub API returned status " + resp.getStatusCode().value()
									+ " minting installation token for " + installationId);
				})
				.body(AccessTokenResponse.class);

			if (res == null || res.token == null || res.token.isBlank()) {
				throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
						"Empty installation token response for " + installationId);
			}

			Instant expiresAt = res.expiresAt != null ? Instant.parse(res.expiresAt)
					: Instant.now().plusSeconds(3600);
			tokenCache.put(installationId, new CachedToken(res.token, expiresAt));
			return res.token;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to mint installation token for {}: {}", installationId, e.getMessage(),
					e);
			throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	// -------------------------------------------------------------------------
	// JWT minting
	// -------------------------------------------------------------------------

	/**
	 * Mint a short-lived App JWT, RS256-signed with the App private key. {@code iat} is backdated
	 * 60s to tolerate clock skew; {@code exp} is +9m (within GitHub's 10-minute cap).
	 */
	String mintAppJwt() {
		PrivateKey key = ensureEnabled();
		long iat = Instant.now().getEpochSecond() - 60;
		long exp = iat + 540; // 9 minutes, within GitHub's 10-minute cap.
		String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String payload = base64Url(
				("{\"iat\":" + iat + ",\"exp\":" + exp + ",\"iss\":\"" + appConfig.getAppId() + "\"}")
						.getBytes(StandardCharsets.UTF_8));
		String signingInput = header + "." + payload;
		try {
			Signature signer = Signature.getInstance("SHA256withRSA");
			signer.initSign(key);
			signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
			String signature = base64Url(signer.sign());
			return signingInput + "." + signature;
		}
		catch (Exception e) {
			throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	private PrivateKey ensureEnabled() {
		PrivateKey key = resolveKey();
		if (key == null || appConfig == null || !appConfig.isConfigured()) {
			throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
					"GitHub App is not configured");
		}
		return key;
	}

	/** Parse a PKCS#8 PEM private key (handles PKCS#1 by stripping headers conservatively). */
	private static PrivateKey parsePrivateKey(String pem) throws Exception {
		String normalized = pem
			.replace("-----BEGIN RSA PRIVATE KEY-----", "")
			.replace("-----END RSA PRIVATE KEY-----", "")
			.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s", "");
		byte[] der = Base64.getDecoder().decode(normalized);
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
		return KeyFactory.getInstance("RSA").generatePrivate(spec);
	}

	private static String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private boolean isUnauthorized(HttpStatusCode status) {
		return status.value() == HttpStatus.UNAUTHORIZED.value();
	}

	private boolean isNotFound(HttpStatusCode status) {
		return status.value() == HttpStatus.NOT_FOUND.value();
	}

	private record CachedToken(String token, Instant expiresAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class InstallationResponse {

		@JsonProperty("id")
		Long id;

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class AccessTokenResponse {

		@JsonProperty("token")
		String token;

		@JsonProperty("expires_at")
		String expiresAt;

	}

}
