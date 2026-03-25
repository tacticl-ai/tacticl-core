package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tracks rework cycles within a PDLC pipeline run. Emits REWORK_TRIGGERED events,
 * increments rework counts, and enforces a configurable maximum rework limit to prevent
 * infinite rejection loops.
 */
@Service
public class ReworkTracker {

	private static final Logger log = LoggerFactory.getLogger(ReworkTracker.class);

	private static final int DEFAULT_MAX_REWORK = 3;

	private final PipelineEventEmitter pipelineEventEmitter;

	private final PipelineRunRepository pipelineRunRepository;

	public ReworkTracker(PipelineEventEmitter pipelineEventEmitter,
			PipelineRunRepository pipelineRunRepository) {
		this.pipelineEventEmitter = pipelineEventEmitter;
		this.pipelineRunRepository = pipelineRunRepository;
	}

	/**
	 * Handle a rework request from a reviewing role. Emits a REWORK_TRIGGERED event,
	 * increments the run's rework count, and persists the updated run.
	 *
	 * @param run           the pipeline run requesting rework
	 * @param rejectingRole the role that rejected the previous output
	 * @param targetRole    the role whose output must be revised
	 * @param feedback      human-readable explanation of why rework is needed
	 * @return {@code true} if the pipeline should proceed with rework,
	 *         {@code false} if the maximum rework count has been exceeded
	 */
	public boolean handleRework(PipelineRun run, PdlcRole rejectingRole, PdlcRole targetRole,
			String feedback) {

		// Emit the REWORK_TRIGGERED event (this also increments reworkCount and saves the run
		// via PipelineEventEmitter's switch on REWORK_TRIGGERED)
		pipelineEventEmitter.emitReworkTriggered(run, rejectingRole, targetRole, feedback);

		boolean exceeded = isMaxReworkExceeded(run, targetRole, DEFAULT_MAX_REWORK);

		if (exceeded) {
			log.warn("[REWORK] Max rework exceeded for run={} targetRole={} count={}",
					run.getId(), targetRole, run.getReworkCount());
		}
		else {
			log.info("[REWORK] Rework triggered: run={} rejectingRole={} targetRole={} count={}",
					run.getId(), rejectingRole, targetRole, run.getReworkCount());
		}

		return !exceeded;
	}

	/**
	 * Determine whether the rework count for a given run has reached the maximum allowed
	 * iterations for the specified role.
	 *
	 * @param run       the pipeline run
	 * @param role      the role being evaluated (informational — limit applies run-wide)
	 * @param maxRework the maximum number of rework cycles permitted
	 * @return {@code true} if the rework limit has been reached or exceeded
	 */
	public boolean isMaxReworkExceeded(PipelineRun run, PdlcRole role, int maxRework) {
		int count = run.getReworkCount();
		boolean exceeded = count >= maxRework;
		log.debug("[REWORK] isMaxReworkExceeded: run={} role={} count={} max={} exceeded={}",
				run.getId(), role, count, maxRework, exceeded);
		return exceeded;
	}

	/**
	 * Return the current rework count for a pipeline run by ID.
	 *
	 * @param pipelineRunId the pipeline run ID to look up
	 * @return the rework count, or {@code 0} if the run is not found
	 */
	public int getReworkCount(String pipelineRunId) {
		Optional<PipelineRun> runOpt = pipelineRunRepository.findById(pipelineRunId);
		return runOpt.map(PipelineRun::getReworkCount).orElse(0);
	}

}
