package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import io.strategiz.social.data.repository.DeviceRepository;
import io.strategiz.social.data.repository.DeviceSessionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Routes commands to the best available device based on required capabilities, online status,
 * battery level, and device type preference.
 */
@Service
public class DeviceRoutingService {

	private static final Logger log = LoggerFactory.getLogger(DeviceRoutingService.class);

	private final DeviceRepository deviceRepository;

	private final DeviceSessionRepository sessionRepository;

	public DeviceRoutingService(DeviceRepository deviceRepository, DeviceSessionRepository sessionRepository) {
		this.deviceRepository = deviceRepository;
		this.sessionRepository = sessionRepository;
	}

	/**
	 * Select the best online device that has the required capability.
	 * @param userId the user
	 * @param requiredCapability the capability key (e.g., "browser", "shortcuts", "terminal")
	 * @return the best matching device, or empty if none available
	 */
	public Optional<DeviceRegistration> selectDevice(String userId, String requiredCapability) {
		List<DeviceRegistration> activeDevices = deviceRepository.findActiveByUserId(userId);
		Set<String> onlineDeviceIds = sessionRepository.findActiveByUserId(userId)
			.stream()
			.map(s -> s.getDeviceId())
			.collect(Collectors.toSet());

		Optional<DeviceRegistration> selected = activeDevices.stream()
			.filter(d -> onlineDeviceIds.contains(d.getId()))
			.filter(d -> requiredCapability == null || d.hasCapability(requiredCapability))
			.sorted(Comparator.comparing(DeviceRegistration::isCharging)
				.reversed()
				.thenComparing(DeviceRegistration::getBatteryLevel, Comparator.reverseOrder())
				.thenComparing(d -> d.getDeviceType().getPriority()))
			.findFirst();

		if (selected.isPresent()) {
			log.debug("Routed to device: {} ({})", selected.get().getDeviceName(), selected.get().getDeviceType());
		}
		else {
			log.warn("No online device found for user {} with capability: {}", userId, requiredCapability);
		}

		return selected;
	}

	/** Select a specific device by ID, verifying it's online and owned by the user. */
	public Optional<DeviceRegistration> getOnlineDevice(String deviceId, String userId) {
		Optional<DeviceRegistration> device = deviceRepository.findById(userId, deviceId)
			.filter(d -> d.getUserId().equals(userId))
			.filter(d -> d.getState() == DeviceState.ACTIVE);

		if (device.isEmpty()) {
			return Optional.empty();
		}

		boolean online = sessionRepository.findActiveByDeviceId(deviceId).isPresent();
		if (!online) {
			log.warn("Device {} is registered but not online", deviceId);
			return Optional.empty();
		}

		return device;
	}

	/** Check if any device is online for a user. */
	public boolean hasOnlineDevice(String userId) {
		return !sessionRepository.findActiveByUserId(userId).isEmpty();
	}

	/** Build a summary of connected devices for the agent system prompt. */
	public String buildDeviceContext(String userId) {
		List<DeviceRegistration> devices = deviceRepository.findActiveByUserId(userId);
		if (devices.isEmpty()) {
			return "No devices registered yet. You can still use cloud tools (web search, "
					+ "content generation, social posting, etc.), but for tasks that require "
					+ "device access (running code, opening apps, file management), guide the "
					+ "user to set up device integration in the Tacticl app.\n";
		}

		Set<String> onlineDeviceIds = sessionRepository.findActiveByUserId(userId)
			.stream()
			.map(s -> s.getDeviceId())
			.collect(Collectors.toSet());

		boolean anyOnline = false;

		StringBuilder sb = new StringBuilder();
		for (DeviceRegistration device : devices) {
			boolean online = onlineDeviceIds.contains(device.getId());
			if (online) {
				anyOnline = true;
			}
			sb.append("- \"")
				.append(device.getDeviceName())
				.append("\" (")
				.append(device.getDeviceType().getDisplayName())
				.append(", ")
				.append(online ? "ONLINE — available for task dispatch" : "offline")
				.append(", ")
				.append(device.getBatteryLevel())
				.append("% battery");

			if (device.isCharging()) {
				sb.append(", charging");
			}
			sb.append(")\n");

			if (device.getCapabilities() != null && !device.getCapabilities().isEmpty()) {
				sb.append("  Capabilities: ");
				sb.append(device.getCapabilities()
					.entrySet()
					.stream()
					.filter(e -> {
						Object v = e.getValue();
						if (v instanceof java.util.Map) {
							return Boolean.TRUE.equals(((java.util.Map<?, ?>) v).get("available"));
						}
						return v != null;
					})
					.map(e -> e.getKey())
					.collect(Collectors.joining(", ")));
				sb.append("\n");
			}
		}

		if (!anyOnline) {
			sb.append("\nAll devices are currently offline. For tasks requiring device access, "
					+ "let the user know they need a device online. Use your cloud tools for "
					+ "what you can handle now.\n");
		}

		return sb.toString();
	}

}
