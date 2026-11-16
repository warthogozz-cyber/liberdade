package br.com.liberdadefamilia.shared;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Counts attempts per key in fixed windows aligned to the epoch (D10). In memory, so each instance counts on its own.
 * Past {@value #MAX_KEYS} keys, the ones from earlier windows are dropped.
 */
public final class RateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final int limit;
    private final long windowMillis;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock, int limit, Duration window) {
        this.clock = clock;
        this.limit = limit;
        this.windowMillis = window.toMillis();
    }

    /** Counts one attempt; {@code false} when the key is over its limit in the current window. */
    public boolean tryAcquire(String key) {
        long index = currentIndex();
        if (windows.size() > MAX_KEYS) {
            windows.values().removeIf(window -> window.index() < index);
        }
        // Capped one past the limit: enough to say "over", and the count can never overflow.
        Window window = windows.compute(
                key,
                (ignored, current) -> current == null || current.index() != index
                        ? new Window(index, 1)
                        : new Window(index, Math.min(current.count() + 1, limit + 1)));
        return window.count() <= limit;
    }

    /** Seconds until the key's window ends, from 1 while it is blocked; 0 when it is not. */
    public long retryAfterSeconds(String key) {
        Window window = windows.get(key);
        if (window == null || window.index() != currentIndex() || window.count() <= limit) {
            return 0;
        }
        long remainingMillis = windowMillis - Math.floorMod(clock.millis(), windowMillis);
        return Math.max(1, Math.ceilDiv(remainingMillis, 1000));
    }

    /** How many keys are being counted. For tests. */
    int size() {
        return windows.size();
    }

    private long currentIndex() {
        return Math.floorDiv(clock.millis(), windowMillis);
    }

    private record Window(long index, int count) {}
}
