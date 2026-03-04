package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.identity.data.base.annotation.Collection;
import io.cidadel.identity.data.base.entity.BaseEntity;

/** Records a single tool call during agent execution for audit and debugging. */
@IgnoreExtraProperties
@Collection("execution_logs")
public class ExecutionLog extends BaseEntity {

	private String id;

	private String sparkId;

	private String tacticId;

	private String userId;

	private String toolName;

	private Map<String, Object> toolInput;

	private Map<String, Object> toolOutput;

	private Map<String, Object> tokenUsage;

	private long durationMs;

	private Instant timestamp;

	public ExecutionLog() {
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
	}

	public String getTacticId() {
		return tacticId;
	}

	public void setTacticId(String tacticId) {
		this.tacticId = tacticId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getToolName() {
		return toolName;
	}

	public void setToolName(String toolName) {
		this.toolName = toolName;
	}

	public Map<String, Object> getToolInput() {
		return toolInput;
	}

	public void setToolInput(Map<String, Object> toolInput) {
		this.toolInput = toolInput;
	}

	public Map<String, Object> getToolOutput() {
		return toolOutput;
	}

	public void setToolOutput(Map<String, Object> toolOutput) {
		this.toolOutput = toolOutput;
	}

	public Map<String, Object> getTokenUsage() {
		return tokenUsage;
	}

	public void setTokenUsage(Map<String, Object> tokenUsage) {
		this.tokenUsage = tokenUsage;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

}
