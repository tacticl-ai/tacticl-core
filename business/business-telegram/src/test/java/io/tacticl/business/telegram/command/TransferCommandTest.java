package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.identity.TelegramUsernameCache;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferCommandTest {

    private TelegramIdentityResolver identity;
    private TelegramUsernameCache usernameCache;
    private MemberPermissionService permissions;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramOutboundQueue outbound;
    private TelegramAuditLogger auditLogger;
    private TransferCommand command;

    private static final long CHAT_ID = -100L;
    private static final long SENDER_TG_ID = 42L;
    private static final long TARGET_TG_ID = 77L;
    private static final String SENDER_TACTICL_ID = "user-alice";
    private static final String TARGET_TACTICL_ID = "user-bob";
    private static final String PROJECT_ID = "proj-1";

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        usernameCache = mock(TelegramUsernameCache.class);
        permissions = mock(MemberPermissionService.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        auditLogger = mock(TelegramAuditLogger.class);
        command = new TransferCommand(identity, usernameCache, permissions, projectRepo, outbound, auditLogger);
    }

    private static CommandContext groupCtx(String text) {
        Chat chat = new Chat(CHAT_ID, "group", null, null, "My Group", false);
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

        command.handle(groupCtx("/transfer @bob"));

        assertThat(capturedReplyText()).contains("must link");
        verify(permissions, never()).require(anyLong(), anyString(), any());
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleSenderNotOwnerReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.deny(MemberRole.ADMIN, MemberRole.OWNER, "insufficient role"));

        command.handle(groupCtx("/transfer @bob"));

        assertThat(capturedReplyText()).contains("Only the project owner");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleUsageInvalidReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));

        command.handle(groupCtx("/transfer"));

        assertThat(capturedReplyText()).contains("Usage: /transfer @user");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleUnknownUsernameReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(usernameCache.lookup(CHAT_ID, "bob")).thenReturn(Optional.empty());

        command.handle(groupCtx("/transfer @bob"));

        assertThat(capturedReplyText()).contains("haven't seen");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleTargetUnlinkedReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(usernameCache.lookup(CHAT_ID, "bob")).thenReturn(Optional.of(TARGET_TG_ID));
        when(identity.resolveByChatId(TARGET_TG_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/transfer @bob"));

        assertThat(capturedReplyText()).contains("must link their Tacticl account");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleTransferToSelfReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(usernameCache.lookup(CHAT_ID, "alice")).thenReturn(Optional.of(SENDER_TG_ID));
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));

        command.handle(groupCtx("/transfer @alice"));

        assertThat(capturedReplyText()).contains("already own");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleNoActiveProjectReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(usernameCache.lookup(CHAT_ID, "bob")).thenReturn(Optional.of(TARGET_TG_ID));
        when(identity.resolveByChatId(TARGET_TG_ID)).thenReturn(Optional.of(TARGET_TACTICL_ID));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/transfer @bob"));

        assertThat(capturedReplyText()).contains("No active project");
        verify(projectRepo, never()).save(any());
    }

    @Test
    void handleHappyPathRotatesOwnership() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.OWNER))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(usernameCache.lookup(CHAT_ID, "bob")).thenReturn(Optional.of(TARGET_TG_ID));
        when(identity.resolveByChatId(TARGET_TG_ID)).thenReturn(Optional.of(TARGET_TACTICL_ID));

        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, SENDER_TACTICL_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        command.handle(groupCtx("/transfer @bob"));

        ArgumentCaptor<TelegramProjectLink> linkCaptor = ArgumentCaptor.forClass(TelegramProjectLink.class);
        verify(projectRepo).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getOwnerUserId()).isEqualTo(TARGET_TACTICL_ID);

        verify(permissions).grant(CHAT_ID, SENDER_TACTICL_ID, SENDER_TG_ID, MemberRole.ADMIN, SENDER_TACTICL_ID);
        verify(permissions).revoke(CHAT_ID, TARGET_TACTICL_ID);

        assertThat(capturedReplyText())
                .contains("@bob")
                .containsIgnoringCase("transferred");
    }
}
