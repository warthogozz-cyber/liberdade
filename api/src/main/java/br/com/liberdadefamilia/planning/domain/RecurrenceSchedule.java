package br.com.liberdadefamilia.planning.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The dates of a recurring rule. The n-th date is the start plus n x interval units, never the previous date plus one
 * step, so a 31st that fell on the 28th does not drag the following months with it. {@code java.time} puts day 31 on
 * the last day of a shorter month and 29 February on 28 February.
 */
public final class RecurrenceSchedule {

    private static final int MIN_INTERVAL = 1;
    private static final int MAX_INTERVAL = 12;

    private RecurrenceSchedule() {}

    /** The dates in ascending order, all within {@code [max(from, startOn), min(toInclusive, endOn)]}. */
    public static List<LocalDate> occurrences(
            Frequency frequency,
            int interval,
            LocalDate startOn,
            @Nullable LocalDate endOn,
            LocalDate from,
            LocalDate toInclusive) {
        requireInterval(interval);
        LocalDate last = endOn != null && endOn.isBefore(toInclusive) ? endOn : toInclusive;
        List<LocalDate> dates = new ArrayList<>();
        for (long n = firstIndexOnOrAfter(frequency, interval, startOn, from); ; n++) {
            LocalDate date = nth(frequency, interval, startOn, n);
            if (date.isAfter(last)) {
                break;
            }
            dates.add(date);
        }
        return dates;
    }

    /** The first date strictly after {@code after}, or empty when the rule has ended by then. */
    public static Optional<LocalDate> firstAfter(
            Frequency frequency, int interval, LocalDate startOn, @Nullable LocalDate endOn, LocalDate after) {
        requireInterval(interval);
        long n = firstIndexOnOrAfter(frequency, interval, startOn, after.plusDays(1));
        LocalDate date = nth(frequency, interval, startOn, n);
        return endOn != null && date.isAfter(endOn) ? Optional.empty() : Optional.of(date);
    }

    private static void requireInterval(int interval) {
        if (interval < MIN_INTERVAL || interval > MAX_INTERVAL) {
            throw new IllegalArgumentException("interval must be between 1 and 12");
        }
    }

    private static LocalDate nth(Frequency frequency, int interval, LocalDate startOn, long n) {
        long units = n * interval;
        return switch (frequency) {
            case WEEKLY -> startOn.plusWeeks(units);
            case MONTHLY -> startOn.plusMonths(units);
            case YEARLY -> startOn.plusYears(units);
        };
    }

    /** The smallest n >= 0 whose date is on or after {@code from}: a guess from the distance, then a short fix. */
    private static long firstIndexOnOrAfter(Frequency frequency, int interval, LocalDate startOn, LocalDate from) {
        if (!from.isAfter(startOn)) {
            return 0;
        }
        long units = switch (frequency) {
            case WEEKLY -> ChronoUnit.WEEKS.between(startOn, from);
            case MONTHLY -> ChronoUnit.MONTHS.between(startOn, from);
            case YEARLY -> ChronoUnit.YEARS.between(startOn, from);
        };
        long n = Math.max(0, units / interval - 1);
        while (nth(frequency, interval, startOn, n).isBefore(from)) {
            n++;
        }
        return n;
    }
}
