package io.tacticl.service.conversation.dto;

import io.tacticl.data.cloudorchestrator.entity.Turn;
import io.tacticl.data.conversation.entity.ConversationMessage;
import io.tacticl.data.conversation.entity.ConversationSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ConversationResponse {

    private String id;
    private String title;
    private String status;
    private String sparkId;
    private List<ConversationMessage> messages;
    private Instant createdAt;
    private Instant updatedAt;

    public static ConversationResponse from(ConversationSession session) {
        ConversationResponse r = new ConversationResponse();
        r.id = session.getId();
        r.title = session.getTitle();
        r.status = webStatus(session);
        r.sparkId = primarySparkId(session);
        r.messages = toMessages(session);
        r.createdAt = session.getCreatedAt();
        r.updatedAt = session.getUpdatedAt();
        return r;
    }

    /** Collapse the orchestrator's 10-state enum onto the 4-value web {@code ConversationStatus} union. */
    private static String webStatus(ConversationSession session) {
        return switch (session.getStatus()) {
            case IDLE, ENGAGED, GATHERING -> "GATHERING";
            case PROPOSING, CONFIRMED -> "PROPOSING";
            case PIPELINE_ACTIVE, PIPELINE_BLOCKED -> "ACTIVE";
            case COMPLETED, ABANDONED, CANCELLED -> "COMPLETED";
        };
    }

    /** Prefer a spark this session actually started; fall back to the legacy single-spark field. */
    private static String primarySparkId(ConversationSession session) {
        List<String> started = session.getSessionStartedSparkIds();
        if (started != null && !started.isEmpty()) {
            return started.get(0);
        }
        return session.getSparkId();
    }

    /** Project the durable turn transcript onto the web {role,content,timestamp} message shape. */
    private static List<ConversationMessage> toMessages(ConversationSession session) {
        List<ConversationMessage> out = new ArrayList<>();
        List<Turn> turns = session.getTurns();
        if (turns != null) {
            for (Turn t : turns) {
                out.add("user".equals(t.getRole())
                        ? ConversationMessage.user(t.getText())
                        : ConversationMessage.assistant(t.getText()));
            }
        }
        return out;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getSparkId() { return sparkId; }
    public List<ConversationMessage> getMessages() { return messages; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
