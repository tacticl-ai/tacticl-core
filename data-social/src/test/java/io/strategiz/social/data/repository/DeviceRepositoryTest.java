package io.strategiz.social.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceRepositoryTest {

	private static final String USER_ID = "user-1";

	@Mock
	private Firestore firestore;

	@Mock
	private CollectionReference usersCol;

	@Mock
	private DocumentReference userDoc;

	@Mock
	private CollectionReference devicesCol;

	@Mock
	private ApiFuture<QuerySnapshot> queryFuture;

	@Mock
	private QuerySnapshot querySnapshot;

	@Mock
	private QueryDocumentSnapshot doc1;

	@Mock
	private QueryDocumentSnapshot doc2;

	private DeviceRepository repository;

	@BeforeEach
	void setUp() {
		when(firestore.collection("tacticl_users")).thenReturn(usersCol);
		when(usersCol.document(USER_ID)).thenReturn(userDoc);
		when(userDoc.collection("devices")).thenReturn(devicesCol);
		repository = new DeviceRepository(firestore);
	}

	@Test
	void findActiveByUserId_queriesSubcollection() {
		// Verify it uses the subcollection path, not a flat 'devices' collection
		repository.getCollectionForUser(USER_ID);

		verify(firestore).collection("tacticl_users");
		verify(usersCol).document(USER_ID);
		verify(userDoc).collection("devices");
		verify(firestore, never()).collection("devices");
	}

	@Test
	void findActiveByUserId_filtersInactiveDevices() throws Exception {
		DeviceRegistration active = new DeviceRegistration();
		active.setId("d-1");
		active.setState(DeviceState.ACTIVE);
		active.setActive(true);

		DeviceRegistration revoked = new DeviceRegistration();
		revoked.setId("d-2");
		revoked.setState(DeviceState.REVOKED);
		revoked.setActive(false);

		DeviceRegistration pending = new DeviceRegistration();
		pending.setId("d-3");
		pending.setState(DeviceState.PENDING_VERIFICATION);
		pending.setActive(true);

		when(devicesCol.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
		when(doc1.toObject(DeviceRegistration.class)).thenReturn(active);
		when(doc2.toObject(DeviceRegistration.class)).thenReturn(revoked);

		List<DeviceRegistration> result = repository.findActiveByUserId(USER_ID);

		assertEquals(1, result.size());
		assertEquals("d-1", result.get(0).getId());
	}

}
