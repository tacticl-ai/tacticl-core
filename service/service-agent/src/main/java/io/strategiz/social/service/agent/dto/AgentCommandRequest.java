package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for voice agent command. */
public class AgentCommandRequest {

	@NotBlank(message = "Command text is required")
	private String text;

	private String sessionId;

	private String timezone;

	private String model;

	private String sparkType;

	/** Optional user-specified playbook override (e.g. "BUG_FIX", "FULL_PDLC"). Null = use classifier. */
	private String playbook;

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getSparkType() {
		return sparkType;
	}

	public void setSparkType(String sparkType) {
		this.sparkType = sparkType;
	}

	public String getPlaybook() {
		return playbook;
	}

	public void setPlaybook(String playbook) {
		this.playbook = playbook;
	}

}
