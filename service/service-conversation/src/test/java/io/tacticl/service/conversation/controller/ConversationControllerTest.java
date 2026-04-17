package io.tacticl.service.conversation.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.service.conversation.dto.ConversationResponse;
import io.tacticl.service.conversation.dto.CreateConversationRequest;
import io.tacticl.service.conversation.dto.MessageResponse;
import io.tacticl.service.conversation.dto.SendMessageRequest;
import io.tacticl.service.conversation.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock ConversationService conversationService;
    ConversationController controller;

    AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        controller = new ConversationController(conversationService);
        user = mock(AuthenticatedUser.class);
        when(user.getUserId()).thenReturn("user-1");
    }

    @Test
    void createConversation_delegatesToServiceAndReturns200() {
        ConversationSession session = ConversationSession.create("user-1", "build a todo app");
        when(conversationService.createSession("user-1", "build a todo app")).thenReturn(session);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setMessage("build a todo app");

        ResponseEntity<ConversationResponse> response = controller.createConversation(request, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo("GATHERING");
    }

    @Test
    void sendMessage_delegatesToServiceAndReturnsContent() {
        MessageResponse msg = new MessageResponse("What tech stack?", "GATHERING", null, null);
        when(conversationService.sendMessage("sess-1", "user-1", "help me")).thenReturn(msg);

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage("help me");

        ResponseEntity<MessageResponse> response = controller.sendMessage("sess-1", request, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getContent()).isEqualTo("What tech stack?");
        assertThat(response.getBody().getSessionStatus()).isEqualTo("GATHERING");
    }

    @Test
    void listSessions_returnsListFromService() {
        ConversationResponse r = ConversationResponse.from(
            ConversationSession.create("user-1", "build a todo app"));
        when(conversationService.listSessions("user-1")).thenReturn(List.of(r));

        ResponseEntity<List<ConversationResponse>> response = controller.listSessions(user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getStatus()).isEqualTo("GATHERING");
    }

    @Test
    void getSession_notFound_returns404() {
        when(conversationService.getSession("missing", "user-1")).thenReturn(Optional.empty());

        ResponseEntity<ConversationResponse> response = controller.getSession("missing", user);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void getSession_found_returns200() {
        ConversationSession session = ConversationSession.create("user-1", "my session");
        when(conversationService.getSession("sess-1", "user-1"))
            .thenReturn(Optional.of(ConversationResponse.from(session)));

        ResponseEntity<ConversationResponse> response = controller.getSession("sess-1", user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo("GATHERING");
    }
}
