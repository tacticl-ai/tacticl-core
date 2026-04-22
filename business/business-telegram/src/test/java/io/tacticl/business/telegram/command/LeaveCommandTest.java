package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.entity.MemberRole;
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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveCommandTest {

    private TelegramIdentityResolver identity;
    private MemberPermissionService permissions;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramOutboundQueue outbound;
    private TelegramBotClient bot;
    private LeaveCommand command;

    private static final long CHAT_ID = -100L;
    private static final long SENDER_TG_ID = 42L;
    private static final String SENDER_TACTICL_ID = "user-alice";
    private static final String PROJECT_ID = "proj-1";

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        permissions = mock(MemberPermissionService.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        bot = mock(TelegramBotClient.class);
        command = new LeaveCommand(identity, permissions, projectRepo, outbound, bot);
    }

    private static CommandContext groupCtx(String text) {
        Chat chat = new Chat(CHAT_ID, "group", null, null, "My Group");
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(CHAT_ID, SENDER_TG_ID, text, "alice", msg);
    }

    private String capturedReplyText() {
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        return captor.getValue().request().text();
    }

    @Test
    void handleSenderNotLinkedReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/leave"));

        assertThat(capturedReplyText()).contains("must link");
        verify(projectRepo, never()).save(any());
        verify(bot, never()).leaveChat(anyLong());
    }

    @Test
    void handleContributorRoleDenied() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.deny(MemberRole.CONTRIBUTOR, MemberRole.ADMIN, "insufficient role"));

        command.handle(groupCtx("/leave"));

        assertThat(capturedReplyText()).containsIgnoringCase("owners or admins");
        verify(projectRepo, never()).save(any());
        verify(bot, never()).leaveChat(anyLong());
    }

    @Test
    void handleNoActiveProjectReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.allow(MemberRole.ADMIN));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/leave"));

        assertThat(capturedReplyText()).contains("No active project");
        verify(projectRepo, never()).save(any());
        verify(bot, never()).leaveChat(anyLong());
    }

    @Test
    void handleHappyPathArchivesFarewellsAndLeaves() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, SENDER_TACTICL_ID, "My Group");
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(bot.leaveChat(CHAT_ID)).thenReturn(true);

        command.handle(groupCtx("/leave"));

        assertThat(link.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(projectRepo).save(link);
        assertThat(capturedReplyText()).containsIgnoringCase("leaving");
        verify(bot).leaveChat(CHAT_ID);
    }

    @Test
    void handleLeaveChatFailureSwallowed() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, SENDER_TACTICL_ID, "My Group");
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(bot.leaveChat(CHAT_ID)).thenThrow(new RuntimeException("telegram down"));

        command.handle(groupCtx("/leave"));

        assertThat(link.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(projectRepo).save(link);
        verify(bot).leaveChat(CHAT_ID);
    }
}
