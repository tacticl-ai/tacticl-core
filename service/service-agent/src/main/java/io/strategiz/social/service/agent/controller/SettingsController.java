package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import io.strategiz.social.business.agent.service.UserConfigService;
import io.strategiz.social.data.entity.ClaudeCodeConfig;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceSettings;
import io.strategiz.social.data.entity.ExecutionEngine;
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
@RequestMapping("/v1/settings")
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
		if (request.getExecutionEngine() != null) {
			try {
				settings.setExecutionEngine(ExecutionEngine.valueOf(request.getExecutionEngine().toUpperCase()));
			}
			catch (IllegalArgumentException e) {
				return ResponseEntity.badRequest().build();
			}
		}
		if (request.getClaudeCodeConfig() != null) {
			ClaudeCodeConfig ccConfig = settings.getClaudeCodeConfig();
			if (ccConfig == null) {
				ccConfig = ClaudeCodeConfig.defaults();
			}
			Map<String, Object> configMap = request.getClaudeCodeConfig();
			if (configMap.containsKey("model")) {
				ccConfig.setModel((String) configMap.get("model"));
			}
			if (configMap.containsKey("maxTurns")) {
				ccConfig.setMaxTurns(((Number) configMap.get("maxTurns")).intValue());
			}
			if (configMap.containsKey("maxBudgetUsd")) {
				ccConfig.setMaxBudgetUsd(new java.math.BigDecimal(configMap.get("maxBudgetUsd").toString()));
			}
			if (configMap.containsKey("permissionMode")) {
				ccConfig.setPermissionMode((String) configMap.get("permissionMode"));
			}
			if (configMap.containsKey("allowedTools")) {
				@SuppressWarnings("unchecked")
				List<String> tools = (List<String>) configMap.get("allowedTools");
				ccConfig.setAllowedTools(tools);
			}
			if (configMap.containsKey("disallowedTools")) {
				@SuppressWarnings("unchecked")
				List<String> tools = (List<String>) configMap.get("disallowedTools");
				ccConfig.setDisallowedTools(tools);
			}
			if (configMap.containsKey("mcpServers")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> servers = (Map<String, Object>) configMap.get("mcpServers");
				ccConfig.setMcpServers(servers);
			}
			if (configMap.containsKey("systemPromptOverride")) {
				ccConfig.setSystemPromptOverride((String) configMap.get("systemPromptOverride"));
			}
			settings.setClaudeCodeConfig(ccConfig);
		}

		device.setSettings(settings);
		deviceRepository.saveInSubcollection(user.getUserId(), device, user.getUserId());

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
		response.setExecutionEngine(
				settings.getExecutionEngine() != null ? settings.getExecutionEngine().name() : null);
		if (settings.getClaudeCodeConfig() != null) {
			ClaudeCodeConfig cc = settings.getClaudeCodeConfig();
			Map<String, Object> ccMap = new java.util.HashMap<>();
			ccMap.put("model", cc.getModel());
			ccMap.put("maxTurns", cc.getMaxTurns());
			ccMap.put("maxBudgetUsd", cc.getMaxBudgetUsd());
			ccMap.put("permissionMode", cc.getPermissionMode());
			if (cc.getAllowedTools() != null) ccMap.put("allowedTools", cc.getAllowedTools());
			if (cc.getDisallowedTools() != null) ccMap.put("disallowedTools", cc.getDisallowedTools());
			if (cc.getMcpServers() != null) ccMap.put("mcpServers", cc.getMcpServers());
			if (cc.getSystemPromptOverride() != null) ccMap.put("systemPromptOverride", cc.getSystemPromptOverride());
			response.setClaudeCodeConfig(ccMap);
		}
		response.setClaudeCodeVersion(device.getClaudeCodeVersion());
		return response;
	}

}
