package io.strategiz.social.service.agent.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strategiz.social.business.agent.service.ActivityBroadcaster;
import io.strategiz.social.business.agent.service.DeviceCommandDispatcher;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.DeviceSession;
import io.strategiz.social.data.repository.DeviceSessionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Manages device WebSocket sessions. Tracks online devices, dispatches commands via direct
 * WebSocket send, and handles connect/disconnect lifecycle.
 */
@Component
public class DeviceSessionManager implements DeviceCommandDispatcher, ActivityBroadcaster {

	private static final Logger log = LoggerFactory.getLogger(DeviceSessionManager.class);

	private static final int SEND_TIME_LIMIT = 20_000;

	private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final DeviceSessionRepository sessionRepository;

	private final SparkService sparkService;

	/** Maps deviceId to thread-safe WebSocketSession for sending commands. */
	private final ConcurrentHashMap<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

	/** Maps deviceId to WebSocketPrincipal. */
	private final ConcurrentHashMap<String, WebSocketPrincipal> devicePrincipals = new ConcurrentHashMap<>();

	/** Maps raw WebSocket session ID to deviceId for disconnect cleanup. */
	private final ConcurrentHashMap<String, String> wsSessionToDevice = new ConcurrentHashMap<>();

	/** Maps deviceId to Firestore session ID. */
	private final ConcurrentHashMap<String, String> deviceFirestoreSessionMap = new ConcurrentHashMap<>();

	public DeviceSessionManager(DeviceSessionRepository sessionRepository, @Lazy SparkService sparkService) {
		this.sessionRepository = sessionRepository;
		this.sparkService = sparkService;
	}

	/** Register a new WebSocket session for a device. Closes any existing session for the same device. */
	public void registerSession(WebSocketSession session, WebSocketPrincipal principal) {
		String deviceId = principal.getDeviceId();

		// Close any existing session for this device (reconnection scenario)
		WebSocketSession oldSession = deviceSessions.get(deviceId);
		if (oldSession != null && oldSession.isOpen()) {
			try {
				oldSession.close(CloseStatus.NORMAL);
			}
			catch (Exception ignored) {
			}
		}

		WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT,
				BUFFER_SIZE_LIMIT);
		deviceSessions.put(deviceId, safeSession);
		devicePrincipals.put(deviceId, principal);
		wsSessionToDevice.put(session.getId(), deviceId);

		// Create Firestore session record
		DeviceSession fsSession = new DeviceSession();
		fsSession.setId(UUID.randomUUID().toString());
		fsSession.setDeviceId(deviceId);
		fsSession.setUserId(principal.getUserId());
		fsSession.setConnectedAt(Instant.now());
		fsSession.setLastPingAt(Instant.now());
		fsSession.setIsActive(true);
		sessionRepository.save(fsSession, principal.getUserId());
		deviceFirestoreSessionMap.put(deviceId, fsSession.getId());

		log.info("[WS] Session registered: device={}, user={}", deviceId, principal.getUserId());

		// Dispatch any sparks that were queued while this device was offline
		sparkService.dispatchQueuedSparks(deviceId, principal.getUserId());
	}

	/** Remove a WebSocket session on disconnect. Handles reconnection race conditions gracefully. */
	public void removeSession(WebSocketSession session) {
		String deviceId = wsSessionToDevice.remove(session.getId());
		if (deviceId == null) {
			return;
		}

		// Check if a newer session has already replaced this one
		WebSocketSession activeSession = deviceSessions.get(deviceId);
		if (activeSession != null && !activeSession.getId().equals(session.getId())) {
			return;
		}

		deviceSessions.remove(deviceId);
		devicePrincipals.remove(deviceId);

		String fsSessionId = deviceFirestoreSessionMap.remove(deviceId);
		if (fsSessionId != null) {
			sessionRepository.findById(fsSessionId).ifPresent(s -> {
				s.setIsActive(false);
				sessionRepository.save(s, s.getUserId());
			});
		}

		log.info("[WS] Session removed: device={}", deviceId);
	}

	/** Send a command to a specific device via its WebSocket session. */
	@Override
	public void dispatch(String userId, String deviceId, Map<String, Object> commandPayload) {
		WebSocketSession session = deviceSessions.get(deviceId);
		if (session == null || !session.isOpen()) {
			log.warn("[WS] Cannot dispatch: device {} not connected", deviceId);
			return;
		}
		try {
			String json = objectMapper.writeValueAsString(commandPayload);
			session.sendMessage(new TextMessage(json));
			log.info("[WS] Command dispatched to device {}", deviceId);
		}
		catch (Exception ex) {
			log.error("[WS] Failed to dispatch command to device {}", deviceId, ex);
		}
	}

	/** Update heartbeat timestamp for a device session. */
	public void updateHeartbeat(String deviceId) {
		String sessionId = deviceFirestoreSessionMap.get(deviceId);
		if (sessionId != null) {
			sessionRepository.findById(sessionId).ifPresent(s -> {
				s.setLastPingAt(Instant.now());
				sessionRepository.save(s, s.getUserId());
			});
		}
	}

	/** Check if a device is currently connected via WebSocket. */
	@Override
	public boolean isDeviceConnected(String deviceId) {
		WebSocketSession session = deviceSessions.get(deviceId);
		return session != null && session.isOpen();
	}

	/** Get the principal for a connected device. */
	public Optional<WebSocketPrincipal> getPrincipal(String deviceId) {
		return Optional.ofNullable(devicePrincipals.get(deviceId));
	}

	/** Broadcast an activity update to all devices owned by a user. */
	@Override
	public void broadcastActivity(String userId, Map<String, Object> activityPayload) {
		for (Map.Entry<String, WebSocketPrincipal> entry : devicePrincipals.entrySet()) {
			if (entry.getValue().getUserId().equals(userId)) {
				WebSocketSession session = deviceSessions.get(entry.getKey());
				if (session != null && session.isOpen()) {
					try {
						String json = objectMapper.writeValueAsString(activityPayload);
						session.sendMessage(new TextMessage(json));
					}
					catch (Exception ex) {
						log.error("[WS] Failed to broadcast activity to device {}", entry.getKey(), ex);
					}
				}
			}
		}
	}

}
