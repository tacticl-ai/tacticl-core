package io.strategiz.social.business.publish;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.strategiz.social.client.github.config.GitHubConfig;
import io.strategiz.social.client.google.config.GoogleConfig;
import io.strategiz.social.client.instagram.config.InstagramConfig;
import io.strategiz.social.client.linkedin.config.LinkedInConfig;
import io.strategiz.social.client.twitter.config.TwitterConfig;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Service that handles OAuth 2.0 token exchange for all supported social media platforms.
 * Exchanges authorization codes for access/refresh tokens and persists them in Firestore.
 */
@Service
public class OAuthTokenExchangeService {

	private static final Logger log = LoggerFactory.getLogger(OAuthTokenExchangeService.class);

	private final TwitterConfig twitterConfig;

	private final LinkedInConfig linkedInConfig;

	private final InstagramConfig instagramConfig;

	private final GoogleConfig googleConfig;

	private final GitHubConfig gitHubConfig;

	private final SocialIntegrationRepository integrationRepository;

	private final RestClient restClient;

	public OAuthTokenExchangeService(Optional<TwitterConfig> twitterConfig, Optional<LinkedInConfig> linkedInConfig,
			Optional<InstagramConfig> instagramConfig, Optional<GoogleConfig> googleConfig,
			Optional<GitHubConfig> gitHubConfig, SocialIntegrationRepository integrationRepository) {
		this.twitterConfig = twitterConfig.orElse(null);
		this.linkedInConfig = linkedInConfig.orElse(null);
		this.instagramConfig = instagramConfig.orElse(null);
		this.googleConfig = googleConfig.orElse(null);
		this.gitHubConfig = gitHubConfig.orElse(null);
		this.integrationRepository = integrationRepository;
		this.restClient = RestClient.create();
	}

	/**
	 * Exchange an authorization code for tokens and store the integration.
	 * @param platform the social media platform
	 * @param code the authorization code from the OAuth callback
	 * @param codeVerifier the PKCE code verifier (null for platforms that don't use PKCE)
	 * @param redirectUri the redirect URI used in the authorization request
	 * @param userId the authenticated user's ID
	 * @return the created or updated SocialIntegration
	 */
	public SocialIntegration exchangeAndStore(PlatformType platform, String code, String codeVerifier,
			String redirectUri, String userId) {
		log.info("Exchanging authorization code for {} tokens for user {}", platform, userId);

		AuthTokens tokens = switch (platform) {
			case TWITTER -> exchangeTwitterToken(code, codeVerifier, redirectUri);
			case LINKEDIN -> exchangeLinkedInToken(code, codeVerifier, redirectUri);
			case INSTAGRAM -> exchangeInstagramToken(code, redirectUri);
			case YOUTUBE -> exchangeGoogleToken(code, codeVerifier, redirectUri);
			case GITHUB -> exchangeGitHubToken(code, redirectUri);
			default -> throw new IllegalArgumentException("Unsupported platform for OAuth: " + platform);
		};

		// Find existing integration or create new one
		Optional<SocialIntegration> existing = integrationRepository.findByUserIdAndPlatform(userId, platform);
		SocialIntegration integration;
		if (existing.isPresent()) {
			integration = existing.get();
			integration.setUpdatedAt(Instant.now());
		}
		else {
			integration = new SocialIntegration();
			integration.setId(UUID.randomUUID().toString());
			integration.setUserId(userId);
			integration.setPlatform(platform);
			integration.setCreatedAt(Instant.now());
			integration.setUpdatedAt(Instant.now());
		}

		integration.setAccessToken(tokens.getAccessToken());
		integration.setRefreshToken(tokens.getRefreshToken());
		integration.setTokenScope(tokens.getScope());
		integration.setTokenExpiresAt(tokens.getExpiresAt());
		integration.setTokenRefreshNeeded(false);
		integration.setDisabled(false);
		integration.setActive(true);

		integrationRepository.save(userId, integration, integration.getId());
		log.info("Stored {} integration {} for user {}", platform, integration.getId(), userId);

		return integration;
	}

	/**
	 * Refresh an expired access token.
	 * @param platform the social media platform
	 * @param refreshToken the refresh token
	 * @return new auth tokens
	 */
	public AuthTokens refreshToken(PlatformType platform, String refreshToken) {
		log.info("Refreshing {} token", platform);

		return switch (platform) {
			case TWITTER -> refreshTwitterToken(refreshToken);
			case LINKEDIN -> refreshLinkedInToken(refreshToken);
			case YOUTUBE -> refreshGoogleToken(refreshToken);
			default -> throw new IllegalArgumentException("Token refresh not supported for: " + platform);
		};
	}

	private AuthTokens exchangeTwitterToken(String code, String codeVerifier, String redirectUri) {
		if (twitterConfig == null) {
			throw new IllegalStateException("Twitter is not enabled — set tacticl.twitter.enabled=true");
		}
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "authorization_code");
		params.add("code", code);
		params.add("redirect_uri", redirectUri);
		params.add("code_verifier", codeVerifier);
		params.add("client_id", twitterConfig.getApiKey());

		TokenResponse response = restClient.post()
			.uri("https://api.twitter.com/2/oauth2/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(params)
			.retrieve()
			.body(TokenResponse.class);

		return toAuthTokens(response);
	}

	private AuthTokens refreshTwitterToken(String refreshToken) {
		if (twitterConfig == null) {
			throw new IllegalStateException("Twitter is not enabled — set tacticl.twitter.enabled=true");
		}
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "refresh_token");
		params.add("refresh_token", refreshToken);
		params.add("client_id", twitterConfig.getApiKey());

		TokenResponse response = restClient.post()
			.uri("https://api.twitter.com/2/oauth2/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(params)
			.retrieve()
			.body(TokenResponse.class);

		return toAuthTokens(response);
	}

	private AuthTokens exchangeLinkedInToken(String code, String codeVerifier, String redirectUri) {
		if (linkedInConfig == null) {
			throw new IllegalStateException("LinkedIn is not enabled — set tacticl.linkedin.enabled=true");
		}
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "authorization_code");
		params.add("code", code);
		params.add("redirect_uri", redirectUri);
		params.add("client_id", linkedInConfig.getClientId());
		params.add("client_secret", linkedInConfig.getClientSecret());
		if (codeVerifier != null) {
			params.add("code_verifier", codeVerifier);
		}

		TokenResponse response = restClient.post()
			.uri("https://www.linkedin.com/oauth/v2/accessToken")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(params)
			.retrieve()
			.body(TokenResponse.class);

		return toAuthTokens(response);
	}

	private AuthTokens refreshLinkedInToken(String refreshToken) {
		if (linkedInConfig == null) {
			throw new IllegalStateException("LinkedIn is not enabled — set tacticl.linkedin.enabled=true");
		}
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "refresh_token");
		params.add("refresh_token", refreshToken);
		params.add("client_id", linkedInConfig.getClientId());
		params.add("client_secret", linkedInConfig.getClientSecret());

		TokenResponse response = restClient.post()
			.uri("https://www.linkedin.com/oauth/v2/accessToken")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(params)
			.retrieve()
			.body(TokenResponse.class);

		return toAuthTokens(response);
	}

	private AuthTokens exchangeInstagramToken(String code, String redirectUri) {
		if (instagramConfig == null) {
			throw new IllegalStateException("Instagram is not enabled — set tacticl.instagram.enabled=true");
		}
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "authorization_code");
		params.add("code", code);
		params.add("redirect_uri", redirectUri);
		params.add("client_id", instagramConfig.getClientId());
		params.add("client_secret", instagramConfig.getClientSecret());

		TokenResponse response = restClient.post()
			.uri("https://api.instagram.com/oauth/access_token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(params)
			.retrieve()
			.body(TokenResponse.class);

		return toAuthTokens(response);
	}

	private AuthTokens exchangeGoogleToken(String code, String codeVerifier, String redirectUri) {
		if (googleConfig == null) {
			throw new IllegalStateException("Google is not enabled — set tacticl.google.enabled=true");
		}
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "authorization_code");
		params.add("code", code);
		params.add("redirect_uri", redirectUri);
		params.add("client_id", googleConfig.getClientId());
		params.add("client_secret", googleConfig.getClientSecret());
		if (codeVerifier != null) {
			params.add("code_verifier", codeVerifier);
		}

		TokenResponse response = restClient.post()
			.uri("https://oauth2.googleapis.com/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(params)
			.retrieve()
			.body(TokenResponse.class);

		return toAuthTokens(response);
	}

	private AuthTokens refreshGoogleToken(String refreshToken) {
		if (googleConfig == null) {
			throw new IllegalStateException("Google is not enabled — set tacticl.google.enabled=true");
		}
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "refresh_token");
		params.add("refresh_token", refreshToken);
		params.add("client_id", googleConfig.getClientId());
		params.add("client_secret", googleConfig.getClientSecret());

		TokenResponse response = restClient.post()
			.uri("https://oauth2.googleapis.com/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(params)
			.retrieve()
			.body(TokenResponse.class);

		return toAuthTokens(response);
	}

	private AuthTokens exchangeGitHubToken(String code, String redirectUri) {
		if (gitHubConfig == null) {
			throw new IllegalStateException("GitHub is not enabled — set tacticl.github.enabled=true");
		}
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("client_id", gitHubConfig.getClientId());
		params.add("client_secret", gitHubConfig.getClientSecret());
		params.add("code", code);
		params.add("redirect_uri", redirectUri);

		TokenResponse response = restClient.post()
			.uri("https://github.com/login/oauth/access_token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.header("Accept", "application/json")
			.body(params)
			.retrieve()
			.body(TokenResponse.class);

		return toAuthTokens(response);
	}

	private AuthTokens toAuthTokens(TokenResponse response) {
		if (response == null) {
			throw new IllegalStateException("Empty token response from OAuth provider");
		}
		Instant expiresAt = response.expiresIn > 0 ? Instant.now().plusSeconds(response.expiresIn) : null;
		return new AuthTokens(response.accessToken, response.refreshToken, response.scope, expiresAt);
	}

	/** Standard OAuth 2.0 token response. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class TokenResponse {

		@JsonProperty("access_token")
		String accessToken;

		@JsonProperty("refresh_token")
		String refreshToken;

		@JsonProperty("expires_in")
		long expiresIn;

		@JsonProperty("scope")
		String scope;

		@JsonProperty("token_type")
		String tokenType;

	}

}
