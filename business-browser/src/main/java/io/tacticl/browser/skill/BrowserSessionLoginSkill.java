package io.tacticl.browser.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.tacticl.browser.service.BrowserActionLogger;
import io.tacticl.browser.service.BrowserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Close the current browser session and release resources. Tier 1: requires confirmation. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserSessionLoginSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserSessionLoginSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final BrowserSessionService sessionService;

	private final BrowserActionLogger actionLogger;

	public BrowserSessionLoginSkill(BrowserSessionService sessionService,
			BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_close_session";
	}

	@Override
	public String getDescription() {
		return "Close the current browser session and release resources";
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
			boolean hadSession = sessionService.hasActiveSession(userId);
			sessionService.releaseSession(userId);

			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = "closed";
			actionLogger.logAction(sessionId, sparkId, getName(), null, null,
					"success", getConfirmationTier(), durationMs);

			if (hadSession) {
				return "Browser session closed successfully. All resources have been released.";
			}
			else {
				return "No active browser session found. Nothing to close.";
			}
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			actionLogger.logAction("unknown", sparkId, getName(), null, null,
					"error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Failed to close browser session for user {}", userId, e);
			return "Failed to close browser session: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
