package io.tacticl.client.elevenlabs.exception;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/** Error definitions for the ElevenLabs streaming TTS client. */
public enum ElevenLabsErrorDetails implements ErrorDetails {

    SESSION_OPEN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "elevenlabs-session-open-failed"),
    SESSION_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "elevenlabs-session-not-configured"),
    SESSION_CLOSED(HttpStatus.INTERNAL_SERVER_ERROR, "elevenlabs-session-closed"),
    SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "elevenlabs-send-failed"),
    UPSTREAM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "elevenlabs-upstream-error");

    private final HttpStatus httpStatus;

    private final String propertyKey;

    ElevenLabsErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
