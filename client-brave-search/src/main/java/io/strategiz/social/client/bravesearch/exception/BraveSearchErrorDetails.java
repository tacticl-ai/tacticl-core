package io.strategiz.social.client.bravesearch.exception;

import io.strategiz.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/** Error definitions for Brave Search API client. */
public enum BraveSearchErrorDetails implements ErrorDetails {

	SEARCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "brave-search-failed"),
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "brave-search-rate-limit-exceeded"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "brave-search-unauthorized");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	BraveSearchErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
