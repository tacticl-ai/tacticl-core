package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.PairingSession;
import org.springframework.stereotype.Repository;

/** Repository for pairing_sessions Firestore collection. */
@Repository
public class PairingSessionRepository extends BaseRepository<PairingSession> {

	public PairingSessionRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, PairingSession.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

}
