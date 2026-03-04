package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.BaseRepository;
import io.tacticl.browser.data.entity.BrowserActionLog;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for browser_action_logs Firestore collection. */
@Repository
public class BrowserActionLogRepository extends BaseRepository<BrowserActionLog> {

	public BrowserActionLogRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, BrowserActionLog.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-browser";
	}

	/** Find all action logs for a browser session. */
	public List<BrowserActionLog> findBySessionId(String sessionId) {
		return findByField("sessionId", sessionId);
	}

	/** Find all action logs for a spark. */
	public List<BrowserActionLog> findBySparkId(String sparkId) {
		return findByField("sparkId", sparkId);
	}

}
