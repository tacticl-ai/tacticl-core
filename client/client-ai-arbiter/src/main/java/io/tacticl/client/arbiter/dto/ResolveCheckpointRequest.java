package io.tacticl.client.arbiter.dto;

public record ResolveCheckpointRequest(
    String pipelineRunId,
    String checkpointId,
    String decision,
    String feedback
) {}
