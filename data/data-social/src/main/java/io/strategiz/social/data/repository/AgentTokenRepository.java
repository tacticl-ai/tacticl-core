package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.SubcollectionRepository;
import io.strategiz.social.data.entity.AgentToken;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for agent_tokens subcollection under tacticl_users/{userId}/. */
@Repository
public class AgentTokenRepository extends SubcollectionRepository<AgentToken> {

	public AgentTokenRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, AgentToken.class, auditingHandler);
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
		return "agent_tokens";
	}

	/** Find all active tokens for a user. */
	public List<AgentToken> findActiveByUserId(String userId) {
		List<AgentToken> all = findAllInSubcollection(userId);
		return all.stream().filter(AgentToken::getIsActive).toList();
	}

}
