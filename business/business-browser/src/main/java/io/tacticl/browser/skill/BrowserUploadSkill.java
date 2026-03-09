package io.tacticl.browser.skill;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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

/** Upload a file to a file input on the current page. Tier 1: requires confirmation. */
@Component
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserUploadSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(BrowserUploadSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private final BrowserSessionService sessionService;

	private final BrowserSecurityService securityService;

	private final BrowserActionLogger actionLogger;

	public BrowserUploadSkill(BrowserSessionService sessionService,
			BrowserSecurityService securityService, BrowserActionLogger actionLogger) {
		this.sessionService = sessionService;
		this.securityService = securityService;
		this.actionLogger = actionLogger;
	}

	@Override
	public String getName() {
		return "browser_upload";
	}

	@Override
	public String getDescription() {
		return "Upload a file to a file input element on the current page";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode selector = properties.putObject("selector");
		selector.put("type", "string");
		selector.put("description", "CSS selector for the file input element");

		ObjectNode fileUrl = properties.putObject("file_url");
		fileUrl.put("type", "string");
		fileUrl.put("description", "URL of the file to upload (e.g. from GCS or other source)");

		ObjectNode sparkId = properties.putObject("spark_id");
		sparkId.put("type", "string");
		sparkId.put("description", "The spark ID for this action");

		var required = schema.putArray("required");
		required.add("selector");
		required.add("file_url");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String selector = input.get("selector").asText();
		String fileUrl = input.get("file_url").asText();
		String sparkId = input.has("spark_id") ? input.get("spark_id").asText() : "unknown";
		long startTime = System.currentTimeMillis();

		if (!securityService.isUploadAllowed(userId)) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), null, selector,
					"blocked: upload not allowed", getConfirmationTier(), durationMs);
			return "Upload blocked: file uploads are not allowed by your security settings.";
		}

		Path tempFile = null;
		try {
			Page page = sessionService.getPage(userId, sparkId);

			// Download file from URL to temp location
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(fileUrl))
					.GET()
					.build();
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

			if (response.statusCode() != 200) {
				return "Upload failed: could not download file from URL (HTTP " + response.statusCode() + ")";
			}

			// Extract filename from URL
			String urlPath = URI.create(fileUrl).getPath();
			String filename = urlPath.substring(urlPath.lastIndexOf('/') + 1);
			if (filename.isEmpty()) {
				filename = "upload";
			}

			tempFile = Files.createTempFile("browser-upload-", "-" + filename);
			Files.write(tempFile, response.body());

			page.locator(selector).setInputFiles(tempFile);

			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), page.url(), selector,
					"success", getConfirmationTier(), durationMs);

			return "Uploaded file '" + filename + "' (" + response.body().length
					+ " bytes) to input element: " + selector;
		}
		catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String sessionId = sessionService.getSessionId(userId).orElse("unknown");
			actionLogger.logAction(sessionId, sparkId, getName(), null, selector,
					"error: " + e.getMessage(), getConfirmationTier(), durationMs);

			log.error("Browser upload failed for user {} with selector: {}", userId, selector, e);
			return "Upload failed: " + e.getMessage();
		}
		finally {
			if (tempFile != null) {
				try {
					Files.deleteIfExists(tempFile);
				}
				catch (Exception e) {
					log.warn("Failed to clean up temp file: {}", tempFile, e);
				}
			}
		}
	}

	@Override
	public int getConfirmationTier() {
		return 1;
	}

}
