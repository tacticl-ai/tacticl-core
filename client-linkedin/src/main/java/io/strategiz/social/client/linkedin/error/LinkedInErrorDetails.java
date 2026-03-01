package io.strategiz.social.client.linkedin.error;

import io.strategiz.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/**
 * Error details for LinkedIn Marketing API operations. Implements ErrorDetails for
 * integration with the Strategiz exception framework.
 *
 * <p>
 * Usage: throw new StrategizException(LinkedInErrorDetails.SHARE_FAILED,
 * "client-linkedin");
 */
public enum LinkedInErrorDetails implements ErrorDetails {

	// === Publishing Errors ===
	SHARE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "linkedin-share-failed"),

	// === Rate Limiting ===
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "linkedin-rate-limit-exceeded"),

	// === Profile Errors ===
	PROFILE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "linkedin-profile-failed"),

	// === Authentication Errors ===
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "linkedin-unauthorized");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	LinkedInErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
