package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.Device;
import io.tacticl.data.connections.entity.PairingToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceRepositoryTest {

    @Mock DeviceRepository deviceRepo;
    @Mock PairingTokenRepository tokenRepo;

    @Test
    void findByUserId_returnsDevicesForUser() {
        var device = Device.create("user1", "My Mac", "MACOS", "1.4.2", List.of("CLAUDE_CODE"));
        when(deviceRepo.findByUserId("user1")).thenReturn(List.of(device));

        var results = deviceRepo.findByUserId("user1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUserId()).isEqualTo("user1");
    }

    @Test
    void findByUserId_returnsEmpty_whenNoDevices() {
        when(deviceRepo.findByUserId("user2")).thenReturn(List.of());

        assertThat(deviceRepo.findByUserId("user2")).isEmpty();
    }

    @Test
    void findToken_returnsPresent_whenUnusedAndNotExpired() {
        var token = PairingToken.create("user1", "tctl_pair_v1_abc", Instant.now().plusSeconds(900));
        when(tokenRepo.findByTokenAndUsedFalseAndExpiresAtAfter("tctl_pair_v1_abc", Instant.MIN))
            .thenReturn(Optional.of(token));

        var result = tokenRepo.findByTokenAndUsedFalseAndExpiresAtAfter("tctl_pair_v1_abc", Instant.MIN);

        assertThat(result).isPresent();
        assertThat(result.get().isUsed()).isFalse();
    }

    @Test
    void findToken_returnsEmpty_whenExpired() {
        when(tokenRepo.findByTokenAndUsedFalseAndExpiresAtAfter("tctl_pair_v1_expired", Instant.MIN))
            .thenReturn(Optional.empty());

        var result = tokenRepo.findByTokenAndUsedFalseAndExpiresAtAfter("tctl_pair_v1_expired", Instant.MIN);

        assertThat(result).isEmpty();
    }
}
