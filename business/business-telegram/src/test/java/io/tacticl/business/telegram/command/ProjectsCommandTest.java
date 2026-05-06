package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramMemberGrantRepository;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectsCommandTest {

    private TelegramIdentityResolver identity;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramMemberGrantRepository grantRepo;
    private TelegramOutboundQueue outbound;
    private TelegramAuditLogger auditLogger;
    private ProjectsCommand command;

    private static final long DM_CHAT_ID = 42L;
    private static final long SENDER_TG_ID = 42L;
    private static final String SENDER_TACTICL_ID = "user-alice";

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        grantRepo = mock(TelegramMemberGrantRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        auditLogger = mock(TelegramAuditLogger.class);
        command = new ProjectsCommand(identity, projectRepo, grantRepo, outbound, auditLogger);
    }

    @Test
    void scopeIsDm() {
        assertThat(command.scope()).isEqualTo(CommandHandler.Scope.DM);
    }

    @Test
    void commandNameIsProjects() {
        assertThat(command.commandName()).isEqualTo("/projects");
    }

    @Test
    void hasDescription() {
        assertThat(command.description()).isNotBlank();
    }

    @Test
    void unlinkedSenderRepliesWithLinkPrompt() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.empty());

        command.handle(dmCtx());

        assertThat(capturedReplyText())
                .containsIgnoringCase("not linked")
                .contains("Settings");
    }

    @Test
    void linkedWithNoProjectsRepliesEmptyMessage() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());

        command.handle(dmCtx());

        assertThat(capturedReplyText())
                .containsIgnoringCase("not in any");
    }

    @Test
    void listsOwnedProjectsAsOwner() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        TelegramProjectLink owned = TelegramProjectLink.create("p1", -100L, SENDER_TACTICL_ID, "Acme Group");
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(owned));
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());

        command.handle(dmCtx());

        String reply = capturedReplyText();
        assertThat(reply).contains("Acme Group");
        assertThat(reply).contains("OWNER");
    }

    @Test
    void listsGrantedProjectsWithRole() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());

        TelegramMemberGrant grant = TelegramMemberGrant.create(
                "p2", -200L, SENDER_TACTICL_ID, SENDER_TG_ID, MemberRole.RUNNER, "user-owner");
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(grant));

        TelegramProjectLink p2 = TelegramProjectLink.create("p2", -200L, "user-owner", "Other Group");
        when(projectRepo.findByProjectIdAndIsActiveTrue("p2"))
                .thenReturn(Optional.of(p2));

        command.handle(dmCtx());

        String reply = capturedReplyText();
        assertThat(reply).contains("Other Group");
        assertThat(reply).contains("RUNNER");
    }

    @Test
    void mergesOwnedAndGrantedProjects() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));

        TelegramProjectLink owned = TelegramProjectLink.create("p1", -100L, SENDER_TACTICL_ID, "Acme Group");
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(owned));

        TelegramMemberGrant grant = TelegramMemberGrant.create(
                "p2", -200L, SENDER_TACTICL_ID, SENDER_TG_ID, MemberRole.RUNNER, "user-owner");
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(grant));

        TelegramProjectLink p2 = TelegramProjectLink.create("p2", -200L, "user-owner", "Other Group");
        when(projectRepo.findByProjectIdAndIsActiveTrue("p2"))
                .thenReturn(Optional.of(p2));

        command.handle(dmCtx());

        String reply = capturedReplyText();
        assertThat(reply).contains("Acme Group").contains("OWNER");
        assertThat(reply).contains("Other Group").contains("RUNNER");
    }

    @Test
    void grantWithMissingProjectLinkSkipped() {
        // WHY: a stale grant for a project whose link was archived must not cause a NullPointer
        // or a malformed line — silently drop and keep listing the rest.
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());

        TelegramMemberGrant orphanGrant = TelegramMemberGrant.create(
                "p-gone", -300L, SENDER_TACTICL_ID, SENDER_TG_ID, MemberRole.CONTRIBUTOR, "user-owner");
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(orphanGrant));
        when(projectRepo.findByProjectIdAndIsActiveTrue("p-gone"))
                .thenReturn(Optional.empty());

        command.handle(dmCtx());

        // Falls back to the empty-list message because no rows survived the join.
        assertThat(capturedReplyText()).containsIgnoringCase("not in any");
    }

    @Test
    void auditRowWritten() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());

        command.handle(dmCtx());

        verify(auditLogger).record(eq(DM_CHAT_ID), eq(SENDER_TG_ID), eq(SENDER_TACTICL_ID), eq("PROJECTS"), org.mockito.ArgumentMatchers.anyString());
    }

    private static CommandContext dmCtx() {
        Chat chat = new Chat(DM_CHAT_ID, "private", "alice", "Alice", null, null);
        Message msg = new Message(
                1L, 0L, chat, null, "/projects",
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(DM_CHAT_ID, SENDER_TG_ID, "/projects", "alice", msg);
    }

    private String capturedReplyText() {
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(DM_CHAT_ID), captor.capture());
        return captor.getValue().request().text();
    }
}
