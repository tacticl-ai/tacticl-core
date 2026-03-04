package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import io.strategiz.social.data.entity.DeviceType;
import io.strategiz.social.data.repository.DeviceRepository;
import io.strategiz.social.data.repository.DeviceSessionRepository;
import com.google.cloud.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Manages device registration, verification, and lifecycle. */
@Service
public class DeviceRegistryService {

	private static final Logger log = LoggerFactory.getLogger(DeviceRegistryService.class);

	private final DeviceRepository deviceRepository;

	private final DeviceSessionRepository sessionRepository;

	public DeviceRegistryService(DeviceRepository deviceRepository, DeviceSessionRepository sessionRepository) {
		this.deviceRepository = deviceRepository;
		this.sessionRepository = sessionRepository;
	}

	/** Register a new device for a user. Returns the device with a verification code. */
	public DeviceRegistration registerDevice(String userId, String deviceName, DeviceType deviceType,
			String pushToken) {
		DeviceRegistration device = new DeviceRegistration();
		device.setId(UUID.randomUUID().toString());
		device.setUserId(userId);
		device.setDeviceName(deviceName);
		device.setDeviceType(deviceType);
		device.setPushToken(pushToken);
		device.setState(DeviceState.PENDING_VERIFICATION);
		device.setVerificationCode(generateVerificationCode());
		device.setCreatedDate(Timestamp.now());
		device.setIsActive(true);

		deviceRepository.save(userId, device, device.getId());
		log.info("Device registered: {} ({}) for user {}", deviceName, deviceType, userId);
		return device;
	}

	/** Verify a device with the 6-digit code. */
	public Optional<DeviceRegistration> verifyDevice(String deviceId, String userId, String code) {
		Optional<DeviceRegistration> opt = deviceRepository.findById(userId, deviceId);
		if (opt.isEmpty()) {
			return Optional.empty();
		}

		DeviceRegistration device = opt.get();
		if (!device.getUserId().equals(userId)) {
			return Optional.empty();
		}
		if (device.getState() != DeviceState.PENDING_VERIFICATION) {
			return Optional.empty();
		}
		if (!code.equals(device.getVerificationCode())) {
			return Optional.empty();
		}

		device.setState(DeviceState.ACTIVE);
		device.setVerificationCode(null);
		deviceRepository.save(userId, device, device.getId());
		log.info("Device verified: {} for user {}", device.getDeviceName(), userId);
		return Optional.of(device);
	}

	/** Auto-verify a device (for the user's first device — no second device to send code to). */
	public DeviceRegistration autoVerifyDevice(String userId, String deviceName, DeviceType deviceType,
			String pushToken) {
		List<DeviceRegistration> existing = deviceRepository.findActiveByUserId(userId);
		if (!existing.isEmpty()) {
			throw new IllegalStateException("Auto-verify only allowed for first device");
		}

		DeviceRegistration device = registerDevice(userId, deviceName, deviceType, pushToken);
		device.setState(DeviceState.ACTIVE);
		device.setVerificationCode(null);
		deviceRepository.save(userId, device, device.getId());
		log.info("Device auto-verified (first device): {} for user {}", deviceName, userId);
		return device;
	}

	/** List all active devices for a user, enriched with online status. */
	public List<DeviceRegistration> listDevices(String userId) {
		List<DeviceRegistration> devices = deviceRepository.findActiveByUserId(userId);
		Set<String> onlineDeviceIds = sessionRepository.findActiveByUserId(userId)
			.stream()
			.map(s -> s.getDeviceId())
			.collect(Collectors.toSet());

		// Enrich connectivity with online status
		for (DeviceRegistration device : devices) {
			Map<String, Object> conn = device.getConnectivity();
			if (conn == null) {
				conn = new java.util.HashMap<>();
			}
			conn.put("online", onlineDeviceIds.contains(device.getId()));
			device.setConnectivity(conn);
		}
		return devices;
	}

	/** Get a single device by ID, verifying user ownership. */
	public Optional<DeviceRegistration> getDevice(String deviceId, String userId) {
		return deviceRepository.findById(userId, deviceId).filter(d -> d.getUserId().equals(userId) && d.getIsActive());
	}

	/** Revoke (soft-delete) a device. */
	public boolean revokeDevice(String deviceId, String userId) {
		Optional<DeviceRegistration> opt = getDevice(deviceId, userId);
		if (opt.isEmpty()) {
			return false;
		}
		DeviceRegistration device = opt.get();
		device.setState(DeviceState.REVOKED);
		device.setIsActive(false);
		deviceRepository.save(userId, device, device.getId());
		log.info("Device revoked: {} for user {}", device.getDeviceName(), userId);
		return true;
	}

	/** Update device capabilities (called on WebSocket connect). */
	public void updateCapabilities(String deviceId, String userId, Map<String, Object> capabilities) {
		deviceRepository.findById(userId, deviceId).ifPresent(device -> {
			device.setCapabilities(capabilities);
			device.setLastSeenAt(Instant.now());
			deviceRepository.save(userId, device, device.getId());
		});
	}

	/** Update device connectivity info (battery, network). */
	public void updateConnectivity(String deviceId, String userId, Map<String, Object> connectivity) {
		deviceRepository.findById(userId, deviceId).ifPresent(device -> {
			device.setConnectivity(connectivity);
			device.setLastSeenAt(Instant.now());
			deviceRepository.save(userId, device, device.getId());
		});
	}

	/** Update device spark routing preferences. Validates user ownership. */
	public boolean updateSparkPreferences(String deviceId, String userId, Map<String, Object> preferences) {
		Optional<DeviceRegistration> opt = getDevice(deviceId, userId);
		if (opt.isEmpty()) {
			return false;
		}
		DeviceRegistration device = opt.get();
		device.setSparkPreferences(preferences);
		deviceRepository.save(userId, device, device.getId());
		log.info("Spark preferences updated for device {} by user {}", deviceId, userId);
		return true;
	}

	private String generateVerificationCode() {
		return String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
	}

}
