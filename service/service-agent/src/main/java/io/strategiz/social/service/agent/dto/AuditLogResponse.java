package io.strategiz.social.service.agent.dto;

import java.time.Instant;
import java.util.List;

/** Response DTO for agent audit log entries. */
public class AuditLogResponse {

	private String id;

	private String commandText;

	private String responseText;

	private List<String> toolsInvoked;

	private boolean success;

	private long executionTimeMs;

	private Instant createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCommandText() {
		return commandText;
	}

	public void setCommandText(String commandText) {
		this.commandText = commandText;
	}

	public String getResponseText() {
		return responseText;
	}

	public void setResponseText(String responseText) {
		this.responseText = responseText;
	}

	public List<String> getToolsInvoked() {
		return toolsInvoked;
	}

	public void setToolsInvoked(List<String> toolsInvoked) {
		this.toolsInvoked = toolsInvoked;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public long getExecutionTimeMs() {
		return executionTimeMs;
	}

	public void setExecutionTimeMs(long executionTimeMs) {
		this.executionTimeMs = executionTimeMs;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
