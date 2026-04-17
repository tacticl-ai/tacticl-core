package io.tacticl.service.connections.grpc;

import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.business.connections.service.DeviceRegistryService;
import io.tacticl.business.connections.service.SecretsVaultService;
import io.tacticl.business.connections.service.VaultTokenStore;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.Device;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TacticlInternalServiceImplTest {

    @Mock ConnectionRegistryService connectionRegistryService;
    @Mock SecretsVaultService secretsVaultService;
    @Mock DeviceRegistryService deviceRegistryService;
    @Mock VaultTokenStore vaultTokenStore;
    @InjectMocks TacticlInternalServiceImpl service;

    @Test
    void getAvailableConnections_returnsConnectionsAndDevices() {
        when(connectionRegistryService.listConnections("user-1")).thenReturn(List.of());
        when(deviceRegistryService.listDevices("user-1")).thenReturn(List.of());

        TacticlInternalResponse.GetConnectionsResponse resp =
                service.getAvailableConnections(new TacticlInternalRequest.GetConnectionsRequest("user-1"));

        assertThat(resp.connections()).isEmpty();
        assertThat(resp.devices()).isEmpty();
    }

    @Test
    void getSecret_returnsValue() {
        when(secretsVaultService.resolveValueByName("user-1", "MY_OPENAI_KEY"))
                .thenReturn(Optional.of("sk-test-value"));

        TacticlInternalResponse.GetSecretResponse resp =
                service.getSecret(new TacticlInternalRequest.GetSecretRequest("user-1", "MY_OPENAI_KEY"));

        assertThat(resp.value()).isEqualTo("sk-test-value");
    }

    @Test
    void getSecret_notFound_returnsEmptyValue() {
        when(secretsVaultService.resolveValueByName("user-1", "MISSING_KEY"))
                .thenReturn(Optional.empty());

        TacticlInternalResponse.GetSecretResponse resp =
                service.getSecret(new TacticlInternalRequest.GetSecretRequest("user-1", "MISSING_KEY"));

        assertThat(resp.value()).isEmpty();
    }

    @Test
    void reportCheckpoint_returnsEmptyCheckpointId() {
        TacticlInternalResponse.CheckpointResponse resp =
                service.reportCheckpoint(new TacticlInternalRequest.CheckpointRequest(
                        "spark-1", "APPROVAL", "Approve PR to main?"));

        assertThat(resp.checkpointId()).isEmpty();
    }
}
