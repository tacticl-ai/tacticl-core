package io.tacticl.business.connections.service;

import io.tacticl.business.connections.provider.OAuthTokens;

public interface VaultTokenStore {
    void store(String userId, String connectionId, OAuthTokens tokens);
    OAuthTokens retrieve(String vaultPath);
    void revoke(String vaultPath);
}
