package io.strategiz.social.client.instagram.error;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/** Error details for Instagram Graph API errors. */
public enum InstagramErrorDetails implements ErrorDetails {

	MEDIA_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "instagram-media-creation-failed"),
	PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "instagram-publish-failed"),
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "instagram-rate-limit-exceeded"),
	PROFILE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "instagram-profile-failed"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "instagram-unauthorized");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	InstagramErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
