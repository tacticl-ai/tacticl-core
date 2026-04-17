package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotNull;

/** Request DTO for confirming or denying a Tier 1/2 action. */
public class ConfirmActionRequest {

	@NotNull(message = "Approved flag is required")
	private Boolean approved;

	public Boolean getApproved() {
		return approved;
	}

	public void setApproved(Boolean approved) {
		this.approved = approved;
	}

}
