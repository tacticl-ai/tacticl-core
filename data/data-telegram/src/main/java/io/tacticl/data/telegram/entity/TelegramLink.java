package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("telegram_links")
@CompoundIndex(name = "user_chat_unique", def = "{'userId': 1, 'chatId': 1}", unique = true)
public class TelegramLink extends BaseMongoEntity {

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private long chatId;

    private String username;
    private String firstName;
    private Instant linkedAt;
    private NotificationPrefs notificationPrefs = new NotificationPrefs();

    public static TelegramLink create(String userId, long chatId, String username, String firstName) {
        var link = new TelegramLink();
        link.userId = userId;
        link.chatId = chatId;
        link.username = username;
        link.firstName = firstName;
        link.linkedAt = Instant.now();
        return link;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public Instant getLinkedAt() { return linkedAt; }
    public void setLinkedAt(Instant linkedAt) { this.linkedAt = linkedAt; }

    public NotificationPrefs getNotificationPrefs() { return notificationPrefs; }
    public void setNotificationPrefs(NotificationPrefs notificationPrefs) {
        this.notificationPrefs = notificationPrefs;
    }
}
