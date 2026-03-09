package io.tacticl.browser.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.tacticl.browser.service.BrowserActionLogger;
import io.tacticl.browser.service.BrowserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Capture an accessibility snapshot of the current page. Tier 0: auto-execute. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserSnapshotSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserSnapshotSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private static final int MAX_CONTENT_LENGTH = 8000;

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserSnapshotSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_snapshot";
	}

	@Override
	public String getDescription() {
		return "Get the current page's text content, title, and URL for understanding page state";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		schema.putArray("required");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);

			String title = page.title();
			String currentUrl = page.url();
			String textContent = page.textContent("body");

			if (textContent == null) {
				textContent = "";
			}
			textContent = truncateContent(textContent);

			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), currentUrl, null,
					"success", getConfirmationTier(), durationMs);

			StringBuilder result = new StringBuilder();
			result.append("Page title: ").append(title).append("\n");
			result.append("URL: ").append(currentUrl).append("\n\n");
			result.append("Page content:\n").append(textContent);

			return result.toString();
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), null, null,
					"error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Browser snapshot failed for user {}", userId, e);
			return "Snapshot failed: " + e.getMessage();
		}
	}

	private String truncateContent(String content) {
		if (content.length() <= MAX_CONTENT_LENGTH) {
			return content;
		}

		int cutoff = content.lastIndexOf("\n", MAX_CONTENT_LENGTH);
		if (cutoff <= 0) {
			cutoff = MAX_CONTENT_LENGTH;
		}

		return content.substring(0, cutoff)
				+ "\n\n[Content truncated at " + cutoff + " characters. The full page has more content.]";
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
