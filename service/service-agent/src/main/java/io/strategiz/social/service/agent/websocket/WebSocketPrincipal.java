package io.strategiz.social.service.agent.websocket;

import java.security.Principal;

/** Principal representing an authenticated WebSocket connection (device or user). */
public class WebSocketPrincipal implements Principal {

	private final String userId;

	private final String deviceId;

	public WebSocketPrincipal(String userId, String deviceId) {
		this.userId = userId;
		this.deviceId = deviceId;
	}

	@Override
	public String getName() {
		// STOMP uses getName() for user-destination routing
		// Use userId so /user/queue/commands routes to the correct user
		return userId;
	}

	public String getUserId() {
		return userId;
	}

	public String getDeviceId() {
		return deviceId;
	}

}
