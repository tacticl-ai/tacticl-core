package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/**
 * User's routing rule for a specific spark type. Maps spark types to preferred devices with
 * fallback policy.
 */
@IgnoreExtraProperties
public class DevicePreference {

	private String id;

	private String userId;

	private String sparkType;

	private String preferredDeviceId;

	private FallbackPolicy fallbackPolicy;

	public DevicePreference() {
		this.fallbackPolicy = FallbackPolicy.ANY_AVAILABLE;
	}

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

	public String getSparkType() {
		return sparkType;
	}

	public void setSparkType(String sparkType) {
		this.sparkType = sparkType;
	}

	public String getPreferredDeviceId() {
		return preferredDeviceId;
	}

	public void setPreferredDeviceId(String preferredDeviceId) {
		this.preferredDeviceId = preferredDeviceId;
	}

	public FallbackPolicy getFallbackPolicy() {
		return fallbackPolicy;
	}

	public void setFallbackPolicy(FallbackPolicy fallbackPolicy) {
		this.fallbackPolicy = fallbackPolicy;
	}

}
