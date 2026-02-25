package io.strategiz.social.service.agent.dto;

/** Response DTO after a successful device pairing. */
public class PairResponse {

	private String deviceId;

	private String sessionToken;

	public PairResponse() {
	}

	public PairResponse(String deviceId, String sessionToken) {
		this.deviceId = deviceId;
		this.sessionToken = sessionToken;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getSessionToken() {
		return sessionToken;
	}

	public void setSessionToken(String sessionToken) {
		this.sessionToken = sessionToken;
	}

}
