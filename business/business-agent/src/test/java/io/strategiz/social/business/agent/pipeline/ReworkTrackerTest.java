package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReworkTrackerTest {

	private static final String RUN_ID = "run-abc";

	private static final String SPARK_ID = "spark-xyz";

	private static final String USER_ID = "user-001";

	@Mock
	private PipelineEventEmitter pipelineEventEmitter;

	@Mock
	private PipelineRunRepository pipelineRunRepository;

	private ReworkTracker reworkTracker;

	@BeforeEach
	void setUp() {
		reworkTracker = new ReworkTracker(pipelineEventEmitter, pipelineRunRepository);
	}

	// --- helpers ---

	private PipelineRun createRun(int reworkCount) {
		PipelineRun run = new PipelineRun();
		run.setId(RUN_ID);
		run.setSparkId(SPARK_ID);
		run.setUserId(USER_ID);
		run.setReworkCount(reworkCount);
		return run;
	}

	// --- handleRework tests ---

	@Test
	void handleRework_returnsTrueWhenUnderMaxLimit() {
		// reworkCount starts at 0; after emitReworkTriggered it will be 1 (< 3)
		PipelineRun run = createRun(0);

		boolean result = reworkTracker.handleRework(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER,
				"Output quality insufficient");

		assertTrue(result, "Should return true (proceed) when rework count is under the limit");
	}

	@Test
	void handleRework_returnsFalseWhenMaxLimitExceeded() {
		// reworkCount already at 3 (== DEFAULT_MAX_REWORK), so isMaxReworkExceeded returns true
		PipelineRun run = createRun(3);

		boolean result = reworkTracker.handleRework(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER,
				"Still failing acceptance criteria");

		assertFalse(result, "Should return false (halt) when rework count equals or exceeds max");
	}

	@Test
	void handleRework_emitsReworkTriggeredEvent() {
		PipelineRun run = createRun(1);

		reworkTracker.handleRework(run, PdlcRole.TESTER, PdlcRole.IMPLEMENTER, "Tests still failing");

		verify(pipelineEventEmitter).emitReworkTriggered(run, PdlcRole.TESTER, PdlcRole.IMPLEMENTER,
				"Tests still failing");
	}

	@Test
	void handleRework_incrementsReworkCountViaEmitter() {
		// The emitter's REWORK_TRIGGERED handler increments reworkCount on the run object
		PipelineRun run = createRun(0);

		// Simulate the emitter incrementing reworkCount (as PipelineEventEmitter does in production)
		doAnswer(invocation -> {
			run.setReworkCount(run.getReworkCount() + 1);
			return null;
		}).when(pipelineEventEmitter).emitReworkTriggered(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER,
				"Bad output");

		reworkTracker.handleRework(run, PdlcRole.REVIEWER, PdlcRole.IMPLEMENTER, "Bad output");

		assertEquals(1, run.getReworkCount());
	}

	@Test
	void handleRework_returnsTrueAtCountTwoWithDefaultMax() {
		// reworkCount=2 < DEFAULT_MAX_REWORK=3 → proceed
		PipelineRun run = createRun(2);

		boolean result = reworkTracker.handleRework(run, PdlcRole.ARCHITECT, PdlcRole.PM,
				"Scope still not well defined");

		assertTrue(result, "Count 2 is still under default max of 3");
	}

	// --- isMaxReworkExceeded tests ---

	@Test
	void isMaxReworkExceeded_returnsFalseWhenCountBelowMax() {
		PipelineRun run = createRun(2);

		boolean exceeded = reworkTracker.isMaxReworkExceeded(run, PdlcRole.IMPLEMENTER, 3);

		assertFalse(exceeded);
	}

	@Test
	void isMaxReworkExceeded_returnsTrueWhenCountEqualsMax() {
		PipelineRun run = createRun(3);

		boolean exceeded = reworkTracker.isMaxReworkExceeded(run, PdlcRole.IMPLEMENTER, 3);

		assertTrue(exceeded);
	}

	@Test
	void isMaxReworkExceeded_returnsTrueWhenCountExceedsMax() {
		PipelineRun run = createRun(5);

		boolean exceeded = reworkTracker.isMaxReworkExceeded(run, PdlcRole.TESTER, 3);

		assertTrue(exceeded);
	}

	@Test
	void isMaxReworkExceeded_returnsFalseWhenCountIsZero() {
		PipelineRun run = createRun(0);

		boolean exceeded = reworkTracker.isMaxReworkExceeded(run, PdlcRole.PM, 3);

		assertFalse(exceeded);
	}

	// --- getReworkCount tests ---

	@Test
	void getReworkCount_returnsCountFromRepository() {
		PipelineRun run = createRun(2);
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		int count = reworkTracker.getReworkCount(RUN_ID);

		assertEquals(2, count);
	}

	@Test
	void getReworkCount_returnsZeroWhenRunNotFound() {
		when(pipelineRunRepository.findById("nonexistent")).thenReturn(Optional.empty());

		int count = reworkTracker.getReworkCount("nonexistent");

		assertEquals(0, count);
	}

}
