package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.data.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class WhoamiCommandTest {

    private TelegramIdentityResolver identity;
    private UserProfileRepository profileRepo;
    private TelegramOutboundQueue outbound;
    private TelegramAuditLogger auditLogger;
    private WhoamiCommand command;

    private static final long DM_CHAT_ID = 42L;
    private static final long SENDER_TG_ID = 42L;
    private static final String SENDER_TACTICL_ID = "user-alice";

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        profileRepo = mock(UserProfileRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        auditLogger = mock(TelegramAuditLogger.class);
        command = new WhoamiCommand(identity, profileRepo, outbound, auditLogger);
    }

    @Test
    void scopeIsDm() {
        assertThat(command.scope()).isEqualTo(CommandHandler.Scope.DM);
    }

    @Test
    void commandNameIsWhoami() {
        assertThat(command.commandName()).isEqualTo("/whoami");
    }

    @Test
    void hasDescription() {
        assertThat(command.description()).isNotBlank();
    }

    @Test
    void unlinkedSenderRepliesWithLinkPrompt() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.empty());

        command.handle(dmCtx("/whoami", "alice"));

        assertThat(capturedReplyText())
                .containsIgnoringCase("not linked")
                .contains("Settings");
    }

    @Test
    void linkedWithProfileRepliesWithUsernameAndEmail() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        UserProfile profile = UserProfile.create(SENDER_TACTICL_ID, "Alice", "alice@example.com");
        when(profileRepo.findByCidadelUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(Optional.of(profile));

        command.handle(dmCtx("/whoami", "alice"));

        String reply = capturedReplyText();
        assertThat(reply).contains("@alice");
        assertThat(reply).contains("alice@example.com");
    }

    @Test
    void linkedWithoutProfileRepliesWithUsernameAndUserId() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(profileRepo.findByCidadelUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(Optional.empty());

        command.handle(dmCtx("/whoami", "alice"));

        String reply = capturedReplyText();
        assertThat(reply).contains("@alice");
        assertThat(reply).contains(SENDER_TACTICL_ID);
    }

    @Test
    void linkedWithoutTelegramUsernameStillReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        UserProfile profile = UserProfile.create(SENDER_TACTICL_ID, "Alice", "alice@example.com");
        when(profileRepo.findByCidadelUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(Optional.of(profile));

        command.handle(dmCtx("/whoami", null));

        String reply = capturedReplyText();
        assertThat(reply).contains("alice@example.com");
    }

    @Test
    void auditRowWrittenOnUnlinked() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.empty());

        command.handle(dmCtx("/whoami", "alice"));

        verify(auditLogger).record(eq(DM_CHAT_ID), eq(SENDER_TG_ID), eq(null), eq("WHOAMI"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void auditRowWrittenOnLinked() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(profileRepo.findByCidadelUserIdAndIsActiveTrue(SENDER_TACTICL_ID))
                .thenReturn(Optional.empty());

        command.handle(dmCtx("/whoami", "alice"));

        verify(auditLogger).record(eq(DM_CHAT_ID), eq(SENDER_TG_ID), eq(SENDER_TACTICL_ID), eq("WHOAMI"), org.mockito.ArgumentMatchers.anyString());
    }

    private static CommandContext dmCtx(String text, String username) {
        Chat chat = new Chat(DM_CHAT_ID, "private", username, "Alice", null, null);
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(DM_CHAT_ID, SENDER_TG_ID, text, username, msg);
    }

    private String capturedReplyText() {
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(DM_CHAT_ID), captor.capture());
        return captor.getValue().request().text();
    }
}
