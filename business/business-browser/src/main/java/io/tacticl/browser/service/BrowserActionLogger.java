package io.tacticl.browser.service;

import java.time.Instant;
import java.util.UUID;

import io.tacticl.browser.data.entity.BrowserActionLog;
import io.tacticl.browser.data.repository.BrowserActionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class BrowserActionLogger {

	private static final Logger log = LoggerFactory.getLogger(BrowserActionLogger.class);

	private final BrowserActionLogRepository actionLogRepository;

	public BrowserActionLogger(BrowserActionLogRepository actionLogRepository) {
		this.actionLogRepository = actionLogRepository;
	}

	public void logAction(String sessionId, String sparkId, String skillName,
			String url, String elementRef, String result, int tier, long durationMs) {
		try {
			BrowserActionLog actionLog = new BrowserActionLog();
			actionLog.setId(UUID.randomUUID().toString());
			actionLog.setSessionId(sessionId);
			actionLog.setSparkId(sparkId);
			actionLog.setSkillName(skillName);
			actionLog.setUrl(url);
			actionLog.setElementRef(elementRef);
			actionLog.setResult(result);
			actionLog.setTier(tier);
			actionLog.setDurationMs(durationMs);
			actionLog.setTimestamp(Instant.now());
			actionLogRepository.save(actionLog, actionLog.getId());
		}
		catch (Exception e) {
			log.error("Failed to log browser action: {}", skillName, e);
		}
	}

}
