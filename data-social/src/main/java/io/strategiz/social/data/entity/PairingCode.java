package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import java.time.Instant;

/**
 * Represents a short-lived pairing code used to link a device to a user's account. The code is a
 * 6-digit zero-padded string with a 5-minute TTL and single-use semantics.
 */
@IgnoreExtraProperties
public class PairingCode {

	private String id;

	private String userId;

	private String code;

	private Instant expiresAt;

	private boolean consumed;

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

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public boolean isConsumed() {
		return consumed;
	}

	public void setConsumed(boolean consumed) {
		this.consumed = consumed;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
