package io.tacticl.business.telegram.event;

import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class GroupMigrationHandlerTest {

    private TelegramProjectLinkRepository projectRepo;
    private GroupMigrationHandler handler;

    @BeforeEach
    void setUp() {
        projectRepo = mock(TelegramProjectLinkRepository.class);
        handler = new GroupMigrationHandler(projectRepo);
    }

    @Test
    void migrateUpdatesChatIdAndClearsTopics() {
        long oldChatId = -100L;
        long newChatId = -1001L;
        var link = TelegramProjectLink.create("proj-1", oldChatId, "user-1", "Team");
        link.setPinnedStatusMessageId(42L);
        when(projectRepo.findByChatIdAndIsActiveTrue(oldChatId)).thenReturn(Optional.of(link));

        var message = new Message(
            1L, 0L,
            new Chat(oldChatId, "group", null, null),
            null, null, null, null, null, null,
            newChatId, // migrate_to_chat_id
            null, null, null, null
        );

        handler.handle(message);

        ArgumentCaptor<TelegramProjectLink> captor = ArgumentCaptor.forClass(TelegramProjectLink.class);
        verify(projectRepo).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getChatId()).isEqualTo(newChatId);
        assertThat(saved.getForumTopics()).isNullOrEmpty();
        assertThat(saved.getPinnedStatusMessageId()).isNull();
    }

    @Test
    void nonMigrationMessageIgnored() {
        var message = new Message(
            1L, 0L,
            new Chat(-100L, "group", null, null),
            null, "hello", null, null, null, null,
            null, // no migrate_to_chat_id
            null, null, null, null
        );

        handler.handle(message);

        verify(projectRepo, never()).findByChatIdAndIsActiveTrue(anyLong());
        verify(projectRepo, never()).save(any());
    }

    @Test
    void migrateWithNoLinkedProjectIsNoOp() {
        long oldChatId = -100L;
        long newChatId = -1001L;
        when(projectRepo.findByChatIdAndIsActiveTrue(oldChatId)).thenReturn(Optional.empty());

        var message = new Message(
            1L, 0L,
            new Chat(oldChatId, "group", null, null),
            null, null, null, null, null, null,
            newChatId,
            null, null, null, null
        );

        handler.handle(message);

        verify(projectRepo, never()).save(any());
    }
}
