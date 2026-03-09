package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.publish.PostContent;
import io.strategiz.social.business.publish.PostValidationResult;
import io.strategiz.social.business.publish.PublishResult;
import io.strategiz.social.business.publish.SocialMediaProvider;
import io.strategiz.social.business.publish.SocialMediaProviderFactory;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to publish a post to a connected social media platform. Tier 1: requires confirmation. */
@Component
public class SocialPostSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(SocialPostSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final SocialMediaProviderFactory providerFactory;

	private final SocialIntegrationRepository integrationRepository;

	public SocialPostSkill(SocialMediaProviderFactory providerFactory,
			SocialIntegrationRepository integrationRepository) {
		this.providerFactory = providerFactory;
		this.integrationRepository = integrationRepository;
	}

	@Override
	public String getName() {
		return "post_to_social";
	}

	@Override
	public String getDescription() {
		return "Post content to a connected social media platform (Twitter, LinkedIn, Instagram)";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode platform = properties.putObject("platform");
		platform.put("type", "string");
		platform.put("description", "Target platform: TWITTER, LINKEDIN, or INSTAGRAM");
		platform.putArray("enum").add("TWITTER").add("LINKEDIN").add("INSTAGRAM");

		ObjectNode text = properties.putObject("text");
		text.put("type", "string");
		text.put("description", "The text content to post");

		schema.putArray("required").add("platform").add("text");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String platformStr = input.get("platform").asText();
		String text = input.get("text").asText();

		PlatformType platformType;
		try {
			platformType = PlatformType.valueOf(platformStr);
		}
		catch (IllegalArgumentException e) {
			return "Unknown platform: " + platformStr + ". Supported: TWITTER, LINKEDIN, INSTAGRAM";
		}

		// Look up the user's integration for this platform
		Optional<SocialIntegration> integration = integrationRepository.findByUserIdAndPlatform(userId, platformType);
		if (integration.isEmpty()) {
			return "You haven't connected " + platformType.getDisplayName()
					+ " yet. Please connect it in settings first.";
		}

		SocialIntegration integ = integration.get();
		if (integ.isDisabled() || integ.getAccessToken() == null) {
			return platformType.getDisplayName() + " integration is disabled or missing access token. "
					+ "Please reconnect it.";
		}

		// Validate content
		SocialMediaProvider provider = providerFactory.getProvider(platformType);
		PostContent content = new PostContent(text);
		PostValidationResult validation = provider.validate(content);
		if (!validation.isValid()) {
			return "Validation failed: " + String.join("; ", validation.getErrors());
		}

		// Publish
		PublishResult result = provider.publish(content, integ.getAccessToken());
		if (result.isSuccess()) {
			log.info("Published to {} for user {}: {}", platformType, userId, result.getPlatformPostId());
			return "Successfully posted to " + platformType.getDisplayName() + "! URL: " + result.getPlatformPostUrl();
		}
		else {
			return "Failed to post to " + platformType.getDisplayName() + ": " + result.getErrorMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
