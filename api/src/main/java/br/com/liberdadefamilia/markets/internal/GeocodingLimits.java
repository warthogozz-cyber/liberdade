package br.com.liberdadefamilia.markets.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * How many lookups each family may make per day, the day counted in São Paulo (D9). In memory, so each process counts
 * on its own. Past {@value #MAX_FAMILIES} families, the ones counted on an earlier day are dropped.
 */
final class GeocodingLimits {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_FAMILIES = 10_000;

    private record Day(LocalDate date, int count) {}

    private final Clock clock;
    private final int dailyLimit;
    private final ConcurrentMap<UUID, Day> days = new ConcurrentHashMap<>();

    GeocodingLimits(Clock clock, int dailyLimit) {
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    /** Counts one lookup; {@code false} when the family has made its limit today. */
    boolean tryConsume(UUID workspaceId) {
        LocalDate today = clock.instant().atZone(ZONE).toLocalDate();
        if (days.size() > MAX_FAMILIES) {
            days.values().removeIf(day -> day.date().isBefore(today));
        }
        AtomicBoolean allowed = new AtomicBoolean();
        days.compute(workspaceId, (ignored, current) -> {
            Day day = current == null || !current.date().equals(today) ? new Day(today, 0) : current;
            if (day.count() >= dailyLimit) {
                return day;
            }
            allowed.set(true);
            return new Day(today, day.count() + 1);
        });
        return allowed.get();
    }
}
