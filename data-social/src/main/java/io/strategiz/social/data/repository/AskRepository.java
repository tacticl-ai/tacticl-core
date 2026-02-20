package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import io.strategiz.social.data.entity.Ask;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for asks Firestore collection. */
@Repository
public class AskRepository extends FirestoreRepository<Ask> {

	public AskRepository(Firestore firestore) {
		super(firestore, Ask.class, "asks");
	}

	/** Find active (non-terminal) asks for a user. */
	public List<Ask> findActiveByUserId(String userId) {
		return executeQuery(getCollection().whereEqualTo("userId", userId)
			.whereIn("state", List.of("PENDING", "RUNNING"))
			.orderBy("createdAt", Query.Direction.DESCENDING));
	}

	/** Find recent asks for a user (all states). */
	public List<Ask> findRecentByUserId(String userId, int limit) {
		return executeQuery(getCollection().whereEqualTo("userId", userId)
			.orderBy("createdAt", Query.Direction.DESCENDING)
			.limit(limit));
	}

}
