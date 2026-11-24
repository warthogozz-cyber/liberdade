package br.com.liberdadefamilia.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BusinessDaysTest {

    @Test
    void countsOnlyWeekdays() {
        assertThat(BusinessDays.weekdaysAfter(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23)))
                .isEqualTo(1);
    }

    @Test
    void sameDayIsZero() {
        LocalDate day = LocalDate.of(2026, 11, 24);
        assertThat(BusinessDays.weekdaysAfter(day, day)).isZero();
    }

    @Test
    void aWeekHasFiveWeekdays() {
        assertThat(BusinessDays.weekdaysAfter(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23)))
                .isEqualTo(5);
    }

    @Test
    void aDateBeforeIsZero() {
        assertThat(BusinessDays.weekdaysAfter(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 20)))
                .isZero();
    }
}
