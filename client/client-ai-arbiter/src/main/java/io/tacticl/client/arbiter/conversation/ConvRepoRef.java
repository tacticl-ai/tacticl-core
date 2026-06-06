package io.tacticl.client.arbiter.conversation;

/**
 * One of the user's known GitHub repos, supplied as grounding to {@code ConverseTurn}
 * so the analyst can offer it once requirements are understood. Provider-neutral; the
 * gRPC client maps it onto the proto {@code RepoRef}.
 *
 * @param owner   GitHub owner (user login or org)
 * @param name    repository name
 * @param repoUrl canonical https URL
 * @param kind    "USER" | "ORG" | "UNKNOWN"
 */
public record ConvRepoRef(String owner, String name, String repoUrl, String kind) {
}
