package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.BaseRepository;
import io.tacticl.browser.data.entity.BrowserSession;
import io.tacticl.browser.data.entity.BrowserSessionStatus;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for browser_sessions Firestore collection. */
@Repository
public class BrowserSessionRepository extends BaseRepository<BrowserSession> {

	public BrowserSessionRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, BrowserSession.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-browser";
	}

	/** Find active or idle sessions for a user. */
	public List<BrowserSession> findActiveByUserId(String userId) {
		return findByField("userId", userId).stream()
			.filter(s -> s.getStatus() == BrowserSessionStatus.ACTIVE
					|| s.getStatus() == BrowserSessionStatus.IDLE)
			.toList();
	}

	/** Find sessions associated with a spark. */
	public List<BrowserSession> findBySparkId(String sparkId) {
		return findByField("sparkId", sparkId);
	}

}
