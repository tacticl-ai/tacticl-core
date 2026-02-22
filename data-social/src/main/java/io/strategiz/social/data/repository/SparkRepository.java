package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.SparkState;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for sparks Firestore collection. */
@Repository
public class SparkRepository extends FirestoreRepository<Spark> {

	public SparkRepository(Firestore firestore) {
		super(firestore, Spark.class, "sparks");
	}

	/** Find active (non-terminal) sparks for a user. */
	public List<Spark> findActiveByUserId(String userId) {
		List<Spark> all = findByField("userId", userId);
		return all.stream()
			.filter(Spark::isActive)
			.filter(s -> s.getStatus() != SparkState.COMPLETED && s.getStatus() != SparkState.FAILED
					&& s.getStatus() != SparkState.CANCELLED)
			.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
			.toList();
	}

	/** Find sparks by status. */
	public List<Spark> findByStatus(SparkState status) {
		return findByField("status", status.name());
	}

	/** Find recent sparks for a user (all states). */
	public List<Spark> findRecentByUserId(String userId, int limit) {
		List<Spark> all = findByField("userId", userId);
		return all.stream()
			.filter(Spark::isActive)
			.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
			.limit(limit)
			.toList();
	}

}
