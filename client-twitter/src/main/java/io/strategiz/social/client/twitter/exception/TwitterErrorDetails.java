package io.strategiz.social.client.twitter.exception;

import io.strategiz.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/**
 * Error details for Twitter/X API v2 client operations.
 *
 * <p>
 * Implements {@link ErrorDetails} for integration with the Strategiz exception framework.
 *
 * <p>
 * Usage: {@code throw new CidadelException(TwitterErrorDetails.TWEET_FAILED, MODULE_NAME, message);}
 */
public enum TwitterErrorDetails implements ErrorDetails {

	TWEET_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "twitter-tweet-failed"),

	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "twitter-rate-limit-exceeded"),

	USER_PROFILE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "twitter-user-profile-failed"),

	TWEET_NOT_FOUND(HttpStatus.NOT_FOUND, "twitter-tweet-not-found"),

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "twitter-unauthorized");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	TwitterErrorDetails(HttpStatus httpStatus, String propertyKey) {
		this.httpStatus = httpStatus;
		this.propertyKey = propertyKey;
	}

	@Override
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	@Override
	public String getPropertyKey() {
		return propertyKey;
	}

}
