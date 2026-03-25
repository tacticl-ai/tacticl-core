package io.strategiz.social.client.github.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.github.GitHubClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the GitHub REST API v3 client.
 *
 * <p>
 * Provides:
 * <ul>
 *   <li>{@link GitHubConfig} bean for OAuth credentials (populated by {@link GitHubVaultConfig})</li>
 *   <li>{@link Bucket} rate limiter (80 requests/minute, well within GitHub's 5,000/hour limit)</li>
 *   <li>{@link RestClient} configured with GitHub API base URL and JSON content-type</li>
 *   <li>{@link GitHubClient} bean wired with all dependencies</li>
 * </ul>
 *
 * <p>
 * Enable in application.properties:
 * <pre>
 * tacticl.github.enabled=true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.github.enabled", havingValue = "true", matchIfMissing = false)
public class ClientGitHubConfig {

	private static final String GITHUB_API_BASE_URL = "https://api.github.com";

	/** Requests per minute — conservative vs. GitHub's 5,000/hour (83/min) cap. */
	private static final int RATE_LIMIT_PER_MINUTE = 80;

	@Bean
	public GitHubConfig gitHubConfig() {
		return new GitHubConfig();
	}

	/**
	 * Rate limiter for GitHub API calls.
	 * @return Bucket capped at {@value #RATE_LIMIT_PER_MINUTE} requests per minute
	 */
	@Bean(name = "gitHubRateLimiter")
	public Bucket gitHubRateLimiter() {
		Bandwidth limit = Bandwidth.classic(RATE_LIMIT_PER_MINUTE,
				Refill.intervally(RATE_LIMIT_PER_MINUTE, Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	/**
	 * RestClient configured for GitHub REST API v3.
	 * @return Configured RestClient with base URL and JSON content-type header
	 */
	@Bean(name = "gitHubRestClient")
	public RestClient gitHubRestClient() {
		return RestClient.builder()
			.baseUrl(GITHUB_API_BASE_URL)
			.defaultHeader("Content-Type", "application/json")
			.defaultHeader("Accept", "application/vnd.github+json")
			.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
			.build();
	}

	/**
	 * GitHub REST API v3 client bean.
	 * @param gitHubRestClient configured RestClient
	 * @param gitHubRateLimiter rate limiter bucket
	 * @param gitHubConfig GitHub OAuth config (owner derived from client ID context)
	 * @return GitHubClient instance
	 */
	@Bean
	public GitHubClient gitHubClient(RestClient gitHubRestClient, Bucket gitHubRateLimiter,
			GitHubConfig gitHubConfig) {
		return new GitHubClient(gitHubRestClient, gitHubRateLimiter, gitHubConfig.getOwner());
	}

}
