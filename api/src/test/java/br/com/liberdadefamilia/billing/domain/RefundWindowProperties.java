package br.com.liberdadefamilia.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.shared.Money;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Property tests with a fixed seed (see ADR-0013): 1000 cases per property. */
class RefundWindowProperties {

    private static final long SEED = 20261102L;
    private static final int CASES = 1000;
    private static final long TEN_DAYS = Duration.ofDays(10).toSeconds();
    private static final long WINDOW = 604_800;
    private static final long EIGHT_DAYS = Duration.ofDays(8).toSeconds();
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @ParameterizedTest
    @MethodSource("moments")
    void refundWindowIsHalfOpen(Instant paidAt, long offsetSeconds) {
        Instant now = paidAt.plusSeconds(offsetSeconds);
        Instant deadline = RefundWindow.deadline(paidAt);

        assertThat(RefundWindow.isOpen(paidAt, now)).isEqualTo(offsetSeconds >= 0 && now.isBefore(deadline));
        if (offsetSeconds >= 0 && offsetSeconds < WINDOW) {
            assertThat(RefundWindow.isOpen(paidAt, now)).isTrue();
        }
    }

    @ParameterizedTest
    @MethodSource("moments")
    void closesExactlyAtTheDeadline(Instant paidAt, long ignored) {
        Instant deadline = RefundWindow.deadline(paidAt);

        ZonedDateTime local = deadline.atZone(SAO_PAULO);
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(local.toLocalDate())
                .isEqualTo(paidAt.atZone(SAO_PAULO).toLocalDate().plusDays(8));
        assertThat(Duration.between(paidAt, deadline).toSeconds())
                .isGreaterThan(WINDOW)
                .isLessThanOrEqualTo(EIGHT_DAYS);
        assertThat(RefundWindow.isOpen(paidAt, deadline)).isFalse();
        assertThat(RefundWindow.isOpen(paidAt, deadline.minusSeconds(1))).isTrue();
        assertThat(RefundWindow.isOpen(paidAt, paidAt)).isTrue();
    }

    @Test
    void founderYearIsCalendarYear() {
        assertThat(FounderOffer.endsAt(Instant.parse("2028-02-29T12:00:00Z")))
                .isEqualTo(Instant.parse("2029-02-28T12:00:00Z"));
        assertThat(FounderOffer.endsAt(Instant.parse("2026-11-17T10:00:00Z")))
                .isEqualTo(Instant.parse("2027-11-17T10:00:00Z"));
    }

    @Test
    void offerIsEightyNineReais() {
        assertThat(FounderOffer.PRICE).isEqualTo(Money.brl("89.00"));
        assertThat(FounderOffer.PRICE_CENTS).isEqualTo(8900L);
        assertThat(FounderOffer.PRODUCT).isEqualTo("FOUNDER_2026");
        assertThat(FounderOffer.CURRENCY).isEqualTo("BRL");
    }

    @Test
    void themeFallsBackToTeal() {
        assertThat(FounderTheme.orDefault(null)).isEqualTo(FounderTheme.TEAL);
        assertThat(FounderTheme.orDefault("nope")).isEqualTo(FounderTheme.TEAL);
        assertThat(FounderTheme.orDefault("PLUM")).isEqualTo(FounderTheme.PLUM);
    }

    static Stream<Arguments> moments() {
        var random = new SplittableRandom(SEED);
        return Stream.generate(() -> Arguments.of(
                        Instant.ofEpochSecond(random.nextLong(1_700_000_000L, 1_900_000_000L)),
                        random.nextLong(-TEN_DAYS, TEN_DAYS + 1)))
                .limit(CASES);
    }
}
