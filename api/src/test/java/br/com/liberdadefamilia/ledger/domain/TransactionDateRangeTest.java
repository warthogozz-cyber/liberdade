package br.com.liberdadefamilia.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class TransactionDateRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @Test
    void boundsAreInside() {
        assertThat(TransactionDateRange.contains(TransactionDateRange.MIN, TODAY))
                .isTrue();
        assertThat(TransactionDateRange.contains(TransactionDateRange.max(TODAY), TODAY))
                .isTrue();
        assertThat(TransactionDateRange.contains(TODAY, TODAY)).isTrue();
    }

    @Test
    void oneDayPastEitherBoundIsOutside() {
        assertThat(TransactionDateRange.contains(TransactionDateRange.MIN.minusDays(1), TODAY))
                .isFalse();
        assertThat(TransactionDateRange.contains(TransactionDateRange.max(TODAY).plusDays(1), TODAY))
                .isFalse();
    }

    @Test
    void maxIsAYearAndADayAhead() {
        assertThat(TransactionDateRange.MIN).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(TransactionDateRange.max(TODAY)).isEqualTo(LocalDate.of(2027, 11, 17));
    }

    @Test
    void months() {
        assertThat(TransactionDateRange.minMonth()).isEqualTo(YearMonth.of(2000, 1));
        assertThat(TransactionDateRange.maxMonth(TODAY)).isEqualTo(YearMonth.of(2027, 11));
    }

    /** 366 days from 2027-03-01 cross 2028-02-29, so they end on 2028-03-01 rather than 03-02. */
    @Test
    void leapYear() {
        assertThat(TransactionDateRange.max(LocalDate.of(2027, 3, 1))).isEqualTo(LocalDate.of(2028, 3, 1));
    }
}
