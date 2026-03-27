package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.business.agent.ai.AiRoleOverrideService;
import io.strategiz.social.business.agent.pipeline.PdlcRoleExecutor.RoleExecutionResult;
import io.strategiz.social.business.agent.pipeline.role.PdlcRoleRegistry;
import io.strategiz.social.business.agent.pipeline.role.PdlcRoleSkill;
import io.strategiz.social.business.agent.pipeline.role.RoleContext;
import io.strategiz.social.business.agent.pipeline.role.RoleMetrics;
import io.strategiz.social.business.agent.pipeline.role.RoleOutcome;
import io.strategiz.social.business.agent.pipeline.role.RoleResult;
import io.strategiz.social.business.agent.pipeline.role.SuccessCriteria;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.AiRoleOverride;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.PipelineTier;
import io.strategiz.social.data.entity.Spark;
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
class RealPdlcRoleExecutorTest {

	private static final String RUN_ID = "run-123";

	private static final String SPARK_ID = "spark-456";

	private static final String USER_ID = "user-789";

	private static final String CHILD_SPARK_ID = "child-spark-001";

	@Mock
	private PdlcRoleRegistry roleRegistry;

	@Mock
	private PipelineArtifactService artifactService;

	@Mock
	private KnowledgeBaseService knowledgeBaseService;

	@Mock
	private SparkService sparkService;

	@Mock
	private PlaybookRegistry playbookRegistry;

	@Mock
	private AiRoleOverrideService roleOverrideService;

	@Mock
	private PdlcRoleSkill roleSkill;

	private RealPdlcRoleExecutor executor;

	@BeforeEach
	void setUp() {
		executor = new RealPdlcRoleExecutor(roleRegistry, artifactService, knowledgeBaseService,
				sparkService, playbookRegistry, roleOverrideService);
		// Default: no role override present
		when(roleOverrideService.getOverride(anyString())).thenReturn(Optional.empty());
	}

	@Test
	void execute_callsRoleRegistryAndReturnsSuccess() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.IMPLEMENTER);

		RoleMetrics metrics = new RoleMetrics(1500L, new BigDecimal("0.12"), 3000L, "claude-sonnet-4", "anthropic");
		RoleResult roleResult = RoleResult.completed(
				List.of(Map.of("content", "Implementation code here")),
				"Implemented the feature",
				metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Produces code", "implementation"));

		when(artifactService.store(anyString(), any(PdlcRole.class), anyString(), anyString(), any()))
				.thenReturn("artifact-001");

		RoleExecutionResult result = executor.execute(run, PdlcRole.IMPLEMENTER, CHILD_SPARK_ID);

		assertFalse(result.rejected());
		assertEquals(1500L, result.tokens());
		assertEquals(new BigDecimal("0.12"), result.cost());
		assertEquals(3000L, result.durationMs());
		assertEquals("claude-sonnet-4", result.model());
		assertEquals("artifact-001", result.artifactId());

		verify(roleRegistry).getRole(PdlcRole.IMPLEMENTER);
		verify(roleSkill).execute(any(RoleContext.class));
	}

	@Test
	void execute_buildsContextWithUpstreamArtifacts() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.REVIEWER);

		PipelineArtifact upstreamArtifact = new PipelineArtifact();
		upstreamArtifact.setRole(PdlcRole.IMPLEMENTER);
		upstreamArtifact.setContent(Map.of("content", "Code output"));

		when(artifactService.getUpstreamArtifacts(eq(RUN_ID), eq(PdlcRole.REVIEWER), any()))
				.thenReturn(Map.of(PdlcRole.IMPLEMENTER, upstreamArtifact));

		RoleMetrics metrics = new RoleMetrics(800L, new BigDecimal("0.06"), 1500L, "claude-haiku-4", "anthropic");
		when(roleSkill.execute(any(RoleContext.class)))
				.thenReturn(RoleResult.completed(List.of(), "Looks good", metrics));
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Review complete", "review"));

		executor.execute(run, PdlcRole.REVIEWER, CHILD_SPARK_ID);

		ArgumentCaptor<RoleContext> ctxCaptor = ArgumentCaptor.forClass(RoleContext.class);
		verify(roleSkill).execute(ctxCaptor.capture());

		RoleContext capturedCtx = ctxCaptor.getValue();
		assertEquals(RUN_ID, capturedCtx.pipelineRunId());
		assertEquals(SPARK_ID, capturedCtx.parentSparkId());
		assertEquals(CHILD_SPARK_ID, capturedCtx.childSparkId());
		assertEquals(USER_ID, capturedCtx.userId());
		assertNotNull(capturedCtx.upstreamArtifacts());
		assertTrue(capturedCtx.upstreamArtifacts().containsKey(PdlcRole.IMPLEMENTER));
	}

	@Test
	void execute_storesArtifactOnCompletion() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.PM);

		Map<String, Object> artifactContent = Map.of("content", "Requirements document");
		RoleMetrics metrics = new RoleMetrics(2000L, new BigDecimal("0.15"), 5000L, "claude-sonnet-4", "anthropic");
		RoleResult roleResult = RoleResult.completed(List.of(artifactContent), "Requirements gathered", metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Produces requirements", "requirements"));

		when(artifactService.store(anyString(), any(PdlcRole.class), anyString(), anyString(), any()))
				.thenReturn("artifact-pm-001");

		RoleExecutionResult result = executor.execute(run, PdlcRole.PM, CHILD_SPARK_ID);

		verify(artifactService).store(RUN_ID, PdlcRole.PM, CHILD_SPARK_ID, "requirements", artifactContent);
		assertEquals("artifact-pm-001", result.artifactId());
	}

	@Test
	void execute_doesNotStoreArtifactWhenNoArtifactsProduced() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.REVIEWER);

		RoleMetrics metrics = new RoleMetrics(500L, new BigDecimal("0.04"), 1000L, "claude-haiku-4", "anthropic");
		RoleResult roleResult = RoleResult.completed(List.of(), "Approved", metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Review complete", "review"));

		RoleExecutionResult result = executor.execute(run, PdlcRole.REVIEWER, CHILD_SPARK_ID);

		verify(artifactService, never()).store(anyString(), any(), anyString(), anyString(), any());
		assertNull(result.artifactId());
	}

	@Test
	void execute_returnsRejectionResult() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.REVIEWER);

		RoleMetrics metrics = new RoleMetrics(1200L, new BigDecimal("0.09"), 2500L, "claude-sonnet-4", "anthropic");
		RoleResult roleResult = RoleResult.rejected(
				"Missing error handling for edge cases",
				PdlcRole.IMPLEMENTER,
				metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);

		RoleExecutionResult result = executor.execute(run, PdlcRole.REVIEWER, CHILD_SPARK_ID);

		assertTrue(result.rejected());
		assertEquals("Missing error handling for edge cases", result.rejectionReason());
		assertEquals(PdlcRole.IMPLEMENTER, result.rejectionTarget());
		assertEquals(1200L, result.tokens());
		assertEquals(new BigDecimal("0.09"), result.cost());
		assertNull(result.artifactId());
	}

	@Test
	void execute_throwsWhenNoSkillRegisteredForRole() {
		PipelineRun run = createTestRun();

		Spark spark = new Spark();
		spark.setDescription("Test request");
		when(sparkService.getSparkInternal(SPARK_ID)).thenReturn(Optional.of(spark));
		when(roleRegistry.getRole(PdlcRole.RETRO_ANALYST)).thenReturn(Optional.empty());

		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> executor.execute(run, PdlcRole.RETRO_ANALYST, CHILD_SPARK_ID));

		assertTrue(ex.getMessage().contains("No skill registered for role: RETRO_ANALYST"));
	}

	@Test
	void execute_passesReworkFeedbackToContext() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.IMPLEMENTER);

		RoleMetrics metrics = new RoleMetrics(2000L, new BigDecimal("0.15"), 4000L, "claude-sonnet-4", "anthropic");
		RoleResult roleResult = RoleResult.completed(
				List.of(Map.of("content", "Fixed implementation")),
				"Addressed rework feedback",
				metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Produces code", "implementation"));

		when(artifactService.store(anyString(), any(PdlcRole.class), anyString(), anyString(), any()))
				.thenReturn("artifact-rework-001");

		String reworkFeedback = "Missing error handling for null inputs";
		RoleExecutionResult result = executor.execute(run, PdlcRole.IMPLEMENTER, CHILD_SPARK_ID,
				reworkFeedback, 1);

		ArgumentCaptor<RoleContext> ctxCaptor = ArgumentCaptor.forClass(RoleContext.class);
		verify(roleSkill).execute(ctxCaptor.capture());

		RoleContext capturedCtx = ctxCaptor.getValue();
		assertEquals(reworkFeedback, capturedCtx.reworkFeedback());
		assertEquals(1, capturedCtx.reworkIteration());
		assertTrue(capturedCtx.isRework());
	}

	@Test
	void execute_injectsKnowledgeContextIntoOriginalRequest() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.ARCHITECT);

		String knowledgeBlock = "## Role Knowledge\n\n- Design for failure.";
		when(knowledgeBaseService.buildKnowledgeContext(eq(PdlcRole.ARCHITECT), anyString()))
				.thenReturn(knowledgeBlock);

		RoleMetrics metrics = new RoleMetrics(1800L, new BigDecimal("0.14"), 3500L, "claude-sonnet-4", "anthropic");
		RoleResult roleResult = RoleResult.completed(
				List.of(Map.of("content", "Architecture design")),
				"Design complete",
				metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Produces design", "design"));

		when(artifactService.store(anyString(), any(PdlcRole.class), anyString(), anyString(), any()))
				.thenReturn("artifact-arch-001");

		executor.execute(run, PdlcRole.ARCHITECT, CHILD_SPARK_ID);

		ArgumentCaptor<RoleContext> ctxCaptor = ArgumentCaptor.forClass(RoleContext.class);
		verify(roleSkill).execute(ctxCaptor.capture());

		RoleContext capturedCtx = ctxCaptor.getValue();
		assertTrue(capturedCtx.originalRequest().contains("Build a new login feature"));
		assertTrue(capturedCtx.originalRequest().contains("Design for failure"));
	}

	@Test
	void execute_resolvesGitContextFromPipelineRun() {
		PipelineRun run = createTestRun();
		run.setGitContext(Map.of(
				"repoFullName", "acme/my-service",
				"baseBranch", "main",
				"workingBranch", "feature/login",
				"latestCommitSha", "abc123"));

		setupCommonMocks(PdlcRole.IMPLEMENTER);

		RoleMetrics metrics = new RoleMetrics(1000L, new BigDecimal("0.08"), 2000L, "claude-sonnet-4", "anthropic");
		RoleResult roleResult = RoleResult.completed(List.of(), "Done", metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Produces code", "implementation"));

		executor.execute(run, PdlcRole.IMPLEMENTER, CHILD_SPARK_ID);

		ArgumentCaptor<RoleContext> ctxCaptor = ArgumentCaptor.forClass(RoleContext.class);
		verify(roleSkill).execute(ctxCaptor.capture());

		RoleContext capturedCtx = ctxCaptor.getValue();
		assertNotNull(capturedCtx.gitContext());
		assertEquals("acme/my-service", capturedCtx.gitContext().repoFullName());
		assertEquals("main", capturedCtx.gitContext().baseBranch());
		assertEquals("feature/login", capturedCtx.gitContext().workingBranch());
		assertEquals("abc123", capturedCtx.gitContext().latestCommitSha());
	}

	@Test
	void execute_handlesFailedRoleResult() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.IMPLEMENTER);

		RoleMetrics metrics = new RoleMetrics(100L, BigDecimal.ZERO, 500L, null, "anthropic");
		RoleResult roleResult = RoleResult.failed("Engine timeout", metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);

		RoleExecutionResult result = executor.execute(run, PdlcRole.IMPLEMENTER, CHILD_SPARK_ID);

		assertFalse(result.rejected());
		assertEquals(100L, result.tokens());
		assertNull(result.artifactId());
		verify(artifactService, never()).store(anyString(), any(), anyString(), anyString(), any());
	}

	@Test
	void execute_passesRoleOverrideEngineAndModelToContext() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.IMPLEMENTER);

		AiRoleOverride override = new AiRoleOverride();
		override.setId("IMPLEMENTER");
		override.setRole("IMPLEMENTER");
		override.setEngineId("openai-agentic");
		override.setModel("gpt-4o");
		when(roleOverrideService.getOverride("IMPLEMENTER")).thenReturn(Optional.of(override));

		RoleMetrics metrics = new RoleMetrics(1500L, new BigDecimal("0.12"), 3000L, "gpt-4o", "openai-agentic");
		RoleResult roleResult = RoleResult.completed(
				List.of(Map.of("content", "Implementation with overridden engine")),
				"Implemented using overridden engine",
				metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Produces code", "implementation"));
		when(artifactService.store(anyString(), any(PdlcRole.class), anyString(), anyString(), any()))
				.thenReturn("artifact-override-001");

		executor.execute(run, PdlcRole.IMPLEMENTER, CHILD_SPARK_ID);

		ArgumentCaptor<RoleContext> ctxCaptor = ArgumentCaptor.forClass(RoleContext.class);
		verify(roleSkill).execute(ctxCaptor.capture());

		RoleContext capturedCtx = ctxCaptor.getValue();
		assertEquals("openai-agentic", capturedCtx.engineIdOverride(),
				"Engine override should be passed through to RoleContext");
		assertEquals("gpt-4o", capturedCtx.modelOverride(),
				"Model override should be passed through to RoleContext");
	}

	@Test
	void execute_passesNullOverrideFieldsWhenNoOverrideConfigured() {
		PipelineRun run = createTestRun();
		setupCommonMocks(PdlcRole.PM);

		// roleOverrideService already returns empty by default from setUp()

		RoleMetrics metrics = new RoleMetrics(500L, new BigDecimal("0.04"), 1000L, "claude-haiku-4", "anthropic");
		RoleResult roleResult = RoleResult.completed(List.of(), "Done", metrics);
		when(roleSkill.execute(any(RoleContext.class))).thenReturn(roleResult);
		when(roleSkill.getSuccessCriteria()).thenReturn(new SuccessCriteria("Requirements doc", "REQUIREMENTS"));

		executor.execute(run, PdlcRole.PM, CHILD_SPARK_ID);

		ArgumentCaptor<RoleContext> ctxCaptor = ArgumentCaptor.forClass(RoleContext.class);
		verify(roleSkill).execute(ctxCaptor.capture());

		RoleContext capturedCtx = ctxCaptor.getValue();
		assertNull(capturedCtx.engineIdOverride(), "No override should result in null engineIdOverride");
		assertNull(capturedCtx.modelOverride(), "No override should result in null modelOverride");
	}

	// --- Helpers ---

	private PipelineRun createTestRun() {
		PipelineRun run = new PipelineRun();
		run.setId(RUN_ID);
		run.setSparkId(SPARK_ID);
		run.setUserId(USER_ID);
		run.setPlaybook("BUG_FIX");
		run.setPipelineTier(PipelineTier.PLAYBOOK);
		run.setActivatedRoles(new ArrayList<>(List.of(
				PdlcRole.PM, PdlcRole.RESEARCHER, PdlcRole.ARCHITECT,
				PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER)));
		run.setStatus(PipelineStatus.EXECUTING);
		return run;
	}

	private void setupCommonMocks(PdlcRole role) {
		Spark spark = new Spark();
		spark.setDescription("Build a new login feature");
		when(sparkService.getSparkInternal(SPARK_ID)).thenReturn(Optional.of(spark));

		when(roleRegistry.getRole(role)).thenReturn(Optional.of(roleSkill));
		when(roleSkill.getRole()).thenReturn(role);

		PlaybookConfig playbook = new PlaybookConfig(
				"BUG_FIX", "Bug Fix", "Fix a bug", PipelineTier.PLAYBOOK,
				List.of(
						new PlaybookStage(PdlcRole.RESEARCHER, true, List.of(), List.of(), Duration.ofMinutes(15)),
						new PlaybookStage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.RESEARCHER),
								List.of(), Duration.ofMinutes(45)),
						new PlaybookStage(PdlcRole.REVIEWER, true, List.of(PdlcRole.IMPLEMENTER),
								List.of(), Duration.ofMinutes(15))
				),
				Map.of(), Map.of(), true);
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(playbook));

		// Default: no upstream artifacts, no knowledge context
		when(artifactService.getUpstreamArtifacts(eq(RUN_ID), eq(role), any()))
				.thenReturn(Map.of());
		when(knowledgeBaseService.buildKnowledgeContext(eq(role), anyString()))
				.thenReturn("");
	}

}
