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

import java.util.List;

/** Skill to select an option from a dropdown on a web page. Tier 1: requires confirmation. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserSelectSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserSelectSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserSelectSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_select";
	}

	@Override
	public String getDescription() {
		return "Select an option from a dropdown (select element) on the current web page";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode selector = properties.putObject("selector");
		selector.put("type", "string");
		selector.put("description", "CSS selector for the <select> dropdown element");

		ObjectNode value = properties.putObject("value");
		value.put("type", "string");
		value.put("description", "The option value or visible label text to select");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		schema.putArray("required").add("selector").add("value");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String selector = input.get("selector").asText();
		String value = input.get("value").asText();
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);

			List<String> selected = page.locator(selector).first().selectOption(value);

			long duration = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");

			String selectedValues = selected.isEmpty() ? value : String.join(", ", selected);
			String result = String.format("Selected '%s' in dropdown '%s' on %s", selectedValues, selector, page.url());

			actionLogger.logAction(sessionId, sparkId, getName(), page.url(), selector, result,
					getConfirmationTier(), duration);

			return result;
		}
		catch (TimeoutError e) {
			long duration = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			String result = String.format("Timeout selecting '%s' in dropdown '%s': element not found or not a <select> element. "
					+ "Verify the selector targets a dropdown.", value, selector);
			actionLogger.logAction(sessionId, sparkId, getName(), "", selector, result,
					getConfirmationTier(), duration);
			return result;
		}
		catch (Exception e) {
			log.error("Browser select failed for user {} with selector: {}", userId, selector, e);
			return "Failed to select '" + value + "' in '" + selector + "': " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
