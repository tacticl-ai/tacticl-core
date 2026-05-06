package io.tacticl.client.whisper.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.tacticl.client.whisper.client.WhisperClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates Whisper client beans with rate limiting. */
@Configuration
@ConditionalOnProperty(name = "tacticl.whisper.enabled", havingValue = "true")
public class ClientWhisperConfig {

    @Bean
    @ConfigurationProperties(prefix = "tacticl.whisper")
    public WhisperConfig whisperConfig() {
        return new WhisperConfig();
    }

    @Bean
    public Bucket whisperRateLimiter(WhisperConfig config) {
        Bandwidth limit = Bandwidth.classic(
            config.getRateLimitPerMinute(),
            Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    @Bean
    public WhisperClient whisperClient(WhisperConfig config, Bucket whisperRateLimiter) {
        return new WhisperClient(config, whisperRateLimiter);
    }

}
