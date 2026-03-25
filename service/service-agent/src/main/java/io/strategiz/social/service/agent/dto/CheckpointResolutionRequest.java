package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request body for resolving a pipeline checkpoint. */
public class CheckpointResolutionRequest {

	@NotBlank
	@Pattern(regexp = "APPROVED|REJECTED|MODIFIED", message = "decision must be APPROVED, REJECTED, or MODIFIED")
	private String decision;

	private String feedback;

	public String getDecision() {
		return decision;
	}

	public void setDecision(String decision) {
		this.decision = decision;
	}

	public String getFeedback() {
		return feedback;
	}

	public void setFeedback(String feedback) {
		this.feedback = feedback;
	}

}
