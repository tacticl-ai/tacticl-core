package io.tacticl.client.deepgram.exception;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/** Error definitions for the Deepgram streaming STT client. */
public enum DeepgramErrorDetails implements ErrorDetails {

    NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "deepgram-not-configured"),
    CONNECT_FAILED(HttpStatus.BAD_GATEWAY, "deepgram-connect-failed"),
    SEND_FAILED(HttpStatus.BAD_GATEWAY, "deepgram-send-failed"),
    INVALID_FRAME(HttpStatus.BAD_REQUEST, "deepgram-invalid-frame"),
    SESSION_CLOSED(HttpStatus.GONE, "deepgram-session-closed");

    private final HttpStatus httpStatus;

    private final String propertyKey;

    DeepgramErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
