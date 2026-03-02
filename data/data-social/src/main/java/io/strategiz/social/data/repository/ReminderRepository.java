package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.Reminder;
import org.springframework.stereotype.Repository;

/** Repository for reminders subcollection under tacticl_users/{userId}/. */
@Repository
public class ReminderRepository extends FirestoreSubcollectionRepository<Reminder> {

	public ReminderRepository(Firestore firestore) {
		super(firestore, Reminder.class, "reminders");
	}

}
