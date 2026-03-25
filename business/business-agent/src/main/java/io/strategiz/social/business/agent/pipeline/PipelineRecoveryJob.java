package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.repository.PipelineRunRepository;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Crash recovery job that runs on startup to resume pipeline runs that were interrupted mid-execution.
 *
 * <p>On startup, queries for all EXECUTING pipelines and claims any that are unclaimed or whose
 * claim is stale (older than 30 minutes). For each claimed run it emits a PIPELINE_RESUMED event
 * and dispatches re-execution via {@link PdlcPipelineOrchestrator#executePipeline(String)}, which
 * resumes from the current role asynchronously on the pipeline thread pool.
 */
@Component
public class PipelineRecoveryJob {

	private static final Logger logger = LoggerFactory.getLogger(PipelineRecoveryJob.class);

	static final Duration CLAIM_STALENESS_THRESHOLD = Duration.ofMinutes(30);

	private final PipelineRunRepository pipelineRunRepository;

	private final PipelineEventEmitter pipelineEventEmitter;

	private final PdlcPipelineOrchestrator pdlcPipelineOrchestrator;

	public PipelineRecoveryJob(PipelineRunRepository pipelineRunRepository,
			PipelineEventEmitter pipelineEventEmitter,
			PdlcPipelineOrchestrator pdlcPipelineOrchestrator) {
		this.pipelineRunRepository = pipelineRunRepository;
		this.pipelineEventEmitter = pipelineEventEmitter;
		this.pdlcPipelineOrchestrator = pdlcPipelineOrchestrator;
	}

	/**
	 * Scans for EXECUTING pipeline runs on startup and re-claims any that are unclaimed or stale.
	 * Emits a PIPELINE_RESUMED event for each successfully claimed run.
	 */
	@PostConstruct
	public void recoverInterruptedPipelines() {
		String instanceId = UUID.randomUUID().toString();
		logger.info("PipelineRecoveryJob starting with instanceId={}", instanceId);

		List<PipelineRun> executingRuns = pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING);

		if (executingRuns.isEmpty()) {
			logger.info("PipelineRecoveryJob: no interrupted pipelines found");
			return;
		}

		logger.info("PipelineRecoveryJob: found {} EXECUTING pipeline(s) to inspect", executingRuns.size());

		for (PipelineRun run : executingRuns) {
			tryClaimRun(run, instanceId);
		}
	}

	// --- Private helpers ---

	private void tryClaimRun(PipelineRun run, String instanceId) {
		String runId = run.getId();

		// Skip runs already claimed by an active instance (claim is fresh)
		if (isActivelyClaimed(run)) {
			logger.debug("PipelineRecoveryJob: skipping run {} — claimed by {} at {}",
					runId, run.getClaimedBy(), run.getClaimedAt());
			return;
		}

		// Claim the run
		run.setClaimedBy(instanceId);
		run.setClaimedAt(Instant.now());
		pipelineRunRepository.save(run);

		// Emit PIPELINE_RESUMED event
		String currentRole = run.getCurrentRole() != null ? run.getCurrentRole().name() : "unknown";
		Map<String, Object> metadata = Map.of(
				"recoveredBy", instanceId,
				"resumedFromRole", currentRole);

		pipelineEventEmitter.emitEvent(run, PipelineEventType.PIPELINE_RESUMED, run.getCurrentRole(), metadata);

		logger.info("Resumed pipeline {} from role {}", runId, currentRole);

		// Dispatch re-execution asynchronously — the orchestrator loads the run, checks which roles
		// are already completed, and continues from the current role.
		pdlcPipelineOrchestrator.executePipeline(runId);
	}

	/**
	 * Returns true if the run is claimed by another instance and the claim is still fresh
	 * (within the staleness threshold).
	 */
	private boolean isActivelyClaimed(PipelineRun run) {
		if (run.getClaimedBy() == null || run.getClaimedAt() == null) {
			return false;
		}
		Duration age = Duration.between(run.getClaimedAt(), Instant.now());
		return age.compareTo(CLAIM_STALENESS_THRESHOLD) < 0;
	}

}
