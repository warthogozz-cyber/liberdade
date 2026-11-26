package br.com.liberdadefamilia.markets.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Answers by the normalized query (D9): in memory, for a day, at most {@code maxEntries}; the oldest goes first. The
 * queries live only here, in this process, and are never written anywhere.
 */
final class GeocodingCache {

    private record Entry(List<GeocodedPlace> places, Instant storedAt) {}

    private final Clock clock;
    private final Duration ttl;
    private final int maxEntries;
    /** In insertion order: the first key is the oldest. */
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    GeocodingCache(Clock clock, Duration ttl, int maxEntries) {
        this.clock = clock;
        this.ttl = ttl;
        this.maxEntries = maxEntries;
    }

    synchronized Optional<List<GeocodedPlace>> get(String normalized) {
        Entry entry = entries.get(normalized);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.instant().isAfter(entry.storedAt().plus(ttl))) {
            entries.remove(normalized);
            return Optional.empty();
        }
        return Optional.of(entry.places());
    }

    synchronized void put(String normalized, List<GeocodedPlace> places) {
        entries.remove(normalized);
        entries.put(normalized, new Entry(List.copyOf(places), clock.instant()));
        Iterator<String> oldest = entries.keySet().iterator();
        while (entries.size() > maxEntries && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
