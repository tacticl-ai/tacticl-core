package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.DeviceSession;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Repository for device_sessions Firestore collection. */
@Repository
public class DeviceSessionRepository extends BaseRepository<DeviceSession> {

	public DeviceSessionRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, DeviceSession.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
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

	protected List<DeviceSession> executeQuery(com.google.cloud.firestore.Query query) {
		try {
			return query.get().get().getDocuments().stream()
				.map(doc -> { DeviceSession entity = doc.toObject(entityClass); entity.setId(doc.getId()); return entity; })
				.collect(Collectors.toList());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Query interrupted", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("Query failed", e);
		}
	}

}
