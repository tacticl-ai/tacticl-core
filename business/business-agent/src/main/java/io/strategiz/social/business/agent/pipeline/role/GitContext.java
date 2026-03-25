package io.strategiz.social.business.agent.pipeline.role;

/**
 * Git repository context available to a PDLC role during execution.
 *
 * @param repoFullName    full repository name in owner/repo format (e.g. "acme/my-service")
 * @param baseBranch      the base branch that the working branch was cut from (e.g. "main")
 * @param workingBranch   the branch where this pipeline's changes will land
 * @param latestCommitSha the HEAD commit SHA on the working branch at execution time
 */
public record GitContext(
		String repoFullName,
		String baseBranch,
		String workingBranch,
		String latestCommitSha
) {}
