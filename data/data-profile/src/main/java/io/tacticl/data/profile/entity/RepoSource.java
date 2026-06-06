package io.tacticl.data.profile.entity;

/** How a remembered repo first entered a user's registry. */
public enum RepoSource {
    /** Freshly provisioned by the analyst's create_repo skill for this build. */
    CREATED,
    /** An existing repo the user reused/attached for a build. */
    ATTACHED
}
