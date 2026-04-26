package io.tacticl.data.telegram.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

// Append-only audit trail for Telegram group-command activity. Intentionally
// does NOT extend BaseMongoEntity: audit rows are immutable, so soft-delete
// (isActive) and updatedAt have no meaning here. tacticlUserId is nullable to
// permit logging actions from a Telegram user before they have been linked
// to a Tacticl account.
@Document("telegram_audit_log")
public class TelegramAuditLog {

    public static final String DEFAULT_SOURCE = "TELEGRAM_GROUP";

    @Id
    private String id;

    @Indexed
    private long chatId;

    @Indexed
    private long telegramUserId;

    private String tacticlUserId;

    private String action;

    private String payloadJson;

    private Instant createdAt;

    private String source;

    public static TelegramAuditLog create(long chatId,
                                          long telegramUserId,
                                          String tacticlUserId,
                                          String action,
                                          String payloadJson) {
        Objects.requireNonNull(action, "action");
        var log = new TelegramAuditLog();
        log.chatId = chatId;
        log.telegramUserId = telegramUserId;
        log.tacticlUserId = tacticlUserId;
        log.action = action;
        log.payloadJson = payloadJson;
        log.createdAt = Instant.now();
        log.source = DEFAULT_SOURCE;
        return log;
    }

    public String getId() { return id; }
    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }
    public long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(long telegramUserId) { this.telegramUserId = telegramUserId; }
    public String getTacticlUserId() { return tacticlUserId; }
    public void setTacticlUserId(String tacticlUserId) { this.tacticlUserId = tacticlUserId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
