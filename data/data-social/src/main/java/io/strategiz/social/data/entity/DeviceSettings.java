package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Per-device configuration embedded in the DeviceRegistration document. */
@IgnoreExtraProperties
public class DeviceSettings {

	private int maxDaemons = 1;

	private boolean autoWake = false;

	private int priority = 0;

	private ExecutionEngine executionEngine = ExecutionEngine.CLAUDE_CODE;

	private ClaudeCodeConfig claudeCodeConfig;

	public DeviceSettings() {}

	public static DeviceSettings defaults() {
		return new DeviceSettings();
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

	public ExecutionEngine getExecutionEngine() {
		return executionEngine;
	}

	public void setExecutionEngine(ExecutionEngine executionEngine) {
		this.executionEngine = executionEngine;
	}

	public ClaudeCodeConfig getClaudeCodeConfig() {
		return claudeCodeConfig;
	}

	public void setClaudeCodeConfig(ClaudeCodeConfig claudeCodeConfig) {
		this.claudeCodeConfig = claudeCodeConfig;
	}

}
