package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.Tactic;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for tactics Firestore collection. */
@Repository
public class TacticRepository extends BaseRepository<Tactic> {

	public TacticRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, Tactic.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	/** Find all tactics for a spark. */
	public List<Tactic> findBySparkId(String sparkId) {
		return findByField("sparkId", sparkId);
	}

	/** Find all tactics assigned to a device. */
	public List<Tactic> findByDeviceId(String deviceId) {
		return findByField("deviceId", deviceId);
	}

}
