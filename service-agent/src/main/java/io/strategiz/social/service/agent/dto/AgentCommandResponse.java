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

}
