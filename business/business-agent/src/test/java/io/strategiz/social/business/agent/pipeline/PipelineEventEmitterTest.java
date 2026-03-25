package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.strategiz.social.business.agent.service.UserBroadcaster;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.repository.PipelineEventRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineEventEmitterTest {

	private static final String RUN_ID = "run-123";

	private static final String SPARK_ID = "spark-456";

	private static final String USER_ID = "user-789";

	@Mock
	private PipelineEventRepository pipelineEventRepository;

	@Mock
	private UserBroadcaster userBroadcaster;

	/** Emitter with a live UserBroadcaster mock — used for WebSocket push tests. */
	private PipelineEventEmitter emitter;

	/** Emitter with no UserBroadcaster — verifies graceful degradation. */
	private PipelineEventEmitter emitterWithoutBroadcaster;

	private PipelineRun run;

	@BeforeEach
	void setUp() {
		emitter = new PipelineEventEmitter(pipelineEventRepository, Optional.of(userBroadcaster));
		emitterWithoutBroadcaster = new PipelineEventEmitter(pipelineEventRepository,
				Optional.empty());

		run = new PipelineRun();
		run.setId(RUN_ID);
		run.setSparkId(SPARK_ID);
		run.setUserId(USER_ID);
	}

	@Test
	void emitEvent_savesPipelineEventToRepository() {
		emitter.emitEvent(run, PipelineEventType.ROLE_STARTED, PdlcRole.IMPLEMENTER,
				Map.of("childSparkId", "child-001"));

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());

		PipelineEvent saved = captor.getValue();
		assertNotNull(saved.getId());
		assertEquals(RUN_ID, saved.getPipelineRunId());
		assertEquals(SPARK_ID, saved.getSparkId());
		assertEquals(USER_ID, saved.getUserId());
		assertEquals(PipelineEventType.ROLE_STARTED, saved.getEventType());
		assertEquals(PdlcRole.IMPLEMENTER, saved.getRole());
		assertNotNull(saved.getTimestamp());
	}

	@Test
	void emitEvent_doesNotMutateRunOrSaveRun_onRoleStarted() {
		PipelineStatus statusBefore = run.getStatus();
		PdlcRole currentRoleBefore = run.getCurrentRole();

		emitter.emitEvent(run, PipelineEventType.ROLE_STARTED, PdlcRole.ARCHITECT, Map.of());

		// Emitter must not mutate the run — PipelineStateManager owns all run mutations
		assertEquals(statusBefore, run.getStatus());
		assertEquals(currentRoleBefore, run.getCurrentRole());
	}

	@Test
	void emitEvent_doesNotMutateRunOrSaveRun_onPipelineCompleted() {
		PipelineStatus statusBefore = run.getStatus();

		emitter.emitEvent(run, PipelineEventType.PIPELINE_COMPLETED, null, Map.of());

		assertEquals(statusBefore, run.getStatus());
	}

	@Test
	void emitEvent_doesNotMutateRunOrSaveRun_onPipelineFailed() {
		PipelineStatus statusBefore = run.getStatus();

		emitter.emitEvent(run, PipelineEventType.PIPELINE_FAILED, null, Map.of());

		assertEquals(statusBefore, run.getStatus());
	}

	@Test
	void emitRoleCompleted_savesEventWithCorrectMetadata() {
		emitter.emitRoleCompleted(run, PdlcRole.TESTER, 1500L, new BigDecimal("0.05"), 3000L,
				"claude-sonnet-4-5");

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());

		PipelineEvent saved = captor.getValue();
		assertEquals(PipelineEventType.ROLE_COMPLETED, saved.getEventType());
		assertEquals(PdlcRole.TESTER, saved.getRole());
		assertEquals(1500L, saved.getMetadata().get("tokens"));
		assertEquals(new BigDecimal("0.05"), saved.getMetadata().get("cost"));
		assertEquals(3000L, saved.getMetadata().get("durationMs"));
		assertEquals("claude-sonnet-4-5", saved.getMetadata().get("model"));
	}

	@Test
	void emitReworkTriggered_doesNotMutateReworkCount() {
		assertEquals(0, run.getReworkCount());

		emitter.emitReworkTriggered(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER,
				"Output did not meet acceptance criteria");

		// Emitter must not increment reworkCount — PipelineStateManager.incrementRework() owns this
		assertEquals(0, run.getReworkCount());

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());

		PipelineEvent saved = captor.getValue();
		assertEquals(PipelineEventType.REWORK_TRIGGERED, saved.getEventType());
		assertEquals("REVIEWER", saved.getMetadata().get("rejectingRole"));
		assertEquals("IMPLEMENTER", saved.getMetadata().get("targetRole"));
		assertEquals("Output did not meet acceptance criteria", saved.getMetadata().get("reason"));
	}

	@Test
	void emitReworkTriggered_savesEventForEachCall() {
		emitter.emitReworkTriggered(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER, "First rejection");
		emitter.emitReworkTriggered(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER, "Second rejection");

		// reworkCount must stay untouched — belongs to StateManager
		assertEquals(0, run.getReworkCount());
		verify(pipelineEventRepository, times(2)).save(org.mockito.ArgumentMatchers.any(PipelineEvent.class));
	}

	@Test
	void emitPipelineStarted_savesEventWithCorrectType() {
		emitter.emitPipelineStarted(run);

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());
		assertEquals(PipelineEventType.PIPELINE_STARTED, captor.getValue().getEventType());
	}

	@Test
	void emitPipelineStarted_doesNotMutateRunStatus() {
		PipelineStatus statusBefore = run.getStatus();

		emitter.emitPipelineStarted(run);

		assertEquals(statusBefore, run.getStatus());
	}

	@Test
	void emitEvent_costThresholdWarning_savesEventOnly() {
		Map<String, Object> warningDetails = Map.of("currentCost", "4.50", "threshold", "5.00");

		emitter.emitEvent(run, PipelineEventType.COST_THRESHOLD_WARNING, null, warningDetails);

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());
		assertEquals(PipelineEventType.COST_THRESHOLD_WARNING, captor.getValue().getEventType());
	}

	@Test
	void emitEvent_costCeilingReached_savesEventOnly() {
		Map<String, Object> ceilingDetails = Map.of("currentCost", "10.00", "ceiling", "10.00");

		emitter.emitEvent(run, PipelineEventType.COST_CEILING_REACHED, null, ceilingDetails);

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());
		assertEquals(PipelineEventType.COST_CEILING_REACHED, captor.getValue().getEventType());
	}

	// --- WebSocket broadcast tests ---

	@Test
	@SuppressWarnings("unchecked")
	void emitEvent_broadcastsWebSocketPayload_forBroadcastEventType() {
		emitter.emitEvent(run, PipelineEventType.PIPELINE_STARTED, null, Map.of());

		ArgumentCaptor<Map<String, Object>> payloadCaptor =
				(ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) forClass(Map.class);
		verify(userBroadcaster).broadcastToUser(org.mockito.ArgumentMatchers.eq(USER_ID),
				payloadCaptor.capture());

		Map<String, Object> payload = payloadCaptor.getValue();
		assertEquals("pipeline_event", payload.get("type"));
		assertEquals(RUN_ID, payload.get("pipelineRunId"));
		assertEquals(SPARK_ID, payload.get("sparkId"));
		assertEquals("PIPELINE_STARTED", payload.get("eventType"));
		assertNotNull(payload.get("timestamp"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void emitEvent_includesRoleInWebSocketPayload_whenRoleIsPresent() {
		emitter.emitEvent(run, PipelineEventType.ROLE_STARTED, PdlcRole.ARCHITECT, Map.of());

		ArgumentCaptor<Map<String, Object>> payloadCaptor =
				(ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) forClass(Map.class);
		verify(userBroadcaster).broadcastToUser(org.mockito.ArgumentMatchers.eq(USER_ID),
				payloadCaptor.capture());

		Map<String, Object> payload = payloadCaptor.getValue();
		assertEquals("ARCHITECT", payload.get("role"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void emitEvent_includesMetadataInWebSocketPayload_whenMetadataIsNonEmpty() {
		Map<String, Object> meta = Map.of("childSparkId", "child-999");

		emitter.emitEvent(run, PipelineEventType.ROLE_STARTED, PdlcRole.IMPLEMENTER, meta);

		ArgumentCaptor<Map<String, Object>> payloadCaptor =
				(ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) forClass(Map.class);
		verify(userBroadcaster).broadcastToUser(org.mockito.ArgumentMatchers.eq(USER_ID),
				payloadCaptor.capture());

		Map<String, Object> payload = payloadCaptor.getValue();
		assertNotNull(payload.get("metadata"));
	}

	@Test
	void emitEvent_doesNotBroadcast_forNonBroadcastEventType() {
		// ROLE_SKIPPED is not in BROADCAST_EVENTS
		emitter.emitEvent(run, PipelineEventType.ROLE_SKIPPED, PdlcRole.TESTER, Map.of());

		verify(userBroadcaster, never()).broadcastToUser(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void emitEvent_gracefullyDegrades_whenNoBroadcasterPresent() {
		// Must not throw even when UserBroadcaster is absent
		emitterWithoutBroadcaster.emitEvent(run, PipelineEventType.PIPELINE_COMPLETED, null, Map.of());

		// Event is still persisted
		verify(pipelineEventRepository).save(org.mockito.ArgumentMatchers.any(PipelineEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void emitEvent_includesRunStatusInWebSocketPayload() {
		// Status is pre-set on the run by PipelineStateManager before calling emitEvent
		run.setStatus(PipelineStatus.COMPLETED);
		emitter.emitEvent(run, PipelineEventType.PIPELINE_COMPLETED, null, Map.of());

		ArgumentCaptor<Map<String, Object>> payloadCaptor =
				(ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) forClass(Map.class);
		verify(userBroadcaster).broadcastToUser(org.mockito.ArgumentMatchers.eq(USER_ID),
				payloadCaptor.capture());

		// The status in the payload reflects what StateManager set before calling emitEvent
		assertEquals("COMPLETED", payloadCaptor.getValue().get("status"));
	}

}
