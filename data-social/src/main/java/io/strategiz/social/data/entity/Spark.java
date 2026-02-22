package io.strategiz.social.data.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/**
 * Represents a user's raw input request. Primary user-facing entity. A spark is routed to a device
 * and decomposed into one or more tactics.
 */
@IgnoreExtraProperties
public class Spark {

	private String id;

	private String userId;

	private String title;

	private String description;

	private String type;

	private SparkState status;

	private SparkPriority priority;

	private String deviceId;

	private String schedule;

	private Instant nextRunAt;

	private CheckpointPolicy checkpointPolicy;

	private List<String> repoAccess;

	private Map<String, Object> result;

	private String parentSparkId;

	private long totalTokens;

	private BigDecimal estimatedCost;

	private boolean isActive;

	private Instant createdAt;

	private Instant completedAt;

	public Spark() {
		this.status = SparkState.PENDING;
		this.priority = SparkPriority.NORMAL;
		this.checkpointPolicy = CheckpointPolicy.CHECKPOINT_MAJOR;
		this.repoAccess = new ArrayList<>();
		this.totalTokens = 0;
		this.estimatedCost = BigDecimal.ZERO;
		this.isActive = true;
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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public SparkState getStatus() {
		return status;
	}

	public void setStatus(SparkState status) {
		this.status = status;
	}

	public SparkPriority getPriority() {
		return priority;
	}

	public void setPriority(SparkPriority priority) {
		this.priority = priority;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getSchedule() {
		return schedule;
	}

	public void setSchedule(String schedule) {
		this.schedule = schedule;
	}

	public Instant getNextRunAt() {
		return nextRunAt;
	}

	public void setNextRunAt(Instant nextRunAt) {
		this.nextRunAt = nextRunAt;
	}

	public CheckpointPolicy getCheckpointPolicy() {
		return checkpointPolicy;
	}

	public void setCheckpointPolicy(CheckpointPolicy checkpointPolicy) {
		this.checkpointPolicy = checkpointPolicy;
	}

	public List<String> getRepoAccess() {
		return repoAccess;
	}

	public void setRepoAccess(List<String> repoAccess) {
		this.repoAccess = repoAccess;
	}

	public Map<String, Object> getResult() {
		return result;
	}

	public void setResult(Map<String, Object> result) {
		this.result = result;
	}

	public String getParentSparkId() {
		return parentSparkId;
	}

	public void setParentSparkId(String parentSparkId) {
		this.parentSparkId = parentSparkId;
	}

	public long getTotalTokens() {
		return totalTokens;
	}

	public void setTotalTokens(long totalTokens) {
		this.totalTokens = totalTokens;
	}

	public BigDecimal getEstimatedCost() {
		return estimatedCost;
	}

	public void setEstimatedCost(BigDecimal estimatedCost) {
		this.estimatedCost = estimatedCost;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean active) {
		isActive = active;
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
