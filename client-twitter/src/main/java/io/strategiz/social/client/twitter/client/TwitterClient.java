package io.strategiz.social.client.twitter.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.bucket4j.Bucket;
import io.strategiz.framework.exception.CidadelException;
import io.strategiz.social.client.twitter.dto.TweetResponse;
import io.strategiz.social.client.twitter.dto.TwitterUser;
import io.strategiz.social.client.twitter.exception.TwitterErrorDetails;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * Twitter/X API v2 client using Spring's {@link RestClient}.
 *
 * <p>
 * All methods require a per-user OAuth 2.0 access token (obtained through the OAuth flow
 * in the business layer). The app-level API key/secret are used only for the OAuth
 * handshake, not for API calls.
 *
 * <p>
 * Rate limiting is enforced via a {@link Bucket} token bucket to stay within Twitter API
 * limits.
 */
public class TwitterClient {

	private static final Logger log = LoggerFactory.getLogger(TwitterClient.class);

	private static final String MODULE_NAME = "client-twitter";

	private final RestClient restClient;

	private final Bucket rateLimiter;

	public TwitterClient(RestClient restClient, Bucket rateLimiter) {
		this.restClient = restClient;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Post a tweet on behalf of a user.
	 * @param text the tweet text (max 280 characters)
	 * @param userAccessToken the user's OAuth 2.0 access token
	 * @return the created tweet with id and text
	 * @throws CidadelException if the request fails or rate limit is exceeded
	 */
	public TweetResponse postTweet(String text, String userAccessToken) {
		consumeRateLimit();
		log.info("Posting tweet for user");

		try {
			TweetDataWrapper response = restClient.post()
				.uri("/2/tweets")
				.header("Authorization", "Bearer " + userAccessToken)
				.body(Map.of("text", text))
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(TwitterErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired user access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(TwitterErrorDetails.TWEET_FAILED, MODULE_NAME,
							"Twitter API returned status " + res.getStatusCode().value());
				})
				.body(TweetDataWrapper.class);

			if (response == null || response.data == null) {
				throw new CidadelException(TwitterErrorDetails.TWEET_FAILED, MODULE_NAME,
						"Empty response from Twitter API");
			}

			log.info("Tweet posted successfully with id: {}", response.data.getId());
			return response.data;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to post tweet: {}", e.getMessage(), e);
			throw new CidadelException(TwitterErrorDetails.TWEET_FAILED, MODULE_NAME, e);
		}
	}

	/**
	 * Get the authenticated user's profile.
	 * @param userAccessToken the user's OAuth 2.0 access token
	 * @return the user profile with id, name, username, and profile image URL
	 * @throws CidadelException if the request fails or rate limit is exceeded
	 */
	public TwitterUser getUserProfile(String userAccessToken) {
		consumeRateLimit();
		log.info("Fetching user profile from Twitter");

		try {
			UserDataWrapper response = restClient.get()
				.uri("/2/users/me?user.fields=name,username,profile_image_url")
				.header("Authorization", "Bearer " + userAccessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(TwitterErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired user access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(TwitterErrorDetails.USER_PROFILE_FAILED, MODULE_NAME,
							"Twitter API returned status " + res.getStatusCode().value());
				})
				.body(UserDataWrapper.class);

			if (response == null || response.data == null) {
				throw new CidadelException(TwitterErrorDetails.USER_PROFILE_FAILED, MODULE_NAME,
						"Empty response from Twitter API");
			}

			log.info("Fetched profile for user: @{}", response.data.getUsername());
			return response.data;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to fetch user profile: {}", e.getMessage(), e);
			throw new CidadelException(TwitterErrorDetails.USER_PROFILE_FAILED, MODULE_NAME, e);
		}
	}

	/**
	 * Delete a tweet on behalf of a user.
	 * @param tweetId the ID of the tweet to delete
	 * @param userAccessToken the user's OAuth 2.0 access token
	 * @throws CidadelException if the request fails or rate limit is exceeded
	 */
	public void deleteTweet(String tweetId, String userAccessToken) {
		consumeRateLimit();
		log.info("Deleting tweet with id: {}", tweetId);

		try {
			restClient.delete()
				.uri("/2/tweets/{id}", tweetId)
				.header("Authorization", "Bearer " + userAccessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(TwitterErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired user access token");
				})
				.onStatus(this::isNotFound, (req, res) -> {
					throw new CidadelException(TwitterErrorDetails.TWEET_NOT_FOUND, MODULE_NAME, tweetId);
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(TwitterErrorDetails.TWEET_FAILED, MODULE_NAME,
							"Twitter API returned status " + res.getStatusCode().value());
				})
				.toBodilessEntity();

			log.info("Tweet deleted successfully: {}", tweetId);
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to delete tweet {}: {}", tweetId, e.getMessage(), e);
			throw new CidadelException(TwitterErrorDetails.TWEET_FAILED, MODULE_NAME, e);
		}
	}

	private void consumeRateLimit() {
		if (!rateLimiter.tryConsume(1)) {
			throw new CidadelException(TwitterErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME,
					"Twitter API rate limit exceeded");
		}
	}

	private boolean isUnauthorized(HttpStatusCode status) {
		return status.value() == HttpStatus.UNAUTHORIZED.value();
	}

	private boolean isNotFound(HttpStatusCode status) {
		return status.value() == HttpStatus.NOT_FOUND.value();
	}

	/** Wrapper for Twitter API v2 responses that nest data under a "data" key. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class TweetDataWrapper {

		@JsonProperty("data")
		TweetResponse data;

	}

	/** Wrapper for Twitter API v2 user responses that nest data under a "data" key. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class UserDataWrapper {

		@JsonProperty("data")
		TwitterUser data;

	}

}
