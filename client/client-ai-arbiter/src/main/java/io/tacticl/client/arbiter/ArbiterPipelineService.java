package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.*;

public interface ArbiterPipelineService {
    SubmitPipelineResponse submitPipeline(SubmitPipelineRequest request);
    void resolveCheckpoint(ResolveCheckpointRequest request);
    PipelineStatusResponse getPipelineStatus(String pipelineRunId);
    void cancelPipeline(String pipelineRunId);
}
