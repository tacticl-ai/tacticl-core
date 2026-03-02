package io.strategiz.social.service.agent.dto;

import java.time.Instant;

/** Response DTO after registering a device. */
public class DeviceRegistrationResponse {

	private String deviceId;

	private String deviceName;

	private String deviceType;

	private String state;

	private boolean verificationRequired;

	private Instant createdAt;

	public DeviceRegistrationResponse() {
	}

	public DeviceRegistrationResponse(String deviceId, String deviceName, String deviceType, String state,
			boolean verificationRequired, Instant createdAt) {
		this.deviceId = deviceId;
		this.deviceName = deviceName;
		this.deviceType = deviceType;
		this.state = state;
		this.verificationRequired = verificationRequired;
		this.createdAt = createdAt;
	}

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

	public boolean isVerificationRequired() {
		return verificationRequired;
	}

	public void setVerificationRequired(boolean verificationRequired) {
		this.verificationRequired = verificationRequired;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
