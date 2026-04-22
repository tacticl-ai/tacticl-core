package io.tacticl.business.telegram.dedup;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramUpdateDedupCacheTest {

    @Test
    void firstSeenReturnsTrueDuplicateReturnsFalse() {
        TelegramUpdateDedupCache cache = new TelegramUpdateDedupCache();
        assertThat(cache.markIfAbsent(42L)).isTrue();
        assertThat(cache.markIfAbsent(42L)).isFalse();
        assertThat(cache.markIfAbsent(42L)).isFalse();
    }

    @Test
    void distinctUpdateIdsAreAllAdmitted() {
        TelegramUpdateDedupCache cache = new TelegramUpdateDedupCache();
        assertThat(cache.markIfAbsent(1L)).isTrue();
        assertThat(cache.markIfAbsent(2L)).isTrue();
        assertThat(cache.markIfAbsent(3L)).isTrue();
    }

    @Test
    void expiredEntryIsReAdmittedAfterTtl() {
        AtomicLong now = new AtomicLong(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli());
        Clock fixedClock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public long millis() { return now.get(); }
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        TelegramUpdateDedupCache cache = new TelegramUpdateDedupCache(fixedClock, Duration.ofMinutes(5), 1_000);

        assertThat(cache.markIfAbsent(99L)).isTrue();
        assertThat(cache.markIfAbsent(99L)).isFalse();

        now.addAndGet(Duration.ofMinutes(6).toMillis());
        assertThat(cache.markIfAbsent(99L)).isTrue();
    }

    @Test
    void sizeCapEvictsWhenExceeded() {
        TelegramUpdateDedupCache cache = new TelegramUpdateDedupCache(Clock.systemUTC(), Duration.ofMinutes(5), 10);
        for (long i = 0; i < 50; i++) {
            cache.markIfAbsent(i);
        }
        assertThat(cache.size()).isLessThanOrEqualTo(10);
    }
}
