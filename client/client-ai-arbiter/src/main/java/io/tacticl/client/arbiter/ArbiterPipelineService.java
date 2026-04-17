package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.PipelineResultResponse;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;

/**
 * Abstraction over the cidadel-ai-arbiter gRPC service.
 * Implementations: ArbiterGrpcClientImpl (real) and ArbiterPipelineServiceStub (fallback).
 */
public interface ArbiterPipelineService {
    SubmitPipelineResponse submitPipeline(SubmitPipelineRequest request);
    void cancelPipeline(String arbiterPipelineId);
    PipelineResultResponse getResult(String arbiterPipelineId);
    void resolveCheckpoint(String arbiterPipelineId, String checkpointId,
                           String decision, String feedback);
}
