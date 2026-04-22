package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveCommandTest {

    private TelegramIdentityResolver identity;
    private MemberPermissionService permissions;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramOutboundQueue outbound;
    private ArchiveCommand command;

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
        command = new ArchiveCommand(identity, permissions, projectRepo, outbound);
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

        command.handle(groupCtx("/archive"));

        assertThat(capturedReplyText()).contains("must link");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleSenderNotOwnerReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.deny(MemberRole.ADMIN, MemberRole.OWNER, "insufficient role"));

        command.handle(groupCtx("/archive"));

        assertThat(capturedReplyText()).containsIgnoringCase("owner");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleNoActiveProjectReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/archive"));

        assertThat(capturedReplyText()).contains("No active project");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleHappyPathArchivesSavesAndNotifies() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, SENDER_TACTICL_ID, "My Group");
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        command.handle(groupCtx("/archive"));

        assertThat(link.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(projectRepo).save(link);
        assertThat(capturedReplyText()).containsIgnoringCase("archived");
    }
}
