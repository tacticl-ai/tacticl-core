package io.strategiz.social.client.instagram.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.instagram.client.InstagramClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Instagram Graph API client.
 *
 * <p>
 * Provides: - Rate limiter based on configured rate limit per minute - InstagramClient
 * bean - Conditional activation based on property: tacticl.instagram.enabled
 *
 * <p>
 * Enable in application.properties:
 *
 * <pre>
 * tacticl.instagram.enabled=true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.instagram.enabled", havingValue = "true", matchIfMissing = false)
public class ClientInstagramConfig {

	/**
	 * Rate limiter for Instagram Graph API based on configured rate limit per minute.
	 * @param config Instagram configuration
	 * @return Bucket for rate limiting
	 */
	@Bean(name = "instagramRateLimiter")
	public Bucket instagramRateLimiter(InstagramConfig config) {
		Bandwidth limit = Bandwidth.classic(config.getRateLimitPerMinute(),
				Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	/**
	 * Instagram Client bean.
	 *
	 * <p>
	 * Created as a @Bean instead of @Component to ensure proper dependency ordering. The
	 * instagramRateLimiter bean must be created first.
	 * @param config Instagram configuration
	 * @param instagramRateLimiter rate limiter bucket
	 * @return InstagramClient instance
	 */
	@Bean
	public InstagramClient instagramClient(InstagramConfig config, Bucket instagramRateLimiter) {
		return new InstagramClient(config, instagramRateLimiter);
	}

}
