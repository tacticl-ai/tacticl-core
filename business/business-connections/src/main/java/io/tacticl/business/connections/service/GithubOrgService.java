package io.tacticl.business.connections.service;

import io.strategiz.social.client.github.GitHubAppAuth;
import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.config.GitHubAppConfig;
import io.strategiz.social.client.github.model.GitHubRepository;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.data.connections.repository.ConnectionRepository;
import io.tacticl.data.profile.entity.Product;
import io.tacticl.data.profile.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves the repos-in-scope for a user's linked GitHub org (single-org per user) via the Tacticl
 * GitHub App installation, and manages the App install URL + callback.
 *
 * <p>
 * Degrades gracefully at every step: returns an empty list when there is no GITHUB connection, no
 * installation id, or the App is unconfigured/disabled. Never throws on the read path so the REST
 * surface can return {@code []} / {@code null url} rather than 500.
 *
 * <p>
 * The GitHub App auth chain ({@link GitHubAppAuth}) mirrors the arbiter's {@code github-app-auth.ts}.
 */
@Service
public class GithubOrgService {

	private static final Logger log = LoggerFactory.getLogger(GithubOrgService.class);

	private static final String GITHUB_PROVIDER = "GITHUB";

	private final ConnectionRepository connectionRepository;

	private final ProductRepository productRepository;

	private final Optional<GitHubClient> gitHubClient;

	private final Optional<GitHubAppAuth> gitHubAppAuth;

	private final Optional<GitHubAppConfig> gitHubAppConfig;

	private final String appSlugOverride;

	public GithubOrgService(ConnectionRepository connectionRepository,
			ProductRepository productRepository, Optional<GitHubClient> gitHubClient,
			Optional<GitHubAppAuth> gitHubAppAuth, Optional<GitHubAppConfig> gitHubAppConfig,
			@Value("${tacticl.github.app.slug:}") String appSlugOverride) {
		this.connectionRepository = connectionRepository;
		this.productRepository = productRepository;
		this.gitHubClient = gitHubClient;
		this.gitHubAppAuth = gitHubAppAuth;
		this.gitHubAppConfig = gitHubAppConfig;
		this.appSlugOverride = appSlugOverride;
	}

	/**
	 * List repositories in scope for the user's linked GitHub org installation.
	 * @param userId the authenticated user id
	 * @return the repos in scope, or an empty list when nothing is configured/linked
	 */
	public List<GithubRepoView> listReposInScope(String userId) {
		if (gitHubClient.isEmpty() || gitHubAppAuth.isEmpty() || !gitHubAppAuth.get().isEnabled()) {
			log.debug("GitHub App unavailable/disabled - returning empty repos-in-scope for {}", userId);
			return List.of();
		}

		Optional<Connection> connectionOpt = connectionRepository
			.findByUserIdAndProvider(userId, GITHUB_PROVIDER);
		if (connectionOpt.isEmpty() || connectionOpt.get().getInstallationId() == null) {
			log.debug("No GITHUB connection / installationId for {} - returning empty", userId);
			return List.of();
		}

		long installationId = connectionOpt.get().getInstallationId();
		String defaultRepoUrl = resolveDefaultRepoUrl(userId);

		try {
			String token = gitHubAppAuth.get().mintInstallationToken(installationId);
			List<GitHubRepository> repos = gitHubClient.get().listInstallationRepos(token);
			List<GithubRepoView> views = new ArrayList<>(repos.size());
			for (GitHubRepository r : repos) {
				String owner = ownerOf(r.fullName(), r.name());
				boolean isDefault = defaultRepoUrl != null && defaultRepoUrl.equals(r.htmlUrl());
				views.add(new GithubRepoView(owner, r.name(), r.fullName(), r.htmlUrl(),
						r.language(), r.defaultBranch(), isDefault));
			}
			return views;
		}
		catch (Exception e) {
			// Read path must never 500 — log and return empty.
			log.warn("Failed to list repos-in-scope for {} (installation {}): {}", userId,
					installationId, e.getMessage());
			return List.of();
		}
	}

	/**
	 * Build the GitHub App install URL, or {@code null} when the App slug is unconfigured.
	 * @return {@code https://github.com/apps/{slug}/installations/new} or {@code null}
	 */
	public String installUrl() {
		String slug = resolveAppSlug();
		if (slug == null || slug.isBlank()) {
			return null;
		}
		return "https://github.com/apps/" + slug + "/installations/new";
	}

	/**
	 * Persist the App installation id (+ org login) onto the user's GITHUB connection. Creates the
	 * GITHUB connection row if one does not already exist.
	 * @param userId the authenticated user id
	 * @param installationId the GitHub App installation id from the install callback
	 * @param orgLogin the org login the installation belongs to (may be {@code null})
	 * @return the saved connection
	 */
	public Connection saveInstallation(String userId, Long installationId, String orgLogin) {
		Connection connection = connectionRepository.findByUserIdAndProvider(userId, GITHUB_PROVIDER)
			.orElseGet(() -> Connection.create(userId, GITHUB_PROVIDER,
					String.format("tacticl/%s/connections/github", userId),
					orgLogin != null ? orgLogin : null, List.of()));
		connection.setInstallationId(installationId);
		if (orgLogin != null && !orgLogin.isBlank()) {
			connection.setOrgLogin(orgLogin);
		}
		return connectionRepository.save(connection);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private String resolveDefaultRepoUrl(String userId) {
		try {
			return productRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId).stream()
				.map(Product::getDefaultRepoUrl)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
		}
		catch (Exception e) {
			log.debug("Could not resolve default repo URL for {}: {}", userId, e.getMessage());
			return null;
		}
	}

	private String resolveAppSlug() {
		if (appSlugOverride != null && !appSlugOverride.isBlank()) {
			return appSlugOverride;
		}
		return gitHubAppConfig.map(GitHubAppConfig::getAppSlug).orElse(null);
	}

	/** Derive the owner from {@code owner/name}, falling back to stripping the repo name. */
	private String ownerOf(String fullName, String name) {
		if (fullName != null && fullName.contains("/")) {
			return fullName.substring(0, fullName.indexOf('/'));
		}
		return null;
	}

}
