package io.tacticl.data.telegram.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramLinkTest {

    @Test
    void create_initializesFieldsAndDefaults() {
        TelegramLink link = TelegramLink.create("user-1", 42L, "alice", "Alice");

        assertEquals("user-1", link.getUserId());
        assertEquals(42L, link.getChatId());
        assertEquals("alice", link.getUsername());
        assertEquals("Alice", link.getFirstName());
        assertTrue(link.isActive());
        assertNotNull(link.getLinkedAt());
        assertNotNull(link.getNotificationPrefs());
        assertTrue(link.getNotificationPrefs().isCheckpoints());
        assertTrue(link.getNotificationPrefs().isCompletion());
        assertTrue(link.getNotificationPrefs().isFailures());
        assertFalse(link.getNotificationPrefs().isProgress());
    }

    @Test
    void delete_flipsActiveFlag() {
        TelegramLink link = TelegramLink.create("user-1", 42L, "alice", "Alice");
        link.delete();
        assertFalse(link.isActive());
    }
}
