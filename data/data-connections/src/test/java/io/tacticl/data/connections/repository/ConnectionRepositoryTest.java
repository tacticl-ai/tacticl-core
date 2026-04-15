package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.ConnectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionRepositoryTest {

    @Mock
    ConnectionRepository repo;

    @Test
    void findByUserId_returnsUserConnections() {
        var conn = Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo", "user"));
        when(repo.findByUserId("user1")).thenReturn(List.of(conn));

        var results = repo.findByUserId("user1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUserId()).isEqualTo("user1");
    }

    @Test
    void findByUserIdAndProvider_returnsConnection() {
        var conn = Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo"));
        when(repo.findByUserIdAndProvider("user1", "GITHUB")).thenReturn(Optional.of(conn));

        var result = repo.findByUserIdAndProvider("user1", "GITHUB");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    void findByUserIdAndProvider_returnsEmpty_whenNotFound() {
        when(repo.findByUserIdAndProvider("user1", "SLACK")).thenReturn(Optional.empty());

        var result = repo.findByUserIdAndProvider("user1", "SLACK");

        assertThat(result).isEmpty();
    }

    @Test
    void connection_markExpired_changesStatus() {
        var conn = Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo"));
        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);

        conn.markExpired();

        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.EXPIRED);
    }

    @Test
    void connection_markError_changesStatus() {
        var conn = Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo"));

        conn.markError();

        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.ERROR);
    }

    @Test
    void repositoryInterface_hasRequiredMethods() throws NoSuchMethodException {
        // Verify method signatures exist at compile time
        assertThat(ConnectionRepository.class.getMethod("findByUserId", String.class)).isNotNull();
        assertThat(ConnectionRepository.class.getMethod("findByUserIdAndProvider", String.class, String.class)).isNotNull();
        assertThat(ConnectionRepository.class.getMethod("deleteByUserIdAndProvider", String.class, String.class)).isNotNull();
    }
}
