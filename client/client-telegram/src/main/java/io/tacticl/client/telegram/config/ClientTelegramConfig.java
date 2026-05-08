package io.tacticl.client.telegram.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.tacticl.client.telegram.TelegramBotClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class ClientTelegramConfig {

    @Bean
    @ConfigurationProperties(prefix = "tacticl.telegram")
    public TelegramConfig telegramConfig() {
        return new TelegramConfig();
    }

    /**
     * Bucket gating outbound HTTP calls to the Telegram Bot API. Renamed from
     * {@code telegramRateLimiter} to {@code telegramApiBucket} so it does not
     * collide with {@code business-telegram}'s {@link io.tacticl.business.telegram.outbound.TelegramRateLimiter}
     * @Component (default bean name {@code telegramRateLimiter}). Spring's
     * bean override silently kept whichever loaded last, leaving consumers of
     * the per-chat 1 msg/s gate without a registered bean.
     */
    @Bean
    public Bucket telegramApiBucket(TelegramConfig config) {
        Bandwidth limit = Bandwidth.classic(
            config.getRateLimitPerMinute(),
            Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    @Bean
    public TelegramBotClient telegramBotClient(TelegramConfig config, Bucket telegramApiBucket) {
        return new TelegramBotClient(config, telegramApiBucket);
    }
}
