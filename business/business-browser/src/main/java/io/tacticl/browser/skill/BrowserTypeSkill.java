package io.tacticl.browser.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Locator;
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

/** Skill to type text into an input element on a web page. Tier 1: requires confirmation. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserTypeSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserTypeSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserTypeSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_type";
	}

	@Override
	public String getDescription() {
		return "Type text into an input field on the current web page";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode selector = properties.putObject("selector");
		selector.put("type", "string");
		selector.put("description",
				"CSS selector or text selector for the input field (e.g. '#search', 'input[name=email]', 'text=Search')");

		ObjectNode text = properties.putObject("text");
		text.put("type", "string");
		text.put("description", "The text to type into the field");

		ObjectNode clearFirst = properties.putObject("clear_first");
		clearFirst.put("type", "boolean");
		clearFirst.put("description", "Whether to clear the field before typing (default: true). Uses fill() when true, type() when false.");

		ObjectNode submit = properties.putObject("submit");
		submit.put("type", "boolean");
		submit.put("description", "Whether to press Enter after typing to submit (default: false)");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		schema.putArray("required").add("selector").add("text");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String selector = input.get("selector").asText();
		String text = input.get("text").asText();
		boolean clearFirst = !input.has("clear_first") || input.get("clear_first").asBoolean(true);
		boolean submit = input.has("submit") && input.get("submit").asBoolean(false);
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);
			Locator locator = page.locator(selector).first();

			if (clearFirst) {
				locator.fill(text);
			}
			else {
				locator.type(text);
			}

			if (submit) {
				locator.press("Enter");
				page.waitForLoadState();
			}

			long duration = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");

			String truncatedText = text.length() > 50 ? text.substring(0, 50) + "..." : text;
			String result = String.format("Typed '%s' into '%s'%s on %s",
					truncatedText, selector, submit ? " and submitted" : "", page.url());

			actionLogger.logAction(sessionId, sparkId, getName(), page.url(), selector, result,
					getConfirmationTier(), duration);

			return result;
		}
		catch (TimeoutError e) {
			long duration = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			String result = String.format("Timeout typing into '%s': element not found or not editable within the time limit. "
					+ "Verify the selector targets an input/textarea element.", selector);
			actionLogger.logAction(sessionId, sparkId, getName(), "", selector, result,
					getConfirmationTier(), duration);
			return result;
		}
		catch (Exception e) {
			log.error("Browser type failed for user {} with selector: {}", userId, selector, e);
			return "Failed to type into '" + selector + "': " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
