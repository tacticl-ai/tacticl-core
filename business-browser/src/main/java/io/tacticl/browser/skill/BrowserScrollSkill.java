package io.tacticl.browser.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import io.strategiz.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.tacticl.browser.service.BrowserActionLogger;
import io.tacticl.browser.service.BrowserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Scroll the browser page up or down. Tier 0: auto-execute. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserScrollSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserScrollSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final int DEFAULT_SCROLL_AMOUNT = 3;

	private static final int PIXELS_PER_TICK = 100;

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserScrollSkill(BrowserSessionService sessionService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_scroll";
	}

	@Override
	public String getDescription() {
		return "Scroll the browser page up or down by a specified amount";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode direction = properties.putObject("direction");
		direction.put("type", "string");
		direction.put("description", "Scroll direction: 'up' or 'down'");
		direction.putArray("enum").add("up").add("down");

		ObjectNode amount = properties.putObject("amount");
		amount.put("type", "integer");
		amount.put("description", "Number of scroll wheel ticks (default 3)");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		schema.putArray("required").add("direction");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String direction = input.get("direction").asText();
		int amount = input.has("amount") ? input.get("amount").asInt() : DEFAULT_SCROLL_AMOUNT;
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		try {
			Page page = sessionService.getPage(userId, sparkId);

			int deltaY = amount * PIXELS_PER_TICK;
			if ("up".equals(direction)) {
				deltaY = -deltaY;
			}

			page.mouse().wheel(0, deltaY);

			String currentUrl = page.url();
			long durationMs = System.currentTimeMillis() - startTime;

			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), currentUrl, null,
					"success", getConfirmationTier(), durationMs);

			return "Scrolled " + direction + " by " + amount + " ticks on: " + currentUrl;
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), null, null,
					"error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Browser scroll failed for user {}", userId, e);
			return "Scroll failed: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
