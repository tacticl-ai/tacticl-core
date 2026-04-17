package io.tacticl.service.connections.grpc;

import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.business.connections.service.DeviceRegistryService;
import io.tacticl.business.connections.service.SecretsVaultService;
import io.tacticl.business.connections.service.VaultTokenStore;
import io.tacticl.business.connections.provider.OAuthTokens;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.Device;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TacticlInternalServiceImpl implements TacticlInternalService {

    private final ConnectionRegistryService connectionRegistryService;
    private final SecretsVaultService secretsVaultService;
    private final DeviceRegistryService deviceRegistryService;
    private final VaultTokenStore vaultTokenStore;

    public TacticlInternalServiceImpl(ConnectionRegistryService connectionRegistryService,
                                       SecretsVaultService secretsVaultService,
                                       DeviceRegistryService deviceRegistryService,
                                       VaultTokenStore vaultTokenStore) {
        this.connectionRegistryService = connectionRegistryService;
        this.secretsVaultService = secretsVaultService;
        this.deviceRegistryService = deviceRegistryService;
        this.vaultTokenStore = vaultTokenStore;
    }

    @Override
    public TacticlInternalResponse.GetConnectionsResponse getAvailableConnections(
            TacticlInternalRequest.GetConnectionsRequest request) {

        List<TacticlInternalResponse.ConnectionInfo> connections =
                connectionRegistryService.listConnections(request.userId())
                        .stream()
                        .map(c -> {
                            String accessToken = resolveAccessToken(c);
                            return new TacticlInternalResponse.ConnectionInfo(
                                    c.getId(), c.getProvider(), accessToken,
                                    c.getAccountIdentity(), c.getScopes());
                        }).toList();

        List<TacticlInternalResponse.DeviceInfo> devices =
                deviceRegistryService.listDevices(request.userId())
                        .stream()
                        .map(d -> new TacticlInternalResponse.DeviceInfo(
                                d.getId(), d.getName(), d.getOs(),
                                d.getStatus().name(), d.getCapabilities()))
                        .toList();

        return new TacticlInternalResponse.GetConnectionsResponse(connections, devices);
    }

    @Override
    public TacticlInternalResponse.GetSecretResponse getSecret(
            TacticlInternalRequest.GetSecretRequest request) {
        String value = secretsVaultService
                .resolveValueByName(request.userId(), request.secretName())
                .orElse("");
        return new TacticlInternalResponse.GetSecretResponse(value);
    }

    @Override
    public TacticlInternalResponse.CheckpointResponse reportCheckpoint(
            TacticlInternalRequest.CheckpointRequest request) {
        return new TacticlInternalResponse.CheckpointResponse("");
    }

    private String resolveAccessToken(Connection connection) {
        try {
            OAuthTokens tokens = vaultTokenStore.retrieve(connection.getVaultPath());
            return tokens != null ? tokens.accessToken() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
