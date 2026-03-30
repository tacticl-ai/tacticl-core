package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import io.strategiz.social.data.entity.PipelineTier;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineStateManagerTest {

	private static final String SPARK_ID = "spark-123";

	private static final String USER_ID = "user-456";

	private static final String RUN_ID = "run-789";

	@Mock
	private PipelineRunRepository pipelineRunRepository;

	@Mock
	private PipelineEventEmitter pipelineEventEmitter;

	private PipelineStateManager stateManager;

	@BeforeEach
	void setUp() {
		stateManager = new PipelineStateManager(pipelineRunRepository, pipelineEventEmitter);
	}

	@Test
	void createRun_persistsRunWithCreatedStatus_doesNotEmitEvent() {
		PlaybookConfig playbook = new PlaybookConfig(
				"BUG_FIX", "Bug Fix", "Fix a bug", PipelineTier.PLAYBOOK,
				List.of(), Map.of(), Map.of(), true);
		PdlcClassification classification = new PdlcClassification(
				PipelineTier.PLAYBOOK, "BUG_FIX", 0.90,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER),
				List.of(), Map.of("scope", 2), "Bug fix classification");

		PipelineRun result = stateManager.createRun(SPARK_ID, USER_ID, playbook, classification);

		assertNotNull(result.getId());
		assertEquals(SPARK_ID, result.getSparkId());
		assertEquals(USER_ID, result.getUserId());
		assertEquals("BUG_FIX", result.getPlaybook());
		assertEquals(PipelineTier.PLAYBOOK, result.getPipelineTier());
		assertEquals(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER), result.getActivatedRoles());
		assertEquals(PipelineStatus.CREATED, result.getStatus());
		assertNotNull(result.getStartedAt());
		assertNotNull(result.getClassificationResult());
		assertEquals("PLAYBOOK", result.getClassificationResult().get("tier"));
		assertEquals(0.90, result.getClassificationResult().get("confidence"));

		// Verify persistence
		ArgumentCaptor<PipelineRun> captor = forClass(PipelineRun.class);
		verify(pipelineRunRepository).save(captor.capture());
		assertEquals(result.getId(), captor.getValue().getId());

		// createRun must NOT emit any event — the orchestrator calls markExecuting() for that
		verify(pipelineEventEmitter, never()).emitPipelineStarted(any());
		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void markExecuting_setsStatusAndEmitsPipelineStarted() {
		PipelineRun run = createTestRun();
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		stateManager.markExecuting(RUN_ID);

		assertEquals(PipelineStatus.EXECUTING, run.getStatus());
		verify(pipelineRunRepository).save(run);
		// emitPipelineStarted must be called AFTER the save so WebSocket sees committed state
		verify(pipelineEventEmitter).emitPipelineStarted(run);
	}

	@Test
	void updateRoleStatus_updatesRoleResultInRun() {
		PipelineRun run = createTestRun();
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		stateManager.updateRoleStatus(RUN_ID, PdlcRole.IMPLEMENTER, RoleStatus.EXECUTING);

		assertNotNull(run.getRoleResults().get(PdlcRole.IMPLEMENTER.name()));
		assertEquals(RoleStatus.EXECUTING, run.getRoleResults().get(PdlcRole.IMPLEMENTER.name()).getStatus());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void updateCurrentRole_setsCurrentRoleOnRun() {
		PipelineRun run = createTestRun();
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		stateManager.updateCurrentRole(RUN_ID, PdlcRole.ARCHITECT);

		assertEquals(PdlcRole.ARCHITECT, run.getCurrentRole());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void markCompleted_setsStatusCompletedAtAndMetrics_thenEmitsEvent() {
		PipelineRun run = createTestRun();
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		stateManager.markCompleted(RUN_ID, 5000L, new BigDecimal("1.50"));

		// StateManager must set all fields before the single save
		assertEquals(PipelineStatus.COMPLETED, run.getStatus());
		assertNotNull(run.getCompletedAt());
		assertNull(run.getCurrentRole());
		assertEquals(5000L, run.getTotalTokens());
		assertEquals(new BigDecimal("1.50"), run.getTotalCost());

		// Save happens exactly once, then the event is emitted
		verify(pipelineRunRepository).save(run);
		verify(pipelineEventEmitter).emitEvent(
				eq(run), eq(PipelineEventType.PIPELINE_COMPLETED), eq(null), any());
	}

	@Test
	void markFailed_setsStatusFailedAndSaves_thenEmitsEvent() {
		PipelineRun run = createTestRun();
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		stateManager.markFailed(RUN_ID, "Something went wrong");

		// StateManager must set status FAILED before the single save
		assertEquals(PipelineStatus.FAILED, run.getStatus());
		verify(pipelineRunRepository).save(run);

		verify(pipelineEventEmitter).emitEvent(
				eq(run), eq(PipelineEventType.PIPELINE_FAILED), eq(null), any());
	}

	@Test
	void incrementRework_incrementsCountAndPersists() {
		PipelineRun run = createTestRun();
		assertEquals(0, run.getReworkCount());
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		stateManager.incrementRework(RUN_ID);

		assertEquals(1, run.getReworkCount());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void incrementRework_accumulatesAcrossMultipleCalls() {
		PipelineRun run = createTestRun();
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		stateManager.incrementRework(RUN_ID);
		stateManager.incrementRework(RUN_ID);
		stateManager.incrementRework(RUN_ID);

		assertEquals(3, run.getReworkCount());
	}

	@Test
	void getRun_delegatesToRepository() {
		PipelineRun run = createTestRun();
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		Optional<PipelineRun> result = stateManager.getRun(RUN_ID);

		assertTrue(result.isPresent());
		assertEquals(RUN_ID, result.get().getId());
	}

	@Test
	void getRun_returnsEmptyWhenNotFound() {
		when(pipelineRunRepository.findById("nonexistent")).thenReturn(Optional.empty());

		Optional<PipelineRun> result = stateManager.getRun("nonexistent");

		assertTrue(result.isEmpty());
	}

	// --- updateSkipRoles ---

	@Test
	void updateSkipRoles_pendingRole_marksSkippedAndEmitsEvent() {
		PipelineRun run = createTestRun();
		run.setStatus(PipelineStatus.EXECUTING);
		// IMPLEMENTER has not started yet — no roleResult entry
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		PipelineRun result = stateManager.updateSkipRoles(SPARK_ID, List.of(PdlcRole.IMPLEMENTER), USER_ID);

		assertTrue(result.getSkippedRequiredRoles().contains("IMPLEMENTER"));
		assertEquals(RoleStatus.SKIPPED, result.getRoleResults().get("IMPLEMENTER").getStatus());
		verify(pipelineRunRepository).save(run);
		verify(pipelineEventEmitter).emitEvent(eq(run), eq(PipelineEventType.ROLE_SKIPPED),
				eq(PdlcRole.IMPLEMENTER), any());
	}

	@Test
	void updateSkipRoles_completedRole_isFiltered() {
		PipelineRun run = createTestRun();
		run.setStatus(PipelineStatus.EXECUTING);
		// RESEARCHER already completed
		RoleResultSummary completedResult = new RoleResultSummary();
		completedResult.setStatus(RoleStatus.COMPLETED);
		run.getRoleResults().put("RESEARCHER", completedResult);
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		PipelineRun result = stateManager.updateSkipRoles(SPARK_ID,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER), USER_ID);

		// RESEARCHER should not be skipped (already completed), but IMPLEMENTER should be
		assertTrue(result.getSkippedRequiredRoles().contains("IMPLEMENTER"));
		assertEquals(1, result.getSkippedRequiredRoles().size());
		assertEquals(RoleStatus.COMPLETED, result.getRoleResults().get("RESEARCHER").getStatus());
		assertEquals(RoleStatus.SKIPPED, result.getRoleResults().get("IMPLEMENTER").getStatus());
	}

	@Test
	void updateSkipRoles_nonExecutingPipeline_throwsIllegalState() {
		PipelineRun run = createTestRun();
		run.setStatus(PipelineStatus.COMPLETED);
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		assertThrows(IllegalStateException.class,
				() -> stateManager.updateSkipRoles(SPARK_ID, List.of(PdlcRole.IMPLEMENTER), USER_ID));
	}

	@Test
	void updateSkipRoles_noPipelineRun_throwsIllegalArgument() {
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class,
				() -> stateManager.updateSkipRoles(SPARK_ID, List.of(PdlcRole.IMPLEMENTER), USER_ID));
	}

	@Test
	void updateSkipRoles_wrongUser_throwsSecurityException() {
		PipelineRun run = createTestRun();
		run.setStatus(PipelineStatus.EXECUTING);
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		assertThrows(SecurityException.class,
				() -> stateManager.updateSkipRoles(SPARK_ID, List.of(PdlcRole.IMPLEMENTER), "other-user"));
	}

	@Test
	void updateSkipRoles_pendingRoleInResults_marksSkipped() {
		PipelineRun run = createTestRun();
		run.setStatus(PipelineStatus.EXECUTING);
		// IMPLEMENTER is explicitly PENDING in roleResults
		RoleResultSummary pendingResult = new RoleResultSummary();
		pendingResult.setStatus(RoleStatus.PENDING);
		run.getRoleResults().put("IMPLEMENTER", pendingResult);
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		PipelineRun result = stateManager.updateSkipRoles(SPARK_ID, List.of(PdlcRole.IMPLEMENTER), USER_ID);

		assertTrue(result.getSkippedRequiredRoles().contains("IMPLEMENTER"));
		assertEquals(RoleStatus.SKIPPED, result.getRoleResults().get("IMPLEMENTER").getStatus());
		verify(pipelineEventEmitter).emitEvent(eq(run), eq(PipelineEventType.ROLE_SKIPPED),
				eq(PdlcRole.IMPLEMENTER), any());
	}

	private PipelineRun createTestRun() {
		PipelineRun run = new PipelineRun();
		run.setId(RUN_ID);
		run.setSparkId(SPARK_ID);
		run.setUserId(USER_ID);
		run.setPlaybook("BUG_FIX");
		run.setPipelineTier(PipelineTier.PLAYBOOK);
		run.setActivatedRoles(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));
		return run;
	}

}
