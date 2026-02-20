# Agent Activity Dashboard Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a real-time agent activity dashboard with Ask/Task/Agent/Command hierarchy, restructure mobile tabs, add WebSocket activity broadcasting, and scaffold the Electron desktop agent.

**Architecture:** New entities (Ask, AgentTask, AgentInstance) in data-social with Firestore repositories. VoiceAgentService wired to create/update these entities during execution. WebSocket broadcasts state changes to all user devices. Mobile dashboard restructured to 4 tabs with live pipeline view. Electron desktop agent as new repo with full OS automation.

**Tech Stack:** Java 21 / Spring Boot (backend), React Native / Expo / React Native Paper (mobile), Electron / React / TypeScript (desktop)

---

## Phase 1: Backend Data Model & Entities

### Task 1: Add new enums for Ask, Task, and Agent states

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/AskState.java`
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/AgentTaskState.java`
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/AgentInstanceState.java`
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/CommandState.java`

**Step 1: Create AskState enum**

```java
package io.strategiz.social.data.entity;

/** Lifecycle states for a user ask. */
public enum AskState {
	PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}
```

**Step 2: Create AgentTaskState enum**

```java
package io.strategiz.social.data.entity;

/** Lifecycle states for an agent task within an ask. */
public enum AgentTaskState {
	PENDING, ASSIGNED, RUNNING, COMPLETED, FAILED, CANCELLED
}
```

**Step 3: Create AgentInstanceState enum**

```java
package io.strategiz.social.data.entity;

/** Lifecycle states for an LLM agent instance working on a task. */
public enum AgentInstanceState {
	INITIALIZING, RUNNING, COMPLETED, FAILED, CANCELLED
}
```

**Step 4: Add CANCELLED to CommandState**

Add `CANCELLED` to the existing `CommandState` enum after `EXPIRED`.

**Step 5: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/AskState.java \
        data-social/src/main/java/io/strategiz/social/data/entity/AgentTaskState.java \
        data-social/src/main/java/io/strategiz/social/data/entity/AgentInstanceState.java \
        data-social/src/main/java/io/strategiz/social/data/entity/CommandState.java
git commit -m "feat: Add state enums for Ask, AgentTask, and AgentInstance"
```

---

### Task 2: Create Ask entity

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/Ask.java`

**Step 1: Create the Ask entity**

```java
package io.strategiz.social.data.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a user's top-level request to the agent. An ask decomposes into one or more tasks,
 * each worked by an agent instance. All tasks within an ask target the same primary device.
 */
public class Ask {

	private String id;
	private String userId;
	private String deviceId;
	private String commandText;
	private AskState state;
	private List<String> taskIds;
	private int totalTokens;
	private BigDecimal estimatedCost;
	private boolean deviceFallbackEnabled;
	private Instant createdAt;
	private Instant completedAt;

	public Ask() {
		this.taskIds = new ArrayList<>();
		this.state = AskState.PENDING;
		this.totalTokens = 0;
		this.estimatedCost = BigDecimal.ZERO;
		this.deviceFallbackEnabled = false;
	}

	// All getters and setters
}
```

**Step 2: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/Ask.java
git commit -m "feat: Add Ask entity"
```

---

### Task 3: Create AgentTask entity

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/AgentTask.java`

**Step 1: Create the AgentTask entity**

```java
package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a unit of work within an ask. Each task is assigned to an agent instance
 * and produces one or more device commands.
 */
public class AgentTask {

	private String id;
	private String askId;
	private String userId;
	private String description;
	private String agentId;
	private AgentTaskState state;
	private List<String> commandIds;
	private Instant createdAt;
	private Instant completedAt;

	public AgentTask() {
		this.commandIds = new ArrayList<>();
		this.state = AgentTaskState.PENDING;
	}

	// All getters and setters
}
```

**Step 2: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/AgentTask.java
git commit -m "feat: Add AgentTask entity"
```

---

### Task 4: Create AgentInstance entity

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/AgentInstance.java`

**Step 1: Create the AgentInstance entity**

```java
package io.strategiz.social.data.entity;

import java.time.Instant;

/**
 * Represents an LLM agent instance working on a specific task. Tracks the model used,
 * token consumption, and lifecycle state.
 */
public class AgentInstance {

	private String id;
	private String taskId;
	private String askId;
	private String userId;
	private String deviceId;
	private String modelId;
	private AgentInstanceState state;
	private int tokenCount;
	private Instant createdAt;
	private Instant completedAt;

	public AgentInstance() {
		this.state = AgentInstanceState.INITIALIZING;
		this.tokenCount = 0;
	}

	// All getters and setters
}
```

**Step 2: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/AgentInstance.java
git commit -m "feat: Add AgentInstance entity"
```

---

### Task 5: Add askId, taskId, agentId to DeviceCommand

**Files:**
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/DeviceCommand.java`

**Step 1: Add three new fields to DeviceCommand**

Add after the `sessionId` field:

```java
private String askId;
private String taskId;
private String agentId;
```

Add corresponding getters and setters.

**Step 2: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/DeviceCommand.java
git commit -m "feat: Add askId, taskId, agentId to DeviceCommand"
```

---

### Task 6: Create repositories for new entities

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/repository/AskRepository.java`
- Create: `data-social/src/main/java/io/strategiz/social/data/repository/AgentTaskRepository.java`
- Create: `data-social/src/main/java/io/strategiz/social/data/repository/AgentInstanceRepository.java`

**Step 1: Create AskRepository**

```java
package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import io.strategiz.social.data.entity.Ask;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for asks Firestore collection. */
@Repository
public class AskRepository extends FirestoreRepository<Ask> {

	public AskRepository(Firestore firestore) {
		super(firestore, Ask.class, "asks");
	}

	/** Find active (non-terminal) asks for a user. */
	public List<Ask> findActiveByUserId(String userId) {
		return executeQuery(getCollection().whereEqualTo("userId", userId)
			.whereIn("state", List.of("PENDING", "RUNNING"))
			.orderBy("createdAt", Query.Direction.DESCENDING));
	}

	/** Find recent asks for a user (all states). */
	public List<Ask> findRecentByUserId(String userId, int limit) {
		return executeQuery(getCollection().whereEqualTo("userId", userId)
			.orderBy("createdAt", Query.Direction.DESCENDING)
			.limit(limit));
	}
}
```

**Step 2: Create AgentTaskRepository**

```java
package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.AgentTask;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for agent_tasks Firestore collection. */
@Repository
public class AgentTaskRepository extends FirestoreRepository<AgentTask> {

	public AgentTaskRepository(Firestore firestore) {
		super(firestore, AgentTask.class, "agent_tasks");
	}

	/** Find all tasks for an ask. */
	public List<AgentTask> findByAskId(String askId) {
		return findByField("askId", askId);
	}
}
```

**Step 3: Create AgentInstanceRepository**

```java
package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.AgentInstance;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for agent_instances Firestore collection. */
@Repository
public class AgentInstanceRepository extends FirestoreRepository<AgentInstance> {

	public AgentInstanceRepository(Firestore firestore) {
		super(firestore, AgentInstance.class, "agent_instances");
	}

	/** Find agent instance for a task. */
	public List<AgentInstance> findByTaskId(String taskId) {
		return findByField("taskId", taskId);
	}

	/** Find all agent instances for an ask. */
	public List<AgentInstance> findByAskId(String askId) {
		return findByField("askId", askId);
	}
}
```

**Step 4: Build to verify compilation**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew build -x test --no-daemon 2>&1 | tail -10
```

**Step 5: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/repository/AskRepository.java \
        data-social/src/main/java/io/strategiz/social/data/repository/AgentTaskRepository.java \
        data-social/src/main/java/io/strategiz/social/data/repository/AgentInstanceRepository.java
git commit -m "feat: Add repositories for Ask, AgentTask, AgentInstance"
```

---

## Phase 2: Backend Services & Activity Broadcasting

### Task 7: Create ActivityBroadcaster for WebSocket push

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/service/ActivityBroadcaster.java`

This is an interface in the business layer. The service layer (DeviceSessionManager) implements it to push activity updates to all user's connected devices.

**Step 1: Create the interface**

```java
package io.strategiz.social.business.agent.service;

import java.util.Map;

/**
 * Interface for broadcasting activity updates to all of a user's connected devices.
 * Implemented by DeviceSessionManager in the service layer.
 */
public interface ActivityBroadcaster {

	/** Broadcast an activity update to all devices owned by a user. */
	void broadcastActivity(String userId, Map<String, Object> activityPayload);

}
```

**Step 2: Implement in DeviceSessionManager**

In `DeviceSessionManager.java`, add `implements ActivityBroadcaster` (alongside existing `DeviceCommandDispatcher`).

Add method:

```java
@Override
public void broadcastActivity(String userId, Map<String, Object> activityPayload) {
    for (Map.Entry<String, DevicePrincipal> entry : devicePrincipals.entrySet()) {
        if (entry.getValue().getUserId().equals(userId)) {
            WebSocketSession session = deviceSessions.get(entry.getKey());
            if (session != null && session.isOpen()) {
                try {
                    String json = objectMapper.writeValueAsString(activityPayload);
                    session.sendMessage(new TextMessage(json));
                }
                catch (Exception ex) {
                    log.error("[WS] Failed to broadcast activity to device {}", entry.getKey(), ex);
                }
            }
        }
    }
}
```

**Step 3: Commit**

```bash
git add business-agent/src/main/java/io/strategiz/social/business/agent/service/ActivityBroadcaster.java \
        service-agent/src/main/java/io/strategiz/social/service/agent/websocket/DeviceSessionManager.java
git commit -m "feat: Add ActivityBroadcaster interface and implement in DeviceSessionManager"
```

---

### Task 8: Create AskService — orchestrates the Ask lifecycle

**Files:**
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/service/AskService.java`

**Step 1: Create AskService**

This service manages the full Ask → Task → Agent → Command lifecycle. For now (v1), one ask = one task = one agent. The service:
- Creates an Ask when a user submits a command
- Creates a single AgentTask and AgentInstance for that ask
- Updates state as the VoiceAgentService executes
- Broadcasts activity on every state change
- Handles cancellation

```java
package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.AgentInstance;
import io.strategiz.social.data.entity.AgentInstanceState;
import io.strategiz.social.data.entity.AgentTask;
import io.strategiz.social.data.entity.AgentTaskState;
import io.strategiz.social.data.entity.Ask;
import io.strategiz.social.data.entity.AskState;
import io.strategiz.social.data.entity.DeviceCommand;
import io.strategiz.social.data.repository.AgentInstanceRepository;
import io.strategiz.social.data.repository.AgentTaskRepository;
import io.strategiz.social.data.repository.AskRepository;
import io.strategiz.social.data.repository.DeviceCommandRepository;
import java.math.BigDecimal;
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
 * Orchestrates the Ask → Task → Agent lifecycle.
 * V1: one ask = one task = one agent (single-task simplification).
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

	/** Create an ask and its initial task + agent instance. Returns the ask. */
	public Ask createAsk(String userId, String deviceId, String commandText, String modelId) {
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
		return ask;
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
		BigDecimal perMillionTokens = switch (modelId != null ? modelId : "") {
			case String s when s.contains("opus") -> new BigDecimal("75.00");
			case String s when s.contains("sonnet") -> new BigDecimal("15.00");
			case String s when s.contains("haiku") -> new BigDecimal("1.25");
			default -> new BigDecimal("15.00");
		};
		return perMillionTokens.multiply(BigDecimal.valueOf(tokens)).divide(BigDecimal.valueOf(1_000_000), 4, BigDecimal.ROUND_HALF_UP);
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
				if (agent.getState() == AgentInstanceState.RUNNING || agent.getState() == AgentInstanceState.INITIALIZING) {
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
				if (agent.getState() != AgentInstanceState.COMPLETED && agent.getState() != AgentInstanceState.CANCELLED) {
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
				if (agent.getState() != AgentInstanceState.COMPLETED && agent.getState() != AgentInstanceState.FAILED) {
					agent.setState(AgentInstanceState.CANCELLED);
					agent.setCompletedAt(Instant.now());
					instanceRepository.save(agent, agent.getId());
				}
			});
		}
	}
}
```

**Step 2: Build**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew build -x test --no-daemon 2>&1 | tail -10
```

**Step 3: Commit**

```bash
git add business-agent/src/main/java/io/strategiz/social/business/agent/service/AskService.java
git commit -m "feat: Add AskService for Ask/Task/Agent lifecycle management"
```

---

### Task 9: Wire AskService into VoiceAgentService

**Files:**
- Modify: `business-agent/src/main/java/io/strategiz/social/business/agent/service/VoiceAgentService.java`

**Step 1: Add AskService dependency**

Add `AskService` to the constructor. Inject alongside existing dependencies.

**Step 2: Create ask at start of execute()**

At the beginning of `execute()`, before the agent loop:

```java
// Create ask for activity tracking
Ask ask = askService.createAsk(userId, null, commandText, model);
String askId = ask.getId();
String taskId = ask.getTaskIds().get(0);
String agentId = /* get from task */ ;

// Mark running when agent loop starts
askService.markRunning(askId);
```

The `deviceId` is null initially — it gets set when the agent selects a device via DeviceRoutingService. Update: pass `null` for deviceId on creation, set it when first device command is created.

**Step 3: Pass askId/taskId/agentId to DeviceCommandService**

When `executeToolSafely()` invokes a skill that creates a DeviceCommand, the askId/taskId/agentId need to flow through. This requires:

1. Add `askId`, `taskId`, `agentId` fields to a new `AgentContext` class (or use ThreadLocal)
2. Skills that call `DeviceCommandService.createCommand()` pass these IDs
3. After each command is created, call `askService.recordCommand(askId, taskId, agentId, commandId)`

For v1, use a ThreadLocal holder:

```java
package io.strategiz.social.business.agent.service;

/** Thread-local context for the currently executing ask/task/agent. */
public class AskContext {
	private static final ThreadLocal<AskContext> CURRENT = new ThreadLocal<>();

	private final String askId;
	private final String taskId;
	private final String agentId;

	public AskContext(String askId, String taskId, String agentId) {
		this.askId = askId;
		this.taskId = taskId;
		this.agentId = agentId;
	}

	public static void set(AskContext ctx) { CURRENT.set(ctx); }
	public static AskContext get() { return CURRENT.get(); }
	public static void clear() { CURRENT.remove(); }

	public String getAskId() { return askId; }
	public String getTaskId() { return taskId; }
	public String getAgentId() { return agentId; }
}
```

**Step 4: Set context before agent loop, clear after**

```java
AskContext.set(new AskContext(askId, taskId, agentId));
try {
    // ... existing agent loop ...
} finally {
    AskContext.clear();
}
```

**Step 5: In DeviceCommandService.createCommand(), read from AskContext**

```java
AskContext ctx = AskContext.get();
if (ctx != null) {
    cmd.setAskId(ctx.getAskId());
    cmd.setTaskId(ctx.getTaskId());
    cmd.setAgentId(ctx.getAgentId());
}
```

**Step 6: Mark completed/failed at end of execute()**

```java
if (result.isSuccess()) {
    askService.markCompleted(askId, result.getTokenCount(), model);
} else {
    askService.markFailed(askId, result.getTokenCount(), model);
}
```

**Step 7: Build and verify**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew build -x test --no-daemon 2>&1 | tail -10
```

**Step 8: Commit**

```bash
git add business-agent/src/main/java/io/strategiz/social/business/agent/service/VoiceAgentService.java \
        business-agent/src/main/java/io/strategiz/social/business/agent/service/AskContext.java \
        business-agent/src/main/java/io/strategiz/social/business/agent/service/DeviceCommandService.java
git commit -m "feat: Wire AskService into VoiceAgentService for activity tracking"
```

---

### Task 10: Add activity REST endpoints

**Files:**
- Create: `service-agent/src/main/java/io/strategiz/social/service/agent/dto/ActivityResponse.java`
- Create: `service-agent/src/main/java/io/strategiz/social/service/agent/dto/AskDetailResponse.java`
- Modify: `service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java`

**Step 1: Create ActivityResponse DTO**

```java
package io.strategiz.social.service.agent.dto;

import java.util.List;
import java.util.Map;

/** Response DTO for the activity dashboard. */
public class ActivityResponse {

	private List<Map<String, Object>> activeAsks;
	private List<Map<String, Object>> recentAsks;

	// Constructor, getters, setters
}
```

**Step 2: Add endpoints to AgentController**

```java
@GetMapping("/activity")
@RequireAuth
@Operation(summary = "Get activity dashboard data",
        description = "Returns active and recent asks with tasks and commands")
public ResponseEntity<ActivityResponse> getActivity(@AuthUser AuthenticatedUser user) {
    List<Ask> active = askService.getActiveAsks(user.getUserId());
    List<Ask> recent = askService.getRecentAsks(user.getUserId(), 10);

    ActivityResponse response = new ActivityResponse();
    response.setActiveAsks(active.stream()
        .map(a -> askService.getAskDetail(a.getId(), user.getUserId()).orElse(Map.of()))
        .toList());
    response.setRecentAsks(recent.stream()
        .filter(a -> a.getState() != AskState.PENDING && a.getState() != AskState.RUNNING)
        .map(a -> askService.getAskDetail(a.getId(), user.getUserId()).orElse(Map.of()))
        .limit(10)
        .toList());
    return ResponseEntity.ok(response);
}

@GetMapping("/asks/{askId}")
@RequireAuth
@Operation(summary = "Get ask detail")
public ResponseEntity<Map<String, Object>> getAskDetail(@PathVariable String askId,
        @AuthUser AuthenticatedUser user) {
    return askService.getAskDetail(askId, user.getUserId())
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}

@PostMapping("/asks/{askId}/cancel")
@RequireAuth
@Operation(summary = "Cancel an ask")
public ResponseEntity<Void> cancelAsk(@PathVariable String askId,
        @AuthUser AuthenticatedUser user) {
    boolean cancelled = askService.cancelAsk(askId, user.getUserId());
    return cancelled ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
}
```

**Step 3: Build and verify**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew build -x test --no-daemon 2>&1 | tail -10
```

**Step 4: Commit**

```bash
git add service-agent/src/main/java/io/strategiz/social/service/agent/dto/ActivityResponse.java \
        service-agent/src/main/java/io/strategiz/social/service/agent/controller/AgentController.java
git commit -m "feat: Add activity dashboard and ask management REST endpoints"
```

---

### Task 11: Handle activity messages in WebSocket handler

**Files:**
- Modify: `service-agent/src/main/java/io/strategiz/social/service/agent/websocket/DeviceWebSocketHandler.java`

**Step 1: No changes needed for inbound**

The WebSocket handler already processes `result`, `capabilities`, `status`, and `ping` messages from devices. Activity broadcasts are **outbound only** (server → device), handled by `DeviceSessionManager.broadcastActivity()`. No handler changes needed.

**Step 2: Verify and commit**

Build to confirm nothing is broken:

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew build -x test --no-daemon 2>&1 | tail -10
```

---

## Phase 3: Mobile Dashboard Restructure

### Task 12: Restructure mobile tabs

**Files:**
- Modify: `tacticl-mobile/app/(tabs)/_layout.tsx`
- Create: `tacticl-mobile/app/(tabs)/devices.tsx`
- Create: `tacticl-mobile/app/(tabs)/settings.tsx`
- Delete: `tacticl-mobile/app/(tabs)/posts.tsx`
- Delete: `tacticl-mobile/app/(tabs)/accounts.tsx`
- Modify: `tacticl-mobile/app/(tabs)/index.tsx` (rewrite as activity dashboard)

**Step 1: Update tab layout**

Replace Posts and Accounts tabs with Devices and Settings:

```typescript
<Tabs.Screen name="index" options={{
  title: 'Home',
  tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="home" color={color} size={size} />,
}} />
<Tabs.Screen name="devices" options={{
  title: 'Devices',
  tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="cellphone-link" color={color} size={size} />,
}} />
<Tabs.Screen name="history" options={{
  title: 'History',
  tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="clock-outline" color={color} size={size} />,
}} />
<Tabs.Screen name="settings" options={{
  title: 'Settings',
  tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="cog" color={color} size={size} />,
}} />
```

**Step 2: Create minimal Devices screen**

```typescript
// app/(tabs)/devices.tsx
import React from 'react';
import { FlatList, RefreshControl, StyleSheet, View } from 'react-native';
import { Card, Chip, Text, useTheme } from 'react-native-paper';
import { useDevices } from '@/src/hooks/useDevices';
import { spacing } from '@/src/theme';

export default function DevicesScreen() {
  const theme = useTheme();
  const { data: devices, isLoading, refetch } = useDevices();

  return (
    <FlatList
      data={devices}
      keyExtractor={(item) => item.deviceId}
      contentContainerStyle={styles.container}
      refreshControl={<RefreshControl refreshing={isLoading} onRefresh={refetch} />}
      ListEmptyComponent={<Text style={styles.empty}>No devices registered</Text>}
      renderItem={({ item }) => (
        <Card style={[styles.card, { backgroundColor: theme.colors.surface }]}>
          <Card.Content>
            <View style={styles.row}>
              <Text variant="titleMedium">{item.deviceName}</Text>
              <Chip compact mode="flat"
                style={{ backgroundColor: item.online ? '#1B5E20' : '#B71C1C' }}>
                {item.online ? 'Online' : 'Offline'}
              </Chip>
            </View>
            <Text variant="bodySmall" style={{ color: '#888' }}>
              {item.deviceType} · {item.capabilities ? Object.keys(item.capabilities).length : 0} capabilities
            </Text>
          </Card.Content>
        </Card>
      )}
    />
  );
}

const styles = StyleSheet.create({
  container: { padding: spacing.md },
  card: { marginBottom: spacing.sm },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  empty: { textAlign: 'center', marginTop: spacing.xl, color: '#888' },
});
```

**Step 3: Create minimal Settings screen**

```typescript
// app/(tabs)/settings.tsx
import React from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { List, Text, useTheme } from 'react-native-paper';
import { useAuthStore } from '@/src/stores/auth-store';
import { spacing } from '@/src/theme';

export default function SettingsScreen() {
  const theme = useTheme();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  return (
    <ScrollView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <List.Section>
        <List.Subheader>Account</List.Subheader>
        <List.Item
          title="Sign Out"
          left={(props) => <List.Icon {...props} icon="logout" />}
          onPress={clearAuth}
        />
      </List.Section>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
});
```

**Step 4: Delete posts.tsx and accounts.tsx**

```bash
rm /Users/cuztomizer/Documents/GitHub/tacticl-mobile/app/\(tabs\)/posts.tsx
rm /Users/cuztomizer/Documents/GitHub/tacticl-mobile/app/\(tabs\)/accounts.tsx
```

**Step 5: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-mobile
git add -A
git commit -m "feat: Restructure tabs — replace Posts/Accounts with Devices/Settings"
```

---

### Task 13: Add API hooks and WebSocket activity handling

**Files:**
- Create: `tacticl-mobile/src/hooks/useDevices.ts`
- Create: `tacticl-mobile/src/hooks/useActivity.ts`
- Create: `tacticl-mobile/src/stores/activity-store.ts`
- Modify: `tacticl-mobile/src/api/client.ts` (add devices and activity API)
- Modify: `tacticl-mobile/src/lib/websocket.ts` (handle activity messages)

**Step 1: Create activity Zustand store**

```typescript
// src/stores/activity-store.ts
import { create } from 'zustand';

export interface ActivityTask {
  taskId: string;
  description: string;
  state: string;
  agentId: string;
  modelId?: string;
  agentState?: string;
  tokenCount?: number;
  commands: ActivityCommand[];
}

export interface ActivityCommand {
  commandId: string;
  commandType: string;
  deviceId: string | null;
  state: string;
  elapsedMs?: number;
}

export interface ActivityAsk {
  askId: string;
  askState: string;
  commandText: string;
  deviceId: string | null;
  totalTokens: number;
  createdAt: string;
  completedAt?: string;
  tasks: ActivityTask[];
}

interface ActivityState {
  activeAsks: ActivityAsk[];
  recentAsks: ActivityAsk[];
  updateAsk: (ask: ActivityAsk) => void;
  setInitialData: (active: ActivityAsk[], recent: ActivityAsk[]) => void;
}

export const useActivityStore = create<ActivityState>((set) => ({
  activeAsks: [],
  recentAsks: [],
  updateAsk: (ask) =>
    set((state) => {
      const isTerminal = ['COMPLETED', 'FAILED', 'CANCELLED'].includes(ask.askState);

      if (isTerminal) {
        return {
          activeAsks: state.activeAsks.filter((a) => a.askId !== ask.askId),
          recentAsks: [ask, ...state.recentAsks.filter((a) => a.askId !== ask.askId)].slice(0, 20),
        };
      }

      const existingIndex = state.activeAsks.findIndex((a) => a.askId === ask.askId);
      if (existingIndex >= 0) {
        const updated = [...state.activeAsks];
        updated[existingIndex] = ask;
        return { activeAsks: updated };
      }
      return { activeAsks: [ask, ...state.activeAsks] };
    }),
  setInitialData: (active, recent) => set({ activeAsks: active, recentAsks: recent }),
}));
```

**Step 2: Handle activity messages in websocket.ts**

In `handleMessage()`, add a case for `'activity'`:

```typescript
case 'activity':
  useActivityStore.getState().updateAsk(message as ActivityAsk);
  break;
```

Add import for `useActivityStore` at top.

**Step 3: Add API endpoints to client.ts**

```typescript
export const activityApi = {
  getActivity: () => apiClient.get<{ activeAsks: ActivityAsk[]; recentAsks: ActivityAsk[] }>('/api/agent/activity'),
  cancelAsk: (askId: string) => apiClient.post<void>(`/api/agent/asks/${askId}/cancel`, {}),
};

export const devicesApi = {
  list: () => apiClient.get<DeviceStatusResponse[]>('/api/devices'),
};
```

**Step 4: Create useActivity hook**

```typescript
// src/hooks/useActivity.ts
import { useQuery } from '@tanstack/react-query';
import { activityApi } from '@/src/api/client';
import { useActivityStore } from '@/src/stores/activity-store';
import { useEffect } from 'react';

export function useActivity() {
  const setInitialData = useActivityStore((s) => s.setInitialData);
  const query = useQuery({
    queryKey: ['activity'],
    queryFn: activityApi.getActivity,
    refetchInterval: 30000,
  });

  useEffect(() => {
    if (query.data) {
      setInitialData(query.data.activeAsks, query.data.recentAsks);
    }
  }, [query.data]);

  return query;
}
```

**Step 5: Create useDevices hook**

```typescript
// src/hooks/useDevices.ts
import { useQuery } from '@tanstack/react-query';
import { devicesApi } from '@/src/api/client';

export function useDevices() {
  return useQuery({
    queryKey: ['devices'],
    queryFn: devicesApi.list,
  });
}
```

**Step 6: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-mobile
git add -A
git commit -m "feat: Add activity store, API hooks, and WebSocket activity handling"
```

---

### Task 14: Build the activity dashboard Home screen

**Files:**
- Rewrite: `tacticl-mobile/app/(tabs)/index.tsx`

**Step 1: Rewrite Home screen as activity dashboard**

Full rewrite of `index.tsx` with:
- Status row (devices online, active asks, commands in flight)
- Active ask cards (expandable with tasks and commands)
- Recent asks (collapsed)
- Pull-to-refresh

The screen reads from `useActivityStore` for real-time updates and uses `useActivity()` for initial data load.

Each ask card shows:
- State chip (color-coded)
- Primary device name
- Original command text
- Elapsed time
- Expandable task list with nested command states

Use React Native Paper components: `Card`, `Chip`, `Text`, `Surface`, `IconButton`.

**Step 2: Test on simulator**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-mobile && npx expo start --clear
```

**Step 3: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-mobile
git add app/\(tabs\)/index.tsx
git commit -m "feat: Build activity dashboard with live ask/task/command pipeline"
```

---

## Phase 4: Electron Desktop Agent Scaffold

### Task 15: Create tacticl-desktop repo and scaffold

**Files:**
- Create: `/Users/cuztomizer/Documents/GitHub/tacticl-desktop/` (new repo)

**Step 1: Create repo directory**

```bash
mkdir -p /Users/cuztomizer/Documents/GitHub/tacticl-desktop
cd /Users/cuztomizer/Documents/GitHub/tacticl-desktop
git init
```

**Step 2: Initialize with package.json**

```json
{
  "name": "tacticl-desktop",
  "version": "0.1.0",
  "description": "Tacticl Desktop Agent — full OS automation via Electron",
  "main": "dist/main/index.js",
  "scripts": {
    "dev": "electron-vite dev",
    "build": "electron-vite build",
    "preview": "electron-vite preview",
    "package": "electron-builder"
  },
  "dependencies": {
    "electron-store": "^10.0.0"
  },
  "devDependencies": {
    "@electron-toolkit/tsconfig": "^1.0.1",
    "electron": "^33.0.0",
    "electron-builder": "^25.0.0",
    "electron-vite": "^2.3.0",
    "typescript": "^5.6.0",
    "react": "^18.3.0",
    "react-dom": "^18.3.0",
    "@types/react": "^18.3.0",
    "@types/react-dom": "^18.3.0"
  }
}
```

**Step 3: Create main process entry**

```typescript
// src/main/index.ts
import { app, BrowserWindow, ipcMain } from 'electron';
import { join } from 'path';
import { WebSocketClient } from './ws-client';
import { CommandExecutor } from './executor';
import { DeviceStore } from './store';

let mainWindow: BrowserWindow | null = null;
let wsClient: WebSocketClient | null = null;

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  // Load renderer
  if (process.env.ELECTRON_RENDERER_URL) {
    mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'));
  }
}

app.whenReady().then(() => {
  createWindow();

  // Initialize WebSocket after window is ready
  const store = new DeviceStore();
  const executor = new CommandExecutor();
  wsClient = new WebSocketClient(store, executor);

  // IPC handlers for renderer
  ipcMain.handle('get-device-id', () => store.getDeviceId());
  ipcMain.handle('get-connection-status', () => wsClient?.isConnected() ?? false);
  ipcMain.handle('connect', (_event, token: string) => wsClient?.connect(token));
  ipcMain.handle('disconnect', () => wsClient?.disconnect());
});

app.on('window-all-closed', () => {
  wsClient?.disconnect();
  app.quit();
});
```

**Step 4: Create WebSocket client**

```typescript
// src/main/ws-client.ts
import WebSocket from 'ws';
import { DeviceStore } from './store';
import { CommandExecutor } from './executor';

const API_BASE = 'wss://tacticl-core-1085580127767.us-east1.run.app';

export class WebSocketClient {
  private ws: WebSocket | null = null;
  private store: DeviceStore;
  private executor: CommandExecutor;
  private reconnectAttempt = 0;
  private heartbeatInterval: NodeJS.Timeout | null = null;

  constructor(store: DeviceStore, executor: CommandExecutor) {
    this.store = store;
    this.executor = executor;
  }

  connect(token: string): void {
    const deviceId = this.store.getDeviceId();
    const url = `${API_BASE}/ws/device?token=${token}&deviceId=${deviceId}`;

    this.ws = new WebSocket(url);
    this.ws.on('open', () => this.onOpen());
    this.ws.on('message', (data) => this.onMessage(data.toString()));
    this.ws.on('close', () => this.onClose());
    this.ws.on('error', (err) => console.error('[WS] Error:', err.message));
  }

  disconnect(): void {
    this.stopHeartbeat();
    this.ws?.close();
    this.ws = null;
  }

  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  private onOpen(): void {
    console.log('[WS] Connected');
    this.reconnectAttempt = 0;
    this.reportCapabilities();
    this.startHeartbeat();
  }

  private async onMessage(data: string): Promise<void> {
    try {
      const message = JSON.parse(data);
      switch (message.type) {
        case 'command':
          const result = await this.executor.execute(message);
          this.send({ type: 'result', ...result });
          break;
        case 'pong':
          break;
        case 'activity':
          // Forward to renderer via IPC
          break;
      }
    } catch (err) {
      console.error('[WS] Parse error:', err);
    }
  }

  private onClose(): void {
    console.log('[WS] Disconnected');
    this.stopHeartbeat();
    this.scheduleReconnect();
  }

  private send(data: object): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    }
  }

  private reportCapabilities(): void {
    this.send({
      type: 'capabilities',
      browser: { available: true },
      terminal: { available: true },
      shortcuts: { available: process.platform === 'darwin' },
      screen: { available: true },
      filesystem: { available: true },
    });
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatInterval = setInterval(() => {
      if (this.isConnected()) {
        this.send({ type: 'ping' });
      }
    }, 30000);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }

  private scheduleReconnect(): void {
    this.reconnectAttempt++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempt), 60000);
    setTimeout(() => {
      if (!this.isConnected()) {
        // Need token to reconnect — request from renderer
        console.log('[WS] Reconnect attempt', this.reconnectAttempt);
      }
    }, delay);
  }
}
```

**Step 5: Create command executor**

```typescript
// src/main/executor.ts
import { exec } from 'child_process';
import { desktopCapturer, shell } from 'electron';
import { promisify } from 'util';

const execAsync = promisify(exec);

interface CommandResult {
  commandId: string;
  success: boolean;
  message: string;
  data?: Record<string, unknown>;
}

export class CommandExecutor {
  async execute(command: { commandId: string; commandType: string; payload: Record<string, unknown> }): Promise<CommandResult> {
    const { commandId, commandType, payload } = command;

    try {
      switch (commandType) {
        case 'OPEN_URL':
          await shell.openExternal(payload.url as string);
          return { commandId, success: true, message: `Opened ${payload.url}` };

        case 'TAKE_SCREENSHOT':
          return await this.takeScreenshot(commandId);

        case 'LAUNCH_APP':
          return await this.launchApp(commandId, payload.appName as string);

        case 'RUN_SHORTCUT':
          return await this.runShortcut(commandId, payload.shortcutName as string);

        case 'TERMINAL_CMD':
          return await this.runTerminalCommand(commandId, payload.command as string);

        default:
          return { commandId, success: false, message: `Unknown command type: ${commandType}` };
      }
    } catch (err: any) {
      return { commandId, success: false, message: err.message };
    }
  }

  private async takeScreenshot(commandId: string): Promise<CommandResult> {
    if (process.platform === 'darwin') {
      const tmpPath = `/tmp/tacticl-screenshot-${Date.now()}.png`;
      await execAsync(`screencapture -x ${tmpPath}`);
      return { commandId, success: true, message: 'Screenshot captured', data: { path: tmpPath } };
    }
    return { commandId, success: false, message: 'Screenshots not yet supported on this platform' };
  }

  private async launchApp(commandId: string, appName: string): Promise<CommandResult> {
    if (process.platform === 'darwin') {
      await execAsync(`open -a "${appName}"`);
      return { commandId, success: true, message: `Launched ${appName}` };
    }
    return { commandId, success: false, message: 'App launch not yet supported on this platform' };
  }

  private async runShortcut(commandId: string, shortcutName: string): Promise<CommandResult> {
    if (process.platform === 'darwin') {
      const { stdout } = await execAsync(`shortcuts run "${shortcutName}"`);
      return { commandId, success: true, message: stdout || `Ran shortcut: ${shortcutName}` };
    }
    return { commandId, success: false, message: 'Shortcuts not supported on this platform' };
  }

  private async runTerminalCommand(commandId: string, command: string): Promise<CommandResult> {
    const { stdout, stderr } = await execAsync(command, { timeout: 30000 });
    return {
      commandId,
      success: true,
      message: stdout || stderr || 'Command completed',
      data: { stdout, stderr },
    };
  }
}
```

**Step 6: Create device store**

```typescript
// src/main/store.ts
import Store from 'electron-store';
import { randomUUID } from 'crypto';

interface StoreSchema {
  deviceId: string;
  deviceName: string;
  defaultDevice: boolean;
}

export class DeviceStore {
  private store: Store<StoreSchema>;

  constructor() {
    this.store = new Store<StoreSchema>({
      defaults: {
        deviceId: randomUUID(),
        deviceName: require('os').hostname(),
        defaultDevice: false,
      },
    });
  }

  getDeviceId(): string {
    return this.store.get('deviceId');
  }

  getDeviceName(): string {
    return this.store.get('deviceName');
  }

  isDefaultDevice(): boolean {
    return this.store.get('defaultDevice');
  }

  setDefaultDevice(isDefault: boolean): void {
    this.store.set('defaultDevice', isDefault);
  }
}
```

**Step 7: Create preload script**

```typescript
// src/preload/index.ts
import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('tacticl', {
  getDeviceId: () => ipcRenderer.invoke('get-device-id'),
  getConnectionStatus: () => ipcRenderer.invoke('get-connection-status'),
  connect: (token: string) => ipcRenderer.invoke('connect', token),
  disconnect: () => ipcRenderer.invoke('disconnect'),
  onActivity: (callback: (data: unknown) => void) => {
    ipcRenderer.on('activity', (_event, data) => callback(data));
  },
});
```

**Step 8: Create minimal renderer**

```typescript
// src/renderer/index.html
<!DOCTYPE html>
<html>
<head><title>Tacticl Desktop</title></head>
<body>
  <div id="root"></div>
  <script type="module" src="./main.tsx"></script>
</body>
</html>
```

```typescript
// src/renderer/main.tsx
import React from 'react';
import { createRoot } from 'react-dom/client';

function App() {
  return (
    <div style={{ padding: 24, fontFamily: 'system-ui', color: '#fff', background: '#121212', minHeight: '100vh' }}>
      <h1>Tacticl Desktop</h1>
      <p>Desktop agent is running. Connect via settings to start.</p>
    </div>
  );
}

createRoot(document.getElementById('root')!).render(<App />);
```

**Step 9: Add tsconfig, electron-builder config, and .gitignore**

**Step 10: Install dependencies and verify build**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-desktop
npm install
npm run build
```

**Step 11: Commit**

```bash
git add -A
git commit -m "feat: Scaffold Electron desktop agent with WebSocket, command executor, and device store"
```

---

## Phase 5: Deploy and Test End-to-End

### Task 16: Deploy backend to QA

**Step 1: Format code**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core && ./gradlew spotlessApply 2>&1 | tail -5
```

**Step 2: Build**

```bash
./gradlew build -x test --no-daemon 2>&1 | tail -10
```

**Step 3: Deploy**

```bash
gcloud builds submit --config deployment/cloudbuild/cloudbuild-qa.yaml --project=tacticl .
```

**Step 4: Verify endpoints**

```bash
curl -s https://tacticl-core-qa-*.run.app/api/agent/activity -H "Authorization: Bearer $TOKEN" | jq .
```

### Task 17: Test mobile app with new dashboard

**Step 1: Start Expo dev server**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-mobile && npx expo start --clear
```

**Step 2: Verify tabs display correctly**

Open in simulator — should see Home, Devices, History, Settings tabs.

**Step 3: Submit an ask and verify live updates**

Use the chat FAB to send a command. Verify the Home dashboard shows the ask with tasks and commands updating in real-time.

### Task 18: Test Electron desktop agent

**Step 1: Run in dev mode**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-desktop && npm run dev
```

**Step 2: Connect to backend**

Enter auth token in settings, verify WebSocket connects and capabilities are reported.

**Step 3: Send a command targeting the desktop**

From mobile, send an ask. Verify the desktop agent receives and executes the command (e.g., OPEN_URL).

---

## Summary

| Phase | Tasks | What it builds |
|-------|-------|---------------|
| 1 | Tasks 1-6 | Backend entities, enums, repositories |
| 2 | Tasks 7-11 | AskService, ActivityBroadcaster, REST endpoints, VoiceAgentService wiring |
| 3 | Tasks 12-14 | Mobile tab restructure, activity store, dashboard UI |
| 4 | Task 15 | Electron desktop agent scaffold |
| 5 | Tasks 16-18 | Deploy and end-to-end testing |
