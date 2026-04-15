package io.tacticl.service.connections.dto;

public record OAuthCallbackRequestDto(String code, String state, String redirectUri) {}
