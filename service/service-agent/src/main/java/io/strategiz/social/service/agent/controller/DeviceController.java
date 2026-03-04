package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.service.DevicePairingService;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceState;
import io.strategiz.social.data.entity.DeviceType;
import io.strategiz.social.data.entity.PairingCode;
import io.strategiz.social.data.entity.PairingSession;
import io.strategiz.social.data.repository.DeviceSessionRepository;
import io.strategiz.social.service.agent.dto.DeviceRegistrationRequest;
import io.strategiz.social.service.agent.dto.DeviceRegistrationResponse;
import io.strategiz.social.service.agent.dto.DeviceStatusResponse;
import io.strategiz.social.service.agent.dto.DeviceVerifyRequest;
import io.strategiz.social.service.agent.dto.PairQrRequest;
import io.strategiz.social.service.agent.dto.PairResponse;
import io.strategiz.social.service.agent.dto.PairWithCodeRequest;
import io.strategiz.social.service.agent.dto.PairingCodeResponse;
import io.strategiz.social.service.agent.dto.PairingSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for device registration and management. */
@RestController
@RequestMapping("/api/devices")
@Tag(name = "Device Management", description = "Register, verify, and manage user devices for remote control")
public class DeviceController {

	private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

	private final DeviceRegistryService registryService;

	private final DevicePairingService pairingService;

	private final DeviceSessionRepository sessionRepository;

	public DeviceController(DeviceRegistryService registryService, DevicePairingService pairingService,
			DeviceSessionRepository sessionRepository) {
		this.registryService = registryService;
		this.pairingService = pairingService;
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
				device.getDeviceType().name(), device.getState().name(), verificationRequired, device.getCreatedDate() != null ? device.getCreatedDate().toDate().toInstant() : null);

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
				device.getDeviceType().name(), device.getState().name(), false, device.getCreatedDate() != null ? device.getCreatedDate().toDate().toInstant() : null);

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

	@PostMapping("/pairing-code")
	@RequireAuth
	@Operation(summary = "Generate device pairing code",
			description = "Generate a 6-digit code for pairing a device. The code expires in 5 minutes.")
	public ResponseEntity<PairingCodeResponse> createPairingCode(@AuthUser AuthenticatedUser user) {
		log.info("Pairing code requested by user {}", user.getUserId());
		PairingCode code = pairingService.generatePairingCode(user.getUserId());
		return ResponseEntity.ok(new PairingCodeResponse(code.getCode(), 300));
	}

	@PostMapping("/pair")
	@Operation(summary = "Pair device using pairing code",
			description = "Pair a device by entering a 6-digit code. No authentication required.")
	public ResponseEntity<PairResponse> pairWithCode(@Valid @RequestBody PairWithCodeRequest request) {
		log.info("Pairing attempt with code for device '{}'", request.getDeviceName());
		DevicePairingService.PairResult result = pairingService.pairWithCode(request.getCode(),
				request.getDeviceName(), request.getDeviceType(), request.getPlatform(), request.getCapabilities());
		return ResponseEntity.ok(new PairResponse(result.deviceId(), result.sessionToken()));
	}

	@PostMapping("/pairing-session")
	@Operation(summary = "Create QR pairing session",
			description = "Create a session for QR-based device pairing. No authentication required.")
	public ResponseEntity<PairingSessionResponse> createPairingSession() {
		log.info("QR pairing session creation requested");
		PairingSession session = pairingService.createPairingSession();
		return ResponseEntity.ok(new PairingSessionResponse(session.getId(), session.getSecret(), 300));
	}

	@PostMapping("/pair-qr")
	@RequireAuth
	@Operation(summary = "Confirm QR device pairing",
			description = "Complete a QR-based pairing after scanning the code on a mobile device.")
	public ResponseEntity<PairResponse> pairWithQr(@AuthUser AuthenticatedUser user,
			@Valid @RequestBody PairQrRequest request) {
		log.info("QR pairing confirmation by user {} for session {}", user.getUserId(), request.getSessionId());
		DevicePairingService.PairResult result = pairingService.pairWithQr(request.getSessionId(), request.getSecret(),
				user.getUserId(), request.getDeviceName(), request.getDeviceType(), request.getPlatform(),
				request.getCapabilities());
		return ResponseEntity.ok(new PairResponse(result.deviceId(), result.sessionToken()));
	}

	@GetMapping("/pairing-session/{id}/status")
	@Operation(summary = "Check QR pairing session status",
			description = "Poll for the status of a QR pairing session. No authentication required.")
	public ResponseEntity<Map<String, Object>> getPairingSessionStatus(@PathVariable String id,
			@RequestParam String secret) {
		Map<String, Object> status = pairingService.getPairingSessionStatus(id, secret);
		return ResponseEntity.ok(status);
	}

	@PutMapping("/{deviceId}/spark-preferences")
	@RequireAuth
	@Operation(summary = "Update device spark routing preferences",
			description = "Set which spark types this device should handle (e.g., code, social, research)")
	public ResponseEntity<Void> updateSparkPreferences(@AuthUser AuthenticatedUser user,
			@PathVariable String deviceId, @RequestBody Map<String, Object> preferences) {
		log.info("Updating spark preferences for device {} by user {}", deviceId, user.getUserId());

		boolean updated = registryService.updateSparkPreferences(deviceId, user.getUserId(), preferences);
		if (!updated) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok().build();
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
		response.setCreatedAt(device.getCreatedDate() != null ? device.getCreatedDate().toDate().toInstant() : null);
		return response;
	}

}
