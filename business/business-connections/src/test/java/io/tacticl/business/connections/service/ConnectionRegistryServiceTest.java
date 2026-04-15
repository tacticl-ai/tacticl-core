package io.tacticl.business.connections.service;

import io.tacticl.business.connections.provider.OAuthProvider;
import io.tacticl.business.connections.provider.OAuthTokens;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.ConnectionStatus;
import io.tacticl.data.connections.repository.ConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionRegistryServiceTest {

    @Mock ConnectionRepository connectionRepository;
    @Mock OAuthProvider githubProvider;
    @Mock VaultTokenStore vaultTokenStore;

    private ConnectionRegistryService service;

    @BeforeEach
    void setUp() {
        when(githubProvider.getType()).thenReturn(OAuthProvider.Type.GITHUB);
        service = new ConnectionRegistryService(
            List.of(githubProvider), connectionRepository, vaultTokenStore);
    }

    @Test
    void generateAuthUrl_delegatesToProvider() {
        when(githubProvider.generateAuthUrl("state-1", "https://app/cb"))
            .thenReturn("https://github.com/auth?state=state-1");

        var url = service.generateAuthUrl("GITHUB", "state-1", "https://app/cb");

        assertThat(url).contains("github.com");
    }

    @Test
    void generateAuthUrl_unknownProvider_throws() {
        assertThatThrownBy(() -> service.generateAuthUrl("SLACK", "state", "uri"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SLACK");
    }

    @Test
    void handleCallback_savesConnectionAndStoresToken() {
        var tokens = new OAuthTokens("access-token", null, null, "@alice");
        when(githubProvider.exchangeCode("code-xyz", "https://app/cb")).thenReturn(tokens);
        when(connectionRepository.findByUserIdAndProvider("user1", "GITHUB")).thenReturn(Optional.empty());
        when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var connection = service.handleCallback("user1", "GITHUB", "code-xyz", "https://app/cb");

        assertThat(connection.getAccountIdentity()).isEqualTo("@alice");
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        verify(vaultTokenStore).store(eq("user1"), eq("github"), eq(tokens));
        verify(connectionRepository).save(any(Connection.class));
    }

    @Test
    void disconnect_deletesConnectionAndRevokesToken() {
        var connection = Connection.create("user1", "GITHUB", "vault/path", "@alice", List.of("repo"));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection));

        service.disconnect("user1", "conn-1");

        verify(vaultTokenStore).revoke("vault/path");
        verify(connectionRepository).delete(connection);
    }

    @Test
    void disconnect_wrongUser_throws() {
        var connection = Connection.create("user2", "GITHUB", "vault/path", "@alice", List.of("repo"));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.disconnect("user1", "conn-1"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void getConnection_wrongUser_throws() {
        var connection = Connection.create("user2", "GITHUB", "vault/path", "@alice", List.of("repo"));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.getConnection("user1", "conn-1"))
            .isInstanceOf(SecurityException.class);
    }
}
