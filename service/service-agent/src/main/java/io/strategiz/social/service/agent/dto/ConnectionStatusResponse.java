package io.strategiz.social.service.agent.dto;

import java.util.List;

/** Aggregated response DTO for all user connections (devices, integrations, repos). */
public class ConnectionStatusResponse {

	private List<DeviceSummary> devices;

	private List<IntegrationSummary> integrations;

	private List<RepoSummary> repos;

	public List<DeviceSummary> getDevices() {
		return devices;
	}

	public void setDevices(List<DeviceSummary> devices) {
		this.devices = devices;
	}

	public List<IntegrationSummary> getIntegrations() {
		return integrations;
	}

	public void setIntegrations(List<IntegrationSummary> integrations) {
		this.integrations = integrations;
	}

	public List<RepoSummary> getRepos() {
		return repos;
	}

	public void setRepos(List<RepoSummary> repos) {
		this.repos = repos;
	}

	/** Summary of a connected device. */
	public static class DeviceSummary {

		private String deviceId;

		private String deviceName;

		private String deviceType;

		private String state;

		private boolean online;

		public String getDeviceId() {
			return deviceId;
		}

		public void setDeviceId(String deviceId) {
			this.deviceId = deviceId;
		}

		public String getDeviceName() {
			return deviceName;
		}

		public void setDeviceName(String deviceName) {
			this.deviceName = deviceName;
		}

		public String getDeviceType() {
			return deviceType;
		}

		public void setDeviceType(String deviceType) {
			this.deviceType = deviceType;
		}

		public String getState() {
			return state;
		}

		public void setState(String state) {
			this.state = state;
		}

		public boolean isOnline() {
			return online;
		}

		public void setOnline(boolean online) {
			this.online = online;
		}

	}

	/** Summary of a connected social integration. */
	public static class IntegrationSummary {

		private String platform;

		private String platformUsername;

		private boolean tokenRefreshNeeded;

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

		public boolean isTokenRefreshNeeded() {
			return tokenRefreshNeeded;
		}

		public void setTokenRefreshNeeded(boolean tokenRefreshNeeded) {
			this.tokenRefreshNeeded = tokenRefreshNeeded;
		}

	}

	/** Summary of a granted repository. */
	public static class RepoSummary {

		private String id;

		private String provider;

		private String repoFullName;

		private String accessLevel;

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

	}

}
