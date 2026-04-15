package io.tacticl.data.connections.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("connections")
@CompoundIndex(def = "{'userId': 1, 'provider': 1}", unique = true)
public class Connection extends BaseMongoEntity {

    @Indexed
    private String userId;
    private String provider;
    private ConnectionStatus status;
    private String accountIdentity;
    private String vaultPath;
    private List<String> scopes;
    private Instant tokenExpiresAt;
    private Instant lastRefreshedAt;

    public static Connection create(String userId, String provider, String vaultPath,
                                    String accountIdentity, List<String> scopes) {
        var c = new Connection();
        c.userId = userId;
        c.provider = provider;
        c.status = ConnectionStatus.CONNECTED;
        c.accountIdentity = accountIdentity;
        c.vaultPath = vaultPath;
        c.scopes = scopes;
        return c;
    }

    public void markExpired() { this.status = ConnectionStatus.EXPIRED; }
    public void markError() { this.status = ConnectionStatus.ERROR; }
    public void markConnected(Instant expiresAt) {
        this.status = ConnectionStatus.CONNECTED;
        this.tokenExpiresAt = expiresAt;
        this.lastRefreshedAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getProvider() { return provider; }
    public ConnectionStatus getStatus() { return status; }
    public String getAccountIdentity() { return accountIdentity; }
    public String getVaultPath() { return vaultPath; }
    public List<String> getScopes() { return scopes; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public Instant getLastRefreshedAt() { return lastRefreshedAt; }
}
