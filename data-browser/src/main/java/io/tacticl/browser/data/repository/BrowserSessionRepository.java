package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.tacticl.browser.data.entity.BrowserSession;
import io.tacticl.browser.data.entity.BrowserSessionStatus;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for browser_sessions Firestore collection. */
@Repository
public class BrowserSessionRepository extends FirestoreRepository<BrowserSession> {

	public BrowserSessionRepository(Firestore firestore) {
		super(firestore, BrowserSession.class, "browser_sessions");
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
