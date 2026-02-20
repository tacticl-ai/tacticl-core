package io.strategiz.social.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** A user-set reminder stored in the reminders Firestore collection. */
@IgnoreExtraProperties
public class Reminder {

	private String id;

	private String userId;

	private String message;

	private Instant remindAt;

	private boolean delivered;

	private Instant createdAt;

	public String getId() {
		return id;
	}

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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
