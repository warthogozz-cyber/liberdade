package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The polite pace towards the address service: one call per interval, and a line that gives up after 10 seconds. */
class GeocodingThrottleTest {

    private static final Duration INTERVAL = Duration.ofSeconds(2);

    private final TestClock clock = new TestClock();
    private final List<Duration> waits = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @AfterEach
    void stop() {
        release.countDown();
        pool.shutdownNow();
    }

    private GeocodingThrottle throttle(Sleeper sleeper) {
        clock.freeze(Instant.parse("2026-11-25T12:00:00Z"));
        return new GeocodingThrottle(clock, sleeper, INTERVAL);
    }

    /** A sleeper that notes the wait and then stays asleep until the test lets it go. */
    private Sleeper blockingSleeper() {
        return duration -> {
            waits.add(duration);
            release.await();
        };
    }

    private static void awaitPending(GeocodingThrottle throttle, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (throttle.pending() < count) {
            assertThat(System.nanoTime()).as("pending calls reached %d", count).isLessThan(deadline);
            Thread.sleep(5);
        }
    }

    @Test
    void secondCallWaitsTheInterval() {
        GeocodingThrottle throttle = throttle(waits::add);

        throttle.acquire();
        assertThat(waits).isEmpty();
        throttle.acquire();

        assertThat(waits).containsExactly(Duration.parse("PT2S"));
    }

    @Test
    void givesUpAfterTenSeconds() throws Exception {
        GeocodingThrottle throttle = throttle(blockingSleeper());
        throttle.acquire(); // the line is free: no wait
        List<Future<?>> inLine = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            inLine.add(pool.submit(throttle::acquire));
        }
        awaitPending(throttle, 5);

        // The sixth would wait 6 x 2 s: it gives up, and it does not sleep.
        assertThatThrownBy(throttle::acquire).isInstanceOf(GeocodingBusyException.class);

        assertThat(waits).hasSize(1);
        assertThat(throttle.pending()).isEqualTo(5);
        release.countDown();
        for (Future<?> each : inLine) {
            each.get(5, TimeUnit.SECONDS);
        }
        assertThat(throttle.pending()).isZero();
    }

    @Test
    void exactlyTenSecondsStillWaits() throws Exception {
        GeocodingThrottle throttle = throttle(blockingSleeper());
        throttle.acquire();
        List<Future<?>> inLine = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            inLine.add(pool.submit(throttle::acquire));
        }
        awaitPending(throttle, 5); // 5 x 2 s = 10 s: the fifth waits, it is not refused

        release.countDown();
        for (Future<?> each : inLine) {
            each.get(5, TimeUnit.SECONDS); // none of them threw
        }
    }

    @Test
    void anInterruptedWaitGivesUp() {
        GeocodingThrottle throttle = throttle(duration -> {
            throw new InterruptedException();
        });
        throttle.acquire();

        assertThatThrownBy(throttle::acquire).isInstanceOf(GeocodingBusyException.class);
        assertThat(Thread.interrupted()).isTrue(); // the flag is kept, and cleared here for the other tests
        assertThat(throttle.pending()).isZero();
    }
}
