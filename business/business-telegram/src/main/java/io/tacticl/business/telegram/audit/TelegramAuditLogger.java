package io.tacticl.business.telegram.audit;

import io.tacticl.data.telegram.entity.TelegramAuditLog;
import io.tacticl.data.telegram.repository.TelegramAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes append-only forensic rows for every Telegram group command.
 *
 * <p>Audit failure is intentionally swallowed: the user-facing command must
 * complete even if the audit collection is unreachable. Forensics is
 * best-effort — losing a row is preferable to losing the action.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(TelegramAuditLogger.class);

    private final TelegramAuditLogRepository repo;

    public TelegramAuditLogger(TelegramAuditLogRepository repo) {
        this.repo = repo;
    }

    /**
     * Persist one audit row. Caller serializes the payload — this layer makes
     * no assumptions about what is forensically useful for a given action.
     *
     * @param chatId          Telegram chat id where the command ran
     * @param telegramUserId  sender's Telegram user id
     * @param tacticlUserId   resolved Tacticl user id, or {@code null} if the
     *                        sender is not yet linked (pre-link activity is
     *                        intentionally captured)
     * @param action          short uppercase verb (e.g. {@code "GRANT"})
     * @param payloadJson     pre-serialized JSON object string, may be null
     */
    public void record(long chatId,
                       long telegramUserId,
                       String tacticlUserId,
                       String action,
                       String payloadJson) {
        try {
            repo.save(TelegramAuditLog.create(chatId, telegramUserId, tacticlUserId, action, payloadJson));
        } catch (RuntimeException e) {
            // WHY: never fail the command on audit failure. Warn-only.
            log.warn("Failed to record telegram audit row chatId={} action={}: {}",
                    chatId, action, e.toString());
        }
    }
}
