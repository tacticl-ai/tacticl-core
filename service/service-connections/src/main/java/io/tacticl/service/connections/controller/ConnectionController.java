package io.tacticl.service.connections.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.service.connections.dto.ConnectionSummaryDto;
import io.tacticl.service.connections.dto.OAuthCallbackRequestDto;
import io.tacticl.service.connections.dto.OAuthUrlResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/connections")
public class ConnectionController extends BaseController {

    @Override
    protected String getModuleName() {
        return "connections";
    }

    private final ConnectionRegistryService connectionRegistryService;

    public ConnectionController(ConnectionRegistryService connectionRegistryService) {
        this.connectionRegistryService = connectionRegistryService;
    }

    @GetMapping
    public ResponseEntity<List<ConnectionSummaryDto>> listConnections(
            @RequestHeader("X-User-Id") String userId) {
        var connections = connectionRegistryService.listConnections(userId).stream()
            .map(c -> new ConnectionSummaryDto(
                c.getId(), c.getProvider(), c.getStatus().name(),
                c.getAccountIdentity(),
                c.getLastRefreshedAt() != null ? c.getLastRefreshedAt().toString() : null))
            .toList();
        return ResponseEntity.ok(connections);
    }

    @GetMapping("/oauth/{provider}/url")
    public ResponseEntity<OAuthUrlResponseDto> generateAuthUrl(
            @PathVariable String provider,
            @RequestParam String redirectUri,
            @RequestHeader("X-User-Id") String userId) {
        var state = UUID.randomUUID().toString();
        var url = connectionRegistryService.generateAuthUrl(provider, state, redirectUri);
        return ResponseEntity.ok(new OAuthUrlResponseDto(url, state));
    }

    @PostMapping("/oauth/{provider}/callback")
    public ResponseEntity<ConnectionSummaryDto> handleCallback(
            @PathVariable String provider,
            @RequestBody OAuthCallbackRequestDto request,
            @RequestHeader("X-User-Id") String userId) {
        var connection = connectionRegistryService.handleCallback(
            userId, provider, request.code(), request.redirectUri());
        return ResponseEntity.ok(new ConnectionSummaryDto(
            connection.getId(), connection.getProvider(), connection.getStatus().name(),
            connection.getAccountIdentity(), null));
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<ConnectionSummaryDto> getConnection(
            @PathVariable String connectionId,
            @RequestHeader("X-User-Id") String userId) {
        return connectionRegistryService.getConnection(userId, connectionId)
            .map(c -> ResponseEntity.ok(new ConnectionSummaryDto(
                c.getId(), c.getProvider(), c.getStatus().name(),
                c.getAccountIdentity(),
                c.getLastRefreshedAt() != null ? c.getLastRefreshedAt().toString() : null)))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> disconnect(
            @PathVariable String connectionId,
            @RequestHeader("X-User-Id") String userId) {
        connectionRegistryService.disconnect(userId, connectionId);
        return ResponseEntity.noContent().build();
    }
}
