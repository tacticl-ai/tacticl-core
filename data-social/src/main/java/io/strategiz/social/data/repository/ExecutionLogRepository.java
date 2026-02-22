package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.ExecutionLog;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for execution_logs Firestore collection. */
@Repository
public class ExecutionLogRepository extends FirestoreRepository<ExecutionLog> {

	public ExecutionLogRepository(Firestore firestore) {
		super(firestore, ExecutionLog.class, "execution_logs");
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
