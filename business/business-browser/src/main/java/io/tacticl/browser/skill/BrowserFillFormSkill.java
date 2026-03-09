package io.tacticl.browser.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.tacticl.browser.service.BrowserActionLogger;
import io.tacticl.browser.service.BrowserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Skill to fill multiple form fields on a web page. Tier 1: requires confirmation. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserFillFormSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserFillFormSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserFillFormSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_fill_form";
	}

	@Override
	public String getDescription() {
		return "Fill multiple form fields on the current web page, optionally submitting the form";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode fields = properties.putObject("fields");
		fields.put("type", "array");
		fields.put("description", "Array of form fields to fill, each with a selector and value");
		ObjectNode items = fields.putObject("items");
		items.put("type", "object");
		ObjectNode itemProperties = items.putObject("properties");
		ObjectNode fieldSelector = itemProperties.putObject("selector");
		fieldSelector.put("type", "string");
		fieldSelector.put("description", "CSS selector for the form field");
		ObjectNode fieldValue = itemProperties.putObject("value");
		fieldValue.put("type", "string");
		fieldValue.put("description", "Value to fill into the field");
		items.putArray("required").add("selector").add("value");

		ObjectNode submitSelector = properties.putObject("submit_selector");
		submitSelector.put("type", "string");
		submitSelector.put("description", "Optional CSS selector for the submit button to click after filling fields");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		schema.putArray("required").add("fields");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		JsonNode fieldsNode = input.get("fields");
		String submitSelector = input.has("submit_selector") ? input.get("submit_selector").asText() : null;
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);
			StringBuilder summary = new StringBuilder();
			int filled = 0;

			for (JsonNode field : fieldsNode) {
				String selector = field.get("selector").asText();
				String value = field.get("value").asText();

				page.locator(selector).first().fill(value);
				filled++;

				String truncatedValue = value.length() > 30 ? value.substring(0, 30) + "..." : value;
				summary.append(String.format("  - '%s' = '%s'\n", selector, truncatedValue));
			}

			if (submitSelector != null) {
				page.locator(submitSelector).first().click();
				page.waitForLoadState();
				summary.append(String.format("  - Clicked submit button '%s'\n", submitSelector));
			}

			long duration = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");

			String result = String.format("Filled %d form field(s) on %s:\n%s", filled, page.url(), summary);

			actionLogger.logAction(sessionId, sparkId, getName(), page.url(),
					String.valueOf(filled) + " fields", result, getConfirmationTier(), duration);

			return result;
		}
		catch (TimeoutError e) {
			long duration = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			String result = "Timeout filling form: one or more fields could not be found within the time limit. "
					+ "Check that the selectors are correct and the fields are visible.";
			actionLogger.logAction(sessionId, sparkId, getName(), "", "form", result,
					getConfirmationTier(), duration);
			return result;
		}
		catch (Exception e) {
			log.error("Browser fill form failed for user {}", userId, e);
			return "Failed to fill form: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
