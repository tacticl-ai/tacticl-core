package io.strategiz.social.client.instagram.client;

import io.github.bucket4j.Bucket;
import io.cidadel.client.base.http.BaseHttpClient;
import io.cidadel.framework.exception.CidadelException;
import io.strategiz.social.client.instagram.config.InstagramConfig;
import io.strategiz.social.client.instagram.dto.InstagramMediaResponse;
import io.strategiz.social.client.instagram.dto.InstagramPublishResponse;
import io.strategiz.social.client.instagram.dto.InstagramUser;
import io.strategiz.social.client.instagram.error.InstagramErrorDetails;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Client for Instagram Graph API interactions.
 *
 * <p>
 * Supports media creation (container), media publishing, and user profile retrieval via the
 * Instagram Graph API (hosted on the Facebook Graph API).
 *
 * <p>
 * Note: This class is NOT a @Component. It is instantiated as a @Bean by
 * ClientInstagramConfig to ensure proper dependency ordering (instagramRateLimiter must
 * exist first).
 *
 * <p>
 * Instagram Graph API Documentation:
 * https://developers.facebook.com/docs/instagram-platform/instagram-graph-api
 */
public class InstagramClient extends BaseHttpClient {

	private static final Logger log = LoggerFactory.getLogger(InstagramClient.class);

	private final InstagramConfig config;

	private final Bucket rateLimiter;

	/** Creates a new {@link InstagramClient} with the given configuration and rate limiter. */
	public InstagramClient(InstagramConfig config, Bucket rateLimiter) {
		super(config.getBaseUrl());
		this.config = config;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Create a media container on Instagram. This is the first step of the two-step
	 * publishing process. The container must be published separately using
	 * {@link #publishMedia(String, String, String)}.
	 * @param igUserId Instagram user ID (IG-scoped)
	 * @param imageUrl Publicly accessible URL of the image to post
	 * @param caption Post caption text
	 * @param userAccessToken User's Instagram access token
	 * @return InstagramMediaResponse containing the creation ID
	 */
	public InstagramMediaResponse createMedia(String igUserId, String imageUrl, String caption,
			String userAccessToken) {
		log.debug("Creating Instagram media container for user: {}", igUserId);

		consumeRateLimit();

		try {
			InstagramMediaResponse response = restClient.post()
				.uri("/{igUserId}/media?image_url={imageUrl}&caption={caption}&access_token={accessToken}", igUserId,
						imageUrl, caption, userAccessToken)
				.retrieve()
				.body(InstagramMediaResponse.class);

			if (response == null || response.getId() == null) {
				throw new CidadelException(InstagramErrorDetails.MEDIA_CREATION_FAILED,
						"Instagram API returned null response for media creation");
			}

			log.info("Successfully created Instagram media container: {}", response.getId());
			return response;

		}
		catch (HttpClientErrorException ex) {
			handleHttpError(ex, "media creation");
			return null; // unreachable, handleHttpError always throws
		}
		catch (CidadelException ex) {
			throw ex;
		}
		catch (Exception ex) {
			log.error("Failed to create Instagram media for user: {}", igUserId, ex);
			throw new CidadelException(InstagramErrorDetails.MEDIA_CREATION_FAILED,
					String.format("Failed to create media: %s", ex.getMessage()), ex);
		}
	}

	/**
	 * Publish a previously created media container. This is the second step of the
	 * two-step publishing process.
	 * @param igUserId Instagram user ID (IG-scoped)
	 * @param creationId The creation ID returned from {@link #createMedia}
	 * @param userAccessToken User's Instagram access token
	 * @return InstagramPublishResponse containing the published media ID
	 */
	public InstagramPublishResponse publishMedia(String igUserId, String creationId, String userAccessToken) {
		log.debug("Publishing Instagram media {} for user: {}", creationId, igUserId);

		consumeRateLimit();

		try {
			InstagramPublishResponse response = restClient.post()
				.uri("/{igUserId}/media_publish?creation_id={creationId}&access_token={accessToken}", igUserId,
						creationId, userAccessToken)
				.retrieve()
				.body(InstagramPublishResponse.class);

			if (response == null || response.getId() == null) {
				throw new CidadelException(InstagramErrorDetails.PUBLISH_FAILED,
						"Instagram API returned null response for media publish");
			}

			log.info("Successfully published Instagram media: {}", response.getId());
			return response;

		}
		catch (HttpClientErrorException ex) {
			handleHttpError(ex, "media publish");
			return null; // unreachable
		}
		catch (CidadelException ex) {
			throw ex;
		}
		catch (Exception ex) {
			log.error("Failed to publish Instagram media {} for user: {}", creationId, igUserId, ex);
			throw new CidadelException(InstagramErrorDetails.PUBLISH_FAILED,
					String.format("Failed to publish media: %s", ex.getMessage()), ex);
		}
	}

	/**
	 * Get the authenticated user's Instagram profile.
	 * @param userAccessToken User's Instagram access token
	 * @return InstagramUser with profile information
	 */
	public InstagramUser getUserProfile(String userAccessToken) {
		log.debug("Fetching Instagram user profile");

		consumeRateLimit();

		try {
			InstagramUser response = restClient.get()
				.uri("/me?fields=id,username,account_type&access_token={accessToken}", userAccessToken)
				.retrieve()
				.body(InstagramUser.class);

			if (response == null || response.getId() == null) {
				throw new CidadelException(InstagramErrorDetails.PROFILE_FAILED,
						"Instagram API returned null response for user profile");
			}

			log.info("Successfully fetched Instagram profile for user: {}", response.getUsername());
			return response;

		}
		catch (HttpClientErrorException ex) {
			handleHttpError(ex, "profile retrieval");
			return null; // unreachable
		}
		catch (CidadelException ex) {
			throw ex;
		}
		catch (Exception ex) {
			log.error("Failed to fetch Instagram user profile", ex);
			throw new CidadelException(InstagramErrorDetails.PROFILE_FAILED,
					String.format("Failed to fetch user profile: %s", ex.getMessage()), ex);
		}
	}

	/**
	 * Consume a rate limit token, blocking if necessary.
	 * @throws CidadelException if rate limit cannot be acquired within timeout
	 */
	private void consumeRateLimit() {
		try {
			if (!rateLimiter.tryConsume(1)) {
				log.debug("Rate limit reached, waiting for token...");
				boolean acquired = rateLimiter.asBlocking().tryConsume(1, Duration.ofSeconds(10));
				if (!acquired) {
					throw new CidadelException(InstagramErrorDetails.RATE_LIMIT_EXCEEDED,
							"Rate limit exceeded for Instagram Graph API");
				}
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new CidadelException(InstagramErrorDetails.RATE_LIMIT_EXCEEDED, "Rate limiter interrupted", ex);
		}
	}

	/**
	 * Handle HTTP client errors from Instagram API.
	 * @param ex the HTTP client error exception
	 * @param operation description of the operation that failed
	 */
	private void handleHttpError(HttpClientErrorException ex, String operation) {
		if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
			log.warn("Instagram API rate limit exceeded (429) during {}", operation);
			throw new CidadelException(InstagramErrorDetails.RATE_LIMIT_EXCEEDED,
					"Instagram API rate limit exceeded", ex);
		}
		else if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
			log.error("Instagram API unauthorized ({}}) during {}", ex.getStatusCode().value(), operation);
			throw new CidadelException(InstagramErrorDetails.UNAUTHORIZED,
					String.format("Instagram API unauthorized: %s", ex.getMessage()), ex);
		}
		else {
			log.error("Instagram API error during {}: {} - {}", operation, ex.getStatusCode(), ex.getMessage());
			throw new CidadelException(InstagramErrorDetails.MEDIA_CREATION_FAILED,
					String.format("Instagram API error: %s", ex.getMessage()), ex);
		}
	}

}
