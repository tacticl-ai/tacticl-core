package io.strategiz.social.business.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.strategiz.client.base.llm.LlmProvider;
import io.strategiz.client.base.llm.model.LlmMessage;
import io.strategiz.client.base.llm.model.LlmResponse;
import io.strategiz.client.base.llm.model.ToolResultMessage;
import io.strategiz.client.base.llm.model.ToolUseBlock;
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

	private static final String ROUTING_MODEL = "claude-haiku-4-5";

	private static final String GENERATION_MODEL = "claude-sonnet-4-5";

	private static final int MAX_TOOL_ROUNDS = 5;

	private final LlmProvider llmProvider;

	private final ToolRegistry toolRegistry;

	private final AgentSystemPrompt agentSystemPrompt;

	private final AgentAuditLogRepository auditLogRepository;

	public VoiceAgentService(LlmProvider llmProvider, ToolRegistry toolRegistry,
			AgentSystemPrompt agentSystemPrompt, AgentAuditLogRepository auditLogRepository) {
		this.llmProvider = llmProvider;
		this.toolRegistry = toolRegistry;
		this.agentSystemPrompt = agentSystemPrompt;
		this.auditLogRepository = auditLogRepository;
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
			String timezone) {
		long startTime = System.currentTimeMillis();
		List<String> toolsInvoked = new ArrayList<>();

		try {
			// 1. Build system prompt
			String systemPrompt = agentSystemPrompt.buildSystemPrompt(userId, connectedPlatforms, timezone);

			// 2. Initialize conversation with user command
			List<LlmMessage> messages = new ArrayList<>();
			messages.add(LlmMessage.user(commandText));

			// 3. Agent loop: call Claude, execute tools, repeat until end_turn
			String finalResponse = null;
			int rounds = 0;

			while (rounds < MAX_TOOL_ROUNDS) {
				rounds++;

				LlmResponse response = llmProvider.generateWithTools(messages, ROUTING_MODEL,
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

			return AgentResult.success(finalResponse, toolsInvoked);
		}
		catch (Exception e) {
			logger.error("Agent execution failed for user {}", userId, e);
			String errorMsg = "Something went wrong: " + e.getMessage();
			logAudit(userId, sessionId, commandText, toolsInvoked, null, false, e.getMessage(),
					System.currentTimeMillis() - startTime);
			return AgentResult.failure(errorMsg);
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

		private AgentResult(String responseText, List<String> toolsInvoked, boolean success) {
			this.responseText = responseText;
			this.toolsInvoked = toolsInvoked;
			this.success = success;
		}

		public static AgentResult success(String responseText, List<String> toolsInvoked) {
			return new AgentResult(responseText, toolsInvoked, true);
		}

		public static AgentResult failure(String errorMessage) {
			return new AgentResult(errorMessage, List.of(), false);
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

	}

}
