package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.Checkpoint;
import io.strategiz.social.data.entity.CheckpointDecision;
import io.strategiz.social.data.entity.CheckpointType;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.repository.CheckpointRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages pipeline checkpoint lifecycle: creation, resolution, and evaluation of whether
 * a checkpoint is needed for a given role. Integrates with {@link PipelineStateManager}
 * to pause/resume the pipeline and {@link PipelineEventEmitter} to notify consumers.
 */
@Service
public class CheckpointService {

	private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);

	private final CheckpointRepository checkpointRepository;

	private final PipelineEventEmitter pipelineEventEmitter;

	private final PipelineStateManager pipelineStateManager;

	public CheckpointService(CheckpointRepository checkpointRepository,
			PipelineEventEmitter pipelineEventEmitter,
			PipelineStateManager pipelineStateManager) {
		this.checkpointRepository = checkpointRepository;
		this.pipelineEventEmitter = pipelineEventEmitter;
		this.pipelineStateManager = pipelineStateManager;
	}

	/**
	 * Create a checkpoint for a pipeline run, pause the pipeline, and emit an event to notify
	 * the user that their input is required.
	 *
	 * @param run         the pipeline run being paused
	 * @param role        the PDLC role this checkpoint is associated with
	 * @param type        the type of checkpoint gate being applied
	 * @param title       short human-readable title for the checkpoint prompt
	 * @param description longer description of what requires review
	 * @param options     the available decision choices to present to the user
	 * @return the persisted Checkpoint entity
	 */
	public Checkpoint createPipelineCheckpoint(PipelineRun run, PdlcRole role, CheckpointType type,
			String title, String description, List<String> options) {

		Checkpoint checkpoint = new Checkpoint();
		checkpoint.setId(UUID.randomUUID().toString());
		checkpoint.setSparkId(run.getSparkId());
		checkpoint.setPipelineRunId(run.getId());
		checkpoint.setPdlcRole(role);
		checkpoint.setCheckpointType(type);
		checkpoint.setTitle(title);
		checkpoint.setDescription(description);
		checkpoint.setOptions(options);

		checkpointRepository.save(checkpoint);

		pipelineStateManager.markCheckpoint(run.getId());

		pipelineEventEmitter.emitEvent(run, PipelineEventType.CHECKPOINT_REQUESTED, role,
				Map.of(
						"checkpointId", checkpoint.getId(),
						"checkpointType", type.name(),
						"title", title));

		log.info("[CHECKPOINT] Created checkpoint={} type={} role={} run={}",
				checkpoint.getId(), type, role, run.getId());

		return checkpoint;
	}

	/**
	 * Resolve a pending checkpoint with the user's decision. Saves the decision, emits a
	 * CHECKPOINT_RESOLVED event, and transitions the pipeline back to EXECUTING so the
	 * orchestrator can continue.
	 *
	 * @param checkpointId the ID of the checkpoint to resolve
	 * @param userId       the user resolving the checkpoint (used for ownership verification)
	 * @param decision     the raw decision string (APPROVED / REJECTED / MODIFIED)
	 * @param feedback     optional free-text feedback from the user
	 * @throws IllegalArgumentException if the checkpoint is not found or already resolved
	 * @throws SecurityException        if the checkpoint does not belong to this user's spark
	 */
	public void resolveCheckpoint(String checkpointId, String userId, String decision,
			String feedback) {

		Optional<Checkpoint> checkpointOpt = checkpointRepository.findById(checkpointId);
		if (checkpointOpt.isEmpty()) {
			throw new IllegalArgumentException("Checkpoint not found: " + checkpointId);
		}

		Checkpoint checkpoint = checkpointOpt.get();

		if (checkpoint.getUserDecision() != null) {
			throw new IllegalStateException(
					"Checkpoint already resolved: " + checkpointId + " decision=" + checkpoint.getUserDecision());
		}

		CheckpointDecision parsedDecision = CheckpointDecision.valueOf(decision);
		checkpoint.setUserDecision(parsedDecision);
		checkpoint.setUserFeedback(feedback);
		checkpoint.setDecidedAt(Instant.now());

		checkpointRepository.save(checkpoint);

		// Reload the run to emit the event properly
		pipelineStateManager.getRun(checkpoint.getPipelineRunId()).ifPresent(run -> {
			pipelineStateManager.markExecuting(run.getId());

			pipelineEventEmitter.emitEvent(run, PipelineEventType.CHECKPOINT_RESOLVED,
					checkpoint.getPdlcRole(),
					Map.of(
							"checkpointId", checkpointId,
							"decision", parsedDecision.name(),
							"feedback", feedback != null ? feedback : ""));
		});

		log.info("[CHECKPOINT] Resolved checkpoint={} decision={} run={}",
				checkpointId, parsedDecision, checkpoint.getPipelineRunId());
	}

	/**
	 * Determine whether a checkpoint should be created for the given role and pipeline run,
	 * respecting both playbook defaults and user-level overrides from {@link UserConfig}.
	 *
	 * <p>User override logic:
	 * <ul>
	 *   <li>If {@code autoApproveAll} is set, no checkpoints are created.</li>
	 *   <li>If a per-role override exists in {@code roleCheckpoints}, it takes precedence over
	 *       the playbook default.</li>
	 *   <li>Well-known convenience flags ({@code approveBeforeDeploy},
	 *       {@code approveRequirements}, {@code approveArchitecture}) are applied when no
	 *       per-role override exists.</li>
	 * </ul>
	 *
	 * @param run        the pipeline run being evaluated
	 * @param role       the PDLC role about to execute (or just completed)
	 * @param beforeRole {@code true} to check the "before" gate, {@code false} for the "after" gate
	 * @param userConfig the user's pipeline checkpoint configuration
	 * @return {@code true} if a checkpoint should be created
	 */
	public boolean shouldCheckpoint(PipelineRun run, PdlcRole role, boolean beforeRole,
			UserConfig userConfig) {

		// User-level auto-approve bypasses all checkpoints
		var pipelineCheckpoints = userConfig != null ? userConfig.getPipelineCheckpoints() : null;
		if (pipelineCheckpoints != null && pipelineCheckpoints.isAutoApproveAll()) {
			return false;
		}

		// Check for per-role user override
		if (pipelineCheckpoints != null) {
			var roleCheckpoints = pipelineCheckpoints.getRoleCheckpoints();
			if (roleCheckpoints != null) {
				var override = roleCheckpoints.get(role.name());
				if (override != null) {
					return beforeRole ? override.isBeforeRole() : override.isAfterRole();
				}
			}

			// Apply well-known convenience flags (no per-role override present)
			if (!beforeRole) {
				if (role == PdlcRole.PM && pipelineCheckpoints.isApproveRequirements()) {
					return true;
				}
				if (role == PdlcRole.ARCHITECT && pipelineCheckpoints.isApproveArchitecture()) {
					return true;
				}
			}
			if (beforeRole && role == PdlcRole.DEVOPS && pipelineCheckpoints.isApproveBeforeDeploy()) {
				return true;
			}
		}

		// Fall back to the playbook default (requires caller to pass the playbook config)
		// This method is a pure predicate; playbook default checks are performed by the orchestrator
		// via isCheckpointBeforeRequired / isCheckpointAfterRequired and then delegated here.
		return false;
	}

	/**
	 * Find the most-recent unresolved checkpoint for a pipeline run.
	 *
	 * @param pipelineRunId the pipeline run to look up
	 * @return the pending (unresolved) checkpoint, or empty if none exists
	 */
	public Optional<Checkpoint> getPendingCheckpoint(String pipelineRunId) {
		return checkpointRepository.findByPipelineRunId(pipelineRunId).stream()
				.filter(cp -> cp.getUserDecision() == null)
				.findFirst();
	}

	/**
	 * Load a checkpoint by its ID.
	 *
	 * @param checkpointId the checkpoint document ID
	 * @return the checkpoint, or empty if not found
	 */
	public Optional<Checkpoint> getCheckpoint(String checkpointId) {
		return checkpointRepository.findById(checkpointId);
	}

}
