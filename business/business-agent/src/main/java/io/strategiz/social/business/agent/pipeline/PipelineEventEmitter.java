package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import io.strategiz.social.data.repository.PipelineEventRepository;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single fan-out point for all PDLC pipeline state changes. Persists pipeline events to
 * Firestore and keeps the PipelineRun summary fields in sync.
 *
 * <p>Future integrations (WebSocket push, FCM notifications) should be wired here once
 * the relevant broadcasters are available.
 */
@Service
public class PipelineEventEmitter {

	private static final Logger logger = LoggerFactory.getLogger(PipelineEventEmitter.class);

	private final PipelineEventRepository pipelineEventRepository;

	private final PipelineRunRepository pipelineRunRepository;

	public PipelineEventEmitter(PipelineEventRepository pipelineEventRepository,
			PipelineRunRepository pipelineRunRepository) {
		this.pipelineEventRepository = pipelineEventRepository;
		this.pipelineRunRepository = pipelineRunRepository;
	}

	/**
	 * Persist a pipeline event and update the PipelineRun summary based on the event type.
	 *
	 * @param run      the pipeline run this event belongs to
	 * @param type     the type of event
	 * @param role     the PDLC role associated with this event (may be null for run-level events)
	 * @param metadata arbitrary key-value pairs specific to this event type
	 */
	public void emitEvent(PipelineRun run, PipelineEventType type, PdlcRole role,
			Map<String, Object> metadata) {

		// 1. Build and persist the event
		PipelineEvent event = new PipelineEvent();
		event.setId(UUID.randomUUID().toString());
		event.setPipelineRunId(run.getId());
		event.setSparkId(run.getSparkId());
		event.setUserId(run.getUserId());
		event.setEventType(type);
		event.setRole(role);
		event.setMetadata(metadata);
		event.setTimestamp(Instant.now());

		pipelineEventRepository.save(event);

		// 2. Update PipelineRun summary fields
		switch (type) {
			case ROLE_STARTED -> {
				run.setCurrentRole(role);
				run.setStatus(PipelineStatus.EXECUTING);
			}
			case ROLE_COMPLETED -> {
				if (role != null && metadata != null) {
					RoleResultSummary summary = buildRoleResultSummary(metadata);
					run.getRoleResults().put(role.name(), summary);
				}
			}
			case PIPELINE_COMPLETED -> {
				run.setStatus(PipelineStatus.COMPLETED);
				run.setCompletedAt(Instant.now());
			}
			case PIPELINE_FAILED -> {
				run.setStatus(PipelineStatus.FAILED);
			}
			case REWORK_TRIGGERED -> {
				run.setReworkCount(run.getReworkCount() + 1);
			}
			case COST_THRESHOLD_WARNING, COST_CEILING_REACHED -> {
				// Cost warning metadata is captured in the event document only;
				// PipelineRun has no metadata map — consumers read the event stream.
			}
			default -> {
				// PIPELINE_STARTED and other informational events require no run summary update
			}
		}

		// 3. Persist updated run
		pipelineRunRepository.save(run);

		// 4. TODO: Push WebSocket event to connected clients (wire ActivityBroadcaster once integrated)
		// 5. TODO: Send FCM push notification for significant events (PIPELINE_COMPLETED, PIPELINE_FAILED)

		logger.info("Pipeline event emitted: runId={} sparkId={} userId={} type={} role={}",
				run.getId(), run.getSparkId(), run.getUserId(), type,
				role != null ? role.name() : "none");
	}

	/**
	 * Emit a PIPELINE_STARTED event. Sets the run status to EXECUTING.
	 *
	 * @param run the pipeline run that has just started
	 */
	public void emitPipelineStarted(PipelineRun run) {
		run.setStatus(PipelineStatus.EXECUTING);
		emitEvent(run, PipelineEventType.PIPELINE_STARTED, null, Map.of());
	}

	/**
	 * Emit a ROLE_STARTED event, including the child spark ID that will execute this role.
	 *
	 * @param run          the pipeline run
	 * @param role         the PDLC role that is starting
	 * @param childSparkId the ID of the child spark driving this role's execution
	 */
	public void emitRoleStarted(PipelineRun run, PdlcRole role, String childSparkId) {
		Map<String, Object> metadata = Map.of("childSparkId", childSparkId);
		emitEvent(run, PipelineEventType.ROLE_STARTED, role, metadata);
	}

	/**
	 * Emit a ROLE_COMPLETED event with execution metrics for the completed role.
	 *
	 * @param run        the pipeline run
	 * @param role       the PDLC role that has completed
	 * @param tokens     total tokens consumed by this role
	 * @param cost       estimated cost for this role
	 * @param durationMs wall-clock time in milliseconds
	 * @param model      the model identifier used for this role
	 */
	public void emitRoleCompleted(PipelineRun run, PdlcRole role, long tokens, BigDecimal cost,
			long durationMs, String model) {
		Map<String, Object> metadata = Map.of(
				"tokens", tokens,
				"cost", cost,
				"durationMs", durationMs,
				"model", model);
		emitEvent(run, PipelineEventType.ROLE_COMPLETED, role, metadata);
	}

	/**
	 * Emit a REWORK_TRIGGERED event when a reviewing role rejects output and targets another
	 * role for revision.
	 *
	 * @param run           the pipeline run
	 * @param rejectingRole the role that rejected the previous output
	 * @param targetRole    the role whose output must be revised
	 * @param reason        human-readable explanation of why rework is needed
	 */
	public void emitReworkTriggered(PipelineRun run, PdlcRole rejectingRole, PdlcRole targetRole,
			String reason) {
		Map<String, Object> metadata = Map.of(
				"rejectingRole", rejectingRole != null ? rejectingRole.name() : "",
				"targetRole", targetRole != null ? targetRole.name() : "",
				"reason", reason != null ? reason : "");
		emitEvent(run, PipelineEventType.REWORK_TRIGGERED, rejectingRole, metadata);
	}

	// --- Helpers ---

	/**
	 * Build a {@link RoleResultSummary} from the raw metadata map produced by
	 * {@link #emitRoleCompleted}. Fields not present in the metadata are left at defaults.
	 */
	private RoleResultSummary buildRoleResultSummary(Map<String, Object> metadata) {
		RoleResultSummary summary = new RoleResultSummary();
		summary.setStatus(RoleStatus.COMPLETED);

		Object tokens = metadata.get("tokens");
		if (tokens instanceof Number num) {
			summary.setTokens(num.longValue());
		}

		Object cost = metadata.get("cost");
		if (cost instanceof BigDecimal bd) {
			summary.setCost(bd);
		}

		Object durationMs = metadata.get("durationMs");
		if (durationMs instanceof Number num) {
			summary.setDurationMs(num.longValue());
		}

		Object model = metadata.get("model");
		if (model instanceof String s) {
			summary.setModel(s);
		}

		Object childSparkId = metadata.get("childSparkId");
		if (childSparkId instanceof String s) {
			summary.setChildSparkId(s);
		}

		return summary;
	}

}
