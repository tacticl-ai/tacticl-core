package io.strategiz.social.service.agent.dto;

import java.util.Map;

/** Response DTO for device settings. */
public class DeviceSettingsResponse {

	private String deviceId;

	private String deviceName;

	private int maxDaemons;

	private boolean autoWake;

	private int priority;

	private String executionEngine;

	private Map<String, Object> claudeCodeConfig;

	private String claudeCodeVersion;

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	public int getMaxDaemons() {
		return maxDaemons;
	}

	public void setMaxDaemons(int maxDaemons) {
		this.maxDaemons = maxDaemons;
	}

	public boolean isAutoWake() {
		return autoWake;
	}

	public void setAutoWake(boolean autoWake) {
		this.autoWake = autoWake;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
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

	public String getClaudeCodeVersion() {
		return claudeCodeVersion;
	}

	public void setClaudeCodeVersion(String claudeCodeVersion) {
		this.claudeCodeVersion = claudeCodeVersion;
	}

}
