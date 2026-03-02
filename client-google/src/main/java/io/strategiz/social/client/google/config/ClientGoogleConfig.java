package io.strategiz.social.client.google.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.google.client.GooglePhotosClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the Google OAuth client (YouTube, Gmail, Photos).
 *
 * <p>
 * Enable in application.properties:
 * <pre>
 * tacticl.google.enabled=true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.google.enabled", havingValue = "true", matchIfMissing = false)
public class ClientGoogleConfig {

	@Bean
	public GoogleConfig googleConfig() {
		return new GoogleConfig();
	}

	@Bean(name = "googlePhotosRateLimiter")
	public Bucket googlePhotosRateLimiter() {
		Bandwidth limit = Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	@Bean(name = "googlePhotosRestClient")
	public RestClient googlePhotosRestClient() {
		return RestClient.builder()
			.baseUrl("https://photoslibrary.googleapis.com")
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	@Bean
	public GooglePhotosClient googlePhotosClient(RestClient googlePhotosRestClient,
			Bucket googlePhotosRateLimiter) {
		return new GooglePhotosClient(googlePhotosRestClient, googlePhotosRateLimiter);
	}

}
