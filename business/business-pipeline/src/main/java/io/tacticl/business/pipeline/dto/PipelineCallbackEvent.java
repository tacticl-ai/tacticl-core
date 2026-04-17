package io.tacticl.business.pipeline.dto;

public record PipelineCallbackEvent(
    String pipelineRunId,
    String eventType,
    String role,
    String phase,
    String payloadJson
) {}
