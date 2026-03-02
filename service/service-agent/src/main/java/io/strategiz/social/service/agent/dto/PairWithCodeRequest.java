package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** Request DTO for pairing a device using a 6-digit code. */
public class PairWithCodeRequest {

	@NotBlank(message = "Pairing code is required")
	private String code;

	@NotBlank(message = "Device name is required")
	private String deviceName;

	@NotBlank(message = "Device type is required")
	private String deviceType;

	private String platform;

	private Map<String, Object> capabilities;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
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

	public String getPlatform() {
		return platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public Map<String, Object> getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(Map<String, Object> capabilities) {
		this.capabilities = capabilities;
	}

}
