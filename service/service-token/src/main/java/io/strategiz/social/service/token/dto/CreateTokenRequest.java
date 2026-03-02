package io.strategiz.social.service.token.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** Request DTO for adding a new agent token. */
public class CreateTokenRequest {

	@NotBlank(message = "Provider is required")
	private String provider;

	@NotBlank(message = "Label is required")
	private String label;

	@NotBlank(message = "Token value is required")
	private String tokenValue;

	private Map<String, Object> usageLimits;

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getTokenValue() {
		return tokenValue;
	}

	public void setTokenValue(String tokenValue) {
		this.tokenValue = tokenValue;
	}

	public Map<String, Object> getUsageLimits() {
		return usageLimits;
	}

	public void setUsageLimits(Map<String, Object> usageLimits) {
		this.usageLimits = usageLimits;
	}

}
