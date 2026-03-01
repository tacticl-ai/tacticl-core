package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.tacticl.browser.data.entity.BrowserActionLog;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for browser_action_logs Firestore collection. */
@Repository
public class BrowserActionLogRepository extends FirestoreRepository<BrowserActionLog> {

	public BrowserActionLogRepository(Firestore firestore) {
		super(firestore, BrowserActionLog.class, "browser_action_logs");
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
