package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled watchdog that detects PDLC pipeline roles that have exceeded their configured timeout.
 *
 * <p>Runs every 60 seconds. For each EXECUTING pipeline run, it resolves the current role's
 * timeout from the playbook and checks whether the role has been executing longer than allowed.
 * When a timeout is detected, a REWORK_ESCALATED event is emitted and a warning is logged.
 *
 * <p>Actual timeout action (marking the run FAILED, escalating to a checkpoint) will be wired
 * when PdlcPipelineOrchestrator is available. For now only events and logs are produced.
 */
@Component
public class PipelineWatchdog {

	private static final Logger logger = LoggerFactory.getLogger(PipelineWatchdog.class);

	private final PipelineRunRepository pipelineRunRepository;

	private final PipelineEventEmitter pipelineEventEmitter;

	private final PlaybookRegistry playbookRegistry;

	public PipelineWatchdog(PipelineRunRepository pipelineRunRepository,
			PipelineEventEmitter pipelineEventEmitter,
			PlaybookRegistry playbookRegistry) {
		this.pipelineRunRepository = pipelineRunRepository;
		this.pipelineEventEmitter = pipelineEventEmitter;
		this.playbookRegistry = playbookRegistry;
	}

	/**
	 * Checks all EXECUTING pipeline runs for roles that have exceeded their configured timeout.
	 * Runs every 60 seconds with a fixed delay between executions.
	 */
	@Scheduled(fixedDelay = 60_000)
	public void checkForTimedOutRoles() {
		List<PipelineRun> executingRuns = pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING);

		for (PipelineRun run : executingRuns) {
			checkRunForTimeout(run);
		}
	}

	// --- Private helpers ---

	private void checkRunForTimeout(PipelineRun run) {
		if (run.getCurrentRole() == null) {
			return;
		}

		String playbookName = run.getPlaybook();
		if (playbookName == null) {
			logger.debug("PipelineWatchdog: run {} has no playbook set, skipping timeout check", run.getId());
			return;
		}

		Optional<PlaybookConfig> playbookOpt = playbookRegistry.getPlaybook(playbookName);
		if (playbookOpt.isEmpty()) {
			logger.warn("PipelineWatchdog: run {} references unknown playbook '{}', skipping timeout check",
					run.getId(), playbookName);
			return;
		}

		PlaybookConfig playbook = playbookOpt.get();
		Optional<PlaybookStage> stageOpt = playbook.stages().stream()
				.filter(s -> s.role() == run.getCurrentRole())
				.findFirst();

		if (stageOpt.isEmpty()) {
			logger.debug("PipelineWatchdog: role {} not found in playbook {} for run {}, skipping",
					run.getCurrentRole(), playbookName, run.getId());
			return;
		}

		PlaybookStage stage = stageOpt.get();
		Duration timeout = stage.timeout();

		// Determine when the current role started: use run.startedAt as the best available proxy.
		// When PipelineRun gains per-role startedAt tracking (via roleResults or events), this
		// can be refined to use the ROLE_STARTED event timestamp.
		Instant roleStartedAt = run.getStartedAt();
		if (roleStartedAt == null) {
			logger.debug("PipelineWatchdog: run {} has no startedAt, skipping timeout check", run.getId());
			return;
		}

		Duration elapsed = Duration.between(roleStartedAt, Instant.now());

		if (elapsed.compareTo(timeout) > 0) {
			logger.warn("PipelineWatchdog: role {} in run {} has timed out (elapsed={}m, timeout={}m)",
					run.getCurrentRole(), run.getId(),
					elapsed.toMinutes(), timeout.toMinutes());

			Map<String, Object> metadata = Map.of(
					"timedOutRole", run.getCurrentRole().name(),
					"elapsedMinutes", elapsed.toMinutes(),
					"timeoutMinutes", timeout.toMinutes());

			pipelineEventEmitter.emitEvent(run, PipelineEventType.REWORK_ESCALATED,
					run.getCurrentRole(), metadata);

			// NOTE: Actual timeout action (mark FAILED, escalate) will be wired when
			// PdlcPipelineOrchestrator is available.
		}
	}

}
