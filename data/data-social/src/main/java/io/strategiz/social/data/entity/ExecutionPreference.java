package io.strategiz.social.data.entity;

/** User preference for how sparks should be routed for execution. */
public enum ExecutionPreference {

	DEVICE_FIRST,   // Try device, fall back to cloud browser (default)
	CLOUD_FIRST,    // Try cloud browser, fall back to device
	CLOUD_ONLY      // Never route to device

}
