package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.PipelineResultResponse;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op stub used when pdlc.v2.arbiter.host is not configured.
 * Logs all calls and returns safe defaults. Registered by ArbiterClientConfig.
 */
public class ArbiterPipelineServiceStub implements ArbiterPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ArbiterPipelineServiceStub.class);

    @Override
    public SubmitPipelineResponse submitPipeline(SubmitPipelineRequest request) {
        log.info("[STUB] submitPipeline: runId={} sparkId={} playbook={}",
                 request.pipelineRunId(), request.sparkId(), request.playbook());
        return new SubmitPipelineResponse(request.pipelineRunId(), null, "PENDING");
    }

    @Override
    public void cancelPipeline(String arbiterPipelineId) {
        log.info("[STUB] cancelPipeline: arbiterPipelineId={}", arbiterPipelineId);
    }

    @Override
    public PipelineResultResponse getResult(String arbiterPipelineId) {
        log.info("[STUB] getResult: arbiterPipelineId={}", arbiterPipelineId);
        return new PipelineResultResponse(arbiterPipelineId, "UNKNOWN", null, null, 0L, 0, 0.0);
    }

    @Override
    public void resolveCheckpoint(String arbiterPipelineId, String checkpointId,
                                  String decision, String feedback) {
        log.info("[STUB] resolveCheckpoint: arbiterPipelineId={} checkpointId={} decision={}",
                 arbiterPipelineId, checkpointId, decision);
    }
}
