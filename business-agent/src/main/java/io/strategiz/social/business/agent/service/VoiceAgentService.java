package io.strategiz.social.business.agent.service;

import io.strategiz.client.base.llm.model.LlmMessage;
import io.strategiz.client.base.llm.model.LlmResponse;
import io.strategiz.client.base.llm.model.ToolResultMessage;
import io.strategiz.client.base.llm.model.ToolUseBlock;
import io.strategiz.framework.llmrouter.LlmRouter;
import io.strategiz.social.business.agent.config.AgentModelConfig;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.strategiz.social.data.entity.AgentAuditLog;
import io.strategiz.social.data.repository.AgentAuditLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core voice agent orchestration service. Handles the full agent loop: system prompt →
 * Claude tool_use → execute tools → tool_result → final response.
 */
@Service
public class VoiceAgentService {

	private static final Logger logger = LoggerFactory.getLogger(VoiceAgentService.class);

	private static final int MAX_TOOL_ROUNDS = 5;

	private final LlmRouter llmRouter;

	private final AgentModelConfig modelConfig;

	private final ToolRegistry toolRegistry;

	private final AgentSystemPrompt agentSystemPrompt;

	private final AgentAuditLogRepository auditLogRepository;

	private final AskService askService;

	public VoiceAgentService(LlmRouter llmRouter, AgentModelConfig modelConfig, ToolRegistry toolRegistry,
			AgentSystemPrompt agentSystemPrompt, AgentAuditLogRepository auditLogRepository, AskService askService) {
		this.llmRouter = llmRouter;
		this.modelConfig = modelConfig;
		this.toolRegistry = toolRegistry;
		this.agentSystemPrompt = agentSystemPrompt;
		this.auditLogRepository = auditLogRepository;
		this.askService = askService;
	}

	/**
	 * Execute a voice command through the agent loop.
	 * @param commandText the transcribed user command
	 * @param userId the authenticated user ID
	 * @param sessionId the conversation session ID
	 * @param connectedPlatforms list of connected platform names
	 * @param timezone user's timezone
	 * @return the agent's text response
	 */
	public AgentResult execute(String commandText, String userId, String sessionId, List<String> connectedPlatforms,
			String timezone, String modelOverride) {
		long startTime = System.currentTimeMillis();
		List<String> toolsInvoked = new ArrayList<>();
		String effectiveModel = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride
				: modelConfig.getRoutingModel();

		// Create ask for activity tracking
		AskService.CreateAskResult askResult = askService.createAsk(userId, null, commandText, effectiveModel);
		String askId = askResult.ask().getId();
		String taskId = askResult.taskId();
		String agentId = askResult.agentId();

		AskContext.set(new AskContext(askId, taskId, agentId));
		try {
			// Mark running when agent loop starts
			askService.markRunning(askId);

			// 1. Build system prompt
			String systemPrompt = agentSystemPrompt.buildSystemPrompt(userId, connectedPlatforms, timezone);

			// 2. Initialize conversation with user command
			List<LlmMessage> messages = new ArrayList<>();
			messages.add(LlmMessage.user(commandText));

			// 3. Agent loop: call LLM via router, execute tools, repeat until end_turn
			String finalResponse = null;
			int rounds = 0;

			while (rounds < MAX_TOOL_ROUNDS) {
				rounds++;

				LlmResponse response = llmRouter.generateWithTools(messages, effectiveModel,
						toolRegistry.getToolDefinitions(), systemPrompt);

				if (!response.isSuccess()) {
					finalResponse = "I'm having trouble processing that. " + response.getError();
					break;
				}

				// Check if Claude wants to call tools
				if (response.hasToolUse()) {
					// Add assistant message with tool_use blocks to conversation
					messages.add(LlmMessage.assistantWithToolUse(response.getToolUseBlocks()));

					// Execute each tool and collect results
					List<ToolResultMessage> results = new ArrayList<>();
					for (ToolUseBlock toolUse : response.getToolUseBlocks()) {
						String result = executeToolSafely(toolUse, userId);
						toolsInvoked.add(toolUse.getName());
						results.add(ToolResultMessage.success(toolUse.getId(), result));
					}

					// Add tool results to conversation
					messages.add(LlmMessage.toolResult(results));
				}
				else {
					// end_turn — Claude is done
					finalResponse = response.getContent();
					break;
				}
			}

			if (finalResponse == null) {
				finalResponse = "I've completed the requested actions.";
			}

			// 4. Log to audit trail
			logAudit(userId, sessionId, commandText, toolsInvoked, finalResponse, true, null,
					System.currentTimeMillis() - startTime);

			// Mark ask completed
			askService.markCompleted(askId, 0, effectiveModel);

			return AgentResult.success(finalResponse, toolsInvoked, effectiveModel);
		}
		catch (Exception e) {
			logger.error("Agent execution failed for user {}", userId, e);
			String errorMsg = "Something went wrong: " + e.getMessage();
			logAudit(userId, sessionId, commandText, toolsInvoked, null, false, e.getMessage(),
					System.currentTimeMillis() - startTime);

			// Mark ask failed
			askService.markFailed(askId, 0, effectiveModel);

			return AgentResult.failure(errorMsg);
		}
		finally {
			AskContext.clear();
		}
	}

	/** Execute a tool call, catching exceptions to prevent crashing the agent loop. */
	private String executeToolSafely(ToolUseBlock toolUse, String userId) {
		Optional<AgentSkill> skill = toolRegistry.getSkill(toolUse.getName());
		if (skill.isEmpty()) {
			return "Tool not found: " + toolUse.getName();
		}

		try {
			return skill.get().execute(toolUse.getInput(), userId);
		}
		catch (Exception e) {
			logger.error("Tool execution failed: {}", toolUse.getName(), e);
			return "Error executing " + toolUse.getName() + ": " + e.getMessage();
		}
	}

	private void logAudit(String userId, String sessionId, String commandText, List<String> toolsInvoked,
			String responseText, boolean success, String errorMessage, long executionTimeMs) {
		try {
			AgentAuditLog log = new AgentAuditLog();
			log.setId(UUID.randomUUID().toString());
			log.setUserId(userId);
			log.setSessionId(sessionId);
			log.setCommandText(commandText);
			log.setToolsInvoked(toolsInvoked);
			log.setResponseText(responseText);
			log.setSuccess(success);
			log.setErrorMessage(errorMessage);
			log.setExecutionTimeMs(executionTimeMs);
			log.setCreatedAt(Instant.now());
			auditLogRepository.save(log, log.getId());
		}
		catch (Exception e) {
			logger.error("Failed to write audit log", e);
		}
	}

	/** Result container for agent execution. */
	public static class AgentResult {

		private final String responseText;

		private final List<String> toolsInvoked;

		private final boolean success;

		private final String model;

		private AgentResult(String responseText, List<String> toolsInvoked, boolean success, String model) {
			this.responseText = responseText;
			this.toolsInvoked = toolsInvoked;
			this.success = success;
			this.model = model;
		}

		public static AgentResult success(String responseText, List<String> toolsInvoked, String model) {
			return new AgentResult(responseText, toolsInvoked, true, model);
		}

		public static AgentResult failure(String errorMessage) {
			return new AgentResult(errorMessage, List.of(), false, null);
		}

		public String getResponseText() {
			return responseText;
		}

		public List<String> getToolsInvoked() {
			return toolsInvoked;
		}

		public boolean isSuccess() {
			return success;
		}

		public String getModel() {
			return model;
		}

	}

}
