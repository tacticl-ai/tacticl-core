package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.SubcollectionRepository;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.SocialIntegration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Repository;

/** Repository for social_integrations subcollection under tacticl_users/{userId}/. */
@Repository
public class SocialIntegrationRepository extends SubcollectionRepository<SocialIntegration> {

	public SocialIntegrationRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, SocialIntegration.class, auditingHandler);
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
		return "social_integrations";
	}

	/** Find integration by user and platform (active only). */
	public Optional<SocialIntegration> findByUserIdAndPlatform(String userId, PlatformType platform) {
		List<SocialIntegration> results = executeQuery(
			getSubcollection(userId)
				.whereEqualTo("platform", platform.name())
				.whereEqualTo("isActive", true));
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	/** Find all integrations for a user (active only). */
	public List<SocialIntegration> findAllByUserId(String userId) {
		return executeQuery(getSubcollection(userId).whereEqualTo("isActive", true));
	}

	/** Execute a custom query. */
	protected List<SocialIntegration> executeQuery(Query query) {
		try {
			QuerySnapshot snapshot = query.get().get();
			List<SocialIntegration> results = new ArrayList<>();
			for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
				results.add(doc.toObject(SocialIntegration.class));
			}
			return results;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to execute query on social_integrations", e);
		}
	}

}
