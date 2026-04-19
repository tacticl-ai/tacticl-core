package io.tacticl.service.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArbiterCallbackDto(
    String pipelineId,
    String event,
    String agentName,
    String message,
    String status,
    String resultJson,
    String errorMessage,
    Long durationMs
) {}
