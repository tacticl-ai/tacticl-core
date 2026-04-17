package io.tacticl.service.connections.grpc;

import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.Device;
import java.util.List;

public class TacticlInternalResponse {

    public record ConnectionInfo(
        String connectionId, String provider, String accessToken,
        String accountIdentity, List<String> scopes
    ) {}

    public record DeviceInfo(
        String deviceId, String name, String os,
        String status, List<String> capabilities
    ) {}

    public record GetConnectionsResponse(
        List<ConnectionInfo> connections,
        List<DeviceInfo> devices
    ) {}

    public record GetSecretResponse(String value) {}

    public record CheckpointResponse(String checkpointId) {}
}
