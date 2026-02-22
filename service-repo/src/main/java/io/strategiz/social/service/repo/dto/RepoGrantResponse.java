package io.strategiz.social.service.repo.dto;

import java.time.Instant;

/** Response DTO for a repo grant. */
public class RepoGrantResponse {

	private String id;

	private String provider;

	private String repoFullName;

	private String accessLevel;

	private Instant grantedAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

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

	public Instant getGrantedAt() {
		return grantedAt;
	}

	public void setGrantedAt(Instant grantedAt) {
		this.grantedAt = grantedAt;
	}

}
