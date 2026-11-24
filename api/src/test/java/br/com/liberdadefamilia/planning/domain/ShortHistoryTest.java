package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class ShortHistoryTest {

    private static final YearMonth CURRENT = YearMonth.of(2026, 11);
    private static final BigDecimal SUM = new BigDecimal("9000");

    @Test
    void firstConfirmedInsideWindowIsShort() {
        assertThat(ShortHistory.of(LocalDate.of(2026, 9, 1), SUM, CURRENT)).isTrue();
    }

    @Test
    void firstConfirmedInOldestMonthIsLong() {
        assertThat(ShortHistory.of(LocalDate.of(2026, 8, 15), SUM, CURRENT)).isFalse();
    }

    @Test
    void zeroOrNullSumIsShort() {
        LocalDate first = LocalDate.of(2026, 1, 10);
        assertThat(ShortHistory.of(first, BigDecimal.ZERO, CURRENT)).isTrue();
        assertThat(ShortHistory.of(first, null, CURRENT)).isTrue();
    }

    @Test
    void noConfirmedIsShort() {
        assertThat(ShortHistory.of(null, SUM, CURRENT)).isTrue();
    }
}
