package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.cloudorchestrator.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RepoCommandTest {

    private static final long CHAT_ID = 123L;
    private static final long SENDER_TG_ID = 42L;
    private static final String SENDER_TACTICL_ID = "user-1";
    private static final String PROJECT_ID = "proj-1";

    private TelegramIdentityResolver identity;
    private MemberPermissionService permissions;
    private TelegramProjectLinkRepository projectRepo;
    private ConversationSessionRepository sessionRepo;
    private TelegramOutboundQueue outbound;
    private RepoCommand cmd;

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        permissions = mock(MemberPermissionService.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        sessionRepo = mock(ConversationSessionRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        cmd = new RepoCommand(identity, permissions, projectRepo, sessionRepo, outbound);
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
    void setsRepoUrlOnGatheringSession() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        TelegramProjectLink link = mock(TelegramProjectLink.class);
        when(link.getProjectId()).thenReturn(PROJECT_ID);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        ConversationSession session = ConversationSession.createForTelegramGroup(
                SENDER_TACTICL_ID, PROJECT_ID, "build X");
        when(sessionRepo.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
                eq(PROJECT_ID), eq(SENDER_TACTICL_ID), anyCollection()))
                .thenReturn(Optional.of(session));

        cmd.handle(groupCtx("/repo https://github.com/owner/repo"));

        assertThat(session.getRepoUrl()).isEqualTo("https://github.com/owner/repo");
        verify(sessionRepo).save(session);
        assertThat(capturedReplyText()).contains("Repo set");
    }

    @Test
    void rejectsInvalidUrl() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(eq(CHAT_ID), eq(SENDER_TACTICL_ID), any()))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));

        cmd.handle(groupCtx("/repo notaurl"));

        assertThat(capturedReplyText().toLowerCase()).contains("invalid");
        verifyNoInteractions(sessionRepo);
    }

    @Test
    void blockedWhenPipelineActive() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(eq(CHAT_ID), eq(SENDER_TACTICL_ID), any()))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        TelegramProjectLink link = mock(TelegramProjectLink.class);
        when(link.getProjectId()).thenReturn(PROJECT_ID);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        ConversationSession session = ConversationSession.createForTelegramGroup(
                SENDER_TACTICL_ID, PROJECT_ID, "x");
        session.markProposing("CODE", "plan");
        session.markActive("spark-1");
        when(sessionRepo.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
                eq(PROJECT_ID), eq(SENDER_TACTICL_ID), anyCollection()))
                .thenReturn(Optional.of(session));

        cmd.handle(groupCtx("/repo https://github.com/x/y"));

        assertThat(capturedReplyText()).contains("already running");
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void noActiveSparkReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(eq(CHAT_ID), eq(SENDER_TACTICL_ID), any()))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        TelegramProjectLink link = mock(TelegramProjectLink.class);
        when(link.getProjectId()).thenReturn(PROJECT_ID);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(sessionRepo.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
                eq(PROJECT_ID), eq(SENDER_TACTICL_ID), anyCollection()))
                .thenReturn(Optional.empty());

        cmd.handle(groupCtx("/repo https://github.com/x/y"));

        assertThat(capturedReplyText()).contains("No active spark");
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void noProjectLinkReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(eq(CHAT_ID), eq(SENDER_TACTICL_ID), any()))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        cmd.handle(groupCtx("/repo https://github.com/x/y"));

        assertThat(capturedReplyText()).contains("/init");
        verifyNoInteractions(sessionRepo);
    }

    @Test
    void senderNotLinkedReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.empty());

        cmd.handle(groupCtx("/repo https://github.com/x/y"));

        assertThat(capturedReplyText().toLowerCase()).contains("link");
        verifyNoInteractions(permissions);
        verifyNoInteractions(sessionRepo);
    }

    @Test
    void senderInsufficientRoleReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(eq(CHAT_ID), eq(SENDER_TACTICL_ID), any()))
                .thenReturn(PermissionCheck.deny(MemberRole.OBSERVER, MemberRole.CONTRIBUTOR, "insufficient"));

        cmd.handle(groupCtx("/repo https://github.com/x/y"));

        assertThat(capturedReplyText().toLowerCase()).contains("contributor");
        verifyNoInteractions(sessionRepo);
    }
}
