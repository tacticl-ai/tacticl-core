package io.tacticl.service.profile.dto;

/**
 * A repo to attach to (or, when {@code create} is true, provision for) a product during onboarding.
 */
public record RepoSpecDto(String url, boolean create, String owner, String repoName, boolean isPrivate) {}
