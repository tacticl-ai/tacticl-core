package io.tacticl.service.conversation.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.service.conversation.dto.ConversationResponse;
import io.tacticl.service.conversation.dto.CreateConversationRequest;
import io.tacticl.service.conversation.dto.MessageResponse;
import io.tacticl.service.conversation.dto.SendMessageRequest;
import io.tacticl.service.conversation.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/conversations")
public class ConversationController extends BaseController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    protected String getModuleName() { return "conversation"; }

    @PostMapping
    @RequireAuth
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthUser AuthenticatedUser user) {
        ConversationSession session = conversationService.createSession(
                user.getUserId(), request.getMessage());
        return ResponseEntity.ok(ConversationResponse.from(session));
    }

    @PostMapping("/{sessionId}/messages")
    @RequireAuth
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthUser AuthenticatedUser user) {
        MessageResponse response = conversationService.sendMessage(
                sessionId, user.getUserId(), request.getMessage());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<ConversationResponse>> listSessions(
            @AuthUser AuthenticatedUser user) {
        return ResponseEntity.ok(conversationService.listSessions(user.getUserId()));
    }

    @GetMapping("/{sessionId}")
    @RequireAuth
    public ResponseEntity<ConversationResponse> getSession(
            @PathVariable String sessionId,
            @AuthUser AuthenticatedUser user) {
        return conversationService.getSession(sessionId, user.getUserId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
