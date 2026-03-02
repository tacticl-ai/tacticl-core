package io.strategiz.social.business.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDataPurgeServiceTest {

	@Mock
	private Firestore firestore;

	private UserDataPurgeService purgeService;

	@BeforeEach
	void setUp() {
		purgeService = new UserDataPurgeService(firestore);
	}

	@Test
	@SuppressWarnings("unchecked")
	void deleteFromFlatCollection_deletesMatchingDocuments() throws Exception {
		String userId = "user-123";
		String collectionName = "sparks";

		CollectionReference collection = mock(CollectionReference.class);
		when(firestore.collection(collectionName)).thenReturn(collection);

		Query query = mock(Query.class);
		when(collection.whereEqualTo("userId", userId)).thenReturn(query);

		ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
		when(query.get()).thenReturn(queryFuture);

		QuerySnapshot snapshot = mock(QuerySnapshot.class);
		when(queryFuture.get()).thenReturn(snapshot);

		QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
		QueryDocumentSnapshot doc2 = mock(QueryDocumentSnapshot.class);
		DocumentReference ref1 = mock(DocumentReference.class);
		DocumentReference ref2 = mock(DocumentReference.class);
		when(doc1.getReference()).thenReturn(ref1);
		when(doc2.getReference()).thenReturn(ref2);
		when(snapshot.getDocuments()).thenReturn(List.of(doc1, doc2));

		WriteBatch batch = mock(WriteBatch.class);
		when(firestore.batch()).thenReturn(batch);
		when(batch.delete(any(DocumentReference.class))).thenReturn(batch);

		ApiFuture<?> commitFuture = mock(ApiFuture.class);
		doReturn(commitFuture).when(batch).commit();
		when(commitFuture.get()).thenReturn(null);

		int deleted = purgeService.deleteFromFlatCollection(collectionName, userId);

		assertEquals(2, deleted);
		verify(batch).delete(ref1);
		verify(batch).delete(ref2);
		verify(batch).commit();
	}

	@Test
	@SuppressWarnings("unchecked")
	void deleteFromFlatCollection_emptyCollection_returnsZero() throws Exception {
		String userId = "user-123";
		String collectionName = "sparks";

		CollectionReference collection = mock(CollectionReference.class);
		when(firestore.collection(collectionName)).thenReturn(collection);

		Query query = mock(Query.class);
		when(collection.whereEqualTo("userId", userId)).thenReturn(query);

		ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
		when(query.get()).thenReturn(queryFuture);

		QuerySnapshot snapshot = mock(QuerySnapshot.class);
		when(queryFuture.get()).thenReturn(snapshot);
		when(snapshot.getDocuments()).thenReturn(Collections.emptyList());

		int deleted = purgeService.deleteFromFlatCollection(collectionName, userId);

		assertEquals(0, deleted);
	}

	@Test
	@SuppressWarnings("unchecked")
	void deleteSubcollection_deletesAllDocuments() throws Exception {
		String userId = "user-123";
		String subcollectionName = "devices";

		CollectionReference usersCol = mock(CollectionReference.class);
		when(firestore.collection("tacticl_users")).thenReturn(usersCol);

		DocumentReference userDoc = mock(DocumentReference.class);
		when(usersCol.document(userId)).thenReturn(userDoc);

		CollectionReference subcollection = mock(CollectionReference.class);
		when(userDoc.collection(subcollectionName)).thenReturn(subcollection);

		ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
		when(subcollection.get()).thenReturn(queryFuture);

		QuerySnapshot snapshot = mock(QuerySnapshot.class);
		when(queryFuture.get()).thenReturn(snapshot);

		QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
		DocumentReference ref1 = mock(DocumentReference.class);
		when(doc1.getReference()).thenReturn(ref1);
		when(snapshot.getDocuments()).thenReturn(List.of(doc1));

		WriteBatch batch = mock(WriteBatch.class);
		when(firestore.batch()).thenReturn(batch);
		when(batch.delete(any(DocumentReference.class))).thenReturn(batch);

		ApiFuture<?> commitFuture = mock(ApiFuture.class);
		doReturn(commitFuture).when(batch).commit();
		when(commitFuture.get()).thenReturn(null);

		int deleted = purgeService.deleteSubcollection(userId, subcollectionName);

		assertEquals(1, deleted);
		verify(batch).delete(ref1);
	}

	@Test
	@SuppressWarnings("unchecked")
	void purgeAllUserData_returnsCompleteSummary() throws Exception {
		// Stub all flat collections as empty
		for (String col : List.of("sparks", "tactics", "social_posts", "execution_logs",
				"checkpoints", "agent_audit_log", "action_confirmations", "device_commands",
				"device_sessions", "pairing_codes", "pairing_sessions")) {
			CollectionReference collection = mock(CollectionReference.class);
			when(firestore.collection(col)).thenReturn(collection);

			Query query = mock(Query.class);
			when(collection.whereEqualTo("userId", "user-1")).thenReturn(query);

			ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
			when(query.get()).thenReturn(future);

			QuerySnapshot snapshot = mock(QuerySnapshot.class);
			when(future.get()).thenReturn(snapshot);
			when(snapshot.getDocuments()).thenReturn(Collections.emptyList());
		}

		// Stub tacticl_users collection (used for subcollections and user doc delete)
		CollectionReference usersCol = mock(CollectionReference.class);
		when(firestore.collection("tacticl_users")).thenReturn(usersCol);

		DocumentReference userDoc = mock(DocumentReference.class);
		when(usersCol.document("user-1")).thenReturn(userDoc);

		// Stub all subcollections as empty
		for (String sub : List.of("devices", "social_integrations", "repo_grants",
				"agent_tokens", "reminders", "spark_templates")) {
			CollectionReference subcol = mock(CollectionReference.class);
			when(userDoc.collection(sub)).thenReturn(subcol);

			ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
			when(subcol.get()).thenReturn(future);

			QuerySnapshot snapshot = mock(QuerySnapshot.class);
			when(future.get()).thenReturn(snapshot);
			when(snapshot.getDocuments()).thenReturn(Collections.emptyList());
		}

		// Stub user document delete
		ApiFuture<?> deleteFuture = mock(ApiFuture.class);
		doReturn(deleteFuture).when(userDoc).delete();
		when(deleteFuture.get()).thenReturn(null);

		UserDataPurgeService.PurgeResult result = purgeService.purgeAllUserData("user-1");

		assertEquals("user-1", result.getUserId());
		// 11 flat + 6 subcollections + 1 user doc = 18 collection results
		assertEquals(18, result.getResults().size());
		// Only the user doc itself counts (1), everything else is empty
		assertEquals(1, result.getTotalDeleted());
	}

	@Test
	@SuppressWarnings("unchecked")
	void deleteFromFlatCollection_executionException_throws() throws Exception {
		CollectionReference collection = mock(CollectionReference.class);
		when(firestore.collection("sparks")).thenReturn(collection);

		Query query = mock(Query.class);
		when(collection.whereEqualTo("userId", "user-1")).thenReturn(query);

		ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
		when(query.get()).thenReturn(future);
		when(future.get()).thenThrow(new ExecutionException("Firestore error", new RuntimeException()));

		assertThrows(RuntimeException.class,
				() -> purgeService.deleteFromFlatCollection("sparks", "user-1"));
	}

}
