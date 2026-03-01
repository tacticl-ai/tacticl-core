package io.tacticl.browser.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.tacticl.browser.config.BrowserProperties;
import io.tacticl.browser.data.entity.BrowserSession;
import io.tacticl.browser.data.entity.BrowserSessionStatus;
import io.tacticl.browser.data.entity.BrowserSessionType;
import io.tacticl.browser.data.repository.BrowserSessionRepository;
import io.tacticl.client.gcs.client.GcsClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserSessionService {

	private static final Logger log = LoggerFactory.getLogger(BrowserSessionService.class);

	private final Browser browser;

	private final BrowserProperties properties;

	private final BrowserSessionRepository sessionRepository;

	private final Optional<GcsClient> gcsClient;

	private final Map<String, BrowserContext> activeContexts = new ConcurrentHashMap<>();

	private final Map<String, String> sessionIds = new ConcurrentHashMap<>();

	public BrowserSessionService(Browser browser, BrowserProperties properties,
			BrowserSessionRepository sessionRepository, Optional<GcsClient> gcsClient) {
		this.browser = browser;
		this.properties = properties;
		this.sessionRepository = sessionRepository;
		this.gcsClient = gcsClient;
	}

	/** Get or create an ephemeral browser context for the user. */
	public BrowserContext getEphemeralContext(String userId, String sparkId) {
		BrowserContext existing = activeContexts.get(userId);
		if (existing != null) {
			return existing;
		}

		if (activeContexts.size() >= properties.getMaxConcurrentContexts()) {
			throw new RuntimeException("Maximum concurrent browser sessions reached ("
				+ properties.getMaxConcurrentContexts() + ")");
		}

		BrowserContext context = browser.newContext(new Browser.NewContextOptions()
			.setViewportSize(1280, 720)
			.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
				+ "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"));

		activeContexts.put(userId, context);

		String sessionId = UUID.randomUUID().toString();
		sessionIds.put(userId, sessionId);
		BrowserSession session = new BrowserSession();
		session.setId(sessionId);
		session.setUserId(userId);
		session.setSparkId(sparkId);
		session.setType(BrowserSessionType.EPHEMERAL);
		session.setStatus(BrowserSessionStatus.ACTIVE);
		session.setCreatedAt(Instant.now());
		session.setLastActiveAt(Instant.now());
		sessionRepository.save(session, sessionId);

		log.info("[BROWSER] Created ephemeral session {} for user={}, spark={}", sessionId, userId, sparkId);
		return context;
	}

	/** Get the active page for a user, creating one if needed. */
	public Page getPage(String userId, String sparkId) {
		BrowserContext context = getEphemeralContext(userId, sparkId);
		var pages = context.pages();
		if (pages.isEmpty()) {
			Page page = context.newPage();
			page.setDefaultTimeout(properties.getPageLoadTimeoutSeconds() * 1000.0);
			return page;
		}
		return pages.get(pages.size() - 1);
	}

	/** Release a user's browser session. */
	public void releaseSession(String userId) {
		BrowserContext context = activeContexts.remove(userId);
		if (context != null) {
			try {
				context.close();
			}
			catch (Exception e) {
				log.warn("Error closing browser context for user={}", userId, e);
			}
		}

		String sessionId = sessionIds.remove(userId);
		if (sessionId != null) {
			sessionRepository.findById(sessionId).ifPresent(session -> {
				session.setStatus(BrowserSessionStatus.CLOSED);
				session.setClosedAt(Instant.now());
				long durationSec = Duration.between(session.getCreatedAt(), Instant.now()).getSeconds();
				session.setDurationSeconds(durationSec);
				sessionRepository.save(session, sessionId);
			});
		}

		log.info("[BROWSER] Released session for user={}", userId);
	}

	/** Check if user has an active browser session. */
	public boolean hasActiveSession(String userId) {
		return activeContexts.containsKey(userId);
	}

	/** Get the current session ID for a user. */
	public Optional<String> getSessionId(String userId) {
		return Optional.ofNullable(sessionIds.get(userId));
	}

	@PreDestroy
	void cleanup() {
		log.info("[BROWSER] Cleaning up {} active sessions", activeContexts.size());
		activeContexts.values().forEach(ctx -> {
			try {
				ctx.close();
			}
			catch (Exception e) {
				// ignore during shutdown
			}
		});
		activeContexts.clear();
		sessionIds.clear();
	}

}
