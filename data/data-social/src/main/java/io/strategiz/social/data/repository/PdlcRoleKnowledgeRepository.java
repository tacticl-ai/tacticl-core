package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PdlcRoleKnowledge;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for pdlc_role_knowledge Firestore collection. */
@Repository
public class PdlcRoleKnowledgeRepository extends BaseRepository<PdlcRoleKnowledge> {

	public PdlcRoleKnowledgeRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, PdlcRoleKnowledge.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find all knowledge entries for a role. */
	public List<PdlcRoleKnowledge> findByRole(PdlcRole role) {
		return findByField("role", role.name());
	}

	/** Find knowledge entries for a role filtered by category. */
	public List<PdlcRoleKnowledge> findByRoleAndCategory(PdlcRole role, String category) {
		return findByField("role", role.name()).stream()
			.filter(k -> category.equals(k.getCategory()))
			.toList();
	}

}
