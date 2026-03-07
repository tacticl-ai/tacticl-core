package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.ExecutionLog;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for execution_logs Firestore collection. */
@Repository
public class ExecutionLogRepository extends BaseRepository<ExecutionLog> {

	public ExecutionLogRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, ExecutionLog.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find all logs for a spark. */
	public List<ExecutionLog> findBySparkId(String sparkId) {
		return findByField("sparkId", sparkId);
	}

	/** Find all logs for a tactic. */
	public List<ExecutionLog> findByTacticId(String tacticId) {
		return findByField("tacticId", tacticId);
	}

}
