package io.strategiz.social.data.repository;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base Firestore repository for subcollections under tacticl_users/{userId}/.
 * All operations are scoped to a specific user's document.
 *
 * @param <T> the entity type
 */
public abstract class FirestoreSubcollectionRepository<T> {

	private static final Logger logger = LoggerFactory.getLogger(FirestoreSubcollectionRepository.class);

	private static final String PARENT_COLLECTION = "tacticl_users";

	private final Firestore firestore;

	private final Class<T> entityClass;

	private final String subcollectionName;

	protected FirestoreSubcollectionRepository(Firestore firestore, Class<T> entityClass,
			String subcollectionName) {
		this.firestore = firestore;
		this.entityClass = entityClass;
		this.subcollectionName = subcollectionName;
	}

	/**
	 * Get the subcollection reference for a specific user.
	 * Path: tacticl_users/{userId}/{subcollectionName}
	 */
	protected CollectionReference getCollectionForUser(String userId) {
		return firestore.collection(PARENT_COLLECTION).document(userId).collection(subcollectionName);
	}

	/** Save or update an entity within a user's subcollection. If id is null, Firestore auto-generates one. */
	public T save(String userId, T entity, String id) {
		try {
			DocumentReference docRef;
			if (id != null) {
				docRef = getCollectionForUser(userId).document(id);
			}
			else {
				docRef = getCollectionForUser(userId).document();
			}
			docRef.set(entity).get();
			return entity;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(
					"Failed to save entity to " + PARENT_COLLECTION + "/" + userId + "/" + subcollectionName, e);
		}
	}

	/** Find entity by ID within a user's subcollection. */
	public Optional<T> findById(String userId, String id) {
		try {
			DocumentSnapshot doc = getCollectionForUser(userId).document(id).get().get();
			if (doc.exists()) {
				return Optional.ofNullable(doc.toObject(entityClass));
			}
			return Optional.empty();
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(
					"Failed to find entity in " + PARENT_COLLECTION + "/" + userId + "/" + subcollectionName, e);
		}
	}

	/** Find all entities within a user's subcollection. */
	public List<T> findAll(String userId) {
		try {
			QuerySnapshot snapshot = getCollectionForUser(userId).get().get();
			List<T> results = new ArrayList<>();
			for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
				results.add(doc.toObject(entityClass));
			}
			return results;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(
					"Failed to list entities in " + PARENT_COLLECTION + "/" + userId + "/" + subcollectionName, e);
		}
	}

	/** Find all entities matching a field value within a user's subcollection. */
	public List<T> findByField(String userId, String fieldName, Object value) {
		try {
			QuerySnapshot snapshot = getCollectionForUser(userId).whereEqualTo(fieldName, value).get().get();
			List<T> results = new ArrayList<>();
			for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
				results.add(doc.toObject(entityClass));
			}
			return results;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(
					"Failed to query " + PARENT_COLLECTION + "/" + userId + "/" + subcollectionName, e);
		}
	}

	/** Delete entity by ID within a user's subcollection (hard delete). */
	public void delete(String userId, String id) {
		try {
			getCollectionForUser(userId).document(id).delete().get();
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(
					"Failed to delete entity from " + PARENT_COLLECTION + "/" + userId + "/" + subcollectionName, e);
		}
	}

	/** Execute a custom query. */
	protected List<T> executeQuery(Query query) {
		try {
			QuerySnapshot snapshot = query.get().get();
			List<T> results = new ArrayList<>();
			for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
				results.add(doc.toObject(entityClass));
			}
			return results;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to execute query on " + subcollectionName, e);
		}
	}

	protected Firestore getFirestore() {
		return firestore;
	}

	protected String getSubcollectionName() {
		return subcollectionName;
	}

}
