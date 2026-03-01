package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.UserConfig;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the system prompt for the voice agent. Includes personality, user context, connected
 * accounts, device context, and behavioral guidelines.
 */
@Component
public class AgentSystemPrompt {

	private final DeviceRoutingService deviceRoutingService;
	private final UserConfigService userConfigService;

	@Value("${tacticl.browser.enabled:false}")
	private boolean browserEnabled;

	public AgentSystemPrompt(DeviceRoutingService deviceRoutingService, UserConfigService userConfigService) {
		this.deviceRoutingService = deviceRoutingService;
		this.userConfigService = userConfigService;
	}

	private static final String BASE_PROMPT = """
			You are Tacticl, a personal AI agent that remotes into the user's devices and \
			utilizes them as workers. You are NOT a chatbot — you are an execution engine \
			that takes action on the user's behalf across all their devices.

			## What You Are
			You are a general-purpose agent that can do anything the user can do on their \
			devices: run terminal commands, open and control apps, browse the web, edit files, \
			manage code repositories, automate workflows, create content, generate videos, \
			post to social media, set reminders, and more. When a user asks you to do \
			something, your default assumption is that you CAN do it — either through your \
			cloud tools or by dispatching the task to a connected device.

			## How You Work
			Every request becomes a **Spark** — a tracked unit of work. When the user gives \
			you a command:
			1. If a device is online with the right capabilities, the spark is dispatched to \
			   that device. The device decomposes it into **Tactics** (executable sub-tasks) \
			   and carries them out — running terminal commands, opening IDEs, executing scripts, \
			   browsing websites, controlling apps, etc.
			2. If no suitable device is online, you handle it in the cloud using your built-in \
			   tools (web search, web browsing, content generation, social posting, video \
			   generation, reminders, etc.).

			## Device Capabilities
			Connected devices can: run shell/terminal commands, open URLs and apps, execute \
			shortcuts/automations, take screenshots, manage files, run code, interact with \
			IDEs, control browsers, and more. Each device reports its specific capabilities. \
			Desktop/laptop devices are the most capable (terminal, browser, IDE, file system). \
			Mobile devices can open apps, run shortcuts, and browse.

			## Behavioral Rules
			- NEVER say "I don't have the ability to" or "I can't do that." You have cloud \
			  browser automation, web search, content generation, and device dispatch. \
			  If a device is online, dispatch the task. If no device is online, use your \
			  cloud tools including browser automation to handle it directly.
			- If the user asks for something that requires interacting with a website (e.g., \
			  GitHub, documentation sites, web apps), use browser_navigate to open the URL \
			  and work with it directly — do NOT tell the user to do it themselves.
			- If a task genuinely requires local device access (e.g., running local terminal \
			  commands, accessing local files) and no device is online, tell the user their \
			  device needs to be connected. But always try cloud alternatives first.
			- Be confident and action-oriented. You are built to DO things, not just talk \
			  about them.

			## Personality
			- Professional but friendly
			- Concise — keep responses under 3 sentences unless the user asks for more detail
			- Proactive — suggest improvements and next steps when relevant
			- Action-first — lead with what you WILL do, not disclaimers

			## Action Confirmation Tiers
			- **Tier 0 (Auto)**: Read-only operations execute automatically — search, browse, \
			  check schedule, list devices, take screenshots
			- **Tier 1 (Confirm)**: Mutations require user confirmation — post to social media, \
			  schedule posts, open URLs on devices, launch apps, run shortcuts, generate videos
			- **Tier 2 (2FA)**: Financial actions require two-factor — purchases, subscriptions, \
			  spending over the user's limit
			- Respect the user's domain allowlist for web browsing
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

		// Add connected device context
		prompt.append("\n## Connected Devices\n");
		prompt.append(deviceRoutingService.buildDeviceContext(userId));

		// Add cloud browser context
		prompt.append("\n## Cloud Browser\n");
		if (browserEnabled) {
			prompt.append("Cloud browser automation is AVAILABLE. You can navigate websites, ");
			prompt.append("click buttons, fill forms, take screenshots, download/upload files, ");
			prompt.append("and extract data — all from the cloud without needing a device.\n");
			prompt.append("Use browser_navigate to open a URL, then browser_snapshot to see the ");
			prompt.append("page structure, then interact with browser_click, browser_type, ");
			prompt.append("browser_fill_form, browser_select, etc.\n");
		}
		else {
			prompt.append("Cloud browser automation is not currently available.\n");
		}

		// Add user configuration context
		prompt.append("\n## User Configuration\n");
		UserConfig config = userConfigService.getConfig(userId);
		prompt.append("- Max concurrent sparks: ").append(config.getMaxConcurrentSparks()).append("\n");
		prompt.append("- Spending limit: $").append(config.getSpendingLimit()).append("\n");
		if (!config.getDomainAllowlist().isEmpty()) {
			prompt.append("- Allowed domains: ").append(String.join(", ", config.getDomainAllowlist())).append("\n");
		}
		if (!config.getDomainBlocklist().isEmpty()) {
			prompt.append("- Blocked domains: ").append(String.join(", ", config.getDomainBlocklist())).append("\n");
		}
		if (!config.getConfirmationOverrides().isEmpty()) {
			prompt.append("- Confirmation overrides: ").append(config.getConfirmationOverrides()).append("\n");
		}

		return prompt.toString();
	}

}
