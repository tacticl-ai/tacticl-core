package io.strategiz.social.data.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/**
 * Represents a user's top-level request to the agent. An ask decomposes into one or more tasks,
 * each worked by an agent instance. All tasks within an ask target the same primary device.
 */
@IgnoreExtraProperties
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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	public String getCommandText() {
		return commandText;
	}

	public void setCommandText(String commandText) {
		this.commandText = commandText;
	}

	public AskState getState() {
		return state;
	}

	public void setState(AskState state) {
		this.state = state;
	}

	public List<String> getTaskIds() {
		return taskIds;
	}

	public void setTaskIds(List<String> taskIds) {
		this.taskIds = taskIds;
	}

	public int getTotalTokens() {
		return totalTokens;
	}

	public void setTotalTokens(int totalTokens) {
		this.totalTokens = totalTokens;
	}

	public BigDecimal getEstimatedCost() {
		return estimatedCost;
	}

	public void setEstimatedCost(BigDecimal estimatedCost) {
		this.estimatedCost = estimatedCost;
	}

	public boolean isDeviceFallbackEnabled() {
		return deviceFallbackEnabled;
	}

	public void setDeviceFallbackEnabled(boolean deviceFallbackEnabled) {
		this.deviceFallbackEnabled = deviceFallbackEnabled;
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
