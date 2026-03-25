package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages PipelineRun persistence and state transitions. All run-level mutations flow through
 * this service to ensure events are emitted consistently and the run document stays in sync.
 */
@Service
public class PipelineStateManager {

	private static final Logger log = LoggerFactory.getLogger(PipelineStateManager.class);

	private final PipelineRunRepository pipelineRunRepository;

	private final PipelineEventEmitter pipelineEventEmitter;

	public PipelineStateManager(PipelineRunRepository pipelineRunRepository,
			PipelineEventEmitter pipelineEventEmitter) {
		this.pipelineRunRepository = pipelineRunRepository;
		this.pipelineEventEmitter = pipelineEventEmitter;
	}

	/**
	 * Create a new PipelineRun, persist it, and emit a PIPELINE_STARTED event.
	 *
	 * @param sparkId        the parent spark that triggered this pipeline
	 * @param userId         the user who owns this pipeline run
	 * @param playbook       the playbook name being executed
	 * @param classification the PDLC classification result
	 * @return the created and persisted PipelineRun
	 */
	public PipelineRun createRun(String sparkId, String userId, PlaybookConfig playbook,
			PdlcClassification classification) {
		PipelineRun run = new PipelineRun();
		run.setId(UUID.randomUUID().toString());
		run.setSparkId(sparkId);
		run.setUserId(userId);
		run.setPlaybook(playbook.name());
		run.setPipelineTier(playbook.tier());
		run.setActivatedRoles(classification.activatedRoles());
		run.setStatus(PipelineStatus.CREATED);
		run.setStartedAt(Instant.now());

		// Store classification result as metadata for traceability
		Map<String, Object> classificationData = new HashMap<>();
		classificationData.put("tier", classification.tier().name());
		classificationData.put("playbook", classification.playbook());
		classificationData.put("confidence", classification.confidence());
		classificationData.put("reasoning", classification.reasoning());
		if (classification.dimensionScores() != null) {
			classificationData.put("dimensionScores", classification.dimensionScores());
		}
		run.setClassificationResult(classificationData);

		pipelineRunRepository.save(run);

		// Emit the PIPELINE_STARTED event (also sets status to EXECUTING)
		pipelineEventEmitter.emitPipelineStarted(run);

		log.info("[PIPELINE] Created run={} spark={} playbook={} tier={} activatedRoles={}",
				run.getId(), sparkId, playbook.name(), playbook.tier(), classification.activatedRoles());

		return run;
	}

	/**
	 * Update the status of a specific role in the pipeline run's roleResults map.
	 *
	 * @param runId  the pipeline run ID
	 * @param role   the role whose status to update
	 * @param status the new status for the role
	 */
	public void updateRoleStatus(String runId, PdlcRole role, RoleStatus status) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			RoleResultSummary summary = run.getRoleResults()
					.computeIfAbsent(role.name(), k -> new RoleResultSummary());
			summary.setStatus(status);
			pipelineRunRepository.save(run);
			log.debug("[PIPELINE] Updated role status: run={} role={} status={}", runId, role, status);
		});
	}

	/**
	 * Update the current active role on the pipeline run.
	 *
	 * @param runId the pipeline run ID
	 * @param role  the role now executing
	 */
	public void updateCurrentRole(String runId, PdlcRole role) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setCurrentRole(role);
			pipelineRunRepository.save(run);
			log.debug("[PIPELINE] Updated current role: run={} role={}", runId, role);
		});
	}

	/**
	 * Mark a pipeline run as EXECUTING (pipeline has begun processing stages).
	 *
	 * @param runId the pipeline run ID
	 */
	public void markExecuting(String runId) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setStatus(PipelineStatus.EXECUTING);
			pipelineRunRepository.save(run);
			log.debug("[PIPELINE] Marked EXECUTING: run={}", runId);
		});
	}

	/**
	 * Mark a pipeline run as CHECKPOINT (waiting for user approval before continuing).
	 *
	 * @param runId the pipeline run ID
	 */
	public void markCheckpoint(String runId) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setStatus(PipelineStatus.CHECKPOINT);
			pipelineRunRepository.save(run);
			log.debug("[PIPELINE] Marked CHECKPOINT: run={}", runId);
		});
	}

	/**
	 * Mark a pipeline run as COMPLETED with aggregated cost metrics.
	 *
	 * @param runId       the pipeline run ID
	 * @param totalTokens aggregated token count across all roles
	 * @param totalCost   aggregated cost across all roles
	 */
	public void markCompleted(String runId, long totalTokens, BigDecimal totalCost) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setTotalTokens(totalTokens);
			run.setTotalCost(totalCost);
			run.setCurrentRole(null);
			pipelineRunRepository.save(run);
			pipelineEventEmitter.emitEvent(run, io.strategiz.social.data.entity.PipelineEventType.PIPELINE_COMPLETED,
					null, Map.of("totalTokens", totalTokens, "totalCost", totalCost));
			log.info("[PIPELINE] Completed run={} tokens={} cost={}", runId, totalTokens, totalCost);
		});
	}

	/**
	 * Mark a pipeline run as FAILED with an error description.
	 *
	 * @param runId the pipeline run ID
	 * @param error the error message
	 */
	public void markFailed(String runId, String error) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			pipelineEventEmitter.emitEvent(run, io.strategiz.social.data.entity.PipelineEventType.PIPELINE_FAILED,
					null, Map.of("error", error != null ? error : "Unknown error"));
			log.info("[PIPELINE] Failed run={} error={}", runId, error);
		});
	}

	/**
	 * Increment the rework count on a pipeline run.
	 *
	 * @param runId the pipeline run ID
	 */
	public void incrementRework(String runId) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setReworkCount(run.getReworkCount() + 1);
			pipelineRunRepository.save(run);
			log.debug("[PIPELINE] Incremented rework count: run={} count={}", runId, run.getReworkCount());
		});
	}

	/**
	 * Retrieve a pipeline run by ID.
	 *
	 * @param runId the pipeline run ID
	 * @return the pipeline run, or empty if not found
	 */
	public Optional<PipelineRun> getRun(String runId) {
		return pipelineRunRepository.findById(runId);
	}

}
