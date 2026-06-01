package io.tacticl.data.discord.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordAccountLinkTest {

    @Test
    void pending_isNotLinkedAndCarriesToken() {
        DiscordAccountLink link = DiscordAccountLink.pending("snowflake-1", "alice", "tok-1", 15);

        assertEquals("snowflake-1", link.getDiscordUserId());
        assertEquals("alice", link.getDiscordUsername());
        assertEquals("tok-1", link.getLinkToken());
        assertNotNull(link.getLinkTokenExpiresAt());
        assertFalse(link.isLinked());
        assertTrue(link.isActive());
    }

    @Test
    void linked_isLinkedWithNoToken() {
        DiscordAccountLink link = DiscordAccountLink.linked("snowflake-1", "alice", "user-1");

        assertTrue(link.isLinked());
        assertEquals("user-1", link.getTacticlUserId());
        assertNotNull(link.getLinkedAt());
        assertNull(link.getLinkToken());
    }

    @Test
    void redeem_bindsUserAndClearsToken() {
        DiscordAccountLink link = DiscordAccountLink.pending("snowflake-1", "alice", "tok-1", 15);

        link.redeem("user-1");

        assertTrue(link.isLinked());
        assertEquals("user-1", link.getTacticlUserId());
        assertNotNull(link.getLinkedAt());
        assertNull(link.getLinkToken());
        assertNull(link.getLinkTokenExpiresAt());
    }

    @Test
    void delete_flipsActiveFlag() {
        DiscordAccountLink link = DiscordAccountLink.linked("snowflake-1", "alice", "user-1");
        link.delete();
        assertFalse(link.isActive());
    }
}
