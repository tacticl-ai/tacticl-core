package io.strategiz.social.business.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.business.ai.engine.AiEngineUnavailableException;
import io.cidadel.framework.ai.engine.model.AiEngineEvent;
import io.cidadel.framework.ai.engine.model.AiEngineEventType;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.repository.AgentAuditLogRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceAgentServiceTest {

	private static final String SPARK_ID = "spark-123";

	private static final String USER_ID = "user-456";

	private static final String SESSION_ID = "session-789";

	private static final String MODEL = "claude-sonnet-4";

	@Mock
	private AiEngineRouterService engineRouterService;

	@Mock
	private AgentSystemPrompt agentSystemPrompt;

	@Mock
	private AgentAuditLogRepository auditLogRepository;

	@Mock
	private SparkService sparkService;

	private VoiceAgentService voiceAgentService;

	@BeforeEach
	void setUp() {
		voiceAgentService = new VoiceAgentService(engineRouterService, agentSystemPrompt, auditLogRepository,
				sparkService);
	}

	@Test
	void execute_resolvesStepFromSparkType_callsEngine_marksCompleted() {
		// Given a spark with type "code"
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setType("code");

		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
		when(agentSystemPrompt.buildSystemPrompt(eq(USER_ID), any(), eq("UTC"))).thenReturn("system prompt");

		AiEngineResult engineResult = AiEngineResult.success("Here is the code.", "anthropic-agentic", MODEL);
		engineResult.setTotalTokens(500);
		AiEngineEvent toolEvent = new AiEngineEvent(AiEngineEventType.TOOL_USE, "Calling tool: web_search");
		toolEvent.setToolName("web_search");
		engineResult.setEvents(List.of(
				new AiEngineEvent(AiEngineEventType.STARTED, "Started"),
				toolEvent,
				new AiEngineEvent(AiEngineEventType.COMPLETED, "Done")));

		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_GENERATION.name()), any(AiEngineRequest.class)))
			.thenReturn(engineResult);

		// When
		VoiceAgentService.AgentResult result = voiceAgentService.execute(SPARK_ID, "Write a hello world program",
				USER_ID, SESSION_ID, List.of("Twitter"), "UTC", null);

		// Then
		assertTrue(result.isSuccess());
		assertEquals("Here is the code.", result.getResponseText());
		assertEquals(MODEL, result.getModel());
		assertEquals(List.of("web_search"), result.getToolsInvoked());

		verify(sparkService).markRunning(SPARK_ID);
		verify(sparkService).markCloudCompleted(SPARK_ID, 500, MODEL);
		verify(auditLogRepository).save(any(), eq(USER_ID));
	}

	@Test
	void execute_socialSparkType_routesToSocialContentStep() {
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setType("social");

		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
		when(agentSystemPrompt.buildSystemPrompt(eq(USER_ID), any(), eq("UTC"))).thenReturn("system prompt");

		AiEngineResult engineResult = AiEngineResult.success("Post created.", "anthropic-agentic", MODEL);
		engineResult.setTotalTokens(200);
		engineResult.setEvents(List.of());

		when(engineRouterService.executeStep(eq(AiSdlcStep.SOCIAL_CONTENT.name()), any(AiEngineRequest.class)))
			.thenReturn(engineResult);

		VoiceAgentService.AgentResult result = voiceAgentService.execute(SPARK_ID, "Post to Twitter",
				USER_ID, SESSION_ID, List.of("Twitter"), "UTC", null);

		assertTrue(result.isSuccess());
		verify(engineRouterService).executeStep(eq(AiSdlcStep.SOCIAL_CONTENT.name()), any(AiEngineRequest.class));
	}

	@Test
	void execute_modelOverrideTakesPrecedence() {
		String overrideModel = "claude-opus-4";

		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setType("code");

		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
		when(agentSystemPrompt.buildSystemPrompt(eq(USER_ID), any(), eq("UTC"))).thenReturn("system prompt");

		AiEngineResult engineResult = AiEngineResult.success("Done.", "anthropic-agentic", overrideModel);
		engineResult.setTotalTokens(100);
		engineResult.setEvents(List.of());

		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_GENERATION.name()), any(AiEngineRequest.class)))
			.thenReturn(engineResult);

		// When: model override is provided
		VoiceAgentService.AgentResult result = voiceAgentService.execute(SPARK_ID, "Hello", USER_ID,
				SESSION_ID, List.of(), "UTC", overrideModel);

		// Then: the request should have the override model set
		ArgumentCaptor<AiEngineRequest> requestCaptor = ArgumentCaptor.forClass(AiEngineRequest.class);
		verify(engineRouterService).executeStep(eq(AiSdlcStep.CODE_GENERATION.name()), requestCaptor.capture());

		AiEngineRequest capturedRequest = requestCaptor.getValue();
		assertEquals(overrideModel, capturedRequest.getModel());
		assertTrue(result.isSuccess());
	}

	@Test
	void execute_noModelOverride_doesNotSetModelOnRequest() {
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setType("research");

		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
		when(agentSystemPrompt.buildSystemPrompt(eq(USER_ID), any(), eq("UTC"))).thenReturn("system prompt");

		AiEngineResult engineResult = AiEngineResult.success("Results.", "anthropic-agentic", MODEL);
		engineResult.setTotalTokens(300);
		engineResult.setEvents(List.of());

		when(engineRouterService.executeStep(eq(AiSdlcStep.WEB_RESEARCH.name()), any(AiEngineRequest.class)))
			.thenReturn(engineResult);

		voiceAgentService.execute(SPARK_ID, "Search for AI news", USER_ID, SESSION_ID, List.of(), "UTC", null);

		ArgumentCaptor<AiEngineRequest> requestCaptor = ArgumentCaptor.forClass(AiEngineRequest.class);
		verify(engineRouterService).executeStep(eq(AiSdlcStep.WEB_RESEARCH.name()), requestCaptor.capture());

		// Model should be null so the router uses the step config default
		assertEquals(null, requestCaptor.getValue().getModel());
	}

	@Test
	void execute_engineUnavailable_returnsFailure() {
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setType("code");

		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
		when(agentSystemPrompt.buildSystemPrompt(eq(USER_ID), any(), eq("UTC"))).thenReturn("system prompt");

		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_GENERATION.name()), any(AiEngineRequest.class)))
			.thenThrow(new AiEngineUnavailableException(AiSdlcStep.CODE_GENERATION.name(),
					"No engine configured for step 'CODE_GENERATION'"));

		VoiceAgentService.AgentResult result = voiceAgentService.execute(SPARK_ID, "Write code", USER_ID,
				SESSION_ID, List.of(), "UTC", null);

		assertFalse(result.isSuccess());
		assertTrue(result.getResponseText().contains("Something went wrong"));
		verify(sparkService).markCloudFailed(eq(SPARK_ID), any(), eq(0L), eq(null));
	}

	@Test
	void execute_sparkNotFound_returnsFailure() {
		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.empty());

		VoiceAgentService.AgentResult result = voiceAgentService.execute(SPARK_ID, "Hello", USER_ID,
				SESSION_ID, List.of(), "UTC", null);

		assertFalse(result.isSuccess());
		assertTrue(result.getResponseText().contains("Something went wrong"));
		verify(sparkService).markCloudFailed(eq(SPARK_ID), any(), eq(0L), eq(null));
		// Engine should never be called
		verify(engineRouterService, never()).executeStep(any(), any());
	}

	@Test
	void execute_engineReturnsFailureResult_marksSparkFailed() {
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setType("code");

		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
		when(agentSystemPrompt.buildSystemPrompt(eq(USER_ID), any(), eq("UTC"))).thenReturn("system prompt");

		AiEngineResult errorResult = AiEngineResult.error("Rate limit exceeded", "anthropic-agentic");
		errorResult.setTotalTokens(50);
		errorResult.setEvents(List.of());

		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_GENERATION.name()), any(AiEngineRequest.class)))
			.thenReturn(errorResult);

		VoiceAgentService.AgentResult result = voiceAgentService.execute(SPARK_ID, "Write code", USER_ID,
				SESSION_ID, List.of(), "UTC", null);

		assertFalse(result.isSuccess());
		assertTrue(result.getResponseText().contains("Rate limit exceeded"));
		verify(sparkService).markCloudFailed(eq(SPARK_ID), eq("Rate limit exceeded"), eq(50L), eq(null));
		verify(sparkService, never()).markCloudCompleted(any(), any(long.class), any());
	}

	@Test
	void execute_extractsToolsFromEvents() {
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setType("code");

		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
		when(agentSystemPrompt.buildSystemPrompt(eq(USER_ID), any(), eq("UTC"))).thenReturn("system prompt");

		AiEngineEvent tool1 = new AiEngineEvent(AiEngineEventType.TOOL_USE, "Calling web_search");
		tool1.setToolName("web_search");
		AiEngineEvent tool2 = new AiEngineEvent(AiEngineEventType.TOOL_USE, "Calling browser_navigate");
		tool2.setToolName("browser_navigate");
		AiEngineEvent nonTool = new AiEngineEvent(AiEngineEventType.STARTED, "Started");

		AiEngineResult engineResult = AiEngineResult.success("Done.", "anthropic-agentic", MODEL);
		engineResult.setTotalTokens(100);
		engineResult.setEvents(List.of(nonTool, tool1, tool2));

		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_GENERATION.name()), any(AiEngineRequest.class)))
			.thenReturn(engineResult);

		VoiceAgentService.AgentResult result = voiceAgentService.execute(SPARK_ID, "Search and browse",
				USER_ID, SESSION_ID, List.of(), "UTC", null);

		assertTrue(result.isSuccess());
		assertEquals(List.of("web_search", "browser_navigate"), result.getToolsInvoked());
	}

	@Test
	void execute_requestMetadataContainsSparkUserAndSession() {
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setType("code");

		when(sparkService.getSpark(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
		when(agentSystemPrompt.buildSystemPrompt(eq(USER_ID), any(), eq("America/New_York")))
			.thenReturn("system prompt");

		AiEngineResult engineResult = AiEngineResult.success("Done.", "anthropic-agentic", MODEL);
		engineResult.setTotalTokens(100);
		engineResult.setEvents(List.of());

		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_GENERATION.name()), any(AiEngineRequest.class)))
			.thenReturn(engineResult);

		voiceAgentService.execute(SPARK_ID, "Hello world", USER_ID, SESSION_ID, List.of("Twitter"),
				"America/New_York", null);

		ArgumentCaptor<AiEngineRequest> requestCaptor = ArgumentCaptor.forClass(AiEngineRequest.class);
		verify(engineRouterService).executeStep(eq(AiSdlcStep.CODE_GENERATION.name()), requestCaptor.capture());

		AiEngineRequest capturedRequest = requestCaptor.getValue();
		assertEquals("Hello world", capturedRequest.getPrompt());
		assertEquals("system prompt", capturedRequest.getSystemPrompt());
		assertNotNull(capturedRequest.getMetadata());
		assertEquals(SPARK_ID, capturedRequest.getMetadata().get("sparkId"));
		assertEquals(USER_ID, capturedRequest.getMetadata().get("userId"));
		assertEquals(SESSION_ID, capturedRequest.getMetadata().get("sessionId"));
	}

}
