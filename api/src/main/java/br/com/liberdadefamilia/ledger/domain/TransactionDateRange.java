package br.com.liberdadefamilia.ledger.domain;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The days a transaction may fall on: from 2000-01-01 to a year and a day after the family's today. "Today" is the
 * date in the family's time zone, never the server's, so the limit moves at the family's midnight.
 */
public final class TransactionDateRange {

    /** The first day a transaction may fall on. */
    public static final LocalDate MIN = LocalDate.of(2000, 1, 1);

    private static final int DAYS_AHEAD = 366;

    private TransactionDateRange() {}

    /** The last day a transaction may fall on, given the family's today. */
    public static LocalDate max(LocalDate today) {
        return today.plusDays(DAYS_AHEAD);
    }

    /** Whether the day is within the range, both ends included. */
    public static boolean contains(LocalDate day, LocalDate today) {
        return !day.isBefore(MIN) && !day.isAfter(max(today));
    }

    /** The first month the list and the dashboard may show. */
    public static YearMonth minMonth() {
        return YearMonth.from(MIN);
    }

    /** The last month the list and the dashboard may show: the month of {@link #max(LocalDate)}. */
    public static YearMonth maxMonth(LocalDate today) {
        return YearMonth.from(max(today));
    }
}
