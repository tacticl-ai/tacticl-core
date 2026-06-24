package io.tacticl.business.connections.service;

/**
 * A repository in scope for the user's linked GitHub org installation, resolved by
 * {@link GithubOrgService}. Maps 1:1 to the service-layer {@code GithubRepoDto}.
 *
 * @param owner repo owner login
 * @param name repo name (without owner prefix)
 * @param fullName {@code owner/name}
 * @param repoUrl HTML URL of the repo
 * @param language GitHub's reported primary language (may be {@code null})
 * @param defaultBranch repo default branch
 * @param isDefault whether {@code repoUrl} equals the user's {@code Product.defaultRepoUrl}
 */
public record GithubRepoView(
		String owner,
		String name,
		String fullName,
		String repoUrl,
		String language,
		String defaultBranch,
		boolean isDefault) {
}
