package io.tacticl.business.telegram.event;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.ChatMember;
import io.tacticl.client.telegram.dto.ChatMemberUpdate;
import io.tacticl.client.telegram.dto.User;
import io.tacticl.data.telegram.entity.ProjectStatus;
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

class GroupMembershipHandlerTest {

    private static final String BOT_USERNAME = "tacticl_bot";

    private TelegramConfig config;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramOutboundQueue outbound;
    private GroupMembershipHandler handler;

    @BeforeEach
    void setUp() {
        config = new TelegramConfig();
        config.setBotUsername(BOT_USERNAME);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        handler = new GroupMembershipHandler(config, projectRepo, outbound);
    }

    @Test
    void botAddedEnqueuesWelcome() {
        long chatId = -100L;
        var bot = new User(999L, true, BOT_USERNAME, "Tacticl");
        var update = new ChatMemberUpdate(
            new Chat(chatId, "group", null, null, null, false),
            bot,
            0L,
            new ChatMember("left", bot),
            new ChatMember("member", bot)
        );

        handler.handle(update);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), captor.capture());
        assertThat(captor.getValue().request().chat_id()).isEqualTo(chatId);
        assertThat(captor.getValue().request().text()).contains("/init");
        verifyNoInteractions(projectRepo);
    }

    @Test
    void botRemovedOrphansExistingProject() {
        long chatId = -100L;
        var bot = new User(999L, true, BOT_USERNAME, "Tacticl");
        var update = new ChatMemberUpdate(
            new Chat(chatId, "group", null, null, null, false),
            bot,
            0L,
            new ChatMember("administrator", bot),
            new ChatMember("kicked", bot)
        );
        var link = TelegramProjectLink.create("proj-1", chatId, "user-1", "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.of(link));

        handler.handle(update);

        ArgumentCaptor<TelegramProjectLink> captor = ArgumentCaptor.forClass(TelegramProjectLink.class);
        verify(projectRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProjectStatus.ORPHANED);
        verify(outbound, never()).enqueue(anyLong(), any());
    }

    @Test
    void nonBotUserEventIgnored() {
        long chatId = -100L;
        var otherUser = new User(42L, false, "alice", "Alice");
        var update = new ChatMemberUpdate(
            new Chat(chatId, "group", null, null, null, false),
            otherUser,
            0L,
            new ChatMember("left", otherUser),
            new ChatMember("member", otherUser)
        );

        handler.handle(update);

        verify(outbound, never()).enqueue(anyLong(), any());
        verify(projectRepo, never()).save(any());
        verify(projectRepo, never()).findByChatIdAndIsActiveTrue(anyLong());
    }

    @Test
    void botRemovedWithNoLinkedProjectIsNoOp() {
        long chatId = -100L;
        var bot = new User(999L, true, BOT_USERNAME, "Tacticl");
        var update = new ChatMemberUpdate(
            new Chat(chatId, "group", null, null, null, false),
            bot,
            0L,
            new ChatMember("member", bot),
            new ChatMember("left", bot)
        );
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.empty());

        handler.handle(update);

        verify(projectRepo, never()).save(any());
        verify(outbound, never()).enqueue(anyLong(), any());
    }
}
