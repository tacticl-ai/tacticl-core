package io.tacticl.service.connections.dto;

public record SecretMetadataDto(
    String secretId, String name, String providerHint,
    String createdAt, String lastTestedAt, String lastTestResult
) {}
