package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.repository.PipelineEventRepository;
import io.strategiz.social.data.repository.PipelineRunRepository;
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
	private PipelineRunRepository pipelineRunRepository;

	private PipelineEventEmitter emitter;

	private PipelineRun run;

	@BeforeEach
	void setUp() {
		emitter = new PipelineEventEmitter(pipelineEventRepository, pipelineRunRepository, Optional.empty());

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
	void emitEvent_updatesRunStatusToExecutingOnRoleStarted() {
		emitter.emitEvent(run, PipelineEventType.ROLE_STARTED, PdlcRole.ARCHITECT, Map.of());

		assertEquals(PipelineStatus.EXECUTING, run.getStatus());
		assertEquals(PdlcRole.ARCHITECT, run.getCurrentRole());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void emitEvent_setsStatusToCompletedAndCompletedAtOnPipelineCompleted() {
		emitter.emitEvent(run, PipelineEventType.PIPELINE_COMPLETED, null, Map.of());

		assertEquals(PipelineStatus.COMPLETED, run.getStatus());
		assertNotNull(run.getCompletedAt());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void emitEvent_setsStatusToFailedOnPipelineFailed() {
		emitter.emitEvent(run, PipelineEventType.PIPELINE_FAILED, null, Map.of());

		assertEquals(PipelineStatus.FAILED, run.getStatus());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void emitRoleCompleted_updatesRoleResultsMap() {
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

		// Run should have the role result stored under the role name
		assertNotNull(run.getRoleResults().get(PdlcRole.TESTER.name()));
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void emitReworkTriggered_incrementsReworkCount() {
		assertEquals(0, run.getReworkCount());

		emitter.emitReworkTriggered(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER,
				"Output did not meet acceptance criteria");

		assertEquals(1, run.getReworkCount());

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());

		PipelineEvent saved = captor.getValue();
		assertEquals(PipelineEventType.REWORK_TRIGGERED, saved.getEventType());
		assertEquals("REVIEWER", saved.getMetadata().get("rejectingRole"));
		assertEquals("IMPLEMENTER", saved.getMetadata().get("targetRole"));
		assertEquals("Output did not meet acceptance criteria", saved.getMetadata().get("reason"));
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void emitReworkTriggered_accumulatesReworkCount_acrossMultipleCalls() {
		emitter.emitReworkTriggered(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER, "First rejection");
		emitter.emitReworkTriggered(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER, "Second rejection");

		assertEquals(2, run.getReworkCount());
		verify(pipelineEventRepository, times(2)).save(org.mockito.ArgumentMatchers.any(PipelineEvent.class));
		verify(pipelineRunRepository, times(2)).save(run);
	}

	@Test
	void emitPipelineStarted_setsStatusToExecuting() {
		// PipelineRun defaults to PipelineStatus.CREATED
		assertEquals(PipelineStatus.CREATED, run.getStatus());

		emitter.emitPipelineStarted(run);

		assertEquals(PipelineStatus.EXECUTING, run.getStatus());

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());
		assertEquals(PipelineEventType.PIPELINE_STARTED, captor.getValue().getEventType());
	}

	@Test
	void emitEvent_costThresholdWarning_savesEventAndPersistsRun() {
		Map<String, Object> warningDetails = Map.of("currentCost", "4.50", "threshold", "5.00");

		emitter.emitEvent(run, PipelineEventType.COST_THRESHOLD_WARNING, null, warningDetails);

		// Cost warning events are captured in the event document only; no run summary field update
		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());
		assertEquals(PipelineEventType.COST_THRESHOLD_WARNING, captor.getValue().getEventType());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void emitEvent_costCeilingReached_savesEventAndPersistsRun() {
		Map<String, Object> ceilingDetails = Map.of("currentCost", "10.00", "ceiling", "10.00");

		emitter.emitEvent(run, PipelineEventType.COST_CEILING_REACHED, null, ceilingDetails);

		ArgumentCaptor<PipelineEvent> captor = forClass(PipelineEvent.class);
		verify(pipelineEventRepository).save(captor.capture());
		assertEquals(PipelineEventType.COST_CEILING_REACHED, captor.getValue().getEventType());
		verify(pipelineRunRepository).save(run);
	}

}
