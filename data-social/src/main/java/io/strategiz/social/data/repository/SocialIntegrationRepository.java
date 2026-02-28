package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.SocialIntegration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Repository for social_integrations subcollection under tacticl_users/{userId}/. */
@Repository
public class SocialIntegrationRepository extends FirestoreSubcollectionRepository<SocialIntegration> {

	public SocialIntegrationRepository(Firestore firestore) {
		super(firestore, SocialIntegration.class, "social_integrations");
	}

	/** Find integration by user and platform (active only). */
	public Optional<SocialIntegration> findByUserIdAndPlatform(String userId, PlatformType platform) {
		List<SocialIntegration> results = executeQuery(
			getCollectionForUser(userId)
				.whereEqualTo("platform", platform.name())
				.whereEqualTo("isActive", true));
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	/** Find all integrations for a user (active only). */
	public List<SocialIntegration> findAllByUserId(String userId) {
		return executeQuery(getCollectionForUser(userId).whereEqualTo("isActive", true));
	}

}
