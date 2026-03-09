package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.data.entity.PostState;
import io.strategiz.social.data.entity.SocialPost;
import io.strategiz.social.data.repository.SocialPostRepository;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/** Skill to list upcoming scheduled posts. Tier 0: auto-execute. */
@Component
public class ListScheduledSkill implements AgentSkill {

	private static final JsonMapper MAPPER = new JsonMapper();

	private final SocialPostRepository postRepository;

	public ListScheduledSkill(SocialPostRepository postRepository) {
		this.postRepository = postRepository;
	}

	@Override
	public String getName() {
		return "list_scheduled";
	}

	@Override
	public String getDescription() {
		return "List upcoming scheduled social media posts for the user";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		schema.putObject("properties");
		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		List<SocialPost> queued = postRepository.findByUserIdAndState(userId, PostState.QUEUED);
		if (queued.isEmpty()) {
			return "No upcoming scheduled posts.";
		}

		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
		StringBuilder sb = new StringBuilder("Upcoming scheduled posts:\n");
		for (int i = 0; i < queued.size(); i++) {
			SocialPost post = queued.get(i);
			String time = post.getPublishDate() != null ? fmt.format(post.getPublishDate()) : "no date";
			String preview = post.getContent() != null && post.getContent().length() > 50
					? post.getContent().substring(0, 50) + "..." : post.getContent();
			sb.append(String.format("%d. [%s UTC] %s (ID: %s)\n", i + 1, time, preview, post.getId()));
		}
		return sb.toString();
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
