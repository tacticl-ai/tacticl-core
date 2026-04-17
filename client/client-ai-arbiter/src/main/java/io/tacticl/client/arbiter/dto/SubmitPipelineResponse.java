package io.tacticl.client.arbiter.dto;

public record SubmitPipelineResponse(
    String pipelineRunId,     // our internal run ID (echoed back)
    String arbiterPipelineId, // arbiter's own pipeline ID (use for cancel/status calls)
    String status
) {}
