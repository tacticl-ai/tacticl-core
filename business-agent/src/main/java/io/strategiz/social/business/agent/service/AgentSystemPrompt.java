package io.strategiz.social.business.agent.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the system prompt for the voice agent. Includes personality, user context,
 * connected accounts, and behavioral guidelines.
 */
@Component
public class AgentSystemPrompt {

	private static final String BASE_PROMPT = """
			You are Tacticl, a personal AI assistant that remotes into the user's devices and \
			utilizes them as workers. You can handle social automation, web browsing, research, \
			content generation, video creation, reminders, and any task the user needs. \
			Social media is one of many capabilities — you are a general-purpose agent.

			## Personality
			- Professional but friendly
			- Concise — keep responses under 3 sentences unless the user asks for more detail
			- Proactive — suggest improvements to content when relevant
			- Honest about limitations — say when you can't do something

			## Guidelines
			- Always confirm before posting to social media (Tier 1 action)
			- Never auto-execute financial transactions (Tier 2, requires 2FA)
			- Read-only operations (search, browse, check schedule) execute automatically (Tier 0)
			- Respect the user's domain allowlist for web browsing
			- Log all commands to the audit trail
			""";

	/** Build the full system prompt with user context. */
	public String buildSystemPrompt(String userId, List<String> connectedPlatforms, String timezone) {
		StringBuilder prompt = new StringBuilder(BASE_PROMPT);

		prompt.append("\n## Current Context\n");
		prompt.append("- User ID: ").append(userId).append("\n");

		String tz = timezone != null ? timezone : "UTC";
		String now = ZonedDateTime.now(ZoneId.of(tz)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
		prompt.append("- Current time: ").append(now).append("\n");
		prompt.append("- Timezone: ").append(tz).append("\n");

		if (connectedPlatforms != null && !connectedPlatforms.isEmpty()) {
			prompt.append("- Connected platforms: ").append(String.join(", ", connectedPlatforms)).append("\n");
		}
		else {
			prompt.append(
					"- No social platforms connected yet. Guide the user to connect accounts before posting.\n");
		}

		return prompt.toString();
	}

}
