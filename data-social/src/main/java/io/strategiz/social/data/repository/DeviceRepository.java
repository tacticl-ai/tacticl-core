package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.DeviceRegistration;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for devices Firestore collection. */
@Repository
public class DeviceRepository extends FirestoreRepository<DeviceRegistration> {

	public DeviceRepository(Firestore firestore) {
		super(firestore, DeviceRegistration.class, "devices");
	}

	/** Find all active devices for a user. */
	public List<DeviceRegistration> findActiveByUserId(String userId) {
		return executeQuery(
				getCollection().whereEqualTo("userId", userId).whereEqualTo("state", "ACTIVE").whereEqualTo("isActive", true));
	}

}
