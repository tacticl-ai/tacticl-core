package io.tacticl.business.telegram.conversation;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.cloudorchestrator.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.business.conversation.dto.MessageResponse;
import io.tacticl.business.conversation.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Bridges inbound Telegram group messages onto the ConversationService:
 * find-or-create an active session per (projectId, userId), feed the message
 * through the conversation engine, render the assistant reply to chat.
 *
 * <p>This is the replacement entry point for {@code TelegramSparkInitiator} on the
 * conversational path. {@code TelegramSparkInitiator} stays in the codebase but is
 * no longer wired from /spark or plain-text mention paths after Task 6.
 *
 * <p>Decision: when a project has an ACTIVE session (pipeline running) and the user
 * sends a fresh message, we still append through that session so context survives —
 * the existing ACTIVE system prompt already tells the agent it's in execution mode.
 * Only when the session reaches COMPLETED do we start a new session.
 */
@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramConversationAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramConversationAdapter.class);
    private static final int MAX_TEXT_CHARS = 2000;
    private static final List<SessionStatus> RESUMABLE = List.of(
        SessionStatus.GATHERING, SessionStatus.PROPOSING, SessionStatus.PIPELINE_ACTIVE);

    private final ConversationService conversationService;
    private final ConversationSessionRepository sessionRepository;
    private final MemberPermissionService permissions;
    private final TelegramOutboundQueue outbound;

    public TelegramConversationAdapter(ConversationService conversationService,
                                       ConversationSessionRepository sessionRepository,
                                       MemberPermissionService permissions,
                                       TelegramOutboundQueue outbound) {
        this.conversationService = conversationService;
        this.sessionRepository = sessionRepository;
        this.permissions = permissions;
        this.outbound = outbound;
    }

    public void handle(long chatId, String tacticlUserId, String text, TelegramProjectLink link) {
        if (text == null || text.isBlank()) {
            reply(chatId, "What would you like to spark?");
            return;
        }
        if (text.length() > MAX_TEXT_CHARS) {
            reply(chatId, "Message too long (max " + MAX_TEXT_CHARS + " chars).");
            return;
        }
        PermissionCheck check = permissions.require(chatId, tacticlUserId, MemberRole.CONTRIBUTOR);
        if (!check.allowed()) {
            reply(chatId, "You need contributor role to spark in this project.");
            return;
        }

        String projectId = link.getProjectId();
        Optional<ConversationSession> active = sessionRepository
            .findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(projectId, tacticlUserId, RESUMABLE);

        ConversationSession session = active.orElseGet(() ->
            conversationService.createSession(tacticlUserId, projectId, text));

        try {
            MessageResponse response = conversationService.sendMessage(session.getId(), tacticlUserId, text);
            String body = response.getContent();
            if (response.getPipelineRunId() != null) {
                body = body + "\n\nPipeline started — I'll post updates here.";
            }
            reply(chatId, body);
        } catch (RuntimeException e) {
            log.error("Conversation turn failed for session {} in chat {}", session.getId(), chatId, e);
            reply(chatId, "I couldn't process that. Try again in a moment.");
        }
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
