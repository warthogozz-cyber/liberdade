package br.com.liberdadefamilia.markets.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Answers of the embed service by canonical URL, in memory: an embed for 7 days, a refusal for a minute, so a post made
 * public later does not stay refused. At most {@code maxEntries}; the oldest goes first.
 */
final class InstagramEmbedCache {

    static final Duration EMBED_TTL = Duration.ofDays(7);
    static final Duration REFUSAL_TTL = Duration.ofMinutes(1);

    /** {@code html} is null for a refusal. */
    private record Entry(@Nullable String html, Instant expiresAt) {}

    private final Clock clock;
    private final int maxEntries;
    /** In insertion order: the first key is the oldest. */
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    InstagramEmbedCache(Clock clock, int maxEntries) {
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    /** Empty when nothing fresh is kept; otherwise the answer, itself empty for a refusal. */
    synchronized Optional<Optional<String>> get(String url) {
        Entry entry = entries.get(url);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.instant().isAfter(entry.expiresAt())) {
            entries.remove(url);
            return Optional.empty();
        }
        return Optional.of(Optional.ofNullable(entry.html()));
    }

    synchronized void put(String url, Optional<String> html) {
        Duration ttl = html.isPresent() ? EMBED_TTL : REFUSAL_TTL;
        entries.remove(url);
        entries.put(url, new Entry(html.orElse(null), clock.instant().plus(ttl)));
        Iterator<String> oldest = entries.keySet().iterator();
        while (entries.size() > maxEntries && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
