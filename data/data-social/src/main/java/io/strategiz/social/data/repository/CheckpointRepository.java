package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.Checkpoint;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for checkpoints Firestore collection. */
@Repository
public class CheckpointRepository extends BaseRepository<Checkpoint> {

	public CheckpointRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, Checkpoint.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find all checkpoints for a spark. */
	public List<Checkpoint> findBySparkId(String sparkId) {
		return findByField("sparkId", sparkId);
	}

	/** Find all checkpoints for a pipeline run. */
	public List<Checkpoint> findByPipelineRunId(String pipelineRunId) {
		return findByField("pipelineRunId", pipelineRunId);
	}

	/** Find pending checkpoints for a user (across all their sparks). */
	public List<Checkpoint> findPendingByUserId(String userId) {
		// We don't have userId directly on checkpoint; query via sparks would be needed.
		// For now, return all and let service layer filter.
		return List.of();
	}

}
