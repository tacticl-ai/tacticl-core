package io.tacticl.service.pipeline.dto;

public record ResolveCheckpointDto(
    String decision,
    String feedback
) {}
