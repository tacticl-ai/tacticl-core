package io.tacticl.data.telegram.entity;

import io.tacticl.data.pipeline.entity.PdlcRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TelegramProjectLinkTest {

    @Test
    void createsActiveLinkWithDefaults() {
        var link = TelegramProjectLink.create("proj-1", -1001L, "user-a", "Team Tacticl");
        assertEquals("proj-1", link.getProjectId());
        assertEquals(-1001L, link.getChatId());
        assertEquals("user-a", link.getOwnerUserId());
        assertEquals("Team Tacticl", link.getGroupTitle());
        assertEquals(ProjectStatus.ACTIVE, link.getStatus());
        assertNotNull(link.getInitializedAt());
        assertNull(link.getForumTopics());
    }

    @Test
    void archiveMarksStatus() {
        var link = TelegramProjectLink.create("p", 1L, "u", "t");
        link.archive();
        assertEquals(ProjectStatus.ARCHIVED, link.getStatus());
    }

    @Test
    void orphanMarksStatus() {
        var link = TelegramProjectLink.create("p", 1L, "u", "t");
        link.orphan();
        assertEquals(ProjectStatus.ORPHANED, link.getStatus());
    }

    @Test
    void setForumTopicsStoresMap() {
        var link = TelegramProjectLink.create("p", 1L, "u", "t");
        link.setForumTopics(Map.of(PdlcRole.ARCHITECT, 42L));
        assertEquals(42L, link.getForumTopics().get(PdlcRole.ARCHITECT));
    }

    @Test
    void statusChangedAtAdvancesOnTransition() throws InterruptedException {
        var link = TelegramProjectLink.create("p", 1L, "u", "t");
        var before = link.getStatusChangedAt();
        Thread.sleep(5);
        link.archive();
        assertTrue(link.getStatusChangedAt().isAfter(before));
    }

    @Test
    void reactivateRestoresActiveStatus() {
        var link = TelegramProjectLink.create("p", 1L, "u", "t");
        link.archive();
        link.reactivate();
        assertEquals(ProjectStatus.ACTIVE, link.getStatus());
    }

    @Test
    void createRejectsNullArgs() {
        assertThrows(NullPointerException.class,
            () -> TelegramProjectLink.create(null, 1L, "u", "t"));
        assertThrows(NullPointerException.class,
            () -> TelegramProjectLink.create("p", 1L, null, "t"));
        assertThrows(NullPointerException.class,
            () -> TelegramProjectLink.create("p", 1L, "u", null));
    }
}
