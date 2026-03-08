package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.siliconflow.client.SiliconFlowClient;
import io.strategiz.social.client.siliconflow.dto.VideoStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Skill to check the status of a video generation request. Tier 0: auto-execute. */
@Component
@ConditionalOnProperty(name = "tacticl.siliconflow.enabled", havingValue = "true", matchIfMissing = false)
public class CheckVideoStatusSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(CheckVideoStatusSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final SiliconFlowClient siliconFlowClient;

	public CheckVideoStatusSkill(SiliconFlowClient siliconFlowClient) {
		this.siliconFlowClient = siliconFlowClient;
	}

	@Override
	public String getName() {
		return "check_video_status";
	}

	@Override
	public String getDescription() {
		return "Check the status of a previously submitted video generation request";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode requestId = properties.putObject("request_id");
		requestId.put("type", "string");
		requestId.put("description", "The request ID returned from a previous video generation request");

		schema.putArray("required").add("request_id");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String requestId = input.get("request_id").asText();
		log.info("Checking video status for user {}, request: {}", userId, requestId);

		try {
			VideoStatusResponse status = siliconFlowClient.checkStatus(requestId);

			if (status.getVideoUrl() != null && !status.getVideoUrl().isEmpty()) {
				return String.format("Video is ready! Status: %s. Download URL: %s", status.getStatus(),
						status.getVideoUrl());
			}
			return String.format("Video status: %s. The video is still being processed. Check again in a minute.",
					status.getStatus());
		}
		catch (Exception e) {
			log.error("Failed to check video status for request {}: {}", requestId, e.getMessage(), e);
			return "Failed to check video status: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
