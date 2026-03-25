package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Repository for the pipeline_runs Firestore collection. */
@Repository
public class PipelineRunRepository extends BaseRepository<PipelineRun> {

	public PipelineRunRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, PipelineRun.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find a pipeline run by the spark that originated it. */
	public Optional<PipelineRun> findBySparkId(String sparkId) {
		return findByField("sparkId", sparkId).stream().findFirst();
	}

	/** Find all pipeline runs for a user. */
	public List<PipelineRun> findByUserId(String userId) {
		return findByField("userId", userId);
	}

	/** Find all pipeline runs with a given status. */
	public List<PipelineRun> findByStatus(PipelineStatus status) {
		return findByField("status", status.name());
	}

	/** Find all pipeline runs for a user filtered by status. */
	public List<PipelineRun> findByUserIdAndStatus(String userId, PipelineStatus status) {
		return findByField("userId", userId).stream()
			.filter(run -> run.getStatus() == status)
			.toList();
	}

}
