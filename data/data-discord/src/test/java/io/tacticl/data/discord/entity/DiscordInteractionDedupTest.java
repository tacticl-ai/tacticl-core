package io.tacticl.data.discord.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordInteractionDedupTest {

    @Test
    void create_initializesFieldsAndExpiry() {
        DiscordInteractionDedup dedup = DiscordInteractionDedup.create("interaction-1", "2", 600);

        assertEquals("interaction-1", dedup.getInteractionId());
        assertEquals("2", dedup.getInteractionType());
        assertNotNull(dedup.getSeenAt());
        assertNotNull(dedup.getExpiresAt());
        assertTrue(dedup.isActive());
    }

    @Test
    void create_expiresAfterTtl() {
        DiscordInteractionDedup dedup = DiscordInteractionDedup.create("interaction-1", "3", 600);

        assertTrue(dedup.getExpiresAt().isAfter(Instant.now()));
        assertEquals(600L, dedup.getExpiresAt().getEpochSecond() - dedup.getSeenAt().getEpochSecond());
    }
}
