package io.tacticl.client.deepgram.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code tacticl.deepgram.*} properties bind correctly into
 * {@link DeepgramConfig} (the same mechanism {@code @ConfigurationProperties}
 * uses at runtime via {@link ClientDeepgramConfig}).
 */
class DeepgramConfigTest {

    @Test
    void defaultsAreNova2At16khzAndNotConfigured() {
        DeepgramConfig cfg = new DeepgramConfig();
        assertEquals("wss://api.deepgram.com", cfg.getApiBaseUrl());
        assertEquals("nova-2", cfg.getModel());
        assertEquals(300, cfg.getEndpointingMs());
        assertEquals(16000, cfg.getSampleRate());
        assertFalse(cfg.isConfigured(), "no api key set → not configured");
    }

    @Test
    void isConfiguredAfterApiKeySet() {
        DeepgramConfig cfg = new DeepgramConfig();
        cfg.setApiKey("dg-secret");
        assertTrue(cfg.isConfigured());
    }

    @Test
    void bindsAllPropertiesFromTacticlDeepgramPrefix() {
        Map<String, Object> source = new HashMap<>();
        source.put("tacticl.deepgram.api-base-url", "wss://example.test");
        source.put("tacticl.deepgram.model", "nova-3");
        source.put("tacticl.deepgram.endpointing-ms", "500");
        source.put("tacticl.deepgram.sample-rate", "48000");

        DeepgramConfig bound = new Binder(new MapConfigurationPropertySource(source))
            .bind("tacticl.deepgram", DeepgramConfig.class)
            .orElseThrow(() -> new AssertionError("binding failed"));

        assertEquals("wss://example.test", bound.getApiBaseUrl());
        assertEquals("nova-3", bound.getModel());
        assertEquals(500, bound.getEndpointingMs());
        assertEquals(48000, bound.getSampleRate());
    }

}
