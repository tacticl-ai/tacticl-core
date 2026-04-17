package io.tacticl.data.conversation.entity;

import java.time.Instant;

public class ConversationMessage {

    private String role;
    private String content;
    private Instant timestamp;

    protected ConversationMessage() {}

    public static ConversationMessage user(String content) {
        ConversationMessage m = new ConversationMessage();
        m.role = "user";
        m.content = content;
        m.timestamp = Instant.now();
        return m;
    }

    public static ConversationMessage assistant(String content) {
        ConversationMessage m = new ConversationMessage();
        m.role = "assistant";
        m.content = content;
        m.timestamp = Instant.now();
        return m;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getTimestamp() { return timestamp; }
}
