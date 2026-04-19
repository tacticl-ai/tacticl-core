package io.tacticl.service.pipeline.dto;

import java.util.List;

public record PlaybookDto(
    String name,
    String displayName,
    String description,
    String tier,
    List<String> stages,
    boolean isSystemPlaybook
) {}
