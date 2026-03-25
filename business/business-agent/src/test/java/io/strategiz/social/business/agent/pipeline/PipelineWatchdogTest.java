package io.strategiz.social.business.agent.pipeline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineWatchdogTest {

	private static final String PLAYBOOK_NAME = "BUG_FIX";

	@Mock
	private PipelineRunRepository pipelineRunRepository;

	@Mock
	private PipelineEventEmitter pipelineEventEmitter;

	@Mock
	private PlaybookRegistry playbookRegistry;

	private PipelineWatchdog watchdog;

	@BeforeEach
	void setUp() {
		watchdog = new PipelineWatchdog(pipelineRunRepository, pipelineEventEmitter, playbookRegistry);
	}

	// --- helpers ---

	private PipelineRun executingRun(String id, PdlcRole currentRole, Instant startedAt) {
		PipelineRun run = new PipelineRun();
		run.setId(id);
		run.setSparkId("spark-" + id);
		run.setUserId("user-001");
		run.setStatus(PipelineStatus.EXECUTING);
		run.setCurrentRole(currentRole);
		run.setStartedAt(startedAt);
		run.setPlaybook(PLAYBOOK_NAME);
		return run;
	}

	private PlaybookConfig singleStagePlaybook(PdlcRole role, Duration timeout) {
		PlaybookStage stage = new PlaybookStage(role, true, List.of(), List.of(), timeout);
		return new PlaybookConfig(
				PLAYBOOK_NAME, "Bug Fix", "desc",
				io.strategiz.social.data.entity.PipelineTier.PLAYBOOK,
				List.of(stage),
				java.util.Map.of(),
				java.util.Map.of(),
				true);
	}

	// --- tests ---

	@Test
	void checkForTimedOutRoles_emitsReworkEscalatedWhenRoleExceedsTimeout() {
		// IMPLEMENTER timeout is 45 min; role started 50 minutes ago
		Duration timeout = Duration.ofMinutes(45);
		Instant startedAt = Instant.now().minus(Duration.ofMinutes(50));
		PipelineRun run = executingRun("run-001", PdlcRole.IMPLEMENTER, startedAt);

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));
		when(playbookRegistry.getPlaybook(PLAYBOOK_NAME))
				.thenReturn(Optional.of(singleStagePlaybook(PdlcRole.IMPLEMENTER, timeout)));

		watchdog.checkForTimedOutRoles();

		verify(pipelineEventEmitter).emitEvent(
				eq(run),
				eq(PipelineEventType.REWORK_ESCALATED),
				eq(PdlcRole.IMPLEMENTER),
				any());
	}

	@Test
	void checkForTimedOutRoles_doesNotEmitWhenRoleIsWithinTimeout() {
		// RESEARCHER timeout is 15 min; role started 10 minutes ago
		Duration timeout = Duration.ofMinutes(15);
		Instant startedAt = Instant.now().minus(Duration.ofMinutes(10));
		PipelineRun run = executingRun("run-002", PdlcRole.RESEARCHER, startedAt);

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));
		when(playbookRegistry.getPlaybook(PLAYBOOK_NAME))
				.thenReturn(Optional.of(singleStagePlaybook(PdlcRole.RESEARCHER, timeout)));

		watchdog.checkForTimedOutRoles();

		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void checkForTimedOutRoles_handlesUnknownPlaybookGracefully() {
		PipelineRun run = executingRun("run-003", PdlcRole.ARCHITECT, Instant.now().minus(Duration.ofHours(2)));
		run.setPlaybook("NONEXISTENT_PLAYBOOK");

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));
		when(playbookRegistry.getPlaybook("NONEXISTENT_PLAYBOOK")).thenReturn(Optional.empty());

		// Should not throw; should silently skip
		watchdog.checkForTimedOutRoles();

		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void checkForTimedOutRoles_handlesRunWithNoCurrentRoleGracefully() {
		PipelineRun run = new PipelineRun();
		run.setId("run-004");
		run.setStatus(PipelineStatus.EXECUTING);
		run.setPlaybook(PLAYBOOK_NAME);
		// currentRole intentionally left null

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));

		watchdog.checkForTimedOutRoles();

		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void checkForTimedOutRoles_handlesRunWithNullPlaybookGracefully() {
		PipelineRun run = new PipelineRun();
		run.setId("run-005");
		run.setStatus(PipelineStatus.EXECUTING);
		run.setCurrentRole(PdlcRole.PM);
		// playbook intentionally left null

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));

		watchdog.checkForTimedOutRoles();

		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void checkForTimedOutRoles_handlesRunWithNullStartedAtGracefully() {
		PipelineRun run = new PipelineRun();
		run.setId("run-006");
		run.setStatus(PipelineStatus.EXECUTING);
		run.setCurrentRole(PdlcRole.TESTER);
		run.setPlaybook(PLAYBOOK_NAME);
		// startedAt intentionally left null

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));
		when(playbookRegistry.getPlaybook(PLAYBOOK_NAME))
				.thenReturn(Optional.of(singleStagePlaybook(PdlcRole.TESTER, Duration.ofMinutes(30))));

		watchdog.checkForTimedOutRoles();

		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void checkForTimedOutRoles_doesNothingWhenNoExecutingRuns() {
		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of());

		watchdog.checkForTimedOutRoles();

		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void checkForTimedOutRoles_exactlyAtTimeoutBoundaryDoesNotTrigger() {
		// Role started just before the timeout boundary — should not trigger (elapsed < timeout)
		// Add 5 seconds of slack to avoid timing-sensitive failures from test execution delay
		Duration timeout = Duration.ofMinutes(10);
		Instant startedAt = Instant.now().minus(timeout).plusSeconds(5);
		PipelineRun run = executingRun("run-007", PdlcRole.PM, startedAt);

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));
		when(playbookRegistry.getPlaybook(PLAYBOOK_NAME))
				.thenReturn(Optional.of(singleStagePlaybook(PdlcRole.PM, timeout)));

		watchdog.checkForTimedOutRoles();

		// At exact boundary (elapsed == timeout), compareTo returns 0, which is not > 0
		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void checkForTimedOutRoles_onlyTimedOutRunAmongMultipleTriggersEvent() {
		Duration timeout = Duration.ofMinutes(15);

		// This run has been executing for 20 minutes — timed out
		PipelineRun timedOut = executingRun("run-008", PdlcRole.REVIEWER, Instant.now().minus(Duration.ofMinutes(20)));

		// This run has been executing for 5 minutes — within timeout
		PipelineRun withinTime = executingRun("run-009", PdlcRole.REVIEWER, Instant.now().minus(Duration.ofMinutes(5)));

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING))
				.thenReturn(List.of(timedOut, withinTime));
		when(playbookRegistry.getPlaybook(PLAYBOOK_NAME))
				.thenReturn(Optional.of(singleStagePlaybook(PdlcRole.REVIEWER, timeout)));

		watchdog.checkForTimedOutRoles();

		// Only the timed-out run should emit an event
		verify(pipelineEventEmitter).emitEvent(
				eq(timedOut), eq(PipelineEventType.REWORK_ESCALATED), eq(PdlcRole.REVIEWER), any());
		verify(pipelineEventEmitter, never()).emitEvent(
				eq(withinTime), any(), any(), any());
	}

}
