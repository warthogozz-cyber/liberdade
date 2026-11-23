package br.com.liberdadefamilia.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/** The window closes at the end of the seventh local day (CC art. 132), in São Paulo time. */
class RefundWindowTest {

    private static Instant sp(String local) {
        return OffsetDateTime.parse(local + "-03:00").toInstant();
    }

    @Test
    void lateEveningPaymentRunsThroughTheEndOfLocalDaySeven() {
        Instant paidAt = sp("2026-11-24T22:00:00");

        assertThat(RefundWindow.deadline(paidAt)).isEqualTo(sp("2026-12-02T00:00:00"));
        assertThat(RefundWindow.isOpen(paidAt, sp("2026-12-01T23:59:59"))).isTrue();
        assertThat(RefundWindow.isOpen(paidAt, sp("2026-12-02T00:00:00"))).isFalse();
    }

    @Test
    void paymentAfterUtcMidnightCountsFromTheLocalDay() {
        // 01:30 UTC on 25/11 is still 22:30 on 24/11 in São Paulo.
        Instant paidAt = Instant.parse("2026-11-25T01:30:00Z");

        assertThat(RefundWindow.deadline(paidAt)).isEqualTo(sp("2026-12-02T00:00:00"));
    }

    @Test
    void paymentJustAfterLocalMidnightGetsAlmostEightDays() {
        Instant paidAt = sp("2026-11-24T00:00:00");

        assertThat(RefundWindow.deadline(paidAt)).isEqualTo(sp("2026-12-02T00:00:00"));
        assertThat(RefundWindow.isOpen(paidAt, paidAt)).isTrue();
        assertThat(RefundWindow.isOpen(paidAt, paidAt.minusSeconds(1))).isFalse();
    }

    @Test
    void lastSecondOfTheDayStillGetsSevenFullDays() {
        Instant paidAt = sp("2026-11-24T23:59:59");

        assertThat(RefundWindow.deadline(paidAt)).isEqualTo(sp("2026-12-02T00:00:00"));
        assertThat(RefundWindow.deadline(paidAt)).isAfter(paidAt.plusSeconds(7L * 24 * 3600));
    }
}
