package io.strategiz.social.service.agent.dto;

import java.time.Instant;

/** Response DTO for credential information. Does NOT include tokens for security. */
public class CredentialResponse {

	private String platform;

	private String username;

	private String platformUserId;

	private boolean connected;

	private boolean tokenRefreshNeeded;

	private Instant updatedAt;

	public String getPlatform() {
		return platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPlatformUserId() {
		return platformUserId;
	}

	public void setPlatformUserId(String platformUserId) {
		this.platformUserId = platformUserId;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public boolean isTokenRefreshNeeded() {
		return tokenRefreshNeeded;
	}

	public void setTokenRefreshNeeded(boolean tokenRefreshNeeded) {
		this.tokenRefreshNeeded = tokenRefreshNeeded;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
