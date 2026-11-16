package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final SettableClock clock = new SettableClock(Instant.parse("2026-11-02T12:00:15Z"));
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock, 20);

    @Test
    void twentyAttemptsInAMinutePass() {
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
        }
    }

    @Test
    void twentyFirstWaitsForTheNextMinute() {
        spend(20, "10.0.0.1");

        assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(OptionalLong.of(45));
    }

    @Test
    void twentyFirstAtTheEndOfTheMinuteWaitsOneSecond() {
        spend(20, "10.0.0.1");
        clock.now = Instant.parse("2026-11-02T12:00:59.500Z");

        assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(OptionalLong.of(1));
    }

    @Test
    void nextMinuteStartsAFreshWindow() {
        spend(20, "10.0.0.1");
        clock.now = Instant.parse("2026-11-02T12:01:00Z");

        assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
    }

    @Test
    void keysAreCountedApart() {
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("a")).isEmpty();
            assertThat(limiter.tryAcquire("b")).isEmpty();
        }
    }

    @Test
    void pastTenThousandKeysOldMinutesAreDropped() {
        for (int i = 0; i <= 10_000; i++) {
            limiter.tryAcquire("10.1." + i);
        }
        clock.now = Instant.parse("2026-11-02T12:01:00Z");

        limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.size()).isOne();
    }

    private void spend(int attempts, String key) {
        for (int i = 0; i < attempts; i++) {
            assertThat(limiter.tryAcquire(key)).isEmpty();
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
