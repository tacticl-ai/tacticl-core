package io.tacticl.service.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitPipelineDto(
    String sparkRequest,
    String repoUrl,
    String playbook,
    List<String> skipRoles,
    String githubToken,
    Double costCeilingUsd
) {}
