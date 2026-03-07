package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.BaseRepository;
import io.strategiz.social.data.entity.ActionConfirmation;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Repository for action_confirmations Firestore collection. */
@Repository
public class ActionConfirmationRepository extends BaseRepository<ActionConfirmation> {

	public ActionConfirmationRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, ActionConfirmation.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
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

	protected List<ActionConfirmation> executeQuery(com.google.cloud.firestore.Query query) {
		try {
			return query.get().get().getDocuments().stream()
				.map(doc -> { ActionConfirmation entity = doc.toObject(entityClass); entity.setId(doc.getId()); return entity; })
				.collect(Collectors.toList());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Query interrupted", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("Query failed", e);
		}
	}

}
