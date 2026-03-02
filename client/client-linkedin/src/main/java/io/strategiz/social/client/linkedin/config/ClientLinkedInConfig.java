package io.strategiz.social.client.linkedin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.linkedin.client.LinkedInClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the LinkedIn Marketing API client.
 *
 * <p>
 * Provides:
 * <ul>
 * <li>Rate limiter based on configured requests per minute</li>
 * <li>LinkedInClient bean wired with config, rate limiter, and ObjectMapper</li>
 * </ul>
 *
 * <p>
 * Enable in application.properties:
 *
 * <pre>
 * tacticl.linkedin.enabled=true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.linkedin.enabled", havingValue = "true", matchIfMissing = false)
public class ClientLinkedInConfig {

	/**
	 * Rate limiter for LinkedIn API based on configured rate limit per minute.
	 * @param config LinkedIn configuration
	 * @return Bucket for rate limiting
	 */
	@Bean(name = "linkedInRateLimiter")
	public Bucket linkedInRateLimiter(LinkedInConfig config) {
		Bandwidth limit = Bandwidth.classic(config.getRateLimitPerMinute(),
				Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	/**
	 * LinkedIn API Client bean.
	 *
	 * <p>
	 * Created as a @Bean instead of @Component to ensure proper dependency ordering. The
	 * linkedInRateLimiter bean must be created first.
	 * @param config LinkedIn configuration
	 * @param linkedInRateLimiter rate limiter bucket
	 * @param objectMapper Jackson ObjectMapper
	 * @return LinkedInClient instance
	 */
	@Bean
	public LinkedInClient linkedInClient(LinkedInConfig config, Bucket linkedInRateLimiter,
			ObjectMapper objectMapper) {
		return new LinkedInClient(config, linkedInRateLimiter, objectMapper);
	}

}
