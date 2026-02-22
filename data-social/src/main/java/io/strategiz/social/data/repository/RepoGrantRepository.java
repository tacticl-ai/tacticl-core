package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.RepoGrant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for repo_grants Firestore collection. */
@Repository
public class RepoGrantRepository extends FirestoreRepository<RepoGrant> {

	public RepoGrantRepository(Firestore firestore) {
		super(firestore, RepoGrant.class, "repo_grants");
	}

	/** Find all active repo grants for a user. */
	public List<RepoGrant> findActiveByUserId(String userId) {
		List<RepoGrant> all = findByField("userId", userId);
		return all.stream().filter(RepoGrant::isActive).toList();
	}

}
