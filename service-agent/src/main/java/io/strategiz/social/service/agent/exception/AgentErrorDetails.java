package io.strategiz.social.service.agent.exception;

import io.strategiz.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/**
 * Error details for voice agent operations.
 *
 * <p>
 * Implements {@link ErrorDetails} for integration with the Strategiz exception framework.
 *
 * <p>
 * Usage: {@code throw new StrategizException(AgentErrorDetails.COMMAND_FAILED, MODULE_NAME, message);}
 */
public enum AgentErrorDetails implements ErrorDetails {

	COMMAND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "agent-command-failed"),

	CONFIRMATION_NOT_FOUND(HttpStatus.NOT_FOUND, "agent-confirmation-not-found"),

	CONFIRMATION_EXPIRED(HttpStatus.GONE, "agent-confirmation-expired"),

	UNAUTHORIZED_CONFIRMATION(HttpStatus.FORBIDDEN, "agent-unauthorized-confirmation");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	AgentErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
