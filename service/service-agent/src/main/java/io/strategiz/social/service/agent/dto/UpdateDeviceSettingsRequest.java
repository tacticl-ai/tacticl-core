package io.strategiz.social.service.agent.dto;

import java.util.Map;

/** Request DTO for updating device settings. All fields are optional (partial update). */
public class UpdateDeviceSettingsRequest {

	private Integer maxDaemons;

	private Boolean autoWake;

	private Integer priority;

	private String executionEngine;

	private Map<String, Object> claudeCodeConfig;

	public Integer getMaxDaemons() {
		return maxDaemons;
	}

	public void setMaxDaemons(Integer maxDaemons) {
		this.maxDaemons = maxDaemons;
	}

	public Boolean getAutoWake() {
		return autoWake;
	}

	public void setAutoWake(Boolean autoWake) {
		this.autoWake = autoWake;
	}

	public Integer getPriority() {
		return priority;
	}

	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	public String getExecutionEngine() {
		return executionEngine;
	}

	public void setExecutionEngine(String executionEngine) {
		this.executionEngine = executionEngine;
	}

	public Map<String, Object> getClaudeCodeConfig() {
		return claudeCodeConfig;
	}

	public void setClaudeCodeConfig(Map<String, Object> claudeCodeConfig) {
		this.claudeCodeConfig = claudeCodeConfig;
	}

}
