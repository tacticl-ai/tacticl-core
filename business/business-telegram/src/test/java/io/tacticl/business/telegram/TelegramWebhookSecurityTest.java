package io.tacticl.business.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramWebhookSecurityTest {

    private TelegramConfig config;
    private TelegramWebhookSecurity security;

    @BeforeEach
    void setUp() {
        config = new TelegramConfig();
        config.setWebhookSecret("expected-secret-token");
        security = new TelegramWebhookSecurity(config);
    }

    @Test
    void matchingSecretReturnsTrue() {
        assertTrue(security.isValidSignature("expected-secret-token"));
    }

    @Test
    void mismatchReturnsFalse() {
        assertFalse(security.isValidSignature("wrong-token"));
    }

    @Test
    void nullHeaderReturnsFalse() {
        assertFalse(security.isValidSignature(null));
    }

    @Test
    void nullConfiguredSecretReturnsFalse() {
        config.setWebhookSecret(null);
        assertFalse(security.isValidSignature("any-value"));
    }

    @Test
    void differentLengthsReturnFalse() {
        assertFalse(security.isValidSignature("short"));
    }
}
