package io.tacticl.client.telegram.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelegramFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(TelegramConfig.class)
    public TelegramConfig telegramConfigFallback() {
        return new TelegramConfig();
    }
}
