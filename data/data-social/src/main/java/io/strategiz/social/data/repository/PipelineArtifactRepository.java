package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Repository for pipeline_artifacts Firestore collection. */
@Repository
public class PipelineArtifactRepository extends BaseRepository<PipelineArtifact> {

	public PipelineArtifactRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, PipelineArtifact.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find all artifacts for a pipeline run. */
	public List<PipelineArtifact> findByPipelineRunId(String runId) {
		return findByField("pipelineRunId", runId);
	}

	/** Find the artifact produced by a specific role in a pipeline run. */
	public Optional<PipelineArtifact> findByPipelineRunIdAndRole(String runId, PdlcRole role) {
		return findByField("pipelineRunId", runId).stream()
			.filter(a -> role.equals(a.getRole()))
			.findFirst();
	}

}
