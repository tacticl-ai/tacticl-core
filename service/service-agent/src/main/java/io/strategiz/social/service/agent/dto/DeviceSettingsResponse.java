package io.strategiz.social.service.agent.dto;

/** Response DTO for device settings. */
public class DeviceSettingsResponse {

	private String deviceId;

	private String deviceName;

	private int maxDaemons;

	private boolean autoWake;

	private int priority;

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	public int getMaxDaemons() {
		return maxDaemons;
	}

	public void setMaxDaemons(int maxDaemons) {
		this.maxDaemons = maxDaemons;
	}

	public boolean isAutoWake() {
		return autoWake;
	}

	public void setAutoWake(boolean autoWake) {
		this.autoWake = autoWake;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

}
