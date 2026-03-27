package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.AiRoleOverride;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for ai_role_overrides Firestore collection. Document ID = role name. */
@Repository
public class AiRoleOverrideRepository extends BaseRepository<AiRoleOverride> {

	public AiRoleOverrideRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, AiRoleOverride.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find all active role overrides. */
	public List<AiRoleOverride> findAllActive() {
		return findAll().stream().filter(r -> Boolean.TRUE.equals(r.getIsActive())).toList();
	}

}
