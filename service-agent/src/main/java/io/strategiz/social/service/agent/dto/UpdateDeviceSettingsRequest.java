package io.strategiz.social.service.agent.dto;

/** Request DTO for updating device settings. All fields are optional (partial update). */
public class UpdateDeviceSettingsRequest {

	private Integer maxDaemons;

	private Boolean autoWake;

	private Integer priority;

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

}
