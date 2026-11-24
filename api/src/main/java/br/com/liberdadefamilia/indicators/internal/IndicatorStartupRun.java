package br.com.liberdadefamilia.indicators.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On the first start, when a series has never succeeded, collects once in a virtual thread so the start is not held
 * and a new environment fills itself. It exists only where scheduling is on, like the scheduled jobs.
 */
@Component
@ConditionalOnProperty(name = "liberdade.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class IndicatorStartupRun {

    private final IndicatorRepository repository;
    private final IndicatorJob job;

    public IndicatorStartupRun(IndicatorRepository repository, IndicatorJob job) {
        this.repository = repository;
        this.job = job;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        Thread.ofVirtual().name("indicator-startup").start(this::collectIfNeverSucceeded);
    }

    /** Collects, without the stale check, when some series has never had a success. */
    public void collectIfNeverSucceeded() {
        if (repository.allSeries().stream().anyMatch(s -> s.lastSuccessAt() == null)) {
            job.runOnce(false);
        }
    }
}
