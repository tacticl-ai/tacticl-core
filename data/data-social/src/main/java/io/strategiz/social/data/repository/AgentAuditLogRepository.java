package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.AgentAuditLog;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Repository for agent_audit_log Firestore collection. */
@Repository
public class AgentAuditLogRepository extends BaseRepository<AgentAuditLog> {

	public AgentAuditLogRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, AgentAuditLog.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find recent audit logs for a user, ordered by creation time descending. */
	public List<AgentAuditLog> findRecentByUserId(String userId, int limit) {
		return executeQuery(getCollection().whereEqualTo("userId", userId)
			.orderBy("createdAt", Query.Direction.DESCENDING)
			.limit(limit));
	}

	protected List<AgentAuditLog> executeQuery(com.google.cloud.firestore.Query query) {
		try {
			return query.get().get().getDocuments().stream()
				.map(doc -> { AgentAuditLog entity = doc.toObject(entityClass); entity.setId(doc.getId()); return entity; })
				.collect(Collectors.toList());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Query interrupted", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("Query failed", e);
		}
	}

}
