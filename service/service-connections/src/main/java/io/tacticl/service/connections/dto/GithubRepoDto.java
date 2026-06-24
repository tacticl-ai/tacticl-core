package io.tacticl.service.connections.dto;

/**
 * A repository in scope for the user's linked GitHub org installation.
 *
 * <p>
 * Endpoint contract: {@code GET /v1/connections/github/repos -> GithubRepoDto[]}.
 *
 * @param owner repo owner login
 * @param name repo name (without owner prefix)
 * @param fullName {@code owner/name}
 * @param repoUrl HTML URL of the repo
 * @param language GitHub's reported primary language (may be {@code null})
 * @param defaultBranch repo default branch
 * @param isDefault whether {@code repoUrl} equals the user's {@code Product.defaultRepoUrl}
 */
public record GithubRepoDto(
		String owner,
		String name,
		String fullName,
		String repoUrl,
		String language,
		String defaultBranch,
		boolean isDefault) {
}
