package br.com.liberdadefamilia.markets.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The polite pace towards the address service (D9): one call per interval in this process, in the order the calls
 * came. A call that would wait more than {@value #MAX_WAIT_SECONDS} seconds gives up without sleeping.
 */
final class GeocodingThrottle {

    static final long MAX_WAIT_SECONDS = 10;
    private static final Duration MAX_WAIT = Duration.ofSeconds(MAX_WAIT_SECONDS);

    private final Clock clock;
    private final Sleeper sleeper;
    private final Duration minInterval;
    /** A fair lock: the calls leave the line in the order they entered it. */
    private final ReentrantLock line = new ReentrantLock(true);

    private final AtomicInteger pending = new AtomicInteger();
    /** When the next call may start; guarded by {@link #line}. */
    private Instant next;

    GeocodingThrottle(Clock clock, Sleeper sleeper, Duration minInterval) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.minInterval = minInterval;
    }

    /**
     * Returns when the call may go to the service.
     *
     * @throws GeocodingBusyException when the line is so long that the wait would pass 10 seconds (exactly 10 waits)
     */
    void acquire() {
        // The estimate counts this call: n calls in the line wait about n intervals.
        int inLine = pending.incrementAndGet();
        try {
            if (minInterval.multipliedBy(inLine).compareTo(MAX_WAIT) > 0) {
                throw new GeocodingBusyException();
            }
            line.lockInterruptibly();
            try {
                Instant now = clock.instant();
                Duration wait = next == null ? Duration.ZERO : Duration.between(now, next);
                if (wait.isPositive()) {
                    sleeper.sleep(wait);
                }
                Instant started = next != null && next.isAfter(now) ? next : now;
                next = started.plus(minInterval);
            } finally {
                line.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeocodingBusyException();
        } finally {
            pending.decrementAndGet();
        }
    }

    /** How many calls are in the line now, the one being served included. For tests. */
    int pending() {
        return pending.get();
    }
}
