package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.business.agent.service.UserBroadcaster;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import io.strategiz.social.data.repository.PipelineEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single fan-out point for all PDLC pipeline events. Persists pipeline events to Firestore and
 * pushes real-time updates to connected user clients via WebSocket.
 *
 * <p><strong>Ownership contract:</strong> This class is responsible only for persisting
 * {@link PipelineEvent} documents and broadcasting WebSocket notifications. All
 * {@link PipelineRun} mutations and saves are the exclusive responsibility of
 * {@link PipelineStateManager}. This class must never call {@code run.set*()} or
 * {@code pipelineRunRepository.save()}.
 *
 * <p>WebSocket push is performed via {@link UserBroadcaster}, which is injected as
 * {@code Optional} so the emitter degrades gracefully when no WebSocket layer is present
 * (e.g. unit tests, or deployments without the service-agent module on the classpath).
 *
 * <p>FCM push notifications are not yet wired — a {@code FcmService} interface should be
 * added to business-agent and wired here (also via {@code Optional}) once it exists.
 */
@Service
public class PipelineEventEmitter {

	private static final Logger logger = LoggerFactory.getLogger(PipelineEventEmitter.class);

	/** Event types that are significant enough to warrant a WebSocket push to the client. */
	private static final Set<PipelineEventType> BROADCAST_EVENTS = Set.of(
			PipelineEventType.PIPELINE_STARTED,
			PipelineEventType.PIPELINE_COMPLETED,
			PipelineEventType.PIPELINE_FAILED,
			PipelineEventType.PIPELINE_CANCELLED,
			PipelineEventType.ROLE_STARTED,
			PipelineEventType.ROLE_COMPLETED,
			PipelineEventType.ROLE_SKIPPED,
			PipelineEventType.REWORK_TRIGGERED,
			PipelineEventType.CHECKPOINT_REQUESTED,
			PipelineEventType.CHECKPOINT_RESOLVED,
			PipelineEventType.COST_THRESHOLD_WARNING,
			PipelineEventType.COST_CEILING_REACHED
	);

	private final PipelineEventRepository pipelineEventRepository;

	private final Optional<UserBroadcaster> userBroadcaster;

	public PipelineEventEmitter(PipelineEventRepository pipelineEventRepository,
			Optional<UserBroadcaster> userBroadcaster) {
		this.pipelineEventRepository = pipelineEventRepository;
		this.userBroadcaster = userBroadcaster;
	}

	/**
	 * Persist a pipeline event and push a real-time WebSocket notification when appropriate.
	 *
	 * <p>This method does NOT modify the {@link PipelineRun}. All run state mutations must be
	 * performed by {@link PipelineStateManager} before calling this method so that the
	 * WebSocket payload reflects the already-committed run state.
	 *
	 * @param run      the pipeline run this event belongs to (read-only; not mutated here)
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

		// 2. Push real-time WebSocket event to connected user clients
		if (BROADCAST_EVENTS.contains(type)) {
			pushWebSocketEvent(event, run);
		}

		// 3. TODO: Wire FCM push notifications for milestone events (PIPELINE_STARTED,
		//    PIPELINE_COMPLETED, PIPELINE_FAILED, CHECKPOINT_REQUESTED) once a FcmService
		//    interface exists in business-agent. Use Optional<FcmService> injection, same
		//    pattern as UserBroadcaster.

		logger.info("Pipeline event emitted: runId={} sparkId={} userId={} type={} role={}",
				run.getId(), run.getSparkId(), run.getUserId(), type,
				role != null ? role.name() : "none");
	}

	/**
	 * Emit a PIPELINE_STARTED event.
	 *
	 * <p>The run status must already be set to EXECUTING by {@link PipelineStateManager} before
	 * calling this method.
	 *
	 * @param run the pipeline run that has just started
	 */
	public void emitPipelineStarted(PipelineRun run) {
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
	 * Serialize the pipeline event as a WebSocket message and push it to all of the user's
	 * connected client sessions. The message type is {@code "pipeline_event"} so mobile and
	 * browser clients can route it independently from spark-level events.
	 *
	 * <p>Failures are logged but do not propagate — a WebSocket delivery failure must never
	 * abort pipeline execution.
	 *
	 * @param event the persisted event to broadcast
	 * @param run   the pipeline run, used for additional context fields
	 */
	private void pushWebSocketEvent(PipelineEvent event, PipelineRun run) {
		userBroadcaster.ifPresent(broadcaster -> {
			try {
				Map<String, Object> payload = new HashMap<>();
				payload.put("type", "pipeline_event");
				payload.put("pipelineRunId", event.getPipelineRunId());
				payload.put("sparkId", event.getSparkId());
				payload.put("eventType", event.getEventType().name());
				payload.put("role", event.getRole() != null ? event.getRole().name() : null);
				payload.put("status", run.getStatus() != null ? run.getStatus().name() : null);
				payload.put("timestamp", event.getTimestamp() != null ? event.getTimestamp().toEpochMilli() : null);
				if (event.getMetadata() != null && !event.getMetadata().isEmpty()) {
					payload.put("metadata", event.getMetadata());
				}
				broadcaster.broadcastToUser(event.getUserId(), payload);
			}
			catch (Exception ex) {
				logger.error("Failed to push pipeline WebSocket event: runId={} type={}",
						event.getPipelineRunId(), event.getEventType(), ex);
			}
		});
	}

}
