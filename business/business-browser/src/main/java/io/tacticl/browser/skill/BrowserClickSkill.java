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

/** Skill to click an element on a web page. Tier 1: requires confirmation. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserClickSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserClickSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserClickSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_click";
	}

	@Override
	public String getDescription() {
		return "Click an element on the current web page using a CSS selector or text selector";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode selector = properties.putObject("selector");
		selector.put("type", "string");
		selector.put("description",
				"CSS selector or text selector (prefix with 'text=' for text-based selection, e.g. 'text=Sign In')");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		schema.putArray("required").add("selector");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String selector = input.get("selector").asText();
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);
			String urlBefore = page.url();

			page.locator(selector).first().click();

			// Small wait to let navigation or DOM updates settle
			page.waitForLoadState();

			String urlAfter = page.url();
			long duration = System.currentTimeMillis() - startTime;

			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			String result;
			if (!urlBefore.equals(urlAfter)) {
				result = String.format("Clicked '%s'. Page navigated from %s to %s", selector, urlBefore, urlAfter);
			}
			else {
				result = String.format("Clicked '%s' on %s. Page URL unchanged.", selector, urlAfter);
			}

			actionLogger.logAction(sessionId, sparkId, getName(), urlAfter, selector, result,
					getConfirmationTier(), duration);

			return result;
		}
		catch (TimeoutError e) {
			long duration = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			String result = String.format("Timeout clicking '%s': element not found or not clickable within the time limit. "
					+ "Try a different selector or check if the element is visible.", selector);
			actionLogger.logAction(sessionId, sparkId, getName(), "", selector, result,
					getConfirmationTier(), duration);
			return result;
		}
		catch (Exception e) {
			log.error("Browser click failed for user {} with selector: {}", userId, selector, e);
			return "Failed to click '" + selector + "': " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
