package io.tacticl.business.telegram.conversation;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.service.conversation.dto.MessageResponse;
import io.tacticl.service.conversation.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramConversationAdapterTest {

    private static final long CHAT_ID = 123L;
    private static final String USER_ID = "user-1";
    private static final String PROJECT_ID = "proj-1";

    @Mock ConversationService conversationService;
    @Mock ConversationSessionRepository sessionRepository;
    @Mock MemberPermissionService permissions;
    @Mock TelegramOutboundQueue outbound;

    TelegramConversationAdapter adapter;
    TelegramProjectLink link;

    @BeforeEach
    void setUp() {
        adapter = new TelegramConversationAdapter(
            conversationService, sessionRepository, permissions, outbound);
        link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
    }

    @Test
    void newConversation_createsSessionAndSendsFirstMessage() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
            .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        when(sessionRepository.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
            eq(PROJECT_ID), eq(USER_ID), anyCollection()))
            .thenReturn(Optional.empty());

        ConversationSession created = ConversationSession.createForTelegramGroup(USER_ID, PROJECT_ID, "build me X");
        ReflectionTestUtils.setField(created, "id", "sess-1");
        when(conversationService.createSession(USER_ID, PROJECT_ID, "build me X"))
            .thenReturn(created);
        when(conversationService.sendMessage("sess-1", USER_ID, "build me X"))
            .thenReturn(new MessageResponse("Hi — what should it do?", "GATHERING", null, null));

        adapter.handle(CHAT_ID, USER_ID, "build me X", link);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text()).isEqualTo("Hi — what should it do?");
    }

    @Test
    void existingActiveSession_isReused() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
            .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        ConversationSession existing = ConversationSession.createForTelegramGroup(
            USER_ID, PROJECT_ID, "previous request");
        ReflectionTestUtils.setField(existing, "id", "sess-existing");
        when(sessionRepository.findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
            eq(PROJECT_ID), eq(USER_ID), anyCollection()))
            .thenReturn(Optional.of(existing));
        when(conversationService.sendMessage("sess-existing", USER_ID, "yes go ahead"))
            .thenReturn(new MessageResponse("Starting!", "ACTIVE", "spark-1", "run-1"));

        adapter.handle(CHAT_ID, USER_ID, "yes go ahead", link);

        verify(conversationService, never()).createSession(any(), any(), any());
        verify(conversationService).sendMessage("sess-existing", USER_ID, "yes go ahead");

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        // Pipeline-start banner is appended when pipelineRunId is set
        assertThat(captor.getValue().request().text()).contains("Starting!");
        assertThat(captor.getValue().request().text()).contains("Pipeline started");
    }

    @Test
    void permissionDenied_repliesWithoutTouchingConversationService() {
        when(permissions.require(eq(CHAT_ID), eq(USER_ID), any()))
            .thenReturn(PermissionCheck.deny(MemberRole.OBSERVER, MemberRole.CONTRIBUTOR, "insufficient role"));

        adapter.handle(CHAT_ID, USER_ID, "anything", link);

        verifyNoInteractions(conversationService);
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text()).contains("contributor");
    }

    @Test
    void overLongText_rejected() {
        String tooLong = "x".repeat(2001);

        adapter.handle(CHAT_ID, USER_ID, tooLong, link);

        verifyNoInteractions(conversationService);
        verifyNoInteractions(permissions);
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text()).contains("too long");
    }

    @Test
    void blankText_promptsUser() {
        adapter.handle(CHAT_ID, USER_ID, "   ", link);

        verifyNoInteractions(conversationService);
        verifyNoInteractions(permissions);
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text()).isNotBlank();
    }
}
