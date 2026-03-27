package io.strategiz.social.service.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.framework.llmrouter.LlmRouter;
import io.strategiz.social.business.agent.pipeline.PdlcClassification;
import io.strategiz.social.business.agent.pipeline.PdlcClassifierService;
import io.strategiz.social.business.agent.pipeline.PdlcPipelineOrchestrator;
import io.strategiz.social.business.agent.pipeline.PipelineStateManager;
import io.strategiz.social.business.agent.pipeline.PlaybookConfig;
import io.strategiz.social.business.agent.pipeline.PlaybookRegistry;
import io.strategiz.social.business.agent.pipeline.PlaybookStage;
import io.strategiz.social.business.agent.service.DeviceRoutingService;
import io.strategiz.social.business.agent.service.SparkClassifierService;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.business.agent.service.TranscriptionService;
import io.strategiz.social.business.agent.service.UserConfigService;
import io.strategiz.social.business.agent.service.UserProvisioningService;
import io.strategiz.social.business.agent.service.CloudOrchestratorService;
import io.strategiz.social.data.entity.ExecutionPreference;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineTier;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.repository.ActionConfirmationRepository;
import io.strategiz.social.data.repository.AgentAuditLogRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import io.strategiz.social.service.agent.service.SetupActionDetector;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AgentControllerPipelineTest {

	private static final String USER_ID = "user-test-123";

	private static final String SPARK_ID = "spark-test-456";

	private static final String PIPELINE_RUN_ID = "run-test-789";

	// --- Mocks ---

	@Mock
	private CloudOrchestratorService voiceAgentService;

	@Mock
	private LlmRouter llmRouter;

	@Mock
	private AgentAuditLogRepository auditLogRepository;

	@Mock
	private ActionConfirmationRepository confirmationRepository;

	@Mock
	private SocialIntegrationRepository integrationRepository;

	@Mock
	private UserProvisioningService userProvisioningService;

	@Mock
	private TranscriptionService transcriptionService;

	@Mock
	private SparkService sparkService;

	@Mock
	private DeviceRoutingService deviceRoutingService;

	@Mock
	private SetupActionDetector setupActionDetector;

	@Mock
	private UserConfigService userConfigService;

	@Mock
	private SparkClassifierService sparkClassifierService;

	@Mock
	private PdlcClassifierService pdlcClassifierService;

	@Mock
	private PdlcPipelineOrchestrator pdlcPipelineOrchestrator;

	@Mock
	private PipelineStateManager pipelineStateManager;

	@Mock
	private PlaybookRegistry playbookRegistry;

	@Mock
	private AuthenticatedUser authenticatedUser;

	private AgentController controller;

	@BeforeEach
	void setUp() {
		controller = new AgentController(
				voiceAgentService, llmRouter, auditLogRepository, confirmationRepository,
				integrationRepository, userProvisioningService, transcriptionService,
				sparkService, deviceRoutingService, setupActionDetector, userConfigService,
				sparkClassifierService, pdlcClassifierService, pdlcPipelineOrchestrator,
				pipelineStateManager, playbookRegistry);

		when(authenticatedUser.getUserId()).thenReturn(USER_ID);

		// Default: CLOUD_ONLY preference so we always hit the cloud routing path
		UserConfig config = new UserConfig();
		config.setExecutionPreference(ExecutionPreference.CLOUD_ONLY);
		when(userConfigService.getConfig(USER_ID)).thenReturn(config);

		// Default spark
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		when(sparkService.createSpark(anyString(), anyString(), anyString(), anyString(),
				isNull(), isNull(), isNull(), isNull())).thenReturn(spark);

		// Default: no connected integrations (lenient — only called when cloud path runs CloudOrchestratorService)
		lenient().when(integrationRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

		// Default: no setup actions (lenient — only called when cloud path runs CloudOrchestratorService)
		lenient().when(setupActionDetector.detect(anyString(), anyString())).thenReturn(List.of());
	}

	// --- Test: code spark triggers PDLC classifier ---

	@Test
	void codeSparkTriggersPdlcClassifier() {
		AgentCommandRequest request = buildRequest("implement OAuth2 login feature", null, null);
		when(sparkClassifierService.classifySparkType(anyString(), anyString())).thenReturn("code");
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(PdlcClassification.simple());
		stubVoiceAgentSuccess();

		controller.executeCommand(request, authenticatedUser);

		verify(pdlcClassifierService).classifyDepth(anyString(), anyString(), eq("code"));
	}

	@Test
	void nonCodeSparkSkipsPdlcClassifierAndGoesToSimple() {
		AgentCommandRequest request = buildRequest("post a tweet about Tacticl", "social", null);
		// sparkType provided in request — classifier should NOT be called for spark type
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("social")))
				.thenReturn(PdlcClassification.simple());
		stubVoiceAgentSuccess();

		controller.executeCommand(request, authenticatedUser);

		// PdlcClassifierService is always called, but will return SIMPLE for non-code/devops types
		verify(pdlcClassifierService).classifyDepth(anyString(), anyString(), eq("social"));
		// SparkClassifierService should NOT be called when sparkType is provided in request
		verify(sparkClassifierService, never()).classifySparkType(anyString(), anyString());
	}

	// --- Test: FULL_PDLC routes to orchestrator ---

	@Test
	void fullPdlcTierRoutesToPipelineOrchestrator() {
		AgentCommandRequest request = buildRequest("build a new payments service with OAuth and Stripe", "code", null);
		PdlcClassification classification = fullPdlcClassification();
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(classification);

		PlaybookConfig fullPdlc = buildPlaybook("FULL_PDLC", PipelineTier.FULL_PDLC,
				List.of(PdlcRole.PM, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER));
		when(playbookRegistry.getPlaybook("FULL_PDLC")).thenReturn(Optional.of(fullPdlc));

		PipelineRun pipelineRun = buildPipelineRun(PIPELINE_RUN_ID, SPARK_ID,
				List.of(PdlcRole.PM, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER));
		when(pipelineStateManager.createRun(eq(SPARK_ID), eq(USER_ID), eq(fullPdlc), eq(classification)))
				.thenReturn(pipelineRun);

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);

		// Orchestrator dispatched
		verify(pdlcPipelineOrchestrator).executePipeline(PIPELINE_RUN_ID);
		// CloudOrchestratorService NOT called
		verify(voiceAgentService, never()).execute(anyString(), anyString(), anyString(),
				anyString(), any(), anyString(), nullable(String.class));

		AgentCommandResponse body = resp.getBody();
		assertNotNull(body);
		assertEquals("PIPELINE", body.getExecutionMode());
		assertEquals(SPARK_ID, body.getSparkId());
		assertEquals(PIPELINE_RUN_ID, body.getPipelineRunId());
		assertEquals("FULL_PDLC", body.getPipelineTier());
		assertEquals("FULL_PDLC", body.getPlaybook());
		assertNotNull(body.getActivatedRoles());
		assertTrue(body.getActivatedRoles().contains("PM"));
		assertTrue(body.isSuccess());
	}

	@Test
	void playbookTierRoutesToPipelineOrchestrator() {
		AgentCommandRequest request = buildRequest("fix null pointer in UserService.getById", "code", null);
		PdlcClassification classification = bugFixClassification();
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(classification);

		PlaybookConfig bugFix = buildPlaybook("BUG_FIX", PipelineTier.PLAYBOOK,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(bugFix));

		PipelineRun pipelineRun = buildPipelineRun(PIPELINE_RUN_ID, SPARK_ID,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		when(pipelineStateManager.createRun(eq(SPARK_ID), eq(USER_ID), eq(bugFix), eq(classification)))
				.thenReturn(pipelineRun);

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);

		verify(pdlcPipelineOrchestrator).executePipeline(PIPELINE_RUN_ID);
		verify(voiceAgentService, never()).execute(anyString(), anyString(), anyString(),
				anyString(), any(), anyString(), nullable(String.class));

		AgentCommandResponse body = resp.getBody();
		assertNotNull(body);
		assertEquals("PIPELINE", body.getExecutionMode());
		assertEquals("PLAYBOOK", body.getPipelineTier());
		assertEquals("BUG_FIX", body.getPlaybook());
	}

	// --- Test: SIMPLE routes to CloudOrchestratorService ---

	@Test
	void simpleTierRoutesToCloudOrchestratorService() {
		AgentCommandRequest request = buildRequest("what time is it in Tokyo?", "code", null);
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(PdlcClassification.simple());
		stubVoiceAgentSuccess();

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);

		verify(voiceAgentService).execute(eq(SPARK_ID), anyString(), eq(USER_ID),
				anyString(), any(), anyString(), nullable(String.class));
		verify(pdlcPipelineOrchestrator, never()).executePipeline(anyString());

		AgentCommandResponse body = resp.getBody();
		assertNotNull(body);
		assertEquals("SYNC", body.getExecutionMode());
		assertEquals("SIMPLE", body.getPipelineTier());
		assertNull(body.getPipelineRunId());
		assertNull(body.getPlaybook());
	}

	// --- Test: user override playbook is honored ---

	@Test
	void userPlaybookOverrideIsHonored() {
		// Request specifies a playbook override even though the classifier would return SIMPLE
		AgentCommandRequest request = buildRequest("refactor the auth module", "code", "REFACTOR");
		// Classifier returns SIMPLE, but user override should take precedence
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(PdlcClassification.simple());

		PlaybookConfig refactorPlaybook = buildPlaybook("REFACTOR", PipelineTier.PLAYBOOK,
				List.of(PdlcRole.RESEARCHER, PdlcRole.ARCHITECT, PdlcRole.IMPLEMENTER));
		when(playbookRegistry.getPlaybook("REFACTOR")).thenReturn(Optional.of(refactorPlaybook));

		PipelineRun pipelineRun = buildPipelineRun(PIPELINE_RUN_ID, SPARK_ID,
				List.of(PdlcRole.RESEARCHER, PdlcRole.ARCHITECT, PdlcRole.IMPLEMENTER));
		when(pipelineStateManager.createRun(eq(SPARK_ID), eq(USER_ID), eq(refactorPlaybook), any(PdlcClassification.class)))
				.thenReturn(pipelineRun);

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);

		// Pipeline should be used because of the override
		verify(pdlcPipelineOrchestrator).executePipeline(PIPELINE_RUN_ID);
		verify(voiceAgentService, never()).execute(anyString(), anyString(), anyString(),
				anyString(), any(), anyString(), nullable(String.class));

		AgentCommandResponse body = resp.getBody();
		assertNotNull(body);
		assertEquals("PIPELINE", body.getExecutionMode());
		assertEquals("REFACTOR", body.getPlaybook());
	}

	@Test
	void unknownPlaybookOverrideFallsBackToClassifierResult() {
		AgentCommandRequest request = buildRequest("simple query", "code", "UNKNOWN_PLAYBOOK");
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(PdlcClassification.simple());
		when(playbookRegistry.getPlaybook("UNKNOWN_PLAYBOOK")).thenReturn(Optional.empty());
		stubVoiceAgentSuccess();

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);

		// Unknown override is ignored — falls back to classifier result (SIMPLE → SYNC)
		verify(voiceAgentService).execute(anyString(), anyString(), anyString(),
				anyString(), any(), anyString(), nullable(String.class));
		verify(pdlcPipelineOrchestrator, never()).executePipeline(anyString());

		AgentCommandResponse body = resp.getBody();
		assertNotNull(body);
		assertEquals("SYNC", body.getExecutionMode());
	}

	// --- Test: response contains correct pipeline fields ---

	@Test
	void pipelineResponseContainsAllRequiredFields() {
		AgentCommandRequest request = buildRequest("build new OAuth service", "code", null);
		PdlcClassification classification = fullPdlcClassification();
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(classification);

		PlaybookConfig fullPdlc = buildPlaybook("FULL_PDLC", PipelineTier.FULL_PDLC,
				List.of(PdlcRole.PM, PdlcRole.RESEARCHER, PdlcRole.ARCHITECT,
						PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		when(playbookRegistry.getPlaybook("FULL_PDLC")).thenReturn(Optional.of(fullPdlc));

		PipelineRun run = buildPipelineRun(PIPELINE_RUN_ID, SPARK_ID,
				List.of(PdlcRole.PM, PdlcRole.RESEARCHER, PdlcRole.ARCHITECT,
						PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		when(pipelineStateManager.createRun(any(), any(), any(), any())).thenReturn(run);

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);
		AgentCommandResponse body = resp.getBody();

		assertNotNull(body);
		// Pipeline run fields
		assertEquals(PIPELINE_RUN_ID, body.getPipelineRunId());
		assertEquals("FULL_PDLC", body.getPipelineTier());
		assertEquals("FULL_PDLC", body.getPlaybook());
		assertNotNull(body.getActivatedRoles());
		assertEquals(6, body.getActivatedRoles().size());
		// Execution mode
		assertEquals("PIPELINE", body.getExecutionMode());
		// Spark linkage
		assertEquals(SPARK_ID, body.getSparkId());
		// Success
		assertTrue(body.isSuccess());
	}

	@Test
	void syncResponseContainsPipelineTierAndExecutionMode() {
		AgentCommandRequest request = buildRequest("what is the weather?", "social", null);
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("social")))
				.thenReturn(PdlcClassification.simple());
		stubVoiceAgentSuccess();

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);
		AgentCommandResponse body = resp.getBody();

		assertNotNull(body);
		assertEquals("SYNC", body.getExecutionMode());
		assertEquals("SIMPLE", body.getPipelineTier());
		// Pipeline-only fields are null for SIMPLE
		assertNull(body.getPipelineRunId());
		assertNull(body.getPlaybook());
		assertNull(body.getActivatedRoles());
	}

	// --- Test: spark type classification when not provided in request ---

	@Test
	void sparkTypeIsClassifiedWhenNotInRequest() {
		AgentCommandRequest request = buildRequest("implement new feature", null, null);
		when(sparkClassifierService.classifySparkType(anyString(), anyString())).thenReturn("code");
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(PdlcClassification.simple());
		stubVoiceAgentSuccess();

		controller.executeCommand(request, authenticatedUser);

		verify(sparkClassifierService).classifySparkType(anyString(), anyString());
	}

	@Test
	void sparkTypeFromRequestSkipsClassifier() {
		AgentCommandRequest request = buildRequest("post to Twitter", "social", null);
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("social")))
				.thenReturn(PdlcClassification.simple());
		stubVoiceAgentSuccess();

		controller.executeCommand(request, authenticatedUser);

		verify(sparkClassifierService, never()).classifySparkType(anyString(), anyString());
	}

	// --- Test: role skipping from natural language ---

	@Test
	void skipRolesFromNaturalLanguage_removedFromActivatedRoles() {
		// "skip review" NL phrase → REVIEWER should be removed from BUG_FIX activated roles
		AgentCommandRequest request = buildRequest("fix the bug, skip review", "code", null);
		PdlcClassification classification = bugFixClassification();
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(classification);

		PlaybookConfig bugFix = buildPlaybook("BUG_FIX", PipelineTier.PLAYBOOK,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(bugFix));

		PipelineRun pipelineRun = buildPipelineRun(PIPELINE_RUN_ID, SPARK_ID,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.TESTER));
		when(pipelineStateManager.createRun(eq(SPARK_ID), eq(USER_ID), eq(bugFix), any(PdlcClassification.class)))
				.thenAnswer(inv -> {
					PdlcClassification cls = inv.getArgument(3);
					assertFalse(cls.activatedRoles().contains(PdlcRole.REVIEWER),
							"REVIEWER should be removed by NL skip");
					return pipelineRun;
				});

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);

		verify(pdlcPipelineOrchestrator).executePipeline(PIPELINE_RUN_ID);
		AgentCommandResponse body = resp.getBody();
		assertNotNull(body);
		assertEquals("PIPELINE", body.getExecutionMode());
	}

	@Test
	void skipRolesFromApiField_removedFromActivatedRoles() {
		// API skipRoles=["TESTER"] → TESTER should be removed from BUG_FIX activated roles
		AgentCommandRequest request = buildRequest("fix the null pointer bug", "code", null);
		request.setSkipRoles(List.of("TESTER"));
		PdlcClassification classification = bugFixClassification();
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(classification);

		PlaybookConfig bugFix = buildPlaybook("BUG_FIX", PipelineTier.PLAYBOOK,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(bugFix));

		PipelineRun pipelineRun = buildPipelineRun(PIPELINE_RUN_ID, SPARK_ID,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER));
		when(pipelineStateManager.createRun(eq(SPARK_ID), eq(USER_ID), eq(bugFix), any(PdlcClassification.class)))
				.thenAnswer(inv -> {
					PdlcClassification cls = inv.getArgument(3);
					assertFalse(cls.activatedRoles().contains(PdlcRole.TESTER),
							"TESTER should be removed by API skip");
					return pipelineRun;
				});

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);

		verify(pdlcPipelineOrchestrator).executePipeline(PIPELINE_RUN_ID);
		AgentCommandResponse body = resp.getBody();
		assertNotNull(body);
		assertEquals("PIPELINE", body.getExecutionMode());
	}

	@Test
	void skipRolesUnion_nlAndApiBothApplied() {
		// NL "skip review" + API ["TESTER"] → both REVIEWER and TESTER removed
		AgentCommandRequest request = buildRequest("fix the auth bug, skip review", "code", null);
		request.setSkipRoles(List.of("TESTER"));
		PdlcClassification classification = bugFixClassification();
		when(pdlcClassifierService.classifyDepth(anyString(), anyString(), eq("code")))
				.thenReturn(classification);

		PlaybookConfig bugFix = buildPlaybook("BUG_FIX", PipelineTier.PLAYBOOK,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		when(playbookRegistry.getPlaybook("BUG_FIX")).thenReturn(Optional.of(bugFix));

		PipelineRun pipelineRun = buildPipelineRun(PIPELINE_RUN_ID, SPARK_ID,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));
		when(pipelineStateManager.createRun(eq(SPARK_ID), eq(USER_ID), eq(bugFix), any(PdlcClassification.class)))
				.thenAnswer(inv -> {
					PdlcClassification cls = inv.getArgument(3);
					assertFalse(cls.activatedRoles().contains(PdlcRole.REVIEWER),
							"REVIEWER should be removed by NL skip");
					assertFalse(cls.activatedRoles().contains(PdlcRole.TESTER),
							"TESTER should be removed by API skip");
					assertTrue(cls.activatedRoles().contains(PdlcRole.RESEARCHER),
							"RESEARCHER should remain");
					assertTrue(cls.activatedRoles().contains(PdlcRole.IMPLEMENTER),
							"IMPLEMENTER should remain");
					return pipelineRun;
				});

		ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(request, authenticatedUser);

		verify(pdlcPipelineOrchestrator).executePipeline(PIPELINE_RUN_ID);
		AgentCommandResponse body = resp.getBody();
		assertNotNull(body);
		assertEquals("PIPELINE", body.getExecutionMode());
	}

	// --- Helpers ---

	private AgentCommandRequest buildRequest(String text, String sparkType, String playbook) {
		AgentCommandRequest req = new AgentCommandRequest();
		req.setText(text);
		req.setSparkType(sparkType);
		req.setPlaybook(playbook);
		req.setSessionId("session-test");
		return req;
	}

	private void stubVoiceAgentSuccess() {
		when(voiceAgentService.execute(anyString(), anyString(), anyString(),
				anyString(), any(), anyString(), nullable(String.class)))
				.thenReturn(CloudOrchestratorService.AgentResult.success("Done.", List.of(), "claude-haiku-4-5"));
	}

	private PdlcClassification fullPdlcClassification() {
		return new PdlcClassification(
				PipelineTier.FULL_PDLC,
				"FULL_PDLC",
				0.92,
				List.of(PdlcRole.PM, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER),
				List.of(),
				Map.of("scope", 5, "risk", 4),
				"Major new system requiring full PDLC coverage.");
	}

	private PdlcClassification bugFixClassification() {
		return new PdlcClassification(
				PipelineTier.PLAYBOOK,
				"BUG_FIX",
				0.88,
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER),
				List.of(),
				Map.of("scope", 2, "risk", 2),
				"Targeted bug fix.");
	}

	private PlaybookConfig buildPlaybook(String name, PipelineTier tier, List<PdlcRole> roles) {
		List<PlaybookStage> stages = roles.stream()
				.map(r -> new PlaybookStage(r, true, List.of(), List.of(), Duration.ofMinutes(15)))
				.toList();
		return new PlaybookConfig(name, name, "Test playbook: " + name, tier,
				stages, Map.of(), Map.of(), true);
	}

	private PipelineRun buildPipelineRun(String runId, String sparkId, List<PdlcRole> activatedRoles) {
		PipelineRun run = new PipelineRun();
		run.setId(runId);
		run.setSparkId(sparkId);
		run.setUserId(USER_ID);
		run.setActivatedRoles(activatedRoles);
		return run;
	}

}
