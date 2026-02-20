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
		// Query all user asks and filter in-memory to avoid Firestore composite index requirement
		List<Ask> all = findByField("userId", userId);
		return all.stream()
			.filter(a -> "PENDING".equals(a.getState().name()) || "RUNNING".equals(a.getState().name()))
			.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
			.toList();
	}

	/** Find recent asks for a user (all states). */
	public List<Ask> findRecentByUserId(String userId, int limit) {
		List<Ask> all = findByField("userId", userId);
		return all.stream()
			.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
			.limit(limit)
			.toList();
	}

}
