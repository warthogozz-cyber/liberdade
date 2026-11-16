package br.com.liberdadefamilia.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final SettableClock clock = new SettableClock(Instant.parse("2026-11-02T12:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock, 3, Duration.ofMinutes(1));

    @Test
    void allowsUpToLimit() {
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void windowResets() {
        spend(3, "a");
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.now = Instant.parse("2026-11-02T12:01:00Z");

        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void keysAreIndependent() {
        spend(3, "a");
        assertThat(limiter.tryAcquire("a")).isFalse();

        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void retryAfterIsRemainingWindow() {
        clock.now = Instant.parse("2026-11-02T12:00:20Z");
        spend(3, "a");
        assertThat(limiter.tryAcquire("a")).isFalse();

        assertThat(limiter.retryAfterSeconds("a")).isEqualTo(40);
    }

    @Test
    void retryAfterIsAtLeastOne() {
        spend(3, "a");
        clock.now = Instant.parse("2026-11-02T12:00:59.500Z");
        assertThat(limiter.tryAcquire("a")).isFalse();

        assertThat(limiter.retryAfterSeconds("a")).isEqualTo(1);
    }

    @Test
    void retryAfterIsZeroWhenNotBlocked() {
        limiter.tryAcquire("a");

        assertThat(limiter.retryAfterSeconds("a")).isZero();
        assertThat(limiter.retryAfterSeconds("never-seen")).isZero();
    }

    @Test
    void prunesExpiredWindows() {
        for (int i = 0; i <= 10_000; i++) {
            limiter.tryAcquire("10.1." + i);
        }
        clock.now = Instant.parse("2026-11-02T12:01:00Z");

        limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.size()).isOne();
    }

    private void spend(int attempts, String key) {
        for (int i = 0; i < attempts; i++) {
            assertThat(limiter.tryAcquire(key)).isTrue();
        }
    }

    private static final class SettableClock extends Clock {

        Instant now;

        SettableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
