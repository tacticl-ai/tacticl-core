package io.strategiz.social.business.agent.service;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for purging all user data across flat collections and subcollections.
 * Supports GDPR right-to-erasure (Article 17) by deleting all data associated with a userId.
 */
@Service
public class UserDataPurgeService {

	private static final Logger log = LoggerFactory.getLogger(UserDataPurgeService.class);

	private static final String USER_COLLECTION = "tacticl_users";

	/** Maximum batch size for Firestore batch writes (Firestore limit is 500). */
	private static final int BATCH_SIZE = 400;

	/**
	 * Flat collections that store documents with a "userId" field.
	 * These are queried by userId and batch-deleted.
	 */
	private static final List<String> FLAT_COLLECTIONS = List.of(
			"sparks",
			"tactics",
			"social_posts",
			"execution_logs",
			"checkpoints",
			"agent_audit_log",
			"action_confirmations",
			"device_commands",
			"device_sessions",
			"pairing_codes",
			"pairing_sessions"
	);

	/**
	 * Subcollections under tacticl_users/{userId}/.
	 * These are enumerated and all documents within them are batch-deleted.
	 */
	private static final List<String> SUBCOLLECTIONS = List.of(
			"devices",
			"social_integrations",
			"repo_grants",
			"agent_tokens",
			"reminders",
			"spark_templates"
	);

	private final Firestore firestore;

	public UserDataPurgeService(Firestore firestore) {
		this.firestore = firestore;
	}

	/**
	 * Purge all data for a user. This includes:
	 * 1. All documents in flat collections where userId matches
	 * 2. All subcollections under tacticl_users/{userId}/
	 * 3. The tacticl_users/{userId} document itself
	 *
	 * @param userId the user whose data should be deleted
	 * @return a summary of what was deleted
	 */
	public PurgeResult purgeAllUserData(String userId) {
		log.info("Starting GDPR data purge for user {}", userId);

		PurgeResult result = new PurgeResult(userId);

		// Step 1: Delete from flat collections
		for (String collectionName : FLAT_COLLECTIONS) {
			int deleted = deleteFromFlatCollection(collectionName, userId);
			result.addCollectionResult(collectionName, deleted);
		}

		// Step 2: Delete subcollections under tacticl_users/{userId}/
		for (String subcollectionName : SUBCOLLECTIONS) {
			int deleted = deleteSubcollection(userId, subcollectionName);
			result.addCollectionResult(USER_COLLECTION + "/" + userId + "/" + subcollectionName, deleted);
		}

		// Step 3: Delete the user document itself
		deleteUserDocument(userId);
		result.addCollectionResult(USER_COLLECTION, 1);

		log.info("GDPR data purge complete for user {}. Total documents deleted: {}",
				userId, result.getTotalDeleted());

		return result;
	}

	/** Delete all documents in a flat collection where userId matches. */
	int deleteFromFlatCollection(String collectionName, String userId) {
		try {
			CollectionReference collection = firestore.collection(collectionName);
			QuerySnapshot snapshot = collection.whereEqualTo("userId", userId).get().get();
			List<QueryDocumentSnapshot> docs = snapshot.getDocuments();

			if (docs.isEmpty()) {
				return 0;
			}

			int deleted = batchDelete(docs.stream().map(QueryDocumentSnapshot::getReference).toList());
			log.debug("Deleted {} documents from {} for user {}", deleted, collectionName, userId);
			return deleted;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to purge " + collectionName + " for user " + userId, e);
		}
	}

	/** Delete all documents in a subcollection under tacticl_users/{userId}/. */
	int deleteSubcollection(String userId, String subcollectionName) {
		try {
			CollectionReference subcollection = firestore.collection(USER_COLLECTION)
					.document(userId)
					.collection(subcollectionName);
			QuerySnapshot snapshot = subcollection.get().get();
			List<QueryDocumentSnapshot> docs = snapshot.getDocuments();

			if (docs.isEmpty()) {
				return 0;
			}

			int deleted = batchDelete(docs.stream().map(QueryDocumentSnapshot::getReference).toList());
			log.debug("Deleted {} documents from {}/{}/{} for user {}",
					deleted, USER_COLLECTION, userId, subcollectionName, userId);
			return deleted;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(
					"Failed to purge subcollection " + subcollectionName + " for user " + userId, e);
		}
	}

	/** Delete the user document from tacticl_users. */
	private void deleteUserDocument(String userId) {
		try {
			firestore.collection(USER_COLLECTION).document(userId).delete().get();
			log.debug("Deleted user document tacticl_users/{}", userId);
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to delete user document for " + userId, e);
		}
	}

	/** Batch-delete a list of document references, respecting the Firestore batch size limit. */
	private int batchDelete(List<DocumentReference> docRefs) {
		int totalDeleted = 0;

		// Partition into batches
		for (int i = 0; i < docRefs.size(); i += BATCH_SIZE) {
			List<DocumentReference> batch = docRefs.subList(i, Math.min(i + BATCH_SIZE, docRefs.size()));
			WriteBatch writeBatch = firestore.batch();
			for (DocumentReference ref : batch) {
				writeBatch.delete(ref);
			}
			try {
				writeBatch.commit().get();
				totalDeleted += batch.size();
			}
			catch (InterruptedException | ExecutionException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Failed to commit batch delete", e);
			}
		}

		return totalDeleted;
	}

	/** Result summary of a purge operation. */
	public static class PurgeResult {

		private final String userId;

		private final List<CollectionResult> results = new ArrayList<>();

		private int totalDeleted = 0;

		public PurgeResult(String userId) {
			this.userId = userId;
		}

		void addCollectionResult(String collection, int count) {
			results.add(new CollectionResult(collection, count));
			totalDeleted += count;
		}

		public String getUserId() {
			return userId;
		}

		public List<CollectionResult> getResults() {
			return results;
		}

		public int getTotalDeleted() {
			return totalDeleted;
		}

	}

	/** Per-collection deletion result. */
	public static class CollectionResult {

		private final String collection;

		private final int documentsDeleted;

		public CollectionResult(String collection, int documentsDeleted) {
			this.collection = collection;
			this.documentsDeleted = documentsDeleted;
		}

		public String getCollection() {
			return collection;
		}

		public int getDocumentsDeleted() {
			return documentsDeleted;
		}

	}

}
