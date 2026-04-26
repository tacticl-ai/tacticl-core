package io.tacticl.business.telegram.outbound;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Per-chat 1 message/second rate limiter shared by every outbound Telegram path.
 *
 * <p>Both {@link OutboundDrainer} and the pinned-status flush in
 * {@code PinnedStatusService} consult this gate before any direct
 * {@code bot.sendMessage}, {@code bot.editMessageText}, or {@code bot.pinChatMessage}
 * call. The single shared gate is what prevents two unrelated Telegram code paths
 * from each pacing themselves correctly while collectively exceeding Telegram's
 * 1 msg/s/chat ceiling and earning the chat a 429.
 *
 * <p>The limiter is record-on-acquire (not record-after-send): the slot is consumed
 * the moment the caller wins the gate, so a slow Telegram round-trip does not extend
 * the next chat's wait beyond the configured 1 s window.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramRateLimiter {

    private static final long PER_CHAT_MIN_INTERVAL_MS = 1_000L;

    private final LongSupplier clock;
    private final ConcurrentMap<Long, Long> lastSentMs = new ConcurrentHashMap<>();

    public TelegramRateLimiter() {
        this(System::currentTimeMillis);
    }

    // Package-private ctor for tests — allows a deterministic clock without a dedicated bean.
    TelegramRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Returns true and atomically records the send time when the call is permitted;
     * returns false when the per-chat 1 msg/s gate has not elapsed since the last
     * acquired send. Callers MUST consult this before any direct
     * {@code bot.sendMessage} / {@code bot.editMessageText} / {@code bot.pinChatMessage}
     * call.
     */
    public boolean tryAcquire(long chatId) {
        long now = clock.getAsLong();
        // WHY: ConcurrentHashMap.compute is the only way to atomically read-and-conditionally-update.
        // The lambda either keeps the prior value (rate-limited) or installs `now` (acquired).
        // We capture the branch via a single-element array because the lambda must be effectively
        // final yet we need its decision to escape — boolean primitive comparison would otherwise
        // require fragile boxed-Long reference equality.
        boolean[] acquired = {false};
        lastSentMs.compute(chatId, (id, last) -> {
            if (last == null || now - last >= PER_CHAT_MIN_INTERVAL_MS) {
                acquired[0] = true;
                return now;
            }
            return last;
        });
        return acquired[0];
    }
}
