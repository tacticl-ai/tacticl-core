package io.tacticl.service.pipeline.dto;

import java.util.Map;

public record RoleArtifactDto(
    String role,
    String artifactType,
    Map<String, Object> content,
    int artifactVersion
) {}
