package io.tacticl.service.sparks.dto;
public record SparkSummaryDto(
    String sparkId, String status, String type, String route,
    String createdAt, String completedAt
) {}
