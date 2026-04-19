package io.tacticl.service.connections.dto;

public record ConnectionSummaryDto(
    String id,
    String platform,
    String platformUsername,
    String profileImageUrl,
    boolean disabled,
    boolean tokenRefreshNeeded,
    String tokenExpiresAt,
    String createdAt
) {}
