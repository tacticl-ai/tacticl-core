package io.tacticl.client.arbiter.dto;

public record PipelineStatusResponse(
    String pipelineRunId,
    String status
) {}
