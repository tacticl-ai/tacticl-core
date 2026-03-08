package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.PostState;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.entity.SocialPost;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.data.repository.SocialPostRepository;
import com.google.cloud.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to schedule a post for future publication. Tier 1: requires confirmation. */
@Component
public class SchedulePostSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(SchedulePostSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final SocialPostRepository postRepository;

	private final SocialIntegrationRepository integrationRepository;

	public SchedulePostSkill(SocialPostRepository postRepository,
			SocialIntegrationRepository integrationRepository) {
		this.postRepository = postRepository;
		this.integrationRepository = integrationRepository;
	}

	@Override
	public String getName() {
		return "schedule_post";
	}

	@Override
	public String getDescription() {
		return "Schedule a social media post for future publication at a specific date and time";
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

		ObjectNode scheduledTime = properties.putObject("scheduled_time");
		scheduledTime.put("type", "string");
		scheduledTime.put("description",
				"ISO-8601 datetime for publishing (e.g., 2026-02-15T10:00:00-05:00)");

		schema.putArray("required").add("platform").add("text").add("scheduled_time");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String platformStr = input.get("platform").asText();
		String text = input.get("text").asText();
		String scheduledTimeStr = input.get("scheduled_time").asText();

		PlatformType platformType;
		try {
			platformType = PlatformType.valueOf(platformStr);
		}
		catch (IllegalArgumentException e) {
			return "Unknown platform: " + platformStr;
		}

		Instant publishDate;
		try {
			publishDate = ZonedDateTime.parse(scheduledTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
		}
		catch (DateTimeParseException e) {
			return "Invalid date format. Use ISO-8601 format like: 2026-02-15T10:00:00-05:00";
		}

		if (publishDate.isBefore(Instant.now())) {
			return "Scheduled time must be in the future.";
		}

		// Verify integration exists
		Optional<SocialIntegration> integration = integrationRepository.findByUserIdAndPlatform(userId, platformType);
		if (integration.isEmpty()) {
			return "You haven't connected " + platformType.getDisplayName() + ". Please connect it first.";
		}

		// Create scheduled post
		SocialPost post = new SocialPost();
		post.setId(UUID.randomUUID().toString());
		post.setUserId(userId);
		post.setContent(text);
		post.setTargetIntegrationIds(List.of(integration.get().getId()));
		post.setPublishDate(publishDate);
		post.setState(PostState.QUEUED);
		post.setCreatedDate(Timestamp.now());
		post.setModifiedDate(Timestamp.now());

		postRepository.save(post, userId);
		log.info("Scheduled post {} for user {} on {} at {}", post.getId(), userId, platformType, publishDate);

		return String.format("Post scheduled for %s on %s at %s. Post ID: %s", platformType.getDisplayName(),
				scheduledTimeStr.substring(0, 10), scheduledTimeStr.substring(11, 16), post.getId());
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
