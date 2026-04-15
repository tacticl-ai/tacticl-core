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

}
