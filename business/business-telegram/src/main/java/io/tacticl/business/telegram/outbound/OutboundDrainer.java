package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.TelegramBotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class OutboundDrainer {

    private static final Logger logger = LoggerFactory.getLogger(OutboundDrainer.class);
    private static final long PER_CHAT_MIN_INTERVAL_MS = 1_000L;

    private final TelegramOutboundQueue queue;
    private final TelegramBotClient bot;
    private final LongSupplier clock;
    private final ConcurrentMap<Long, Long> lastSentMs = new ConcurrentHashMap<>();

    public OutboundDrainer(TelegramOutboundQueue queue, TelegramBotClient bot) {
        this(queue, bot, System::currentTimeMillis);
    }

    OutboundDrainer(TelegramOutboundQueue queue, TelegramBotClient bot, LongSupplier clock) {
        this.queue = queue;
        this.bot = bot;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tacticl.telegram.outbound.drain-ms:50}")
    public void drain() {
        long now = clock.getAsLong();
        for (long chatId : queue.activeChatIds()) {
            long last = lastSentMs.getOrDefault(chatId, 0L);
            if (now - last < PER_CHAT_MIN_INTERVAL_MS) continue;
            queue.poll(chatId).ifPresent(msg -> {
                try {
                    bot.sendMessage(msg.request());
                    lastSentMs.put(chatId, now);
                } catch (RuntimeException e) {
                    logger.error("Outbound send failed for chat {}", chatId, e);
                }
            });
        }
    }
}
