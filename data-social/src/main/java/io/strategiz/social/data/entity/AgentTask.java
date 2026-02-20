package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/**
 * Represents a unit of work within an ask. Each task is assigned to an agent instance and produces
 * one or more device commands.
 */
@IgnoreExtraProperties
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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getAskId() {
		return askId;
	}

	public void setAskId(String askId) {
		this.askId = askId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getAgentId() {
		return agentId;
	}

	public void setAgentId(String agentId) {
		this.agentId = agentId;
	}

	public AgentTaskState getState() {
		return state;
	}

	public void setState(AgentTaskState state) {
		this.state = state;
	}

	public List<String> getCommandIds() {
		return commandIds;
	}

	public void setCommandIds(List<String> commandIds) {
		this.commandIds = commandIds;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

}
