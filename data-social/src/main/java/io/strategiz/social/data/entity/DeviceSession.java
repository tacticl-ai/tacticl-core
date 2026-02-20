package io.strategiz.social.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/**
 * Tracks an active WebSocket session for a device. Used to determine which devices are currently
 * online and reachable.
 */
@IgnoreExtraProperties
public class DeviceSession {

	private String id;

	private String deviceId;

	private String userId;

	private Instant connectedAt;

	private Instant lastPingAt;

	private boolean isActive;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Instant getConnectedAt() {
		return connectedAt;
	}

	public void setConnectedAt(Instant connectedAt) {
		this.connectedAt = connectedAt;
	}

	public Instant getLastPingAt() {
		return lastPingAt;
	}

	public void setLastPingAt(Instant lastPingAt) {
		this.lastPingAt = lastPingAt;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean active) {
		isActive = active;
	}

}
