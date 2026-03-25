package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentCaptor.forClass;
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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineRecoveryJobTest {

	@Mock
	private PipelineRunRepository pipelineRunRepository;

	@Mock
	private PipelineEventEmitter pipelineEventEmitter;

	private PipelineRecoveryJob recoveryJob;

	@BeforeEach
	void setUp() {
		recoveryJob = new PipelineRecoveryJob(pipelineRunRepository, pipelineEventEmitter);
	}

	// --- helpers ---

	private PipelineRun executingRun(String id, PdlcRole currentRole) {
		PipelineRun run = new PipelineRun();
		run.setId(id);
		run.setSparkId("spark-" + id);
		run.setUserId("user-001");
		run.setStatus(PipelineStatus.EXECUTING);
		run.setCurrentRole(currentRole);
		return run;
	}

	// --- tests ---

	@Test
	void recoverInterruptedPipelines_claimsUnclaimedExecutingRun() {
		PipelineRun run = executingRun("run-001", PdlcRole.IMPLEMENTER);
		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));

		recoveryJob.recoverInterruptedPipelines();

		ArgumentCaptor<PipelineRun> savedCaptor = forClass(PipelineRun.class);
		verify(pipelineRunRepository).save(savedCaptor.capture());

		PipelineRun saved = savedCaptor.getValue();
		assertNotNull(saved.getClaimedBy(), "claimedBy should be set after claim");
		assertNotNull(saved.getClaimedAt(), "claimedAt should be set after claim");
	}

	@Test
	void recoverInterruptedPipelines_emitsPipelineResumedEvent() {
		PipelineRun run = executingRun("run-002", PdlcRole.ARCHITECT);
		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));

		recoveryJob.recoverInterruptedPipelines();

		verify(pipelineEventEmitter).emitEvent(
				eq(run),
				eq(PipelineEventType.PIPELINE_RESUMED),
				eq(PdlcRole.ARCHITECT),
				any());
	}

	@Test
	void recoverInterruptedPipelines_skipsRunWithFreshClaim() {
		PipelineRun run = executingRun("run-003", PdlcRole.TESTER);
		// Claim is fresh (2 minutes old — well within 30-minute threshold)
		run.setClaimedBy("other-instance-id");
		run.setClaimedAt(Instant.now().minusSeconds(120));
		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));

		recoveryJob.recoverInterruptedPipelines();

		verify(pipelineRunRepository, never()).save(any());
		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void recoverInterruptedPipelines_reclaimsStaleClaimedRun() {
		PipelineRun run = executingRun("run-004", PdlcRole.REVIEWER);
		// Claim is stale (45 minutes old — exceeds 30-minute threshold)
		run.setClaimedBy("old-instance-id");
		run.setClaimedAt(Instant.now().minusSeconds(60 * 45));
		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));

		recoveryJob.recoverInterruptedPipelines();

		ArgumentCaptor<PipelineRun> savedCaptor = forClass(PipelineRun.class);
		verify(pipelineRunRepository).save(savedCaptor.capture());

		PipelineRun saved = savedCaptor.getValue();
		// Claimed by should have changed to the new instance
		assertNotNull(saved.getClaimedBy());
		// The new claimedAt should be recent
		assertNotNull(saved.getClaimedAt());

		verify(pipelineEventEmitter).emitEvent(
				eq(run),
				eq(PipelineEventType.PIPELINE_RESUMED),
				eq(PdlcRole.REVIEWER),
				any());
	}

	@Test
	void recoverInterruptedPipelines_doesNothingWhenNoExecutingRuns() {
		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of());

		recoveryJob.recoverInterruptedPipelines();

		verify(pipelineRunRepository, never()).save(any());
		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void recoverInterruptedPipelines_claimsExactlyAtBoundary() {
		PipelineRun run = executingRun("run-005", PdlcRole.PM);
		// Claim is just over 30 minutes old (stale)
		run.setClaimedBy("boundary-instance-id");
		run.setClaimedAt(Instant.now().minus(PipelineRecoveryJob.CLAIM_STALENESS_THRESHOLD).minusSeconds(1));
		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING)).thenReturn(List.of(run));

		recoveryJob.recoverInterruptedPipelines();

		verify(pipelineRunRepository).save(any(PipelineRun.class));
		verify(pipelineEventEmitter).emitEvent(
				eq(run),
				eq(PipelineEventType.PIPELINE_RESUMED),
				eq(PdlcRole.PM),
				any());
	}

	@Test
	void recoverInterruptedPipelines_claimedByDifferentInstanceCountsAsStaleAfterThreshold() {
		PipelineRun stale = executingRun("run-006", PdlcRole.DEVOPS);
		stale.setClaimedBy("instance-A");
		stale.setClaimedAt(Instant.now().minusSeconds(60 * 31)); // 31 min old

		PipelineRun fresh = executingRun("run-007", PdlcRole.RESEARCHER);
		fresh.setClaimedBy("instance-B");
		fresh.setClaimedAt(Instant.now().minusSeconds(60 * 5)); // 5 min old

		when(pipelineRunRepository.findByStatus(PipelineStatus.EXECUTING))
				.thenReturn(List.of(stale, fresh));

		recoveryJob.recoverInterruptedPipelines();

		// Only the stale run should be saved and have an event emitted
		verify(pipelineRunRepository).save(eq(stale));
		verify(pipelineEventEmitter).emitEvent(
				eq(stale), eq(PipelineEventType.PIPELINE_RESUMED), eq(PdlcRole.DEVOPS), any());

		// The fresh run should be untouched
		verify(pipelineRunRepository, never()).save(eq(fresh));
	}

}
