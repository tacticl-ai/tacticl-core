package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.AgentToken;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for agent_tokens Firestore collection. */
@Repository
public class AgentTokenRepository extends FirestoreRepository<AgentToken> {

	public AgentTokenRepository(Firestore firestore) {
		super(firestore, AgentToken.class, "agent_tokens");
	}

	/** Find all active tokens for a user. */
	public List<AgentToken> findActiveByUserId(String userId) {
		List<AgentToken> all = findByField("userId", userId);
		return all.stream().filter(AgentToken::isActive).toList();
	}

}
