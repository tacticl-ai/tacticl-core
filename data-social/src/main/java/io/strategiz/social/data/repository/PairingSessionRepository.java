package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.PairingSession;
import org.springframework.stereotype.Repository;

/** Repository for pairing_sessions Firestore collection. */
@Repository
public class PairingSessionRepository extends FirestoreRepository<PairingSession> {

	public PairingSessionRepository(Firestore firestore) {
		super(firestore, PairingSession.class, "pairing_sessions");
	}

}
