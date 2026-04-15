package io.tacticl.service.connections.controller;

import io.tacticl.business.connections.service.DeviceRegistryService;
import io.tacticl.service.connections.dto.DeviceSummaryDto;
import io.tacticl.service.connections.dto.PairingTokenResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/connections/devices")
public class DeviceController {

    private final DeviceRegistryService deviceRegistryService;

    public DeviceController(DeviceRegistryService deviceRegistryService) {
        this.deviceRegistryService = deviceRegistryService;
    }

    @PostMapping("/pair")
    public ResponseEntity<PairingTokenResponseDto> generatePairingToken(
            @RequestHeader("X-User-Id") String userId) {
        var result = deviceRegistryService.generatePairingToken(userId);
        return ResponseEntity.ok(new PairingTokenResponseDto(
            result.token(), result.expiresAt().toString()));
    }

    @GetMapping
    public ResponseEntity<List<DeviceSummaryDto>> listDevices(
            @RequestHeader("X-User-Id") String userId) {
        var devices = deviceRegistryService.listDevices(userId).stream()
            .map(d -> new DeviceSummaryDto(
                d.getId(), d.getName(), d.getOs(), d.getStatus().name(),
                d.getCapabilities(),
                d.getLastSeenAt() != null ? d.getLastSeenAt().toString() : null))
            .toList();
        return ResponseEntity.ok(devices);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> unpair(
            @PathVariable String deviceId,
            @RequestHeader("X-User-Id") String userId) {
        deviceRegistryService.unpair(userId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<DeviceSummaryDto> rename(
            @PathVariable String deviceId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") String userId) {
        var device = deviceRegistryService.rename(userId, deviceId, body.get("name"));
        return ResponseEntity.ok(new DeviceSummaryDto(
            device.getId(), device.getName(), device.getOs(), device.getStatus().name(),
            device.getCapabilities(), null));
    }
}
