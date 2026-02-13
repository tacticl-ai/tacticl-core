package io.strategiz.social.client.siliconflow.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.siliconflow.client.SiliconFlowClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates SiliconFlow client beans with rate limiting. */
@Configuration
@ConditionalOnProperty(name = "tacticl.siliconflow.enabled", havingValue = "true")
public class ClientSiliconFlowConfig {

	@Bean
	public SiliconFlowConfig siliconFlowConfig() {
		return new SiliconFlowConfig();
	}

	@Bean
	public Bucket siliconFlowRateLimiter(SiliconFlowConfig config) {
		Bandwidth limit = Bandwidth.classic(config.getRateLimitPerMinute(),
				Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1)));
		return Bucket.builder().addLimit(limit).build();
	}

	@Bean
	public SiliconFlowClient siliconFlowClient(SiliconFlowConfig config, Bucket siliconFlowRateLimiter) {
		return new SiliconFlowClient(config, siliconFlowRateLimiter);
	}

}
