package io.tacticl.business.telegram.outbound;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramRateLimiterTest {

    @Test
    void firstAcquireReturnsTrue() {
        AtomicLong now = new AtomicLong(1_000_000L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(now::get);

        assertThat(limiter.tryAcquire(42L)).isTrue();
    }

    @Test
    void secondAcquireWithinOneSecondReturnsFalse() {
        AtomicLong now = new AtomicLong(1_000_000L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(now::get);

        assertThat(limiter.tryAcquire(42L)).isTrue();
        now.addAndGet(500L);
        assertThat(limiter.tryAcquire(42L)).isFalse();
        now.addAndGet(499L); // total 999ms — still under 1s
        assertThat(limiter.tryAcquire(42L)).isFalse();
    }

    @Test
    void acquireAfterOneSecondReturnsTrueAgain() {
        AtomicLong now = new AtomicLong(1_000_000L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(now::get);

        assertThat(limiter.tryAcquire(42L)).isTrue();
        now.addAndGet(1_000L);
        assertThat(limiter.tryAcquire(42L)).isTrue();
    }

    @Test
    void perChatGateIsIndependent() {
        AtomicLong now = new AtomicLong(1_000_000L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(now::get);

        assertThat(limiter.tryAcquire(10L)).isTrue();
        // Different chat at same instant — must not be blocked.
        assertThat(limiter.tryAcquire(20L)).isTrue();
        // Same chat — blocked.
        assertThat(limiter.tryAcquire(10L)).isFalse();
        assertThat(limiter.tryAcquire(20L)).isFalse();
    }

    @Test
    void rateLimitedAcquireDoesNotAdvanceTheGate() {
        // WHY: a denied tryAcquire must NOT push the next allowed time further out.
        // Otherwise a hot caller could starve a slow caller forever.
        AtomicLong now = new AtomicLong(1_000_000L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(now::get);

        assertThat(limiter.tryAcquire(42L)).isTrue(); // record at t=1_000_000
        now.addAndGet(500L);
        assertThat(limiter.tryAcquire(42L)).isFalse(); // denied at t=1_000_500 — must not move the gate
        now.addAndGet(500L); // t=1_001_000 — exactly 1s after the first acquire
        assertThat(limiter.tryAcquire(42L)).isTrue();
    }
}
