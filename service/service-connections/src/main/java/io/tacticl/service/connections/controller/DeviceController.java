package io.tacticl.service.connections.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.connections.service.DeviceRegistryService;
import io.tacticl.data.connections.entity.Device;
import io.tacticl.service.connections.dto.DeviceSummaryDto;
import io.tacticl.service.connections.dto.PairingTokenResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/connections/devices")
public class DeviceController extends BaseController {

    @Override
    protected String getModuleName() {
        return "devices";
    }

    private final DeviceRegistryService deviceRegistryService;

    public DeviceController(DeviceRegistryService deviceRegistryService) {
        this.deviceRegistryService = deviceRegistryService;
    }

    @PostMapping("/pair")
    @RequireAuth
    public ResponseEntity<PairingTokenResponseDto> generatePairingToken(
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        var result = deviceRegistryService.generatePairingToken(userId);
        return ResponseEntity.ok(new PairingTokenResponseDto(
            result.token(), result.expiresAt().toString()));
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<DeviceSummaryDto>> listDevices(
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        var devices = deviceRegistryService.listDevices(userId).stream()
            .map(this::toDto)
            .toList();
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/{deviceId}")
    @RequireAuth
    public ResponseEntity<DeviceSummaryDto> getDevice(
            @PathVariable String deviceId,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        return deviceRegistryService.getDevice(userId, deviceId)
            .map(d -> ResponseEntity.ok(toDto(d)))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{deviceId}")
    @RequireAuth
    public ResponseEntity<Void> unpair(
            @PathVariable String deviceId,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        deviceRegistryService.unpair(userId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{deviceId}")
    @RequireAuth
    public ResponseEntity<?> rename(
            @PathVariable String deviceId,
            @RequestBody Map<String, String> body,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        var name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var device = deviceRegistryService.rename(userId, deviceId, name);
        return ResponseEntity.ok(toDto(device));
    }

    @PutMapping("/{deviceId}/preferences")
    @RequireAuth
    public ResponseEntity<DeviceSummaryDto> updatePreferences(
            @PathVariable String deviceId,
            @RequestBody Map<String, Boolean> preferences,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        // Preferences persistence is not yet implemented; return current device state.
        return deviceRegistryService.getDevice(userId, deviceId)
            .map(d -> ResponseEntity.ok(toDto(d)))
            .orElse(ResponseEntity.notFound().build());
    }

    private DeviceSummaryDto toDto(Device d) {
        String os = d.getOs();
        String deviceType = deriveDeviceType(os);
        return new DeviceSummaryDto(
            d.getId(),
            d.getUserId(),
            d.getName(),
            deviceType,
            os,
            null,
            d.getStatus() != null ? d.getStatus().name() : null,
            d.getLastSeenAt() != null ? d.getLastSeenAt().toString() : null,
            Collections.emptyMap(),
            Collections.emptyList(),
            0,
            d.getAgentVersion(),
            Collections.emptyMap(),
            d.getCreatedAt() != null ? d.getCreatedAt().toString() : null,
            d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null
        );
    }

    private String deriveDeviceType(String os) {
        if (os == null) {
            return "LINUX";
        }
        String lower = os.toLowerCase();
        if (lower.contains("mac")) {
            return "MACOS";
        }
        if (lower.contains("windows")) {
            return "WINDOWS";
        }
        if (lower.contains("linux")) {
            return "LINUX";
        }
        return "LINUX";
    }
}
