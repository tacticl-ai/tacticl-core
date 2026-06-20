package io.tacticl.service.profile.dto;

/**
 * Body for {@code PUT /v1/users/me}. Both fields optional — null leaves the existing
 * value unchanged. Email is not editable (sourced from the identity token).
 */
public record UpdateProfileRequest(String displayName, String avatarUrl) {}
