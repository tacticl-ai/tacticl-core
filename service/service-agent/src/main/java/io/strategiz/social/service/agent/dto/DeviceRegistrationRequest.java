package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request DTO for registering a new device. */
public class DeviceRegistrationRequest {

	@NotBlank(message = "Device name is required")
	private String deviceName;

	@NotNull(message = "Device type is required")
	private String deviceType;

	private String pushToken;

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

	public String getPushToken() {
		return pushToken;
	}

	public void setPushToken(String pushToken) {
		this.pushToken = pushToken;
	}

}
