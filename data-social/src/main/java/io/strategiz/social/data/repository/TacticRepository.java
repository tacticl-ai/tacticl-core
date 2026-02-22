package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.Tactic;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for tactics Firestore collection. */
@Repository
public class TacticRepository extends FirestoreRepository<Tactic> {

	public TacticRepository(Firestore firestore) {
		super(firestore, Tactic.class, "tactics");
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
