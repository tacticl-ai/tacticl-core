package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.business.agent.pipeline.PdlcRoleExecutor.RoleExecutionResult;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.Checkpoint;
import io.strategiz.social.data.entity.CheckpointDecision;
import io.strategiz.social.data.entity.CheckpointType;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import io.strategiz.social.data.entity.UserConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Core orchestration engine for the PDLC pipeline. Manages the full pipeline lifecycle:
 * creates child sparks per role, iterates through playbook stages respecting dependency order,
 * handles parallel role groups, emits lifecycle events, and aggregates cost metrics.
 *
 * <p>Role execution is delegated to {@link PdlcRoleExecutor}, with the production implementation
 * in {@link RealPdlcRoleExecutor} that routes through {@link io.strategiz.social.business.agent.pipeline.role.PdlcRoleSkill}.
 * Checkpoint gates pause the pipeline and poll for user resolution via {@link CheckpointService}.
 * Rework loops are handled via {@link ReworkTracker}.</p>
 */
@Service
public class PdlcPipelineOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(PdlcPipelineOrchestrator.class);

	/** How often to poll Firestore waiting for a checkpoint to be resolved. */
	static final Duration CHECKPOINT_POLL_INTERVAL = Duration.ofSeconds(5);

	/** Default maximum time to wait for a user to respond to a checkpoint (24 hours). */
	static final Duration DEFAULT_CHECKPOINT_TIMEOUT = Duration.ofHours(24);

	private final PipelineStateManager pipelineStateManager;

	private final PipelineEventEmitter pipelineEventEmitter;

	private final PipelineArtifactService pipelineArtifactService;

	private final SparkService sparkService;

	private final PlaybookRegistry playbookRegistry;

	private final PdlcRoleExecutor roleExecutor;

	private final CheckpointService checkpointService;

	private final ReworkTracker reworkTracker;

	private final Executor pdlcPipelineExecutor;

	public PdlcPipelineOrchestrator(PipelineStateManager pipelineStateManager,
			PipelineEventEmitter pipelineEventEmitter, PipelineArtifactService pipelineArtifactService,
			SparkService sparkService, PlaybookRegistry playbookRegistry, PdlcRoleExecutor roleExecutor,
			CheckpointService checkpointService, ReworkTracker reworkTracker,
			@Qualifier("pdlcPipelineExecutor") Executor pdlcPipelineExecutor) {
		this.pipelineStateManager = pipelineStateManager;
		this.pipelineEventEmitter = pipelineEventEmitter;
		this.pipelineArtifactService = pipelineArtifactService;
		this.sparkService = sparkService;
		this.playbookRegistry = playbookRegistry;
		this.roleExecutor = roleExecutor;
		this.checkpointService = checkpointService;
		this.reworkTracker = reworkTracker;
		this.pdlcPipelineExecutor = pdlcPipelineExecutor;
	}

	/**
	 * Execute the full pipeline lifecycle asynchronously. Iterates through all stages in the
	 * playbook, executes roles (including parallel groups), emits events, stores artifacts,
	 * and aggregates final metrics.
	 *
	 * <p>Checkpoint gates pause the executing thread (polling every
	 * {@link #CHECKPOINT_POLL_INTERVAL}) until the user resolves the checkpoint via the REST
	 * API, or until {@link #DEFAULT_CHECKPOINT_TIMEOUT} elapses and the pipeline is cancelled.</p>
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

		// Resolve the user config for checkpoint overrides (null-safe — CheckpointService handles null gracefully)
		UserConfig userConfig = resolveUserConfig(run);

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

				// b. Checkpoint BEFORE this role (playbook default OR user override)
				if (isCheckpointBeforeRequired(role, playbook)
						|| checkpointService.shouldCheckpoint(run, role, true, userConfig)) {
					log.info("[PIPELINE] Checkpoint required before role={} for run={}", role, pipelineRunId);
					CheckpointDecision decision = pauseForCheckpoint(run, role,
							CheckpointType.PIPELINE_STAGE,
							"Approve before: " + role.name(),
							"Review required before the " + role.name() + " stage begins.",
							List.of("APPROVED", "REJECTED"),
							userConfig);

					if (decision == CheckpointDecision.REJECTED) {
						log.info("[PIPELINE] User rejected before-role checkpoint for role={} run={} — cancelling",
								role, pipelineRunId);
						pipelineStateManager.markFailed(pipelineRunId,
								"Cancelled by user at checkpoint before " + role);
						sparkService.markCloudFailed(run.getSparkId(), "Pipeline cancelled at checkpoint", 0, null);
						return;
					}
					// APPROVED or MODIFIED: reload run after state transition back to EXECUTING
					run = pipelineStateManager.getRun(pipelineRunId).orElse(run);
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

				// i. Checkpoint AFTER this role (playbook default OR user override)
				if (isCheckpointAfterRequired(role, playbook)
						|| checkpointService.shouldCheckpoint(run, role, false, userConfig)) {
					log.info("[PIPELINE] Checkpoint required after role={} for run={}", role, pipelineRunId);
					CheckpointDecision decision = pauseForCheckpoint(run, role,
							CheckpointType.PIPELINE_STAGE,
							"Approve after: " + role.name(),
							"Review the output produced by the " + role.name() + " stage before continuing.",
							List.of("APPROVED", "REJECTED", "MODIFIED"),
							userConfig);

					if (decision == CheckpointDecision.REJECTED) {
						log.info("[PIPELINE] User rejected after-role checkpoint for role={} run={} — cancelling",
								role, pipelineRunId);
						pipelineStateManager.markFailed(pipelineRunId,
								"Cancelled by user at checkpoint after " + role);
						sparkService.markCloudFailed(run.getSparkId(), "Pipeline cancelled at checkpoint", 0, null);
						return;
					}
					// APPROVED or MODIFIED: reload run, continue to next stage
					run = pipelineStateManager.getRun(pipelineRunId).orElse(run);
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
		catch (CheckpointTimeoutException ex) {
			log.warn("[PIPELINE] Checkpoint timed out for run={}: {}", pipelineRunId, ex.getMessage());
			pipelineStateManager.markFailed(pipelineRunId, "Checkpoint timed out: " + ex.getMessage());
			sparkService.markCloudFailed(run.getSparkId(), "Pipeline timed out waiting for checkpoint", 0, null);
		}
		catch (Exception ex) {
			log.error("[PIPELINE] Execution failed for run={}", pipelineRunId, ex);
			pipelineStateManager.markFailed(pipelineRunId, ex.getMessage());
			sparkService.markCloudFailed(run.getSparkId(), "Pipeline execution failed: " + ex.getMessage(), 0, null);
		}
	}

	/**
	 * Pause the executing thread until the user resolves the checkpoint, then return the decision.
	 * Polls {@link CheckpointService#getPendingCheckpoint} every {@link #CHECKPOINT_POLL_INTERVAL}.
	 * Throws {@link CheckpointTimeoutException} if the timeout elapses without a resolution.
	 *
	 * @param run         the pipeline run being paused
	 * @param role        the PDLC role associated with this gate
	 * @param type        the checkpoint gate type
	 * @param title       short label for the checkpoint prompt
	 * @param description detailed review instructions
	 * @param options     available decisions to present to the user
	 * @param userConfig  user config (currently unused; reserved for future per-user timeout)
	 * @return the user's resolved {@link CheckpointDecision}
	 */
	CheckpointDecision pauseForCheckpoint(PipelineRun run, PdlcRole role, CheckpointType type,
			String title, String description, List<String> options, UserConfig userConfig) {

		Checkpoint checkpoint = checkpointService.createPipelineCheckpoint(
				run, role, type, title, description, options);

		long deadlineEpochMs = System.currentTimeMillis() + DEFAULT_CHECKPOINT_TIMEOUT.toMillis();

		log.info("[CHECKPOINT] Waiting for resolution of checkpoint={} run={} timeout={}",
				checkpoint.getId(), run.getId(), DEFAULT_CHECKPOINT_TIMEOUT);

		while (System.currentTimeMillis() < deadlineEpochMs) {
			try {
				Thread.sleep(CHECKPOINT_POLL_INTERVAL.toMillis());
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new CheckpointTimeoutException(
						"Interrupted while waiting for checkpoint " + checkpoint.getId());
			}

			Optional<Checkpoint> pending = checkpointService.getPendingCheckpoint(run.getId());
			if (pending.isEmpty()) {
				// No pending checkpoint means it was resolved — fetch the decision
				Optional<Checkpoint> resolved = checkpointService.getCheckpoint(checkpoint.getId());
				CheckpointDecision decision = resolved
						.map(Checkpoint::getUserDecision)
						.orElse(CheckpointDecision.APPROVED);
				log.info("[CHECKPOINT] Resolved: checkpoint={} decision={} run={}",
						checkpoint.getId(), decision, run.getId());
				return decision;
			}
		}

		throw new CheckpointTimeoutException(
				"Checkpoint " + checkpoint.getId() + " timed out after " + DEFAULT_CHECKPOINT_TIMEOUT);
	}

	/**
	 * Resolve the user config for the given run. Returns defaults until UserConfigService
	 * integration is wired (future task).
	 */
	private UserConfig resolveUserConfig(PipelineRun run) {
		return UserConfig.defaults();
	}

	/**
	 * Execute a single role: create child spark, emit events, run role executor, store results.
	 * Delegates to the overloaded method with no rework context.
	 */
	void executeRole(PipelineRun run, PdlcRole role) {
		executeRole(run, role, null, 0);
	}

	/**
	 * Execute a single role with optional rework context. Creates a child spark, emits lifecycle
	 * events, delegates to the role executor (which routes through real PdlcRoleSkill implementations),
	 * stores result summaries, and handles rejection by delegating to the ReworkTracker.
	 *
	 * @param run              the pipeline run context
	 * @param role             the role to execute
	 * @param reworkFeedback   feedback from a rejecting role (null on first execution)
	 * @param reworkIteration  rework iteration count (0 on first execution)
	 */
	void executeRole(PipelineRun run, PdlcRole role, String reworkFeedback, int reworkIteration) {
		String childSparkId = sparkService.createChildSpark(run.getSparkId(), role, run.getUserId());

		// Emit ROLE_STARTED
		pipelineEventEmitter.emitRoleStarted(run, role, childSparkId);
		pipelineStateManager.updateRoleStatus(run.getId(), role, RoleStatus.EXECUTING);

		// Execute via the role executor — if it supports rework context, pass it through
		RoleExecutionResult result;
		if (roleExecutor instanceof RealPdlcRoleExecutor realExecutor) {
			result = realExecutor.execute(run, role, childSparkId, reworkFeedback, reworkIteration);
		}
		else {
			result = roleExecutor.execute(run, role, childSparkId);
		}

		// Build role result summary directly from execution result
		RoleResultSummary summary = run.getRoleResults()
				.computeIfAbsent(role.name(), k -> new RoleResultSummary());
		summary.setStatus(result.rejected() ? RoleStatus.REJECTED : RoleStatus.COMPLETED);
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

		// Handle rejection via ReworkTracker
		if (result.rejected()) {
			log.info("[PIPELINE] Role {} rejected output, target={} reason={} for run={}",
					role, result.rejectionTarget(), result.rejectionReason(), run.getId());

			boolean shouldRework = reworkTracker.handleRework(
					run, role, result.rejectionTarget(), result.rejectionReason());

			if (shouldRework && result.rejectionTarget() != null) {
				log.info("[PIPELINE] Re-executing target role={} after rejection for run={}",
						result.rejectionTarget(), run.getId());
				executeRole(run, result.rejectionTarget(), result.rejectionReason(), run.getReworkCount());
			}
			else if (!shouldRework) {
				log.warn("[PIPELINE] Max rework exceeded for run={}, proceeding without rework", run.getId());
			}
		}

		log.info("[PIPELINE] Role {} completed for run={} tokens={} cost={}",
				role, run.getId(), result.tokens(), result.cost());
	}

	/**
	 * Execute a group of roles in parallel using CompletableFuture.
	 */
	void executeParallelRoles(PipelineRun run, List<PdlcRole> roles) {
		List<CompletableFuture<Void>> futures = roles.stream()
				.map(role -> CompletableFuture.runAsync(() -> executeRole(run, role), pdlcPipelineExecutor))
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
