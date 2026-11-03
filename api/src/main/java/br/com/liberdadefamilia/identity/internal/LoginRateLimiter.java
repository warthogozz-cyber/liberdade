package br.com.liberdadefamilia.identity.internal;

import java.time.Clock;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Counts sign-in attempts per key in fixed windows of one epoch minute (D10). In memory, so each instance counts on
 * its own. Past {@value #MAX_KEYS} keys, the ones from earlier minutes are dropped.
 */
public final class LoginRateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final int limitPerMinute;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimiter(Clock clock, int limitPerMinute) {
        this.clock = clock;
        this.limitPerMinute = limitPerMinute;
    }

    /**
     * Counts one attempt.
     *
     * @return empty when the attempt is within the limit; otherwise the seconds until the next minute, from 1 to 60
     */
    public OptionalLong tryAcquire(String key) {
        long epochSecond = clock.instant().getEpochSecond();
        long minute = Math.floorDiv(epochSecond, 60);
        if (windows.size() > MAX_KEYS) {
            windows.values().removeIf(window -> window.minute() < minute);
        }
        // Capped one past the limit: enough to say "over", and the count can never overflow.
        Window window = windows.compute(
                key,
                (ignored, current) -> current == null || current.minute() != minute
                        ? new Window(minute, 1)
                        : new Window(minute, Math.min(current.count() + 1, limitPerMinute + 1)));
        return window.count() <= limitPerMinute
                ? OptionalLong.empty()
                : OptionalLong.of(60 - Math.floorMod(epochSecond, 60));
    }

    /** How many keys are being counted. For tests. */
    int size() {
        return windows.size();
    }

    private record Window(long minute, int count) {}
}
