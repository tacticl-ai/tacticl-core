package io.tacticl.service.connections.dto;

import java.util.List;

public record DeviceSummaryDto(
    String deviceId, String name, String os, String status,
    List<String> capabilities, String lastSeenAt) {}
