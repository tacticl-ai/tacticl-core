package io.tacticl.browser.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.tacticl.browser.service.BrowserActionLogger;
import io.tacticl.browser.service.BrowserSecurityService;
import io.tacticl.browser.service.BrowserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Navigate the browser to a URL. Tier 0: auto-execute. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserNavigateSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserNavigateSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final BrowserSessionService sessionService;

	private final BrowserSecurityService securityService;

	private final BrowserActionLogger actionLogger;

	public BrowserNavigateSkill(BrowserSessionService sessionService,
			BrowserSecurityService securityService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.securityService = securityService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_navigate";
	}

	@Override
	public String getDescription() {
		return "Navigate the browser to a specified URL";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode url = properties.putObject("url");
		url.put("type", "string");
		url.put("description", "The URL to navigate to");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		schema.putArray("required").add("url");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String url = input.get("url").asText();
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		if (!securityService.isUrlAllowed(url, userId)) {
			return "Navigation blocked: the URL " + url + " is not allowed by your security settings.";
		}

		try {
			Page page = sessionService.getPage(userId, sparkId);
			page.navigate(url);

			String title = page.title();
			String currentUrl = page.url();
			long durationMs = System.currentTimeMillis() - startTime;

			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), currentUrl, null,
					"success", getConfirmationTier(), durationMs);

			return "Navigated to: " + currentUrl + "\nPage title: " + title;
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), url, null,
					"error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Browser navigation failed for user {} to URL: {}", userId, url, e);
			return "Navigation failed: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
