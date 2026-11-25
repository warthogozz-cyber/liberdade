package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.internal.OsmImportException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tells the person watching the logs that the markets extract needs a look. The lines carry a fixed type and reason,
 * counts and nothing else: never a market name, a coordinate or a URL. CloudWatch watches the {@code MARKETS_ALERT}
 * term.
 */
@Component
public class MarketsAlerts {

    private static final Logger log = LoggerFactory.getLogger("br.com.liberdadefamilia.markets.alerts");

    public void importFailed(Reason reason) {
        log.warn("MARKETS_ALERT type=IMPORT_FAILED reason={}", reason);
    }

    public void datasetStale() {
        log.warn("MARKETS_ALERT type=DATASET_STALE");
    }

    public void importOk(int rows) {
        log.info("MARKETS_IMPORT ok rows={}", rows);
    }

    public void importSkipped() {
        log.info("MARKETS_IMPORT skipped");
    }
}
