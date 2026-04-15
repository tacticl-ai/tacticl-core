package io.tacticl.service.connections.controller;

import io.tacticl.business.connections.service.DeviceRegistryService;
import io.tacticl.service.connections.dto.DeviceSummaryDto;
import io.tacticl.service.connections.dto.PairingTokenResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock DeviceRegistryService deviceRegistryService;
    @InjectMocks DeviceController controller;

    @Test
    void postPair_returns200WithToken() {
        var result = new DeviceRegistryService.PairingTokenResult(
            "tctl_pair_v1_abc", Instant.now().plusSeconds(900));
        when(deviceRegistryService.generatePairingToken("user1")).thenReturn(result);

        ResponseEntity<PairingTokenResponseDto> response = controller.generatePairingToken("user1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().token()).isEqualTo("tctl_pair_v1_abc");
    }

    @Test
    void getDevices_returns200() {
        when(deviceRegistryService.listDevices("user1")).thenReturn(List.of());

        ResponseEntity<List<DeviceSummaryDto>> response = controller.listDevices("user1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isInstanceOf(List.class);
    }

    @Test
    void deleteDevice_returns204() {
        ResponseEntity<Void> response = controller.unpair("dev-1", "user1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deviceRegistryService).unpair("user1", "dev-1");
    }
}
