package io.strategiz.social.service.social.error;

import io.strategiz.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/**
 * Error details for social media post and integration operations.
 *
 * <p>
 * Implements {@link ErrorDetails} for integration with the Strategiz exception framework.
 *
 * <p>
 * Usage: {@code throw new CidadelException(SocialErrorDetails.POST_NOT_FOUND, MODULE_NAME, postId);}
 */
public enum SocialErrorDetails implements ErrorDetails {

	POST_NOT_FOUND(HttpStatus.NOT_FOUND, "social-post-not-found"),

	POST_INVALID_STATE(HttpStatus.BAD_REQUEST, "social-post-invalid-state"),

	INTEGRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "social-integration-not-found"),

	OAUTH_FAILED(HttpStatus.BAD_GATEWAY, "social-oauth-failed"),

	PLATFORM_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "social-platform-not-supported");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	SocialErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
