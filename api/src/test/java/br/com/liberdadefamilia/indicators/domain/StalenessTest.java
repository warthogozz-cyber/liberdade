package br.com.liberdadefamilia.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class StalenessTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Instant FRIDAY_SUCCESS =
            ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, SAO_PAULO).toInstant();

    @Test
    void neverSucceededIsStale() {
        assertThat(Staleness.isStale(null, Instant.parse("2026-11-24T12:00:00Z"), SAO_PAULO))
                .isTrue();
    }

    @Test
    void fridaySuccessFreshUntilWednesday() {
        Instant wednesday =
                ZonedDateTime.of(2026, 11, 25, 20, 0, 0, 0, SAO_PAULO).toInstant();
        assertThat(Staleness.isStale(FRIDAY_SUCCESS, wednesday, SAO_PAULO)).isFalse();
    }

    @Test
    void staleOnThursday() {
        Instant thursday = ZonedDateTime.of(2026, 11, 26, 8, 0, 0, 0, SAO_PAULO).toInstant();
        assertThat(Staleness.isStale(FRIDAY_SUCCESS, thursday, SAO_PAULO)).isTrue();
    }

    @Test
    void usesSaoPauloDate() {
        // 23:30 on Thursday in Sao Paulo is already Friday in UTC: it must count as Thursday.
        Instant success = Instant.parse("2026-11-20T02:30:00Z");
        Instant tuesday = ZonedDateTime.of(2026, 11, 24, 12, 0, 0, 0, SAO_PAULO).toInstant();
        Instant wednesday =
                ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, SAO_PAULO).toInstant();
        // From Thursday 19: Fri, Mon, Tue = 3 (fresh); Wed = 4 (stale). Counted as Friday it would be 2 and 3.
        assertThat(Staleness.isStale(success, tuesday, SAO_PAULO)).isFalse();
        assertThat(Staleness.isStale(success, wednesday, SAO_PAULO)).isTrue();
    }
}
