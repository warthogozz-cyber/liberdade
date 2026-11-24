package br.com.liberdadefamilia.indicators.domain;

import java.time.Instant;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/** When a series counts as out of date: more than three weekdays since the day of its last success. */
public final class Staleness {

    private static final int MAX_WEEKDAYS = 3;

    private Staleness() {}

    /** A series that never succeeded is stale. Days are counted in {@code zone}, without holidays. */
    public static boolean isStale(@Nullable Instant lastSuccessAt, Instant now, ZoneId zone) {
        if (lastSuccessAt == null) {
            return true;
        }
        return BusinessDays.weekdaysAfter(
                        lastSuccessAt.atZone(zone).toLocalDate(),
                        now.atZone(zone).toLocalDate())
                > MAX_WEEKDAYS;
    }
}
