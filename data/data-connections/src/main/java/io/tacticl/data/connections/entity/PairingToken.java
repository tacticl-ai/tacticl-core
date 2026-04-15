package io.tacticl.data.connections.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("pairing_tokens")
public class PairingToken extends BaseMongoEntity {

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private String userId;
    private String token;
    private boolean used;

    public static PairingToken create(String userId, String token, Instant expiresAt) {
        var pt = new PairingToken();
        pt.userId = userId;
        pt.token = token;
        pt.expiresAt = expiresAt;
        pt.used = false;
        return pt;
    }

    public void markUsed() { this.used = true; }

    public String getUserId() { return userId; }
    public String getToken() { return token; }
    public boolean isUsed() { return used; }
    public Instant getExpiresAt() { return expiresAt; }
}
