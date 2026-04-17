package io.tacticl.service.connections.grpc;

public class TacticlInternalRequest {
    public record GetConnectionsRequest(String userId) {}
    public record GetSecretRequest(String userId, String secretName) {}
    public record CheckpointRequest(String sparkId, String type, String prompt) {}
}
