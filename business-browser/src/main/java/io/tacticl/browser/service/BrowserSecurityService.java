package io.tacticl.browser.service;

import java.net.URI;
import java.util.Set;

import io.strategiz.social.business.agent.service.UserConfigService;
import io.strategiz.social.data.entity.BrowserSettings;
import io.strategiz.social.data.entity.UserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserSecurityService {

	private static final Logger log = LoggerFactory.getLogger(BrowserSecurityService.class);

	private static final Set<String> BLOCKED_DOMAINS = Set.of(
		"169.254.169.254",
		"metadata.google.internal"
	);

	private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
		".exe", ".bat", ".sh", ".msi", ".cmd", ".ps1", ".vbs"
	);

	private static final long MAX_FILE_SIZE = 52_428_800; // 50MB

	private final UserConfigService userConfigService;

	public BrowserSecurityService(UserConfigService userConfigService) {
		this.userConfigService = userConfigService;
	}

	/** Check if a URL is allowed for navigation. */
	public boolean isUrlAllowed(String url, String userId) {
		try {
			URI uri = URI.create(url);
			String host = uri.getHost();
			if (host == null) {
				return false;
			}

			if (BLOCKED_DOMAINS.contains(host)) {
				log.warn("[BROWSER-SEC] Blocked infrastructure URL: {} for user={}", url, userId);
				return false;
			}

			UserConfig config = userConfigService.getConfig(userId);
			BrowserSettings browserSettings = config.getBrowserSettings();
			if (browserSettings == null) {
				return true;
			}

			if (!browserSettings.getDomainBlocklist().isEmpty()
					&& browserSettings.getDomainBlocklist().stream().anyMatch(host::endsWith)) {
				log.warn("[BROWSER-SEC] Domain blocked by user: {} for user={}", host, userId);
				return false;
			}

			if (!browserSettings.getDomainAllowlist().isEmpty()
					&& browserSettings.getDomainAllowlist().stream().noneMatch(host::endsWith)) {
				log.warn("[BROWSER-SEC] Domain not in allowlist: {} for user={}", host, userId);
				return false;
			}

			return true;
		}
		catch (Exception e) {
			log.error("[BROWSER-SEC] URL validation failed: {}", url, e);
			return false;
		}
	}

	/** Check if a file download is allowed. */
	public boolean isDownloadAllowed(String fileName, long sizeBytes, String userId) {
		UserConfig config = userConfigService.getConfig(userId);
		BrowserSettings settings = config.getBrowserSettings();

		if (settings != null && !settings.isAllowFileDownloads()) {
			return false;
		}

		long maxSize = settings != null ? settings.getMaxFileSize() : MAX_FILE_SIZE;
		if (sizeBytes > maxSize) {
			return false;
		}

		return BLOCKED_EXTENSIONS.stream().noneMatch(ext -> fileName.toLowerCase().endsWith(ext));
	}

	/** Check if file upload is allowed. */
	public boolean isUploadAllowed(String userId) {
		UserConfig config = userConfigService.getConfig(userId);
		BrowserSettings settings = config.getBrowserSettings();
		return settings == null || settings.isAllowFileUploads();
	}

}
