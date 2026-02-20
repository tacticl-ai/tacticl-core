package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.AgentInstance;
import io.strategiz.social.data.entity.AgentInstanceState;
import io.strategiz.social.data.entity.AgentTask;
import io.strategiz.social.data.entity.AgentTaskState;
import io.strategiz.social.data.entity.Ask;
import io.strategiz.social.data.entity.AskState;
import io.strategiz.social.data.repository.AgentInstanceRepository;
import io.strategiz.social.data.repository.AgentTaskRepository;
import io.strategiz.social.data.repository.AskRepository;
import io.strategiz.social.data.repository.DeviceCommandRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the Ask -> Task -> Agent lifecycle. V1: one ask = one task = one agent (single-task
 * simplification).
 */
@Service
public class AskService {

	private static final Logger log = LoggerFactory.getLogger(AskService.class);

	private final AskRepository askRepository;

	private final AgentTaskRepository taskRepository;

	private final AgentInstanceRepository instanceRepository;

	private final DeviceCommandRepository commandRepository;

	private final ActivityBroadcaster activityBroadcaster;

	public AskService(AskRepository askRepository, AgentTaskRepository taskRepository,
			AgentInstanceRepository instanceRepository, DeviceCommandRepository commandRepository,
			ActivityBroadcaster activityBroadcaster) {
		this.askRepository = askRepository;
		this.taskRepository = taskRepository;
		this.instanceRepository = instanceRepository;
		this.commandRepository = commandRepository;
		this.activityBroadcaster = activityBroadcaster;
	}

	/** Result container for ask creation with all generated IDs. */
	public record CreateAskResult(Ask ask, String taskId, String agentId) {
	}

	/** Create an ask and its initial task + agent instance. Returns the result with all IDs. */
	public CreateAskResult createAsk(String userId, String deviceId, String commandText, String modelId) {
		String askId = UUID.randomUUID().toString();
		String taskId = UUID.randomUUID().toString();
		String agentId = UUID.randomUUID().toString();

		// Create agent instance
		AgentInstance agent = new AgentInstance();
		agent.setId(agentId);
		agent.setTaskId(taskId);
		agent.setAskId(askId);
		agent.setUserId(userId);
		agent.setDeviceId(deviceId);
		agent.setModelId(modelId);
		agent.setState(AgentInstanceState.INITIALIZING);
		agent.setCreatedAt(Instant.now());
		instanceRepository.save(agent, agent.getId());

		// Create task
		AgentTask task = new AgentTask();
		task.setId(taskId);
		task.setAskId(askId);
		task.setUserId(userId);
		task.setDescription(commandText);
		task.setAgentId(agentId);
		task.setState(AgentTaskState.ASSIGNED);
		task.setCreatedAt(Instant.now());
		taskRepository.save(task, task.getId());

		// Create ask
		Ask ask = new Ask();
		ask.setId(askId);
		ask.setUserId(userId);
		ask.setDeviceId(deviceId);
		ask.setCommandText(commandText);
		ask.setState(AskState.PENDING);
		ask.setTaskIds(List.of(taskId));
		ask.setCreatedAt(Instant.now());
		askRepository.save(ask, ask.getId());

		log.info("[ASK] Created ask={} task={} agent={} for user={}", askId, taskId, agentId, userId);
		broadcastAskActivity(ask);
		return new CreateAskResult(ask, taskId, agentId);
	}

	/** Mark the ask as RUNNING (agent started processing). */
	public void markRunning(String askId) {
		askRepository.findById(askId).ifPresent(ask -> {
			ask.setState(AskState.RUNNING);
			askRepository.save(ask, ask.getId());

			// Also mark task and agent as running
			for (String taskId : ask.getTaskIds()) {
				taskRepository.findById(taskId).ifPresent(task -> {
					task.setState(AgentTaskState.RUNNING);
					taskRepository.save(task, task.getId());
				});
				instanceRepository.findByTaskId(taskId).stream().findFirst().ifPresent(agent -> {
					agent.setState(AgentInstanceState.RUNNING);
					instanceRepository.save(agent, agent.getId());
				});
			}

			broadcastAskActivity(ask);
		});
	}

	/** Record that a command was created as part of this ask/task. */
	public void recordCommand(String askId, String taskId, String agentId, String commandId) {
		taskRepository.findById(taskId).ifPresent(task -> {
			List<String> cmds = task.getCommandIds();
			if (cmds == null) {
				cmds = new ArrayList<>();
			}
			cmds.add(commandId);
			task.setCommandIds(cmds);
			taskRepository.save(task, task.getId());
		});
		// Broadcast updated activity
		askRepository.findById(askId).ifPresent(this::broadcastAskActivity);
	}

	/** Mark the ask as COMPLETED with token count. */
	public void markCompleted(String askId, int totalTokens, String modelId) {
		askRepository.findById(askId).ifPresent(ask -> {
			ask.setState(AskState.COMPLETED);
			ask.setTotalTokens(totalTokens);
			ask.setEstimatedCost(estimateCost(totalTokens, modelId));
			ask.setCompletedAt(Instant.now());
			askRepository.save(ask, ask.getId());

			completeTasksAndAgents(ask);
			broadcastAskActivity(ask);
			log.info("[ASK] Completed ask={} tokens={}", askId, totalTokens);
		});
	}

	/** Mark the ask as FAILED. */
	public void markFailed(String askId, int totalTokens, String modelId) {
		askRepository.findById(askId).ifPresent(ask -> {
			ask.setState(AskState.FAILED);
			ask.setTotalTokens(totalTokens);
			ask.setEstimatedCost(estimateCost(totalTokens, modelId));
			ask.setCompletedAt(Instant.now());
			askRepository.save(ask, ask.getId());

			failTasksAndAgents(ask);
			broadcastAskActivity(ask);
			log.info("[ASK] Failed ask={}", askId);
		});
	}

	/** Cancel an ask and cascade to all tasks/agents/commands. */
	public boolean cancelAsk(String askId, String userId) {
		Optional<Ask> opt = askRepository.findById(askId);
		if (opt.isEmpty()) {
			return false;
		}
		Ask ask = opt.get();
		if (!ask.getUserId().equals(userId)) {
			return false;
		}
		if (ask.getState() == AskState.COMPLETED || ask.getState() == AskState.CANCELLED) {
			return false;
		}

		ask.setState(AskState.CANCELLED);
		ask.setCompletedAt(Instant.now());
		askRepository.save(ask, ask.getId());

		cancelTasksAndAgents(ask);
		broadcastAskActivity(ask);
		log.info("[ASK] Cancelled ask={}", askId);
		return true;
	}

	/** Get active asks for the activity dashboard. */
	public List<Ask> getActiveAsks(String userId) {
		return askRepository.findActiveByUserId(userId);
	}

	/** Get recent asks for history. */
	public List<Ask> getRecentAsks(String userId, int limit) {
		return askRepository.findRecentByUserId(userId, limit);
	}

	/** Get full ask detail with tasks and commands. */
	public Optional<Map<String, Object>> getAskDetail(String askId, String userId) {
		Optional<Ask> opt = askRepository.findById(askId);
		if (opt.isEmpty() || !opt.get().getUserId().equals(userId)) {
			return Optional.empty();
		}
		return Optional.of(buildActivityPayload(opt.get()));
	}

	private void broadcastAskActivity(Ask ask) {
		Map<String, Object> payload = buildActivityPayload(ask);
		activityBroadcaster.broadcastActivity(ask.getUserId(), payload);
	}

	private Map<String, Object> buildActivityPayload(Ask ask) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("type", "activity");
		payload.put("askId", ask.getId());
		payload.put("askState", ask.getState().name());
		payload.put("commandText", ask.getCommandText());
		payload.put("deviceId", ask.getDeviceId());
		payload.put("totalTokens", ask.getTotalTokens());
		payload.put("createdAt", ask.getCreatedAt().toString());
		if (ask.getCompletedAt() != null) {
			payload.put("completedAt", ask.getCompletedAt().toString());
		}

		List<Map<String, Object>> taskList = new ArrayList<>();
		for (String taskId : ask.getTaskIds()) {
			taskRepository.findById(taskId).ifPresent(task -> {
				Map<String, Object> taskMap = new HashMap<>();
				taskMap.put("taskId", task.getId());
				taskMap.put("description", task.getDescription());
				taskMap.put("state", task.getState().name());
				taskMap.put("agentId", task.getAgentId());

				// Get agent info
				instanceRepository.findByTaskId(task.getId()).stream().findFirst().ifPresent(agent -> {
					taskMap.put("modelId", agent.getModelId());
					taskMap.put("agentState", agent.getState().name());
					taskMap.put("tokenCount", agent.getTokenCount());
				});

				// Get commands
				List<Map<String, Object>> cmdList = new ArrayList<>();
				if (task.getCommandIds() != null) {
					for (String cmdId : task.getCommandIds()) {
						commandRepository.findById(cmdId).ifPresent(cmd -> {
							Map<String, Object> cmdMap = new HashMap<>();
							cmdMap.put("commandId", cmd.getId());
							cmdMap.put("commandType", cmd.getCommandType().name());
							cmdMap.put("deviceId", cmd.getDeviceId());
							cmdMap.put("state", cmd.getState().name());
							if (cmd.getCreatedAt() != null && cmd.getCompletedAt() != null) {
								cmdMap.put("elapsedMs",
										cmd.getCompletedAt().toEpochMilli() - cmd.getCreatedAt().toEpochMilli());
							}
							cmdList.add(cmdMap);
						});
					}
				}
				taskMap.put("commands", cmdList);
				taskList.add(taskMap);
			});
		}
		payload.put("tasks", taskList);
		return payload;
	}

	private BigDecimal estimateCost(int tokens, String modelId) {
		// Approximate pricing per 1M tokens (input+output blended)
		BigDecimal perMillionTokens;
		String model = modelId != null ? modelId : "";
		if (model.contains("opus")) {
			perMillionTokens = new BigDecimal("75.00");
		}
		else if (model.contains("sonnet")) {
			perMillionTokens = new BigDecimal("15.00");
		}
		else if (model.contains("haiku")) {
			perMillionTokens = new BigDecimal("1.25");
		}
		else {
			perMillionTokens = new BigDecimal("15.00");
		}
		return perMillionTokens.multiply(BigDecimal.valueOf(tokens))
			.divide(BigDecimal.valueOf(1_000_000), 4, RoundingMode.HALF_UP);
	}

	private void completeTasksAndAgents(Ask ask) {
		for (String taskId : ask.getTaskIds()) {
			taskRepository.findById(taskId).ifPresent(task -> {
				if (task.getState() == AgentTaskState.RUNNING || task.getState() == AgentTaskState.ASSIGNED) {
					task.setState(AgentTaskState.COMPLETED);
					task.setCompletedAt(Instant.now());
					taskRepository.save(task, task.getId());
				}
			});
			instanceRepository.findByTaskId(taskId).stream().findFirst().ifPresent(agent -> {
				if (agent.getState() == AgentInstanceState.RUNNING
						|| agent.getState() == AgentInstanceState.INITIALIZING) {
					agent.setState(AgentInstanceState.COMPLETED);
					agent.setCompletedAt(Instant.now());
					instanceRepository.save(agent, agent.getId());
				}
			});
		}
	}

	private void failTasksAndAgents(Ask ask) {
		for (String taskId : ask.getTaskIds()) {
			taskRepository.findById(taskId).ifPresent(task -> {
				if (task.getState() != AgentTaskState.COMPLETED && task.getState() != AgentTaskState.CANCELLED) {
					task.setState(AgentTaskState.FAILED);
					task.setCompletedAt(Instant.now());
					taskRepository.save(task, task.getId());
				}
			});
			instanceRepository.findByTaskId(taskId).stream().findFirst().ifPresent(agent -> {
				if (agent.getState() != AgentInstanceState.COMPLETED
						&& agent.getState() != AgentInstanceState.CANCELLED) {
					agent.setState(AgentInstanceState.FAILED);
					agent.setCompletedAt(Instant.now());
					instanceRepository.save(agent, agent.getId());
				}
			});
		}
	}

	private void cancelTasksAndAgents(Ask ask) {
		for (String taskId : ask.getTaskIds()) {
			taskRepository.findById(taskId).ifPresent(task -> {
				if (task.getState() != AgentTaskState.COMPLETED && task.getState() != AgentTaskState.FAILED) {
					task.setState(AgentTaskState.CANCELLED);
					task.setCompletedAt(Instant.now());
					taskRepository.save(task, task.getId());
				}
			});
			instanceRepository.findByTaskId(taskId).stream().findFirst().ifPresent(agent -> {
				if (agent.getState() != AgentInstanceState.COMPLETED
						&& agent.getState() != AgentInstanceState.FAILED) {
					agent.setState(AgentInstanceState.CANCELLED);
					agent.setCompletedAt(Instant.now());
					instanceRepository.save(agent, agent.getId());
				}
			});
		}
	}

}
