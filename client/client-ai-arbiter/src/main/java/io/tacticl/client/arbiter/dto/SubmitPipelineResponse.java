package io.tacticl.client.arbiter.dto;

public record SubmitPipelineResponse(
    String pipelineRunId,
    String status
) {}
