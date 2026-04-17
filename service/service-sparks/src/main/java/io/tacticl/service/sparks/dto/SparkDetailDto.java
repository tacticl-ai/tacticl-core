package io.tacticl.service.sparks.dto;
public record SparkDetailDto(
    String sparkId, String input, String status, String type, String route,
    String deviceId, String pipelineRunId, int tokenCost, String modelUsed,
    String createdAt, String startedAt, String completedAt
) {}
