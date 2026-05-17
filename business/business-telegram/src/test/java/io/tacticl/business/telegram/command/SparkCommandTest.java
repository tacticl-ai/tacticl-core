package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.conversation.TelegramConversationAdapter;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SparkCommandTest {

    private static final long CHAT_ID = -100L;
    private static final long SENDER_TG_ID = 42L;
    private static final String TACTICL_USER_ID = "user-alice";
    private static final String PROJECT_ID = "proj-1";

    private TelegramIdentityResolver identity;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramConversationAdapter adapter;
    private TelegramOutboundQueue outbound;
    private SparkCommand command;

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        adapter = mock(TelegramConversationAdapter.class);
        outbound = mock(TelegramOutboundQueue.class);
        command = new SparkCommand(identity, projectRepo, adapter, outbound);
    }

    private static CommandContext groupCtx(String text) {
        Chat chat = new Chat(CHAT_ID, "group", null, null, "My Group", false);
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(CHAT_ID, SENDER_TG_ID, text, "alice", msg);
    }

    @Test
    void commandMetadata() {
        assertThat(command.commandName()).isEqualTo("/spark");
        assertThat(command.scope()).isEqualTo(SparkCommand.Scope.GROUP);
    }

    @Test
    void handleUnlinkedSenderReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/spark deploy frontend"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text())
                .containsIgnoringCase("link your Tacticl account");
        verify(adapter, never()).handle(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void handleNoProjectReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(TACTICL_USER_ID));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/spark deploy frontend"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text())
                .containsIgnoringCase("No active project")
                .contains("/init");
        verify(adapter, never()).handle(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void handleBlankArgsReplies() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(TACTICL_USER_ID));
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, TACTICL_USER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        command.handle(groupCtx("/spark"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text()).contains("Usage: /spark");
        verify(adapter, never()).handle(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void handleHappyPathDelegatesToAdapter() {
        when(identity.resolveByChatId(SENDER_TG_ID)).thenReturn(Optional.of(TACTICL_USER_ID));
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, TACTICL_USER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        command.handle(groupCtx("/spark  deploy frontend"));

        verify(adapter).handle(eq(CHAT_ID), eq(TACTICL_USER_ID),
                eq("deploy frontend"), eq(link));
        // SparkCommand does not reply; adapter owns all user-facing replies.
        verify(outbound, never()).enqueue(anyLong(), any());
    }
}
