package io.strategiz.social.data.entity;

/** Supported device platforms for the Tacticl device agent system. */
public enum DeviceType {

	IPHONE("iPhone", 1), ANDROID("Android", 1), MACOS("macOS", 0), WINDOWS("Windows", 0), LINUX("Linux", 0);

	private final String displayName;

	private final int priority; // 0 = desktop (preferred), 1 = mobile

	DeviceType(String displayName, int priority) {
		this.displayName = displayName;
		this.priority = priority;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getPriority() {
		return priority;
	}

}
