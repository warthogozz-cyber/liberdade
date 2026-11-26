package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.support.TestClock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The family's lookups per day, counted by the day in São Paulo (UTC-3). */
class GeocodingLimitsTest {

    private final TestClock clock = new TestClock();

    @Test
    void stopsAtTheLimitAndCountsEachFamilyAlone() {
        clock.freeze(Instant.parse("2026-11-25T15:00:00Z"));
        GeocodingLimits limits = new GeocodingLimits(clock, 2);
        UUID one = UUID.randomUUID();

        assertThat(limits.tryConsume(one)).isTrue();
        assertThat(limits.tryConsume(one)).isTrue();
        assertThat(limits.tryConsume(one)).isFalse();
        assertThat(limits.tryConsume(UUID.randomUUID())).isTrue();
    }

    @Test
    void theDayTurnsAtMidnightInSaoPaulo() {
        UUID family = UUID.randomUUID();
        clock.freeze(Instant.parse("2026-11-26T02:59:00Z")); // 23:59 on the 25th in São Paulo
        GeocodingLimits limits = new GeocodingLimits(clock, 1);
        assertThat(limits.tryConsume(family)).isTrue();

        clock.freeze(Instant.parse("2026-11-26T02:59:59Z"));
        assertThat(limits.tryConsume(family)).isFalse();

        clock.freeze(Instant.parse("2026-11-26T03:00:00Z")); // midnight on the 26th
        assertThat(limits.tryConsume(family)).isTrue();
    }
}
