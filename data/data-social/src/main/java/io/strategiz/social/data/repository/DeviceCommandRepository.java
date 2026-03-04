package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.DeviceCommand;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Repository for device_commands Firestore collection. */
@Repository
public class DeviceCommandRepository extends BaseRepository<DeviceCommand> {

	public DeviceCommandRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, DeviceCommand.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
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

	protected List<DeviceCommand> executeQuery(com.google.cloud.firestore.Query query) {
		try {
			return query.get().get().getDocuments().stream()
				.map(doc -> { DeviceCommand entity = doc.toObject(entityClass); entity.setId(doc.getId()); return entity; })
				.collect(Collectors.toList());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Query interrupted", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("Query failed", e);
		}
	}

}
