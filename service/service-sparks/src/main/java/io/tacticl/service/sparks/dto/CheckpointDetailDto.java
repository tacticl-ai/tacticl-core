package io.tacticl.service.sparks.dto;
public record CheckpointDetailDto(
    String checkpointId, String sparkId, String type, String prompt,
    String status, String createdAt, String resolvedAt
) {}
