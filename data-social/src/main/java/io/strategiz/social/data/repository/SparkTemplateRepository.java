package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.SparkTemplate;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for spark_templates Firestore collection. */
@Repository
public class SparkTemplateRepository extends FirestoreRepository<SparkTemplate> {

	public SparkTemplateRepository(Firestore firestore) {
		super(firestore, SparkTemplate.class, "spark_templates");
	}

	/** Find all active templates for a user. */
	public List<SparkTemplate> findActiveByUserId(String userId) {
		List<SparkTemplate> all = findByField("userId", userId);
		return all.stream().filter(SparkTemplate::isActive).toList();
	}

}
