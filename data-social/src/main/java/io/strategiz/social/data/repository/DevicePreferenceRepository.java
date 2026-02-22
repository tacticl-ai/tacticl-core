package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.DevicePreference;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for device_preferences Firestore collection. */
@Repository
public class DevicePreferenceRepository extends FirestoreRepository<DevicePreference> {

	public DevicePreferenceRepository(Firestore firestore) {
		super(firestore, DevicePreference.class, "device_preferences");
	}

	/** Find all preferences for a user. */
	public List<DevicePreference> findAllByUserId(String userId) {
		return findByField("userId", userId);
	}

}
