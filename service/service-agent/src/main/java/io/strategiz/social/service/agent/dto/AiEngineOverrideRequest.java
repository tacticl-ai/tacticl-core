package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for setting a role-level AI engine override via the admin console. */
public class AiEngineOverrideRequest {

	@NotBlank(message = "engineId is required")
	private String engineId;

	@NotBlank(message = "model is required")
	private String model;

	public String getEngineId() {
		return engineId;
	}

	public void setEngineId(String engineId) {
		this.engineId = engineId;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

}
