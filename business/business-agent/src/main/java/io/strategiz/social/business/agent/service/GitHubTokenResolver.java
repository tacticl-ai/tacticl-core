package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.RepoGrant;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.RepoGrantRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the GitHub OAuth access token for a user, optionally scoped to a specific repository.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>If {@code repo} is provided, find an active {@link RepoGrant} for that repo. If the grant
 *       has an {@code oauthTokenRef}, look up that specific {@link SocialIntegration} by ID —
 *       this supports users with multiple GitHub accounts linked to different repos.</li>
 *   <li>Fall back to the user's default GitHub {@link SocialIntegration}
 *       ({@link PlatformType#GITHUB}).</li>
 * </ol>
 */
@Service
public class GitHubTokenResolver {

	private static final Logger log = LoggerFactory.getLogger(GitHubTokenResolver.class);

	private final RepoGrantRepository repoGrantRepository;

	private final SocialIntegrationRepository integrationRepository;

	public GitHubTokenResolver(RepoGrantRepository repoGrantRepository,
			SocialIntegrationRepository integrationRepository) {
		this.repoGrantRepository = repoGrantRepository;
		this.integrationRepository = integrationRepository;
	}

	/**
	 * Resolve the GitHub access token for the given user and repository.
	 *
	 * @param userId the Tacticl user ID
	 * @param repo   the full repository name (e.g. "owner/repo-name"), or {@code null} to skip
	 *               repo-specific lookup and use the user's default GitHub integration
	 * @return an {@link Optional} containing the access token, or empty if none is found
	 */
	public Optional<String> resolve(String userId, String repo) {
		// Step 1: try repo-specific token via RepoGrant.oauthTokenRef
		if (repo != null && !repo.isBlank()) {
			Optional<String> repoToken = resolveViaRepoGrant(userId, repo);
			if (repoToken.isPresent()) {
				return repoToken;
			}
		}

		// Step 2: fall back to default GitHub social integration
		return resolveDefaultGitHubIntegration(userId);
	}

	private Optional<String> resolveViaRepoGrant(String userId, String repo) {
		try {
			List<RepoGrant> grants = repoGrantRepository.findActiveByUserId(userId);
			Optional<RepoGrant> grant = grants.stream()
					.filter(g -> repo.equals(g.getRepoFullName()))
					.findFirst();

			if (grant.isEmpty()) {
				return Optional.empty();
			}

			String tokenRef = grant.get().getOauthTokenRef();
			if (tokenRef == null || tokenRef.isBlank()) {
				// Grant exists but no specific token ref — let it fall through to default
				return Optional.empty();
			}

			// Look up the specific SocialIntegration by ID
			Optional<SocialIntegration> integration =
					integrationRepository.findByIdInSubcollection(userId, tokenRef);

			if (integration.isEmpty()) {
				log.warn("[GITHUB-TOKEN] oauthTokenRef {} not found for user={} repo={}", tokenRef, userId, repo);
				return Optional.empty();
			}

			SocialIntegration si = integration.get();
			if (si.isDisabled() || !si.getIsActive()) {
				log.warn("[GITHUB-TOKEN] Integration {} is disabled/inactive for user={}", tokenRef, userId);
				return Optional.empty();
			}

			return Optional.ofNullable(si.getAccessToken());
		}
		catch (Exception e) {
			log.warn("[GITHUB-TOKEN] Failed repo-grant lookup for user={} repo={}: {}", userId, repo, e.getMessage());
			return Optional.empty();
		}
	}

	private Optional<String> resolveDefaultGitHubIntegration(String userId) {
		try {
			return integrationRepository
					.findByUserIdAndPlatform(userId, PlatformType.GITHUB)
					.filter(si -> !si.isDisabled() && si.getIsActive())
					.map(SocialIntegration::getAccessToken);
		}
		catch (Exception e) {
			log.warn("[GITHUB-TOKEN] Failed default integration lookup for user={}: {}", userId, e.getMessage());
			return Optional.empty();
		}
	}

}
