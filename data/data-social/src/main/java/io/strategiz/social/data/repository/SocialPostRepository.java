package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.PostState;
import io.strategiz.social.data.entity.SocialPost;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for social_posts Firestore collection. */
@Repository
public class SocialPostRepository extends FirestoreRepository<SocialPost> {

	public SocialPostRepository(Firestore firestore) {
		super(firestore, SocialPost.class, "social_posts");
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

}
