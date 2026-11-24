package br.com.liberdadefamilia.indicators.internal;

import java.time.Duration;

/** Waiting between attempts, behind an interface so tests can note the waits instead of sleeping. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
