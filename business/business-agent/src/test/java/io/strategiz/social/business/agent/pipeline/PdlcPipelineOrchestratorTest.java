package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.business.agent.pipeline.PdlcRoleExecutor.RoleExecutionResult;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.Checkpoint;
import io.strategiz.social.data.entity.CheckpointDecision;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.PipelineTier;
import io.strategiz.social.data.entity.RoleStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdlcPipelineOrchestratorTest {

	private static final String RUN_ID = "run-123";

	private static final String SPARK_ID = "spark-456";

	private static final String USER_ID = "user-789";

	private static final String CHILD_SPARK_ID = "child-spark-001";

	@Mock
	private PipelineStateManager pipelineStateManager;

	@Mock
	private PipelineEventEmitter pipelineEventEmitter;

	@Mock
	private PipelineArtifactService pipelineArtifactService;

	@Mock
	private SparkService sparkService;

	@Mock
	private PlaybookRegistry playbookRegistry;

	@Mock
	private PdlcRoleExecutor roleExecutor;

	@Mock
	private CheckpointService checkpointService;

	@Mock
	private ReworkTracker reworkTracker;

	private PdlcPipelineOrchestrator orchestrator;

	@BeforeEach
	void setUp() {
		orchestrator = new PdlcPipelineOrchestrator(pipelineStateManager, pipelineEventEmitter,
				pipelineArtifactService, sparkService, playbookRegistry, roleExecutor,
				checkpointService, reworkTracker);

		// Default: checkpoint gates auto-approve so tests can focus on pipeline flow
		Checkpoint autoApproved = new Checkpoint();
		autoApproved.setId("auto-checkpoint");
		autoApproved.setUserDecision(CheckpointDecision.APPROVED);
		when(checkpointService.createPipelineCheckpoint(any(), any(), any(), anyString(), anyString(), any()))
				.thenReturn(autoApproved);
		// Empty means "no pending checkpoint" = resolved immediately
		when(checkpointService.getPendingCheckpoint(anyString())).thenReturn(Optional.empty());
		when(checkpointService.getCheckpoint("auto-checkpoint")).thenReturn(Optional.of(autoApproved));
	}

	@Test
	void executePipeline_executesAllRolesInOrder() {
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER));
		PlaybookConfig playbook = createBugFixPlaybook();

		when(pipelineStateManager.getRun(RUN_ID)).thenReturn(Optional.of(run));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(playbook));
		when(sparkService.createChildSpark(anyString(), any(PdlcRole.class), anyString()))
				.thenReturn(CHILD_SPARK_ID);
		when(roleExecutor.execute(any(), any(), anyString()))
				.thenReturn(RoleExecutionResult.success(500L, new BigDecimal("0.01"), 100L, "stub-model", null));

		orchestrator.executePipeline(RUN_ID);

		// Verify child sparks created for each role
		verify(sparkService).createChildSpark(SPARK_ID, PdlcRole.RESEARCHER, USER_ID);
		verify(sparkService).createChildSpark(SPARK_ID, PdlcRole.IMPLEMENTER, USER_ID);
		verify(sparkService).createChildSpark(SPARK_ID, PdlcRole.REVIEWER, USER_ID);

		// Verify ROLE_STARTED events emitted for each role
		verify(pipelineEventEmitter).emitRoleStarted(eq(run), eq(PdlcRole.RESEARCHER), eq(CHILD_SPARK_ID));
		verify(pipelineEventEmitter).emitRoleStarted(eq(run), eq(PdlcRole.IMPLEMENTER), eq(CHILD_SPARK_ID));
		verify(pipelineEventEmitter).emitRoleStarted(eq(run), eq(PdlcRole.REVIEWER), eq(CHILD_SPARK_ID));

		// Verify ROLE_COMPLETED events emitted for each role
		verify(pipelineEventEmitter, times(3)).emitRoleCompleted(
				eq(run), any(PdlcRole.class), eq(500L), eq(new BigDecimal("0.01")), eq(100L), eq("stub-model"));

		// Verify pipeline completion
		verify(pipelineStateManager).markCompleted(eq(RUN_ID), anyLong(), any(BigDecimal.class));
		verify(sparkService).markCloudCompleted(eq(SPARK_ID), anyLong(), eq("pdlc-pipeline"));
	}

	@Test
	void executePipeline_skipsNonActivatedRoles() {
		// Only RESEARCHER and IMPLEMENTER are activated — REVIEWER and TESTER should be skipped
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));
		PlaybookConfig playbook = createBugFixPlaybook();

		when(pipelineStateManager.getRun(RUN_ID)).thenReturn(Optional.of(run));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(playbook));
		when(sparkService.createChildSpark(anyString(), any(PdlcRole.class), anyString()))
				.thenReturn(CHILD_SPARK_ID);
		when(roleExecutor.execute(any(), any(), anyString()))
				.thenReturn(RoleExecutionResult.success(500L, new BigDecimal("0.01"), 100L, "stub-model", null));

		orchestrator.executePipeline(RUN_ID);

		// Should only create child sparks for activated roles
		verify(sparkService, times(2)).createChildSpark(anyString(), any(PdlcRole.class), anyString());
		verify(sparkService).createChildSpark(SPARK_ID, PdlcRole.RESEARCHER, USER_ID);
		verify(sparkService).createChildSpark(SPARK_ID, PdlcRole.IMPLEMENTER, USER_ID);

		// REVIEWER and TESTER should be skipped with ROLE_SKIPPED events
		verify(pipelineEventEmitter).emitEvent(eq(run), eq(PipelineEventType.ROLE_SKIPPED),
				eq(PdlcRole.REVIEWER), any());
		verify(pipelineEventEmitter).emitEvent(eq(run), eq(PipelineEventType.ROLE_SKIPPED),
				eq(PdlcRole.TESTER), any());
	}

	@Test
	void executePipeline_handlesParallelGroups() {
		// Full PDLC with TESTER and SECURITY_ANALYST in parallel
		List<PdlcRole> activated = List.of(
				PdlcRole.PM, PdlcRole.RESEARCHER, PdlcRole.ARCHITECT, PdlcRole.PLANNER,
				PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER, PdlcRole.SECURITY_ANALYST,
				PdlcRole.TECHNICAL_WRITER, PdlcRole.DEVOPS);
		PipelineRun run = createTestRun(activated);
		run.setPlaybook("FULL_PDLC");
		run.setPipelineTier(PipelineTier.FULL_PDLC);

		PlaybookRegistry realRegistry = new PlaybookRegistry();
		realRegistry.registerDefaults();
		PlaybookConfig fullPdlc = realRegistry.getPlaybook("FULL_PDLC").orElseThrow();

		when(pipelineStateManager.getRun(RUN_ID)).thenReturn(Optional.of(run));
		when(playbookRegistry.getPlaybook("FULL_PDLC")).thenReturn(Optional.of(fullPdlc));
		when(sparkService.createChildSpark(anyString(), any(PdlcRole.class), anyString()))
				.thenReturn(CHILD_SPARK_ID);
		when(roleExecutor.execute(any(), any(), anyString()))
				.thenReturn(RoleExecutionResult.success(500L, new BigDecimal("0.01"), 100L, "stub-model", null));

		orchestrator.executePipeline(RUN_ID);

		// Verify PARALLEL_ROLES_STARTED event was emitted (for tester+security_analyst group)
		verify(pipelineEventEmitter, atLeastOnce()).emitEvent(
				eq(run), eq(PipelineEventType.PARALLEL_ROLES_STARTED), eq(null), any());

		// All 10 roles should have been executed (DESIGNER is not activated so skipped)
		verify(sparkService, times(10)).createChildSpark(anyString(), any(PdlcRole.class), anyString());
	}

	@Test
	void executePipeline_failsGracefullyWhenRunNotFound() {
		when(pipelineStateManager.getRun(RUN_ID)).thenReturn(Optional.empty());

		orchestrator.executePipeline(RUN_ID);

		verify(pipelineStateManager, never()).markCompleted(anyString(), anyLong(), any());
		verify(pipelineStateManager, never()).markFailed(anyString(), anyString());
	}

	@Test
	void executePipeline_failsWhenPlaybookNotFound() {
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER));

		when(pipelineStateManager.getRun(RUN_ID)).thenReturn(Optional.of(run));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.empty());

		orchestrator.executePipeline(RUN_ID);

		verify(pipelineStateManager).markFailed(eq(RUN_ID), anyString());
		verify(sparkService).markCloudFailed(eq(SPARK_ID), anyString(), eq(0L), eq(null));
	}

	@Test
	void executePipeline_handlesRoleExecutorFailure() {
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));
		PlaybookConfig playbook = createBugFixPlaybook();

		when(pipelineStateManager.getRun(RUN_ID)).thenReturn(Optional.of(run));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(playbook));
		when(sparkService.createChildSpark(anyString(), any(PdlcRole.class), anyString()))
				.thenReturn(CHILD_SPARK_ID);
		when(roleExecutor.execute(any(), any(), anyString()))
				.thenThrow(new RuntimeException("AI engine unavailable"));

		orchestrator.executePipeline(RUN_ID);

		verify(pipelineStateManager).markFailed(eq(RUN_ID), anyString());
		verify(sparkService).markCloudFailed(eq(SPARK_ID), anyString(), eq(0L), eq(null));
	}

	@Test
	void executePipeline_aggregatesMetricsFromAllRoles() {
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));
		PlaybookConfig playbook = new PlaybookConfig(
				"BUG_FIX", "Bug Fix", "Fix a bug", PipelineTier.PLAYBOOK,
				List.of(
						new PlaybookStage(PdlcRole.RESEARCHER, true, List.of(), List.of(), Duration.ofMinutes(15)),
						new PlaybookStage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.RESEARCHER), List.of(), Duration.ofMinutes(45))
				),
				Map.of(), Map.of(), true);

		when(pipelineStateManager.getRun(RUN_ID)).thenReturn(Optional.of(run));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(playbook));
		when(sparkService.createChildSpark(anyString(), any(PdlcRole.class), anyString()))
				.thenReturn(CHILD_SPARK_ID);

		// Return different metrics for each role
		when(roleExecutor.execute(any(), eq(PdlcRole.RESEARCHER), anyString()))
				.thenReturn(RoleExecutionResult.success(1000L, new BigDecimal("0.05"), 200L, "model-a", null));
		when(roleExecutor.execute(any(), eq(PdlcRole.IMPLEMENTER), anyString()))
				.thenReturn(RoleExecutionResult.success(3000L, new BigDecimal("0.15"), 500L, "model-b", null));

		orchestrator.executePipeline(RUN_ID);

		// Verify aggregated metrics passed to markCompleted: 1000+3000=4000 tokens, 0.05+0.15=0.20 cost
		ArgumentCaptor<Long> tokensCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
		verify(pipelineStateManager).markCompleted(eq(RUN_ID), tokensCaptor.capture(), costCaptor.capture());

		assertEquals(4000L, tokensCaptor.getValue());
		assertEquals(new BigDecimal("0.20"), costCaptor.getValue());
	}

	@Test
	void shouldSkipRole_returnsTrueWhenRoleNotInActivatedList() {
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));

		assertTrue(orchestrator.shouldSkipRole(PdlcRole.REVIEWER, run));
		assertTrue(orchestrator.shouldSkipRole(PdlcRole.TESTER, run));
	}

	@Test
	void shouldSkipRole_returnsFalseWhenRoleIsActivated() {
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));

		assertFalse(orchestrator.shouldSkipRole(PdlcRole.RESEARCHER, run));
		assertFalse(orchestrator.shouldSkipRole(PdlcRole.IMPLEMENTER, run));
	}

	@Test
	void shouldSkipRole_returnsFalseWhenActivatedRolesIsEmpty() {
		PipelineRun run = createTestRun(List.of());

		assertFalse(orchestrator.shouldSkipRole(PdlcRole.RESEARCHER, run));
	}

	@Test
	void getParallelPeers_returnsPeersFromPlaybook() {
		PlaybookConfig playbook = new PlaybookConfig(
				"TEST", "Test", "Test playbook", PipelineTier.FULL_PDLC,
				List.of(),
				Map.of(PdlcRole.TESTER, List.of(PdlcRole.SECURITY_ANALYST)),
				Map.of(), true);

		List<PdlcRole> peers = orchestrator.getParallelPeers(PdlcRole.TESTER, playbook);

		assertEquals(1, peers.size());
		assertEquals(PdlcRole.SECURITY_ANALYST, peers.get(0));
	}

	@Test
	void getParallelPeers_returnsEmptyListWhenNoPeers() {
		PlaybookConfig playbook = new PlaybookConfig(
				"TEST", "Test", "Test playbook", PipelineTier.PLAYBOOK,
				List.of(), Map.of(), Map.of(), true);

		List<PdlcRole> peers = orchestrator.getParallelPeers(PdlcRole.IMPLEMENTER, playbook);

		assertTrue(peers.isEmpty());
	}

	@Test
	void isCheckpointBeforeRequired_returnsTrueWhenConfigured() {
		PlaybookConfig playbook = new PlaybookConfig(
				"TEST", "Test", "Test playbook", PipelineTier.FULL_PDLC,
				List.of(), Map.of(),
				Map.of(PdlcRole.DEVOPS, new PlaybookConfig.CheckpointRule(true, false, false)),
				true);

		assertTrue(orchestrator.isCheckpointBeforeRequired(PdlcRole.DEVOPS, playbook));
	}

	@Test
	void isCheckpointAfterRequired_returnsTrueWhenConfigured() {
		PlaybookConfig playbook = new PlaybookConfig(
				"TEST", "Test", "Test playbook", PipelineTier.FULL_PDLC,
				List.of(), Map.of(),
				Map.of(PdlcRole.PM, new PlaybookConfig.CheckpointRule(false, true, false)),
				true);

		assertTrue(orchestrator.isCheckpointAfterRequired(PdlcRole.PM, playbook));
	}

	@Test
	void aggregateMetrics_sumsTotalTokensAndCost() {
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));

		var researcherResult = new io.strategiz.social.data.entity.RoleResultSummary();
		researcherResult.setTokens(1500L);
		researcherResult.setCost(new BigDecimal("0.05"));
		run.getRoleResults().put(PdlcRole.RESEARCHER.name(), researcherResult);

		var implementerResult = new io.strategiz.social.data.entity.RoleResultSummary();
		implementerResult.setTokens(3500L);
		implementerResult.setCost(new BigDecimal("0.20"));
		run.getRoleResults().put(PdlcRole.IMPLEMENTER.name(), implementerResult);

		orchestrator.aggregateMetrics(run);

		assertEquals(5000L, run.getTotalTokens());
		assertEquals(new BigDecimal("0.25"), run.getTotalCost());
	}

	@Test
	void aggregateMetrics_handlesNullCosts() {
		PipelineRun run = createTestRun(List.of(PdlcRole.RESEARCHER));

		var result = new io.strategiz.social.data.entity.RoleResultSummary();
		result.setTokens(1000L);
		result.setCost(null);
		run.getRoleResults().put(PdlcRole.RESEARCHER.name(), result);

		orchestrator.aggregateMetrics(run);

		assertEquals(1000L, run.getTotalTokens());
		assertEquals(BigDecimal.ZERO, run.getTotalCost());
	}

	// --- Helpers ---

	private PipelineRun createTestRun(List<PdlcRole> activatedRoles) {
		PipelineRun run = new PipelineRun();
		run.setId(RUN_ID);
		run.setSparkId(SPARK_ID);
		run.setUserId(USER_ID);
		run.setPlaybook("BUG_FIX");
		run.setPipelineTier(PipelineTier.PLAYBOOK);
		run.setActivatedRoles(new ArrayList<>(activatedRoles));
		run.setStatus(PipelineStatus.EXECUTING);
		return run;
	}

	private PlaybookConfig createBugFixPlaybook() {
		List<PlaybookStage> stages = List.of(
				new PlaybookStage(PdlcRole.RESEARCHER, true, List.of(), List.of(), Duration.ofMinutes(15)),
				new PlaybookStage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.RESEARCHER),
						List.of(PdlcRole.RESEARCHER), Duration.ofMinutes(45)),
				new PlaybookStage(PdlcRole.REVIEWER, true, List.of(PdlcRole.IMPLEMENTER),
						List.of(PdlcRole.IMPLEMENTER), Duration.ofMinutes(15)),
				new PlaybookStage(PdlcRole.TESTER, true, List.of(PdlcRole.REVIEWER),
						List.of(PdlcRole.IMPLEMENTER), Duration.ofMinutes(30))
		);

		return new PlaybookConfig(
				"BUG_FIX", "Bug Fix", "Fix a bug", PipelineTier.PLAYBOOK,
				stages, Map.of(), Map.of(), true);
	}

}
