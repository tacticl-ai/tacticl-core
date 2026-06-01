package io.tacticl.business.voice;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/**
 * Error definitions for the voice command-center turn orchestration.
 *
 * <p>The {@code voice-errors.properties} basename is registered with the message
 * source in the application layer ({@code GlobalMessageSourceConfig} in
 * application-api), consistent with the other channel modules.
 */
public enum VoiceErrorDetails implements ErrorDetails {

    /** A voice turn arrived with no usable transcript text. */
    EMPTY_TRANSCRIPT(HttpStatus.BAD_REQUEST, "voice-empty-transcript"),

    /** A {@code decision} frame referenced a checkpoint with no resolvable spark/run. */
    UNRESOLVABLE_DECISION(HttpStatus.UNPROCESSABLE_ENTITY, "voice-unresolvable-decision"),

    /** The STT (Deepgram) leg failed to open or stream. */
    STT_FAILED(HttpStatus.BAD_GATEWAY, "voice-stt-failed"),

    /** The TTS (ElevenLabs) leg failed to open or stream. */
    TTS_FAILED(HttpStatus.BAD_GATEWAY, "voice-tts-failed");

    private final HttpStatus httpStatus;

    private final String propertyKey;

    VoiceErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
