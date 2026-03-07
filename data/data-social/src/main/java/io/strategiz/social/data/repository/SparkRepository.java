package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.SparkState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for sparks Firestore collection. */
@Repository
public class SparkRepository extends BaseRepository<Spark> {

	public SparkRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, Spark.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find active (non-terminal) sparks for a user. */
	public List<Spark> findActiveByUserId(String userId) {
		List<Spark> all = findByField("userId", userId);
		return all.stream()
			.filter(s -> s.getStatus() != SparkState.COMPLETED && s.getStatus() != SparkState.FAILED
					&& s.getStatus() != SparkState.CANCELLED)
			.sorted((a, b) -> b.getCreatedDate().compareTo(a.getCreatedDate()))
			.toList();
	}

	/** Find sparks by status. */
	public List<Spark> findByStatus(SparkState status) {
		return findByField("status", status.name());
	}

	/** Find scheduled sparks that are due for execution. */
	public List<Spark> findScheduledDue(Instant now) {
		List<Spark> scheduled = findByStatus(SparkState.SCHEDULED);
		List<Spark> completed = findByStatus(SparkState.COMPLETED);

		List<Spark> combined = new ArrayList<>();
		combined.addAll(scheduled);
		combined.addAll(completed);

		return combined.stream()
			.filter(s -> s.getSchedule() != null && !s.getSchedule().isEmpty())
			.filter(s -> s.getNextRunAt() != null && !s.getNextRunAt().isAfter(now))
			.toList();
	}

	/** Find sparks for a user with a specific status. */
	public List<Spark> findByUserIdAndStatus(String userId, SparkState status) {
		return findByField("userId", userId).stream()
			.filter(s -> s.getStatus() == status)
			.toList();
	}

	/** Find recent sparks for a user (all states). */
	public List<Spark> findRecentByUserId(String userId, int limit) {
		List<Spark> all = findByField("userId", userId);
		return all.stream()
			.sorted((a, b) -> b.getCreatedDate().compareTo(a.getCreatedDate()))
			.limit(limit)
			.toList();
	}

}
