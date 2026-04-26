package io.tacticl.business.telegram.command;

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

class GrantCommandTest {

    private TelegramIdentityResolver identity;
    private TelegramUsernameCache usernameCache;
    private MemberPermissionService permissions;
    private TelegramOutboundQueue outbound;
    private GrantCommand command;

    private static final long CHAT_ID = -100L;
    private static final long SENDER_TG_ID = 42L;
    private static final long TARGET_TG_ID = 77L;
    private static final String SENDER_TACTICL_ID = "user-alice";
    private static final String TARGET_TACTICL_ID = "user-bob";

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        usernameCache = mock(TelegramUsernameCache.class);
        permissions = mock(MemberPermissionService.class);
        outbound = mock(TelegramOutboundQueue.class);
        command = new GrantCommand(identity, usernameCache, permissions, outbound);
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

        command.handle(groupCtx("/grant @bob runner"));

        assertThat(capturedReplyText()).contains("must link");
        verify(permissions, never()).require(anyLong(), anyString(), any());
        verify(permissions, never()).grant(anyLong(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void handleSenderInsufficientRoleReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.deny(MemberRole.CONTRIBUTOR, MemberRole.ADMIN, "insufficient role"));

        command.handle(groupCtx("/grant @bob runner"));

        assertThat(capturedReplyText()).containsIgnoringCase("admin");
        verify(permissions, never()).grant(anyLong(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void handleUsageInvalidRoleReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.allow(MemberRole.ADMIN));

        command.handle(groupCtx("/grant @bob nonsense"));

        assertThat(capturedReplyText()).contains("Usage");
        verify(permissions, never()).grant(anyLong(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void handleUnknownUsernameReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.allow(MemberRole.ADMIN));
        when(usernameCache.lookup(CHAT_ID, "bob")).thenReturn(Optional.empty());

        command.handle(groupCtx("/grant @bob runner"));

        assertThat(capturedReplyText()).contains("haven't seen");
        verify(permissions, never()).grant(anyLong(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void handleTargetUnlinkedReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.allow(MemberRole.ADMIN));
        when(usernameCache.lookup(CHAT_ID, "bob")).thenReturn(Optional.of(TARGET_TG_ID));
        when(identity.resolveByChatId(TARGET_TG_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/grant @bob runner"));

        assertThat(capturedReplyText()).contains("must link their Tacticl account");
        verify(permissions, never()).grant(anyLong(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void handleHappyPathGrants() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(SENDER_TACTICL_ID));
        when(permissions.require(CHAT_ID, SENDER_TACTICL_ID, MemberRole.ADMIN))
                .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(usernameCache.lookup(CHAT_ID, "bob")).thenReturn(Optional.of(TARGET_TG_ID));
        when(identity.resolveByChatId(TARGET_TG_ID)).thenReturn(Optional.of(TARGET_TACTICL_ID));

        command.handle(groupCtx("/grant @bob runner"));

        verify(permissions).grant(CHAT_ID, TARGET_TACTICL_ID, TARGET_TG_ID, MemberRole.RUNNER, SENDER_TACTICL_ID);
        assertThat(capturedReplyText())
                .contains("@bob")
                .containsIgnoringCase("is now")
                .containsIgnoringCase("runner");
    }
}
