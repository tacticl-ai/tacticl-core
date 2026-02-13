package io.strategiz.social.service.social.dto;

import java.time.Instant;

/** Response DTO for a connected social media integration. */
public class IntegrationResponse {

	private String id;

	private String platform;

	private String platformUsername;

	private String profileImageUrl;

	private boolean disabled;

	private boolean tokenRefreshNeeded;

	private Instant tokenExpiresAt;

	private Instant createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPlatform() {
		return platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public String getPlatformUsername() {
		return platformUsername;
	}

	public void setPlatformUsername(String platformUsername) {
		this.platformUsername = platformUsername;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}

	public void setProfileImageUrl(String profileImageUrl) {
		this.profileImageUrl = profileImageUrl;
	}

	public boolean isDisabled() {
		return disabled;
	}

	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}

	public boolean isTokenRefreshNeeded() {
		return tokenRefreshNeeded;
	}

	public void setTokenRefreshNeeded(boolean tokenRefreshNeeded) {
		this.tokenRefreshNeeded = tokenRefreshNeeded;
	}

	public Instant getTokenExpiresAt() {
		return tokenExpiresAt;
	}

	public void setTokenExpiresAt(Instant tokenExpiresAt) {
		this.tokenExpiresAt = tokenExpiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
