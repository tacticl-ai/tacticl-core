package io.strategiz.social.client.jina.exception;

import io.strategiz.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/** Error definitions for Jina Reader API client. */
public enum JinaErrorDetails implements ErrorDetails {

	PAGE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "jina-page-read-failed"),
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "jina-rate-limit-exceeded"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "jina-unauthorized");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	JinaErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
