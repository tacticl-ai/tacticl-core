package io.tacticl.data.conversation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document("conversation_sessions")
public class ConversationSession {

    @Id private String id;
    @Indexed private String userId;
    private String title;
    private SessionStatus status;
    private String detectedSparkType;
    private String proposalText;
    private String sparkId;
    private List<ConversationMessage> messages;
    private Instant createdAt;
    private Instant updatedAt;

    protected ConversationSession() {}

    public static ConversationSession create(String userId, String firstMessage) {
        ConversationSession s = new ConversationSession();
        s.id = UUID.randomUUID().toString();
        s.userId = userId;
        s.title = firstMessage.length() > 57
            ? firstMessage.substring(0, 57) + "..."
            : firstMessage;
        s.status = SessionStatus.GATHERING;
        s.messages = new ArrayList<>();
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public void addMessage(ConversationMessage message) {
        this.messages.add(message);
        this.updatedAt = Instant.now();
    }

    public void markProposing(String detectedSparkType, String proposalText) {
        this.detectedSparkType = detectedSparkType;
        this.proposalText = proposalText;
        this.status = SessionStatus.PROPOSING;
        this.updatedAt = Instant.now();
    }

    public void markActive(String sparkId) {
        if (this.status != SessionStatus.PROPOSING) {
            throw new IllegalStateException("Can only activate from PROPOSING state, current: " + this.status);
        }
        this.sparkId = sparkId;
        this.status = SessionStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void revertToGathering() {
        this.status = SessionStatus.GATHERING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = SessionStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public SessionStatus getStatus() { return status; }
    public String getDetectedSparkType() { return detectedSparkType; }
    public String getProposalText() { return proposalText; }
    public String getSparkId() { return sparkId; }
    public List<ConversationMessage> getMessages() { return messages; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
