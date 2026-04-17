package io.tacticl.business.pipeline.service;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;
import io.tacticl.data.pipeline.entity.*;
import io.tacticl.data.pipeline.repository.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PdlcV2Service {

    private static final Logger log = LoggerFactory.getLogger(PdlcV2Service.class);
    private static final JsonMapper JSON = new JsonMapper();

    private final PipelineRunRepository pipelineRunRepository;
    private final PipelineEventRepository pipelineEventRepository;
    private final PipelineCheckpointRepository pipelineCheckpointRepository;
    private final SparkRepository sparkRepository;
    private final ArbiterPipelineService arbiterPipelineService;
    private final PipelineEventEmitter pipelineEventEmitter;
    private final String callbackUrl;

    public PdlcV2Service(PipelineRunRepository pipelineRunRepository,
                         PipelineEventRepository pipelineEventRepository,
                         PipelineCheckpointRepository pipelineCheckpointRepository,
                         SparkRepository sparkRepository,
                         ArbiterPipelineService arbiterPipelineService,
                         PipelineEventEmitter pipelineEventEmitter,
                         @Value("${pdlc.v2.callback-url:https://api.tacticl.ai/v1/internal/pipeline/callback}")
                         String callbackUrl) {
        this.pipelineRunRepository = pipelineRunRepository;
        this.pipelineEventRepository = pipelineEventRepository;
        this.pipelineCheckpointRepository = pipelineCheckpointRepository;
        this.sparkRepository = sparkRepository;
        this.arbiterPipelineService = arbiterPipelineService;
        this.pipelineEventEmitter = pipelineEventEmitter;
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

    /**
     * Handle an arbiter callback event: persist the event, update run state based on
     * event type, create checkpoints on demand, and fan out to SSE subscribers.
     */
    public void handleCallbackEvent(PipelineCallbackEvent event) {
        // 1) Always persist the event, regardless of whether the run exists.
        pipelineEventRepository.save(PipelineEvent.create(
            event.pipelineRunId(), event.eventType(), event.role(), event.phase(), event.payloadJson()
        ));

        // 2) Load run; warn and continue if missing (we still fan out SSE for observability).
        Optional<PipelineRun> runOpt = pipelineRunRepository.findById(event.pipelineRunId());
        if (runOpt.isEmpty()) {
            log.warn("Received callback event {} for unknown pipeline run {}",
                     event.eventType(), event.pipelineRunId());
        } else {
            processRunEvent(runOpt.get(), event);
        }

        // 3) Fan out to SSE subscribers.
        pipelineEventEmitter.emit(event.pipelineRunId(), event.eventType(), event.payloadJson());

        // 4) On terminal events, close all SSE emitters for this run.
        String type = event.eventType();
        if ("PIPELINE_COMPLETED".equals(type)
                || "PIPELINE_FAILED".equals(type)
                || "PIPELINE_CANCELLED".equals(type)) {
            pipelineEventEmitter.completeAll(event.pipelineRunId());
        }
    }

    private void processRunEvent(PipelineRun run, PipelineCallbackEvent event) {
        JsonNode payload = parsePayload(event.payloadJson());
        switch (event.eventType()) {
            case "PIPELINE_STARTED" -> {
                run.markRunning();
                pipelineRunRepository.save(run);
            }
            case "ROLE_STARTED" -> {
                run.markRoleStarted(event.phase(), event.role());
                pipelineRunRepository.save(run);
            }
            case "ROLE_COMPLETED" -> {
                double cost = payload.path("costUsd").asDouble(0.0);
                run.markRoleCompleted(event.phase(), event.role(), cost);
                JsonNode artifactPath = payload.path("artifactPath");
                if (!artifactPath.isMissingNode() && !artifactPath.isNull()) {
                    String path = artifactPath.asString("");
                    if (!path.isEmpty()) {
                        run.setArtifact(event.phase() + "_" + event.role(), path);
                    }
                }
                pipelineRunRepository.save(run);
            }
            case "ROLE_REWORK" -> {
                run.markRoleRework(event.phase(), event.role());
                pipelineRunRepository.save(run);
            }
            case "CHECKPOINT_REQUESTED" -> handleCheckpointRequested(run, event, payload);
            case "PIPELINE_COMPLETED" -> {
                run.markCompleted();
                pipelineRunRepository.save(run);
            }
            case "PIPELINE_FAILED" -> {
                String reason = payload.path("reason").asString("");
                run.markFailed(reason);
                pipelineRunRepository.save(run);
            }
            case "PIPELINE_CANCELLED" -> {
                run.markCancelled();
                pipelineRunRepository.save(run);
            }
            default -> log.debug("Unhandled callback event type: {}", event.eventType());
        }
    }

    private void handleCheckpointRequested(PipelineRun run, PipelineCallbackEvent event, JsonNode payload) {
        String type = payload.path("type").asString("PHASE_COMPLETE");
        Map<String, String> artifactPaths = new HashMap<>();
        JsonNode paths = payload.path("artifactPaths");
        if (paths.isObject()) {
            paths.properties().forEach(entry -> artifactPaths.put(entry.getKey(), entry.getValue().asString("")));
        }
        PipelineCheckpoint checkpoint = PipelineCheckpoint.create(
            run.getId(), run.getSparkId(), event.phase(), type, artifactPaths
        );
        pipelineCheckpointRepository.save(checkpoint);
        run.pauseAtCheckpoint(checkpoint.getId(), event.phase());
        pipelineRunRepository.save(run);
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return JSON.nullNode();
        }
        try {
            return JSON.readTree(payloadJson);
        } catch (JacksonException e) {
            log.warn("Failed to parse callback payload JSON: {}", e.getMessage());
            return JSON.nullNode();
        }
    }
}
