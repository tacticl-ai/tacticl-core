package io.strategiz.social.business.agent.service;

import io.cidadel.framework.token.issuer.PasetoTokenIssuer;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import io.strategiz.social.data.entity.DeviceType;
import io.strategiz.social.data.entity.PairingCode;
import io.strategiz.social.data.entity.PairingSession;
import io.strategiz.social.data.repository.DeviceRepository;
import io.strategiz.social.data.repository.PairingCodeRepository;
import io.strategiz.social.data.repository.PairingSessionRepository;
import com.google.cloud.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles device pairing via two flows: 6-digit code entry (desktop CLI) and QR scan (mobile app).
 * Both flows result in a DeviceRegistration and a PASETO session token for WebSocket auth.
 */
@Service
public class DevicePairingService {

	private static final Logger log = LoggerFactory.getLogger(DevicePairingService.class);

	private final PairingCodeRepository pairingCodeRepository;

	private final PairingSessionRepository pairingSessionRepository;

	private final DeviceRepository deviceRepository;

	private final PasetoTokenIssuer tokenIssuer;

	public DevicePairingService(PairingCodeRepository pairingCodeRepository,
			PairingSessionRepository pairingSessionRepository, DeviceRepository deviceRepository,
			PasetoTokenIssuer tokenIssuer) {
		this.pairingCodeRepository = pairingCodeRepository;
		this.pairingSessionRepository = pairingSessionRepository;
		this.deviceRepository = deviceRepository;
		this.tokenIssuer = tokenIssuer;
	}

	/**
	 * Generate a 6-digit pairing code for the given user. Invalidates any existing active codes for
	 * the user before creating a new one.
	 * @param userId the authenticated user's ID
	 * @return the newly created PairingCode with a 5-minute TTL
	 */
	public PairingCode generatePairingCode(String userId) {
		// Invalidate any existing active codes for this user
		List<PairingCode> activeCodes = pairingCodeRepository.findActiveByUserId(userId);
		for (PairingCode existing : activeCodes) {
			existing.setConsumed(true);
			pairingCodeRepository.save(existing, existing.getUserId());
		}

		// Generate new 6-digit code
		String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

		PairingCode pairingCode = new PairingCode();
		pairingCode.setId(UUID.randomUUID().toString());
		pairingCode.setUserId(userId);
		pairingCode.setCode(code);
		pairingCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
		pairingCode.setConsumed(false);
		pairingCode.setCreatedDate(Timestamp.now());

		pairingCodeRepository.save(pairingCode, userId);
		log.info("[PAIRING] Generated pairing code for user={}", userId);
		return pairingCode;
	}

	/**
	 * Pair a device using a 6-digit code. Validates the code, creates a DeviceRegistration, and
	 * issues a PASETO session token.
	 * @param code the 6-digit pairing code entered on the device
	 * @param deviceName the human-readable device name
	 * @param deviceType the device type string (must match DeviceType enum)
	 * @param platform the platform identifier (e.g. "macos", "windows")
	 * @param capabilities device capabilities map
	 * @return PairResult containing the device ID and session token
	 */
	public PairResult pairWithCode(String code, String deviceName, String deviceType, String platform,
			Map<String, Object> capabilities) {
		Optional<PairingCode> opt = pairingCodeRepository.findByCode(code);
		if (opt.isEmpty()) {
			throw new IllegalArgumentException("Invalid or expired pairing code");
		}

		PairingCode pairingCode = opt.get();
		if (pairingCode.isConsumed()) {
			throw new IllegalArgumentException("Pairing code has already been used");
		}
		if (pairingCode.getExpiresAt() != null && pairingCode.getExpiresAt().isBefore(Instant.now())) {
			throw new IllegalArgumentException("Pairing code has expired");
		}

		// Mark code as consumed
		pairingCode.setConsumed(true);
		pairingCodeRepository.save(pairingCode, pairingCode.getUserId());

		// Create device registration
		String deviceId = UUID.randomUUID().toString();
		DeviceRegistration device = new DeviceRegistration();
		device.setId(deviceId);
		device.setUserId(pairingCode.getUserId());
		device.setDeviceName(deviceName);
		device.setDeviceType(parseDeviceType(deviceType));
		device.setState(DeviceState.ACTIVE);
		device.setCapabilities(capabilities);
		device.setIsActive(true);
		device.setCreatedDate(Timestamp.now());
		deviceRepository.saveInSubcollection(pairingCode.getUserId(), device, pairingCode.getUserId());

		// Generate PASETO session token with userId as sub (for WebSocket interceptor)
		String sessionToken = tokenIssuer.createAuthenticationToken(pairingCode.getUserId(),
				List.of("device_pairing"), "1", Duration.ofDays(90), false);

		log.info("[PAIRING] Device paired via code: device={} user={}", deviceId, pairingCode.getUserId());
		return new PairResult(deviceId, sessionToken);
	}

	/**
	 * Create a new QR pairing session. The session ID and secret are embedded in a QR code displayed
	 * on the desktop browser. The mobile app scans it and calls pairWithQr.
	 * @return the newly created PairingSession with a 5-minute TTL
	 */
	public PairingSession createPairingSession() {
		PairingSession session = new PairingSession();
		session.setId(UUID.randomUUID().toString());
		session.setSecret(UUID.randomUUID().toString());
		session.setStatus("waiting");
		session.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
		session.setCreatedDate(Timestamp.now());

		pairingSessionRepository.save(session, session.getUserId());
		log.info("[PAIRING] Created QR pairing session={}", session.getId());
		return session;
	}

	/**
	 * Complete a QR-based pairing. Called by the mobile app after scanning the QR code. Creates a
	 * DeviceRegistration and updates the session so the desktop browser can poll for the result.
	 * @param sessionId the pairing session ID from the QR code
	 * @param secret the secret from the QR code (prevents session hijacking)
	 * @param userId the authenticated mobile user's ID
	 * @param deviceName the device name
	 * @param deviceType the device type string
	 * @param platform the platform identifier
	 * @param capabilities device capabilities map
	 * @return PairResult containing the device ID and session token
	 */
	public PairResult pairWithQr(String sessionId, String secret, String userId, String deviceName, String deviceType,
			String platform, Map<String, Object> capabilities) {
		Optional<PairingSession> opt = pairingSessionRepository.findById(sessionId);
		if (opt.isEmpty()) {
			throw new IllegalArgumentException("Pairing session not found");
		}

		PairingSession session = opt.get();
		if (!session.getSecret().equals(secret)) {
			throw new IllegalArgumentException("Invalid pairing session secret");
		}
		if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
			throw new IllegalArgumentException("Pairing session has expired");
		}
		if (!"waiting".equals(session.getStatus())) {
			throw new IllegalArgumentException("Pairing session is no longer available");
		}

		// Create device registration
		String deviceId = UUID.randomUUID().toString();
		DeviceRegistration device = new DeviceRegistration();
		device.setId(deviceId);
		device.setUserId(userId);
		device.setDeviceName(deviceName);
		device.setDeviceType(parseDeviceType(deviceType));
		device.setState(DeviceState.ACTIVE);
		device.setCapabilities(capabilities);
		device.setIsActive(true);
		device.setCreatedDate(Timestamp.now());
		deviceRepository.saveInSubcollection(userId, device, userId);

		// Generate PASETO session token with userId as sub (for WebSocket interceptor)
		String sessionToken = tokenIssuer.createAuthenticationToken(userId, List.of("device_pairing"), "1",
				Duration.ofDays(90), false);

		// Update session so desktop browser can poll the result
		session.setStatus("paired");
		session.setDeviceId(deviceId);
		session.setSessionToken(sessionToken);
		session.setUserId(userId);
		session.setDeviceName(deviceName);
		session.setPlatform(platform);
		pairingSessionRepository.save(session, userId);

		log.info("[PAIRING] Device paired via QR: device={} user={} session={}", deviceId, userId, sessionId);
		return new PairResult(deviceId, sessionToken);
	}

	/**
	 * Get the current status of a QR pairing session. Used by the desktop browser to poll for
	 * completion after displaying the QR code.
	 * @param sessionId the pairing session ID
	 * @param secret the session secret for validation
	 * @return a map with status and, if paired, the deviceId and sessionToken
	 */
	public Map<String, Object> getPairingSessionStatus(String sessionId, String secret) {
		Optional<PairingSession> opt = pairingSessionRepository.findById(sessionId);
		if (opt.isEmpty()) {
			throw new IllegalArgumentException("Pairing session not found");
		}

		PairingSession session = opt.get();
		if (!session.getSecret().equals(secret)) {
			throw new IllegalArgumentException("Invalid pairing session secret");
		}

		Map<String, Object> result = new HashMap<>();

		if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
			result.put("status", "expired");
			return result;
		}

		if ("waiting".equals(session.getStatus())) {
			result.put("status", "waiting");
			return result;
		}

		if ("paired".equals(session.getStatus())) {
			result.put("status", "paired");
			result.put("deviceId", session.getDeviceId());
			result.put("sessionToken", session.getSessionToken());
			return result;
		}

		result.put("status", session.getStatus());
		return result;
	}

	private DeviceType parseDeviceType(String deviceType) {
		if (deviceType == null || deviceType.isBlank()) {
			return DeviceType.MACOS;
		}
		try {
			return DeviceType.valueOf(deviceType.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			log.warn("[PAIRING] Unknown device type '{}', defaulting to MACOS", deviceType);
			return DeviceType.MACOS;
		}
	}

	/** Result of a successful device pairing containing the device ID and session token. */
	public record PairResult(String deviceId, String sessionToken) {
	}

}
