package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.DeviceSession;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Repository for device_sessions Firestore collection. */
@Repository
public class DeviceSessionRepository extends FirestoreRepository<DeviceSession> {

	public DeviceSessionRepository(Firestore firestore) {
		super(firestore, DeviceSession.class, "device_sessions");
	}

	/** Find active session for a device. */
	public Optional<DeviceSession> findActiveByDeviceId(String deviceId) {
		List<DeviceSession> sessions = executeQuery(
				getCollection().whereEqualTo("deviceId", deviceId).whereEqualTo("isActive", true));
		return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.get(0));
	}

	/** Find all active sessions for a user's devices. */
	public List<DeviceSession> findActiveByUserId(String userId) {
		return executeQuery(getCollection().whereEqualTo("userId", userId).whereEqualTo("isActive", true));
	}

}
