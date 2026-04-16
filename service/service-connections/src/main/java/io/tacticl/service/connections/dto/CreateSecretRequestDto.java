package io.tacticl.service.connections.dto;

public record CreateSecretRequestDto(String name, String providerHint, String value) {}
