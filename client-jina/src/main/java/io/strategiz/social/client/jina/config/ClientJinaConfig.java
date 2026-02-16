package io.strategiz.social.client.jina.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.jina.client.JinaClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates Jina Reader client beans with rate limiting. */
@Configuration
@ConditionalOnProperty(name = "tacticl.jina.enabled", havingValue = "true")
public class ClientJinaConfig {

	@Bean
	public JinaConfig jinaConfig() {
		return new JinaConfig();
	}

	@Bean
	public Bucket jinaRateLimiter(JinaConfig config) {
		Bandwidth limit = Bandwidth.classic(config.getRateLimitPerMinute(),
				Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	@Bean
	public JinaClient jinaClient(JinaConfig config, Bucket jinaRateLimiter) {
		return new JinaClient(config, jinaRateLimiter);
	}

}
