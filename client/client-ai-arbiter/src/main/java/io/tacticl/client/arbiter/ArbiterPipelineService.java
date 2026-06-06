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

    /**
     * Resume a parked Temporal merge/interview gate (SignalPipelineDecision). The
     * {@code workflowId} is the arbiterPipelineId (= {@code pdlc-{pipelineRunId}}); the
     * {@code askId}+{@code gateNonce} echo the blocked callback so the workflow accepts the
     * live ask. {@code decision} is the arbiter MergeDecisionKind string
     * (APPROVE_MERGE | REJECT | GRANT_REWORK | ANSWER_ASK).
     */
    void signalDecision(String workflowId, String askId, String decision,
                        String approvedSha, String gateNonce, String approver, String reason);
}
