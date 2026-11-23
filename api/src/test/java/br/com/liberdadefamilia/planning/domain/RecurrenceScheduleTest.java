package br.com.liberdadefamilia.planning.domain;

import static br.com.liberdadefamilia.planning.domain.Frequency.MONTHLY;
import static br.com.liberdadefamilia.planning.domain.Frequency.WEEKLY;
import static br.com.liberdadefamilia.planning.domain.Frequency.YEARLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurrenceScheduleTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void day31FallsOnLastDay() {
        assertThat(RecurrenceSchedule.occurrences(MONTHLY, 1, d("2026-01-31"), null, d("2026-01-01"), d("2026-05-31")))
                .containsExactly(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30"), d("2026-05-31"));
    }

    @Test
    void leapDayFallsOn28() {
        assertThat(RecurrenceSchedule.occurrences(YEARLY, 1, d("2028-02-29"), null, d("2028-01-01"), d("2031-12-31")))
                .containsExactly(d("2028-02-29"), d("2029-02-28"), d("2030-02-28"), d("2031-02-28"));
    }

    @Test
    void weeklyEveryTwo() {
        assertThat(RecurrenceSchedule.occurrences(WEEKLY, 2, d("2026-11-02"), null, d("2026-11-01"), d("2026-12-31")))
                .containsExactly(d("2026-11-02"), d("2026-11-16"), d("2026-11-30"), d("2026-12-14"), d("2026-12-28"));
    }

    @Test
    void endOnCutsTheHorizon() {
        assertThat(RecurrenceSchedule.occurrences(
                        MONTHLY, 1, d("2026-11-10"), d("2026-12-01"), d("2026-11-01"), d("2027-01-31")))
                .containsExactly(d("2026-11-10"));
    }

    @Test
    void fromAfterStartSkipsPast() {
        assertThat(RecurrenceSchedule.occurrences(MONTHLY, 1, d("2026-01-05"), null, d("2026-11-01"), d("2026-12-31")))
                .containsExactly(d("2026-11-05"), d("2026-12-05"));
    }

    @Test
    void fromBeforeStartBeginsAtStart() {
        assertThat(RecurrenceSchedule.occurrences(MONTHLY, 3, d("2026-05-15"), null, d("2026-01-01"), d("2026-12-31")))
                .containsExactly(d("2026-05-15"), d("2026-08-15"), d("2026-11-15"));
    }

    @Test
    void emptyWhenWindowEndsBeforeStart() {
        assertThat(RecurrenceSchedule.occurrences(MONTHLY, 1, d("2026-05-15"), null, d("2026-01-01"), d("2026-02-01")))
                .isEmpty();
    }

    @Test
    void firstAfterSkipsTheGivenDay() {
        assertThat(RecurrenceSchedule.firstAfter(MONTHLY, 1, d("2026-01-31"), null, d("2026-02-28")))
                .contains(d("2026-03-31"));
        assertThat(RecurrenceSchedule.firstAfter(WEEKLY, 1, d("2026-11-02"), null, d("2026-01-01")))
                .contains(d("2026-11-02"));
    }

    @Test
    void firstAfterReturnsEmptyPastEnd() {
        assertThat(RecurrenceSchedule.firstAfter(MONTHLY, 1, d("2026-01-05"), d("2026-12-01"), d("2026-12-31")))
                .isEmpty();
    }

    @Test
    void intervalOutOfRangeFails() {
        for (int bad : new int[] {0, 13}) {
            assertThatThrownBy(() -> RecurrenceSchedule.occurrences(
                            MONTHLY, bad, d("2026-01-01"), null, d("2026-01-01"), d("2026-12-31")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () -> RecurrenceSchedule.firstAfter(MONTHLY, bad, d("2026-01-01"), null, d("2026-01-01")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
