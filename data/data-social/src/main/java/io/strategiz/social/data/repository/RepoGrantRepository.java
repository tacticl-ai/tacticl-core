package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.SubcollectionRepository;
import io.strategiz.social.data.entity.RepoGrant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for repo_grants subcollection under tacticl_users/{userId}/. */
@Repository
public class RepoGrantRepository extends SubcollectionRepository<RepoGrant> {

	public RepoGrantRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, RepoGrant.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	@Override
	protected String getParentCollectionName() {
		return "tacticl_users";
	}

	@Override
	protected String getSubcollectionName() {
		return "repo_grants";
	}

	/** Find all active repo grants for a user. */
	public List<RepoGrant> findActiveByUserId(String userId) {
		List<RepoGrant> all = findAllInSubcollection(userId);
		return all.stream().filter(RepoGrant::getIsActive).toList();
	}

}
