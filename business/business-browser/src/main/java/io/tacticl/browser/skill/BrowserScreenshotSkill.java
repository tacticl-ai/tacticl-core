package io.tacticl.browser.skill;

import java.util.Base64;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

/** Take a screenshot of the current browser page. Tier 0: auto-execute. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserScreenshotSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserScreenshotSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserScreenshotSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_screenshot";
	}

	@Override
	public String getDescription() {
		return "Take a screenshot of the current browser page";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode fullPage = properties.putObject("full_page");
		fullPage.put("type", "boolean");
		fullPage.put("description", "Whether to capture the full scrollable page (default: false)");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		schema.putArray("required");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		boolean fullPage = input.has("full_page") && input.get("full_page").asBoolean(false);
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);

			Page.ScreenshotOptions options = new Page.ScreenshotOptions();
			if (fullPage) {
				options.setFullPage(true);
			}

			byte[] screenshotBytes = page.screenshot(options);
			String base64 = Base64.getEncoder().encodeToString(screenshotBytes);

			int width = page.viewportSize().width;
			int height = page.viewportSize().height;
			int sizeKb = screenshotBytes.length / 1024;

			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), page.url(), null,
					"success", getConfirmationTier(), durationMs);

			return "Screenshot captured (" + width + "x" + height + ", " + sizeKb + "kb). "
					+ "Base64 data attached.\n" + base64;
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), null, null,
					"error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Browser screenshot failed for user {}", userId, e);
			return "Screenshot failed: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
