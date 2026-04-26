package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.TelegramBotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class OutboundDrainer {

    private static final Logger logger = LoggerFactory.getLogger(OutboundDrainer.class);

    private final TelegramOutboundQueue queue;
    private final TelegramBotClient bot;
    private final TelegramRateLimiter rateLimiter;

    public OutboundDrainer(TelegramOutboundQueue queue,
                           TelegramBotClient bot,
                           TelegramRateLimiter rateLimiter) {
        this.queue = queue;
        this.bot = bot;
        this.rateLimiter = rateLimiter;
    }

    @Scheduled(fixedDelayString = "${tacticl.telegram.outbound.drain-ms:50}")
    public void drain() {
        for (long chatId : queue.activeChatIds()) {
            // WHY: tryAcquire reserves the slot before we poll, so a slow bot.sendMessage
            // does not push the next chat's wait beyond the configured 1 s window.
            // PinnedStatusService consults the same limiter — that is the whole point of
            // sharing the bean: two unrelated outbound paths never collectively breach
            // Telegram's 1 msg/s/chat ceiling.
            if (!rateLimiter.tryAcquire(chatId)) continue;
            queue.poll(chatId).ifPresent(msg -> {
                try {
                    bot.sendMessage(msg.request());
                } catch (RuntimeException e) {
                    logger.error("Outbound send failed for chat {}", chatId, e);
                }
            });
        }
    }
}
