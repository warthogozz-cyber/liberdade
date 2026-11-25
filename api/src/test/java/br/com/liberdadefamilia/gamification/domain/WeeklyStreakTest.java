package br.com.liberdadefamilia.gamification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyStreakTest {

    private static final LocalDate NOV_23 = seg(11, 23);

    private static LocalDate seg(int month, int day) {
        return LocalDate.of(2026, month, day);
    }

    private static SortedSet<LocalDate> mondays(LocalDate... days) {
        return new TreeSet<>(Arrays.asList(days));
    }

    @Test
    @DisplayName("sem lançamento")
    void noEntries() {
        assertThat(WeeklyStreak.of(mondays(), NOV_23)).isEqualTo(new Streak(0, 0, 0));
    }

    @Test
    @DisplayName("só a semana corrente")
    void onlyCurrentWeek() {
        assertThat(WeeklyStreak.of(mondays(seg(11, 23)), NOV_23)).isEqualTo(new Streak(1, 0, 1));
    }

    @Test
    @DisplayName("quatro seguidas")
    void fourInARow() {
        assertThat(WeeklyStreak.of(mondays(seg(11, 2), seg(11, 9), seg(11, 16), seg(11, 23)), NOV_23))
                .isEqualTo(new Streak(4, 0, 4));
    }

    @Test
    @DisplayName("semana corrente vazia não quebra")
    void emptyCurrentWeekDoesNotBreak() {
        assertThat(WeeklyStreak.of(mondays(seg(11, 2), seg(11, 9), seg(11, 16)), NOV_23))
                .isEqualTo(new Streak(3, 0, 3));
    }

    @Test
    @DisplayName("congelada não soma")
    void frozenDoesNotAdd() {
        assertThat(WeeklyStreak.of(mondays(seg(11, 2), seg(11, 9), seg(11, 23)), seg(11, 30)))
                .isEqualTo(new Streak(3, 1, 3));
    }

    @Test
    @DisplayName("duas faltas no mesmo mês quebram")
    void twoMissesInTheSameMonthBreak() {
        assertThat(WeeklyStreak.of(mondays(seg(11, 2), seg(11, 16), seg(11, 30)), seg(11, 30)))
                .isEqualTo(new Streak(1, 0, 2));
    }

    @Test
    @DisplayName("virada de mês congela de novo")
    void newMonthFreezesAgain() {
        assertThat(WeeklyStreak.of(mondays(seg(10, 19), seg(11, 2), seg(11, 16), seg(11, 23)), NOV_23))
                .isEqualTo(new Streak(4, 2, 4));
    }

    @Test
    @DisplayName("antes do primeiro lançamento não conta")
    void beforeTheFirstEntryDoesNotCount() {
        assertThat(WeeklyStreak.of(mondays(seg(11, 16)), seg(11, 30))).isEqualTo(new Streak(1, 1, 1));
    }

    @Test
    @DisplayName("falta sem sequência não gasta o mês")
    void missWithoutRunDoesNotSpendTheMonth() {
        assertThat(WeeklyStreak.of(mondays(seg(10, 5), seg(11, 9), seg(11, 23)), NOV_23))
                .isEqualTo(new Streak(2, 1, 2));
    }

    @Test
    @DisplayName("segunda futura é ignorada")
    void futureMondayIsIgnored() {
        assertThat(WeeklyStreak.of(mondays(seg(11, 23), seg(11, 30)), NOV_23)).isEqualTo(new Streak(1, 0, 1));
    }

    @Test
    @DisplayName("só segundas futuras dão zero")
    void onlyFutureMondaysGiveZero() {
        assertThat(WeeklyStreak.of(mondays(seg(11, 30)), NOV_23)).isEqualTo(new Streak(0, 0, 0));
    }
}
