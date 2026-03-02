package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.TacticlUser;
import org.springframework.stereotype.Repository;

/** Repository for tacticl_users Firestore collection. */
@Repository
public class TacticlUserRepository extends FirestoreRepository<TacticlUser> {

	public TacticlUserRepository(Firestore firestore) {
		super(firestore, TacticlUser.class, "tacticl_users");
	}

}
