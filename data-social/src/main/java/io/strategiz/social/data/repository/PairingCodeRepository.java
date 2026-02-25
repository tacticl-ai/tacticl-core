package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.PairingCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Repository for pairing_codes Firestore collection. */
@Repository
public class PairingCodeRepository extends FirestoreRepository<PairingCode> {

	private static final Logger logger = LoggerFactory.getLogger(PairingCodeRepository.class);

	public PairingCodeRepository(Firestore firestore) {
		super(firestore, PairingCode.class, "pairing_codes");
	}

	/** Find a valid (unconsumed, not expired) pairing code by its code string. */
	public Optional<PairingCode> findByCode(String code) {
		List<PairingCode> results = findByField("code", code);
		Instant now = Instant.now();
		return results.stream()
			.filter(pc -> !pc.isConsumed())
			.filter(pc -> pc.getExpiresAt() != null && pc.getExpiresAt().isAfter(now))
			.findFirst();
	}

	/** Find all active (unconsumed) pairing codes for a user. */
	public List<PairingCode> findActiveByUserId(String userId) {
		List<PairingCode> all = findByField("userId", userId);
		return all.stream().filter(pc -> !pc.isConsumed()).toList();
	}

}
