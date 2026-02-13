package io.strategiz.social.client.siliconflow.dto;

/** Response from video generation submission. */
public class VideoGenerationResponse {

	private String requestId;

	private String status;

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}
