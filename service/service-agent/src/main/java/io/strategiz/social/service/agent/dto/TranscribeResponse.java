package io.strategiz.social.service.agent.dto;

/** Response DTO for audio transcription. */
public class TranscribeResponse {

	private String text;

	public TranscribeResponse() {
	}

	public TranscribeResponse(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

}
