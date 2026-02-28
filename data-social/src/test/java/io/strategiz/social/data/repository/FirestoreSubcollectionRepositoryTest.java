package io.strategiz.social.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirestoreSubcollectionRepositoryTest {

	private static final String USER_ID = "user-123";

	private static final String SUBCOLLECTION_NAME = "devices";

	@Mock
	private Firestore firestore;

	@Mock
	private CollectionReference parentCollection;

	@Mock
	private DocumentReference userDocument;

	@Mock
	private CollectionReference subcollection;

	@Mock
	private DocumentReference documentRef;

	@Mock
	private ApiFuture<DocumentSnapshot> docSnapshotFuture;

	@Mock
	private DocumentSnapshot documentSnapshot;

	@Mock
	private ApiFuture<WriteResult> writeResultFuture;

	@Mock
	private ApiFuture<QuerySnapshot> querySnapshotFuture;

	@Mock
	private QuerySnapshot querySnapshot;

	@Mock
	private QueryDocumentSnapshot queryDocumentSnapshot;

	private TestSubcollectionRepo repository;

	@BeforeEach
	void setUp() {
		when(firestore.collection("tacticl_users")).thenReturn(parentCollection);
		when(parentCollection.document(USER_ID)).thenReturn(userDocument);
		when(userDocument.collection(SUBCOLLECTION_NAME)).thenReturn(subcollection);
		repository = new TestSubcollectionRepo(firestore);
	}

	@Test
	void getCollectionForUser_constructsCorrectPath() {
		CollectionReference result = repository.getCollectionForUser(USER_ID);

		assertEquals(subcollection, result);
		verify(firestore).collection("tacticl_users");
		verify(parentCollection).document(USER_ID);
		verify(userDocument).collection(SUBCOLLECTION_NAME);
	}

	@Test
	void save_withId_savesToCorrectPath() throws Exception {
		String entityId = "device-456";
		String entity = "test-device";
		when(subcollection.document(entityId)).thenReturn(documentRef);
		when(documentRef.set(entity)).thenReturn(writeResultFuture);
		when(writeResultFuture.get()).thenReturn(null);

		String result = repository.save(USER_ID, entity, entityId);

		assertEquals(entity, result);
		verify(subcollection).document(entityId);
		verify(documentRef).set(entity);
	}

	@Test
	void save_withNullId_autoGeneratesId() throws Exception {
		String entity = "test-device";
		when(subcollection.document()).thenReturn(documentRef);
		when(documentRef.set(entity)).thenReturn(writeResultFuture);
		when(writeResultFuture.get()).thenReturn(null);

		String result = repository.save(USER_ID, entity, null);

		assertEquals(entity, result);
		verify(subcollection).document();
		verify(documentRef).set(entity);
	}

	@Test
	void findById_existingDocument_returnsOptionalWithValue() throws Exception {
		String entityId = "device-456";
		when(subcollection.document(entityId)).thenReturn(documentRef);
		when(documentRef.get()).thenReturn(docSnapshotFuture);
		when(docSnapshotFuture.get()).thenReturn(documentSnapshot);
		when(documentSnapshot.exists()).thenReturn(true);
		when(documentSnapshot.toObject(String.class)).thenReturn("found-device");

		Optional<String> result = repository.findById(USER_ID, entityId);

		assertTrue(result.isPresent());
		assertEquals("found-device", result.get());
	}

	@Test
	void findById_nonExistingDocument_returnsEmpty() throws Exception {
		String entityId = "missing-device";
		when(subcollection.document(entityId)).thenReturn(documentRef);
		when(documentRef.get()).thenReturn(docSnapshotFuture);
		when(docSnapshotFuture.get()).thenReturn(documentSnapshot);
		when(documentSnapshot.exists()).thenReturn(false);

		Optional<String> result = repository.findById(USER_ID, entityId);

		assertTrue(result.isEmpty());
	}

	@Test
	void findAll_returnsAllEntities() throws Exception {
		when(subcollection.get()).thenReturn(querySnapshotFuture);
		when(querySnapshotFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(queryDocumentSnapshot));
		when(queryDocumentSnapshot.toObject(String.class)).thenReturn("device-a");

		List<String> result = repository.findAll(USER_ID);

		assertEquals(1, result.size());
		assertEquals("device-a", result.get(0));
	}

	@Test
	void findByField_returnsMatchingEntities() throws Exception {
		when(subcollection.whereEqualTo("status", "active")).thenReturn(subcollection);
		when(subcollection.get()).thenReturn(querySnapshotFuture);
		when(querySnapshotFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(queryDocumentSnapshot));
		when(queryDocumentSnapshot.toObject(String.class)).thenReturn("active-device");

		List<String> result = repository.findByField(USER_ID, "status", "active");

		assertEquals(1, result.size());
		assertEquals("active-device", result.get(0));
	}

	@Test
	void delete_deletesFromCorrectPath() throws Exception {
		String entityId = "device-456";
		when(subcollection.document(entityId)).thenReturn(documentRef);
		when(documentRef.delete()).thenReturn(writeResultFuture);
		when(writeResultFuture.get()).thenReturn(null);

		repository.delete(USER_ID, entityId);

		verify(subcollection).document(entityId);
		verify(documentRef).delete();
	}

	@Test
	void save_onExecutionException_throwsRuntimeException() throws Exception {
		String entityId = "device-456";
		when(subcollection.document(entityId)).thenReturn(documentRef);
		when(documentRef.set(any())).thenReturn(writeResultFuture);
		when(writeResultFuture.get()).thenThrow(new ExecutionException("Firestore error", new Exception()));

		assertThrows(RuntimeException.class, () -> repository.save(USER_ID, "entity", entityId));
	}

	@Test
	void getCollectionForUser_differentUsers_usesDifferentPaths() {
		String otherUserId = "user-789";
		DocumentReference otherUserDoc = org.mockito.Mockito.mock(DocumentReference.class);
		CollectionReference otherSubcollection = org.mockito.Mockito.mock(CollectionReference.class);
		when(parentCollection.document(otherUserId)).thenReturn(otherUserDoc);
		when(otherUserDoc.collection(SUBCOLLECTION_NAME)).thenReturn(otherSubcollection);

		CollectionReference result1 = repository.getCollectionForUser(USER_ID);
		CollectionReference result2 = repository.getCollectionForUser(otherUserId);

		assertEquals(subcollection, result1);
		assertEquals(otherSubcollection, result2);
	}

	/** Concrete test subclass to instantiate the abstract repository. */
	static class TestSubcollectionRepo extends FirestoreSubcollectionRepository<String> {

		TestSubcollectionRepo(Firestore firestore) {
			super(firestore, String.class, SUBCOLLECTION_NAME);
		}

	}

}
