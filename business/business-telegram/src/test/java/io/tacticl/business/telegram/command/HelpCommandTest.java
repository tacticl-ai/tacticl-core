package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.entity.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelpCommandTest {

    private TelegramIdentityResolver identity;
    private MemberPermissionService permissions;
    private TelegramOutboundQueue outbound;
    private HelpCommand command;

    private static final long CHAT_ID = -100L;
    private static final long SENDER_TG_ID = 42L;
    private static final String SENDER_TACTICL_ID = "user-alice";

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        permissions = mock(MemberPermissionService.class);
        outbound = mock(TelegramOutboundQueue.class);
        command = new HelpCommand(identity, permissions, outbound);
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

        command.handle(groupCtx("/help"));

        assertThat(capturedReplyText()).contains("must link");
    }

    @Test
    void handleObserverShowsBasicCommandsOnly() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.findRole(CHAT_ID, SENDER_TACTICL_ID)).thenReturn(Optional.of(MemberRole.OBSERVER));

        command.handle(groupCtx("/help"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("/help")
                .contains("/members")
                .contains("/status")
                .doesNotContain("/spark")
                .doesNotContain("/grant")
                .doesNotContain("/revoke")
                .doesNotContain("/transfer")
                .doesNotContain("/archive")
                .doesNotContain("/leave");
    }

    @Test
    void handleUnknownRoleTreatedAsObserver() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.findRole(CHAT_ID, SENDER_TACTICL_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/help"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("/help")
                .doesNotContain("/grant")
                .doesNotContain("/archive");
    }

    @Test
    void handleContributorSeesSpark() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.findRole(CHAT_ID, SENDER_TACTICL_ID)).thenReturn(Optional.of(MemberRole.CONTRIBUTOR));

        command.handle(groupCtx("/help"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("/spark")
                .doesNotContain("/grant")
                .doesNotContain("/archive");
    }

    @Test
    void handleAdminSeesGrantRevoke() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.findRole(CHAT_ID, SENDER_TACTICL_ID)).thenReturn(Optional.of(MemberRole.ADMIN));

        command.handle(groupCtx("/help"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("/spark")
                .contains("/grant")
                .contains("/revoke")
                .doesNotContain("/transfer")
                .doesNotContain("/archive")
                .doesNotContain("/leave");
    }

    @Test
    void handleOwnerSeesAllCommands() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.findRole(CHAT_ID, SENDER_TACTICL_ID)).thenReturn(Optional.of(MemberRole.OWNER));

        command.handle(groupCtx("/help"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("/help")
                .contains("/members")
                .contains("/status")
                .contains("/spark")
                .contains("/grant")
                .contains("/revoke")
                .contains("/transfer")
                .contains("/archive")
                .contains("/leave");
    }
}
