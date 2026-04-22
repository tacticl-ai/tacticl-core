package io.tacticl.data.telegram.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramMemberGrantTest {

    @Test
    void createStoresFields() {
        var g = TelegramMemberGrant.create("proj-1", -100L, "u-1", 42L, MemberRole.RUNNER, "u-owner");
        assertEquals("proj-1", g.getProjectId());
        assertEquals(-100L, g.getChatId());
        assertEquals("u-1", g.getTacticlUserId());
        assertEquals(42L, g.getTelegramUserId());
        assertEquals(MemberRole.RUNNER, g.getRole());
        assertEquals("u-owner", g.getGrantedByUserId());
        assertNotNull(g.getGrantedAt());
    }

    @Test
    void updateRoleChangesRoleAndTimestamp() throws InterruptedException {
        var g = TelegramMemberGrant.create("p", 1L, "u", 2L, MemberRole.OBSERVER, "o");
        var before = g.getGrantedAt();
        Thread.sleep(5);
        g.updateRole(MemberRole.ADMIN, "o");
        assertEquals(MemberRole.ADMIN, g.getRole());
        assertTrue(g.getGrantedAt().isAfter(before));
    }

    @Test
    void createRejectsNullArgs() {
        assertThrows(NullPointerException.class,
            () -> TelegramMemberGrant.create(null, 1L, "u", 2L, MemberRole.RUNNER, "o"));
        assertThrows(NullPointerException.class,
            () -> TelegramMemberGrant.create("p", 1L, null, 2L, MemberRole.RUNNER, "o"));
        assertThrows(NullPointerException.class,
            () -> TelegramMemberGrant.create("p", 1L, "u", 2L, null, "o"));
        assertThrows(NullPointerException.class,
            () -> TelegramMemberGrant.create("p", 1L, "u", 2L, MemberRole.RUNNER, null));
    }
}
