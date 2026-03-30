package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages PipelineRun persistence and state transitions. All run-level mutations flow through
 * this service to ensure the run document is saved exactly once per transition, and events are
 * emitted after the save so the WebSocket payload reflects committed state.
 *
 * <p><strong>Ownership contract:</strong> This class is the sole owner of all
 * {@link PipelineRun} mutations and {@code pipelineRunRepository.save()} calls.
 * {@link PipelineEventEmitter} must never mutate the run or persist it.
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
	 * Create a new PipelineRun and persist it with status {@code CREATED}.
	 *
	 * <p>The caller (orchestrator) is responsible for calling {@link #markExecuting(String)}
	 * once execution actually begins, which will emit the {@code PIPELINE_STARTED} event.
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
	 * Mark a pipeline run as EXECUTING (pipeline has begun processing stages) and emit the
	 * {@code PIPELINE_STARTED} event.
	 *
	 * <p>The run is saved with status {@code EXECUTING} before the event is emitted, ensuring
	 * the WebSocket payload reflects committed state.
	 *
	 * @param runId the pipeline run ID
	 */
	public void markExecuting(String runId) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setStatus(PipelineStatus.EXECUTING);
			pipelineRunRepository.save(run);
			pipelineEventEmitter.emitPipelineStarted(run);
			log.debug("[PIPELINE] Marked EXECUTING and emitted PIPELINE_STARTED: run={}", runId);
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
	 * Mark a pipeline run as COMPLETED with aggregated cost metrics and emit the
	 * {@code PIPELINE_COMPLETED} event.
	 *
	 * <p>Sets status to {@code COMPLETED}, records {@code completedAt}, clears
	 * {@code currentRole}, and saves exactly once before emitting the event.
	 *
	 * @param runId       the pipeline run ID
	 * @param totalTokens aggregated token count across all roles
	 * @param totalCost   aggregated cost across all roles
	 */
	public void markCompleted(String runId, long totalTokens, BigDecimal totalCost) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setStatus(PipelineStatus.COMPLETED);
			run.setCompletedAt(Instant.now());
			run.setCurrentRole(null);
			run.setTotalTokens(totalTokens);
			run.setTotalCost(totalCost);
			pipelineRunRepository.save(run);
			pipelineEventEmitter.emitEvent(run, PipelineEventType.PIPELINE_COMPLETED,
					null, Map.of("totalTokens", totalTokens, "totalCost", totalCost));
			log.info("[PIPELINE] Completed run={} tokens={} cost={}", runId, totalTokens, totalCost);
		});
	}

	/**
	 * Mark a pipeline run as FAILED with an error description and emit the
	 * {@code PIPELINE_FAILED} event.
	 *
	 * <p>Sets status to {@code FAILED} and saves exactly once before emitting the event.
	 *
	 * @param runId the pipeline run ID
	 * @param error the error message
	 */
	public void markFailed(String runId, String error) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setStatus(PipelineStatus.FAILED);
			pipelineRunRepository.save(run);
			pipelineEventEmitter.emitEvent(run, PipelineEventType.PIPELINE_FAILED,
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
	 * Persist the list of required roles that the user has elected to skip for a pipeline run.
	 * Called by the controller after {@link #createRun} when required-role skips are detected.
	 * The orchestrator checks this field at pipeline start to create a soft-guardrail checkpoint.
	 *
	 * @param runId               the pipeline run ID
	 * @param skippedRequiredRoles role names (e.g., "REVIEWER", "TESTER") that were required but skipped
	 */
	public void updateSkippedRequiredRoles(String runId, List<String> skippedRequiredRoles) {
		pipelineRunRepository.findById(runId).ifPresent(run -> {
			run.setSkippedRequiredRoles(skippedRequiredRoles);
			pipelineRunRepository.save(run);
			log.warn("[PIPELINE] Set skippedRequiredRoles={} for run={}", skippedRequiredRoles, runId);
		});
	}

	/**
	 * Update the set of skipped roles on an executing pipeline, filtering to only roles
	 * that have not yet started (still {@code PENDING} or absent from roleResults).
	 *
	 * <p>Validates that the pipeline is in {@code EXECUTING} status. For each newly skipped
	 * role, marks the role as {@code SKIPPED} in roleResults and emits a {@code ROLE_SKIPPED}
	 * event via {@link PipelineEventEmitter}.
	 *
	 * @param sparkId   the spark ID that owns this pipeline
	 * @param skipRoles the roles the user wants to skip
	 * @param userId    the authenticated user requesting the skip
	 * @return the updated PipelineRun
	 * @throws IllegalStateException    if the pipeline is not in EXECUTING status
	 * @throws IllegalArgumentException if no pipeline run exists for the given sparkId
	 * @throws SecurityException        if the pipeline does not belong to the given userId
	 */
	public PipelineRun updateSkipRoles(String sparkId, List<PdlcRole> skipRoles, String userId) {
		Optional<PipelineRun> runOpt = pipelineRunRepository.findBySparkId(sparkId);
		if (runOpt.isEmpty()) {
			throw new IllegalArgumentException("No pipeline run found for spark: " + sparkId);
		}

		PipelineRun run = runOpt.get();

		if (!run.getUserId().equals(userId)) {
			throw new SecurityException("Pipeline does not belong to user: " + userId);
		}

		if (run.getStatus() != PipelineStatus.EXECUTING) {
			throw new IllegalStateException("Pipeline must be EXECUTING to skip roles; current status: " + run.getStatus());
		}

		// Filter to only roles that are still PENDING (or not yet in roleResults)
		List<String> newSkips = skipRoles.stream()
				.map(PdlcRole::name)
				.filter(roleName -> {
					RoleResultSummary result = run.getRoleResults().get(roleName);
					return result == null || result.getStatus() == RoleStatus.PENDING;
				})
				.toList();

		// Merge with existing skipped roles
		List<String> existingSkips = run.getSkippedRequiredRoles() != null
				? run.getSkippedRequiredRoles()
				: List.of();
		List<String> merged = new ArrayList<>(existingSkips);
		for (String skip : newSkips) {
			if (!merged.contains(skip)) {
				merged.add(skip);
			}
		}
		run.setSkippedRequiredRoles(merged);

		// Mark each newly skipped role as SKIPPED in roleResults and emit events
		for (String roleName : newSkips) {
			RoleResultSummary summary = run.getRoleResults()
					.computeIfAbsent(roleName, k -> new RoleResultSummary());
			summary.setStatus(RoleStatus.SKIPPED);

			PdlcRole role = PdlcRole.valueOf(roleName);
			pipelineEventEmitter.emitEvent(run, PipelineEventType.ROLE_SKIPPED, role,
					Map.of("reason", "User requested skip"));
		}

		pipelineRunRepository.save(run);
		log.info("[PIPELINE] Updated skip-roles for run={} spark={} newSkips={}", run.getId(), sparkId, newSkips);

		return run;
	}

	/**
	 * Find a pipeline run by spark ID.
	 *
	 * @param sparkId the spark ID
	 * @return the pipeline run, or empty if not found
	 */
	public Optional<PipelineRun> findBySparkId(String sparkId) {
		return pipelineRunRepository.findBySparkId(sparkId);
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
