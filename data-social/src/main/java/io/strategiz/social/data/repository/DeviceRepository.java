package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
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
		// Filter in-memory to avoid Firestore field name serialization issues
		// (boolean isActive may serialize as "active" not "isActive" depending on JavaBean conventions)
		List<DeviceRegistration> all = findByField("userId", userId);
		return all.stream()
			.filter(d -> d.getState() == DeviceState.ACTIVE)
			.filter(DeviceRegistration::isActive)
			.toList();
	}

}
