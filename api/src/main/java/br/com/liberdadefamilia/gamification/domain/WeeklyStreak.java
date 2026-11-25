package br.com.liberdadefamilia.gamification.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

/**
 * Counts the weeks in a row with at least one entry. A week without entries is forgiven once per calendar month
 * (frozen: it keeps the run alive but adds nothing); a second one in the same month ends the run. The current week
 * never breaks the run, because it is not over yet.
 */
public final class WeeklyStreak {

    private WeeklyStreak() {}

    /** The streak given the Mondays that have entries; Mondays after {@code currentMonday} are ignored. */
    public static Streak of(SortedSet<LocalDate> mondays, LocalDate currentMonday) {
        if (mondays.isEmpty() || mondays.first().isAfter(currentMonday)) {
            return new Streak(0, 0, 0);
        }
        Set<YearMonth> frozenMonths = new HashSet<>();
        int run = 0;
        int frozenInRun = 0;
        int longest = 0;
        boolean alive = false;
        for (LocalDate monday = mondays.first(); !monday.isAfter(currentMonday); monday = monday.plusWeeks(1)) {
            if (mondays.contains(monday)) {
                run++;
                alive = true;
                longest = Math.max(longest, run);
            } else if (!monday.equals(currentMonday) && alive) {
                if (frozenMonths.add(YearMonth.from(monday))) {
                    frozenInRun++;
                } else {
                    alive = false;
                    run = 0;
                    frozenInRun = 0;
                }
            }
        }
        return alive ? new Streak(run, frozenInRun, longest) : new Streak(0, 0, longest);
    }
}
