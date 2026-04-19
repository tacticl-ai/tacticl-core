package io.tacticl.service.connections.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.entity.ConnectionStatus;
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
    @RequireAuth
    public ResponseEntity<List<ConnectionSummaryDto>> listConnections(
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        var connections = connectionRegistryService.listConnections(userId).stream()
            .map(this::toDto)
            .toList();
        return ResponseEntity.ok(connections);
    }

    @GetMapping("/oauth/{provider}/url")
    @RequireAuth
    public ResponseEntity<OAuthUrlResponseDto> generateAuthUrl(
            @PathVariable String provider,
            @RequestParam String redirectUri,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        var state = UUID.randomUUID().toString();
        var url = connectionRegistryService.generateAuthUrl(provider, state, redirectUri);
        return ResponseEntity.ok(new OAuthUrlResponseDto(url, state));
    }

    @PostMapping("/oauth/{provider}/callback")
    @RequireAuth
    public ResponseEntity<ConnectionSummaryDto> handleCallback(
            @PathVariable String provider,
            @RequestBody OAuthCallbackRequestDto request,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        var connection = connectionRegistryService.handleCallback(
            userId, provider, request.code(), request.redirectUri());
        return ResponseEntity.ok(toDto(connection));
    }

    @GetMapping("/{connectionId}")
    @RequireAuth
    public ResponseEntity<ConnectionSummaryDto> getConnection(
            @PathVariable String connectionId,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        return connectionRegistryService.getConnection(userId, connectionId)
            .map(c -> ResponseEntity.ok(toDto(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{connectionId}")
    @RequireAuth
    public ResponseEntity<Void> disconnect(
            @PathVariable String connectionId,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        connectionRegistryService.disconnect(userId, connectionId);
        return ResponseEntity.noContent().build();
    }

    private ConnectionSummaryDto toDto(Connection c) {
        return new ConnectionSummaryDto(
            c.getId(),
            c.getProvider(),
            c.getAccountIdentity(),
            null,
            c.getStatus() == ConnectionStatus.ERROR,
            c.getStatus() == ConnectionStatus.EXPIRED,
            c.getTokenExpiresAt() != null ? c.getTokenExpiresAt().toString() : null,
            c.getCreatedAt() != null ? c.getCreatedAt().toString() : null
        );
    }
}
