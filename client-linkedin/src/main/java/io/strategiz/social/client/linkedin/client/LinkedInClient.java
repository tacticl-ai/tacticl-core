package io.strategiz.social.client.linkedin.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.bucket4j.Bucket;
import io.strategiz.framework.exception.CidadelException;
import io.strategiz.social.client.linkedin.config.LinkedInConfig;
import io.strategiz.social.client.linkedin.dto.LinkedInShareResponse;
import io.strategiz.social.client.linkedin.dto.LinkedInUser;
import io.strategiz.social.client.linkedin.error.LinkedInErrorDetails;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Client for the LinkedIn Marketing API using Spring's RestClient.
 *
 * <p>
 * Provides methods for creating shares (UGC posts) and retrieving user profiles. All API
 * calls are rate-limited via Bucket4j and require a user-level OAuth2 access token.
 *
 * <p>
 * This class is NOT a @Component. It is instantiated as a @Bean by
 * {@link io.strategiz.social.client.linkedin.config.ClientLinkedInConfig} to ensure
 * proper dependency ordering.
 *
 * <p>
 * LinkedIn API Documentation:
 * https://learn.microsoft.com/en-us/linkedin/marketing/
 */
public class LinkedInClient {

	private static final Logger log = LoggerFactory.getLogger(LinkedInClient.class);

	private static final String MODULE_NAME = "client-linkedin";

	private final LinkedInConfig config;

	private final Bucket rateLimiter;

	private final ObjectMapper objectMapper;

	private final RestClient restClient;

	public LinkedInClient(LinkedInConfig config, Bucket rateLimiter, ObjectMapper objectMapper) {
		this.config = config;
		this.rateLimiter = rateLimiter;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.builder()
			.baseUrl(config.getBaseUrl())
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
		log.info("Initialized LinkedInClient with base URL: {}", config.getBaseUrl());
	}

	/**
	 * Create a text share (UGC post) on LinkedIn.
	 *
	 * <p>
	 * Posts to the /v2/ugcPosts endpoint using the UGC post format. The post is
	 * attributed to the user identified by the access token. The user's member URN is
	 * first retrieved from the /v2/userinfo endpoint to set the author field.
	 * @param text the share text content
	 * @param userAccessToken the user's OAuth2 access token with w_member_social scope
	 * @return LinkedInShareResponse containing the post ID and activity URN
	 * @throws CidadelException if the share creation fails
	 */
	public LinkedInShareResponse createShare(String text, String userAccessToken) {
		waitForRateLimit();

		try {
			// Retrieve the user's sub (member ID) to construct the author URN
			LinkedInUser user = getUserProfile(userAccessToken);
			String authorUrn = "urn:li:person:" + user.getSub();

			// Build UGC post body per LinkedIn Marketing API spec
			String requestBody = buildUgcPostBody(authorUrn, text);

			log.debug("Creating LinkedIn share for author: {}", authorUrn);

			LinkedInShareResponse response = restClient.post()
				.uri("/v2/ugcPosts")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken)
				.header("X-Restli-Protocol-Version", "2.0.0")
				.body(requestBody)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					HttpStatus status = HttpStatus.valueOf(res.getStatusCode().value());
					if (status == HttpStatus.UNAUTHORIZED) {
						throw new CidadelException(LinkedInErrorDetails.UNAUTHORIZED, MODULE_NAME,
								"LinkedIn access token is invalid or expired");
					}
					if (status == HttpStatus.TOO_MANY_REQUESTS) {
						throw new CidadelException(LinkedInErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME,
								"LinkedIn API rate limit exceeded");
					}
					throw new CidadelException(LinkedInErrorDetails.SHARE_FAILED, MODULE_NAME,
							String.format("LinkedIn API returned %s", status));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					throw new CidadelException(LinkedInErrorDetails.SHARE_FAILED, MODULE_NAME,
							"LinkedIn API server error");
				})
				.body(LinkedInShareResponse.class);

			log.info("Successfully created LinkedIn share: {}", response != null ? response.getId() : "unknown");
			return response;

		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to create LinkedIn share: {}", e.getMessage(), e);
			throw new CidadelException(LinkedInErrorDetails.SHARE_FAILED, MODULE_NAME, e,
					"Failed to create LinkedIn share: " + e.getMessage());
		}
	}

	/**
	 * Retrieve the authenticated user's LinkedIn profile via OpenID Connect.
	 * @param userAccessToken the user's OAuth2 access token with openid and profile
	 * scopes
	 * @return LinkedInUser containing sub, name, email, and picture
	 * @throws CidadelException if the profile retrieval fails
	 */
	public LinkedInUser getUserProfile(String userAccessToken) {
		waitForRateLimit();

		try {
			log.debug("Fetching LinkedIn user profile");

			LinkedInUser user = restClient.get()
				.uri("/v2/userinfo")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
					HttpStatus status = HttpStatus.valueOf(res.getStatusCode().value());
					if (status == HttpStatus.UNAUTHORIZED) {
						throw new CidadelException(LinkedInErrorDetails.UNAUTHORIZED, MODULE_NAME,
								"LinkedIn access token is invalid or expired");
					}
					throw new CidadelException(LinkedInErrorDetails.PROFILE_FAILED, MODULE_NAME,
							String.format("LinkedIn API returned %s", status));
				})
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
					throw new CidadelException(LinkedInErrorDetails.PROFILE_FAILED, MODULE_NAME,
							"LinkedIn API server error");
				})
				.body(LinkedInUser.class);

			log.info("Successfully retrieved LinkedIn profile for: {}", user != null ? user.getName() : "unknown");
			return user;

		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to fetch LinkedIn user profile: {}", e.getMessage(), e);
			throw new CidadelException(LinkedInErrorDetails.PROFILE_FAILED, MODULE_NAME, e,
					"Failed to fetch LinkedIn user profile: " + e.getMessage());
		}
	}

	/**
	 * Build the UGC post request body as a JSON string.
	 *
	 * <p>
	 * LinkedIn UGC post format:
	 *
	 * <pre>
	 * {
	 *   "author": "urn:li:person:{id}",
	 *   "lifecycleState": "PUBLISHED",
	 *   "specificContent": {
	 *     "com.linkedin.ugc.ShareContent": {
	 *       "shareCommentary": {
	 *         "text": "..."
	 *       },
	 *       "shareMediaCategory": "NONE"
	 *     }
	 *   },
	 *   "visibility": {
	 *     "com.linkedin.ugc.MemberNetworkVisibility": "PUBLIC"
	 *   }
	 * }
	 * </pre>
	 * @param authorUrn the author URN (e.g., "urn:li:person:abc123")
	 * @param text the share text
	 * @return JSON string for the request body
	 */
	private String buildUgcPostBody(String authorUrn, String text) {
		try {
			ObjectNode root = objectMapper.createObjectNode();
			root.put("author", authorUrn);
			root.put("lifecycleState", "PUBLISHED");

			// specificContent -> com.linkedin.ugc.ShareContent
			ObjectNode specificContent = objectMapper.createObjectNode();
			ObjectNode shareContent = objectMapper.createObjectNode();

			ObjectNode shareCommentary = objectMapper.createObjectNode();
			shareCommentary.put("text", text);
			shareContent.set("shareCommentary", shareCommentary);
			shareContent.put("shareMediaCategory", "NONE");

			specificContent.set("com.linkedin.ugc.ShareContent", shareContent);
			root.set("specificContent", specificContent);

			// visibility
			ObjectNode visibility = objectMapper.createObjectNode();
			visibility.put("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC");
			root.set("visibility", visibility);

			return objectMapper.writeValueAsString(root);
		}
		catch (Exception e) {
			log.error("Failed to build UGC post body: {}", e.getMessage(), e);
			throw new CidadelException(LinkedInErrorDetails.SHARE_FAILED, MODULE_NAME, e,
					"Failed to serialize UGC post body");
		}
	}

	/** Wait for rate limiter token before making an API call. */
	private void waitForRateLimit() {
		try {
			if (!rateLimiter.tryConsume(1)) {
				log.debug("LinkedIn rate limit reached, waiting for token...");
				boolean acquired = rateLimiter.asBlocking().tryConsume(1, Duration.ofSeconds(10));
				if (!acquired) {
					throw new CidadelException(LinkedInErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME,
							"Rate limit exceeded for LinkedIn API");
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Interrupted while waiting for LinkedIn rate limiter", e);
			throw new CidadelException(LinkedInErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME, e,
					"Rate limiter interrupted");
		}
	}

}
