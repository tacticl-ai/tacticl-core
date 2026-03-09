package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.siliconflow.client.SiliconFlowClient;
import io.strategiz.social.client.siliconflow.dto.VideoGenerationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Skill to generate AI video via SiliconFlow Wan 2.2. Tier 1: requires confirmation. */
@Component
@ConditionalOnProperty(name = "tacticl.siliconflow.enabled", havingValue = "true", matchIfMissing = false)
public class VideoGenSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(VideoGenSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final SiliconFlowClient siliconFlowClient;

	public VideoGenSkill(SiliconFlowClient siliconFlowClient) {
		this.siliconFlowClient = siliconFlowClient;
	}

	@Override
	public String getName() {
		return "generate_video";
	}

	@Override
	public String getDescription() {
		return "Generate a short AI video from a text prompt using Wan 2.2 (~$0.21 per video, 480p, ~5 seconds)";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode prompt = properties.putObject("prompt");
		prompt.put("type", "string");
		prompt.put("description", "Descriptive prompt for the video to generate");

		schema.putArray("required").add("prompt");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String prompt = input.get("prompt").asText();
		log.info("Generating video for user {} with prompt: {}", userId, prompt);

		try {
			VideoGenerationResponse response = siliconFlowClient.generateVideo(prompt, null);
			return String.format("Video generation started! Request ID: %s. Status: %s. "
					+ "Video will be ready in 1-3 minutes. Use 'check video status' to check progress.",
					response.getRequestId(), response.getStatus());
		}
		catch (Exception e) {
			log.error("Video generation failed for user {}: {}", userId, e.getMessage(), e);
			return "Video generation failed: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
