package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.ActionConfirmation;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Repository for action_confirmations Firestore collection. */
@Repository
public class ActionConfirmationRepository extends FirestoreRepository<ActionConfirmation> {

	public ActionConfirmationRepository(Firestore firestore) {
		super(firestore, ActionConfirmation.class, "action_confirmations");
	}

	/** Find pending confirmations for a user. */
	public List<ActionConfirmation> findPendingByUserId(String userId) {
		return executeQuery(getCollection().whereEqualTo("userId", userId).whereEqualTo("state", "PENDING"));
	}

	/** Find a pending confirmation by ID and user. */
	public Optional<ActionConfirmation> findPendingById(String confirmationId, String userId) {
		return findById(confirmationId).filter(
				c -> c.getUserId().equals(userId) && c.getState() == ActionConfirmation.ConfirmationState.PENDING);
	}

}
