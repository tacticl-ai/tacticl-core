package io.strategiz.social.client.twitter.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.twitter.client.TwitterClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the Twitter/X API v2 client.
 *
 * <p>
 * Provides:
 * <ul>
 *   <li>{@link TwitterConfig} bean for configuration properties</li>
 *   <li>{@link Bucket} rate limiter based on configured calls per minute</li>
 *   <li>{@link RestClient} configured with Twitter API base URL</li>
 *   <li>{@link TwitterClient} bean wired with all dependencies</li>
 * </ul>
 *
 * <p>
 * Enable in application.properties:
 * <pre>
 * tacticl.twitter.enabled=true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.twitter.enabled", havingValue = "true", matchIfMissing = false)
public class ClientTwitterConfig {

	@Bean
	public TwitterConfig twitterConfig() {
		return new TwitterConfig();
	}

	/**
	 * Rate limiter for Twitter API based on configured rate limit per minute.
	 * @param config Twitter configuration
	 * @return Bucket for rate limiting
	 */
	@Bean(name = "twitterRateLimiter")
	public Bucket twitterRateLimiter(TwitterConfig config) {
		Bandwidth limit = Bandwidth.classic(config.getRateLimitPerMinute(),
				Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	/**
	 * RestClient configured for Twitter API v2.
	 * @param config Twitter configuration
	 * @return Configured RestClient
	 */
	@Bean(name = "twitterRestClient")
	public RestClient twitterRestClient(TwitterConfig config) {
		return RestClient.builder()
			.baseUrl(config.getBaseUrl())
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	/**
	 * Twitter API v2 client bean.
	 * @param twitterRestClient configured RestClient
	 * @param twitterRateLimiter rate limiter bucket
	 * @return TwitterClient instance
	 */
	@Bean
	public TwitterClient twitterClient(RestClient twitterRestClient, Bucket twitterRateLimiter) {
		return new TwitterClient(twitterRestClient, twitterRateLimiter);
	}

}
