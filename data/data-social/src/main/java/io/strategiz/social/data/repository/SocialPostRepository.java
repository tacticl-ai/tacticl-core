package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.PostState;
import io.strategiz.social.data.entity.SocialPost;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Repository for social_posts Firestore collection. */
@Repository
public class SocialPostRepository extends BaseRepository<SocialPost> {

	public SocialPostRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, SocialPost.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find posts by user and state. */
	public List<SocialPost> findByUserIdAndState(String userId, PostState state) {
		return executeQuery(getCollection().whereEqualTo("userId", userId).whereEqualTo("state", state.name()));
	}

	/** Find posts that are due for publishing (QUEUED with publishDate <= now). */
	public List<SocialPost> findDueForPublishing(Instant now) {
		return executeQuery(getCollection().whereEqualTo("state", PostState.QUEUED.name())
			.whereLessThanOrEqualTo("publishDate", now)
			.whereEqualTo("isActive", true));
	}

	protected List<SocialPost> executeQuery(com.google.cloud.firestore.Query query) {
		try {
			return query.get().get().getDocuments().stream()
				.map(doc -> { SocialPost entity = doc.toObject(entityClass); entity.setId(doc.getId()); return entity; })
				.collect(Collectors.toList());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Query interrupted", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("Query failed", e);
		}
	}

}
