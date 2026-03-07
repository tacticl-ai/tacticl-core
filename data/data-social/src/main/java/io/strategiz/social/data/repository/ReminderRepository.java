package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.data.base.repository.SubcollectionRepository;
import io.strategiz.social.data.entity.Reminder;
import org.springframework.stereotype.Repository;

/** Repository for reminders subcollection under tacticl_users/{userId}/. */
@Repository
public class ReminderRepository extends SubcollectionRepository<Reminder> {

	public ReminderRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, Reminder.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-social";
	}

	@Override
	protected String getParentCollectionName() {
		return "tacticl_users";
	}

	@Override
	protected String getSubcollectionName() {
		return "reminders";
	}

}
