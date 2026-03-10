package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Claude Code Agent SDK configuration, embedded in DeviceSettings for desktop devices. */
@IgnoreExtraProperties
public class ClaudeCodeConfig {

	private String model = "claude-opus-4-6";

	private int maxTurns = 25;

	private BigDecimal maxBudgetUsd = new BigDecimal("5.00");

	private List<String> allowedTools;

	private List<String> disallowedTools;

	private Map<String, Object> mcpServers;

	private String permissionMode = "acceptEdits";

	private String systemPromptOverride;

	public ClaudeCodeConfig() {}

	public static ClaudeCodeConfig defaults() {
		return new ClaudeCodeConfig();
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getMaxTurns() {
		return maxTurns;
	}

	public void setMaxTurns(int maxTurns) {
		this.maxTurns = maxTurns;
	}

	public BigDecimal getMaxBudgetUsd() {
		return maxBudgetUsd;
	}

	public void setMaxBudgetUsd(BigDecimal maxBudgetUsd) {
		this.maxBudgetUsd = maxBudgetUsd;
	}

	public List<String> getAllowedTools() {
		return allowedTools;
	}

	public void setAllowedTools(List<String> allowedTools) {
		this.allowedTools = allowedTools;
	}

	public List<String> getDisallowedTools() {
		return disallowedTools;
	}

	public void setDisallowedTools(List<String> disallowedTools) {
		this.disallowedTools = disallowedTools;
	}

	public Map<String, Object> getMcpServers() {
		return mcpServers;
	}

	public void setMcpServers(Map<String, Object> mcpServers) {
		this.mcpServers = mcpServers;
	}

	public String getPermissionMode() {
		return permissionMode;
	}

	public void setPermissionMode(String permissionMode) {
		this.permissionMode = permissionMode;
	}

	public String getSystemPromptOverride() {
		return systemPromptOverride;
	}

	public void setSystemPromptOverride(String systemPromptOverride) {
		this.systemPromptOverride = systemPromptOverride;
	}

}
