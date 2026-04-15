package io.tacticl.data.connections.entity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class PairingTokenTest {

    @Test
    void create_setsUnusedByDefault() {
        var token = PairingToken.create("user1", "tctl_pair_v1_abc123", Instant.now().plusSeconds(900));
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getUserId()).isEqualTo("user1");
        assertThat(token.getToken()).isEqualTo("tctl_pair_v1_abc123");
    }

    @Test
    void markUsed_setsUsedTrue() {
        var token = PairingToken.create("user1", "tctl_pair_v1_abc123", Instant.now().plusSeconds(900));
        token.markUsed();
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void expiresAt_isSetCorrectly() {
        var expiresAt = Instant.now().plusSeconds(900);
        var token = PairingToken.create("user1", "tctl_pair_v1_abc123", expiresAt);
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
    }
}
