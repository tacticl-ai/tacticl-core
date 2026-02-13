package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.Reminder;
import org.springframework.stereotype.Repository;

/** Repository for reminders Firestore collection. */
@Repository
public class ReminderRepository extends FirestoreRepository<Reminder> {

	public ReminderRepository(Firestore firestore) {
		super(firestore, Reminder.class, "reminders");
	}

}
