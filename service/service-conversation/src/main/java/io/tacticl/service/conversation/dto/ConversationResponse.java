package io.tacticl.service.conversation.dto;

import io.tacticl.data.conversation.entity.ConversationMessage;
import io.tacticl.data.conversation.entity.ConversationSession;
import java.time.Instant;
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
        r.status = session.getStatus().name();
        r.sparkId = session.getSparkId();
        r.messages = session.getMessages();
        r.createdAt = session.getCreatedAt();
        r.updatedAt = session.getUpdatedAt();
        return r;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getSparkId() { return sparkId; }
    public List<ConversationMessage> getMessages() { return messages; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
