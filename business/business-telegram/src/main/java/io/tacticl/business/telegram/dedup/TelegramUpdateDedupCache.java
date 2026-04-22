package io.tacticl.business.telegram.dedup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which Telegram {@code update_id}s have been seen recently so that
 * webhook redeliveries (Telegram retries on non-2xx or slow responses) cannot
 * double-execute a single user message into two Sparks / two pipeline runs.
 *
 * <p>Thread-safe; approximate bounded-LRU via insertion-time TTL + size-cap
 * sweep. Caffeine was intentionally not pulled in — a single cheap concurrent
 * map is sufficient for the expected inbound rate.</p>
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramUpdateDedupCache {

    static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final ConcurrentHashMap<Long, Long> seenAtMillis = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long ttlMillis;
    private final int maxEntries;

    public TelegramUpdateDedupCache() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_ENTRIES);
    }

    // Package-private ctor for tests — allows injecting a deterministic clock.
    TelegramUpdateDedupCache(Clock clock, Duration ttl, int maxEntries) {
        this.clock = clock;
        this.ttlMillis = ttl.toMillis();
        this.maxEntries = maxEntries;
    }

    /**
     * @return {@code true} iff this is the first time we have seen {@code updateId}
     *         within the TTL window. Callers should treat a {@code false} result
     *         as "drop; already processed".
     */
    public boolean markIfAbsent(long updateId) {
        long now = clock.millis();
        Long prior = seenAtMillis.putIfAbsent(updateId, now);
        if (prior == null) {
            maybeEvict(now);
            return true;
        }
        if (now - prior > ttlMillis) {
            // Stale entry — refresh in place so the window slides forward.
            seenAtMillis.put(updateId, now);
            return true;
        }
        return false;
    }

    private void maybeEvict(long now) {
        if (seenAtMillis.size() <= maxEntries) {
            return;
        }
        // Sweep expired entries first; only if still over cap do we drop
        // arbitrary survivors to bound memory.
        Iterator<Map.Entry<Long, Long>> it = seenAtMillis.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (now - e.getValue() > ttlMillis) {
                it.remove();
            }
        }
        if (seenAtMillis.size() <= maxEntries) {
            return;
        }
        Iterator<Map.Entry<Long, Long>> it2 = seenAtMillis.entrySet().iterator();
        while (it2.hasNext() && seenAtMillis.size() > maxEntries) {
            it2.next();
            it2.remove();
        }
    }

    int size() {
        return seenAtMillis.size();
    }
}
