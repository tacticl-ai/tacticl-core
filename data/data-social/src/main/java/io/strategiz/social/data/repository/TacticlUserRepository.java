package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.TacticlUser;
import org.springframework.stereotype.Repository;

/** Repository for tacticl_users Firestore collection. */
@Repository
public class TacticlUserRepository extends BaseRepository<TacticlUser> {

	public TacticlUserRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, TacticlUser.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

}
