package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.AgentToken;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for agent_tokens subcollection under tacticl_users/{userId}/. */
@Repository
public class AgentTokenRepository extends FirestoreSubcollectionRepository<AgentToken> {

	public AgentTokenRepository(Firestore firestore) {
		super(firestore, AgentToken.class, "agent_tokens");
	}

	/** Find all active tokens for a user. */
	public List<AgentToken> findActiveByUserId(String userId) {
		List<AgentToken> all = findAll(userId);
		return all.stream().filter(AgentToken::getIsActive).toList();
	}

}
