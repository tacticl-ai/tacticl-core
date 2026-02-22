package io.strategiz.social.service.token.dto;

import java.util.Map;

/** Response DTO for an agent token (never exposes actual token value). */
public class TokenResponse {

	private String id;

	private String provider;

	private String label;

	private Map<String, Object> usageLimits;

	private Map<String, Object> currentUsage;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

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

	public Map<String, Object> getUsageLimits() {
		return usageLimits;
	}

	public void setUsageLimits(Map<String, Object> usageLimits) {
		this.usageLimits = usageLimits;
	}

	public Map<String, Object> getCurrentUsage() {
		return currentUsage;
	}

	public void setCurrentUsage(Map<String, Object> currentUsage) {
		this.currentUsage = currentUsage;
	}

}
