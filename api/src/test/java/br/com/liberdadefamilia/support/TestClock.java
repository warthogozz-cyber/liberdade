package br.com.liberdadefamilia.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/**
 * A clock a test can stop and move. It follows the system clock until {@link #freeze} is called, so the tests that
 * never touch it behave as with the real one.
 */
public final class TestClock extends Clock {

    private volatile @Nullable Instant frozen;

    /** Stops the clock at the instant; calling it again moves it. */
    public void freeze(Instant instant) {
        this.frozen = instant;
    }

    /** Goes back to the system clock. */
    public void release() {
        this.frozen = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        Instant stopped = frozen;
        return stopped != null ? stopped : Instant.now();
    }
}
