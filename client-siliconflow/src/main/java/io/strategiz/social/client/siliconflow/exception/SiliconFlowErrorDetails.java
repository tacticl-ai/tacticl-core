package io.strategiz.social.client.siliconflow.exception;

import io.strategiz.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/** Error definitions for SiliconFlow API client. */
public enum SiliconFlowErrorDetails implements ErrorDetails {

	VIDEO_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "siliconflow-video-generation-failed"),
	STATUS_CHECK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "siliconflow-status-check-failed"),
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "siliconflow-rate-limit-exceeded"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "siliconflow-unauthorized");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	SiliconFlowErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
