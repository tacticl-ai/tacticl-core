package io.tacticl.client.discord.exception;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

public enum DiscordErrorDetails implements ErrorDetails {

    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "discord-invalid-signature"),
    INVALID_PUBLIC_KEY(HttpStatus.INTERNAL_SERVER_ERROR, "discord-invalid-public-key"),
    COMMAND_REGISTRATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "discord-command-registration-failed"),
    SEND_MESSAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "discord-send-message-failed"),
    INTERACTION_RESPONSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "discord-interaction-response-failed"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "discord-rate-limit-exceeded"),
    BOT_API_ERROR(HttpStatus.BAD_GATEWAY, "discord-bot-api-error");

    private final HttpStatus httpStatus;
    private final String propertyKey;

    DiscordErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
