package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Repository for devices Firestore collection. */
@Repository
public class DeviceRepository extends FirestoreRepository<DeviceRegistration> {

	private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);

	public DeviceRepository(Firestore firestore) {
		super(firestore, DeviceRegistration.class, "devices");
	}

	/** Find all active devices for a user. */
	public List<DeviceRegistration> findActiveByUserId(String userId) {
		// Filter in-memory to avoid Firestore field name serialization issues
		// (boolean isActive may serialize as "active" not "isActive" depending on JavaBean conventions)
		List<DeviceRegistration> all = findByField("userId", userId);
		logger.warn("[DEVICE-DEBUG] findActiveByUserId('{}') — findByField returned {} documents", userId,
				all.size());
		for (DeviceRegistration d : all) {
			logger.warn("[DEVICE-DEBUG]   device id={}, name={}, state={}, isActive={}", d.getId(),
					d.getDeviceName(), d.getState(), d.isActive());
		}
		List<DeviceRegistration> filtered = all.stream()
			.filter(d -> d.getState() == DeviceState.ACTIVE)
			.filter(DeviceRegistration::isActive)
			.toList();
		logger.warn("[DEVICE-DEBUG] After filtering: {} devices", filtered.size());
		return filtered;
	}

}
