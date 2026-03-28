package io.strategiz.social.business.agent.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.cidadel.client.base.llm.LlmProvider;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.cidadel.client.base.llm.model.ToolResultMessage;
import io.cidadel.client.base.llm.model.ToolUseBlock;
import io.cidadel.framework.ai.engine.AiEngine;
import io.cidadel.framework.ai.engine.AiEngineCapability;
import io.cidadel.framework.ai.engine.AiEngineCostTier;
import io.cidadel.framework.ai.engine.model.AiEngineEvent;
import io.cidadel.framework.ai.engine.model.AiEngineEventType;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.cidadel.framework.ai.engine.model.AiEngineToolDefinition;
import io.strategiz.social.business.agent.service.ToolRegistry;
import io.strategiz.social.business.agent.skill.AgentSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agentic AI engine that wraps an {@link LlmProvider} and {@link ToolRegistry} to run a
 * multi-turn agent tool loop internally. Replicates the core loop from
 * {@code CloudOrchestratorService} but behind the {@link AiEngine} interface, making it
 * composable within the SDLC step pipeline.
 *
 * <p>Each execution round calls the LLM with tools, executes any requested tool calls via
 * the {@link ToolRegistry}, feeds results back, and repeats until the LLM signals end_turn
 * or the maximum number of rounds is reached.</p>
 */
public class AgenticApiAiEngine implements AiEngine {

	private static final Logger logger = LoggerFactory.getLogger(AgenticApiAiEngine.class);

	private static final Set<AiEngineCapability> CAPABILITIES = Set.of(AiEngineCapability.TEXT_GENERATION,
			AiEngineCapability.TOOL_USE, AiEngineCapability.AGENTIC_EXECUTION);

	private final LlmProvider provider;

	private final ToolRegistry toolRegistry;

	private final String engineId;

	private final String displayName;

	private final AiEngineCostTier costTier;

	private final int maxRounds;

	/**
	 * Creates a new {@link AgenticApiAiEngine}.
	 * @param provider the LLM provider to delegate generation to
	 * @param toolRegistry registry of available agent skills for tool execution
	 * @param engineId unique identifier for this engine (e.g. "anthropic-agentic")
	 * @param displayName human-readable display name
	 * @param costTier cost tier classification
	 * @param maxRounds maximum number of tool-use rounds before stopping
	 */
	public AgenticApiAiEngine(LlmProvider provider, ToolRegistry toolRegistry, String engineId, String displayName,
			AiEngineCostTier costTier, int maxRounds) {
		this.provider = provider;
		this.toolRegistry = toolRegistry;
		this.engineId = engineId;
		this.displayName = displayName;
		this.costTier = costTier;
		this.maxRounds = maxRounds;
	}

	@Override
	public AiEngineResult execute(AiEngineRequest request) {
		Instant start = Instant.now();
		Consumer<AiEngineEvent> listener = request.getEventListener();
		List<AiEngineEvent> events = new ArrayList<>();

		fireEvent(listener, events, new AiEngineEvent(AiEngineEventType.STARTED, "Executing via " + displayName));

		try {
			// 1. Determine model
			String model = resolveModel(request);

			// 2. Get tool definitions — prefer request-provided tools, fall back to registry
			List<ToolDefinition> tools = resolveTools(request);

			// 3. Build initial messages
			List<LlmMessage> messages = new ArrayList<>();
			messages.add(LlmMessage.user(request.getPrompt()));

			// 4. Resolve effective max rounds
			int effectiveMaxRounds = request.getMaxTurns() != null ? request.getMaxTurns() : this.maxRounds;

			// 5. Agent loop
			String finalContent = null;
			int totalPromptTokens = 0;
			int totalCompletionTokens = 0;
			int totalTokens = 0;
			List<String> toolsInvoked = new ArrayList<>();
			String userId = resolveUserId(request);

			for (int round = 0; round < effectiveMaxRounds; round++) {
				LlmResponse response = provider.generateWithTools(messages, model, tools, request.getSystemPrompt());

				// Accumulate tokens
				if (response.getPromptTokens() != null) {
					totalPromptTokens += response.getPromptTokens();
				}
				if (response.getCompletionTokens() != null) {
					totalCompletionTokens += response.getCompletionTokens();
				}
				if (response.getTotalTokens() != null) {
					totalTokens += response.getTotalTokens();
				}

				if (!response.isSuccess()) {
					fireEvent(listener, events,
							new AiEngineEvent(AiEngineEventType.FAILED, response.getError()));
					AiEngineResult errorResult = AiEngineResult.error(
							"LLM call failed: " + response.getError(), engineId);
					errorResult.setExecutionTime(Duration.between(start, Instant.now()));
					errorResult.setEvents(events);
					errorResult.setPromptTokens(totalPromptTokens);
					errorResult.setCompletionTokens(totalCompletionTokens);
					errorResult.setTotalTokens(totalTokens);
					return errorResult;
				}

				if (response.hasToolUse()) {
					// Add assistant message with tool_use blocks to conversation
					messages.add(LlmMessage.assistantWithToolUse(response.getToolUseBlocks()));

					// Execute each tool and collect results
					List<ToolResultMessage> results = new ArrayList<>();
					for (ToolUseBlock toolUse : response.getToolUseBlocks()) {
						AiEngineEvent toolUseEvent = new AiEngineEvent(AiEngineEventType.TOOL_USE,
								"Calling tool: " + toolUse.getName());
						toolUseEvent.setToolName(toolUse.getName());
						fireEvent(listener, events, toolUseEvent);

						String result = executeToolSafely(toolUse, userId);
						toolsInvoked.add(toolUse.getName());
						results.add(ToolResultMessage.success(toolUse.getId(), result));

						AiEngineEvent toolResultEvent = new AiEngineEvent(AiEngineEventType.TOOL_RESULT,
								"Tool result from: " + toolUse.getName());
						toolResultEvent.setToolName(toolUse.getName());
						fireEvent(listener, events, toolResultEvent);
					}

					// Add tool results to conversation
					messages.add(LlmMessage.toolResult(results));
				}
				else {
					// end_turn — LLM is done
					finalContent = response.getContent();
					break;
				}
			}

			// If we exhausted all rounds without an end_turn, use fallback
			if (finalContent == null) {
				finalContent = "I've completed the requested actions.";
			}

			Duration executionTime = Duration.between(start, Instant.now());
			fireEvent(listener, events, new AiEngineEvent(AiEngineEventType.COMPLETED, "Completed successfully"));

			AiEngineResult result = AiEngineResult.success(finalContent, engineId, model);
			result.setPromptTokens(totalPromptTokens);
			result.setCompletionTokens(totalCompletionTokens);
			result.setTotalTokens(totalTokens);
			result.setExecutionTime(executionTime);
			result.setEvents(events);
			return result;
		}
		catch (Exception ex) {
			Duration executionTime = Duration.between(start, Instant.now());
			logger.error("Agentic engine execution failed for engine {}: {}", engineId, ex.getMessage(), ex);
			fireEvent(listener, events, new AiEngineEvent(AiEngineEventType.FAILED, ex.getMessage()));

			AiEngineResult errorResult = AiEngineResult.error(ex.getMessage(), engineId);
			errorResult.setExecutionTime(executionTime);
			errorResult.setEvents(events);
			return errorResult;
		}
	}

	@Override
	public String getEngineId() {
		return engineId;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

	@Override
	public Set<AiEngineCapability> getCapabilities() {
		return CAPABILITIES;
	}

	@Override
	public AiEngineCostTier getCostTier() {
		return costTier;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	/** Resolve which model to use: request-specified, or provider's first supported model. */
	private String resolveModel(AiEngineRequest request) {
		if (request.getModel() != null && !request.getModel().isBlank()) {
			return request.getModel();
		}
		List<String> supported = provider.getSupportedModels();
		return (supported != null && !supported.isEmpty()) ? supported.getFirst() : null;
	}

	/**
	 * Resolve tool definitions: prefer request-provided tools (converted from
	 * AiEngineToolDefinition), fall back to the full ToolRegistry.
	 */
	private List<ToolDefinition> resolveTools(AiEngineRequest request) {
		if (request.getTools() != null && !request.getTools().isEmpty()) {
			List<ToolDefinition> converted = new ArrayList<>(request.getTools().size());
			for (AiEngineToolDefinition tool : request.getTools()) {
				converted.add(new ToolDefinition(tool.getName(), tool.getDescription(), tool.getInputSchema()));
			}
			return converted;
		}
		return toolRegistry.getToolDefinitions();
	}

	/** Extract userId from request metadata. */
	private String resolveUserId(AiEngineRequest request) {
		if (request.getMetadata() != null) {
			Object userId = request.getMetadata().get("userId");
			if (userId != null) {
				return userId.toString();
			}
		}
		return null;
	}

	/** Execute a tool call safely, catching exceptions to prevent crashing the agent loop. */
	private String executeToolSafely(ToolUseBlock toolUse, String userId) {
		Optional<AgentSkill> skill = toolRegistry.getSkill(toolUse.getName());
		if (skill.isEmpty()) {
			return "Unknown tool: " + toolUse.getName();
		}

		try {
			return skill.get().execute(toolUse.getInput(), userId);
		}
		catch (Exception e) {
			logger.error("Tool execution failed: {}", toolUse.getName(), e);
			return "Error executing " + toolUse.getName() + ": " + e.getMessage();
		}
	}

	/** Fire an event to the listener and record it in the events list. */
	private void fireEvent(Consumer<AiEngineEvent> listener, List<AiEngineEvent> events, AiEngineEvent event) {
		events.add(event);
		if (listener != null) {
			try {
				listener.accept(event);
			}
			catch (Exception ex) {
				logger.warn("Event listener threw exception for event {}: {}", event.getType(), ex.getMessage());
			}
		}
	}

}
