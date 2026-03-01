package io.strategiz.social.service.agent.controller;

import io.strategiz.framework.authorization.annotation.AuthUser;
import io.strategiz.framework.authorization.annotation.RequireAuth;
import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import io.strategiz.social.business.agent.service.UserConfigService;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceSettings;
import io.strategiz.social.data.entity.RepoGrant;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.repository.DeviceRepository;
import io.strategiz.social.data.repository.RepoGrantRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.service.agent.dto.ConnectionStatusResponse;
import io.strategiz.social.service.agent.dto.DeviceSettingsResponse;
import io.strategiz.social.service.agent.dto.UpdateConfigRequest;
import io.strategiz.social.service.agent.dto.UpdateDeviceSettingsRequest;
import io.strategiz.social.service.agent.dto.UserConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for user and device configuration (settings page backend). */
@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings", description = "User and device configuration")
public class SettingsController {

	private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

	private final UserConfigService userConfigService;

	private final DeviceRegistryService deviceRegistryService;

	private final DeviceRepository deviceRepository;

	private final SocialIntegrationRepository integrationRepository;

	private final RepoGrantRepository repoGrantRepository;

	public SettingsController(UserConfigService userConfigService, DeviceRegistryService deviceRegistryService,
			DeviceRepository deviceRepository, SocialIntegrationRepository integrationRepository,
			RepoGrantRepository repoGrantRepository) {
		this.userConfigService = userConfigService;
		this.deviceRegistryService = deviceRegistryService;
		this.deviceRepository = deviceRepository;
		this.integrationRepository = integrationRepository;
		this.repoGrantRepository = repoGrantRepository;
	}

	@GetMapping
	@RequireAuth
	@Operation(summary = "Get user config", description = "Returns the user's agent configuration with defaults")
	public ResponseEntity<UserConfigResponse> getConfig(@AuthUser AuthenticatedUser user) {
		UserConfig config = userConfigService.getConfig(user.getUserId());
		return ResponseEntity.ok(toConfigResponse(config));
	}

	@PutMapping
	@RequireAuth
	@Operation(summary = "Update user config", description = "Partial update of user agent configuration")
	public ResponseEntity<UserConfigResponse> updateConfig(@RequestBody UpdateConfigRequest request,
			@AuthUser AuthenticatedUser user) {
		log.info("Config update from user {}", user.getUserId());

		Map<String, Object> updates = request.toUpdateMap();
		if (updates.isEmpty()) {
			return ResponseEntity.ok(toConfigResponse(userConfigService.getConfig(user.getUserId())));
		}

		userConfigService.updateConfig(user.getUserId(), updates);
		UserConfig updated = userConfigService.getConfig(user.getUserId());
		return ResponseEntity.ok(toConfigResponse(updated));
	}

	@GetMapping("/devices/{deviceId}")
	@RequireAuth
	@Operation(summary = "Get device settings", description = "Returns settings for a specific device")
	public ResponseEntity<DeviceSettingsResponse> getDeviceSettings(@PathVariable String deviceId,
			@AuthUser AuthenticatedUser user) {
		Optional<DeviceRegistration> opt = deviceRegistryService.getDevice(deviceId, user.getUserId());
		if (opt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		DeviceRegistration device = opt.get();
		DeviceSettings settings = device.getSettings() != null ? device.getSettings() : DeviceSettings.defaults();
		return ResponseEntity.ok(toDeviceSettingsResponse(device, settings));
	}

	@PutMapping("/devices/{deviceId}")
	@RequireAuth
	@Operation(summary = "Update device settings", description = "Partial update of device settings")
	public ResponseEntity<DeviceSettingsResponse> updateDeviceSettings(@PathVariable String deviceId,
			@RequestBody UpdateDeviceSettingsRequest request, @AuthUser AuthenticatedUser user) {
		log.info("Device settings update from user {} for device {}", user.getUserId(), deviceId);

		Optional<DeviceRegistration> opt = deviceRegistryService.getDevice(deviceId, user.getUserId());
		if (opt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		DeviceRegistration device = opt.get();
		DeviceSettings settings = device.getSettings() != null ? device.getSettings() : DeviceSettings.defaults();

		if (request.getMaxDaemons() != null) {
			settings.setMaxDaemons(request.getMaxDaemons());
		}
		if (request.getAutoWake() != null) {
			settings.setAutoWake(request.getAutoWake());
		}
		if (request.getPriority() != null) {
			settings.setPriority(request.getPriority());
		}

		device.setSettings(settings);
		deviceRepository.save(user.getUserId(), device, device.getId());

		return ResponseEntity.ok(toDeviceSettingsResponse(device, settings));
	}

	@GetMapping("/connections")
	@RequireAuth
	@Operation(summary = "Get connection status",
			description = "Aggregated status of all devices, social integrations, and repos")
	public ResponseEntity<ConnectionStatusResponse> getConnections(@AuthUser AuthenticatedUser user) {
		String userId = user.getUserId();

		List<DeviceRegistration> devices = deviceRegistryService.listDevices(userId);
		List<SocialIntegration> integrations = integrationRepository.findAllByUserId(userId);
		List<RepoGrant> repos = repoGrantRepository.findActiveByUserId(userId);

		ConnectionStatusResponse response = new ConnectionStatusResponse();

		response.setDevices(devices.stream().map(d -> {
			ConnectionStatusResponse.DeviceSummary summary = new ConnectionStatusResponse.DeviceSummary();
			summary.setDeviceId(d.getId());
			summary.setDeviceName(d.getDeviceName());
			summary.setDeviceType(d.getDeviceType() != null ? d.getDeviceType().name() : null);
			summary.setState(d.getState() != null ? d.getState().name() : null);
			summary.setOnline(d.getConnectivity() != null && Boolean.TRUE.equals(d.getConnectivity().get("online")));
			return summary;
		}).toList());

		response.setIntegrations(integrations.stream().map(i -> {
			ConnectionStatusResponse.IntegrationSummary summary = new ConnectionStatusResponse.IntegrationSummary();
			summary.setPlatform(i.getPlatform() != null ? i.getPlatform().name() : null);
			summary.setPlatformUsername(i.getPlatformUsername());
			summary.setTokenRefreshNeeded(i.isTokenRefreshNeeded());
			return summary;
		}).toList());

		response.setRepos(repos.stream().map(r -> {
			ConnectionStatusResponse.RepoSummary summary = new ConnectionStatusResponse.RepoSummary();
			summary.setId(r.getId());
			summary.setProvider(r.getProvider() != null ? r.getProvider().name() : null);
			summary.setRepoFullName(r.getRepoFullName());
			summary.setAccessLevel(r.getAccessLevel() != null ? r.getAccessLevel().name() : null);
			return summary;
		}).toList());

		return ResponseEntity.ok(response);
	}

	private UserConfigResponse toConfigResponse(UserConfig config) {
		UserConfigResponse response = new UserConfigResponse();
		response.setMaxConcurrentSparks(config.getMaxConcurrentSparks());
		response.setSpendingLimit(config.getSpendingLimit());
		response.setDomainAllowlist(config.getDomainAllowlist());
		response.setDomainBlocklist(config.getDomainBlocklist());
		response.setConfirmationOverrides(config.getConfirmationOverrides());
		return response;
	}

	private DeviceSettingsResponse toDeviceSettingsResponse(DeviceRegistration device, DeviceSettings settings) {
		DeviceSettingsResponse response = new DeviceSettingsResponse();
		response.setDeviceId(device.getId());
		response.setDeviceName(device.getDeviceName());
		response.setMaxDaemons(settings.getMaxDaemons());
		response.setAutoWake(settings.isAutoWake());
		response.setPriority(settings.getPriority());
		return response;
	}

}
