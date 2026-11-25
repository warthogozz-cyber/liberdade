package br.com.liberdadefamilia.markets.internal;

import java.time.Duration;

/** Waiting, behind an interface so tests can note the waits instead of sleeping. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
