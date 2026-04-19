package io.tacticl.business.telegram;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.config.TelegramConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramWebhookRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramBotClient bot;
    private final TelegramConfig config;

    public TelegramWebhookRegistrar(TelegramBotClient bot, TelegramConfig config) {
        this.bot = bot;
        this.config = config;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void registerOnStartup() {
        if (!config.isConfigured()) {
            logger.warn("Telegram bot token missing — skipping webhook registration");
            return;
        }
        if (config.getPublicBaseUrl() == null || config.getPublicBaseUrl().isBlank()) {
            logger.warn("tacticl.telegram.public-base-url not set — skipping webhook registration");
            return;
        }
        String url = config.getPublicBaseUrl() + config.getWebhookPath();
        try {
            boolean ok = bot.setWebhook(url, config.getWebhookSecret());
            if (ok) logger.info("Telegram webhook registered at {}", url);
            else logger.error("Telegram webhook registration returned ok=false");
        } catch (Exception e) {
            logger.error("Telegram webhook registration failed", e);
        }
    }
}
