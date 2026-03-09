package io.strategiz.social.client.siliconflow.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.bucket4j.Bucket;
import io.cidadel.framework.exception.CidadelException;
import io.strategiz.social.client.siliconflow.config.SiliconFlowConfig;
import io.strategiz.social.client.siliconflow.dto.VideoGenerationResponse;
import io.strategiz.social.client.siliconflow.dto.VideoStatusResponse;
import io.strategiz.social.client.siliconflow.exception.SiliconFlowErrorDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Client for SiliconFlow API — Wan 2.2 video generation. */
public class SiliconFlowClient {

	private static final Logger logger = LoggerFactory.getLogger(SiliconFlowClient.class);

	private static final String MODULE_NAME = "client-siliconflow";

	private final SiliconFlowConfig config;

	private final Bucket rateLimiter;

	private final RestClient restClient;

	private final JsonMapper objectMapper;

	public SiliconFlowClient(SiliconFlowConfig config, Bucket rateLimiter) {
		this.config = config;
		this.rateLimiter = rateLimiter;
		this.objectMapper = new JsonMapper();
		this.restClient = RestClient.builder()
			.baseUrl(config.getBaseUrl())
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.build();
	}

	/** Submit a video generation request. Returns a request ID for status polling. */
	public VideoGenerationResponse generateVideo(String prompt, String model) {
		checkRateLimit();

		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("model", model != null ? model : "Wan-AI/Wan2.2-T2V-14B");
			body.put("prompt", prompt);
			body.put("image_size", "480x832");
			body.put("batch_size", 1);

			String responseBody = restClient.post()
				.uri("/v1/video/submit")
				.header("Authorization", "Bearer " + config.getApiKey())
				.body(objectMapper.writeValueAsString(body))
				.retrieve()
				.body(String.class);

			JsonNode root = objectMapper.readTree(responseBody);
			VideoGenerationResponse response = new VideoGenerationResponse();
			response.setRequestId(root.path("requestId").asText());
			response.setStatus(root.path("status").asText());
			return response;
		}
		catch (Exception e) {
			logger.error("Failed to submit video generation", e);
			throw new CidadelException(SiliconFlowErrorDetails.VIDEO_GENERATION_FAILED, MODULE_NAME, e.getMessage());
		}
	}

	/** Check the status of a video generation request. */
	public VideoStatusResponse checkStatus(String requestId) {
		checkRateLimit();

		try {
			String responseBody = restClient.get()
				.uri("/v1/video/status/{requestId}", requestId)
				.header("Authorization", "Bearer " + config.getApiKey())
				.retrieve()
				.body(String.class);

			JsonNode root = objectMapper.readTree(responseBody);
			VideoStatusResponse response = new VideoStatusResponse();
			response.setRequestId(requestId);
			response.setStatus(root.path("status").asText());

			if (root.has("results") && root.get("results").isArray() && !root.get("results").isEmpty()) {
				response.setVideoUrl(root.get("results").get(0).path("url").asText());
			}

			return response;
		}
		catch (Exception e) {
			logger.error("Failed to check video status", e);
			throw new CidadelException(SiliconFlowErrorDetails.STATUS_CHECK_FAILED, MODULE_NAME, e.getMessage());
		}
	}

	private void checkRateLimit() {
		if (!rateLimiter.tryConsume(1)) {
			throw new CidadelException(SiliconFlowErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
		}
	}

}
