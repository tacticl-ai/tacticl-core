package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.TelegramUserLinker;
import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramLink;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import io.tacticl.data.telegram.repository.TelegramMemberGrantRepository;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
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

class UnlinkCommandTest {

    private TelegramIdentityResolver identity;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramMemberGrantRepository grantRepo;
    private TelegramLinkRepository linkRepo;
    private TelegramUserLinker userLinker;
    private TelegramOutboundQueue outbound;
    private TelegramAuditLogger auditLogger;
    private UnlinkCommand command;

    private static final long DM_CHAT_ID = 42L;
    private static final long SENDER_TG_ID = 42L;
    private static final String SENDER_TACTICL_ID = "user-alice";

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        grantRepo = mock(TelegramMemberGrantRepository.class);
        linkRepo = mock(TelegramLinkRepository.class);
        userLinker = mock(TelegramUserLinker.class);
        outbound = mock(TelegramOutboundQueue.class);
        auditLogger = mock(TelegramAuditLogger.class);
        command = new UnlinkCommand(identity, projectRepo, grantRepo, linkRepo, userLinker, outbound, auditLogger);
    }

    @Test
    void scopeIsDm() {
        assertThat(command.scope()).isEqualTo(CommandHandler.Scope.DM);
    }

    @Test
    void commandNameIsUnlink() {
        assertThat(command.commandName()).isEqualTo("/unlink");
    }

    @Test
    void hasDescription() {
        assertThat(command.description()).isNotBlank();
    }

    @Test
    void unlinkedSenderRepliesNotLinked() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.empty());

        command.handle(dmCtx());

        assertThat(capturedReplyText()).containsIgnoringCase("weren't linked");
        verify(userLinker, never()).unlink(anyString(), anyLong());
        verify(grantRepo, never()).save(any());
    }

    @Test
    void soleOwnerOfActiveProjectIsBlocked() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        TelegramProjectLink owned = TelegramProjectLink.create("p1", -100L, SENDER_TACTICL_ID, "Acme Group");
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(owned));

        command.handle(dmCtx());

        String reply = capturedReplyText();
        assertThat(reply).containsIgnoringCase("transfer");
        assertThat(reply).contains("Acme Group");
        verify(userLinker, never()).unlink(anyString(), anyLong());
        verify(grantRepo, never()).save(any());
    }

    @Test
    void multipleOwnedProjectsAllListedInBlockMessage() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        TelegramProjectLink p1 = TelegramProjectLink.create("p1", -100L, SENDER_TACTICL_ID, "Acme Group");
        TelegramProjectLink p2 = TelegramProjectLink.create("p2", -200L, SENDER_TACTICL_ID, "Beta Group");
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(p1, p2));

        command.handle(dmCtx());

        String reply = capturedReplyText();
        assertThat(reply).contains("Acme Group");
        assertThat(reply).contains("Beta Group");
        verify(userLinker, never()).unlink(anyString(), anyLong());
    }

    @Test
    void noOwnedProjectsHappyPathDeletesGrantsAndUnlinks() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());

        TelegramMemberGrant g1 = TelegramMemberGrant.create(
                "p1", -100L, SENDER_TACTICL_ID, SENDER_TG_ID, MemberRole.RUNNER, "user-owner");
        TelegramMemberGrant g2 = TelegramMemberGrant.create(
                "p2", -200L, SENDER_TACTICL_ID, SENDER_TG_ID, MemberRole.CONTRIBUTOR, "user-owner");
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(g1, g2));

        TelegramLink link = TelegramLink.create(SENDER_TACTICL_ID, DM_CHAT_ID, "alice", "Alice");
        when(linkRepo.findByUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(link));

        command.handle(dmCtx());

        // Both grants soft-deleted (saved with isActive=false).
        verify(grantRepo, org.mockito.Mockito.times(2)).save(any(TelegramMemberGrant.class));
        // The DM link unlinked via the user linker.
        verify(userLinker).unlink(SENDER_TACTICL_ID, DM_CHAT_ID);
        // Confirmation reply.
        assertThat(capturedReplyText()).containsIgnoringCase("unlinked");
    }

    @Test
    void noGrantsButLinkedHappyPathStillUnlinks() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());
        TelegramLink link = TelegramLink.create(SENDER_TACTICL_ID, DM_CHAT_ID, "alice", "Alice");
        when(linkRepo.findByUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(link));

        command.handle(dmCtx());

        verify(grantRepo, never()).save(any());
        verify(userLinker).unlink(SENDER_TACTICL_ID, DM_CHAT_ID);
        assertThat(capturedReplyText()).containsIgnoringCase("unlinked");
    }

    @Test
    void multipleLinksAllUnlinked() {
        // WHY: a user may have multiple active TelegramLink rows (DM + a direct
        // bot DM from another device, edge case). Unlink all of them.
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());
        TelegramLink l1 = TelegramLink.create(SENDER_TACTICL_ID, DM_CHAT_ID, "alice", "Alice");
        TelegramLink l2 = TelegramLink.create(SENDER_TACTICL_ID, 999L, "alice", "Alice");
        when(linkRepo.findByUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(l1, l2));

        command.handle(dmCtx());

        verify(userLinker).unlink(SENDER_TACTICL_ID, DM_CHAT_ID);
        verify(userLinker).unlink(SENDER_TACTICL_ID, 999L);
    }

    @Test
    void auditRowWrittenOnSuccess() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());
        when(grantRepo.findByTacticlUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());
        when(linkRepo.findByUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of());

        command.handle(dmCtx());

        verify(auditLogger).record(eq(DM_CHAT_ID), eq(SENDER_TG_ID), eq(SENDER_TACTICL_ID), eq("UNLINK"), anyString());
    }

    @Test
    void auditRowWrittenOnBlockedByOwnership() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        TelegramProjectLink owned = TelegramProjectLink.create("p1", -100L, SENDER_TACTICL_ID, "Acme Group");
        when(projectRepo.findByOwnerUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(List.of(owned));

        command.handle(dmCtx());

        verify(auditLogger).record(eq(DM_CHAT_ID), eq(SENDER_TG_ID), eq(SENDER_TACTICL_ID), eq("UNLINK"), anyString());
    }

    private static CommandContext dmCtx() {
        Chat chat = new Chat(DM_CHAT_ID, "private", "alice", "Alice", null, null);
        Message msg = new Message(
                1L, 0L, chat, null, "/unlink",
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(DM_CHAT_ID, SENDER_TG_ID, "/unlink", "alice", msg);
    }

    private String capturedReplyText() {
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(DM_CHAT_ID), captor.capture());
        return captor.getValue().request().text();
    }
}
