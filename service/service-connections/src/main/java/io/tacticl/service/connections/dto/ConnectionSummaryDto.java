package io.tacticl.service.connections.dto;

public record ConnectionSummaryDto(
    String connectionId, String provider, String status,
    String accountIdentity, String lastRefreshedAt) {}
