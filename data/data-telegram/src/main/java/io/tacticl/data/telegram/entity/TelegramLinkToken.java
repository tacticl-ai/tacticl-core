package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("telegram_link_tokens")
public class TelegramLinkToken extends BaseMongoEntity {

    @Indexed(unique = true)
    private String token;

    @Indexed
    private String userId;

    private Instant issuedAt;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    private Instant consumedAt;

    public static TelegramLinkToken create(String token, String userId, int ttlMinutes) {
        var t = new TelegramLinkToken();
        t.token = token;
        t.userId = userId;
        t.issuedAt = Instant.now();
        t.expiresAt = t.issuedAt.plusSeconds(ttlMinutes * 60L);
        return t;
    }

    public boolean isConsumed() { return consumedAt != null; }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }

    public void consume() { this.consumedAt = Instant.now(); }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
}
