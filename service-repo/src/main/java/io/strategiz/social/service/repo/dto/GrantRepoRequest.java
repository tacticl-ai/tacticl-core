package io.strategiz.social.service.repo.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for granting repo access. */
public class GrantRepoRequest {

	@NotBlank(message = "Provider is required")
	private String provider;

	@NotBlank(message = "Repository full name is required")
	private String repoFullName;

	private String accessLevel;

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getRepoFullName() {
		return repoFullName;
	}

	public void setRepoFullName(String repoFullName) {
		this.repoFullName = repoFullName;
	}

	public String getAccessLevel() {
		return accessLevel;
	}

	public void setAccessLevel(String accessLevel) {
		this.accessLevel = accessLevel;
	}

}
