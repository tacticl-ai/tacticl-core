package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import io.strategiz.social.data.entity.AgentAuditLog;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for agent_audit_log Firestore collection. */
@Repository
public class AgentAuditLogRepository extends FirestoreRepository<AgentAuditLog> {

	public AgentAuditLogRepository(Firestore firestore) {
		super(firestore, AgentAuditLog.class, "agent_audit_log");
	}

	/** Find recent audit logs for a user, ordered by creation time descending. */
	public List<AgentAuditLog> findRecentByUserId(String userId, int limit) {
		return executeQuery(getCollection().whereEqualTo("userId", userId)
			.orderBy("createdAt", Query.Direction.DESCENDING)
			.limit(limit));
	}

}
