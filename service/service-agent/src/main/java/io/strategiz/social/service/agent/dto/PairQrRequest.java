package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** Request DTO for confirming a QR-based device pairing. */
public class PairQrRequest {

	@NotBlank(message = "Session ID is required")
	private String sessionId;

	@NotBlank(message = "Secret is required")
	private String secret;

	private String deviceName;

	private String deviceType;

	private String platform;

	private Map<String, Object> capabilities;

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
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
