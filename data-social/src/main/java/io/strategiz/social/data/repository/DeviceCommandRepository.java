package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.DeviceCommand;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for device_commands Firestore collection. */
@Repository
public class DeviceCommandRepository extends FirestoreRepository<DeviceCommand> {

	public DeviceCommandRepository(Firestore firestore) {
		super(firestore, DeviceCommand.class, "device_commands");
	}

	/** Find pending commands for a device. */
	public List<DeviceCommand> findPendingByDeviceId(String deviceId) {
		return executeQuery(getCollection().whereEqualTo("deviceId", deviceId).whereEqualTo("state", "QUEUED"));
	}

	/** Find recent commands for a user. */
	public List<DeviceCommand> findRecentByUserId(String userId, int limit) {
		return executeQuery(
				getCollection().whereEqualTo("userId", userId).orderBy("createdAt").limit(limit));
	}

}
