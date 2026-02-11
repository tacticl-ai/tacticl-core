package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a connected social media account (OAuth integration).
 */
public class SocialIntegration {

	private String id;

	private String userId;

	private String workspaceId;

	private PlatformType platform;

	private String platformUserId;

	private String platformUsername;

	private String profileImageUrl;

	private String accessToken;

	private String refreshToken;

	private String tokenScope;

	private Instant tokenExpiresAt;

	private boolean tokenRefreshNeeded;

	private boolean isDisabled;

	private Map<String, Object> platformMetadata = new HashMap<>();

	private Instant createdAt;

	private Instant updatedAt;

	private boolean isActive = true;

	// Getters and setters

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getWorkspaceId() {
		return workspaceId;
	}

	public void setWorkspaceId(String workspaceId) {
		this.workspaceId = workspaceId;
	}

	public PlatformType getPlatform() {
		return platform;
	}

	public void setPlatform(PlatformType platform) {
		this.platform = platform;
	}

	public String getPlatformUserId() {
		return platformUserId;
	}

	public void setPlatformUserId(String platformUserId) {
		this.platformUserId = platformUserId;
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

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public String getTokenScope() {
		return tokenScope;
	}

	public void setTokenScope(String tokenScope) {
		this.tokenScope = tokenScope;
	}

	public Instant getTokenExpiresAt() {
		return tokenExpiresAt;
	}

	public void setTokenExpiresAt(Instant tokenExpiresAt) {
		this.tokenExpiresAt = tokenExpiresAt;
	}

	public boolean isTokenRefreshNeeded() {
		return tokenRefreshNeeded;
	}

	public void setTokenRefreshNeeded(boolean tokenRefreshNeeded) {
		this.tokenRefreshNeeded = tokenRefreshNeeded;
	}

	public boolean isDisabled() {
		return isDisabled;
	}

	public void setDisabled(boolean disabled) {
		isDisabled = disabled;
	}

	public Map<String, Object> getPlatformMetadata() {
		return platformMetadata;
	}

	public void setPlatformMetadata(Map<String, Object> platformMetadata) {
		this.platformMetadata = platformMetadata;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean active) {
		isActive = active;
	}

}
