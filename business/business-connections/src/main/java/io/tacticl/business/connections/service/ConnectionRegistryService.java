package io.tacticl.business.connections.service;

import io.tacticl.business.connections.provider.OAuthProvider;
import io.tacticl.business.connections.provider.OAuthTokens;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.repository.ConnectionRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConnectionRegistryService {

    private final Map<String, OAuthProvider> providers;
    private final ConnectionRepository connectionRepository;
    private final VaultTokenStore vaultTokenStore;

    public ConnectionRegistryService(List<OAuthProvider> providers,
                                     ConnectionRepository connectionRepository,
                                     VaultTokenStore vaultTokenStore) {
        this.providers = providers.stream()
            .collect(Collectors.toMap(p -> p.getType().name(), Function.identity()));
        this.connectionRepository = connectionRepository;
        this.vaultTokenStore = vaultTokenStore;
    }

    public String generateAuthUrl(String provider, String state, String redirectUri) {
        return resolveProvider(provider).generateAuthUrl(state, redirectUri);
    }

    public Connection handleCallback(String userId, String providerName,
                                     String code, String redirectUri) {
        var provider = resolveProvider(providerName);
        var tokens = provider.exchangeCode(code, redirectUri);

        var connection = connectionRepository.findByUserIdAndProvider(userId, providerName)
            .orElseGet(() -> Connection.create(userId, providerName,
                buildVaultPath(userId, providerName), tokens.accountIdentity(),
                List.of()));

        vaultTokenStore.store(userId, providerName.toLowerCase(), tokens);

        if (tokens.expiresAt() != null) {
            connection.markConnected(tokens.expiresAt());
        }
        return connectionRepository.save(connection);
    }

    public List<Connection> listConnections(String userId) {
        return connectionRepository.findByUserId(userId);
    }

    public Optional<Connection> getConnection(String userId, String connectionId) {
        return connectionRepository.findById(connectionId)
            .filter(conn -> {
                if (!conn.getUserId().equals(userId)) {
                    throw new SecurityException("Connection does not belong to user");
                }
                return true;
            });
    }

    public void disconnect(String userId, String connectionId) {
        var connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));

        if (!connection.getUserId().equals(userId)) {
            throw new SecurityException("Connection does not belong to user");
        }

        vaultTokenStore.revoke(connection.getVaultPath());
        connectionRepository.delete(connection);
    }

    private OAuthProvider resolveProvider(String provider) {
        var p = providers.get(provider);
        if (p == null) {
            throw new IllegalArgumentException("Unknown OAuth provider: " + provider);
        }
        return p;
    }

    private String buildVaultPath(String userId, String provider) {
        return String.format("tacticl/%s/connections/%s", userId, provider.toLowerCase());
    }
}
