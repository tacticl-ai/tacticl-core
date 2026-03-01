package io.tacticl.browser.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.strategiz.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.tacticl.browser.service.BrowserActionLogger;
import io.tacticl.browser.service.BrowserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Wait for an element, text, or a fixed duration. Tier 0: auto-execute. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserWaitSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserWaitSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final int DEFAULT_TIMEOUT_SECONDS = 10;

	private static final int MAX_TIMEOUT_SECONDS = 30;

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserWaitSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_wait";
	}

	@Override
	public String getDescription() {
		return "Wait for an element to appear, text to appear on page, or a fixed duration";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode selector = properties.putObject("selector");
		selector.put("type", "string");
		selector.put("description", "CSS selector to wait for element to appear");

		ObjectNode text = properties.putObject("text");
		text.put("type", "string");
		text.put("description", "Text content to wait for on the page");

		ObjectNode timeoutSeconds = properties.putObject("timeout_seconds");
		timeoutSeconds.put("type", "integer");
		timeoutSeconds.put("description", "Maximum wait time in seconds (default 10, max 30)");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String selector = input.has("selector") ? input.get("selector").asText() : null;
		String text = input.has("text") ? input.get("text").asText() : null;
		int timeout = input.has("timeout_seconds") ? input.get("timeout_seconds").asInt() : DEFAULT_TIMEOUT_SECONDS;
		timeout = Math.min(timeout, MAX_TIMEOUT_SECONDS);
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);
			String currentUrl = page.url();
			String waitTarget;

			if (selector != null) {
				page.locator(selector).waitFor(
						new Locator.WaitForOptions().setTimeout(timeout * 1000.0));
				waitTarget = "selector '" + selector + "'";
			}
			else if (text != null) {
				page.locator("text=" + text).waitFor(
						new Locator.WaitForOptions().setTimeout(timeout * 1000.0));
				waitTarget = "text '" + text + "'";
			}
			else {
				page.waitForTimeout(timeout * 1000.0);
				waitTarget = timeout + " seconds";
			}

			long durationMs = System.currentTimeMillis() - startTime;

			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), currentUrl,
					selector, "success", getConfirmationTier(), durationMs);

			if (selector != null || text != null) {
				return "Wait completed: " + waitTarget + " appeared on " + currentUrl
						+ " (took " + durationMs + "ms)";
			}
			return "Waited " + waitTarget + " on " + currentUrl;
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			String waitTarget = selector != null ? selector : (text != null ? text : timeout + "s");
			actionLogger.logAction(sessionId, sparkId, getName(), null,
					waitTarget, "error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Browser wait failed for user {}", userId, e);
			return "Wait failed: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
