package io.strategiz.social.business.agent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.cidadel.client.base.llm.LlmProvider;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.cidadel.client.base.llm.model.ToolUseBlock;
import io.cidadel.framework.ai.engine.AiEngineCapability;
import io.cidadel.framework.ai.engine.AiEngineCostTier;
import io.cidadel.framework.ai.engine.model.AiEngineEvent;
import io.cidadel.framework.ai.engine.model.AiEngineEventType;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.cidadel.framework.ai.engine.model.AiEngineToolDefinition;
import io.strategiz.social.business.agent.service.ToolRegistry;
import io.strategiz.social.business.agent.skill.AgentSkill;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgenticApiAiEngineTest {

	private static final JsonMapper MAPPER = new JsonMapper();

	private static final String ENGINE_ID = "test-agentic";

	private static final String DISPLAY_NAME = "Test Agentic";

	private static final String MODEL = "test-model-1";

	private static final String USER_ID = "user-123";

	@Mock
	private LlmProvider provider;

	@Mock
	private ToolRegistry toolRegistry;

	@Mock
	private AgentSkill mockSkill;

	private AgenticApiAiEngine engine;

	@BeforeEach
	void setUp() {
		engine = new AgenticApiAiEngine(provider, toolRegistry, ENGINE_ID, DISPLAY_NAME,
				AiEngineCostTier.MEDIUM, 5);
	}

	@Test
	void singleTurnNoToolUse() {
		// LLM returns end_turn immediately with no tool use
		LlmResponse response = new LlmResponse("Hello, how can I help?");
		response.setModel(MODEL);
		response.setStopReason("end_turn");
		response.setPromptTokens(10);
		response.setCompletionTokens(20);
		response.setTotalTokens(30);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString())).thenReturn(response);

		AiEngineRequest request = buildRequest("Say hello", "Be helpful");
		AiEngineResult result = engine.execute(request);

		assertTrue(result.isSuccess());
		assertEquals("Hello, how can I help?", result.getContent());
		assertEquals(ENGINE_ID, result.getEngineId());
		assertEquals(MODEL, result.getModel());
		assertEquals(10, result.getPromptTokens());
		assertEquals(20, result.getCompletionTokens());
		assertEquals(30, result.getTotalTokens());
		assertNotNull(result.getExecutionTime());
	}

	@Test
	void multiTurnWithToolUse() {
		// Round 1: LLM requests a tool call
		ObjectNode toolInput = MAPPER.createObjectNode();
		toolInput.put("query", "weather");

		ToolUseBlock toolUse = new ToolUseBlock("call-1", "web_search", toolInput);

		LlmResponse toolUseResponse = new LlmResponse();
		toolUseResponse.setSuccess(true);
		toolUseResponse.setStopReason("tool_use");
		toolUseResponse.setToolUseBlocks(List.of(toolUse));
		toolUseResponse.setPromptTokens(15);
		toolUseResponse.setCompletionTokens(25);
		toolUseResponse.setTotalTokens(40);

		// Round 2: LLM returns final answer
		LlmResponse finalResponse = new LlmResponse("The weather is sunny.");
		finalResponse.setModel(MODEL);
		finalResponse.setStopReason("end_turn");
		finalResponse.setPromptTokens(20);
		finalResponse.setCompletionTokens(10);
		finalResponse.setTotalTokens(30);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString()))
				.thenReturn(toolUseResponse)
				.thenReturn(finalResponse);
		when(toolRegistry.getSkill("web_search")).thenReturn(Optional.of(mockSkill));
		when(mockSkill.execute(toolInput, USER_ID)).thenReturn("Sunny, 72F");

		AiEngineRequest request = buildRequest("What's the weather?", "Be helpful");
		AiEngineResult result = engine.execute(request);

		assertTrue(result.isSuccess());
		assertEquals("The weather is sunny.", result.getContent());
		// Tokens accumulated across both rounds
		assertEquals(35, result.getPromptTokens());
		assertEquals(35, result.getCompletionTokens());
		assertEquals(70, result.getTotalTokens());
		verify(mockSkill).execute(toolInput, USER_ID);
		verify(provider, times(2)).generateWithTools(anyList(), eq(MODEL), anyList(), anyString());
	}

	@Test
	void maxRoundsExceeded() {
		// Every round returns tool_use, never end_turn — should stop after maxRounds
		AgenticApiAiEngine smallEngine = new AgenticApiAiEngine(provider, toolRegistry, ENGINE_ID,
				DISPLAY_NAME, AiEngineCostTier.MEDIUM, 2);

		ObjectNode toolInput = MAPPER.createObjectNode();
		ToolUseBlock toolUse = new ToolUseBlock("call-1", "search", toolInput);

		LlmResponse toolUseResponse = new LlmResponse();
		toolUseResponse.setSuccess(true);
		toolUseResponse.setStopReason("tool_use");
		toolUseResponse.setToolUseBlocks(List.of(toolUse));
		toolUseResponse.setPromptTokens(10);
		toolUseResponse.setCompletionTokens(10);
		toolUseResponse.setTotalTokens(20);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString()))
				.thenReturn(toolUseResponse);
		when(toolRegistry.getSkill("search")).thenReturn(Optional.of(mockSkill));
		when(mockSkill.execute(any(), eq(USER_ID))).thenReturn("result");

		AiEngineRequest request = buildRequest("Do something", "System");
		AiEngineResult result = smallEngine.execute(request);

		assertTrue(result.isSuccess());
		assertEquals("I've completed the requested actions.", result.getContent());
		// Called exactly maxRounds=2 times
		verify(provider, times(2)).generateWithTools(anyList(), eq(MODEL), anyList(), anyString());
	}

	@Test
	void toolNotFoundReturnsErrorAsToolResult() {
		// LLM requests an unknown tool — engine should return "Unknown tool" as tool result
		// and continue the loop; the LLM then responds with end_turn
		ObjectNode toolInput = MAPPER.createObjectNode();
		ToolUseBlock toolUse = new ToolUseBlock("call-1", "nonexistent_tool", toolInput);

		LlmResponse toolUseResponse = new LlmResponse();
		toolUseResponse.setSuccess(true);
		toolUseResponse.setStopReason("tool_use");
		toolUseResponse.setToolUseBlocks(List.of(toolUse));
		toolUseResponse.setPromptTokens(10);
		toolUseResponse.setCompletionTokens(10);
		toolUseResponse.setTotalTokens(20);

		LlmResponse finalResponse = new LlmResponse("Sorry, I don't have that tool.");
		finalResponse.setModel(MODEL);
		finalResponse.setStopReason("end_turn");
		finalResponse.setPromptTokens(15);
		finalResponse.setCompletionTokens(5);
		finalResponse.setTotalTokens(20);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString()))
				.thenReturn(toolUseResponse)
				.thenReturn(finalResponse);
		when(toolRegistry.getSkill("nonexistent_tool")).thenReturn(Optional.empty());

		AiEngineRequest request = buildRequest("Use a fake tool", "System");
		AiEngineResult result = engine.execute(request);

		assertTrue(result.isSuccess());
		assertEquals("Sorry, I don't have that tool.", result.getContent());
		// The skill was never invoked (it doesn't exist)
		verify(mockSkill, never()).execute(any(), anyString());
	}

	@Test
	void tokenAccumulationAcrossMultipleRounds() {
		// Three rounds: two tool_use + one end_turn
		ObjectNode toolInput = MAPPER.createObjectNode();
		ToolUseBlock toolUse1 = new ToolUseBlock("call-1", "search", toolInput);
		ToolUseBlock toolUse2 = new ToolUseBlock("call-2", "search", toolInput);

		LlmResponse round1 = new LlmResponse();
		round1.setSuccess(true);
		round1.setStopReason("tool_use");
		round1.setToolUseBlocks(List.of(toolUse1));
		round1.setPromptTokens(100);
		round1.setCompletionTokens(50);
		round1.setTotalTokens(150);

		LlmResponse round2 = new LlmResponse();
		round2.setSuccess(true);
		round2.setStopReason("tool_use");
		round2.setToolUseBlocks(List.of(toolUse2));
		round2.setPromptTokens(200);
		round2.setCompletionTokens(80);
		round2.setTotalTokens(280);

		LlmResponse round3 = new LlmResponse("Done.");
		round3.setModel(MODEL);
		round3.setStopReason("end_turn");
		round3.setPromptTokens(300);
		round3.setCompletionTokens(20);
		round3.setTotalTokens(320);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString()))
				.thenReturn(round1)
				.thenReturn(round2)
				.thenReturn(round3);
		when(toolRegistry.getSkill("search")).thenReturn(Optional.of(mockSkill));
		when(mockSkill.execute(any(), eq(USER_ID))).thenReturn("result");

		AiEngineRequest request = buildRequest("Do a multi-step task", "System");
		AiEngineResult result = engine.execute(request);

		assertTrue(result.isSuccess());
		assertEquals("Done.", result.getContent());
		assertEquals(600, result.getPromptTokens());     // 100 + 200 + 300
		assertEquals(150, result.getCompletionTokens());  // 50 + 80 + 20
		assertEquals(750, result.getTotalTokens());        // 150 + 280 + 320
	}

	@Test
	void eventsFiredCorrectly() {
		// Track events: STARTED, TOOL_USE, TOOL_RESULT, COMPLETED
		ObjectNode toolInput = MAPPER.createObjectNode();
		ToolUseBlock toolUse = new ToolUseBlock("call-1", "my_tool", toolInput);

		LlmResponse toolUseResponse = new LlmResponse();
		toolUseResponse.setSuccess(true);
		toolUseResponse.setStopReason("tool_use");
		toolUseResponse.setToolUseBlocks(List.of(toolUse));
		toolUseResponse.setPromptTokens(10);
		toolUseResponse.setCompletionTokens(10);
		toolUseResponse.setTotalTokens(20);

		LlmResponse finalResponse = new LlmResponse("All done.");
		finalResponse.setModel(MODEL);
		finalResponse.setStopReason("end_turn");
		finalResponse.setPromptTokens(10);
		finalResponse.setCompletionTokens(10);
		finalResponse.setTotalTokens(20);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString()))
				.thenReturn(toolUseResponse)
				.thenReturn(finalResponse);
		when(toolRegistry.getSkill("my_tool")).thenReturn(Optional.of(mockSkill));
		when(mockSkill.execute(any(), eq(USER_ID))).thenReturn("tool output");

		List<AiEngineEvent> capturedEvents = new ArrayList<>();
		AiEngineRequest request = buildRequest("Run my tool", "System");
		request.setEventListener(capturedEvents::add);

		AiEngineResult result = engine.execute(request);

		assertTrue(result.isSuccess());

		// Check event types in order: STARTED, TOOL_USE, TOOL_RESULT, COMPLETED
		assertNotNull(result.getEvents());
		assertEquals(4, result.getEvents().size());
		assertEquals(AiEngineEventType.STARTED, result.getEvents().get(0).getType());
		assertEquals(AiEngineEventType.TOOL_USE, result.getEvents().get(1).getType());
		assertEquals("my_tool", result.getEvents().get(1).getToolName());
		assertEquals(AiEngineEventType.TOOL_RESULT, result.getEvents().get(2).getType());
		assertEquals("my_tool", result.getEvents().get(2).getToolName());
		assertEquals(AiEngineEventType.COMPLETED, result.getEvents().get(3).getType());

		// Also verify the listener received the same events
		assertEquals(4, capturedEvents.size());
		assertEquals(AiEngineEventType.STARTED, capturedEvents.get(0).getType());
		assertEquals(AiEngineEventType.COMPLETED, capturedEvents.get(3).getType());
	}

	@Test
	void llmErrorReturnsFailureResult() {
		LlmResponse errorResponse = LlmResponse.error("Rate limit exceeded");
		errorResponse.setPromptTokens(5);
		errorResponse.setCompletionTokens(0);
		errorResponse.setTotalTokens(5);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString())).thenReturn(errorResponse);

		AiEngineRequest request = buildRequest("Hello", "System");
		AiEngineResult result = engine.execute(request);

		assertFalse(result.isSuccess());
		assertTrue(result.getError().contains("Rate limit exceeded"));
		assertEquals(ENGINE_ID, result.getEngineId());
	}

	@Test
	void capabilitiesIncludeAgenticExecution() {
		Set<AiEngineCapability> caps = engine.getCapabilities();
		assertTrue(caps.contains(AiEngineCapability.TEXT_GENERATION));
		assertTrue(caps.contains(AiEngineCapability.TOOL_USE));
		assertTrue(caps.contains(AiEngineCapability.AGENTIC_EXECUTION));
	}

	@Test
	void engineMetadata() {
		assertEquals(ENGINE_ID, engine.getEngineId());
		assertEquals(DISPLAY_NAME, engine.getDisplayName());
		assertEquals(AiEngineCostTier.MEDIUM, engine.getCostTier());
		assertTrue(engine.isAvailable());
	}

	@Test
	void usesModelFromRequestWhenProvided() {
		String customModel = "custom-model-v2";

		LlmResponse response = new LlmResponse("Done.");
		response.setModel(customModel);
		response.setStopReason("end_turn");

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(customModel), anyList(), anyString())).thenReturn(response);

		AiEngineRequest request = new AiEngineRequest();
		request.setPrompt("Test");
		request.setSystemPrompt("System");
		request.setModel(customModel);
		request.setMetadata(Map.of("userId", USER_ID));

		AiEngineResult result = engine.execute(request);
		assertTrue(result.isSuccess());
		assertEquals(customModel, result.getModel());
	}

	@Test
	void fallsBackToProviderDefaultModelWhenNoneSpecified() {
		String defaultModel = "provider-default-v1";

		LlmResponse response = new LlmResponse("Done.");
		response.setModel(defaultModel);
		response.setStopReason("end_turn");

		when(provider.getSupportedModels()).thenReturn(List.of(defaultModel));
		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(defaultModel), anyList(), anyString())).thenReturn(response);

		AiEngineRequest request = new AiEngineRequest();
		request.setPrompt("Test");
		request.setSystemPrompt("System");
		request.setMetadata(Map.of("userId", USER_ID));
		// model is null — should use provider's first model

		AiEngineResult result = engine.execute(request);
		assertTrue(result.isSuccess());
		assertEquals(defaultModel, result.getModel());
	}

	@Test
	void usesRequestProvidedToolDefinitionsOverRegistry() {
		AiEngineToolDefinition customTool = new AiEngineToolDefinition("custom_tool", "A custom tool",
				MAPPER.createObjectNode());

		LlmResponse response = new LlmResponse("Used custom tool defs.");
		response.setModel(MODEL);
		response.setStopReason("end_turn");

		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString())).thenReturn(response);

		AiEngineRequest request = buildRequest("Test tools", "System");
		request.setTools(List.of(customTool));

		AiEngineResult result = engine.execute(request);

		assertTrue(result.isSuccess());
		// Verify toolRegistry.getToolDefinitions() was NOT called since request provided tools
		verify(toolRegistry, never()).getToolDefinitions();
	}

	@Test
	void maxTurnsFromRequestOverridesConstructor() {
		// Engine has maxRounds=5, but request sets maxTurns=1
		ObjectNode toolInput = MAPPER.createObjectNode();
		ToolUseBlock toolUse = new ToolUseBlock("call-1", "search", toolInput);

		LlmResponse toolUseResponse = new LlmResponse();
		toolUseResponse.setSuccess(true);
		toolUseResponse.setStopReason("tool_use");
		toolUseResponse.setToolUseBlocks(List.of(toolUse));
		toolUseResponse.setPromptTokens(10);
		toolUseResponse.setCompletionTokens(10);
		toolUseResponse.setTotalTokens(20);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString()))
				.thenReturn(toolUseResponse);
		when(toolRegistry.getSkill("search")).thenReturn(Optional.of(mockSkill));
		when(mockSkill.execute(any(), eq(USER_ID))).thenReturn("result");

		AiEngineRequest request = buildRequest("Do something", "System");
		request.setMaxTurns(1);

		AiEngineResult result = engine.execute(request);

		assertTrue(result.isSuccess());
		assertEquals("I've completed the requested actions.", result.getContent());
		// Only 1 call to the provider because maxTurns=1
		verify(provider, times(1)).generateWithTools(anyList(), eq(MODEL), anyList(), anyString());
	}

	@Test
	void toolExecutionExceptionHandledGracefully() {
		// Tool throws an exception — engine should catch it and feed error result back
		ObjectNode toolInput = MAPPER.createObjectNode();
		ToolUseBlock toolUse = new ToolUseBlock("call-1", "flaky_tool", toolInput);

		LlmResponse toolUseResponse = new LlmResponse();
		toolUseResponse.setSuccess(true);
		toolUseResponse.setStopReason("tool_use");
		toolUseResponse.setToolUseBlocks(List.of(toolUse));
		toolUseResponse.setPromptTokens(10);
		toolUseResponse.setCompletionTokens(10);
		toolUseResponse.setTotalTokens(20);

		LlmResponse finalResponse = new LlmResponse("I encountered an error with that tool.");
		finalResponse.setModel(MODEL);
		finalResponse.setStopReason("end_turn");
		finalResponse.setPromptTokens(15);
		finalResponse.setCompletionTokens(10);
		finalResponse.setTotalTokens(25);

		when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
		when(provider.generateWithTools(anyList(), eq(MODEL), anyList(), anyString()))
				.thenReturn(toolUseResponse)
				.thenReturn(finalResponse);
		when(toolRegistry.getSkill("flaky_tool")).thenReturn(Optional.of(mockSkill));
		when(mockSkill.execute(any(), eq(USER_ID))).thenThrow(new RuntimeException("Connection timeout"));

		AiEngineRequest request = buildRequest("Call flaky tool", "System");
		AiEngineResult result = engine.execute(request);

		// Despite the tool exception, the agent loop continued and completed
		assertTrue(result.isSuccess());
		assertEquals("I encountered an error with that tool.", result.getContent());
	}

	/** Helper to build a standard request with model and userId in metadata. */
	private AiEngineRequest buildRequest(String prompt, String systemPrompt) {
		AiEngineRequest request = new AiEngineRequest();
		request.setPrompt(prompt);
		request.setSystemPrompt(systemPrompt);
		request.setModel(MODEL);
		request.setMetadata(Map.of("userId", USER_ID));
		return request;
	}

}
