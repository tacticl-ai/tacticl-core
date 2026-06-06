package io.tacticl.data.profile.entity;

/** Whether a remembered repo's owner is a GitHub user account or an organization. */
public enum RepoKind {
    /** Owner is a personal GitHub account (e.g. "cuztomizer"). */
    USER,
    /** Owner is a GitHub organization (e.g. "tacticl-ai"). */
    ORG,
    /** Owner type not yet resolved (cheap default — backfillable). */
    UNKNOWN
}
