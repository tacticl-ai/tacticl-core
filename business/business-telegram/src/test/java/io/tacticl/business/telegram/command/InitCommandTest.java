package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
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

class InitCommandTest {

    private TelegramIdentityResolver identity;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramOutboundQueue outbound;
    private InitCommand command;

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        command = new InitCommand(identity, projectRepo, outbound);
    }

    private static CommandContext groupCtx(long chatId,
                                           long telegramUserId,
                                           String senderUsername,
                                           String groupTitle,
                                           String text) {
        Chat chat = new Chat(chatId, "group", null, null, groupTitle);
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(chatId, telegramUserId, text, senderUsername, msg);
    }

    @Test
    void handleUnlinkedSenderDmsLinkPrompt() {
        long chatId = -100L;
        long telegramUserId = 99L;
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.empty());

        command.handle(groupCtx(chatId, telegramUserId, "alice", "My Group", "/init"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(telegramUserId), captor.capture());
        assertThat(captor.getValue().request().chat_id()).isEqualTo(telegramUserId);
        assertThat(captor.getValue().request().text()).contains("Link your Tacticl account");
        verify(projectRepo, never()).save(any());
        verify(projectRepo, never()).findByChatIdAndIsActiveTrue(anyLong());
    }

    @Test
    void handleAlreadyClaimedGroupRepliesInGroup() {
        long chatId = -100L;
        long telegramUserId = 99L;
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of("user-abc"));
        TelegramProjectLink existing = TelegramProjectLink.create(
                "proj-123", chatId, "user-abc", "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.of(existing));

        command.handle(groupCtx(chatId, telegramUserId, "alice", "My Group", "/init"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), captor.capture());
        assertThat(captor.getValue().request().chat_id()).isEqualTo(chatId);
        assertThat(captor.getValue().request().text())
                .contains("Already linked")
                .contains("proj-123");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleHappyPathPersistsLinkAndWelcomes() {
        long chatId = -100L;
        long telegramUserId = 99L;
        String tacticlUserId = "user-abc";
        String groupTitle = "My Group";
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of(tacticlUserId));
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.empty());

        command.handle(groupCtx(chatId, telegramUserId, "alice", groupTitle, "/init"));

        ArgumentCaptor<TelegramProjectLink> linkCaptor =
                ArgumentCaptor.forClass(TelegramProjectLink.class);
        verify(projectRepo).save(linkCaptor.capture());
        TelegramProjectLink saved = linkCaptor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(tacticlUserId);
        assertThat(saved.getChatId()).isEqualTo(chatId);
        assertThat(saved.getGroupTitle()).isEqualTo(groupTitle);
        assertThat(saved.getProjectId()).isNotBlank();

        ArgumentCaptor<OutboundMessage> msgCaptor =
                ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), msgCaptor.capture());
        String welcome = msgCaptor.getValue().request().text();
        assertThat(welcome)
                .contains("@alice")
                .contains(tacticlUserId)
                .contains("/grant")
                .contains("/revoke")
                .contains("/transfer")
                .contains("/members")
                .contains("/status")
                .contains("/help");
    }
}
