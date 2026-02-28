package io.strategiz.social.business.agent.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One-time migration service that copies existing flat collection data into the
 * subcollection structure under tacticl_users/{userId}/.
 *
 * <p>Flat data is NOT deleted after migration (dual-read period).
 */
@Service
public class DataMigrationService {

	private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

	private static final String USER_COLLECTION = "tacticl_users";

	/** Maximum batch size for Firestore batch writes (Firestore limit is 500). */
	private static final int BATCH_SIZE = 400;

	/**
	 * Mapping of flat collection names to their subcollection names under tacticl_users/{userId}/.
	 * Key = flat collection name, Value = subcollection name.
	 */
	private static final Map<String, String> COLLECTION_MAPPING = Map.of(
			"devices", "devices",
			"social_integrations", "social_integrations",
			"repo_grants", "repo_grants",
			"agent_tokens", "agent_tokens",
			"reminders", "reminders",
			"spark_templates", "spark_templates"
	);

	private final Firestore firestore;

	public DataMigrationService(Firestore firestore) {
		this.firestore = firestore;
	}

	/**
	 * Migrate all flat collections to subcollections for all users.
	 *
	 * @return a summary of the migration
	 */
	public MigrationResult migrateAllUsers() {
		log.info("Starting flat-to-subcollection migration for all collections");

		MigrationResult result = new MigrationResult();

		for (Map.Entry<String, String> entry : COLLECTION_MAPPING.entrySet()) {
			String flatCollection = entry.getKey();
			String subcollectionName = entry.getValue();

			int migrated = migrateCollection(flatCollection, subcollectionName);
			result.addCollectionResult(flatCollection, migrated);
		}

		log.info("Migration complete. Total documents migrated: {}", result.getTotalMigrated());
		return result;
	}

	/**
	 * Migrate a single flat collection to subcollections.
	 * Reads all documents, groups by userId, and writes to tacticl_users/{userId}/{subcollection}/{docId}.
	 */
	int migrateCollection(String flatCollectionName, String subcollectionName) {
		log.info("Migrating collection: {} -> tacticl_users/{{userId}}/{}", flatCollectionName, subcollectionName);

		List<QueryDocumentSnapshot> docs;
		try {
			QuerySnapshot snapshot = firestore.collection(flatCollectionName).get().get();
			docs = snapshot.getDocuments();
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to read flat collection: " + flatCollectionName, e);
		}

		if (docs.isEmpty()) {
			log.info("No documents found in {}, skipping", flatCollectionName);
			return 0;
		}

		// Group documents by userId
		Map<String, List<QueryDocumentSnapshot>> byUser = new HashMap<>();
		int skipped = 0;
		for (QueryDocumentSnapshot doc : docs) {
			String userId = doc.getString("userId");
			if (userId == null || userId.isEmpty()) {
				log.warn("Skipping document {} in {} — no userId field", doc.getId(), flatCollectionName);
				skipped++;
				continue;
			}
			byUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(doc);
		}

		if (skipped > 0) {
			log.warn("Skipped {} documents without userId in {}", skipped, flatCollectionName);
		}

		// Write each user's documents to their subcollection
		int totalMigrated = 0;
		for (Map.Entry<String, List<QueryDocumentSnapshot>> userEntry : byUser.entrySet()) {
			String userId = userEntry.getKey();
			List<QueryDocumentSnapshot> userDocs = userEntry.getValue();

			int migrated = writeToSubcollection(userId, subcollectionName, userDocs);
			totalMigrated += migrated;
			log.debug("Migrated {} documents for user {} to {}", migrated, userId, subcollectionName);
		}

		log.info("Migrated {} documents from {} for {} users (skipped {})",
				totalMigrated, flatCollectionName, byUser.size(), skipped);
		return totalMigrated;
	}

	/** Write documents to a user's subcollection using batch writes. */
	private int writeToSubcollection(String userId, String subcollectionName,
			List<QueryDocumentSnapshot> docs) {
		int totalWritten = 0;

		for (int i = 0; i < docs.size(); i += BATCH_SIZE) {
			List<QueryDocumentSnapshot> batch = docs.subList(i, Math.min(i + BATCH_SIZE, docs.size()));
			WriteBatch writeBatch = firestore.batch();

			for (QueryDocumentSnapshot doc : batch) {
				DocumentReference targetRef = firestore.collection(USER_COLLECTION)
						.document(userId)
						.collection(subcollectionName)
						.document(doc.getId());

				// Check if already migrated (idempotent)
				writeBatch.set(targetRef, doc.getData());
			}

			try {
				writeBatch.commit().get();
				totalWritten += batch.size();
			}
			catch (InterruptedException | ExecutionException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Failed to write batch to " + USER_COLLECTION + "/"
						+ userId + "/" + subcollectionName, e);
			}
		}

		return totalWritten;
	}

	/**
	 * Migrate a single user's data from flat collections to subcollections.
	 * Useful for migrating individual users on-demand.
	 */
	public MigrationResult migrateUser(String userId) {
		log.info("Starting migration for user {}", userId);

		MigrationResult result = new MigrationResult();

		for (Map.Entry<String, String> entry : COLLECTION_MAPPING.entrySet()) {
			String flatCollection = entry.getKey();
			String subcollectionName = entry.getValue();

			int migrated = migrateUserCollection(userId, flatCollection, subcollectionName);
			result.addCollectionResult(flatCollection, migrated);
		}

		log.info("Migration complete for user {}. Total: {}", userId, result.getTotalMigrated());
		return result;
	}

	/** Migrate a single user's documents from a flat collection to a subcollection. */
	private int migrateUserCollection(String userId, String flatCollectionName, String subcollectionName) {
		try {
			QuerySnapshot snapshot = firestore.collection(flatCollectionName)
					.whereEqualTo("userId", userId)
					.get().get();

			List<QueryDocumentSnapshot> docs = snapshot.getDocuments();
			if (docs.isEmpty()) {
				return 0;
			}

			return writeToSubcollection(userId, subcollectionName, docs);
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to migrate " + flatCollectionName + " for user " + userId, e);
		}
	}

	/** Result summary of a migration operation. */
	public static class MigrationResult {

		private final List<CollectionResult> results = new ArrayList<>();

		private int totalMigrated = 0;

		void addCollectionResult(String collection, int count) {
			results.add(new CollectionResult(collection, count));
			totalMigrated += count;
		}

		public List<CollectionResult> getResults() {
			return results;
		}

		public int getTotalMigrated() {
			return totalMigrated;
		}

	}

	/** Per-collection migration result. */
	public static class CollectionResult {

		private final String collection;

		private final int documentsMigrated;

		public CollectionResult(String collection, int documentsMigrated) {
			this.collection = collection;
			this.documentsMigrated = documentsMigrated;
		}

		public String getCollection() {
			return collection;
		}

		public int getDocumentsMigrated() {
			return documentsMigrated;
		}

	}

}
