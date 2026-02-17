package io.strategiz.social.service.agent.controller;

import io.strategiz.framework.authorization.annotation.AuthUser;
import io.strategiz.framework.authorization.annotation.RequireAuth;
import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import io.strategiz.social.data.entity.DeviceType;
import io.strategiz.social.data.repository.DeviceSessionRepository;
import io.strategiz.social.service.agent.dto.DeviceRegistrationRequest;
import io.strategiz.social.service.agent.dto.DeviceRegistrationResponse;
import io.strategiz.social.service.agent.dto.DeviceStatusResponse;
import io.strategiz.social.service.agent.dto.DeviceVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for device registration and management. */
@RestController
@RequestMapping("/api/devices")
@Tag(name = "Device Management", description = "Register, verify, and manage user devices for remote control")
public class DeviceController {

	private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

	private final DeviceRegistryService registryService;

	private final DeviceSessionRepository sessionRepository;

	public DeviceController(DeviceRegistryService registryService, DeviceSessionRepository sessionRepository) {
		this.registryService = registryService;
		this.sessionRepository = sessionRepository;
	}

	@PostMapping("/register")
	@RequireAuth
	@Operation(summary = "Register a new device",
			description = "Register a device for remote control. First device is auto-verified.")
	public ResponseEntity<DeviceRegistrationResponse> registerDevice(
			@Valid @RequestBody DeviceRegistrationRequest request, @AuthUser AuthenticatedUser user) {
		log.info("Device registration from user {}: {} ({})", user.getUserId(), request.getDeviceName(),
				request.getDeviceType());

		DeviceType deviceType;
		try {
			deviceType = DeviceType.valueOf(request.getDeviceType().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}

		DeviceRegistration device;
		List<DeviceRegistration> existingDevices = registryService.listDevices(user.getUserId());

		if (existingDevices.isEmpty()) {
			// First device — auto-verify
			device = registryService.autoVerifyDevice(user.getUserId(), request.getDeviceName(), deviceType,
					request.getPushToken());
		}
		else {
			device = registryService.registerDevice(user.getUserId(), request.getDeviceName(), deviceType,
					request.getPushToken());
		}

		boolean verificationRequired = device.getState() == DeviceState.PENDING_VERIFICATION;
		DeviceRegistrationResponse response = new DeviceRegistrationResponse(device.getId(), device.getDeviceName(),
				device.getDeviceType().name(), device.getState().name(), verificationRequired, device.getCreatedAt());

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/{deviceId}/verify")
	@RequireAuth
	@Operation(summary = "Verify a device", description = "Verify a device with the 6-digit code sent to another device")
	public ResponseEntity<DeviceRegistrationResponse> verifyDevice(@PathVariable String deviceId,
			@Valid @RequestBody DeviceVerifyRequest request, @AuthUser AuthenticatedUser user) {
		log.info("Device verification for {} by user {}", deviceId, user.getUserId());

		Optional<DeviceRegistration> verified = registryService.verifyDevice(deviceId, user.getUserId(),
				request.getCode());

		if (verified.isEmpty()) {
			return ResponseEntity.badRequest().build();
		}

		DeviceRegistration device = verified.get();
		DeviceRegistrationResponse response = new DeviceRegistrationResponse(device.getId(), device.getDeviceName(),
				device.getDeviceType().name(), device.getState().name(), false, device.getCreatedAt());

		return ResponseEntity.ok(response);
	}

	@GetMapping
	@RequireAuth
	@Operation(summary = "List devices", description = "List all registered devices with online status")
	public ResponseEntity<List<DeviceStatusResponse>> listDevices(@AuthUser AuthenticatedUser user) {
		List<DeviceRegistration> devices = registryService.listDevices(user.getUserId());

		List<DeviceStatusResponse> response = devices.stream().map(this::toStatusResponse).toList();
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{deviceId}/status")
	@RequireAuth
	@Operation(summary = "Get device status", description = "Get detailed status and capabilities of a device")
	public ResponseEntity<DeviceStatusResponse> getDeviceStatus(@PathVariable String deviceId,
			@AuthUser AuthenticatedUser user) {
		Optional<DeviceRegistration> device = registryService.getDevice(deviceId, user.getUserId());

		if (device.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok(toStatusResponse(device.get()));
	}

	@DeleteMapping("/{deviceId}")
	@RequireAuth
	@Operation(summary = "Revoke a device", description = "Remove a device from the user's account")
	public ResponseEntity<Void> revokeDevice(@PathVariable String deviceId, @AuthUser AuthenticatedUser user) {
		log.info("Device revocation for {} by user {}", deviceId, user.getUserId());

		boolean revoked = registryService.revokeDevice(deviceId, user.getUserId());
		if (!revoked) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.noContent().build();
	}

	private DeviceStatusResponse toStatusResponse(DeviceRegistration device) {
		boolean online = sessionRepository.findActiveByDeviceId(device.getId()).isPresent();

		DeviceStatusResponse response = new DeviceStatusResponse();
		response.setDeviceId(device.getId());
		response.setDeviceName(device.getDeviceName());
		response.setDeviceType(device.getDeviceType().name());
		response.setState(device.getState().name());
		response.setOnline(online);
		response.setCapabilities(device.getCapabilities());
		response.setConnectivity(device.getConnectivity());
		response.setLastSeenAt(device.getLastSeenAt());
		response.setCreatedAt(device.getCreatedAt());
		return response;
	}

}
