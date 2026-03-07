package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.SubcollectionRepository;
import io.strategiz.social.data.entity.SparkTemplate;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for spark_templates subcollection under tacticl_users/{userId}/. */
@Repository
public class SparkTemplateRepository extends SubcollectionRepository<SparkTemplate> {

	public SparkTemplateRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, SparkTemplate.class, auditingHandler);
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
		return "spark_templates";
	}

	/** Find all active templates for a user. */
	public List<SparkTemplate> findActiveByUserId(String userId) {
		List<SparkTemplate> all = findAllInSubcollection(userId);
		return all.stream().filter(SparkTemplate::getIsActive).toList();
	}

}
