package io.tacticl.client.telegram.exception;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

public enum TelegramErrorDetails implements ErrorDetails {

    SEND_MESSAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "telegram-send-message-failed"),
    WEBHOOK_REGISTRATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "telegram-webhook-registration-failed"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "telegram-rate-limit-exceeded"),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "telegram-invalid-webhook-signature"),
    BOT_API_ERROR(HttpStatus.BAD_GATEWAY, "telegram-bot-api-error");

    private final HttpStatus httpStatus;
    private final String propertyKey;

    TelegramErrorDetails(HttpStatus httpStatus, String propertyKey) {
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
