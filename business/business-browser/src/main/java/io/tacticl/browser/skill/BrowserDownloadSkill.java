package io.tacticl.browser.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.skill.AgentSkill;
import io.tacticl.browser.service.BrowserActionLogger;
import io.tacticl.browser.service.BrowserSecurityService;
import io.tacticl.browser.service.BrowserSessionService;
import io.tacticl.client.gcs.client.GcsClient;
import io.tacticl.client.gcs.dto.GcsUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Download a file from the current page by clicking a download link/button. Tier 1: requires confirmation. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserDownloadSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserDownloadSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final BrowserSessionService sessionService;

	private final BrowserSecurityService securityService;

	private final BrowserActionLogger actionLogger;

	private final Optional<GcsClient> gcsClient;

	public BrowserDownloadSkill(BrowserSessionService sessionService,
			BrowserSecurityService securityService, BrowserActionLogger actionLogger,
			Optional<GcsClient> gcsClient) {
		this.sessionService = sessionService;
		this.securityService = securityService;
		this.actionLogger = actionLogger;
		this.gcsClient = gcsClient;
	}

	@Override
	public String getName() {
		return "browser_download";
	}

	@Override
	public String getDescription() {
		return "Download a file by clicking a download link or button on the current page";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode selector = properties.putObject("selector");
		selector.put("type", "string");
		selector.put("description", "CSS selector for the download link or button to click");

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

			Download download = page.waitForDownload(() -> {
				page.locator(selector).click();
			});

			String filename = download.suggestedFilename();
			Path tempPath = download.path();
			byte[] fileBytes = Files.readAllBytes(tempPath);

			if (!securityService.isDownloadAllowed(filename, fileBytes.length, userId)) {
				long durationMs = System.currentTimeMillis() - startTime;
				String sessionId = sessionService.getSessionId(userId).orElse("unknown");
				actionLogger.logAction(sessionId, sparkId, getName(), page.url(), selector,
						"blocked: download not allowed", getConfirmationTier(), durationMs);
				return "Download blocked: file '" + filename + "' (" + fileBytes.length
						+ " bytes) is not allowed by your security settings.";
			}

			String gcsPath = null;
			if (gcsClient.isPresent()) {
				String contentType = Files.probeContentType(tempPath);
				if (contentType == null) {
					contentType = "application/octet-stream";
				}
				String objectPath = "downloads/" + userId + "/" + filename;
				GcsUploadResult uploadResult = gcsClient.get()
						.upload("tacticl-user-files", objectPath, fileBytes, contentType);
				gcsPath = uploadResult.getGcsPath();
			}

			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), page.url(), selector,
					"success", getConfirmationTier(), durationMs);

			StringBuilder result = new StringBuilder();
			result.append("Downloaded: ").append(filename).append("\n");
			result.append("Size: ").append(fileBytes.length).append(" bytes\n");
			if (gcsPath != null) {
				result.append("Stored at: ").append(gcsPath);
			}
			else {
				result.append("File saved temporarily (no cloud storage configured)");
			}

			return result.toString();
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), null, selector,
					"error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Browser download failed for user {} with selector: {}", userId, selector, e);
			return "Download failed: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
