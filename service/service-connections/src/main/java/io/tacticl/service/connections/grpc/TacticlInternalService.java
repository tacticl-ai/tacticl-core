package io.tacticl.service.connections.grpc;

public interface TacticlInternalService {

    TacticlInternalResponse.GetConnectionsResponse getAvailableConnections(
            TacticlInternalRequest.GetConnectionsRequest request);

    TacticlInternalResponse.GetSecretResponse getSecret(
            TacticlInternalRequest.GetSecretRequest request);

    TacticlInternalResponse.CheckpointResponse reportCheckpoint(
            TacticlInternalRequest.CheckpointRequest request);
}
