package io.tacticl.data.connections.entity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionTest {

    @Test
    void create_setsConnectedStatus() {
        var conn = Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo"));
        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    void markExpired_changesStatus() {
        var conn = Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo"));
        conn.markExpired();
        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.EXPIRED);
    }

    @Test
    void markError_changesStatus() {
        var conn = Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo"));
        conn.markError();
        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.ERROR);
    }

    @Test
    void markConnected_restoresConnectedStatus() {
        var conn = Connection.create("user1", "GITHUB", "vault/path/1", "@alice", List.of("repo"));
        conn.markExpired();
        conn.markConnected(Instant.now().plusSeconds(3600));
        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(conn.getLastRefreshedAt()).isNotNull();
    }
}
