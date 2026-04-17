package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of ArbiterPipelineService.
 * Used until the real gRPC connection to cidadel-ai-arbiter is established.
 * All methods log the call and return safe defaults.
 */
@Service
public class ArbiterPipelineServiceStub implements ArbiterPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ArbiterPipelineServiceStub.class);

    @Override
    public SubmitPipelineResponse submitPipeline(SubmitPipelineRequest request) {
        log.info("[STUB] submitPipeline: runId={} sparkId={} playbook={}",
                 request.pipelineRunId(), request.sparkId(), request.playbook());
        return new SubmitPipelineResponse(request.pipelineRunId(), "PENDING");
    }

    @Override
    public void resolveCheckpoint(ResolveCheckpointRequest request) {
        log.info("[STUB] resolveCheckpoint: runId={} checkpointId={} decision={}",
                 request.pipelineRunId(), request.checkpointId(), request.decision());
    }

    @Override
    public PipelineStatusResponse getPipelineStatus(String pipelineRunId) {
        log.info("[STUB] getPipelineStatus: runId={}", pipelineRunId);
        return new PipelineStatusResponse(pipelineRunId, "UNKNOWN");
    }

    @Override
    public void cancelPipeline(String pipelineRunId) {
        log.info("[STUB] cancelPipeline: runId={}", pipelineRunId);
    }
}
