package io.strategiz.social.service.spark.dto;

import java.util.List;

/** Request DTO for updating an existing spark. */
public class UpdateSparkRequest {

	private String title;

	private String description;

	private String priority;

	private String checkpointPolicy;

	private List<String> repoAccess;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
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

}
