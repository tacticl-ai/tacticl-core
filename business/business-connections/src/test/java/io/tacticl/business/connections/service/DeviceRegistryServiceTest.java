package io.tacticl.business.connections.service;

import io.tacticl.data.connections.entity.Device;
import io.tacticl.data.connections.entity.PairingToken;
import io.tacticl.data.connections.repository.DeviceRepository;
import io.tacticl.data.connections.repository.PairingTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceRegistryServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock PairingTokenRepository pairingTokenRepository;
    @InjectMocks DeviceRegistryService service;

    @Test
    void generatePairingToken_returnsTctlPrefixedToken() {
        when(pairingTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.generatePairingToken("user1");

        assertThat(result.token()).startsWith("tctl_pair_v1_");
        assertThat(result.expiresAt()).isAfter(Instant.now());
        verify(pairingTokenRepository).save(any(PairingToken.class));
    }

    @Test
    void completePairing_validToken_createsDevice() {
        var token = PairingToken.create("user1", "tctl_pair_v1_abc",
            Instant.now().plusSeconds(900));
        when(pairingTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(
            eq("tctl_pair_v1_abc"), any())).thenReturn(Optional.of(token));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var device = service.completePairing("tctl_pair_v1_abc", "My Mac", "MACOS",
            "1.0.0", List.of("CLAUDE_CODE"));

        assertThat(device.getUserId()).isEqualTo("user1");
        assertThat(device.getName()).isEqualTo("My Mac");
        verify(pairingTokenRepository).save(argThat(PairingToken::isUsed));
    }

    @Test
    void completePairing_invalidToken_throws() {
        when(pairingTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completePairing("bad-token", "Mac", "MACOS", "1.0", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or expired pairing token");
    }

    @Test
    void unpair_wrongUser_throws() {
        var device = Device.create("user2", "Mac", "MACOS", "1.0", List.of());
        when(deviceRepository.findById("dev-1")).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> service.unpair("user1", "dev-1"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void listDevices_returnsUserDevices() {
        var device = Device.create("user1", "My Mac", "MACOS", "1.0", List.of("CLAUDE_CODE"));
        when(deviceRepository.findByUserId("user1")).thenReturn(List.of(device));

        var results = service.listDevices("user1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("My Mac");
    }
}
