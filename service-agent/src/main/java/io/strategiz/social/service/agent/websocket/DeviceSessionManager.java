package io.strategiz.social.service.agent.websocket;

import io.strategiz.social.business.agent.service.DeviceRegistryService;
import io.strategiz.social.data.entity.DeviceSession;
import io.strategiz.social.data.repository.DeviceSessionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Manages device WebSocket sessions. Tracks online devices, dispatches commands, and handles
 * connect/disconnect lifecycle.
 */
@Component
public class DeviceSessionManager {

	private static final Logger log = LoggerFactory.getLogger(DeviceSessionManager.class);

	private final DeviceSessionRepository sessionRepository;

	private final DeviceRegistryService registryService;

	private final SimpMessagingTemplate messagingTemplate;

	/** Maps WebSocket session ID to DevicePrincipal for disconnect handling. */
	private final ConcurrentHashMap<String, DevicePrincipal> sessionPrincipalMap = new ConcurrentHashMap<>();

	/** Maps deviceId to Firestore session ID for cleanup. */
	private final ConcurrentHashMap<String, String> deviceSessionIdMap = new ConcurrentHashMap<>();

	public DeviceSessionManager(DeviceSessionRepository sessionRepository, DeviceRegistryService registryService,
			SimpMessagingTemplate messagingTemplate) {
		this.sessionRepository = sessionRepository;
		this.registryService = registryService;
		this.messagingTemplate = messagingTemplate;
	}

	@EventListener
	public void handleSessionConnected(SessionConnectedEvent event) {
		if (event.getUser() instanceof DevicePrincipal principal) {
			String wsSessionId = extractSessionId(event);
			sessionPrincipalMap.put(wsSessionId, principal);

			// Create Firestore session record
			DeviceSession session = new DeviceSession();
			session.setId(UUID.randomUUID().toString());
			session.setDeviceId(principal.getDeviceId());
			session.setUserId(principal.getUserId());
			session.setConnectedAt(Instant.now());
			session.setLastPingAt(Instant.now());
			session.setActive(true);
			sessionRepository.save(session, session.getId());

			deviceSessionIdMap.put(principal.getDeviceId(), session.getId());

			log.info("Device connected: {} (user: {}, ws: {})", principal.getDeviceId(), principal.getUserId(),
					wsSessionId);
		}
	}

	@EventListener
	public void handleSessionDisconnect(SessionDisconnectEvent event) {
		String wsSessionId = event.getSessionId();
		DevicePrincipal principal = sessionPrincipalMap.remove(wsSessionId);

		if (principal != null) {
			// Mark Firestore session as inactive
			String firestoreSessionId = deviceSessionIdMap.remove(principal.getDeviceId());
			if (firestoreSessionId != null) {
				sessionRepository.findById(firestoreSessionId).ifPresent(session -> {
					session.setActive(false);
					sessionRepository.save(session, session.getId());
				});
			}

			log.info("Device disconnected: {} (user: {})", principal.getDeviceId(), principal.getUserId());
		}
	}

	/** Send a command to a specific device via its user-specific queue. */
	public void dispatchCommand(String userId, String deviceId, Map<String, Object> commandPayload) {
		// Route to the specific device's user queue
		messagingTemplate.convertAndSendToUser(userId, "/queue/commands", commandPayload);
		log.debug("Command dispatched to device {} (user {})", deviceId, userId);
	}

	/** Update heartbeat timestamp for a device session. */
	public void updateHeartbeat(String deviceId) {
		String sessionId = deviceSessionIdMap.get(deviceId);
		if (sessionId != null) {
			sessionRepository.findById(sessionId).ifPresent(session -> {
				session.setLastPingAt(Instant.now());
				sessionRepository.save(session, session.getId());
			});
		}
	}

	/** Check if a device is currently connected via WebSocket. */
	public boolean isDeviceConnected(String deviceId) {
		return deviceSessionIdMap.containsKey(deviceId);
	}

	/** Get the principal for a connected device. */
	public Optional<DevicePrincipal> getPrincipal(String deviceId) {
		return sessionPrincipalMap.values()
			.stream()
			.filter(p -> p.getDeviceId().equals(deviceId))
			.findFirst();
	}

	private String extractSessionId(SessionConnectedEvent event) {
		return event.getMessage().getHeaders().get("simpSessionId", String.class);
	}

}
