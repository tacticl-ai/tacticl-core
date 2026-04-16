package io.tacticl.service.connections.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.connections.service.SecretsVaultService;
import io.tacticl.data.connections.entity.SecretMetadata;
import io.tacticl.data.connections.entity.TestResult;
import io.tacticl.service.connections.dto.CreateSecretRequestDto;
import io.tacticl.service.connections.dto.SecretMetadataDto;
import io.tacticl.service.connections.dto.SecretTestResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/secrets")
public class SecretController extends BaseController {

    private final SecretsVaultService secretsVaultService;

    public SecretController(SecretsVaultService secretsVaultService) {
        this.secretsVaultService = secretsVaultService;
    }

    @Override
    protected String getModuleName() { return "secrets"; }

    @GetMapping
    public ResponseEntity<List<SecretMetadataDto>> listSecrets(@AuthUser AuthenticatedUser user) {
        List<SecretMetadataDto> dtos = secretsVaultService.listSecrets(user.getUserId())
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<SecretMetadataDto> createSecret(
            @AuthUser AuthenticatedUser user,
            @RequestBody CreateSecretRequestDto body) {
        SecretMetadata saved = secretsVaultService.store(
                user.getUserId(), body.name(), body.providerHint(), body.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @DeleteMapping("/{secretId}")
    public ResponseEntity<Void> deleteSecret(@AuthUser AuthenticatedUser user, @PathVariable String secretId) {
        secretsVaultService.delete(user.getUserId(), secretId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{secretId}/test")
    public ResponseEntity<SecretTestResultDto> testSecret(
            @AuthUser AuthenticatedUser user, @PathVariable String secretId) {
        TestResult result = secretsVaultService.testSecret(user.getUserId(), secretId);
        return ResponseEntity.ok(new SecretTestResultDto(result.name()));
    }

    private SecretMetadataDto toDto(SecretMetadata s) {
        return new SecretMetadataDto(
                s.getId(), s.getName(), s.getProviderHint(),
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getLastTestedAt() != null ? s.getLastTestedAt().toString() : null,
                s.getLastTestResult() != null ? s.getLastTestResult().name() : null
        );
    }
}
