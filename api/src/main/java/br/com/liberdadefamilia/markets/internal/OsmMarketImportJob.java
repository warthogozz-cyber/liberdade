package br.com.liberdadefamilia.markets.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The weekly import (Tuesday morning) and the daily "is the data too old" check, both in Sao Paulo time. */
@Component
public class OsmMarketImportJob {

    private static final Logger log = LoggerFactory.getLogger(OsmMarketImportJob.class);

    private final OsmMarketImporter importer;

    public OsmMarketImportJob(OsmMarketImporter importer) {
        this.importer = importer;
    }

    @Scheduled(cron = "0 40 4 * * TUE", zone = "America/Sao_Paulo")
    public void run() {
        Thread.ofVirtual().name("osm-markets-import").start(() -> runGuarded(importer::importNow));
    }

    @Scheduled(cron = "0 10 9 * * *", zone = "America/Sao_Paulo")
    public void checkStale() {
        runGuarded(importer::checkStale);
    }

    /** A job must not die with the scheduler: log the class of what went wrong, never its message. */
    static void runGuarded(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("markets job failed: {}", e.getClass().getName());
        }
    }
}
