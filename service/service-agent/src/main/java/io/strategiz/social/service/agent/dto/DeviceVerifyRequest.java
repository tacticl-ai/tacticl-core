package io.strategiz.social.service.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO for device verification with 6-digit code. */
public class DeviceVerifyRequest {

	@NotBlank(message = "Verification code is required")
	@Size(min = 6, max = 6, message = "Verification code must be 6 digits")
	private String code;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

}
