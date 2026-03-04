package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.Exclude;
import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.identity.data.base.annotation.Collection;
import io.cidadel.identity.data.base.entity.BaseEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a device registered to a user's Tacticl account. Each device connects via WebSocket
 * and reports its capabilities for task routing.
 */
@IgnoreExtraProperties
@Collection("devices")
public class DeviceRegistration extends BaseEntity {

	private String id;

	private String userId;

	private String deviceName;

	private DeviceType deviceType;

	private String publicKeyFingerprint;

	private String pushToken;

	private Map<String, Object> capabilities;

	private Map<String, Object> connectivity;

	private DeviceState state;

	private String verificationCode;

	private Instant lastSeenAt;

	private Map<String, Object> specs;

	private List<String> clonedRepos;

	private int activeDaemons;

	private String daemonVersion;

	private Map<String, Object> sparkPreferences;

	private DeviceSettings settings;

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

	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	public DeviceType getDeviceType() {
		return deviceType;
	}

	public void setDeviceType(DeviceType deviceType) {
		this.deviceType = deviceType;
	}

	public String getPublicKeyFingerprint() {
		return publicKeyFingerprint;
	}

	public void setPublicKeyFingerprint(String publicKeyFingerprint) {
		this.publicKeyFingerprint = publicKeyFingerprint;
	}

	public String getPushToken() {
		return pushToken;
	}

	public void setPushToken(String pushToken) {
		this.pushToken = pushToken;
	}

	public Map<String, Object> getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(Map<String, Object> capabilities) {
		this.capabilities = capabilities;
	}

	public Map<String, Object> getConnectivity() {
		return connectivity;
	}

	public void setConnectivity(Map<String, Object> connectivity) {
		this.connectivity = connectivity;
	}

	public DeviceState getState() {
		return state;
	}

	public void setState(DeviceState state) {
		this.state = state;
	}

	public String getVerificationCode() {
		return verificationCode;
	}

	public void setVerificationCode(String verificationCode) {
		this.verificationCode = verificationCode;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(Instant lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

	public Map<String, Object> getSpecs() {
		return specs;
	}

	public void setSpecs(Map<String, Object> specs) {
		this.specs = specs;
	}

	public List<String> getClonedRepos() {
		return clonedRepos;
	}

	public void setClonedRepos(List<String> clonedRepos) {
		this.clonedRepos = clonedRepos;
	}

	public int getActiveDaemons() {
		return activeDaemons;
	}

	public void setActiveDaemons(int activeDaemons) {
		this.activeDaemons = activeDaemons;
	}

	public String getDaemonVersion() {
		return daemonVersion;
	}

	public void setDaemonVersion(String daemonVersion) {
		this.daemonVersion = daemonVersion;
	}

	public Map<String, Object> getSparkPreferences() {
		return sparkPreferences;
	}

	public void setSparkPreferences(Map<String, Object> sparkPreferences) {
		this.sparkPreferences = sparkPreferences;
	}

	public DeviceSettings getSettings() {
		return settings;
	}

	public void setSettings(DeviceSettings settings) {
		this.settings = settings;
	}

	/** Check if the device has a specific capability. */
	@Exclude
	public boolean hasCapability(String capabilityKey) {
		if (capabilities == null) {
			return false;
		}
		Object cap = capabilities.get(capabilityKey);
		if (cap instanceof Map) {
			Object available = ((Map<?, ?>) cap).get("available");
			return Boolean.TRUE.equals(available);
		}
		return cap != null;
	}

	/** Check if the device is currently charging. */
	@Exclude
	public boolean isCharging() {
		if (connectivity == null) {
			return false;
		}
		return Boolean.TRUE.equals(connectivity.get("isCharging"));
	}

	/** Get battery level (0-100), defaults to 0 if unknown. */
	@Exclude
	public int getBatteryLevel() {
		if (connectivity == null) {
			return 0;
		}
		Object level = connectivity.get("batteryLevel");
		if (level instanceof Number) {
			return ((Number) level).intValue();
		}
		return 0;
	}

}
