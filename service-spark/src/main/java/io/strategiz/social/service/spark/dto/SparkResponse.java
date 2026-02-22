package io.strategiz.social.service.spark.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Response DTO for a spark. */
public class SparkResponse {

	private String id;

	private String title;

	private String description;

	private String type;

	private String status;

	private String priority;

	private String deviceId;

	private String schedule;

	private String checkpointPolicy;

	private List<String> repoAccess;

	private Map<String, Object> result;

	private String parentSparkId;

	private long totalTokens;

	private BigDecimal estimatedCost;

	private Instant createdAt;

	private Instant completedAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getPriority() {
		return priority;
	}

	public void setPriority(String priority) {
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

	public String getCheckpointPolicy() {
		return checkpointPolicy;
	}

	public void setCheckpointPolicy(String checkpointPolicy) {
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
