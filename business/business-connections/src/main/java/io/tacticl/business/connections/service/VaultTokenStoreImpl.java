package io.tacticl.business.connections.service;

import io.cidadel.framework.secrets.client.VaultClient;
import io.tacticl.business.connections.provider.OAuthTokens;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashMap;

@Component
public class VaultTokenStoreImpl implements VaultTokenStore {

    private final VaultClient vaultClient;

    public VaultTokenStoreImpl(VaultClient vaultClient) {
        this.vaultClient = vaultClient;
    }

    @Override
    public void store(String userId, String connectionId, OAuthTokens tokens) {
        var data = new HashMap<String, Object>();
        data.put("accessToken", tokens.accessToken());
        data.put("refreshToken", tokens.refreshToken() != null ? tokens.refreshToken() : "");
        data.put("expiresAt", tokens.expiresAt() != null ? tokens.expiresAt().toString() : "");
        vaultClient.write(buildPath(userId, connectionId), data);
    }

    @Override
    public OAuthTokens retrieve(String vaultPath) {
        var data = vaultClient.read(vaultPath);
        var expiresAtStr = (String) data.get("expiresAt");
        var expiresAt = (expiresAtStr != null && !expiresAtStr.isEmpty())
            ? Instant.parse(expiresAtStr) : null;
        var refreshToken = (String) data.get("refreshToken");
        return new OAuthTokens(
            (String) data.get("accessToken"),
            (refreshToken != null && !refreshToken.isEmpty()) ? refreshToken : null,
            expiresAt,
            null
        );
    }

    @Override
    public void revoke(String vaultPath) {
        vaultClient.delete(vaultPath);
    }

    private String buildPath(String userId, String connectionId) {
        return String.format("tacticl/%s/connections/%s", userId, connectionId);
    }
}
