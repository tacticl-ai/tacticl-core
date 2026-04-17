package io.tacticl.business.profile;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/** Error definitions for the business-profile module. */
public enum ProfileErrorCode implements ErrorDetails {

	INVALID_TOKEN_CLAIMS(HttpStatus.BAD_REQUEST, "profile-invalid-token-claims");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	ProfileErrorCode(HttpStatus httpStatus, String propertyKey) {
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
