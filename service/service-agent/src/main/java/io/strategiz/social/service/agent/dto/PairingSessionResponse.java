package io.strategiz.social.service.agent.dto;

/** Response DTO after creating a QR pairing session. */
public class PairingSessionResponse {

	private String sessionId;

	private String secret;

	private int expiresIn;

	public PairingSessionResponse() {
	}

	public PairingSessionResponse(String sessionId, String secret, int expiresIn) {
		this.sessionId = sessionId;
		this.secret = secret;
		this.expiresIn = expiresIn;
	}

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

	public int getExpiresIn() {
		return expiresIn;
	}

	public void setExpiresIn(int expiresIn) {
		this.expiresIn = expiresIn;
	}

}
