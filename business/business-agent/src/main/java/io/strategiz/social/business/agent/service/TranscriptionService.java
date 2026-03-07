package io.strategiz.social.business.agent.service;

import io.cidadel.client.openai.config.OpenAiDirectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Transcribes audio files using OpenAI's Whisper API. Acts as a backend proxy so
 * mobile clients don't need API keys.
 */
@Service
public class TranscriptionService {

	private static final Logger logger = LoggerFactory.getLogger(TranscriptionService.class);

	private static final String WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions";

	private static final String DEFAULT_MODEL = "whisper-1";

	private final OpenAiDirectConfig openAiConfig;

	private final RestClient restClient;

	public TranscriptionService(OpenAiDirectConfig openAiConfig) {
		this.openAiConfig = openAiConfig;
		this.restClient = RestClient.create();
	}

	/**
	 * Transcribe audio bytes to text using OpenAI Whisper.
	 * @param audioBytes the raw audio file bytes
	 * @param filename the original filename (used for content type detection)
	 * @return the transcribed text
	 */
	public String transcribe(byte[] audioBytes, String filename) {
		if (openAiConfig.getApiKey() == null || openAiConfig.getApiKey().isBlank()) {
			throw new IllegalStateException("OpenAI API key not configured — cannot transcribe audio");
		}

		logger.info("Transcribing audio: {} ({} bytes)", filename, audioBytes.length);

		ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		};

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", audioResource);
		body.add("model", DEFAULT_MODEL);

		WhisperResponse response = restClient.post()
			.uri(WHISPER_URL)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiConfig.getApiKey())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(body)
			.retrieve()
			.body(WhisperResponse.class);

		if (response == null || response.getText() == null) {
			throw new RuntimeException("Whisper API returned empty response");
		}

		logger.info("Transcription complete: {} chars", response.getText().length());
		return response.getText();
	}

	/** Response DTO for the OpenAI Whisper API. */
	private static class WhisperResponse {

		private String text;

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

	}

}
