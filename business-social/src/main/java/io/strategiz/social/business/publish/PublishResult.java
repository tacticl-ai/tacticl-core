package io.strategiz.social.business.publish;

public class PublishResult {

	private boolean success;

	private String platformPostId;

	private String platformPostUrl;

	private String errorMessage;

	public static PublishResult success(String platformPostId, String platformPostUrl) {
		PublishResult result = new PublishResult();
		result.success = true;
		result.platformPostId = platformPostId;
		result.platformPostUrl = platformPostUrl;
		return result;
	}

	public static PublishResult failed(String errorMessage) {
		PublishResult result = new PublishResult();
		result.success = false;
		result.errorMessage = errorMessage;
		return result;
	}

	public boolean isSuccess() {
		return success;
	}

	public String getPlatformPostId() {
		return platformPostId;
	}

	public String getPlatformPostUrl() {
		return platformPostUrl;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

}
