package io.strategiz.social.business.agent.service;

import java.util.Map;

/**
 * Interface for broadcasting activity updates to all of a user's connected devices. Implemented by
 * DeviceSessionManager in the service layer.
 */
public interface ActivityBroadcaster {

	/** Broadcast an activity update to all devices owned by a user. */
	void broadcastActivity(String userId, Map<String, Object> activityPayload);

}
