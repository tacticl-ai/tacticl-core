package io.strategiz.social.service.agent.dto;

/** Response DTO after generating a device pairing code. */
public class PairingCodeResponse {

	private String code;

	private int expiresIn;

	public PairingCodeResponse() {
	}

	public PairingCodeResponse(String code, int expiresIn) {
		this.code = code;
		this.expiresIn = expiresIn;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public int getExpiresIn() {
		return expiresIn;
	}

	public void setExpiresIn(int expiresIn) {
		this.expiresIn = expiresIn;
	}

}
