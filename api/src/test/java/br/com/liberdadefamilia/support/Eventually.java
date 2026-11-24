package br.com.liberdadefamilia.support;

import java.time.Duration;

/** Repeats an assertion until it passes or the time is up, for work that happens on another thread. */
public final class Eventually {

    private Eventually() {}

    /** An assertion that may throw. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Runs the assertion until it passes; past the timeout, throws the last error it raised. */
    public static void within(Duration timeout, ThrowingRunnable assertion) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError | Exception failure) {
                if (System.nanoTime() >= deadline) {
                    if (failure instanceof AssertionError error) {
                        throw error;
                    }
                    throw new IllegalStateException(failure);
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }
}
