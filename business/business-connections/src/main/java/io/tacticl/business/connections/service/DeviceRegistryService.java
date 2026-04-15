package io.tacticl.business.connections.service;

import io.tacticl.data.connections.entity.Device;
import io.tacticl.data.connections.entity.PairingToken;
import io.tacticl.data.connections.repository.DeviceRepository;
import io.tacticl.data.connections.repository.PairingTokenRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceRegistryService {

    private static final long PAIRING_TTL_SECONDS = 900; // 15 minutes

    private final DeviceRepository deviceRepository;
    private final PairingTokenRepository pairingTokenRepository;

    public DeviceRegistryService(DeviceRepository deviceRepository,
                                  PairingTokenRepository pairingTokenRepository) {
        this.deviceRepository = deviceRepository;
        this.pairingTokenRepository = pairingTokenRepository;
    }

    public record PairingTokenResult(String token, Instant expiresAt) {}

    public PairingTokenResult generatePairingToken(String userId) {
        var rawToken = "tctl_pair_v1_" + UUID.randomUUID().toString().replace("-", "");
        var expiresAt = Instant.now().plusSeconds(PAIRING_TTL_SECONDS);
        var token = PairingToken.create(userId, rawToken, expiresAt);
        pairingTokenRepository.save(token);
        return new PairingTokenResult(rawToken, expiresAt);
    }

    public Device completePairing(String rawToken, String name, String os,
                                   String agentVersion, List<String> capabilities) {
        var pairingToken = pairingTokenRepository
            .findByTokenAndUsedFalseAndExpiresAtAfter(rawToken, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired pairing token"));

        pairingToken.markUsed();
        pairingTokenRepository.save(pairingToken);

        var device = Device.create(pairingToken.getUserId(), name, os, agentVersion, capabilities);
        return deviceRepository.save(device);
    }

    public List<Device> listDevices(String userId) {
        return deviceRepository.findByUserId(userId);
    }

    public void unpair(String userId, String deviceId) {
        var device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (!device.getUserId().equals(userId)) {
            throw new SecurityException("Device does not belong to user");
        }

        deviceRepository.delete(device);
    }

    public Device rename(String userId, String deviceId, String newName) {
        var device = deviceRepository.findById(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (!device.getUserId().equals(userId)) {
            throw new SecurityException("Device does not belong to user");
        }

        device.setName(newName);
        return deviceRepository.save(device);
    }
}
