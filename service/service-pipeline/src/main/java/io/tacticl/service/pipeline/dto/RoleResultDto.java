package io.tacticl.service.pipeline.dto;

public record RoleResultDto(
    String status,
    int iteration,
    double cost
) {}
