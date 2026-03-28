package io.strategiz.social.service.agent.dto;

import java.util.List;

/** Response DTO for voice agent command result. */
public class AgentCommandResponse {

	private String responseText;

	private List<String> toolsInvoked;

	private boolean success;

	private String model;

	private boolean delegated;

	private String deviceName;

	private String sparkId;

	private String sparkStatus;

	private List<AgentAction> actions;

	// --- Pipeline fields (null for non-pipeline SIMPLE responses) ---

	/** The PDLC pipeline run ID, present only when executionMode is PIPELINE. */
	private String pipelineRunId;

	/**
	 * The pipeline tier that was selected: SIMPLE, PLAYBOOK, or FULL_PDLC.
	 * Always present after classification; null only for device-delegated responses.
	 */
	private String pipelineTier;

	/** The playbook name executed (e.g. "BUG_FIX", "FULL_PDLC"); null for SIMPLE. */
	private String playbook;

	/** Ordered list of role names activated in this pipeline run; null for SIMPLE. */
	private List<String> activatedRoles;

	/**
	 * How the command was executed:
	 * <ul>
	 *   <li>SYNC — synchronous cloud execution via CloudOrchestratorService</li>
	 *   <li>PIPELINE — async PDLC pipeline (PLAYBOOK or FULL_PDLC tier)</li>
	 *   <li>DEVICE — delegated to a connected device</li>
	 * </ul>
	 */
	private String executionMode;

	public AgentCommandResponse() {
	}

	public AgentCommandResponse(String responseText, List<String> toolsInvoked, boolean success, String model) {
		this.responseText = responseText;
		this.toolsInvoked = toolsInvoked;
		this.success = success;
		this.model = model;
	}

	/** Create a delegated response (command routed to a device). */
	public static AgentCommandResponse delegated(String sparkId, String sparkStatus, String deviceName) {
		AgentCommandResponse resp = new AgentCommandResponse();
		resp.responseText = "Routing to " + deviceName + "...";
		resp.toolsInvoked = List.of();
		resp.success = true;
		resp.delegated = true;
		resp.sparkId = sparkId;
		resp.sparkStatus = sparkStatus;
		resp.deviceName = deviceName;
		resp.executionMode = "DEVICE";
		return resp;
	}

	/** Create a pipeline response (async PDLC execution dispatched). */
	public static AgentCommandResponse pipeline(String sparkId, String pipelineRunId, String pipelineTier,
			String playbook, List<String> activatedRoles) {
		AgentCommandResponse resp = new AgentCommandResponse();
		resp.responseText = "Pipeline started (" + pipelineTier + "). Tracking spark " + sparkId + ".";
		resp.toolsInvoked = List.of();
		resp.success = true;
		resp.sparkId = sparkId;
		resp.pipelineRunId = pipelineRunId;
		resp.pipelineTier = pipelineTier;
		resp.playbook = playbook;
		resp.activatedRoles = activatedRoles;
		resp.executionMode = "PIPELINE";
		return resp;
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

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public boolean isDelegated() {
		return delegated;
	}

	public void setDelegated(boolean delegated) {
		this.delegated = delegated;
	}

	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
	}

	public String getSparkStatus() {
		return sparkStatus;
	}

	public void setSparkStatus(String sparkStatus) {
		this.sparkStatus = sparkStatus;
	}

	public List<AgentAction> getActions() {
		return actions;
	}

	public void setActions(List<AgentAction> actions) {
		this.actions = actions;
	}

	public String getPipelineRunId() {
		return pipelineRunId;
	}

	public void setPipelineRunId(String pipelineRunId) {
		this.pipelineRunId = pipelineRunId;
	}

	public String getPipelineTier() {
		return pipelineTier;
	}

	public void setPipelineTier(String pipelineTier) {
		this.pipelineTier = pipelineTier;
	}

	public String getPlaybook() {
		return playbook;
	}

	public void setPlaybook(String playbook) {
		this.playbook = playbook;
	}

	public List<String> getActivatedRoles() {
		return activatedRoles;
	}

	public void setActivatedRoles(List<String> activatedRoles) {
		this.activatedRoles = activatedRoles;
	}

	public String getExecutionMode() {
		return executionMode;
	}

	public void setExecutionMode(String executionMode) {
		this.executionMode = executionMode;
	}

}
