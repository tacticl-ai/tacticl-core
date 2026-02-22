package io.strategiz.social.service.spark.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** Request DTO for creating a new spark. */
public class CreateSparkRequest {

	@NotBlank(message = "Description is required")
	private String description;

	private String title;

	private String type;

	private String priority;

	private String checkpointPolicy;

	private List<String> repoAccess;

	private String schedule;

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getPriority() {
		return priority;
	}

	public void setPriority(String priority) {
		this.priority = priority;
	}

	public String getCheckpointPolicy() {
		return checkpointPolicy;
	}

	public void setCheckpointPolicy(String checkpointPolicy) {
		this.checkpointPolicy = checkpointPolicy;
	}

	public List<String> getRepoAccess() {
		return repoAccess;
	}

	public void setRepoAccess(List<String> repoAccess) {
		this.repoAccess = repoAccess;
	}

	public String getSchedule() {
		return schedule;
	}

	public void setSchedule(String schedule) {
		this.schedule = schedule;
	}

}
