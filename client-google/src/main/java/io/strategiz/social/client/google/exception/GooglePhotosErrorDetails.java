package io.strategiz.social.client.google.exception;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/**
 * Error details for Google Photos API client operations.
 *
 * <p>
 * Implements {@link ErrorDetails} for integration with the Cidadel exception framework.
 *
 * <p>
 * Usage: {@code throw new CidadelException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME, message);}
 */
public enum GooglePhotosErrorDetails implements ErrorDetails {

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "google-photos-unauthorized"),

	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "google-photos-rate-limit-exceeded"),

	NOT_FOUND(HttpStatus.NOT_FOUND, "google-photos-not-found"),

	FORBIDDEN(HttpStatus.FORBIDDEN, "google-photos-forbidden"),

	API_ERROR(HttpStatus.BAD_GATEWAY, "google-photos-api-error");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	GooglePhotosErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
