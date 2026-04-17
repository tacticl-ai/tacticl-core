package io.strategiz.social.service.agent.dto;

import java.time.Instant;
import java.util.Map;

/** Response DTO for device status and capabilities. */
public class DeviceStatusResponse {

	private String deviceId;

	private String deviceName;

	private String deviceType;

	private String state;

	private boolean online;

	private Map<String, Object> capabilities;

	private Map<String, Object> connectivity;

	private Instant lastSeenAt;

	private Instant createdAt;

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

	public Map<String, Object> getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(Map<String, Object> capabilities) {
		this.capabilities = capabilities;
	}

	public Map<String, Object> getConnectivity() {
		return connectivity;
	}

	public void setConnectivity(Map<String, Object> connectivity) {
		this.connectivity = connectivity;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(Instant lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
