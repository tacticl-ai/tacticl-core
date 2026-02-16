package io.strategiz.social.client.bravesearch.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.bravesearch.client.BraveSearchClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates Brave Search client beans with rate limiting. */
@Configuration
@ConditionalOnProperty(name = "tacticl.brave-search.enabled", havingValue = "true")
public class ClientBraveSearchConfig {

	@Bean
	public BraveSearchConfig braveSearchConfig() {
		return new BraveSearchConfig();
	}

	@Bean
	public Bucket braveSearchRateLimiter(BraveSearchConfig config) {
		Bandwidth limit = Bandwidth.classic(config.getRateLimitPerMinute(),
				Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	@Bean
	public BraveSearchClient braveSearchClient(BraveSearchConfig config, Bucket braveSearchRateLimiter) {
		return new BraveSearchClient(config, braveSearchRateLimiter);
	}

}
