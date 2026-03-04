package io.strategiz.social.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.identity.data.base.annotation.Collection;
import io.cidadel.identity.data.base.entity.BaseEntity;

/** A user-set reminder stored in the reminders Firestore collection. */
@IgnoreExtraProperties
@Collection("reminders")
public class Reminder extends BaseEntity {

	private String id;

	private String userId;

	private String message;

	private Instant remindAt;

	private boolean delivered;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Instant getRemindAt() {
		return remindAt;
	}

	public void setRemindAt(Instant remindAt) {
		this.remindAt = remindAt;
	}

	public boolean isDelivered() {
		return delivered;
	}

	public void setDelivered(boolean delivered) {
		this.delivered = delivered;
	}

}
