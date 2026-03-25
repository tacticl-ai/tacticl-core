package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for the pipeline_events Firestore collection. Append-only by convention. */
@Repository
public class PipelineEventRepository extends BaseRepository<PipelineEvent> {

	public PipelineEventRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, PipelineEvent.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find all events belonging to a pipeline run. */
	public List<PipelineEvent> findByPipelineRunId(String pipelineRunId) {
		return findByField("pipelineRunId", pipelineRunId);
	}

	/** Find events of a specific type within a pipeline run. */
	public List<PipelineEvent> findByPipelineRunIdAndEventType(String pipelineRunId, PipelineEventType eventType) {
		return findByField("pipelineRunId", pipelineRunId).stream()
			.filter(event -> event.getEventType() == eventType)
			.toList();
	}

	/** Find all events for a user. */
	public List<PipelineEvent> findByUserId(String userId) {
		return findByField("userId", userId);
	}

}
