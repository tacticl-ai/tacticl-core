package io.strategiz.social.data.repository;

import com.google.api.core.ApiFuture;
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
 * Base Firestore repository providing common CRUD operations. Lightweight alternative to
 * strategiz-core's BaseRepository for tacticl-core MVP.
 *
 * @param <T> the entity type
 */
public abstract class FirestoreRepository<T> {

	private static final Logger logger = LoggerFactory.getLogger(FirestoreRepository.class);

	private final Firestore firestore;

	private final Class<T> entityClass;

	private final String collectionName;

	protected FirestoreRepository(Firestore firestore, Class<T> entityClass, String collectionName) {
		this.firestore = firestore;
		this.entityClass = entityClass;
		this.collectionName = collectionName;
	}

	protected CollectionReference getCollection() {
		return firestore.collection(collectionName);
	}

	/** Save or update an entity. If id is null, Firestore auto-generates one. */
	public T save(T entity, String id) {
		try {
			DocumentReference docRef;
			if (id != null) {
				docRef = getCollection().document(id);
			}
			else {
				docRef = getCollection().document();
			}
			docRef.set(entity).get();
			return entity;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to save entity to " + collectionName, e);
		}
	}

	/** Find entity by ID. */
	public Optional<T> findById(String id) {
		try {
			DocumentSnapshot doc = getCollection().document(id).get().get();
			if (doc.exists()) {
				return Optional.ofNullable(doc.toObject(entityClass));
			}
			return Optional.empty();
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to find entity in " + collectionName, e);
		}
	}

	/** Find all entities matching a field value. */
	public List<T> findByField(String fieldName, Object value) {
		try {
			QuerySnapshot snapshot = getCollection().whereEqualTo(fieldName, value).get().get();
			List<T> results = new ArrayList<>();
			for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
				results.add(doc.toObject(entityClass));
			}
			return results;
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to query " + collectionName, e);
		}
	}

	/** Find all active entities for a user. */
	public List<T> findByUserId(String userId) {
		return findByField("userId", userId);
	}

	/** Delete entity by ID (hard delete). */
	public void delete(String id) {
		try {
			getCollection().document(id).delete().get();
		}
		catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Failed to delete entity from " + collectionName, e);
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
			throw new RuntimeException("Failed to execute query on " + collectionName, e);
		}
	}

	protected Firestore getFirestore() {
		return firestore;
	}

	protected String getCollectionName() {
		return collectionName;
	}

}
