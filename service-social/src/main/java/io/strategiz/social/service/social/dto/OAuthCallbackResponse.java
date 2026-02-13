package io.strategiz.social.service.social.dto;

/** Response DTO for a successful OAuth callback token exchange. */
public class OAuthCallbackResponse {

	private String integrationId;

	private String platform;

	private boolean success;

	public OAuthCallbackResponse(String integrationId, String platform, boolean success) {
		this.integrationId = integrationId;
		this.platform = platform;
		this.success = success;
	}

	public String getIntegrationId() {
		return integrationId;
	}

	public void setIntegrationId(String integrationId) {
		this.integrationId = integrationId;
	}

	public String getPlatform() {
		return platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

}
