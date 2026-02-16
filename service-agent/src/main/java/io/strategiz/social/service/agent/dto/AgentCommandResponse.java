package io.strategiz.social.service.agent.dto;

import java.util.List;

/** Response DTO for voice agent command result. */
public class AgentCommandResponse {

	private String responseText;

	private List<String> toolsInvoked;

	private boolean success;

	private String model;

	public AgentCommandResponse() {
	}

	public AgentCommandResponse(String responseText, List<String> toolsInvoked, boolean success, String model) {
		this.responseText = responseText;
		this.toolsInvoked = toolsInvoked;
		this.success = success;
		this.model = model;
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

}
