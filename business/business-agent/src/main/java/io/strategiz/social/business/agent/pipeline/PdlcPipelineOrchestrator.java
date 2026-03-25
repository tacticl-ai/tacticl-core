package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.business.agent.pipeline.PdlcRoleExecutor.RoleExecutionResult;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Core orchestration engine for the PDLC pipeline. Manages the full pipeline lifecycle:
 * creates child sparks per role, iterates through playbook stages respecting dependency order,
 * handles parallel role groups, emits lifecycle events, and aggregates cost metrics.
 *
 * <p>Role execution is delegated to {@link PdlcRoleExecutor} (stubbed until Wave 4).
 * Checkpoint handling and rework loops are logged but deferred to future tasks.</p>
 */
@Service
public class PdlcPipelineOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(PdlcPipelineOrchestrator.class);

	private final PipelineStateManager pipelineStateManager;

	private final PipelineEventEmitter pipelineEventEmitter;

	private final PipelineArtifactService pipelineArtifactService;

	private final SparkService sparkService;

	private final PlaybookRegistry playbookRegistry;

	private final PdlcRoleExecutor roleExecutor;

	public PdlcPipelineOrchestrator(PipelineStateManager pipelineStateManager,
			PipelineEventEmitter pipelineEventEmitter, PipelineArtifactService pipelineArtifactService,
			SparkService sparkService, PlaybookRegistry playbookRegistry, PdlcRoleExecutor roleExecutor) {
		this.pipelineStateManager = pipelineStateManager;
		this.pipelineEventEmitter = pipelineEventEmitter;
		this.pipelineArtifactService = pipelineArtifactService;
		this.sparkService = sparkService;
		this.playbookRegistry = playbookRegistry;
		this.roleExecutor = roleExecutor;
	}

	/**
	 * Execute the full pipeline lifecycle asynchronously. Iterates through all stages in the
	 * playbook, executes roles (including parallel groups), emits events, stores artifacts,
	 * and aggregates final metrics.
	 *
	 * @param pipelineRunId the ID of the PipelineRun to execute
	 */
	@Async("pdlcPipelineExecutor")
	public void executePipeline(String pipelineRunId) {
		log.info("[PIPELINE] Starting execution for run={}", pipelineRunId);

		// 1. Load PipelineRun from Firestore
		Optional<PipelineRun> runOpt = pipelineStateManager.getRun(pipelineRunId);
		if (runOpt.isEmpty()) {
			log.error("[PIPELINE] Run not found: {}", pipelineRunId);
			return;
		}
		PipelineRun run = runOpt.get();

		// Emit PIPELINE_STARTED and transition to EXECUTING now that execution is actually beginning
		pipelineEventEmitter.emitPipelineStarted(run);
		pipelineStateManager.markExecuting(pipelineRunId);

		// 2. Load PlaybookConfig for this run
		Optional<PlaybookConfig> playbookOpt = playbookRegistry.getPlaybook(run.getPlaybook());
		if (playbookOpt.isEmpty()) {
			log.error("[PIPELINE] Playbook not found: {} for run={}", run.getPlaybook(), pipelineRunId);
			pipelineStateManager.markFailed(pipelineRunId, "Playbook not found: " + run.getPlaybook());
			sparkService.markCloudFailed(run.getSparkId(), "Pipeline playbook not found", 0, null);
			return;
		}
		PlaybookConfig playbook = playbookOpt.get();

		// Mark the parent spark as running
		sparkService.markRunning(run.getSparkId());

		try {
			// 3. Iterate through stages
			Set<PdlcRole> completedRoles = new HashSet<>();
			List<PlaybookStage> stages = playbook.stages();

			int stageIndex = 0;
			while (stageIndex < stages.size()) {
				PlaybookStage stage = stages.get(stageIndex);
				PdlcRole role = stage.role();

				// a. Skip if role not in activatedRoles
				if (shouldSkipRole(role, run)) {
					log.info("[PIPELINE] Skipping role={} (not activated) for run={}", role, pipelineRunId);
					pipelineEventEmitter.emitEvent(run, PipelineEventType.ROLE_SKIPPED, role,
							Map.of("reason", "Not in activated roles"));
					completedRoles.add(role);
					stageIndex++;
					continue;
				}

				// b. Check for human checkpoint before this role
				if (isCheckpointBeforeRequired(role, playbook)) {
					log.info("[PIPELINE] Checkpoint required before role={} for run={} (skipping for now)",
							role, pipelineRunId);
					// Future: pause pipeline, create checkpoint, wait for resolution
				}

				// c. Handle parallel groups
				List<PdlcRole> parallelPeers = getParallelPeers(role, playbook);
				if (!parallelPeers.isEmpty() && !completedRoles.contains(role)) {
					// Build the full parallel group (current role + peers not yet completed)
					List<PdlcRole> parallelGroup = new ArrayList<>();
					parallelGroup.add(role);
					for (PdlcRole peer : parallelPeers) {
						if (!completedRoles.contains(peer) && !shouldSkipRole(peer, run)) {
							parallelGroup.add(peer);
						}
					}

					if (parallelGroup.size() > 1) {
						log.info("[PIPELINE] Executing parallel group {} for run={}", parallelGroup, pipelineRunId);
						pipelineEventEmitter.emitEvent(run, PipelineEventType.PARALLEL_ROLES_STARTED, null,
								Map.of("roles", parallelGroup.stream().map(Enum::name).toList()));

						executeParallelRoles(run, parallelGroup);
						completedRoles.addAll(parallelGroup);

						// Advance past all roles in this parallel group
						while (stageIndex < stages.size() && completedRoles.contains(stages.get(stageIndex).role())) {
							stageIndex++;
						}
						continue;
					}
				}

				// d-h. Execute single role
				executeRole(run, role);
				completedRoles.add(role);

				// i. Check for human checkpoint after this role
				if (isCheckpointAfterRequired(role, playbook)) {
					log.info("[PIPELINE] Checkpoint required after role={} for run={} (skipping for now)",
							role, pipelineRunId);
					// Future: pause pipeline, create checkpoint, wait for resolution
				}

				stageIndex++;
			}

			// 4. Aggregate metrics and mark completed
			// Reload run to get latest roleResults
			run = pipelineStateManager.getRun(pipelineRunId).orElse(run);
			aggregateMetrics(run);

			pipelineStateManager.markCompleted(pipelineRunId, run.getTotalTokens(), run.getTotalCost());
			sparkService.markCloudCompleted(run.getSparkId(), run.getTotalTokens(), "pdlc-pipeline");

			log.info("[PIPELINE] Completed run={} totalTokens={} totalCost={}",
					pipelineRunId, run.getTotalTokens(), run.getTotalCost());

		}
		catch (Exception ex) {
			log.error("[PIPELINE] Execution failed for run={}", pipelineRunId, ex);
			pipelineStateManager.markFailed(pipelineRunId, ex.getMessage());
			sparkService.markCloudFailed(run.getSparkId(), "Pipeline execution failed: " + ex.getMessage(), 0, null);
		}
	}

	/**
	 * Execute a single role: create child spark, emit events, run role executor, store results.
	 */
	void executeRole(PipelineRun run, PdlcRole role) {
		String childSparkId = sparkService.createChildSpark(run.getSparkId(), role, run.getUserId());

		// Emit ROLE_STARTED
		pipelineEventEmitter.emitRoleStarted(run, role, childSparkId);
		pipelineStateManager.updateRoleStatus(run.getId(), role, RoleStatus.EXECUTING);

		// Execute via the role executor (stubbed until Wave 4)
		RoleExecutionResult result = roleExecutor.execute(run, role, childSparkId);

		// Build role result summary directly from execution result
		RoleResultSummary summary = run.getRoleResults()
				.computeIfAbsent(role.name(), k -> new RoleResultSummary());
		summary.setStatus(RoleStatus.COMPLETED);
		summary.setChildSparkId(childSparkId);
		summary.setTokens(result.tokens());
		summary.setCost(result.cost());
		summary.setDurationMs(result.durationMs());
		summary.setModel(result.model());
		if (result.artifactId() != null) {
			summary.setArtifactId(result.artifactId());
		}

		// Emit ROLE_COMPLETED with metrics (also updates the run via event emitter)
		pipelineEventEmitter.emitRoleCompleted(run, role, result.tokens(), result.cost(),
				result.durationMs(), result.model());

		// Mark child spark completed
		sparkService.markCloudCompleted(childSparkId, result.tokens(), result.model());

		// Handle rejection (rework delegation — logged for now, full rework is Task 12)
		if (result.rejected()) {
			log.info("[PIPELINE] Role {} rejected output, target={} reason={} for run={}",
					role, result.rejectionTarget(), result.rejectionReason(), run.getId());
			pipelineEventEmitter.emitReworkTriggered(run, role, result.rejectionTarget(), result.rejectionReason());
			// Future: delegate to ReworkTracker (Task 12)
		}

		log.info("[PIPELINE] Role {} completed for run={} tokens={} cost={}",
				role, run.getId(), result.tokens(), result.cost());
	}

	/**
	 * Execute a group of roles in parallel using CompletableFuture.
	 */
	void executeParallelRoles(PipelineRun run, List<PdlcRole> roles) {
		List<CompletableFuture<Void>> futures = roles.stream()
				.map(role -> CompletableFuture.runAsync(() -> executeRole(run, role)))
				.toList();

		try {
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		}
		catch (CompletionException ex) {
			log.error("[PIPELINE] Parallel execution failed for run={}", run.getId(), ex);
			throw new RuntimeException("Parallel role execution failed", ex.getCause());
		}
	}

	/**
	 * Determine if a role should be skipped (not in the activated roles list).
	 */
	boolean shouldSkipRole(PdlcRole role, PipelineRun run) {
		List<PdlcRole> activatedRoles = run.getActivatedRoles();
		return activatedRoles != null && !activatedRoles.isEmpty() && !activatedRoles.contains(role);
	}

	/**
	 * Check if a checkpoint is required before a given role, based on playbook defaults.
	 * User-level checkpoint config overrides will be handled in a future task.
	 */
	boolean isCheckpointBeforeRequired(PdlcRole role, PlaybookConfig playbook) {
		PlaybookConfig.CheckpointRule rule = playbook.defaultCheckpoints().get(role);
		return rule != null && rule.beforeRole();
	}

	/**
	 * Check if a checkpoint is required after a given role, based on playbook defaults.
	 */
	boolean isCheckpointAfterRequired(PdlcRole role, PlaybookConfig playbook) {
		PlaybookConfig.CheckpointRule rule = playbook.defaultCheckpoints().get(role);
		return rule != null && rule.afterRole();
	}

	/**
	 * Get the list of parallel peers for a role from the playbook configuration.
	 */
	List<PdlcRole> getParallelPeers(PdlcRole role, PlaybookConfig playbook) {
		List<PdlcRole> peers = playbook.parallelGroups().get(role);
		return peers != null ? peers : List.of();
	}

	/**
	 * Aggregate total tokens and cost from all roleResults in the pipeline run.
	 */
	void aggregateMetrics(PipelineRun run) {
		long totalTokens = 0;
		BigDecimal totalCost = BigDecimal.ZERO;

		for (RoleResultSummary summary : run.getRoleResults().values()) {
			totalTokens += summary.getTokens();
			if (summary.getCost() != null) {
				totalCost = totalCost.add(summary.getCost());
			}
		}

		run.setTotalTokens(totalTokens);
		run.setTotalCost(totalCost);
	}

}
