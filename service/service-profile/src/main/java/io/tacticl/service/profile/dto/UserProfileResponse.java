package io.tacticl.service.profile.dto;

public record UserProfileResponse(
    String displayName,
    String email,
    String avatarUrl
) {}
