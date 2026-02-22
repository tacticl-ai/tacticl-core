package io.strategiz.social.service.checkpoint.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for deciding on a checkpoint. */
public class DecideCheckpointRequest {

	@NotBlank(message = "Decision is required")
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
