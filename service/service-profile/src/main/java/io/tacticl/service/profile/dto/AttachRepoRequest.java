package io.tacticl.service.profile.dto;

/**
 * Body for {@code POST /v1/repos} — attach/grant a GitHub repo to the user's repo memory.
 * {@code accessLevel} is accepted for forward-compatibility (e.g. "read"/"write") but is
 * not yet persisted; repo grants are currently binary (present = usable).
 */
public record AttachRepoRequest(String repoUrl, String accessLevel) {}
