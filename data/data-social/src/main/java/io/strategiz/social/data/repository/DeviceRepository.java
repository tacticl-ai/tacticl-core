package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Repository for devices subcollection under tacticl_users/{userId}/devices/. */
@Repository
public class DeviceRepository extends FirestoreSubcollectionRepository<DeviceRegistration> {

	private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);

	public DeviceRepository(Firestore firestore) {
		super(firestore, DeviceRegistration.class, "devices");
	}

	/** Find all active devices for a user. */
	public List<DeviceRegistration> findActiveByUserId(String userId) {
		List<DeviceRegistration> all = findAll(userId);
		logger.debug("Found {} total devices for user {}", all.size(), userId);
		return all.stream()
			.filter(d -> d.getState() == DeviceState.ACTIVE)
			.filter(DeviceRegistration::getIsActive)
			.toList();
	}

}
