package io.tacticl.browser.skill;

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

/** Extract text content or attributes from page elements. Tier 0: auto-execute. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserExtractSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserExtractSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final int MAX_CONTENT_LENGTH = 8000;

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserExtractSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_extract";
	}

	@Override
	public String getDescription() {
		return "Extract text content or a specific attribute from page elements";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode selector = properties.putObject("selector");
		selector.put("type", "string");
		selector.put("description", "CSS selector to extract from (defaults to 'body')");

		ObjectNode attribute = properties.putObject("attribute");
		attribute.put("type", "string");
		attribute.put("description", "Specific attribute to extract (e.g. 'href', 'src'). If omitted, extracts text content.");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String selector = input.has("selector") ? input.get("selector").asText() : "body";
		String attribute = input.has("attribute") ? input.get("attribute").asText() : null;
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);
			String currentUrl = page.url();

			String content;
			if (attribute != null) {
				content = page.locator(selector).first().getAttribute(attribute);
				if (content == null) {
					content = "(attribute '" + attribute + "' not found on element)";
				}
			}
			else {
				content = page.locator(selector).first().textContent();
				if (content == null) {
					content = "(no text content found)";
				}
			}

			if (content.length() > MAX_CONTENT_LENGTH) {
				content = content.substring(0, MAX_CONTENT_LENGTH) + "... (truncated)";
			}

			long durationMs = System.currentTimeMillis() - startTime;

			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), currentUrl, selector,
					"success", getConfirmationTier(), durationMs);

			String extractType = attribute != null
					? "attribute '" + attribute + "' from '" + selector + "'"
					: "text content from '" + selector + "'";
			return "Extracted " + extractType + " on " + currentUrl + ":\n" + content;
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), null, selector,
					"error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Browser extract failed for user {}", userId, e);
			return "Extract failed: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
