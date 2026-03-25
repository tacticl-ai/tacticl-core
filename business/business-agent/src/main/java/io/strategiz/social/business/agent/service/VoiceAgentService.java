package io.strategiz.social.business.agent.service;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.framework.ai.engine.model.AiEngineEvent;
import io.cidadel.framework.ai.engine.model.AiEngineEventType;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.business.agent.ai.AiSparkTypeStepMapper;
import io.strategiz.social.data.entity.AgentAuditLog;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.repository.AgentAuditLogRepository;
import com.google.cloud.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Thin orchestration service for cloud agent execution. Delegates the full agent loop
 * (LLM calls, tool execution, multi-turn conversation) to the AI engine framework
 * via {@link AiEngineRouterService}, which resolves the appropriate engine and model
 * based on the spark's SDLC step type.
 */
@Service
public class VoiceAgentService {

	private static final Logger logger = LoggerFactory.getLogger(VoiceAgentService.class);

	private final AiEngineRouterService engineRouterService;

	private final AgentSystemPrompt agentSystemPrompt;

	private final AgentAuditLogRepository auditLogRepository;

	private final SparkService sparkService;

	private final TaskExecutor simpleSparkExecutor;

	public VoiceAgentService(AiEngineRouterService engineRouterService, AgentSystemPrompt agentSystemPrompt,
			AgentAuditLogRepository auditLogRepository, SparkService sparkService,
			@Qualifier("simpleSparkExecutor") TaskExecutor simpleSparkExecutor) {
		this.engineRouterService = engineRouterService;
		this.agentSystemPrompt = agentSystemPrompt;
		this.auditLogRepository = auditLogRepository;
		this.sparkService = sparkService;
		this.simpleSparkExecutor = simpleSparkExecutor;
	}

	/**
	 * Execute a voice command through the AI engine framework (cloud execution path).
	 * @param sparkId the spark ID (already created by the controller)
	 * @param commandText the transcribed user command
	 * @param userId the authenticated user ID
	 * @param sessionId the conversation session ID
	 * @param connectedPlatforms list of connected platform names
	 * @param timezone user's timezone
	 * @param modelOverride optional model override from the client
	 * @return the agent's execution result
	 */
	public AgentResult execute(String sparkId, String commandText, String userId, String sessionId,
			List<String> connectedPlatforms, String timezone, String modelOverride) {
		long startTime = System.currentTimeMillis();

		SparkContext.set(new SparkContext(sparkId));
		try {
			// 1. Mark spark as executing
			sparkService.markRunning(sparkId);

			// 2. Get spark to determine its type
			Spark spark = sparkService.getSpark(sparkId, userId)
				.orElseThrow(() -> new IllegalStateException("Spark not found: " + sparkId));

			// 3. Map spark type to SDLC step
			AiSdlcStep step = AiSparkTypeStepMapper.mapToStep(spark.getType());

			// 4. Build system prompt
			String systemPrompt = agentSystemPrompt.buildSystemPrompt(userId, connectedPlatforms, timezone);

			// 5. Build engine request
			AiEngineRequest request = new AiEngineRequest();
			request.setPrompt(commandText);
			request.setSystemPrompt(systemPrompt);
			request.setMetadata(Map.of("sparkId", sparkId, "userId", userId, "sessionId",
					sessionId != null ? sessionId : ""));

			// 6. Apply model override if provided, otherwise let the router use step config
			if (modelOverride != null && !modelOverride.isBlank()) {
				request.setModel(modelOverride);
			}

			// 7. Execute via engine router (resolves engine + model from step config)
			AiEngineResult result = engineRouterService.executeStep(step.name(), request);

			// 8. Extract tools invoked from engine events
			List<String> toolsInvoked = extractToolsInvoked(result);

			// 9. Determine effective model from result
			String effectiveModel = result.getModel();

			if (result.isSuccess()) {
				// 10. Log audit
				logAudit(userId, sessionId, commandText, toolsInvoked, result.getContent(), true, null,
						System.currentTimeMillis() - startTime);

				// 11. Mark spark completed
				sparkService.markCloudCompleted(sparkId, result.getTotalTokens(), effectiveModel);

				return AgentResult.success(result.getContent(), toolsInvoked, effectiveModel);
			}
			else {
				String errorMsg = "I'm having trouble processing that. " + result.getError();

				logAudit(userId, sessionId, commandText, toolsInvoked, null, false, result.getError(),
						System.currentTimeMillis() - startTime);

				sparkService.markCloudFailed(sparkId, result.getError(), result.getTotalTokens(), effectiveModel);

				return AgentResult.failure(errorMsg);
			}
		}
		catch (Exception e) {
			logger.error("Agent execution failed for user {}", userId, e);
			String errorMsg = "Something went wrong: " + e.getMessage();
			logAudit(userId, sessionId, commandText, List.of(), null, false, e.getMessage(),
					System.currentTimeMillis() - startTime);

			sparkService.markCloudFailed(sparkId, e.getMessage(), 0, null);

			return AgentResult.failure(errorMsg);
		}
		finally {
			SparkContext.clear();
		}
	}

	/**
	 * Submit a voice command for asynchronous execution and return a {@link CompletableFuture}
	 * that completes when execution finishes. The future is submitted to the
	 * {@code simpleSparkExecutor} thread pool.
	 *
	 * <p>Timeout handling (completing exceptionally after {@code timeoutMs} milliseconds) is
	 * the responsibility of the caller (AgentController) using
	 * {@link CompletableFuture#orTimeout} or equivalent, so that the controller can respond
	 * to the client appropriately without coupling timeout policy to the service layer.
	 *
	 * @param sparkId           the spark ID (already created by the controller)
	 * @param commandText       the transcribed user command
	 * @param userId            the authenticated user ID
	 * @param sessionId         the conversation session ID
	 * @param connectedPlatforms list of connected platform names
	 * @param timezone          user's timezone
	 * @param modelOverride     optional model override from the client
	 * @param timeoutMs         the timeout in milliseconds (informational; enforced by caller)
	 * @return a future that resolves to the {@link AgentResult} when execution completes
	 */
	public CompletableFuture<AgentResult> executeWithTimeout(String sparkId, String commandText, String userId,
			String sessionId, List<String> connectedPlatforms, String timezone, String modelOverride, int timeoutMs) {
		return CompletableFuture.supplyAsync(
				() -> execute(sparkId, commandText, userId, sessionId, connectedPlatforms, timezone, modelOverride),
				simpleSparkExecutor);
	}

	/** Extract tool names from engine events (TOOL_USE events). */
	private List<String> extractToolsInvoked(AiEngineResult result) {
		if (result.getEvents() == null) {
			return List.of();
		}
		List<String> tools = new ArrayList<>();
		for (AiEngineEvent event : result.getEvents()) {
			if (event.getType() == AiEngineEventType.TOOL_USE && event.getToolName() != null) {
				tools.add(event.getToolName());
			}
		}
		return tools;
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
			log.setCreatedDate(Timestamp.now());
			auditLogRepository.save(log, userId);
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
