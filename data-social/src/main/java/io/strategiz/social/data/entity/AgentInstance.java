package io.strategiz.social.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/**
 * Represents an LLM agent instance working on a specific task. Tracks the model used, token
 * consumption, and lifecycle state.
 */
@IgnoreExtraProperties
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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
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

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getModelId() {
		return modelId;
	}

	public void setModelId(String modelId) {
		this.modelId = modelId;
	}

	public AgentInstanceState getState() {
		return state;
	}

	public void setState(AgentInstanceState state) {
		this.state = state;
	}

	public int getTokenCount() {
		return tokenCount;
	}

	public void setTokenCount(int tokenCount) {
		this.tokenCount = tokenCount;
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
