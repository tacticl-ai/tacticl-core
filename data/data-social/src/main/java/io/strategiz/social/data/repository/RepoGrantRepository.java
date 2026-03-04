package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.RepoGrant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for repo_grants subcollection under tacticl_users/{userId}/. */
@Repository
public class RepoGrantRepository extends FirestoreSubcollectionRepository<RepoGrant> {

	public RepoGrantRepository(Firestore firestore) {
		super(firestore, RepoGrant.class, "repo_grants");
	}

	/** Find all active repo grants for a user. */
	public List<RepoGrant> findActiveByUserId(String userId) {
		List<RepoGrant> all = findAll(userId);
		return all.stream().filter(RepoGrant::getIsActive).toList();
	}

}
