package io.tacticl.service.connections.dto;

import java.util.List;
import java.util.Map;

public record DeviceSummaryDto(
    String id,
    String userId,
    String name,
    String deviceType,
    String platform,
    Object specs,
    String state,
    String lastSeenAt,
    Map<String, Object> capabilities,
    List<String> clonedRepos,
    int activeDaemons,
    String daemonVersion,
    Map<String, Boolean> sparkPreferences,
    String createdAt,
    String updatedAt
) {}
