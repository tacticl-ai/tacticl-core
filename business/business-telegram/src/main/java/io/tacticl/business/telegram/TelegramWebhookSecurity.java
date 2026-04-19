package io.tacticl.business.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class TelegramWebhookSecurity {

    private final TelegramConfig config;

    public TelegramWebhookSecurity(TelegramConfig config) {
        this.config = config;
    }

    public boolean isValidSignature(String headerValue) {
        if (headerValue == null) {
            return false;
        }
        String configured = config.getWebhookSecret();
        if (configured == null) {
            return false;
        }
        byte[] expected = configured.getBytes(StandardCharsets.UTF_8);
        byte[] actual = headerValue.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
