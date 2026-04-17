package io.tacticl.business.pipeline.service;

import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;
import io.tacticl.data.pipeline.entity.*;
import io.tacticl.data.pipeline.repository.*;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class PdlcV2Service {

    private static final Logger log = LoggerFactory.getLogger(PdlcV2Service.class);

    private final PipelineRunRepository pipelineRunRepository;
    private final PipelineEventRepository pipelineEventRepository;
    private final PipelineCheckpointRepository pipelineCheckpointRepository;
    private final SparkRepository sparkRepository;
    private final ArbiterPipelineService arbiterPipelineService;
    private final String callbackUrl;

    public PdlcV2Service(PipelineRunRepository pipelineRunRepository,
                         PipelineEventRepository pipelineEventRepository,
                         PipelineCheckpointRepository pipelineCheckpointRepository,
                         SparkRepository sparkRepository,
                         ArbiterPipelineService arbiterPipelineService,
                         @Value("${pdlc.v2.callback-url:https://api.tacticl.ai/v1/internal/pipeline/callback}")
                         String callbackUrl) {
        this.pipelineRunRepository = pipelineRunRepository;
        this.pipelineEventRepository = pipelineEventRepository;
        this.pipelineCheckpointRepository = pipelineCheckpointRepository;
        this.sparkRepository = sparkRepository;
        this.arbiterPipelineService = arbiterPipelineService;
        this.callbackUrl = callbackUrl;
    }

    public PipelineRun submitPipeline(String userId, String sparkId, String sparkRequest,
                                      String repoUrl, String playbook, List<String> skipRoles,
                                      String githubToken, double costCeilingUsd) {
        PipelineRun run = PipelineRun.create(userId, sparkId, sparkRequest, repoUrl,
                                             playbook, skipRoles, costCeilingUsd);
        pipelineRunRepository.save(run);

        SubmitPipelineRequest request = new SubmitPipelineRequest(
            run.getId(), sparkId, userId, playbook, sparkRequest,
            repoUrl, githubToken, skipRoles, costCeilingUsd, callbackUrl
        );
        SubmitPipelineResponse response = arbiterPipelineService.submitPipeline(request);
        log.info("Submitted pipeline run {} for spark {} (playbook={}) — arbiterPipelineId={}",
                 run.getId(), sparkId, playbook, response.arbiterPipelineId());

        if (response.arbiterPipelineId() != null) {
            run.setArbiterPipelineId(response.arbiterPipelineId());
            pipelineRunRepository.save(run);
        }

        // Back-reference on MongoDB Spark when it exists (sparks may be Firestore-backed via legacy path)
        sparkRepository.findByIdAndUserId(sparkId, userId).ifPresent(spark -> {
            spark.setPipelineRunId(run.getId());
            sparkRepository.save(spark);
        });

        return run;
    }

    public Optional<PipelineRun> getStatus(String userId, String sparkId) {
        return pipelineRunRepository.findBySparkIdAndUserId(sparkId, userId);
    }

    public Optional<PipelineRun> getStatusByRunId(String userId, String pipelineRunId) {
        return pipelineRunRepository.findByIdAndUserId(pipelineRunId, userId);
    }

    public Page<PipelineEvent> getEvents(String pipelineRunId, int page, int size) {
        return pipelineEventRepository.findByPipelineRunIdOrderByTimestampAsc(
            pipelineRunId, PageRequest.of(page, size));
    }

    /**
     * Checkpoint resolution is local-only — we update our state so the SSE stream reflects it.
     * The arbiter handles its own internal checkpoint flow; there is no resolveCheckpoint RPC.
     */
    public void resolveCheckpoint(String userId, String sparkId, String checkpointId,
                                  CheckpointDecision decision, String feedback) {
        PipelineRun run = pipelineRunRepository.findBySparkIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Pipeline run not found for spark: " + sparkId));

        PipelineCheckpoint checkpoint = pipelineCheckpointRepository
                .findByIdAndPipelineRunId(checkpointId, run.getId())
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));

        checkpoint.resolve(decision, feedback);
        pipelineCheckpointRepository.save(checkpoint);
        log.info("Resolved checkpoint {} for run {} with decision {}", checkpointId, run.getId(), decision);
    }

    public void cancelPipeline(String userId, String sparkId) {
        PipelineRun run = pipelineRunRepository.findBySparkIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Pipeline run not found for spark: " + sparkId));
        run.markCancelled();
        pipelineRunRepository.save(run);

        if (run.getArbiterPipelineId() != null) {
            arbiterPipelineService.cancelPipeline(run.getArbiterPipelineId());
        }
        log.info("Cancelled pipeline run {} for spark {} (arbiterPipelineId={})",
                 run.getId(), sparkId, run.getArbiterPipelineId());
    }
}
