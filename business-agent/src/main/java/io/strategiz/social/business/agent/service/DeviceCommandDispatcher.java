package io.strategiz.social.business.agent.service;

import java.util.Map;

/** Interface for dispatching commands to devices via WebSocket. Implemented in service layer. */
public interface DeviceCommandDispatcher {

	/** Send a command payload to a specific device. */
	void dispatch(String userId, String deviceId, Map<String, Object> commandPayload);

	/** Check if a device is currently connected. */
	boolean isDeviceConnected(String deviceId);

}
