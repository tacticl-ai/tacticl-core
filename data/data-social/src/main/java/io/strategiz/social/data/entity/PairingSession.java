package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.identity.data.base.annotation.Collection;
import io.cidadel.identity.data.base.entity.BaseEntity;
import java.time.Instant;

/**
 * Represents a QR-code-based pairing session for linking a mobile device to a desktop browser. The
 * desktop generates a session with a secret embedded in a QR code. When the mobile app scans the QR
 * and confirms, the session transitions from "waiting" to "paired" with a session token.
 */
@IgnoreExtraProperties
@Collection("pairing_sessions")
public class PairingSession extends BaseEntity {

	private String id;

	private String secret;

	private String userId;

	private String deviceId;

	private String sessionToken;

	private String deviceName;

	private String platform;

	private String status;

	private Instant expiresAt;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getSessionToken() {
		return sessionToken;
	}

	public void setSessionToken(String sessionToken) {
		this.sessionToken = sessionToken;
	}

	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	public String getPlatform() {
		return platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

}
