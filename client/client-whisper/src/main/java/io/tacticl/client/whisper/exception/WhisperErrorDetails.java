package io.tacticl.client.whisper.exception;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/** Error definitions for the OpenAI Whisper transcription client. */
public enum WhisperErrorDetails implements ErrorDetails {

    TRANSCRIPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "whisper-transcription-failed"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "whisper-rate-limit-exceeded"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "whisper-unauthorized"),
    INVALID_AUDIO(HttpStatus.BAD_REQUEST, "whisper-invalid-audio");

    private final HttpStatus httpStatus;

    private final String propertyKey;

    WhisperErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
