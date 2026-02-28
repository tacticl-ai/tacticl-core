package io.strategiz.social.business.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataMigrationServiceTest {

	@Mock
	private Firestore firestore;

	private DataMigrationService migrationService;

	@BeforeEach
	void setUp() {
		migrationService = new DataMigrationService(firestore);
	}

	@Test
	@SuppressWarnings("unchecked")
	void migrateCollection_writesDocumentsToSubcollection() throws Exception {
		String flatCollection = "devices";
		String subcollection = "devices";

		// Mock flat collection read
		CollectionReference flatCol = mock(CollectionReference.class);
		when(firestore.collection(flatCollection)).thenReturn(flatCol);

		ApiFuture<QuerySnapshot> readFuture = mock(ApiFuture.class);
		when(flatCol.get()).thenReturn(readFuture);

		QuerySnapshot readSnapshot = mock(QuerySnapshot.class);
		when(readFuture.get()).thenReturn(readSnapshot);

		// One document with userId
		QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
		when(doc.getString("userId")).thenReturn("user-1");
		when(doc.getId()).thenReturn("device-abc");
		when(doc.getData()).thenReturn(Map.of("userId", "user-1", "deviceName", "MacBook"));
		when(readSnapshot.getDocuments()).thenReturn(List.of(doc));

		// Mock subcollection write path
		CollectionReference usersCol = mock(CollectionReference.class);
		when(firestore.collection("tacticl_users")).thenReturn(usersCol);

		DocumentReference userDoc = mock(DocumentReference.class);
		when(usersCol.document("user-1")).thenReturn(userDoc);

		CollectionReference subCol = mock(CollectionReference.class);
		when(userDoc.collection(subcollection)).thenReturn(subCol);

		DocumentReference targetDoc = mock(DocumentReference.class);
		when(subCol.document("device-abc")).thenReturn(targetDoc);

		// Mock batch write
		WriteBatch batch = mock(WriteBatch.class);
		when(firestore.batch()).thenReturn(batch);
		when(batch.set(any(DocumentReference.class), any())).thenReturn(batch);

		ApiFuture<?> commitFuture = mock(ApiFuture.class);
		doReturn(commitFuture).when(batch).commit();
		when(commitFuture.get()).thenReturn(null);

		int migrated = migrationService.migrateCollection(flatCollection, subcollection);

		assertEquals(1, migrated);
		verify(batch).set(targetDoc, Map.of("userId", "user-1", "deviceName", "MacBook"));
		verify(batch).commit();
	}

	@Test
	@SuppressWarnings("unchecked")
	void migrateCollection_emptyCollection_returnsZero() throws Exception {
		CollectionReference flatCol = mock(CollectionReference.class);
		when(firestore.collection("devices")).thenReturn(flatCol);

		ApiFuture<QuerySnapshot> readFuture = mock(ApiFuture.class);
		when(flatCol.get()).thenReturn(readFuture);

		QuerySnapshot readSnapshot = mock(QuerySnapshot.class);
		when(readFuture.get()).thenReturn(readSnapshot);
		when(readSnapshot.getDocuments()).thenReturn(Collections.emptyList());

		int migrated = migrationService.migrateCollection("devices", "devices");

		assertEquals(0, migrated);
		verify(firestore, never()).batch();
	}

	@Test
	@SuppressWarnings("unchecked")
	void migrateCollection_skipsDocumentsWithoutUserId() throws Exception {
		CollectionReference flatCol = mock(CollectionReference.class);
		when(firestore.collection("reminders")).thenReturn(flatCol);

		ApiFuture<QuerySnapshot> readFuture = mock(ApiFuture.class);
		when(flatCol.get()).thenReturn(readFuture);

		QuerySnapshot readSnapshot = mock(QuerySnapshot.class);
		when(readFuture.get()).thenReturn(readSnapshot);

		// Document without userId
		QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
		when(doc.getString("userId")).thenReturn(null);
		when(doc.getId()).thenReturn("orphan-doc");
		when(readSnapshot.getDocuments()).thenReturn(List.of(doc));

		int migrated = migrationService.migrateCollection("reminders", "reminders");

		assertEquals(0, migrated);
		verify(firestore, never()).batch();
	}

	@Test
	@SuppressWarnings("unchecked")
	void migrateCollection_readFailure_throws() throws Exception {
		CollectionReference flatCol = mock(CollectionReference.class);
		when(firestore.collection("devices")).thenReturn(flatCol);

		ApiFuture<QuerySnapshot> readFuture = mock(ApiFuture.class);
		when(flatCol.get()).thenReturn(readFuture);
		when(readFuture.get()).thenThrow(new ExecutionException("Firestore error", new RuntimeException()));

		assertThrows(RuntimeException.class,
				() -> migrationService.migrateCollection("devices", "devices"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void migrateCollection_groupsMultipleUserDocumentsCorrectly() throws Exception {
		CollectionReference flatCol = mock(CollectionReference.class);
		when(firestore.collection("repo_grants")).thenReturn(flatCol);

		ApiFuture<QuerySnapshot> readFuture = mock(ApiFuture.class);
		when(flatCol.get()).thenReturn(readFuture);

		QuerySnapshot readSnapshot = mock(QuerySnapshot.class);
		when(readFuture.get()).thenReturn(readSnapshot);

		// Two documents for different users
		QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
		when(doc1.getString("userId")).thenReturn("user-1");
		when(doc1.getId()).thenReturn("grant-1");
		when(doc1.getData()).thenReturn(Map.of("userId", "user-1"));

		QueryDocumentSnapshot doc2 = mock(QueryDocumentSnapshot.class);
		when(doc2.getString("userId")).thenReturn("user-2");
		when(doc2.getId()).thenReturn("grant-2");
		when(doc2.getData()).thenReturn(Map.of("userId", "user-2"));

		when(readSnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));

		// Mock subcollection write paths for both users
		CollectionReference usersCol = mock(CollectionReference.class);
		when(firestore.collection("tacticl_users")).thenReturn(usersCol);

		DocumentReference userDoc1 = mock(DocumentReference.class);
		when(usersCol.document("user-1")).thenReturn(userDoc1);
		CollectionReference subCol1 = mock(CollectionReference.class);
		when(userDoc1.collection("repo_grants")).thenReturn(subCol1);
		DocumentReference targetDoc1 = mock(DocumentReference.class);
		when(subCol1.document("grant-1")).thenReturn(targetDoc1);

		DocumentReference userDoc2 = mock(DocumentReference.class);
		when(usersCol.document("user-2")).thenReturn(userDoc2);
		CollectionReference subCol2 = mock(CollectionReference.class);
		when(userDoc2.collection("repo_grants")).thenReturn(subCol2);
		DocumentReference targetDoc2 = mock(DocumentReference.class);
		when(subCol2.document("grant-2")).thenReturn(targetDoc2);

		// Mock batch writes (one batch per user)
		WriteBatch batch1 = mock(WriteBatch.class);
		WriteBatch batch2 = mock(WriteBatch.class);
		when(firestore.batch()).thenReturn(batch1, batch2);
		when(batch1.set(any(DocumentReference.class), any())).thenReturn(batch1);
		when(batch2.set(any(DocumentReference.class), any())).thenReturn(batch2);

		ApiFuture<?> commitFuture1 = mock(ApiFuture.class);
		doReturn(commitFuture1).when(batch1).commit();
		when(commitFuture1.get()).thenReturn(null);

		ApiFuture<?> commitFuture2 = mock(ApiFuture.class);
		doReturn(commitFuture2).when(batch2).commit();
		when(commitFuture2.get()).thenReturn(null);

		int migrated = migrationService.migrateCollection("repo_grants", "repo_grants");

		assertEquals(2, migrated);
	}

}
