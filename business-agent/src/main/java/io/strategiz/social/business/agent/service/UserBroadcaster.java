package io.strategiz.social.business.agent.service;

import java.util.Map;

/** Broadcasts events to user browser/mobile clients via WebSocket. */
public interface UserBroadcaster {

	void broadcastToUser(String userId, Map<String, Object> payload);

}
