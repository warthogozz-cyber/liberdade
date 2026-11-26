package br.com.liberdadefamilia.markets.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A new environment has no markets until Tuesday: import once at startup when the table is empty. */
@Component
@ConditionalOnProperty(name = "liberdade.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OsmImportStartupRun {

    private final JdbcClient jdbc;
    private final OsmMarketImporter importer;

    public OsmImportStartupRun(JdbcClient jdbc, OsmMarketImporter importer) {
        this.jdbc = jdbc;
        this.importer = importer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread.ofVirtual().name("osm-markets-startup").start(() -> OsmMarketImportJob.runGuarded(this::importIfEmpty));
    }

    void importIfEmpty() {
        Long rows =
                jdbc.sql("SELECT count(*) FROM osm_markets").query(Long.class).single();
        if (rows == 0) {
            importer.importNow();
        }
    }
}
